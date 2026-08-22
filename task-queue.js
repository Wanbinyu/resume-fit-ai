import { randomUUID, timingSafeEqual } from "node:crypto";

export class QueueFullError extends Error {
  constructor(message = "任务队列已满，请稍后再试。") {
    super(message);
    this.name = "QueueFullError";
  }
}

export class TaskQueue {
  constructor({ worker, concurrency = 2, maxQueued = 20, ttlMs = 15 * 60 * 1000, onSettled = () => {} }) {
    if (typeof worker !== "function") throw new TypeError("TaskQueue worker must be a function");
    this.worker = worker;
    this.concurrency = clampInteger(concurrency, 1, 8, 2);
    this.maxQueued = clampInteger(maxQueued, 1, 100, 20);
    this.ttlMs = clampInteger(ttlMs, 60_000, 60 * 60 * 1000, 15 * 60 * 1000);
    this.onSettled = onSettled;
    this.tasks = new Map();
    this.pending = [];
    this.active = 0;
    this.closed = false;
    this.cleanupTimer = setInterval(() => this.cleanup(), Math.min(this.ttlMs, 60_000));
    this.cleanupTimer.unref?.();
  }

  create(input, metadata = {}) {
    if (this.closed) throw new Error("TaskQueue is closed");
    const queuedCount = this.pending.filter((task) => task.status === "queued").length;
    if (queuedCount >= this.maxQueued) throw new QueueFullError();

    const now = Date.now();
    const task = {
      id: randomUUID(),
      token: randomUUID(),
      status: "queued",
      progress: 5,
      message: "任务已进入队列",
      input,
      metadata,
      result: null,
      error: "",
      createdAt: now,
      updatedAt: now,
      completedAt: null,
      expiresAt: null,
      controller: null
    };

    this.tasks.set(task.id, task);
    this.pending.push(task);
    queueMicrotask(() => this.drain());
    return this.toPublicTask(task, true);
  }

  get(id, token) {
    const task = this.tasks.get(id);
    if (!task || !secureTokenEqual(task.token, token)) return null;
    return this.toPublicTask(task, false);
  }

  cancel(id, token) {
    const task = this.tasks.get(id);
    if (!task || !secureTokenEqual(task.token, token)) return null;
    if (["succeeded", "failed", "canceled"].includes(task.status)) return this.toPublicTask(task, false);

    task.status = "canceled";
    task.progress = 0;
    task.message = "任务已取消";
    task.error = "任务已取消。";
    task.input = null;
    task.updatedAt = Date.now();
    task.completedAt = task.updatedAt;
    task.expiresAt = task.updatedAt + this.ttlMs;
    task.controller?.abort(new DOMException("Task canceled", "AbortError"));
    this.onSettledSafely(task);
    return this.toPublicTask(task, false);
  }

  snapshot() {
    const counts = { queued: 0, running: 0, succeeded: 0, failed: 0, canceled: 0 };
    for (const task of this.tasks.values()) {
      if (Object.hasOwn(counts, task.status)) counts[task.status] += 1;
    }
    return { active: this.active, retained: this.tasks.size, ...counts };
  }

  cleanup(now = Date.now()) {
    for (const [id, task] of this.tasks) {
      if (task.expiresAt && task.expiresAt <= now) this.tasks.delete(id);
    }
    this.pending = this.pending.filter((task) => task.status === "queued");
  }

  close() {
    this.closed = true;
    clearInterval(this.cleanupTimer);
    for (const task of this.tasks.values()) {
      if (task.status === "running") task.controller?.abort(new DOMException("Server shutting down", "AbortError"));
      task.input = null;
    }
  }

  async drain() {
    while (!this.closed && this.active < this.concurrency) {
      const task = this.pending.shift();
      if (!task) return;
      if (task.status !== "queued") continue;
      this.run(task);
    }
  }

  async run(task) {
    this.active += 1;
    task.status = "running";
    task.progress = 12;
    task.message = "正在准备分析内容";
    task.updatedAt = Date.now();
    task.controller = new AbortController();

    const updateProgress = (progress, message) => {
      if (task.status !== "running") return;
      task.progress = clampInteger(progress, task.progress, 95, task.progress);
      if (message) task.message = String(message).slice(0, 100);
      task.updatedAt = Date.now();
    };

    try {
      const result = await this.worker(task.input, {
        signal: task.controller.signal,
        updateProgress,
        metadata: task.metadata
      });
      if (task.status === "canceled") return;
      task.status = "succeeded";
      task.progress = 100;
      task.message = "报告生成完成";
      task.result = result;
    } catch (error) {
      if (task.status === "canceled") return;
      task.status = "failed";
      task.progress = 0;
      task.message = "报告生成失败";
      task.error = String(error?.publicMessage || "AI 暂时生成失败，请稍后重试。").slice(0, 200);
    } finally {
      task.input = null;
      task.controller = null;
      if (task.status !== "canceled") {
        task.updatedAt = Date.now();
        task.completedAt = task.updatedAt;
        task.expiresAt = task.updatedAt + this.ttlMs;
        this.onSettledSafely(task);
      }
      this.active -= 1;
      this.drain();
    }
  }

  onSettledSafely(task) {
    try {
      this.onSettled({
        id: task.id,
        status: task.status,
        metadata: task.metadata,
        createdAt: task.createdAt,
        completedAt: task.completedAt
      });
    } catch (error) {
      console.error("Task settlement hook failed:", error.message);
    }
  }

  toPublicTask(task, includeToken) {
    const result = {
      id: task.id,
      status: task.status,
      progress: task.progress,
      message: task.message,
      error: task.error || undefined,
      result: task.status === "succeeded" ? task.result : undefined,
      createdAt: new Date(task.createdAt).toISOString(),
      completedAt: task.completedAt ? new Date(task.completedAt).toISOString() : undefined
    };
    if (includeToken) result.token = task.token;
    return result;
  }
}

function secureTokenEqual(expected, actual) {
  if (typeof expected !== "string" || typeof actual !== "string") return false;
  const expectedBuffer = Buffer.from(expected);
  const actualBuffer = Buffer.from(actual);
  return expectedBuffer.length === actualBuffer.length && timingSafeEqual(expectedBuffer, actualBuffer);
}

function clampInteger(value, min, max, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.round(parsed)));
}
