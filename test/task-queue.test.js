import assert from "node:assert/strict";
import test from "node:test";
import { QueueFullError, TaskQueue } from "../task-queue.js";

test("runs tasks with the configured concurrency and clears sensitive input", async () => {
  let active = 0;
  let peak = 0;
  const releases = [];
  const queue = new TaskQueue({
    concurrency: 1,
    ttlMs: 60_000,
    worker: async (input, { updateProgress }) => {
      active += 1;
      peak = Math.max(peak, active);
      updateProgress(60, "working");
      await new Promise((resolve) => releases.push(resolve));
      active -= 1;
      return { value: input.secret.length };
    }
  });

  const first = queue.create({ secret: "resume one" });
  const second = queue.create({ secret: "resume two" });
  await waitFor(() => queue.get(first.id, first.token)?.status === "running");
  assert.equal(queue.get(second.id, second.token)?.status, "queued");
  releases.shift()();
  await waitFor(() => queue.get(second.id, second.token)?.status === "running");
  releases.shift()();
  await waitFor(() => queue.get(second.id, second.token)?.status === "succeeded");

  assert.equal(peak, 1);
  assert.equal(queue.tasks.get(first.id).input, null);
  assert.deepEqual(queue.get(first.id, first.token).result, { value: 10 });
  assert.equal(queue.get(first.id, "wrong-token"), null);
  queue.close();
});

test("cancels queued tasks without invoking the worker", async () => {
  let calls = 0;
  let releaseFirst;
  const queue = new TaskQueue({
    concurrency: 1,
    worker: async () => {
      calls += 1;
      await new Promise((resolve) => { releaseFirst = resolve; });
      return {};
    }
  });

  const first = queue.create({ resume: "one" });
  const second = queue.create({ resume: "two" });
  await waitFor(() => queue.get(first.id, first.token)?.status === "running");
  const canceled = queue.cancel(second.id, second.token);
  assert.equal(canceled.status, "canceled");
  assert.equal(queue.tasks.get(second.id).input, null);
  releaseFirst();
  await waitFor(() => queue.get(first.id, first.token)?.status === "succeeded");
  assert.equal(calls, 1);
  queue.close();
});

test("rejects new queued work when the queue is full", async () => {
  let release;
  const queue = new TaskQueue({
    concurrency: 1,
    maxQueued: 1,
    worker: () => new Promise((resolve) => { release = resolve; })
  });

  const first = queue.create({});
  await waitFor(() => queue.get(first.id, first.token)?.status === "running");
  queue.create({});
  assert.throws(() => queue.create({}), QueueFullError);
  release({});
  queue.close();
});

test("removes completed task data after the retention period", async () => {
  const queue = new TaskQueue({ worker: async () => ({ ok: true }), ttlMs: 60_000 });
  const task = queue.create({ resume: "private" });
  await waitFor(() => queue.get(task.id, task.token)?.status === "succeeded");
  const expiresAt = queue.tasks.get(task.id).expiresAt;
  queue.cleanup(expiresAt);
  assert.equal(queue.get(task.id, task.token), null);
  queue.close();
});

async function waitFor(predicate, timeoutMs = 1000) {
  const startedAt = Date.now();
  while (!predicate()) {
    if (Date.now() - startedAt > timeoutMs) throw new Error("Timed out waiting for task state");
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}
