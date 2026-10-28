import {
  ApiClient,
  isAborted,
  splitList,
  salaryRange,
  sourceUrl,
  today,
} from "./api.js";
import {
  stages,
  esc,
  renderStats,
  renderJobs,
  renderCompanies,
  renderApplications,
  evidenceRow,
  renderAgentResult,
} from "./views.js";

const $ = (id) => document.getElementById(id);
const activeTaskKey = "resume-fit-career-active-task";
const savedTaskId = Number(sessionStorage.getItem(activeTaskKey));
const state = {
  resumes: [],
  jobs: [],
  companies: [],
  applications: [],
  profile: null,
  capabilities: null,
  versions: [],
  authMode: "login",
  loadEpoch: 0,
  versionEpoch: 0,
  pollEpoch: 0,
  agentEpoch: 0,
  activeTaskId:
    Number.isSafeInteger(savedTaskId) && savedTaskId > 0 ? savedTaskId : null,
  polling: false,
  profileDirty: false,
  profileDraftRevision: 0,
  versionReady: false,
  busy: new Set(),
};
const api = new ApiClient(() => signOut("登录已失效，请重新登录。", false));
const fields = (form) => Object.fromEntries(new FormData(form));
function setActiveTask(id) {
  state.activeTaskId = id || null;
  if (state.activeTaskId)
    sessionStorage.setItem(activeTaskKey, String(state.activeTaskId));
  else sessionStorage.removeItem(activeTaskKey);
}
function message(id, text = "", error = false) {
  $(id).textContent = text;
  $(id).classList.toggle("error", error);
}
function setAuthenticated(loggedIn) {
  $("authLayer").hidden = loggedIn;
  document.body.classList.toggle("authenticated-out", !loggedIn);
  $("content").inert = !loggedIn;
  if (!loggedIn) $("authForm").elements.username.focus();
}
function signOut(reason = "", clearToken = true) {
  if (clearToken) api.setToken("");
  state.loadEpoch++;
  state.versionEpoch++;
  state.pollEpoch++;
  state.agentEpoch++;
  setActiveTask(null);
  state.polling = false;
  state.busy.clear();
  state.resumes = [];
  state.jobs = [];
  state.companies = [];
  state.applications = [];
  state.versions = [];
  state.profile = null;
  state.capabilities = null;
  state.profileDirty = false;
  state.profileDraftRevision = 0;
  state.versionReady = false;
  for (const id of [
    "authForm",
    "profileForm",
    "jobForm",
    "companyForm",
    "applicationForm",
    "assistantForm",
  ]) {
    $(id).reset();
    $(id).inert = false;
    $(id).removeAttribute("aria-busy");
    $(id)
      .querySelectorAll('button[type="submit"]')
      .forEach((button) => (button.disabled = false));
  }
  $("refresh").disabled = false;
  $("evidenceRows").replaceChildren();
  $("agentResult").replaceChildren();
  $("agentResult").hidden = true;
  $("applicationEditor").hidden = true;
  $("companyEditorTitle").textContent = "＋ 新建公司档案";
  for (const id of [
    "pageMessage",
    "agentMessage",
    "profileMessage",
    "jobMessage",
    "companyMessage",
    "applicationMessage",
  ])
    message(id);
  renderLists();
  updateAgentControls();
  setAuthenticated(false);
  message("authMessage", reason, Boolean(reason));
}
async function submit(formId, messageId, work) {
  if (state.busy.has(formId)) return;
  const epoch = api.epoch;
  state.busy.add(formId);
  $(formId).inert = true;
  $(formId).setAttribute("aria-busy", "true");
  const buttons = [...$(formId).querySelectorAll('button[type="submit"]')];
  buttons.forEach((button) => (button.disabled = true));
  message(messageId, "正在保存…");
  try {
    await work();
  } catch (error) {
    if (!isAborted(error) && epoch === api.epoch)
      message(messageId, error.message, true);
  } finally {
    if (epoch === api.epoch) {
      state.busy.delete(formId);
      $(formId).inert = false;
      $(formId).removeAttribute("aria-busy");
      buttons.forEach((button) => (button.disabled = false));
    }
  }
}

// Authentication boundaries: in-flight requests are aborted before switching accounts.
$("authToggle").addEventListener("click", () => {
  state.authMode = state.authMode === "login" ? "register" : "login";
  const register = state.authMode === "register";
  $("authTitle").textContent = register ? "创建求职账号" : "登录我的求职";
  $("authSubmit").textContent = register ? "注册并进入" : "登录";
  $("authToggle").textContent = register
    ? "已有账号？返回登录"
    : "首次使用？注册账号";
  $("displayNameLabel").hidden = !register;
  $("authForm").elements.password.autocomplete = register
    ? "new-password"
    : "current-password";
  message("authMessage");
});
$("authForm").addEventListener("submit", (event) => {
  event.preventDefault();
  submit("authForm", "authMessage", async () => {
    const values = fields(event.currentTarget),
      body = { username: values.username.trim(), password: values.password };
    if (state.authMode === "register")
      body.displayName = values.displayName.trim();
    const data = await api.request(`/api/auth/${state.authMode}`, {
      method: "POST",
      body,
      anonymous: true,
    });
    if (!data?.token) throw new Error("服务未返回登录凭证，请重试。");
    api.setToken(data.token);
    // setToken invalidates the submit epoch, so explicitly restore this button.
    state.busy.delete("authForm");
    $("authForm").inert = false;
    $("authForm").removeAttribute("aria-busy");
    $("authSubmit").disabled = false;
    $("authForm").elements.password.value = "";
    setAuthenticated(true);
    message("authMessage");
    await loadData(true);
  });
});
$("logout").addEventListener("click", () => signOut());
window.addEventListener("storage", (event) => {
  if (event.key !== "resume-fit-user-token" || event.newValue === api.token)
    return;
  api.setToken(event.newValue || "");
  signOut(event.newValue ? "" : "账号已在另一页面退出。", false);
  if (event.newValue) {
    setAuthenticated(true);
    loadData(true);
  }
});

// Refresh lists independently; one unavailable service must not erase other records.
const resources = [
  ["resumes", "/api/resumes"],
  ["jobs", "/api/career/jobs?limit=100&includeArchived=true"],
  ["companies", "/api/career/companies?limit=100"],
  ["applications", "/api/career/applications?limit=100"],
  ["profile", "/api/career/profile"],
  ["capabilities", "/api/career/assistant/capabilities"],
];
async function loadData(fillProfile = false) {
  if (!api.token) return;
  const generation = ++state.loadEpoch,
    epoch = api.epoch;
  $("refresh").disabled = true;
  message("pageMessage", "正在读取求职资料…");
  const results = await Promise.allSettled(
    resources.map(([, path]) => api.request(path)),
  );
  if (generation !== state.loadEpoch || epoch !== api.epoch) return;
  const failed = [];
  results.forEach((result, index) => {
    const key = resources[index][0];
    if (result.status === "fulfilled") {
      state[key] = ["profile", "capabilities"].includes(key)
        ? result.value
        : Array.isArray(result.value)
          ? result.value
          : [];
    } else if (!isAborted(result.reason))
      failed.push(
        `${{ resumes: "简历", jobs: "岗位", companies: "公司", applications: "跟进", profile: "偏好", capabilities: "助手服务" }[key]}：${result.reason.message}`,
      );
  });
  renderLists();
  if (fillProfile && state.profile && !state.profileDirty) fillProfileForm();
  const config = state.capabilities || {};
  $("agentMode").textContent = state.capabilities
    ? config.demo
      ? "离线演示"
      : "真实模型"
    : "服务状态待确认";
  $("capabilityHint").textContent =
    (config.notice ||
      (config.demo
        ? "当前为离线演示，按规则整理已有材料；不会生成实时招聘信息。"
        : config.searchAvailable
          ? "可以结合已保存的岗位和公司信息分析；检索结果仍需核实招聘时效与原文。"
          : "根据已保存的真实岗位和公司信息分析。未配置外部搜索时，不生成实时招聘信息。")) +
    " 求职总览整理最近 5 个未归档岗位和 3 家公司；可选择具体对象单独分析。";
  message(
    "pageMessage",
    failed.length ? failed.join("\n") : "资料已更新。",
    failed.length > 0,
  );
  $("refresh").disabled = false;
  await loadVersions();
}
$("refresh").addEventListener("click", () => loadData(!state.profileDirty));
function updateOptions(id, records, placeholder, label) {
  const element = $(id),
    previous = element.value;
  element.innerHTML =
    `<option value="">${esc(placeholder)}</option>` +
    records
      .map((item) => `<option value="${item.id}">${esc(label(item))}</option>`)
      .join("");
  if (records.some((item) => String(item.id) === previous))
    element.value = previous;
}
function renderLists() {
  $("stats").innerHTML = renderStats(state);
  $("jobList").innerHTML = renderJobs(state.jobs);
  $("companyList").innerHTML = renderCompanies(state.companies);
  $("applicationList").innerHTML = renderApplications(state.applications);
  const previousResume = $("resumeSelect").value;
  updateOptions("resumeSelect", state.resumes, "请选择简历", (x) => x.title);
  if (!$("resumeSelect").value && state.resumes.length)
    $("resumeSelect").value = String(state.resumes[0].id);
  if ($("resumeSelect").value !== previousResume) {
    state.versions = [];
    $("versionSelect").innerHTML = '<option value="">原始简历主记录</option>';
  }
  updateOptions(
    "agentJobSelect",
    state.jobs,
    "暂不指定",
    (x) =>
      `${x.title} · ${x.companyName}${x.status === "ARCHIVED" ? "（已归档）" : ""}`,
  );
  updateOptions(
    "agentCompanySelect",
    state.companies,
    "暂不指定",
    (x) => x.name,
  );
  updateOptions("jobCompanySelect", state.companies, "不关联", (x) => x.name);
}
async function loadVersions() {
  const resumeId = $("resumeSelect").value,
    generation = ++state.versionEpoch,
    epoch = api.epoch;
  state.versions = [];
  state.versionReady = false;
  updateAgentControls();
  const previous = $("versionSelect").value;
  const sameResume = $("versionSelect").dataset.resumeId === resumeId;
  $("versionSelect").disabled = true;
  if (!resumeId) {
    $("versionSelect").innerHTML = '<option value="">原始简历主记录</option>';
    updateAgentControls();
    return;
  }
  try {
    const versions = await api.request(`/api/resumes/${resumeId}/versions`);
    if (generation !== state.versionEpoch || epoch !== api.epoch) return;
    state.versions = Array.isArray(versions) ? versions : [];
    state.versionReady = true;
    updateOptions(
      "versionSelect",
      state.versions,
      "原始简历主记录",
      (x) => `v${x.versionNo} · ${x.title}`,
    );
    if (
      sameResume &&
      (previous === "" || state.versions.some((x) => String(x.id) === previous))
    )
      $("versionSelect").value = previous;
    else if (state.versions.length)
      $("versionSelect").value = String(state.versions[0].id);
    $("versionSelect").dataset.resumeId = resumeId;
  } catch (error) {
    if (generation === state.versionEpoch && !isAborted(error)) {
      $("versionSelect").innerHTML =
        '<option value="">版本读取失败，请刷新</option>';
      message("agentMessage", error.message, true);
    }
  } finally {
    if (generation === state.versionEpoch && epoch === api.epoch) {
      $("versionSelect").disabled = state.polling;
      updateAgentControls();
    }
  }
}
$("resumeSelect").addEventListener("change", () => {
  $("versionSelect").value = "";
  $("versionSelect").dataset.resumeId = "";
  loadVersions();
});

// Optimistic revisions are sent back unchanged; conflicts require a deliberate refresh.
function fillProfileForm() {
  const profile = state.profile,
    form = $("profileForm");
  for (const key of ["targetRoles", "preferredCities", "priorities"])
    form.elements[key].value = (profile[key] || []).join("，");
  for (const key of ["salaryMin", "salaryMax", "notes"])
    form.elements[key].value = profile[key] ?? "";
  form.elements.remoteAccepted.checked = profile.remoteAccepted;
  state.profileDirty = false;
  state.profileDraftRevision = profile.revision ?? 0;
}
$("reloadProfile").addEventListener("click", async () => {
  try {
    state.profile = await api.request("/api/career/profile");
    fillProfileForm();
    message("profileMessage", "已重新载入服务器保存的偏好，未保存修改已清除。");
  } catch (error) {
    if (!isAborted(error)) message("profileMessage", error.message, true);
  }
});
$("profileForm").addEventListener("input", () => (state.profileDirty = true));
$("profileForm").addEventListener("submit", (event) => {
  event.preventDefault();
  submit("profileForm", "profileMessage", async () => {
    if (!state.profile) throw new Error("偏好尚未读取完成，请刷新后重试。");
    const values = fields($("profileForm"));
    const body = {
      targetRoles: splitList(values.targetRoles, 6, 80, "期望岗位"),
      preferredCities: splitList(values.preferredCities, 8, 40, "意向城市"),
      priorities: splitList(values.priorities, 8, 40, "看重的因素"),
      ...salaryRange(values),
      remoteAccepted: $("profileForm").elements.remoteAccepted.checked,
      notes: values.notes.trim(),
      revision: state.profileDraftRevision,
    };
    state.profile = await api.request("/api/career/profile", {
      method: "PUT",
      body,
    });
    fillProfileForm();
    message("profileMessage", "偏好已保存。");
  });
});
$("jobForm").elements.collectedAt.value = today();
$("jobForm").addEventListener("submit", (event) => {
  event.preventDefault();
  submit("jobForm", "jobMessage", async () => {
    const values = fields($("jobForm"));
    if (
      values.expiresAt &&
      values.collectedAt &&
      values.expiresAt < values.collectedAt
    )
      throw new Error("截止日期不能早于采集日期。");
    const companyId = Number(values.companyId) || null;
    if (companyId) {
      const company = state.companies.find((x) => x.id === companyId);
      if (company && company.name !== values.companyName.trim())
        throw new Error("公司名称与关联档案不同，请统一名称或取消关联。");
    }
    await api.request("/api/career/jobs", {
      method: "POST",
      body: {
        title: values.title.trim(),
        companyName: values.companyName.trim(),
        companyId,
        location: values.location.trim(),
        ...salaryRange(values),
        description: values.description.trim(),
        sourceUrl: sourceUrl(values.sourceUrl),
        collectedAt: values.collectedAt || null,
        expiresAt: values.expiresAt || null,
      },
    });
    $("jobForm").reset();
    $("jobForm").elements.collectedAt.value = today();
    message("jobMessage", "岗位已收藏。");
    await loadData();
  });
});
$("jobCompanySelect").addEventListener("change", () => {
  const company = state.companies.find(
    (x) => x.id === Number($("jobCompanySelect").value),
  );
  if (company) $("jobForm").elements.companyName.value = company.name;
});
function resetCompany() {
  $("companyForm").reset();
  $("companyForm").elements.id.value = "";
  $("companyForm").elements.revision.value = "0";
  $("evidenceRows").replaceChildren();
  $("companyEditorTitle").textContent = "＋ 新建公司档案";
  message("companyMessage");
}
$("resetCompany").addEventListener("click", resetCompany);
$("addEvidence").addEventListener("click", () => {
  const count = $("evidenceRows").children.length;
  if (count >= 30)
    return message("companyMessage", "每家公司最多保留 30 条信息。", true);
  $("evidenceRows").insertAdjacentHTML("beforeend", evidenceRow({}, count + 1));
});
$("companyForm").addEventListener("submit", (event) => {
  event.preventDefault();
  submit("companyForm", "companyMessage", async () => {
    const values = fields($("companyForm"));
    const evidence = [...$("evidenceRows").children].map((row) => {
      const item = Object.fromEntries(
        [...row.querySelectorAll("[data-field]")].map((input) => [
          input.dataset.field,
          input.value.trim(),
        ]),
      );
      item.sourceUrl = sourceUrl(item.sourceUrl);
      return item;
    });
    await api.request(
      `/api/career/companies${values.id ? `/${Number(values.id)}` : ""}`,
      {
        method: values.id ? "PUT" : "POST",
        body: {
          name: values.name.trim(),
          industry: values.industry.trim(),
          website: sourceUrl(values.website),
          evidence,
          revision: Number(values.revision),
        },
      },
    );
    resetCompany();
    message("companyMessage", "公司档案已保存。");
    await loadData();
  });
});
$("stageSelect").innerHTML = Object.entries(stages)
  .map(([key, label]) => `<option value="${key}">${label}</option>`)
  .join("");
$("closeApplication").addEventListener(
  "click",
  () => ($("applicationEditor").hidden = true),
);
$("applicationForm").addEventListener("submit", (event) => {
  event.preventDefault();
  submit("applicationForm", "applicationMessage", async () => {
    const values = fields($("applicationForm"));
    await api.request(`/api/career/applications/${Number(values.id)}`, {
      method: "PATCH",
      body: {
        stage: values.stage,
        notes: values.notes.trim(),
        nextActionAt: values.nextActionAt || null,
        revision: Number(values.revision),
      },
    });
    message("applicationMessage", "进度已更新。");
    await loadData();
  });
});

// One delegated action handler keeps generated record markup free from inline JavaScript.
document.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button || button.disabled) return;
  const action = button.dataset.action,
    id = Number(button.dataset.id),
    epoch = api.epoch;
  if (action === "remove-evidence") {
    button.closest(".evidence-row").remove();
    return;
  }
  const key = `${action}:${id}`;
  if (state.busy.has(key)) return;
  state.busy.add(key);
  button.disabled = true;
  try {
    if (action === "archive-job") {
      const job = state.jobs.find((x) => x.id === id);
      if (!job) return;
      await api.request(`/api/career/jobs/${id}`, {
        method: "PATCH",
        body: {
          status: job.status === "ARCHIVED" ? "ACTIVE" : "ARCHIVED",
          revision: job.revision,
        },
      });
      await loadData();
    } else if (action === "follow-job") {
      const item = await api.request("/api/career/applications", {
        method: "POST",
        body: { jobId: id, stage: "SAVED", notes: "", nextActionAt: null },
      });
      await loadData();
      editApplication(item);
      $("applications").scrollIntoView({ behavior: "smooth" });
    } else if (action === "edit-company") {
      const company = await api.request(`/api/career/companies/${id}`),
        form = $("companyForm");
      for (const key of ["id", "revision", "name", "industry", "website"])
        form.elements[key].value = company[key] ?? "";
      $("evidenceRows").innerHTML = (company.evidence || [])
        .map((item, index) => evidenceRow(item, index + 1))
        .join("");
      $("companyEditorTitle").textContent = `编辑：${company.name}`;
      $("companyEditor").open = true;
      message("companyMessage");
      $("companyEditor").scrollIntoView({ behavior: "smooth", block: "start" });
    } else if (action === "edit-application") {
      editApplication(await api.request(`/api/career/applications/${id}`));
    } else if (action === "analyze-job" || action === "analyze-company") {
      if (state.activeTaskId)
        throw new Error("请先结束当前任务，再选择新的分析对象。");
      const form = $("assistantForm");
      if (action === "analyze-job") {
        const job = state.jobs.find((x) => x.id === id);
        if (!job) return;
        form.elements.jobId.value = String(id);
        form.elements.intent.value = "MATCH";
        form.elements.companyId.value = job.companyId || "";
        form.elements.targetRole.value = job.title.slice(0, 100);
      } else {
        form.elements.companyId.value = String(id);
        form.elements.intent.value = "COMPANY";
      }
      $("assistant").scrollIntoView({ behavior: "smooth", block: "start" });
      message("agentMessage", "已选好分析对象，确认简历版本后点击“开始分析”。");
    }
  } catch (error) {
    if (!isAborted(error) && epoch === api.epoch)
      message("pageMessage", error.message, true);
  } finally {
    if (epoch === api.epoch) {
      state.busy.delete(key);
      button.disabled = false;
    }
  }
});
function editApplication(item) {
  if (!item) return;
  const form = $("applicationForm");
  for (const key of ["id", "revision", "stage", "notes"])
    form.elements[key].value = item[key] ?? "";
  form.elements.nextActionAt.value = (item.nextActionAt || "").slice(0, 16);
  $("applicationTitle").textContent = `${item.jobTitle} · ${item.companyName}`;
  $("applicationEditor").hidden = false;
  $("applicationEditor").open = true;
  message("applicationMessage");
  $("applicationEditor").scrollIntoView({
    behavior: "smooth",
    block: "nearest",
  });
}

// A task POST is never retried: it could consume another analysis quota.
// Only idempotent polling reads get a small, bounded network retry budget.
function updateAgentControls() {
  const active = Boolean(state.activeTaskId);
  $("startAgent").disabled =
    active || state.polling || !$("resumeSelect").value || !state.versionReady;
  $("cancelAgent").disabled = !active;
  $("resumeAgent").hidden = !active || state.polling;
  for (const element of $("assistantForm").querySelectorAll("select,input"))
    element.disabled = active || state.polling;
  $("versionSelect").disabled = active || state.polling || !state.versionReady;
}
$("assistantForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (state.activeTaskId || state.polling) return;
  if (!state.versionReady)
    return message(
      "agentMessage",
      "请等待版本读取完成，读取失败时请刷新。",
      true,
    );
  const values = fields($("assistantForm")),
    epoch = api.epoch;
  if (!values.resumeId)
    return message("agentMessage", "请先在简历工作台导入简历。", true);
  if (values.intent === "MATCH" && !values.jobId)
    return message("agentMessage", "岗位对比需要先选择一个收藏岗位。", true);
  if (values.intent === "COMPANY" && !values.companyId)
    return message("agentMessage", "公司分析需要先选择一个公司档案。", true);
  const operation = ++state.agentEpoch;
  state.polling = true;
  updateAgentControls();
  message("agentMessage", "正在创建求职分析任务…");
  $("agentResult").hidden = true;
  try {
    const task = await api.request("/api/career/assistant/tasks", {
      method: "POST",
      body: {
        resumeId: Number(values.resumeId),
        versionId: Number(values.versionId) || null,
        jobId: Number(values.jobId) || null,
        companyId: Number(values.companyId) || null,
        targetRole: values.targetRole.trim(),
        intent: values.intent,
      },
    });
    if (!Number.isSafeInteger(task.id) || task.id <= 0)
      throw new Error("服务未返回有效任务编号，请刷新任务状态。");
    setActiveTask(task.id);
    updateAgentControls();
    await pollTask(task.id);
  } catch (error) {
    if (!isAborted(error) && epoch === api.epoch)
      message(
        "agentMessage",
        `${error.message}${state.activeTaskId ? " 可继续查看或取消当前任务。" : ""}`,
        true,
      );
  } finally {
    if (epoch === api.epoch && operation === state.agentEpoch) {
      state.polling = false;
      updateAgentControls();
    }
  }
});
const terminal = new Set(["SUCCEEDED", "FAILED", "CANCELLED", "NEEDS_INPUT"]);
async function pollTask(taskId) {
  const generation = ++state.pollEpoch,
    epoch = api.epoch;
  const timeout = Math.min(
    600,
    Math.max(30, Number(state.capabilities?.timeoutSeconds) || 180),
  );
  const deadline = Date.now() + (timeout + 30) * 1000;
  let failures = 0;
  while (generation === state.pollEpoch && epoch === api.epoch) {
    let task;
    try {
      task = await api.request(`/api/analysis/tasks/${taskId}`);
      failures = 0;
    } catch (error) {
      if ([400, 403, 404].includes(error.status)) setActiveTask(null);
      if (
        isAborted(error) ||
        (error.status && error.status < 500) ||
        ++failures > 3
      )
        throw error;
      message(
        "agentMessage",
        `读取任务暂时失败，正在重新连接（${failures}/3）…`,
      );
      await new Promise((resolve) =>
        setTimeout(resolve, Math.min(4000, 750 * 2 ** failures)),
      );
      continue;
    }
    if (generation !== state.pollEpoch || epoch !== api.epoch) return;
    $("agentResult").hidden = false;
    $("agentResult").innerHTML = renderAgentResult(task);
    message(
      "agentMessage",
      `任务 #${taskId} · ${task.message || task.status}`,
      ["FAILED", "NEEDS_INPUT"].includes(task.status),
    );
    if (terminal.has(task.status)) {
      setActiveTask(null);
      if (task.status === "FAILED")
        message(
          "agentMessage",
          task.errorMessage || task.message || "任务执行失败，请查看工具记录。",
          true,
        );
      return;
    }
    if (Date.now() > deadline)
      throw new Error("等待已超时，任务可能仍在服务端执行。");
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
}
$("resumeAgent").addEventListener("click", async () => {
  if (!state.activeTaskId || state.polling) return;
  const epoch = api.epoch,
    operation = ++state.agentEpoch;
  state.polling = true;
  updateAgentControls();
  try {
    await pollTask(state.activeTaskId);
  } catch (error) {
    if (!isAborted(error) && epoch === api.epoch)
      message("agentMessage", error.message, true);
  } finally {
    if (epoch === api.epoch && operation === state.agentEpoch) {
      state.polling = false;
      updateAgentControls();
    }
  }
});
$("cancelAgent").addEventListener("click", async () => {
  const id = state.activeTaskId,
    epoch = api.epoch;
  if (!id || state.busy.has("cancel")) return;
  state.busy.add("cancel");
  $("cancelAgent").disabled = true;
  try {
    const task = await api.request(`/api/analysis/tasks/${id}`, {
      method: "DELETE",
    });
    if (state.activeTaskId !== id) return;
    message("agentMessage", task.message || "已请求取消。");
    if (terminal.has(task.status)) {
      state.pollEpoch++;
      state.agentEpoch++;
      setActiveTask(null);
      state.polling = false;
      $("agentResult").hidden = false;
      $("agentResult").innerHTML = renderAgentResult(task);
    }
  } catch (error) {
    if (!isAborted(error) && epoch === api.epoch)
      message("agentMessage", error.message, true);
  } finally {
    if (epoch === api.epoch) {
      state.busy.delete("cancel");
      updateAgentControls();
    }
  }
});

setAuthenticated(Boolean(api.token));
renderLists();
updateAgentControls();
async function recoverTask() {
  const id = state.activeTaskId;
  if (!id || !api.token) return;
  const epoch = api.epoch;
  try {
    // The server checks ownership before any prior task content is displayed.
    const task = await api.request(`/api/analysis/tasks/${id}`);
    if (epoch !== api.epoch || state.activeTaskId !== id) return;
    $("agentResult").innerHTML = renderAgentResult(task);
    $("agentResult").hidden = false;
    if (terminal.has(task.status)) {
      setActiveTask(null);
      message(
        "agentMessage",
        `上次任务 #${id} · ${task.message || task.status}`,
      );
    } else
      message(
        "agentMessage",
        `已恢复本账号任务 #${id}。可继续查看结果，或取消后开始新的分析。`,
      );
  } catch (error) {
    if (isAborted(error) || epoch !== api.epoch) return;
    if ([400, 403, 404].includes(error.status)) {
      setActiveTask(null);
      message(
        "agentMessage",
        "上次任务不存在或不属于当前账号，已清除恢复记录。",
        true,
      );
    } else
      message(
        "agentMessage",
        "暂时无法恢复上次任务，可点击继续查看重试。",
        true,
      );
  } finally {
    if (epoch === api.epoch) updateAgentControls();
  }
}
if (api.token) loadData(true).then(recoverTask);
else setActiveTask(null);
