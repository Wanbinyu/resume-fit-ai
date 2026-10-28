import assert from "node:assert/strict";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";
const data = new Map();
globalThis.localStorage = {
  getItem: (k) => data.get(k) || null,
  setItem: (k, v) => data.set(k, v),
  removeItem: (k) => data.delete(k),
};
const frontend = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../main/resources/static/assets/job-assistant",
);
const {
  ApiClient,
  safeHttps,
  sourceUrl,
  splitList,
  optionalNumber,
  salaryRange,
} = await import(pathToFileURL(path.join(frontend, "api.js")));
const { esc, renderAgentResult, evidenceRow, renderCompanies } = await import(
  pathToFileURL(path.join(frontend, "views.js"))
);
const passed = [];
const check = (name, test) => {
  test();
  passed.push(name);
};
check("unknown salary is null, zero remains a legitimate value", () => {
  assert.equal(optionalNumber(""), null);
  assert.equal(optionalNumber("0"), 0);
});
check("salary rejects decimals, Infinity, negatives and unsafe amounts", () => {
  for (const x of ["1.5", "Infinity", "-1", "2000001", "NaN"])
    assert.throws(() => optionalNumber(x));
});
check("salary range validates order with independent nullable bounds", () => {
  assert.throws(() => salaryRange({ salaryMin: "2", salaryMax: "1" }));
  assert.deepEqual(salaryRange({ salaryMin: "", salaryMax: "2" }), {
    salaryMin: null,
    salaryMax: 2,
  });
});
check("list normalization deduplicates and enforces limits", () => {
  assert.deepEqual(splitList("Java，Java, Python\nSQL", 3, 8, "技能"), [
    "Java",
    "Python",
    "SQL",
  ]);
  assert.throws(() => splitList("ab,cd", 1, 2, "技能"));
  assert.throws(() => splitList("abc", 1, 2, "技能"));
});
check(
  "URLs reject unsafe schemes, credentials, private and malformed hosts",
  () => {
    for (const url of [
      "javascript:alert(1)",
      "data:text/html,test",
      "http://example.com",
      "https://u:p@example.com",
      "https://127.0.0.1",
      "https://192.168.0.1",
      "https://172.16.0.1",
      "https://localhost",
      "https://host.local",
      "not URL",
    ])
      assert.equal(safeHttps(url), "");
    assert.equal(
      safeHttps("https://example.com/path"),
      "https://example.com/path",
    );
  },
);
check("optional source differs from invalid nonempty input", () => {
  assert.equal(sourceUrl(""), "");
  assert.throws(() => sourceUrl("http://example.com"));
});
check("HTML output escapes both text and attribute contexts", () => {
  assert.equal(esc('<img a="\'&">'), "&lt;img a=&quot;&#39;&amp;&quot;&gt;");
  assert.ok(!evidenceRow({ title: '"><img src=x>' }).includes("<img"));
});
check("unknown company score is explicitly unscored", () => {
  const html = renderCompanies([
    {
      id: 1,
      name: "公司",
      evidence: [],
      evidenceAssessment: { reputationScore: null },
    },
  ]);
  assert.ok(html.includes("信誉信息待核实"));
  assert.ok(!html.includes("/ 100"));
});
check(
  "assistant does not display legacy placeholder zero as matching score",
  () => {
    const html = renderAgentResult({
      status: "SUCCEEDED",
      result: {
        score: 0,
        matching: {},
        assistant: { resumeQuality: { score: 80 } },
      },
    });
    assert.ok(!html.includes("目标岗位关键词覆盖率"));
    assert.ok(html.includes("80"));
  },
);
check(
  "unknown job coverage remains unknown and extreme strings are escaped",
  () => {
    const html = renderAgentResult({
      result: {
        assistant: {
          jobMatches: [{ title: "<script>bad</script>", coverage: null }],
        },
      },
    });
    assert.ok(html.includes("暂无法计算覆盖率"));
    assert.ok(!html.includes("<script>"));
  },
);
check("missing and partial task result shapes are safe", () => {
  for (const x of [
    {},
    { result: {} },
    { result: { assistant: { companyEvidence: [], jobMatches: [] } } },
  ])
    assert.ok(renderAgentResult(x));
});
let expired = 0;
const api = new ApiClient(() => expired++);
api.setToken("qa-token");
globalThis.fetch = async () => ({
  status: 403,
  ok: false,
  json: async () => ({ success: false, message: "denied" }),
});
await assert.rejects(api.request("/fake"), (e) => e.status === 403);
assert.equal(api.token, "qa-token");
assert.equal(expired, 0);
passed.push("403 retains current authentication");
globalThis.fetch = async () => ({
  status: 409,
  ok: false,
  json: async () => ({ success: false, message: "conflict" }),
});
await assert.rejects(
  api.request("/fake"),
  (e) => e.status === 409 && e.message.includes("避免覆盖"),
);
passed.push("409 communicates optimistic concurrency recovery");
let release;
globalThis.fetch = () => new Promise((r) => (release = r));
const old = api.request("/fake");
api.setToken("new-account");
release({
  status: 200,
  ok: true,
  json: async () => ({ success: true, data: { private: "old" } }),
});
await assert.rejects(old, (e) => e.name === "AbortError");
passed.push(
  "old account responses never return private data after account change",
);
globalThis.fetch = async () => ({
  status: 401,
  ok: false,
  json: async () => ({}),
});
await assert.rejects(api.request("/fake"), (e) => e.status === 401);
assert.equal(expired, 1);
assert.equal(api.token, "");
passed.push("401 clears token and expires session exactly once");
console.log(JSON.stringify({ passed, count: passed.length }, null, 2));
