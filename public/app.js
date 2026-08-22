const HISTORY_KEY = "resume-fit-history-v1";
const SAMPLE_RETURN_DRAFT_KEY = "resume-fit-ai-sample-return-draft-v1";
const ACTIVE_TASK_KEY = "resume-fit-ai-active-task-v1";
const MAX_HISTORY = 8;

const form = document.querySelector("#analyzeForm");
const sampleBtn = document.querySelector("#sampleBtn");
const scoreRing = document.querySelector("#scoreRing");
const scoreValue = document.querySelector("#scoreValue");
const summary = document.querySelector("#summary");
const resumePaper = document.querySelector("#resumePaper");
const printRoot = document.querySelector("#printRoot");
const fontSize = document.querySelector("#fontSize");
const fontSizeValue = document.querySelector("#fontSizeValue");
const lineHeight = document.querySelector("#lineHeight");
const printBtn = document.querySelector("#printBtn");
const fileInput = document.querySelector("#resumeFile");
const uploadZone = document.querySelector("#uploadZone");
const fileStatus = document.querySelector("#fileStatus");
const resumeCount = document.querySelector("#resumeCount");
const jdCount = document.querySelector("#jdCount");
const analysisProgress = document.querySelector("#analysisProgress");
const analysisStatus = document.querySelector("#analysisStatus");
const analysisStatusText = document.querySelector("#analysisStatusText");
const cancelAnalysisBtn = document.querySelector("#cancelAnalysisBtn");
const reportMeta = document.querySelector("#reportMeta");
const gapsLocked = document.querySelector("#gapsLocked");
const previewUpgrade = document.querySelector("#previewUpgrade");
const upgradeFullBtn = document.querySelector("#upgradeFullBtn");
const letterPreviewNotice = document.querySelector("#letterPreviewNotice");
const layoutPreviewNotice = document.querySelector("#layoutPreviewNotice");
const rewritePreviewNotice = document.querySelector("#rewritePreviewNotice");
const copyReportBtn = document.querySelector("#copyReportBtn");
const exportTargetingBtn = document.querySelector("#exportTargetingBtn");
const exportFormat = document.querySelector("#exportFormat");
const copyRewriteBtn = document.querySelector("#copyRewriteBtn");
const copyLetterBtn = document.querySelector("#copyLetterBtn");
const historyBtn = document.querySelector("#historyBtn");
const sampleBackBtn = document.querySelector("#sampleBackBtn");
const fullSampleLink = document.querySelector("#fullSampleLink");
const historyDialog = document.querySelector("#historyDialog");
const historyList = document.querySelector("#historyList");
const closeHistoryBtn = document.querySelector("#closeHistoryBtn");
const clearHistoryBtn = document.querySelector("#clearHistoryBtn");

const fields = {
  resume: document.querySelector("#resume"),
  jd: document.querySelector("#jd"),
  roleCategory: document.querySelector("#roleCategory"),
  targetRole: document.querySelector("#targetRole"),
  candidateType: document.querySelector("#candidateType"),
  experienceLevel: document.querySelector("#experienceLevel"),
  language: document.querySelector("#language")
};

const output = {
  scoreInsights: document.querySelector("#scoreInsights"),
  strengths: document.querySelector("#strengths"),
  gaps: document.querySelector("#gaps"),
  issueDetailsSection: document.querySelector("#issueDetailsSection"),
  issueDetails: document.querySelector("#issueDetails"),
  keywords: document.querySelector("#keywords"),
  actionPlan: document.querySelector("#actionPlan"),
  rewriteBullets: document.querySelector("#rewriteBullets"),
  atsTips: document.querySelector("#atsTips"),
  coverLetter: document.querySelector("#coverLetter"),
  strategySummary: document.querySelector("#strategySummary"),
  layoutChanges: document.querySelector("#layoutChanges"),
  projectDecisions: document.querySelector("#projectDecisions"),
  newProjectSection: document.querySelector("#newProjectSection"),
  newProject: document.querySelector("#newProject"),
  skillPriorities: document.querySelector("#skillPriorities"),
  interviewFocus: document.querySelector("#interviewFocus")
};

let activeMode = "preview";
let currentMode = "";
let currentReport = null;
let currentDraft = null;
let roleTaxonomy = [];
let activeTask = null;

document.querySelectorAll("button[data-mode]").forEach((button) => {
  button.addEventListener("click", () => {
    activeMode = button.dataset.mode;
  });
});

sampleBackBtn.addEventListener("click", () => {
  if (window.history.length > 1) {
    window.history.back();
    return;
  }

  window.location.replace("/");
});

fullSampleLink.addEventListener("click", saveSampleReturnDraft);

window.addEventListener("pageshow", () => {
  if (!isFullSamplePage() && roleTaxonomy.length) restoreSampleReturnDraft();
});

fields.roleCategory.addEventListener("change", () => {
  populateRoleDirections(fields.roleCategory.value);
});

fields.candidateType.addEventListener("change", () => {
  populateExperienceLevels(fields.candidateType.value);
});

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => activateTab(tab.dataset.tab));
});

document.querySelectorAll("#templatePicker .segment").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll("#templatePicker .segment").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    resumePaper.classList.remove("template-classic", "template-modern");
    resumePaper.classList.add(`template-${button.dataset.template}`);
  });
});

document.querySelectorAll("#colorPicker .color-swatch").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll("#colorPicker .color-swatch").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    resumePaper.style.setProperty("--resume-accent", button.dataset.color);
  });
});

fontSize.addEventListener("input", () => {
  fontSizeValue.value = fontSize.value;
  resumePaper.style.setProperty("--resume-font-size", `${fontSize.value}px`);
});

lineHeight.addEventListener("change", () => {
  resumePaper.style.setProperty("--resume-line-height", lineHeight.value);
});

printBtn.addEventListener("click", () => {
  if (!currentDraft) {
    showToast("请先生成一份报告。");
    return;
  }
  syncPrintRoot();
  window.print();
});

window.addEventListener("beforeprint", syncPrintRoot);

sampleBtn.addEventListener("click", () => {
  selectRole("技术与互联网", "软件开发");
  setCandidateProfile("experienced", "1-3");
  fields.resume.value = `张三
后端工程师，3 年经验

技能：Java, Spring Boot, MySQL, Redis, Docker, Linux, REST API

项目经历：
1. 电商订单系统
- 负责订单创建、支付回调、库存扣减等功能开发
- 使用 Redis 处理热点商品库存缓存
- 参与 MySQL 表结构设计和慢查询优化

2. 内部运营平台
- 开发用户管理、权限配置、数据统计接口
- 和前端联调接口，处理线上问题
- 编写接口文档和部署说明`;

  fields.jd.value = `岗位：后端开发工程师

职责：
- 负责核心业务系统后端服务设计、开发和维护
- 参与高并发场景下的性能优化和稳定性建设
- 编写清晰的接口文档，与产品、前端、测试协作交付

要求：
- 熟悉 Java、Spring Boot、MySQL、Redis
- 理解常见数据库优化、缓存、消息队列和分布式系统基础
- 有 Docker、Linux、CI/CD 或云服务部署经验优先
- 具备良好的问题排查能力和业务理解能力`;
  updateCounts();
});

fields.resume.addEventListener("input", updateCounts);
fields.jd.addEventListener("input", updateCounts);
fileInput.addEventListener("change", () => handleResumeFile(fileInput.files?.[0]));

["dragenter", "dragover"].forEach((eventName) => {
  uploadZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    uploadZone.classList.add("dragging");
  });
});

["dragleave", "drop"].forEach((eventName) => {
  uploadZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    uploadZone.classList.remove("dragging");
  });
});

uploadZone.addEventListener("drop", (event) => {
  handleResumeFile(event.dataTransfer?.files?.[0]);
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const requestedMode = activeMode;
  const meta = {
    roleCategory: fields.roleCategory.value,
    targetRole: fields.targetRole.value,
    candidateType: fields.candidateType.value,
    experienceLevel: fields.experienceLevel.value,
    language: fields.language.value,
    mode: requestedMode,
    createdAt: new Date().toISOString()
  };
  setLoading(true, 5, "正在创建分析任务...");

  try {
    const data = await createAnalysisTask({
      resume: fields.resume.value,
      jd: fields.jd.value,
      roleCategory: fields.roleCategory.value,
      targetRole: fields.targetRole.value,
      candidateType: fields.candidateType.value,
      experienceLevel: fields.experienceLevel.value,
      language: fields.language.value,
      mode: requestedMode
    });
    activeTask = { id: data.id, token: data.token, meta };
    sessionStorage.setItem(ACTIVE_TASK_KEY, JSON.stringify(activeTask));
    const result = await pollAnalysisTask(activeTask);
    completeAnalysis(result, meta);
  } catch (error) {
    summary.textContent = error.message || "生成失败，请检查输入后重试";
    showToast(error.message);
  } finally {
    setLoading(false);
  }
});

async function createAnalysisTask(payload) {
  const idempotencyKey = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  let lastError;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await fetch("/api/analyze", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(15_000)
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || "请求失败");
      return data;
    } catch (error) {
      lastError = error;
      const transient = error.name === "TimeoutError" || error.name === "TypeError";
      if (!transient || attempt === 1) throw error;
      updateAnalysisProgress(5, "网络暂时波动，正在确认任务是否已创建");
      await delay(700);
    }
  }
  throw lastError;
}

cancelAnalysisBtn.addEventListener("click", async () => {
  if (!activeTask) return;
  cancelAnalysisBtn.disabled = true;
  analysisStatusText.textContent = "正在取消任务...";
  try {
    const response = await fetch(`/api/analyze/${encodeURIComponent(activeTask.id)}`, {
      method: "DELETE",
      headers: { "X-Task-Token": activeTask.token }
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "取消失败");
    analysisStatusText.textContent = data.message || "任务已取消";
  } catch (error) {
    cancelAnalysisBtn.disabled = false;
    showToast(error.message || "取消失败，请稍后重试。");
  }
});

copyReportBtn.addEventListener("click", () => copyText(formatReport(currentReport), "报告已复制。"));
copyRewriteBtn.addEventListener("click", () => copyText(formatRewrites(currentReport), "改写建议已复制。"));
copyLetterBtn.addEventListener("click", () => copyText(currentReport?.coverLetter || "", "求职信已复制。"));
exportTargetingBtn.addEventListener("click", async () => {
  const plan = currentReport?.targetingPlan;
  if (!hasTargetingPlan(plan)) return;
  const roleName = (fields.targetRole.value || "目标岗位").replace(/[\\/:*?"<>|]/g, "-");
  const format = exportFormat.value;
  exportTargetingBtn.disabled = true;

  try {
    if (format === "docx") {
      const response = await fetch("/api/export-targeting", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ plan, targetRole: roleName })
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || "Word 导出失败");
      }
      downloadBlob(`${roleName}-岗位定向建议.docx`, await response.blob());
    } else if (format === "md") {
      downloadText(`${roleName}-岗位定向建议.md`, formatTargetingPlanMarkdown(plan), "text/markdown;charset=utf-8");
    } else {
      downloadText(`${roleName}-岗位定向建议.txt`, formatTargetingPlan(plan), "text/plain;charset=utf-8");
    }
    showToast("岗位定向建议已导出。", "success");
  } catch (error) {
    showToast(error.message);
  } finally {
    exportTargetingBtn.disabled = false;
  }
});

upgradeFullBtn.addEventListener("click", () => {
  activeMode = "full";
  form.requestSubmit();
});

historyBtn.addEventListener("click", () => {
  renderHistory();
  historyDialog.showModal();
});
closeHistoryBtn.addEventListener("click", () => historyDialog.close());
clearHistoryBtn.addEventListener("click", () => {
  localStorage.removeItem(HISTORY_KEY);
  renderHistory();
  showToast("历史记录已清除。", "success");
});

async function handleResumeFile(file) {
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) {
    showToast("文件不能超过 5MB。");
    return;
  }

  fileInput.disabled = true;
  fileStatus.textContent = `正在解析 ${file.name}...`;

  try {
    const body = new FormData();
    body.append("file", file);
    const response = await fetch("/api/parse-resume", { method: "POST", body });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "文件解析失败");

    fields.resume.value = data.text;
    fileStatus.textContent = `${data.fileName} · 已提取 ${data.text.length} 字符`;
    updateCounts();
    showToast("简历内容已提取。", "success");
  } catch (error) {
    fileStatus.textContent = "PDF、DOCX 或 TXT，最大 5MB";
    showToast(error.message);
  } finally {
    fileInput.disabled = false;
    fileInput.value = "";
  }
}

async function loadRoleOptions() {
  try {
    const response = await fetch("/api/roles");
    const data = await response.json();
    if (!response.ok || !Array.isArray(data.categories) || !data.categories.length) {
      throw new Error("职业分类加载失败");
    }

    roleTaxonomy = data.categories;
    fields.roleCategory.innerHTML = "";
    roleTaxonomy.forEach((category) => {
      const option = document.createElement("option");
      option.value = category.name;
      option.textContent = category.name;
      fields.roleCategory.appendChild(option);
    });

    fields.roleCategory.disabled = false;
    populateRoleDirections(roleTaxonomy[0].name);
    sampleBtn.disabled = false;
    document.querySelectorAll("button[data-mode]").forEach((button) => {
      button.disabled = false;
    });
    if (isFullSamplePage()) {
      await loadFullSample();
    } else {
      restoreSampleReturnDraft();
    }
  } catch (error) {
    showToast(error.message);
  }
}

function isFullSamplePage() {
  return new URLSearchParams(window.location.search).get("sample") === "full";
}

function saveSampleReturnDraft() {
  const draft = {
    resume: fields.resume.value,
    jd: fields.jd.value,
    roleCategory: fields.roleCategory.value,
    targetRole: fields.targetRole.value,
    candidateType: fields.candidateType.value,
    experienceLevel: fields.experienceLevel.value,
    language: fields.language.value,
    scrollY: window.scrollY
  };
  sessionStorage.setItem(SAMPLE_RETURN_DRAFT_KEY, JSON.stringify(draft));
}

function restoreSampleReturnDraft() {
  const raw = sessionStorage.getItem(SAMPLE_RETURN_DRAFT_KEY);
  if (!raw) return;

  try {
    const draft = JSON.parse(raw);
    selectRole(draft.roleCategory, draft.targetRole);
    setCandidateProfile(draft.candidateType, draft.experienceLevel);
    fields.language.value = draft.language === "en" ? "en" : "zh";
    fields.resume.value = typeof draft.resume === "string" ? draft.resume : "";
    fields.jd.value = typeof draft.jd === "string" ? draft.jd : "";
    updateCounts();
    window.requestAnimationFrame(() => window.scrollTo({ top: Number(draft.scrollY) || 0, behavior: "auto" }));
  } finally {
    sessionStorage.removeItem(SAMPLE_RETURN_DRAFT_KEY);
  }
}

async function loadFullSample() {
  document.body.classList.add("sample-mode");
  sampleBackBtn.hidden = false;
  summary.textContent = "正在加载完整报告样例...";

  const response = await fetch("/api/sample-report");
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "完整报告样例加载失败");

  const meta = {
    ...data.meta,
    createdAt: new Date().toISOString()
  };
  selectRole(meta.roleCategory, meta.targetRole);
  setCandidateProfile(meta.candidateType, meta.experienceLevel);
  fields.language.value = meta.language;
  activeMode = "full";
  renderReport(data.report, meta);
}

function populateRoleDirections(categoryName, preferredDirection = "") {
  const category = roleTaxonomy.find((item) => item.name === categoryName) || roleTaxonomy[0];
  fields.targetRole.innerHTML = "";
  if (!category) return;

  fields.roleCategory.value = category.name;
  category.directions.forEach((direction) => {
    const option = document.createElement("option");
    option.value = direction;
    option.textContent = direction;
    fields.targetRole.appendChild(option);
  });
  fields.targetRole.value = category.directions.includes(preferredDirection)
    ? preferredDirection
    : category.directions[0];
  fields.targetRole.disabled = false;
}

function selectRole(categoryName, direction) {
  if (!roleTaxonomy.length) return;
  populateRoleDirections(categoryName, direction);
}

function populateExperienceLevels(candidateType, preferredLevel = "") {
  const isFresh = candidateType !== "experienced";
  const options = isFresh
    ? [{ value: "0", label: "0 年" }]
    : [
        { value: "lt1", label: "1 年以内" },
        { value: "1-3", label: "1-3 年" },
        { value: "3-5", label: "3-5 年" },
        { value: "5-10", label: "5-10 年" },
        { value: "10+", label: "10 年以上" }
      ];

  fields.candidateType.value = isFresh ? "fresh" : "experienced";
  fields.experienceLevel.replaceChildren();
  options.forEach(({ value, label }) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    fields.experienceLevel.appendChild(option);
  });
  fields.experienceLevel.value = options.some((item) => item.value === preferredLevel)
    ? preferredLevel
    : options[0].value;
  fields.experienceLevel.disabled = isFresh;
}

function setCandidateProfile(candidateType, experienceLevel) {
  fields.candidateType.value = candidateType === "experienced" ? "experienced" : "fresh";
  populateExperienceLevels(fields.candidateType.value, experienceLevel);
}

function setLoading(isLoading, progress = 0, message = "") {
  form.setAttribute("aria-busy", String(isLoading));
  form.querySelectorAll("button, select, textarea, input").forEach((control) => {
    control.disabled = isLoading;
  });
  analysisProgress.hidden = !isLoading;
  analysisStatus.hidden = !isLoading;
  upgradeFullBtn.disabled = isLoading;
  cancelAnalysisBtn.disabled = !isLoading;
  if (isLoading) {
    updateAnalysisProgress(progress, message || "正在创建分析任务...");
    summary.textContent = activeMode === "preview" ? "正在快速诊断岗位匹配度..." : "正在生成完整报告并重写简历...";
    output.scoreInsights.innerHTML = "";
  }
  if (!isLoading) populateExperienceLevels(fields.candidateType.value, fields.experienceLevel.value);
}

function updateAnalysisProgress(progress, message) {
  const normalizedProgress = Math.max(0, Math.min(100, Number(progress) || 0));
  analysisProgress.style.setProperty("--analysis-progress", `${normalizedProgress}%`);
  analysisStatusText.textContent = `${message || "正在处理"} · ${normalizedProgress}%`;
}

async function pollAnalysisTask(task) {
  let networkFailures = 0;
  while (activeTask?.id === task.id) {
    try {
      const response = await fetch(`/api/analyze/${encodeURIComponent(task.id)}`, {
        headers: { "X-Task-Token": task.token },
        signal: AbortSignal.timeout(10_000)
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        sessionStorage.removeItem(ACTIVE_TASK_KEY);
        activeTask = null;
        throw new Error(data.error || "任务状态查询失败");
      }

      networkFailures = 0;
      updateAnalysisProgress(data.progress, data.message);
      if (data.status === "succeeded") {
        sessionStorage.removeItem(ACTIVE_TASK_KEY);
        activeTask = null;
        return data.result;
      }
      if (data.status === "failed" || data.status === "canceled") {
        sessionStorage.removeItem(ACTIVE_TASK_KEY);
        activeTask = null;
        throw new Error(data.error || data.message || "任务未完成");
      }
      await delay(data.status === "queued" ? 1200 : 1800);
    } catch (error) {
      if (!activeTask || error.message === "任务已取消。") throw error;
      if (error.name !== "TimeoutError" && error.name !== "TypeError") throw error;
      networkFailures += 1;
      analysisStatusText.textContent = networkFailures < 3
        ? "网络暂时波动，正在继续查询任务..."
        : "网络连接不稳定；任务仍在后台运行，请保持页面开启。";
      await delay(Math.min(5000, 1000 * networkFailures));
    }
  }
  throw new Error("任务已停止。");
}

function completeAnalysis(result, meta) {
  if (!result?.report) throw new Error("任务没有返回有效报告，请重新生成。");
  renderReport(result.report, meta);
  saveHistory(result.report, meta);
  showToast(meta.mode === "preview" ? "快速诊断已生成。" : "完整报告已生成。", "success");
}

async function resumeActiveAnalysis() {
  if (isFullSamplePage()) return;
  const raw = sessionStorage.getItem(ACTIVE_TASK_KEY);
  if (!raw) return;

  try {
    const storedTask = JSON.parse(raw);
    if (!storedTask?.id || !storedTask?.token || !storedTask?.meta) throw new Error("Invalid task state");
    activeTask = storedTask;
    activeMode = storedTask.meta.mode === "full" ? "full" : "preview";
    selectRole(storedTask.meta.roleCategory, storedTask.meta.targetRole);
    setCandidateProfile(storedTask.meta.candidateType, storedTask.meta.experienceLevel);
    fields.language.value = storedTask.meta.language === "en" ? "en" : "zh";
    setLoading(true, 5, "正在恢复未完成的任务...");
    const result = await pollAnalysisTask(storedTask);
    completeAnalysis(result, storedTask.meta);
  } catch (error) {
    sessionStorage.removeItem(ACTIVE_TASK_KEY);
    activeTask = null;
    summary.textContent = error.message || "未完成任务恢复失败";
    showToast(error.message || "未完成任务恢复失败");
  } finally {
    setLoading(false);
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function activateTab(tabName) {
  document.querySelectorAll(".tab").forEach((item) => {
    const active = item.dataset.tab === tabName;
    item.classList.toggle("active", active);
    item.setAttribute("aria-selected", String(active));
  });
  document.querySelectorAll(".tab-panel").forEach((item) => item.classList.toggle("active", item.id === tabName));
}

function renderReport(report, meta = {}) {
  currentReport = report;
  currentMode = meta.mode || "full";
  const candidateType = meta.candidateType || fields.candidateType.value;
  const experienceLevel = meta.experienceLevel || fields.experienceLevel.value;
  const score = Number(report.score || 0);
  scoreValue.textContent = score;
  scoreRing.style.background = `radial-gradient(circle at center, #fff 59%, transparent 60%), conic-gradient(var(--green) ${score * 3.6}deg, var(--line) 0deg)`;
  summary.textContent = report.summary || "已生成报告";
  renderScoreInsights(report.scoreInsights);

  renderList(output.strengths, report.strengths);
  renderList(output.gaps, report.gaps);
  renderIssueDetails(report.issueDetails);
  renderList(output.actionPlan, report.actionPlan);
  renderList(output.atsTips, report.atsTips);
  renderKeywords(report.keywords);
  renderRewrites(report.rewriteBullets);
  renderTargetingPlan(report.targetingPlan);
  output.coverLetter.textContent = report.coverLetter || "";
  currentDraft = normalizeDraft(report.resumeDraft);
  renderResume(
    currentDraft,
    meta.language || fields.language.value,
    candidateType
  );

  copyReportBtn.disabled = false;
  exportTargetingBtn.disabled = !hasTargetingPlan(report.targetingPlan);
  copyRewriteBtn.disabled = !report.rewriteBullets?.length;
  copyLetterBtn.disabled = !report.coverLetter;
  const audience = candidateType === "experienced"
    ? `社招 ${formatExperienceLevel(experienceLevel)}`
    : "应届生 0 年";
  const reportType = meta.sample ? "完整版样例" : meta.mode === "preview" ? "快速诊断" : "完整报告";
  reportMeta.textContent = `${audience} · ${reportType} · ${formatTime(meta.createdAt)}`;
  setReportAccess(currentMode === "preview");
  activateTab("overview");
}

function setReportAccess(isPreview) {
  previewUpgrade.hidden = !isPreview;
  gapsLocked.classList.toggle("show", isPreview);
  letterPreviewNotice.classList.toggle("show", isPreview);
  layoutPreviewNotice.classList.toggle("show", isPreview);
  rewritePreviewNotice.classList.toggle("show", isPreview);
  exportFormat.disabled = isPreview;
  exportTargetingBtn.disabled = isPreview || !hasTargetingPlan(currentReport?.targetingPlan);
  printBtn.disabled = isPreview;
  resumePaper.setAttribute("contenteditable", String(!isPreview));
  if (isPreview) printRoot.replaceChildren();
}

function renderScoreInsights(items = []) {
  output.scoreInsights.innerHTML = (items || [])
    .map((item) => `
      <div class="score-insight">
        <div><strong>${escapeHtml(item.dimension)}</strong><span>${Number(item.score) || 0}</span></div>
        <p>${escapeHtml(item.advice)}</p>
      </div>
    `)
    .join("");
}

function renderIssueDetails(items = []) {
  const details = Array.isArray(items) ? items : [];
  output.issueDetailsSection.hidden = details.length === 0;
  output.issueDetails.innerHTML = details
    .map((item, index) => `
      <article class="issue-detail">
        <div class="issue-detail-heading"><span>${index + 1}</span><strong>${escapeHtml(item.title)}</strong></div>
        <p>${escapeHtml(item.problem)}</p>
        ${item.evidence ? `<p class="issue-evidence"><strong>简历依据</strong>${escapeHtml(item.evidence)}</p>` : ""}
        ${renderStrategyList("修改建议", item.fixes)}
      </article>
    `)
    .join("");
}

function renderTargetingPlan(plan = {}) {
  const decisionLabels = {
    keep: "保留",
    strengthen: "强化",
    replace: "替换",
    remove: "删除"
  };
  const actionLabels = {
    move_up: "前置",
    move_down: "后移",
    expand: "展开",
    condense: "压缩",
    remove: "移除"
  };
  const priorityLabels = { must: "必须掌握", important: "重点准备", bonus: "加分项" };

  output.strategySummary.textContent = plan.strategySummary || "";
  output.layoutChanges.innerHTML = (plan.layoutChanges || [])
    .map((item) => `
      <article class="strategy-row">
        <div class="strategy-heading"><strong>${escapeHtml(item.section)}</strong><span class="strategy-badge">${escapeHtml(actionLabels[item.action] || "调整")}</span></div>
        <p>${escapeHtml(item.reason)}</p>
      </article>
    `)
    .join("");

  output.projectDecisions.innerHTML = (plan.projectDecisions || [])
    .map((item) => `
      <article class="strategy-card">
        <div class="strategy-heading">
          <strong>${escapeHtml(item.name)}</strong>
          <div class="strategy-badges"><span class="strategy-badge decision-${escapeHtml(item.decision)}">${escapeHtml(decisionLabels[item.decision] || "强化")}</span><span class="relevance-score">相关度 ${Number(item.relevance) || 0}</span></div>
        </div>
        <p>${escapeHtml(item.reason)}</p>
        ${renderStrategyList("建议新增功能", item.featuresToAdd)}
        ${renderStrategyList("建议技术栈", item.techStackToAdd)}
        ${renderStrategyList("需要留存的证据", item.evidenceNeeded)}
        ${renderStrategyList("完成后的简历重点", item.writingFocus)}
      </article>
    `)
    .join("");

  const newProject = plan.newProject || {};
  output.newProjectSection.hidden = !newProject.recommended;
  output.newProject.innerHTML = newProject.recommended
    ? `<article class="strategy-card strategy-highlight">
        <div class="strategy-heading"><strong>${escapeHtml(newProject.name || "建议项目")}</strong><span class="strategy-badge decision-strengthen">新增</span></div>
        <p>${escapeHtml(newProject.reason || "")}</p>
        ${newProject.scenario ? `<h4>使用场景</h4><p>${escapeHtml(newProject.scenario)}</p>` : ""}
        ${renderStrategyList("核心功能", newProject.coreFeatures)}
        ${renderStrategyList("技术栈", newProject.techStack)}
        ${renderStrategyList("验收物", newProject.deliverables)}
        ${renderStrategyList("完成后的简历重点", newProject.resumeFocus)}
      </article>`
    : "";

  output.skillPriorities.innerHTML = (plan.skillPriorities || [])
    .map((item) => `
      <article class="strategy-card">
        <div class="strategy-heading"><strong>${escapeHtml(item.name)}</strong><span class="strategy-badge priority-${escapeHtml(item.level)}">${escapeHtml(priorityLabels[item.level] || "重点准备")}</span></div>
        <p>${escapeHtml(item.reason)}</p>
        ${renderStrategyList("可能被问", item.interviewQuestions)}
      </article>
    `)
    .join("");

  output.interviewFocus.innerHTML = (plan.interviewFocus || [])
    .map((item) => `
      <article class="strategy-card">
        <div class="strategy-heading"><strong>${escapeHtml(item.topic)}</strong></div>
        <p>${escapeHtml(item.reason)}</p>
        ${renderStrategyList("面试问题", item.questions)}
        ${renderStrategyList("准备方式", item.preparation)}
      </article>
    `)
    .join("");
}

function renderStrategyList(title, items = []) {
  if (!Array.isArray(items) || !items.length) return "";
  const list = items.map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  return `<h4>${escapeHtml(title)}</h4><ul>${list}</ul>`;
}

function hasTargetingPlan(plan) {
  return Boolean(plan?.strategySummary || plan?.projectDecisions?.length || plan?.interviewFocus?.length);
}

function normalizeDraft(draft = {}) {
  return {
    name: draft.name || "候选人",
    headline: draft.headline || fields.targetRole.value,
    contact: Array.isArray(draft.contact) ? draft.contact : [],
    summary: draft.summary || "",
    skills: Array.isArray(draft.skills) ? draft.skills : [],
    skillGroups: normalizeSkillGroups(draft.skillGroups),
    experience: normalizeEntries(draft.experience),
    projects: normalizeEntries(draft.projects),
    education: normalizeEntries(draft.education),
    organizations: normalizeEntries(draft.organizations),
    additionalSections: normalizeAdditionalSections(draft.additionalSections),
    sectionOrder: Array.isArray(draft.sectionOrder) ? draft.sectionOrder : []
  };
}

function normalizeSkillGroups(groups) {
  if (!Array.isArray(groups)) return [];
  return groups.map((group) => ({
    name: group?.name || "",
    details: Array.isArray(group?.details) ? group.details : []
  }));
}

function normalizeAdditionalSections(sections) {
  if (!Array.isArray(sections)) return [];
  return sections.map((section) => ({
    title: section?.title || "",
    items: Array.isArray(section?.items) ? section.items : []
  }));
}

function normalizeEntries(entries) {
  if (!Array.isArray(entries)) return [];
  return entries.map((entry) => ({
    title: entry?.title || "",
    meta: entry?.meta || "",
    bullets: Array.isArray(entry?.bullets) ? entry.bullets : []
  }));
}

function renderResume(draft, language, candidateType) {
  const labels = language === "en"
    ? { summary: "PROFILE", skills: "SKILLS", experience: "EXPERIENCE", projects: "PROJECTS", education: "EDUCATION", organizations: "LEADERSHIP & ACTIVITIES" }
    : { summary: "职业概述", skills: "核心技能", experience: "工作经历", projects: "项目经历", education: "教育经历", organizations: "校园与组织经历" };
  const contact = draft.contact.map((item) => `<span>${escapeHtml(item)}</span>`).join("");
  const skills = renderSkills(draft);
  const isEarlyCareer = candidateType !== "experienced";
  const defaultOrder = isEarlyCareer
    ? ["summary", "skills", "education", "projects", "experience", "organizations", "additional"]
    : ["summary", "skills", "experience", "projects", "education", "organizations", "additional"];
  const requestedOrder = draft.sectionOrder.filter((key) => defaultOrder.includes(key));
  const sectionOrder = [...requestedOrder, ...defaultOrder.filter((key) => !requestedOrder.includes(key))];
  const sections = {
    summary: renderResumeSection(labels.summary, draft.summary ? `<p>${escapeHtml(draft.summary)}</p>` : ""),
    skills: renderResumeSection(labels.skills, skills),
    education: renderEntrySection(labels.education, draft.education),
    experience: renderEntrySection(labels.experience, draft.experience),
    projects: renderEntrySection(labels.projects, draft.projects),
    organizations: renderEntrySection(labels.organizations, draft.organizations),
    additional: renderAdditionalSections(draft.additionalSections)
  };

  resumePaper.innerHTML = `
    <header class="resume-header">
      <h1>${escapeHtml(draft.name)}</h1>
      <p class="resume-headline">${escapeHtml(draft.headline)}</p>
      <div class="resume-contact">${contact}</div>
    </header>
    ${sectionOrder.map((key) => sections[key]).join("")}
  `;
}

function renderSkills(draft) {
  if (draft.skillGroups.length) {
    return `<div class="resume-skill-groups">${draft.skillGroups
      .map((group) => {
        const details = group.details.map((item) => escapeHtml(item)).join("；");
        return `<div class="resume-skill-group"><strong>${escapeHtml(group.name)}</strong><span>${details}</span></div>`;
      })
      .join("")}</div>`;
  }
  const skills = draft.skills.map((item) => `<span>${escapeHtml(item)}</span>`).join("");
  return skills ? `<div class="resume-skills">${skills}</div>` : "";
}

function renderAdditionalSections(sections) {
  return sections
    .map((section) => {
      const items = section.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("");
      return renderResumeSection(section.title, items ? `<ul class="resume-additional-list">${items}</ul>` : "");
    })
    .join("");
}

function syncPrintRoot() {
  if (!printRoot || !currentDraft || currentMode === "preview") return;
  const printable = resumePaper.cloneNode(true);
  printable.id = "resumePaperPrint";
  printable.removeAttribute("contenteditable");
  printable.removeAttribute("title");
  printRoot.replaceChildren(printable);
}

function renderResumeSection(title, content) {
  if (!content) return "";
  return `<section class="resume-section"><h2>${escapeHtml(title)}</h2>${content}</section>`;
}

function renderEntrySection(title, entries) {
  if (!entries.length) return "";
  const content = entries
    .map((entry) => {
      const bullets = entry.bullets.map((item) => `<li>${escapeHtml(item)}</li>`).join("");
      return `
        <div class="resume-entry">
          <div class="resume-entry-heading">
            <h3>${escapeHtml(entry.title)}</h3>
            <span>${escapeHtml(entry.meta)}</span>
          </div>
          ${bullets ? `<ul>${bullets}</ul>` : ""}
        </div>
      `;
    })
    .join("");
  return renderResumeSection(title, content);
}

function renderList(node, items = []) {
  node.innerHTML = "";
  items.forEach((item) => {
    const li = document.createElement("li");
    li.textContent = item;
    node.appendChild(li);
  });
}

function renderKeywords(items = []) {
  output.keywords.innerHTML = "";
  items.forEach((item) => {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = item;
    output.keywords.appendChild(chip);
  });
}

function renderRewrites(items = []) {
  output.rewriteBullets.innerHTML = "";
  items.forEach((item) => {
    const article = document.createElement("article");
    article.className = "rewrite-item";
    article.innerHTML = `
      <p><strong>原句</strong><br>${escapeHtml(item.before || "")}</p>
      <p><strong>优化</strong><br>${escapeHtml(item.after || "")}</p>
      <p><strong>原因</strong><br>${escapeHtml(item.reason || "")}</p>
    `;
    output.rewriteBullets.appendChild(article);
  });
}

function saveHistory(report, meta) {
  const history = readHistory();
  history.unshift({ id: Date.now(), report, ...meta });
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(0, MAX_HISTORY)));
}

function readHistory() {
  try {
    const value = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

function renderHistory() {
  const history = readHistory();
  historyList.innerHTML = "";
  clearHistoryBtn.hidden = history.length === 0;

  if (!history.length) {
    const empty = document.createElement("div");
    empty.className = "history-empty";
    empty.textContent = "还没有生成过报告。";
    historyList.appendChild(empty);
    return;
  }

  history.forEach((entry) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "history-item";

    const score = document.createElement("span");
    score.className = "history-score";
    score.textContent = entry.report?.score ?? "--";

    const content = document.createElement("span");
    content.className = "history-content";
    const role = document.createElement("strong");
    role.textContent = entry.targetRole || "未命名岗位";
    const description = document.createElement("span");
    description.textContent = entry.report?.summary || "分析报告";
    content.append(role, description);

    const time = document.createElement("span");
    time.className = "history-time";
    time.textContent = formatTime(entry.createdAt);

    button.append(score, content, time);
    button.addEventListener("click", () => {
      const selection = resolveHistoryRole(entry);
      selectRole(selection.category, selection.direction);
      const candidateType = entry.candidateType || (entry.report?.resumeDraft?.experience?.length ? "experienced" : "fresh");
      setCandidateProfile(candidateType, entry.experienceLevel || (candidateType === "fresh" ? "0" : "1-3"));
      fields.language.value = entry.language || "zh";
      activeMode = entry.mode || "full";
      renderReport(entry.report, entry);
      historyDialog.close();
    });
    historyList.appendChild(button);
  });
}

function formatReport(report) {
  if (!report) return "";
  return [
    `匹配评分：${report.score}`,
    report.summary,
    `\n评分建议\n${(report.scoreInsights || []).map((item) => `- ${item.dimension} ${item.score}：${item.advice}`).join("\n")}`,
    `\n优势\n${formatList(report.strengths)}`,
    `\n缺口\n${formatList(report.gaps)}`,
    `\n问题详解\n${formatIssueDetails(report.issueDetails)}`,
    `\n关键词\n${(report.keywords || []).join("、")}`,
    `\n行动清单\n${formatList(report.actionPlan)}`,
    `\nATS 建议\n${formatList(report.atsTips)}`,
    hasTargetingPlan(report.targetingPlan) ? `\n${formatTargetingPlan(report.targetingPlan)}` : "",
    `\n求职信\n${report.coverLetter || ""}`
  ].join("\n");
}

function formatIssueDetails(items = []) {
  return (items || [])
    .map((item, index) => {
      const fixes = (item.fixes || []).map((fix) => `  - ${fix}`).join("\n");
      return `${index + 1}. ${item.title}\n问题：${item.problem}\n简历依据：${item.evidence || "未提供"}\n修改建议：\n${fixes}`;
    })
    .join("\n\n");
}

function formatTargetingPlan(plan = {}) {
  if (!hasTargetingPlan(plan)) return "";
  const decisionLabels = { keep: "保留", strengthen: "强化", replace: "替换", remove: "删除" };
  const actionLabels = { move_up: "前置", move_down: "后移", expand: "展开", condense: "压缩", remove: "移除" };
  const priorityLabels = { must: "必须掌握", important: "重点准备", bonus: "加分项" };
  const lines = ["岗位定向改造建议", "========================", "", plan.strategySummary || ""];

  lines.push("", "一、排版与栏目取舍", "------------------------");
  (plan.layoutChanges || []).forEach((item) => {
    lines.push(`- ${item.section} [${actionLabels[item.action] || "调整"}]：${item.reason}`);
  });

  lines.push("", "二、项目取舍与改造", "------------------------");
  (plan.projectDecisions || []).forEach((item) => {
    lines.push("", `项目：${item.name}`, `决策：${decisionLabels[item.decision] || "强化"} | 相关度：${item.relevance || 0}`);
    lines.push(item.reason || "");
    appendTextList(lines, "建议新增功能", item.featuresToAdd);
    appendTextList(lines, "建议技术栈", item.techStackToAdd);
    appendTextList(lines, "需要留存的证据", item.evidenceNeeded);
    appendTextList(lines, "完成后的简历重点", item.writingFocus);
  });

  const newProject = plan.newProject || {};
  if (newProject.recommended) {
    lines.push("", "三、建议新增项目", "------------------------", "", `项目：${newProject.name || "建议项目"}`, newProject.reason || "");
    if (newProject.scenario) lines.push("", "使用场景：", newProject.scenario);
    appendTextList(lines, "核心功能", newProject.coreFeatures);
    appendTextList(lines, "技术栈", newProject.techStack);
    appendTextList(lines, "验收物", newProject.deliverables);
    appendTextList(lines, "完成后的简历重点", newProject.resumeFocus);
  }

  lines.push("", "四、技能与面试准备", "------------------------");
  (plan.skillPriorities || []).forEach((item) => {
    lines.push("", `${item.name} [${priorityLabels[item.level] || "重点准备"}]`, item.reason || "");
    appendTextList(lines, "可能被问", item.interviewQuestions);
  });
  (plan.interviewFocus || []).forEach((item) => {
    lines.push("", `面试主题：${item.topic}`, item.reason || "");
    appendTextList(lines, "面试问题", item.questions);
    appendTextList(lines, "准备方式", item.preparation);
  });
  lines.push("", "注意：新功能、新技术和新项目需实际完成并留存证据后，才能作为已完成经历写入简历。", "");
  return lines.join("\n");
}

function formatTargetingPlanMarkdown(plan = {}) {
  if (!hasTargetingPlan(plan)) return "";
  const decisionLabels = { keep: "保留", strengthen: "强化", replace: "替换", remove: "删除" };
  const actionLabels = { move_up: "前置", move_down: "后移", expand: "展开", condense: "压缩", remove: "移除" };
  const priorityLabels = { must: "必须掌握", important: "重点准备", bonus: "加分项" };
  const lines = ["# 岗位定向改造建议", "", plan.strategySummary || "", "", "## 排版与栏目取舍"];

  (plan.layoutChanges || []).forEach((item) => {
    lines.push(`- **${item.section} · ${actionLabels[item.action] || "调整"}**：${item.reason}`);
  });
  lines.push("", "## 项目取舍与改造");
  (plan.projectDecisions || []).forEach((item) => {
    lines.push("", `### ${item.name} · ${decisionLabels[item.decision] || "强化"} · 相关度 ${item.relevance || 0}`, "", item.reason || "");
    appendMarkdownList(lines, "建议新增功能", item.featuresToAdd);
    appendMarkdownList(lines, "建议技术栈", item.techStackToAdd);
    appendMarkdownList(lines, "需要留存的证据", item.evidenceNeeded);
    appendMarkdownList(lines, "完成后的简历重点", item.writingFocus);
  });

  const newProject = plan.newProject || {};
  if (newProject.recommended) {
    lines.push("", "## 建议新增项目", "", `### ${newProject.name || "建议项目"}`, "", newProject.reason || "");
    if (newProject.scenario) lines.push("", "**使用场景**", "", newProject.scenario);
    appendMarkdownList(lines, "核心功能", newProject.coreFeatures);
    appendMarkdownList(lines, "技术栈", newProject.techStack);
    appendMarkdownList(lines, "验收物", newProject.deliverables);
    appendMarkdownList(lines, "完成后的简历重点", newProject.resumeFocus);
  }

  lines.push("", "## 技能与面试准备");
  (plan.skillPriorities || []).forEach((item) => {
    lines.push("", `### ${item.name} · ${priorityLabels[item.level] || "重点准备"}`, "", item.reason || "");
    appendMarkdownList(lines, "可能被问", item.interviewQuestions);
  });
  (plan.interviewFocus || []).forEach((item) => {
    lines.push("", `### ${item.topic}`, "", item.reason || "");
    appendMarkdownList(lines, "面试问题", item.questions);
    appendMarkdownList(lines, "准备方式", item.preparation);
  });
  lines.push("", "> 新功能、新技术和新项目需实际完成并留存证据后，才能作为已完成经历写入简历。", "");
  return lines.join("\n");
}

function appendMarkdownList(lines, title, items = []) {
  if (!Array.isArray(items) || !items.length) return;
  lines.push("", `**${title}**`, "", ...items.map((item) => `- ${item}`));
}

function appendTextList(lines, title, items = []) {
  if (!Array.isArray(items) || !items.length) return;
  lines.push("", `${title}：`, ...items.map((item, index) => `${index + 1}. ${item}`));
}

function formatRewrites(report) {
  return (report?.rewriteBullets || [])
    .map((item, index) => `${index + 1}. 原句：${item.before || ""}\n优化：${item.after || ""}\n原因：${item.reason || ""}`)
    .join("\n\n");
}

function formatList(items = []) {
  return items.map((item) => `- ${item}`).join("\n");
}

async function copyText(text, successMessage) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
  }
  showToast(successMessage, "success");
}

function downloadText(fileName, content, type) {
  downloadBlob(fileName, new Blob(["\uFEFF", content], { type }));
}

function downloadBlob(fileName, blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function updateCounts() {
  resumeCount.value = `${fields.resume.value.length} / 6000`;
  jdCount.value = `${fields.jd.value.length} / 3000`;
}

function formatTime(value) {
  if (!value) return "刚刚";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "刚刚";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function formatExperienceLevel(value) {
  const labels = {
    lt1: "1 年以内",
    "1-3": "1-3 年",
    "3-5": "3-5 年",
    "5-10": "5-10 年",
    "10+": "10 年以上"
  };
  return labels[value] || "年限未填写";
}

function resolveHistoryRole(entry) {
  const directCategory = roleTaxonomy.find((item) => item.name === entry.roleCategory);
  if (directCategory?.directions.includes(entry.targetRole)) {
    return { category: directCategory.name, direction: entry.targetRole };
  }

  const value = entry.targetRole || "";
  if (/AI|人工智能|数据/.test(value)) return { category: "数据与人工智能", direction: "数据分析" };
  if (/财务|会计|审计/.test(value)) return { category: "财务、金融与法律", direction: "会计与核算" };
  return { category: "技术与互联网", direction: "软件开发" };
}

function showToast(message, type = "error") {
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 3200);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

updateCounts();
setCandidateProfile("fresh", "0");
loadRoleOptions().then(resumeActiveAnalysis);
