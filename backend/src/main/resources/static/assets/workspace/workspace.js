const state = {
  token: localStorage.getItem("resume-fit-user-token") || "",
  resumes: [],
  jobs: [],
  versions: [],
  editingId: null,
  authMode: "login",
  authEpoch: 0,
  baseEpoch: 0,
  resumeEpoch: 0,
};
const $ = (id) => document.getElementById(id);
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>'"]/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[
        c
      ],
  );
const num = (v) => Number(v || 0).toLocaleString("zh-CN");

const panelStorageKey = "resume-fit-workspace-panels-v1";
function rememberPanel(panel) {
  try {
    const saved = JSON.parse(localStorage.getItem(panelStorageKey) || "{}");
    saved[panel.id] = panel.open;
    localStorage.setItem(panelStorageKey, JSON.stringify(saved));
  } catch {}
}
function openPanel(id, scroll = false) {
  const panel = $(id);
  if (!panel) return;
  panel.open = true;
  rememberPanel(panel);
  if (scroll) panel.scrollIntoView({ behavior: "smooth", block: "start" });
}
function setupPanels() {
  let saved = {};
  try {
    saved = JSON.parse(localStorage.getItem(panelStorageKey) || "{}") || {};
  } catch {}
  document.querySelectorAll("details[data-panel]").forEach((panel) => {
    if (typeof saved[panel.id] === "boolean") panel.open = saved[panel.id];
    panel.addEventListener("toggle", () => rememberPanel(panel));
  });
}
function resetAnalysisPanels() {
  state.analysisRenderEpoch = (state.analysisRenderEpoch || 0) + 1;
  $("assistantReportPanel").hidden = true;
  $("assistantReportContent").replaceChildren();
  $("analysisSummary").hidden = true;
  for (const id of [
    "rolesContent",
    "targetPlanContent",
    "matchingContent",
    "reviewContent",
    "draftContent",
    "traceContent",
  ]) {
    $(id).textContent = "正在分析，完成后在这里展示结果…";
    $(id).classList.add("empty-state");
  }
}
async function request(url, options = {}) {
  // A response from an earlier login must never populate the current account.
  const token = state.token,
    epoch = state.authEpoch;
  const response = await fetch(url, {
    ...options,
    headers: {
      ...(options.body instanceof FormData
        ? {}
        : { "Content-Type": "application/json" }),
      ...(options.headers || {}),
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
  });
  if (token !== state.token || epoch !== state.authEpoch)
    throw new DOMException("账号状态已变化，请重新操作", "AbortError");
  if (response.status === 401) {
    logout(false);
    throw new Error("登录已失效，请重新登录");
  }
  if (response.status === 403)
    throw new Error("没有操作权限，请确认账号；当前登录已保留");
  let payload;
  try {
    payload = await response.json();
  } catch {
    throw new Error("服务暂时不可用，请稍后重试");
  }
  if (token !== state.token || epoch !== state.authEpoch)
    throw new DOMException("账号状态已变化，请重新操作", "AbortError");
  if (!response.ok || payload.success === false)
    throw new Error(payload.message || "请求失败");
  return payload.data;
}
function logout(clear = true, removeToken = true) {
  state.authEpoch++;
  state.baseEpoch++;
  state.resumeEpoch++;
  state.activeTaskId = null;
  state.running = false;
  state.draft = null;
  state.selectedResumeId = null;
  state.resumes = [];
  state.versions = [];
  state.jobs = [];
  state.reports = [];
  renderBase();
  renderVersions();
  renderReports();
  $("newVersionBtn").click();
  resetAnalysisPanels();
  for (const id of [
    "newResumeTitle",
    "newResumeContent",
    "newJobTitle",
    "newJobContent",
    "agentJobText",
    "targetRole",
    "targetCity",
    "targetCompany",
    "targetExperience",
  ])
    $(id).value = "";
  $("analysisError").textContent = "";
  $("cancelAgentBtn").disabled = true;
  $("useDraftBtn").disabled = true;
  invalidateDocument();
  state.token = "";
  if (removeToken) localStorage.removeItem("resume-fit-user-token");
  $("login").classList.remove("hidden");
  if (clear) $("loginError").textContent = "";
}
function setAuthMode(mode) {
  state.authMode = mode;
  $("login").classList.toggle("register-mode", mode === "register");
  $("authTitle").textContent =
    mode === "register" ? "创建工作台账号" : "简历版本工作台";
  $("authHint").textContent =
    mode === "register"
      ? "注册后即可保存简历版本、分析报告和分享链接。"
      : "登录后管理原始版、岗位定制版和公开分享版本。";
  $("authSubmit").textContent =
    mode === "register" ? "注册并进入工作台" : "登录工作台";
  $("authToggle").textContent =
    mode === "register" ? "已有账号？返回登录" : "首次使用？注册账号";
  $("loginError").textContent = "";
}
$("authToggle").onclick = () =>
  setAuthMode(state.authMode === "login" ? "register" : "login");
$("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  $("loginError").textContent =
    state.authMode === "register" ? "注册中…" : "登录中…";
  try {
    const body = {
      username: $("username").value.trim(),
      password: $("password").value,
    };
    if (state.authMode === "register")
      body.displayName = $("displayName").value.trim();
    const data = await request(
      state.authMode === "register" ? "/api/auth/register" : "/api/auth/login",
      { method: "POST", body: JSON.stringify(body) },
    );
    state.token = data.token;
    state.authEpoch++;
    localStorage.setItem("resume-fit-user-token", state.token);
    $("login").classList.add("hidden");
    await loadBase();
  } catch (error) {
    $("loginError").textContent = error.message;
  }
});
$("logoutBtn").onclick = () => logout();
$("refreshBtn").onclick = loadBase;
async function loadBase() {
  const generation = ++state.baseEpoch;
  try {
    const [resumes, jobs, config] = await Promise.all([
      request("/api/resumes"),
      request("/api/jobs"),
      request("/api/analysis/tasks/capabilities"),
    ]);
    if (generation !== state.baseEpoch) return;
    state.resumes = resumes || [];
    state.jobs = jobs || [];
    state.agentConfig = config;
    $("agentMode").textContent = config.demo
      ? "Demo · 离线模型演示"
      : config.model + " · 真实模型";
    $("searchHint").textContent = config.searchAvailable
      ? "已配置搜索。来源摘要不保证当前在招，请核实完整 JD。"
      : "未配置搜索服务：填写期望岗位可生成初步调整方向；选择或粘贴真实 JD 后可细化岗位要求与差距。";
    renderBase();
    if (state.resumes.length) {
      if (
        !state.selectedResumeId ||
        !state.resumes.some((x) => x.id === state.selectedResumeId)
      )
        state.selectedResumeId = state.resumes[0].id;
      await loadResume();
    } else {
      state.versions = [];
      renderVersions();
    }
  } catch (error) {
    if (state.token) alert(error.message);
  }
}
function renderBase() {
  $("resumeCount").textContent = state.resumes.length + " 份";
  $("resumeList").innerHTML = state.resumes.length
    ? state.resumes
        .map(
          (item) =>
            `<button class="resume-item ${item.id === state.selectedResumeId ? "active" : ""}" onclick="selectResume(${item.id})">${esc(item.title)}<small>${esc(item.sourceFileName || "手动创建")}</small></button>`,
        )
        .join("")
    : '<div class="hint">还没有简历</div>';
  const options =
    '<option value="">不绑定目标 JD</option>' +
    state.jobs
      .map((job) => `<option value="${job.id}">${esc(job.title)}</option>`)
      .join("");
  $("jobSelect").innerHTML = options;
  $("analysisJobSelect").innerHTML =
    '<option value="">使用粘贴的 JD / 检索岗位需求</option>' +
    state.jobs
      .map((job) => `<option value="${job.id}">${esc(job.title)}</option>`)
      .join("");
}
window.selectResume = async (id) => {
  if (state.running || state.activeTaskId) {
    $("analysisError").textContent = "请先等待当前任务结束或取消，再切换简历。";
    return;
  }
  state.selectedResumeId = id;
  renderBase();
  await loadResume();
};
async function loadResume() {
  const generation = ++state.resumeEpoch;
  const resumeId = state.selectedResumeId;
  if (!resumeId) return;
  invalidateDocument();
  try {
    const [versions, reports] = await Promise.all([
      request(`/api/resumes/${resumeId}/versions`),
      request(`/api/resumes/${resumeId}/reports`),
    ]);
    if (resumeId !== state.selectedResumeId || generation !== state.resumeEpoch)
      return;
    state.versions = versions || [];
    state.reports = reports || [];
    renderVersions();
    renderReports();
  } catch (error) {
    if (resumeId === state.selectedResumeId) alert(error.message);
  }
}
function renderVersions() {
  renderAnalysisVersions();
  $("versionHint").textContent = state.versions.length
    ? `${state.versions.length} 个版本`
    : "暂无版本";
  $("versions").innerHTML = state.versions.length
    ? state.versions
        .map(
          (v) =>
            `<div class="version"><div><div class="version-title">v${v.versionNo} · ${esc(v.title)}</div><div class="version-meta"><span>${esc(v.source)}</span><span>${num(v.viewCount)} 次访问</span><span>${new Date(v.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>${v.published ? '<span class="published">● 已发布</span>' : ""}</div></div><div class="version-actions"><button class="mini" onclick="editVersion(${v.id})">编辑</button><button class="mini" onclick="publishVersion(${v.id},${v.published})">${v.published ? "取消发布" : "发布"}</button></div></div>`,
        )
        .join("")
    : '<div class="empty hint">暂无版本，请从右侧新建</div>';
  renderDocumentVersions();
  const opts =
    '<option value="">基准版本</option>' +
    state.versions
      .map(
        (v) =>
          `<option value="${v.id}">v${v.versionNo} · ${esc(v.title)}</option>`,
      )
      .join("");
  $("fromVersion").innerHTML = opts;
  $("toVersion").innerHTML = opts;
}
function renderAnalysisVersions() {
  const select = $("analysisVersionSelect");
  const sameResume = select.dataset.resumeId === String(state.selectedResumeId);
  const previous = select.value;
  select.innerHTML =
    '<option value="">原始简历主记录</option>' +
    state.versions
      .map(
        (v) =>
          `<option value="${v.id}">v${v.versionNo} · ${esc(v.title)}</option>`,
      )
      .join("");
  select.value =
    sameResume &&
    (previous === "" || state.versions.some((v) => String(v.id) === previous))
      ? previous
      : String(state.versions[0]?.id || "");
  select.dataset.resumeId = String(state.selectedResumeId);
}
function renderReports() {
  const reports = state.reports || [];
  $("reportHint").textContent = reports.length
    ? `${reports.length} 份报告`
    : "暂无报告";
  $("reports").innerHTML = reports.length
    ? reports
        .map(
          (r) =>
            `<div class="report-item"><strong>任务 #${r.taskId} · ${r.report?.analysisType === "CAREER_ASSISTANT" ? "求职综合分析" : r.report?.analysisType === "RESUME_REVIEW" ? "简历体检" : esc(r.score) + "% 关键词覆盖率"}</strong><span class="hint">　${new Date(r.createdAt).toLocaleString("zh-CN", { hour12: false })}</span><div style="margin-top:5px;color:#aebbd5">${esc(r.report?.summary || "无摘要")}</div><button class="mini" style="margin-top:8px" onclick="viewReport(${r.taskId})">查看完整报告</button></div>`,
        )
        .join("")
    : '<div class="hint">暂无分析报告，先运行一次 Agent 分析</div>';
}
window.viewReport = async (taskId) => {
  try {
    const task = await request("/api/analysis/tasks/" + taskId);
    if (task.result) {
      renderAnalysis(task.result);
      openPanel("analysisPanel", true);
    }
  } catch (error) {
    $("analysisError").textContent = error.message;
  }
};
window.editVersion = (id) => {
  const v = state.versions.find((x) => x.id === id);
  if (!v) return;
  state.editingId = id;
  $("analysisVersionSelect").value = String(id);
  $("editorTitle").textContent = `编辑 v${v.versionNo}`;
  $("versionName").value = v.title;
  $("versionContent").value = v.content;
  $("versionSource").value = [
    "MANUAL",
    "AI_OPTIMIZED",
    "JOB_TARGETED",
  ].includes(v.source)
    ? v.source
    : "MANUAL";
  $("jobSelect").value = v.targetJobId || "";
  $("taskId").value = v.analysisTaskId || "";
  openPanel("editorPanel", true);
};
window.publishVersion = async (id, published) => {
  try {
    const data = await request(
      `/api/resumes/${state.selectedResumeId}/versions/${id}/publish`,
      { method: published ? "DELETE" : "POST" },
    );
    if (!published) {
      const url =
        location.origin +
        "/share.html?token=" +
        data.sharePath.split("/").pop();
      await navigator.clipboard?.writeText(url);
      alert("已发布，分享链接已复制：\n" + url);
    }
    await loadResume();
  } catch (error) {
    alert(error.message);
  }
};
$("newVersionBtn").onclick = () => {
  state.editingId = null;
  $("editorTitle").textContent = "新建简历版本";
  $("versionName").value = "";
  $("versionContent").value = "";
  $("versionSource").value = "MANUAL";
  $("jobSelect").value = "";
  $("taskId").value = "";
  $("versionError").textContent = "";
};
$("saveVersionBtn").onclick = async () => {
  if (!state.selectedResumeId)
    return ($("versionError").textContent = "请先选择简历");
  const body = {
    title: $("versionName").value.trim(),
    content: $("versionContent").value.trim(),
  };
  if (!body.title || !body.content)
    return ($("versionError").textContent = "版本名称和内容不能为空");
  body.source = $("versionSource").value;
  body.targetJobId = $("jobSelect").value ? Number($("jobSelect").value) : null;
  body.analysisTaskId = $("taskId").value ? Number($("taskId").value) : null;
  try {
    await request(
      state.editingId
        ? `/api/resumes/${state.selectedResumeId}/versions/${state.editingId}`
        : `/api/resumes/${state.selectedResumeId}/versions`,
      { method: state.editingId ? "PUT" : "POST", body: JSON.stringify(body) },
    );
    $("versionError").textContent = "保存成功";
    $("newVersionBtn").click();
    await loadResume();
  } catch (error) {
    $("versionError").textContent = error.message;
  }
};
$("createResumeBtn").onclick = async () => {
  const title = $("newResumeTitle").value.trim(),
    content = $("newResumeContent").value.trim();
  if (!title || !content)
    return ($("resumeError").textContent = "标题和内容不能为空");
  try {
    const data = await request("/api/resumes", {
      method: "POST",
      body: JSON.stringify({ title, content, sourceFileName: "workspace.txt" }),
    });
    $("newResumeTitle").value = "";
    $("newResumeContent").value = "";
    $("resumeError").textContent = "保存成功";
    state.selectedResumeId = data.id;
    await loadBase();
  } catch (error) {
    $("resumeError").textContent = error.message;
  }
};
$("createJobBtn").onclick = async () => {
  const title = $("newJobTitle").value.trim(),
    content = $("newJobContent").value.trim();
  if (!title || !content)
    return ($("jobError").textContent = "岗位标题和 JD 内容不能为空");
  try {
    await request("/api/jobs", {
      method: "POST",
      body: JSON.stringify({ title, content }),
    });
    $("newJobTitle").value = "";
    $("newJobContent").value = "";
    $("jobError").textContent = "保存成功";
    await loadBase();
  } catch (error) {
    $("jobError").textContent = error.message;
  }
};
function analysisInput(resumeId) {
  return {
    resumeId,
    versionId: resumeId
      ? Number($("analysisVersionSelect").value) || null
      : null,
    jobId: Number($("analysisJobSelect").value) || null,
    targetRole: $("targetRole").value.trim(),
    city: $("targetCity").value.trim(),
    company: $("targetCompany").value.trim(),
    experience: $("targetExperience").value.trim(),
    jobDescription: $("agentJobText").value.trim(),
  };
}
async function runAgent(body) {
  if (state.running || state.activeTaskId)
    throw new Error("已有分析正在运行，请等待完成或取消");
  const epoch = state.authEpoch;
  state.running = true;
  state.draft = null;
  $("analyzeBtn").disabled = true;
  $("importResumeBtn").disabled = true;
  $("reviewResumeBtn").disabled = true;
  $("useDraftBtn").disabled = true;
  $("analysisError").textContent = body.resumeOnly
    ? "正在创建简历体检任务…"
    : "正在创建岗位分析任务…";
  resetAnalysisPanels();
  openPanel("analysisPanel");
  try {
    const task = await request("/api/analysis/tasks", {
      method: "POST",
      body: JSON.stringify(body),
    });
    state.activeTaskId = task.id;
    $("cancelAgentBtn").disabled = false;
    openPanel("analysisPanel", true);
    const deadline =
      Date.now() + ((state.agentConfig?.timeoutSeconds || 120) + 25) * 1000;
    while (true) {
      if (epoch !== state.authEpoch)
        throw new DOMException("账号状态已变化", "AbortError");
      const current = await request("/api/analysis/tasks/" + task.id);
      if (current.result) renderAnalysis(current.result);
      $("analysisError").textContent = current.message || current.status;
      if (
        ["SUCCEEDED", "FAILED", "CANCELLED", "NEEDS_INPUT"].includes(
          current.status,
        )
      ) {
        state.activeTaskId = null;
        if (current.status === "SUCCEEDED") {
          state.draft = {
            content: current.result.draftContent,
            resumeId: body.resumeId,
            jobId: body.jobId,
            taskId: task.id,
            title: body.targetRole || "岗位定制",
          };
          $("useDraftBtn").disabled = !state.draft.content;
          $("analysisError").textContent = body.resumeOnly
            ? "简历体检与岗位方向分析完成。补充真实 JD 后可进一步细化调整建议。"
            : "分析完成。请检查建议与草稿，再载入编辑器并另存新版本。";
        } else
          $("analysisError").textContent =
            current.result?.summary || current.errorMessage || current.message;
        if (state.selectedResumeId === body.resumeId) await loadResume();
        return current;
      }
      if (Date.now() > deadline)
        throw new Error(
          "任务 #" + task.id + " 仍未结束，可点击取消，稍后重试。",
        );
      await new Promise((r) => setTimeout(r, 650));
    }
  } finally {
    if (epoch === state.authEpoch) {
      state.running = false;
      $("analyzeBtn").disabled = false;
      $("importResumeBtn").disabled = false;
      $("reviewResumeBtn").disabled = false;
      $("cancelAgentBtn").disabled = !state.activeTaskId;
    }
  }
}
$("analyzeBtn").onclick = async () => {
  if (!state.selectedResumeId)
    return ($("analysisError").textContent = "请先导入或选择简历");
  const body = analysisInput(state.selectedResumeId);
  if (!body.jobId && !body.targetRole && !body.jobDescription)
    return ($("analysisError").textContent =
      "请补充目标岗位或 JD，也可以先点击“体检并分析岗位方向”。");
  if (
    !body.jobId &&
    !body.jobDescription &&
    !state.agentConfig?.searchAvailable
  )
    body.resumeOnly = true;
  try {
    await runAgent(body);
  } catch (error) {
    $("analysisError").textContent = error.message;
  }
};
$("reviewResumeBtn").onclick = async () => {
  if (!state.selectedResumeId)
    return ($("analysisError").textContent = "请先导入或选择简历");
  try {
    await runAgent({
      ...analysisInput(state.selectedResumeId),
      jobId: null,
      jobDescription: "",
      resumeOnly: true,
    });
  } catch (error) {
    $("analysisError").textContent = error.message;
  }
};
$("resumeFile").onchange = () => {
  const file = $("resumeFile").files[0];
  if (file) {
    const title = file.name.replace(/\.[^.]+$/, "").slice(0, 160);
    if (
      !$("newResumeTitle").value.trim() ||
      $("newResumeTitle").value === state.importTitle
    )
      $("newResumeTitle").value = title;
    state.importTitle = title;
  }
  $("importStatus").textContent = "";
};
$("importResumeBtn").onclick = async () => {
  if (state.importing || state.running) return;
  const file = $("resumeFile").files[0];
  if (!file)
    return ($("importStatus").textContent =
      "请先选择 PDF、Word 或 TXT 简历文件");
  if (!/\.(pdf|docx?|txt)$/i.test(file.name))
    return ($("importStatus").textContent = "仅支持 PDF、DOC、DOCX 或 TXT");
  if (file.size > 5 * 1024 * 1024)
    return ($("importStatus").textContent = "文件不能超过 5MB");
  if (!file.size)
    return ($("importStatus").textContent = "文件为空，请重新选择");
  state.importing = true;
  $("importResumeBtn").disabled = true;
  $("analyzeBtn").disabled = true;
  $("reviewResumeBtn").disabled = true;
  let saved = false;
  try {
    const form = new FormData();
    form.append("file", file);
    $("importStatus").textContent = "正在识别文件文字…";
    const parsed = await request("/api/resumes/parse", {
      method: "POST",
      body: form,
    });
    $("newResumeContent").value = parsed.content;
    const input = analysisInput(null); // 保存之前固定本次岗位条件，刷新列表不会丢失选择。
    const resume = await request("/api/resumes", {
      method: "POST",
      body: JSON.stringify({
        title: (
          $("newResumeTitle").value.trim() || file.name.replace(/\.[^.]+$/, "")
        ).slice(0, 160),
        content: parsed.content,
        sourceFileName: file.name.slice(0, 255),
      }),
    });
    saved = true;
    state.selectedResumeId = resume.id;
    $("importStatus").textContent =
      "已识别 " + parsed.characterCount + " 字符并保存原始版，正在自动分析…";
    await loadBase();
    const hasJd = !!(input.jobId || input.jobDescription);
    const task = await runAgent(
      hasJd
        ? { ...input, resumeId: resume.id }
        : { ...input, resumeId: resume.id, resumeOnly: true },
    );
    $("importStatus").textContent =
      task.status === "SUCCEEDED"
        ? "导入完成，分析结果已生成。"
        : "简历已保存；" +
          (task.result?.summary || task.errorMessage || task.message);
  } catch (error) {
    $("importStatus").textContent =
      (saved
        ? "简历已保存，自动分析未完成；可点击“体检并分析岗位方向”重试。"
        : "导入未完成，未创建简历。") +
      " " +
      error.message;
  } finally {
    state.importing = false;
    $("importResumeBtn").disabled = false;
    $("analyzeBtn").disabled = !!state.running;
    $("reviewResumeBtn").disabled = !!state.running;
  }
};
$("cancelAgentBtn").onclick = async () => {
  if (!state.activeTaskId) return;
  try {
    const task = await request(`/api/analysis/tasks/${state.activeTaskId}`, {
      method: "DELETE",
    });
    $("analysisError").textContent = task.message;
    if (
      ["SUCCEEDED", "FAILED", "CANCELLED", "NEEDS_INPUT"].includes(task.status)
    )
      state.activeTaskId = null;
    $("cancelAgentBtn").disabled = true;
  } catch (error) {
    $("analysisError").textContent = error.message;
  }
};
$("useDraftBtn").onclick = async () => {
  const draft = state.draft;
  if (!draft?.content) return;
  if (state.selectedResumeId !== draft.resumeId) {
    state.selectedResumeId = draft.resumeId;
    renderBase();
    await loadResume();
  }
  $("newVersionBtn").click();
  $("versionName").value = draft.title + " · 待核实草稿";
  $("versionContent").value = draft.content;
  $("versionSource").value = "JOB_TARGETED";
  $("jobSelect").value = draft.jobId || "";
  $("taskId").value = draft.taskId;
  $("versionError").textContent =
    "这是未保存的原文排序草稿。请检查段落关系和事实，再点击“保存版本”另存。";
  openPanel("editorPanel", true);
};
function renderResumeReview(review) {
  if (!review || !Object.keys(review).length) return "";
  return (
    "<h3>简历结构与原文线索</h3>" +
    review.sections
      .map(
        (s) =>
          '<div class="report-item"><b>' +
          esc(s.name) +
          "</b> · " +
          (s.found ? "已识别线索" : "待核实") +
          '<p class="hint">' +
          esc(s.quote || "未识别到明确文字，请核实是否完整导入。") +
          "</p></div>",
      )
      .join("") +
    "<h3>体检建议</h3>" +
    review.observations.map((s) => "<p>" + esc(s) + "</p>").join("") +
    '<div class="chips">' +
    review.technicalKeywords
      .map((k) => '<span class="chip">原文提到：' + esc(k) + "</span>")
      .join("") +
    '<p class="hint">' +
    esc(review.notice) +
    "</p>"
  );
}
function renderCareerAdvice(advice, completed) {
  if (!advice) {
    const empty =
      '<p class="hint">' +
      (completed
        ? "此报告尚无岗位方向分析，重新分析可生成。"
        : "分析完成后展示结果。") +
      "</p>";
    return { roles: empty, target: empty };
  }
  const roles = Array.isArray(advice.recommendedRoles)
      ? advice.recommendedRoles
      : [],
    plan = advice.targetPlan || {},
    steps = Array.isArray(plan.priorities) ? plan.priorities : [];
  const quoteHtml = (quotes) =>
    (Array.isArray(quotes) ? quotes : [])
      .map(
        (q) => '<blockquote class="career-quote">' + esc(q) + "</blockquote>",
      )
      .join("");
  const basis = {
    NONE: "未填写期望岗位",
    ROLE_ONLY: "仅按岗位名称初步分析",
    USER_JD: "结合所提供的 JD",
    SEARCH_EXCERPT: "结合搜索摘要，需核实",
  };
  const categories = {
    HIGHLIGHT: "突出已有经历",
    VERIFY: "先核实再补充",
    LEARN: "后续学习与实践",
  };
  const recommended =
    '<p class="hint">' +
    esc(advice.recommendationNotice) +
    "</p>" +
    (roles.length
      ? '<div class="career-grid">' +
        roles
          .map(
            (r, i) =>
              '<article class="career-card"><span class="career-tag">' +
              (i === 0 ? "优先考虑" : "可考虑") +
              "</span><h4>" +
              esc(r.title) +
              "</h4><p>" +
              esc(r.reason) +
              '</p><div class="hint">简历原文依据</div>' +
              quoteHtml(r.evidence) +
              (r.uncertainties || [])
                .map((q) => '<p class="hint">待核实：' + esc(q) + "</p>")
                .join("") +
              "</article>",
          )
          .join("") +
        "</div>"
      : '<p class="hint">原文证据不足，暂不强行推荐。可以补充真实项目、工作职责和技能使用情况后重新分析。</p>');
  const target =
    '<div class="career-target">' +
    esc(plan.targetRole || "请填写上方期望岗位，然后重新分析") +
    '</div><p class="hint">' +
    esc(basis[plan.basis] || "初步方向") +
    " · " +
    esc(plan.notice) +
    "</p>" +
    (steps.length
      ? steps
          .map(
            (p) =>
              '<article class="career-step"><span class="career-tag">' +
              esc(categories[p.category] || "待核实建议") +
              "</span><h4>" +
              esc(p.title) +
              "</h4><p>" +
              esc(p.action) +
              "</p>" +
              quoteHtml(p.evidence) +
              "</article>",
          )
          .join("")
      : plan.targetRole
        ? '<p class="hint">本次未生成有充分依据的具体调整步骤，可补充真实 JD 和项目细节后重新分析。</p>'
        : "") +
    (plan.questions || [])
      .map((q) => '<p class="hint">需要补充：' + esc(q) + "</p>")
      .join("") +
    (advice.omittedUnsupportedItems
      ? '<p class="hint">部分建议缺少可核对的简历原文依据，已省略。</p>'
      : "");
  return { roles: recommended, target };
}
function safeSourceUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}
function renderAnalysis(result) {
  const renderEpoch = (state.analysisRenderEpoch =
    (state.analysisRenderEpoch || 0) + 1);
  const authEpoch = state.authEpoch;
  const assistantReport = result.analysisType === "CAREER_ASSISTANT";
  $("assistantReportPanel").hidden = !assistantReport;
  $("assistantReportContent").replaceChildren();
  if (assistantReport) {
    $("assistantReportContent").textContent = "正在载入求职综合报告…";
    // Share one safe renderer with the assistant page; never duplicate result contracts.
    import("/assets/job-assistant/views.js")
      .then(({ renderAgentResult }) => {
        if (
          renderEpoch !== state.analysisRenderEpoch ||
          authEpoch !== state.authEpoch
        )
          return;
        $("assistantReportContent").innerHTML = renderAgentResult({
          status: "SUCCEEDED",
          result,
        });
      })
      .catch(() => {
        if (
          renderEpoch === state.analysisRenderEpoch &&
          authEpoch === state.authEpoch
        )
          $("assistantReportContent").textContent =
            "报告展示暂时不可用，请刷新后重试。";
      });
  }
  const matching = result.matching || {},
    trace = result.agentTrace || [],
    usage = result.usage || {};
  $("analysisSummary").hidden = !result.summary;
  $("analysisSummary").textContent = result.summary || "";
  const career = renderCareerAdvice(
    result.careerAdvice,
    result.termination === "COMPLETED",
  );
  $("rolesContent").innerHTML = career.roles;
  $("targetPlanContent").innerHTML = career.target;
  const hasMatch =
    result.analysisType !== "RESUME_REVIEW" && Object.keys(matching).length > 0;
  $("matchingContent").innerHTML = hasMatch
    ? `${result.score === undefined ? "" : `<div class="score">${esc(result.score)}%<small>关键词文字覆盖率</small></div>`}<p class="hint">${esc(result.scoreMeaning || "不代表能力评分或录用概率")}</p><div class="chips">${(matching.matchedKeywords || []).map((x) => `<span class="chip">已有文字：${esc(x)}</span>`).join("")}${(matching.missingKeywords || []).map((x) => `<span class="chip missing">待核实：${esc(x)}</span>`).join("")}</div>${(result.evidence || []).map((x) => `<p class="hint"><b>${esc(x.keyword)}</b>：${esc(x.quote)}</p>`).join("")}`
    : `<p class="hint">${result.analysisType === "RESUME_REVIEW" ? "本次未使用岗位 JD，不计算岗位匹配分数。可在上方提供真实 JD 后重新分析，细化已有证据与待核实要求。" : result.termination ? esc(result.summary || "本次没有可用的 JD 对比结果。") : "正在执行，岗位 JD 对比结果尚未生成。"}</p>`;
  $("reviewContent").innerHTML =
    renderResumeReview(result.resumeReview) +
      ((result.sources || []).length
        ? "<h3>检索来源（摘要，需核实完整 JD）</h3>"
        : "") +
      (result.sources || [])
        .map((x) => {
          const url = safeSourceUrl(x.url);
          return `<div class="report-item">${url ? `<a href="${esc(url)}" target="_blank" rel="noopener noreferrer" style="color:var(--cyan)">${esc(x.title)}</a>` : esc(x.title)}<div class="hint">${esc(x.kind)} · ${esc(x.retrievedAt)}</div><p>${esc(x.excerpt)}</p></div>`;
        })
        .join("") +
      ((result.interviewQuestions || []).length
        ? "<h3>核实与面试问题</h3>"
        : "") +
      (result.interviewQuestions || [])
        .map((q) => "<p>" + esc(q) + "</p>")
        .join("") || '<p class="hint">本次暂无额外体检建议或核实问题。</p>';
  $("draftContent").innerHTML = result.draftContent
    ? '<p class="hint">这是原文排序草稿，尚未保存。请核实段落关系和事实，再载入编辑器另存。</p><pre>' +
      esc(result.draftContent) +
      "</pre>"
    : '<p class="hint">本次未生成岗位调整草稿。提供 JD 后可分析并生成原文排序草稿。</p>';
  $("traceContent").innerHTML =
    '<div class="trace">' +
    trace
      .map(
        (x) =>
          '<div class="trace-item"><b>' +
          esc(x.status) +
          "</b><span>" +
          esc(x.name) +
          '</span><span class="hint">' +
          esc(x.message) +
          "</span></div>",
      )
      .join("") +
    "</div>" +
    (usage.modelRounds === undefined
      ? ""
      : '<p class="hint">结束原因：' +
        esc(result.termination) +
        " · 模型轮次 " +
        esc(usage.modelRounds) +
        " · 工具调用 " +
        esc(usage.toolCalls) +
        " · 模型报告输入/输出 token " +
        esc(usage.reportedInputTokens) +
        " / " +
        esc(usage.reportedOutputTokens) +
        (result.demo ? "（离线 Demo，无模型费用）" : "") +
        "</p>");
  for (const id of [
    "rolesContent",
    "targetPlanContent",
    "matchingContent",
    "reviewContent",
    "draftContent",
    "traceContent",
  ])
    $(id).classList.remove("empty-state");
}
$("compareBtn").onclick = async () => {
  const from = $("fromVersion").value,
    to = $("toVersion").value;
  if (!from || !to) return ($("compareResult").textContent = "请选择两个版本");
  try {
    const data = await request(
      `/api/resumes/${state.selectedResumeId}/versions/compare?from=${from}&to=${to}`,
    );
    $("compareResult").innerHTML =
      `从 <strong>v${data.from.versionNo}</strong> 到 <strong>v${data.to.versionNo}</strong>：共变化 <strong style="color:var(--cyan)">${data.changedLines}</strong> 行，新增 ${data.addedLines} 行，删除 ${data.removedLines} 行。`;
  } catch (error) {
    $("compareResult").textContent = error.message;
  }
};

function invalidateDocument() {
  state.documentEpoch = (state.documentEpoch || 0) + 1;
  if (state.documentUrl) URL.revokeObjectURL(state.documentUrl);
  state.documentUrl = null;
  state.documentResumeId = null;
  $("documentPreview").hidden = true;
  $("resumePdfFrame").removeAttribute("src");
  $("downloadDocumentBtn").disabled = true;
}
async function privateBlob(url) {
  const response = await fetch(url, {
    headers: { Authorization: "Bearer " + state.token },
    cache: "no-store",
  });
  if (!response.ok) {
    let message = "文件读取失败";
    try {
      message = (await response.json()).message || message;
    } catch {}
    throw new Error(message);
  }
  return response.blob();
}
$("documentTarget").oninput = invalidateDocument;
function downloadUrl(url, name) {
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
}
function renderDocumentVersions() {
  const selected = $("documentVersion").value;
  invalidateDocument();
  $("documentVersion").innerHTML = state.versions.length
    ? state.versions
        .map(
          (v) =>
            `<option value="${v.id}">v${v.versionNo} · ${esc(v.title)}</option>`,
        )
        .join("")
    : '<option value="">请先保存简历版本</option>';
  if (state.versions.some((v) => String(v.id) === selected))
    $("documentVersion").value = selected;
}
$("documentVersion").onchange = invalidateDocument;
function copyForRevision(version) {
  if (!version) return;
  $("newVersionBtn").click();
  $("versionName").value = (version.title + " · 调整版").slice(0, 160);
  $("versionContent").value = version.content;
  $("versionSource").value = "MANUAL";
  $("jobSelect").value = version.targetJobId || "";
  $("versionError").textContent =
    "已复制所选版本。请按建议修改内容，再另存为新版本并生成简历。";
  openPanel("editorPanel", true);
}
$("startRevisionBtn").onclick = () => {
  if (!state.selectedResumeId)
    return ($("analysisError").textContent = "请先导入或选择简历");
  copyForRevision(state.versions[0]);
};
$("reviseSelectedBtn").onclick = () =>
  copyForRevision(
    state.versions.find((v) => String(v.id) === $("documentVersion").value),
  );
$("saveAndGenerateBtn").onclick = async () => {
  if (state.documentSaving) return;
  const resumeId = state.selectedResumeId;
  if (!resumeId) return ($("versionError").textContent = "请先选择简历");
  const title = $("versionName").value.trim(),
    content = $("versionContent").value.trim();
  if (!title || !content)
    return ($("versionError").textContent = "版本名称和内容不能为空");
  state.documentSaving = true;
  $("saveAndGenerateBtn").disabled = true;
  try {
    const saved = await request(`/api/resumes/${resumeId}/versions`, {
      method: "POST",
      body: JSON.stringify({
        title,
        content,
        source: $("versionSource").value || "MANUAL",
        targetJobId: Number($("jobSelect").value) || null,
        analysisTaskId: Number($("taskId").value) || null,
      }),
    });
    if (state.selectedResumeId !== resumeId) return;
    await loadResume();
    $("documentVersion").value = saved.id;
    $("documentTarget").value = $("targetRole").value.trim();
    $("versionError").textContent =
      "已另存为新版本，正在生成 PDF；原版本保留。";
    openPanel("documentPanel", true);
    await previewDocument();
  } catch (error) {
    $("versionError").textContent = error.message;
  } finally {
    state.documentSaving = false;
    $("saveAndGenerateBtn").disabled = false;
  }
};
async function previewDocument() {
  if (state.documentBusy) return;
  const resumeId = state.selectedResumeId,
    versionId = $("documentVersion").value;
  if (!resumeId || !versionId)
    return ($("documentStatus").textContent = "请先选择已保存的简历版本");
  state.documentBusy = true;
  $("previewDocumentBtn").disabled = true;
  invalidateDocument();
  $("documentStatus").textContent = "正在排版并生成 PDF…";
  const epoch = state.documentEpoch,
    token = state.token;
  const options = {
    targetRole: $("documentTarget").value.trim(),
  };
  try {
    const query = new URLSearchParams({
      targetRole: options.targetRole,
    });
    const blob = await privateBlob(
      `/api/resumes/${resumeId}/versions/${versionId}/document?${query}`,
    );
    if (
      epoch !== state.documentEpoch ||
      token !== state.token ||
      state.selectedResumeId !== resumeId ||
      $("documentVersion").value !== versionId ||
      $("documentTarget").value.trim() !== options.targetRole
    )
      return;
    state.documentUrl = URL.createObjectURL(blob);
    state.documentResumeId = resumeId;
    state.documentVersionId = versionId;
    $("resumePdfFrame").src = state.documentUrl;
    $("documentPreview").hidden = false;
    $("downloadDocumentBtn").disabled = false;
    $("documentStatus").textContent = "PDF 已生成。请检查预览后下载。";
  } catch (error) {
    $("documentStatus").textContent = error.message;
  } finally {
    state.documentBusy = false;
    $("previewDocumentBtn").disabled = false;
  }
}
$("previewDocumentBtn").onclick = previewDocument;
$("downloadDocumentBtn").onclick = () => {
  if (state.documentUrl && state.documentResumeId === state.selectedResumeId)
    downloadUrl(state.documentUrl, "简历-v" + state.documentVersionId + ".pdf");
};
window.addEventListener("beforeunload", () => {
  if (state.documentUrl) URL.revokeObjectURL(state.documentUrl);
});

setupPanels();
window.addEventListener("storage", (event) => {
  if (event.key !== "resume-fit-user-token" || event.newValue === state.token)
    return;
  logout(true, false);
  state.token = event.newValue || "";
  $("login").classList.toggle("hidden", Boolean(state.token));
  if (state.token) loadBase();
});
$("login").classList.toggle("hidden", Boolean(state.token));
if (state.token) loadBase();
