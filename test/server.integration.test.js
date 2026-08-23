import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import test, { after, before } from "node:test";

process.env.AI_PROVIDER = "custom";
process.env.CUSTOM_API_KEY = "";
process.env.CUSTOM_BASE_URL = "";
process.env.CUSTOM_MODEL = "";
process.env.ADMIN_STATS_TOKEN = "test-admin-token-that-is-long-enough";
process.env.USAGE_HASH_SALT = "test-usage-hash-salt-that-is-long-enough";
process.env.USAGE_STATS_FILE = "";
process.env.SITE_OPERATOR_NAME = "Test Operator";
process.env.SITE_CONTACT = "https://example.com/contact";

const { app, stopBackgroundServices, validateProductionConfig } = await import("../server.js");
let server;
let baseUrl;

before(async () => {
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  stopBackgroundServices();
  await new Promise((resolve) => server.close(resolve));
});

test("creates and completes an authenticated analysis task", async () => {
  const response = await fetch(`${baseUrl}/api/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Idempotency-Key": randomUUID() },
    body: JSON.stringify(validPayload())
  });
  assert.equal(response.status, 202);
  const created = await response.json();
  assert.match(created.id, /^[0-9a-f-]{36}$/);
  assert.match(created.token, /^[0-9a-f-]{36}$/);

  const unauthorized = await fetch(`${baseUrl}/api/analyze/${created.id}`, {
    headers: { "X-Task-Token": "wrong-token" }
  });
  assert.equal(unauthorized.status, 404);

  const completed = await waitForTask(created);
  assert.equal(completed.status, "succeeded");
  assert.equal(completed.result.demo, true);
  assert.equal(typeof completed.result.report.summary, "string");
  assert.equal(completed.result.report.issueDetails.length, 2);
});

test("protects runtime statistics with the configured admin token", async () => {
  const unauthorized = await fetch(`${baseUrl}/api/admin/stats`);
  assert.equal(unauthorized.status, 401);

  const response = await fetch(`${baseUrl}/api/admin/stats`, {
    headers: { Authorization: `Bearer ${process.env.ADMIN_STATS_TOKEN}` }
  });
  assert.equal(response.status, 200);
  const stats = await response.json();
  assert.ok(stats.requests.accepted >= 1);
  assert.ok(stats.requests.succeeded >= 1);
  assert.equal(typeof stats.queue.active, "number");
  assert.equal(typeof stats.fullGeneration.totalClicks, "number");
  assert.equal(typeof stats.fullGeneration.approximateUniqueBrowsers, "number");
});

test("serves the statistics dashboard only from the private path", async () => {
  const current = await fetch(`${baseUrl}/121/admin.html`);
  assert.equal(current.status, 200);
  assert.match(await current.text(), /使用统计/);

  const legacy = await fetch(`${baseUrl}/admin.html`);
  assert.equal(legacy.status, 404);
});

test("returns the same task for a repeated idempotency key", async () => {
  const idempotencyKey = randomUUID();
  const request = () => fetch(`${baseUrl}/api/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Idempotency-Key": idempotencyKey },
    body: JSON.stringify(validPayload())
  });
  const first = await request();
  const second = await request();
  const firstTask = await first.json();
  const secondTask = await second.json();
  assert.equal(firstTask.id, secondTask.id);
  assert.equal(firstTask.token, secondTask.token);
});

test("counts accepted full generations and deduplicates the same browser", async () => {
  const before = await readAdminStats();
  const visitorId = randomUUID();

  for (let index = 0; index < 2; index += 1) {
    const idempotencyKey = randomUUID();
    const request = () => fetch(`${baseUrl}/api/analyze`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Idempotency-Key": idempotencyKey,
        "X-Visitor-Id": visitorId
      },
      body: JSON.stringify({ ...validPayload(), mode: "full" })
    });
    const first = await request();
    const repeated = await request();
    assert.equal(first.status, 202);
    assert.ok([200, 202].includes(repeated.status));
    await waitForTask(await first.json());
  }

  const after = await readAdminStats();
  assert.equal(after.fullGeneration.totalClicks, before.fullGeneration.totalClicks + 2);
  assert.equal(after.fullGeneration.approximateUniqueBrowsers, before.fullGeneration.approximateUniqueBrowsers + 1);
  assert.match(after.fullGeneration.lastGeneratedAt, /^\d{4}-\d{2}-\d{2}T/);
});

test("serves the privacy policy and terms", async () => {
  const [privacy, terms] = await Promise.all([
    fetch(`${baseUrl}/privacy.html`),
    fetch(`${baseUrl}/terms.html`)
  ]);
  assert.equal(privacy.status, 200);
  assert.equal(terms.status, 200);
  assert.match(await privacy.text(), /AI 服务商/);
  assert.match(await terms.text(), /AI 输出边界/);
});

test("exports targeting advice as a valid DOCX archive", async () => {
  const response = await fetch(`${baseUrl}/api/export-targeting`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      targetRole: "软件开发",
      plan: {
        strategySummary: "突出后端项目与数据库优化经验。",
        layoutChanges: [],
        projectDecisions: [{
          name: "课程项目",
          decision: "strengthen",
          relevance: 80,
          reason: "与岗位技术要求相关",
          featuresToAdd: ["补充接口性能测试"],
          techStackToAdd: ["Redis"],
          evidenceNeeded: ["压测报告"],
          writingFocus: ["说明个人职责"]
        }],
        skillPriorities: [],
        interviewFocus: []
      }
    })
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /wordprocessingml/);
  const bytes = new Uint8Array(await response.arrayBuffer());
  assert.equal(String.fromCharCode(bytes[0], bytes[1]), "PK");
});

test("exports an edited resume as a valid DOCX archive", async () => {
  const response = await fetch(`${baseUrl}/api/export-resume`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      language: "zh",
      template: "modern",
      accentColor: "#2f5f46",
      draft: {
        name: "张三",
        headline: "Java 后端工程师",
        contact: ["zhangsan@example.com", "上海"],
        summary: "具备后端接口、数据库与缓存开发经验。",
        skillGroups: [{ name: "后端", details: ["Java", "Spring Boot", "MySQL"] }],
        experience: [],
        projects: [{
          title: "订单系统",
          meta: "核心开发",
          bullets: ["负责订单接口和库存一致性设计。"]
        }],
        education: [{ title: "某大学 计算机科学与技术", meta: "2021-2025", bullets: [] }],
        organizations: [],
        additionalSections: [],
        sectionOrder: ["summary", "skills", "projects", "education"]
      }
    })
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type"), /wordprocessingml/);
  assert.match(response.headers.get("content-disposition"), /filename\*=UTF-8''/);
  const bytes = new Uint8Array(await response.arrayBuffer());
  assert.equal(String.fromCharCode(bytes[0], bytes[1]), "PK");
  assert.ok(bytes.length > 1000);
});

test("rejects an empty resume export", async () => {
  const response = await fetch(`${baseUrl}/api/export-resume`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ draft: {} })
  });
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "没有可导出的简历内容。" });
});

test("retries a transient upstream failure and records token usage", async () => {
  let calls = 0;
  const upstream = createServer((_req, res) => {
    calls += 1;
    res.setHeader("Content-Type", "application/json");
    if (calls === 1) {
      res.statusCode = 500;
      res.end(JSON.stringify({ error: "temporary" }));
      return;
    }
    res.end(JSON.stringify({
      choices: [{ message: { content: JSON.stringify({ score: 72, summary: "重试成功的诊断报告" }) } }],
      usage: { prompt_tokens: 120, completion_tokens: 40, total_tokens: 160 }
    }));
  });
  await new Promise((resolve) => upstream.listen(0, "127.0.0.1", resolve));

  process.env.AI_PROVIDER = "custom";
  process.env.CUSTOM_API_KEY = "test-key";
  process.env.CUSTOM_BASE_URL = `http://127.0.0.1:${upstream.address().port}`;
  process.env.CUSTOM_MODEL = "test-model";
  process.env.AI_MAX_RETRIES = "1";

  try {
    const response = await fetch(`${baseUrl}/api/analyze`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Idempotency-Key": randomUUID() },
      body: JSON.stringify(validPayload())
    });
    const created = await response.json();
    const completed = await waitForTask(created, 4000);
    assert.equal(completed.status, "succeeded");
    assert.equal(completed.result.demo, false);
    assert.equal(completed.result.report.summary, "重试成功的诊断报告");
    assert.equal(calls, 2);

    const statsResponse = await fetch(`${baseUrl}/api/admin/stats`, {
      headers: { Authorization: `Bearer ${process.env.ADMIN_STATS_TOKEN}` }
    });
    const stats = await statsResponse.json();
    assert.ok(stats.requests.retries >= 1);
    assert.ok(stats.requests.totalTokens >= 160);
  } finally {
    process.env.AI_PROVIDER = "custom";
    process.env.CUSTOM_API_KEY = "";
    process.env.CUSTOM_BASE_URL = "";
    process.env.CUSTOM_MODEL = "";
    await new Promise((resolve) => upstream.close(resolve));
  }
});

test("blocks production startup without an AI provider unless demo mode is explicit", () => {
  const previous = {
    nodeEnv: process.env.NODE_ENV,
    provider: process.env.AI_PROVIDER,
    allowDemo: process.env.ALLOW_DEMO_MODE
  };
  process.env.NODE_ENV = "production";
  process.env.AI_PROVIDER = "deepseek";
  process.env.DEEPSEEK_API_KEY = "";
  process.env.ALLOW_DEMO_MODE = "false";
  assert.throws(() => validateProductionConfig(), /Production startup blocked/);
  process.env.ALLOW_DEMO_MODE = "true";
  assert.doesNotThrow(() => validateProductionConfig());
  process.env.NODE_ENV = previous.nodeEnv;
  process.env.AI_PROVIDER = previous.provider;
  process.env.ALLOW_DEMO_MODE = previous.allowDemo;
});

async function waitForTask(task, timeoutMs = 2000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const response = await fetch(`${baseUrl}/api/analyze/${task.id}`, {
      headers: { "X-Task-Token": task.token }
    });
    const data = await response.json();
    if (["succeeded", "failed", "canceled"].includes(data.status)) return data;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("Analysis task did not finish in time");
}

async function readAdminStats() {
  const response = await fetch(`${baseUrl}/api/admin/stats`, {
    headers: { Authorization: `Bearer ${process.env.ADMIN_STATS_TOKEN}` }
  });
  assert.equal(response.status, 200);
  return response.json();
}

function validPayload() {
  return {
    roleCategory: "技术与互联网",
    targetRole: "软件开发",
    candidateType: "fresh",
    experienceLevel: "0",
    language: "zh",
    mode: "preview",
    resume: "应届毕业生，计算机科学与技术专业。掌握 Java、Spring Boot、MySQL 和 Redis，完成课程项目与毕业设计，负责接口开发、数据库设计、测试和项目文档。",
    jd: "招聘软件开发工程师，负责后端服务设计、接口开发和数据库优化。要求掌握 Java、Spring Boot、MySQL、Redis，理解测试、部署、性能优化和团队协作。"
  };
}
