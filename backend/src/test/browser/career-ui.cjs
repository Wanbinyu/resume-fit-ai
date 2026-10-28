// Run only against a disposable local Demo backend. Creates an isolated test account.
// PLAYWRIGHT_MODULE_PATH, CHROME_PATH and BASE_URL may be supplied by the host.
const { chromium } = require(
  process.env.PLAYWRIGHT_MODULE_PATH || "playwright",
);
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs");
const base = process.env.BASE_URL || "http://127.0.0.1:18080";
if (!["127.0.0.1", "localhost", "[::1]"].includes(new URL(base).hostname))
  throw new Error(
    "Use a disposable local Demo instance, not a public or production server.",
  );
const outputDir = path.resolve(__dirname, "../../../target/ui-qa");
const passed = [];
const check = (name, ok) => {
  assert.ok(ok, name);
  passed.push(name);
};
(async () => {
  const browser = await chromium.launch({
    ...(process.env.CHROME_PATH
      ? { executablePath: process.env.CHROME_PATH }
      : {}),
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1365, height: 1000 },
  });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const username = "qa_ui_" + Date.now().toString(36);
  await page.goto(base + "/job-assistant.html");
  await page.locator("#authToggle").click();
  await page.locator("#authForm [name=displayName]").fill("前端隔离验收");
  await page.locator("#authForm [name=username]").fill(username);
  await page
    .locator("#authForm [name=password]")
    .fill("Qa!" + Date.now() + "Demo");
  await page.locator("#authSubmit").click();
  await page.locator("#authLayer").waitFor({ state: "hidden" });
  await page.waitForFunction(() =>
    document.querySelector("#pageMessage").textContent.includes("资料已更新"),
  );
  check(
    "register, authenticated empty state",
    await page.locator("#startAgent").isDisabled(),
  );
  async function request(url, method = "GET", body) {
    return page.evaluate(
      async ({ url, method, body }) => {
        const r = await fetch(url, {
          method,
          headers: {
            "Content-Type": "application/json",
            Authorization:
              "Bearer " + localStorage.getItem("resume-fit-user-token"),
          },
          ...(body ? { body: JSON.stringify(body) } : {}),
        });
        const p = await r.json();
        if (!r.ok || !p.success) throw new Error(JSON.stringify(p));
        return p.data;
      },
      { url, method, body },
    );
  }
  const content =
    "教育背景：计算机本科\n技能：Java、Spring Boot、MySQL、Redis、Python、RAG\n项目经历：开发简历分析平台，负责后端接口与模型接入\n实现岗位关键词检索和评估，完成自动化验证\n交付结果：上线真实功能，优化用户操作体验\n" +
    "负责项目开发和需求分析，结果有可验证的接口交付。".repeat(6);
  const resume = await request("/api/resumes", "POST", {
    title: "前端验收简历",
    content,
    sourceFileName: "qa.txt",
  });
  const version = await request(`/api/resumes/${resume.id}/versions`, "POST", {
    title: "前端验收定制版",
    content: content + "\n独立完成 Spring AI 应用工具注册。",
    source: "MANUAL",
  });
  await page.locator("#refresh").click();
  await page.waitForFunction(
    (id) => document.querySelector("#versionSelect").value === String(id),
    version.id,
  );
  check(
    "latest saved version is selectable",
    (await page.locator("#versionSelect").inputValue()) === String(version.id),
  );
  await page.locator("#versionSelect").selectOption("");
  await page.locator("#refresh").click();
  await page.waitForFunction(
    () =>
      !document.querySelector("#refresh").disabled &&
      !document.querySelector("#startAgent").disabled,
  );
  check(
    "explicit original resume selection survives refresh",
    (await page.locator("#versionSelect").inputValue()) === "",
  );
  await page.locator("#versionSelect").selectOption(String(version.id));
  const profile = page.locator("#profileForm");
  await profile.locator("[name=targetRoles]").fill("AI 应用开发，Java 后端");
  await profile.locator("[name=preferredCities]").fill("武汉，上海");
  await profile.locator("[name=salaryMin]").fill("12000");
  await profile.locator("[name=salaryMax]").fill("18000");
  await profile.locator("[name=priorities]").fill("成长空间，工作时间");
  await profile.locator("button[type=submit]").click();
  await page.waitForFunction(() =>
    document
      .querySelector("#profileMessage")
      .textContent.includes("偏好已保存"),
  );
  const savedProfile = await request("/api/career/profile");
  check(
    "profile list parsing and salary contract",
    savedProfile.targetRoles.length === 2 && savedProfile.salaryMin === 12000,
  );
  await profile.locator("[name=notes]").fill("正在编辑的旧版本");
  await request("/api/career/profile", "PUT", {
    ...savedProfile,
    notes: "另一页面的新修改",
  });
  await page.locator("#refresh").click();
  await page.waitForFunction(
    () => !document.querySelector("#refresh").disabled,
  );
  await profile.locator("button[type=submit]").click();
  await page.waitForFunction(() =>
    document.querySelector("#profileMessage").classList.contains("error"),
  );
  check(
    "revision conflict does not silently overwrite",
    (await request("/api/career/profile")).notes === "另一页面的新修改",
  );
  await page.locator("#reloadProfile").click();
  await page.waitForFunction(
    () =>
      document.querySelector("#profileForm [name=notes]").value ===
      "另一页面的新修改",
  );
  check("explicit conflict recovery loads saved revision", true);
  await page.locator("#companyEditor summary").click();
  const company = page.locator("#companyForm");
  await company
    .locator("[name=name]")
    .fill("验收公司 <img src=x onerror=alert(1)>");
  await company.locator("[name=industry]").fill("企业软件");
  await company.locator("[name=website]").fill("https://example.com");
  for (let i = 0; i < 2; i++) {
    await page.locator("#addEvidence").click();
    const row = page.locator(".evidence-row").nth(i);
    await row.locator("[data-field=type]").selectOption(i ? "OPINION" : "FACT");
    await row
      .locator("[data-field=category]")
      .selectOption(i ? "WORKLIFE" : "IDENTITY");
    await row
      .locator("[data-field=title]")
      .fill(i ? "员工主观看法" : "公司主体信息");
    await row
      .locator("[data-field=content]")
      .fill(
        i ? "此为未核实的主观感受。" : "此处仅作隔离测试，非真实企业信息。",
      );
    await row
      .locator("[data-field=sourceUrl]")
      .fill("https://example.com/evidence");
  }
  await company.locator("button[type=submit]").click();
  await page.waitForFunction(
    () => document.querySelectorAll("#companyList .record").length === 1,
  );
  check(
    "company evidence renders escaped text and no invented score",
    (await page.locator("#companyList img").count()) === 0 &&
      (await page.locator("#companyList").innerText()).includes(
        "信誉信息待核实",
      ),
  );
  await page.locator("[data-action=edit-company]").click();
  await page.waitForFunction(
    () => document.querySelectorAll("#evidenceRows .evidence-row").length === 2,
  );
  check("editing company preserves all evidence", true);
  await page.locator("#resetCompany").click();
  await page.locator("#jobs .editor summary").click();
  const job = page.locator("#jobForm");
  await job.locator("[name=title]").fill("AI 应用开发工程师");
  await job
    .locator("[name=companyId]")
    .selectOption({ label: "验收公司 <img src=x onerror=alert(1)>" });
  await job.locator("[name=location]").fill("武汉");
  await job
    .locator("[name=description]")
    .fill(
      "熟悉 Java Spring Boot MySQL Redis Python RAG，负责模型工具调用、检索和接口开发。",
    );
  await job.locator("[name=sourceUrl]").fill("https://example.com/jobs/qa");
  await job.locator("button[type=submit]").click();
  await page.waitForFunction(
    () => document.querySelectorAll("#jobList .record").length === 1,
  );
  check("job is saved through real API", true);
  await page.locator("[data-action=archive-job]").click();
  await page.waitForFunction(
    () =>
      document.querySelector("[data-action=archive-job]")?.textContent ===
      "恢复收藏",
  );
  check(
    "archived job remains visible and can restore",
    await page.locator("[data-action=follow-job]").isDisabled(),
  );
  await page.locator("[data-action=archive-job]").click();
  await page.waitForFunction(
    () =>
      document.querySelector("[data-action=archive-job]")?.textContent ===
      "归档",
  );
  await page.locator("[data-action=follow-job]").click();
  await page.locator("#applicationEditor").waitFor({ state: "visible" });
  await page.locator("#stageSelect").selectOption("INTERVIEW");
  await page.locator("#applicationForm [name=notes]").fill("准备项目演练");
  await page.locator("#applicationForm button[type=submit]").click();
  await page.waitForFunction(() =>
    document.querySelector("#applicationList").textContent.includes("面试中"),
  );
  check("followup create and update stage", true);
  await page.route("**/api/career/jobs?*", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({ success: false, message: "禁止访问测试" }),
    }),
  );
  await page.locator("#refresh").click();
  await page.waitForFunction(() =>
    document.querySelector("#pageMessage").textContent.includes("禁止访问测试"),
  );
  check(
    "403 does not clear auth",
    (await page.locator("#authLayer").isHidden()) &&
      (await page.evaluate(() =>
        Boolean(localStorage.getItem("resume-fit-user-token")),
      )),
  );
  await page.unroute("**/api/career/jobs?*");
  await page.locator("#refresh").click();
  await page.waitForFunction(
    () =>
      !document.querySelector("#refresh").disabled &&
      !document.querySelector("#startAgent").disabled,
  );
  await page.locator("[data-action=analyze-job]").click();
  const postPromise = page.waitForRequest(
    (r) =>
      r.url().endsWith("/api/career/assistant/tasks") && r.method() === "POST",
  );
  await page.locator("#startAgent").click();
  const post = await postPromise;
  const body = post.postDataJSON();
  check(
    "assistant request freezes selected saved version and career job",
    body.versionId === version.id && body.intent === "MATCH" && body.jobId > 0,
  );
  await page.waitForFunction(
    () => !document.querySelector("#startAgent").disabled,
    {},
    { timeout: 45000 },
  );
  check(
    "real demo task returns separated quality and keyword results",
    (await page.locator("#agentResult").innerText()).includes(
      "简历完整度与表达质量",
    ) &&
      (await page.locator("#agentResult").innerText()).includes("关键词覆盖"),
  );
  check(
    "tool trace is visible",
    (await page.locator("#agentResult").innerText()).includes("工具执行过程"),
  );
  fs.mkdirSync(outputDir, { recursive: true });
  await page.screenshot({
    path: path.join(outputDir, "desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  check(
    "mobile has no horizontal overflow",
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  );
  await page.screenshot({
    path: path.join(outputDir, "mobile.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 1365, height: 1000 });
  await page.route("**/api/career/assistant/tasks", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: { id: 999999, status: "PENDING" },
      }),
    }),
  );
  await page.route("**/api/analysis/tasks/999999", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: {
          id: 999999,
          status:
            route.request().method() === "DELETE" ? "CANCELLED" : "RUNNING",
          message: "隔离取消流程",
          result: { agentTrace: [] },
        },
      }),
    }),
  );
  await page.locator("#startAgent").click();
  await page.waitForFunction(
    () => !document.querySelector("#cancelAgent").disabled,
  );
  await page.reload();
  await page.waitForFunction(() =>
    document
      .querySelector("#agentMessage")
      .textContent.includes("已恢复本账号任务"),
  );
  check(
    "refresh validates task ownership and restores monitoring",
    (await page.locator("#resumeAgent").isVisible()) &&
      (await page.locator("#startAgent").isDisabled()),
  );
  await page.locator("#resumeAgent").click();
  await page.waitForFunction(
    () => !document.querySelector("#cancelAgent").disabled,
  );
  await page.locator("#cancelAgent").click();
  await page.waitForFunction(
    () => !document.querySelector("#startAgent").disabled,
  );
  check(
    "cancellation restores controls",
    await page.locator("#cancelAgent").isDisabled(),
  );
  await page.route("**/api/analysis/tasks/999998", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({
        success: false,
        message: "不可读的其他账号任务",
        data: { summary: "NEVER_DISPLAY_OTHER_ACCOUNT_DATA" },
      }),
    }),
  );
  await page.evaluate(() =>
    sessionStorage.setItem("resume-fit-career-active-task", "999998"),
  );
  await page.reload();
  await page.waitForFunction(() =>
    document
      .querySelector("#agentMessage")
      .textContent.includes("不属于当前账号"),
  );
  check(
    "recovery rejects another account task without clearing login or showing its content",
    (await page.locator("#authLayer").isHidden()) &&
      (await page.evaluate(
        () =>
          sessionStorage.getItem("resume-fit-career-active-task") === null &&
          !document.body.textContent.includes(
            "NEVER_DISPLAY_OTHER_ACCOUNT_DATA",
          ),
      )),
  );

  // The old workbench must continue to work after extracting its inline assets.
  const otherResume = await request("/api/resumes", "POST", {
    title: "第二份隔离验收简历",
    content: "本科，Java 后端开发。负责项目接口和 MySQL 数据库设计。",
    sourceFileName: "other.txt",
  });
  await page.goto(base + "/workspace.html");
  await page.locator("#login").waitFor({ state: "hidden" });
  await page
    .locator(`#resumeList button[onclick="selectResume(${resume.id})"]`)
    .click();
  await page.waitForFunction(
    (id) =>
      document.querySelector("#analysisVersionSelect").value === String(id),
    version.id,
  );
  check(
    "workspace external resources and new navigation are present",
    (await page.locator('a[href="/job-assistant.html"]').count()) === 1,
  );
  check(
    "workspace defaults to saved version of selected resume",
    (await page.locator("#analysisVersionSelect").inputValue()) ===
      String(version.id),
  );
  await page
    .locator("#reports")
    .evaluate((el) => (el.closest("details").open = true));
  const assistantReport = page
    .locator("#reports .report-item")
    .filter({ hasText: "求职综合分析" })
    .first();
  await assistantReport.waitFor();
  check(
    "assistant report history does not mislabel legacy zero as match coverage",
    !(await assistantReport.locator("strong").innerText()).includes(
      "关键词覆盖率",
    ),
  );
  await assistantReport.locator("button").click();
  await page.waitForFunction(() =>
    document
      .querySelector("#assistantReportContent")
      .textContent.includes("公司信息与核实问题"),
  );
  check(
    "workspace complete assistant report reuses the shared renderer",
    await page.locator("#assistantReportPanel").isVisible(),
  );
  await page
    .locator("#versions")
    .evaluate((el) => (el.closest("details").open = true));
  await page.locator(`[onclick="editVersion(${version.id})"]`).click();
  await page
    .locator("#versionContent")
    .fill("尚未保存的私有编辑内容，不应进入本次分析。");
  const workspacePost = page.waitForRequest(
    (r) => r.url().endsWith("/api/analysis/tasks") && r.method() === "POST",
  );
  await page.locator("#reviewResumeBtn").click();
  const workspaceBody = (await workspacePost).postDataJSON();
  check(
    "workspace excludes unsaved editor text and binds versionId",
    workspaceBody.versionId === version.id &&
      workspaceBody.resumeId === resume.id,
  );
  await page.waitForFunction(
    () => !document.querySelector("#reviewResumeBtn").disabled,
    {},
    { timeout: 45000 },
  );
  check(
    "existing resume-only analysis renders after structural extraction",
    (await page.locator("#analysisError").innerText()).includes("完成"),
  );
  await page.route("**/api/analysis/tasks", (route) =>
    route.request().method() === "POST"
      ? route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            success: true,
            data: { id: 888888, status: "PENDING" },
          }),
        })
      : route.continue(),
  );
  let workspaceCancelled = false;
  await page.route("**/api/analysis/tasks/888888", (route) => {
    if (route.request().method() === "DELETE") workspaceCancelled = true;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: {
          id: 888888,
          status: workspaceCancelled ? "CANCELLED" : "RUNNING",
          message: "隔离工作台任务",
          result: {},
        },
      }),
    });
  });
  await page.locator("#reviewResumeBtn").click();
  await page.waitForFunction(
    () => !document.querySelector("#cancelAgentBtn").disabled,
  );
  await page
    .locator(`#resumeList button[onclick="selectResume(${otherResume.id})"]`)
    .click();
  check(
    "workspace blocks resume switching during a task",
    (await page.locator("#analysisError").innerText()).includes("再切换简历"),
  );
  await page.locator("#cancelAgentBtn").click();
  await page.waitForFunction(
    () => !document.querySelector("#reviewResumeBtn").disabled,
  );
  await page
    .locator(`#resumeList button[onclick="selectResume(${otherResume.id})"]`)
    .click();
  await page.waitForFunction(
    (id) =>
      document
        .querySelector("#resumeList button.active")
        ?.getAttribute("onclick") === `selectResume(${id})`,
    otherResume.id,
  );
  check("workspace cancellation releases task and resume selection", true);
  await page.route("**/api/jobs", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({ success: false, message: "隔离拒绝访问测试" }),
    }),
  );
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.locator("#refreshBtn").click();
  await page.waitForTimeout(200);
  check(
    "workspace 403 retains login",
    await page
      .locator("#login")
      .evaluate((el) => el.classList.contains("hidden")),
  );
  await page.unroute("**/api/jobs");
  await page.setViewportSize({ width: 390, height: 844 });
  check(
    "workspace stylesheet preserves mobile layout",
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.setViewportSize({ width: 1365, height: 1000 });
  const workspaceTab = await context.newPage();
  await workspaceTab.goto(base + "/workspace.html");
  await workspaceTab.locator("#login").waitFor({ state: "hidden" });
  await workspaceTab
    .locator("#versionContent")
    .evaluate((el) => (el.closest("details").open = true));
  await workspaceTab
    .locator("#versionContent")
    .fill("此内容应在跨标签退出时清空");
  await page.goto(base + "/job-assistant.html");
  await page.waitForFunction(
    () => !document.querySelector("#refresh").disabled,
  );
  await page.locator("#logout").click();
  await page.locator("#authLayer").waitFor({ state: "visible" });
  check(
    "logout removes private records and token",
    (await page.locator("#companyList .record").count()) === 0 &&
      (await page.evaluate(
        () => !localStorage.getItem("resume-fit-user-token"),
      )),
  );
  await workspaceTab.locator("#login:not(.hidden)").waitFor();
  check(
    "cross-tab logout clears workbench private data",
    (await workspaceTab.locator("#versions .version").count()) === 0 &&
      (await workspaceTab.locator("#versionContent").inputValue()) === "",
  );
  check("no frontend uncaught errors", errors.length === 0);
  const report = { passed, errors, screenshots: outputDir };
  fs.writeFileSync(
    path.join(outputDir, "career-ui-report.json"),
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
