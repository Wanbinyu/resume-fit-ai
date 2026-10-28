import { safeHttps, today } from "./api.js";

export const stages = {
  SAVED: "已收藏",
  APPLIED: "已投递",
  CONTACTED: "已沟通",
  INTERVIEW: "面试中",
  OFFER: "已收到 Offer",
  REJECTED: "未通过",
  WITHDRAWN: "已撤回",
};
const types = { FACT: "事实记录", OPINION: "主观看法", UNVERIFIED: "待核实" };
const categories = {
  IDENTITY: "公司身份",
  BUSINESS: "业务情况",
  COMPENSATION: "薪酬待遇",
  WORKLIFE: "工作时间",
  RISK: "风险线索",
  OTHER: "其他",
};
export const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const list = (value) => (Array.isArray(value) ? value : []);
const empty = (text) => `<div class="empty">${esc(text)}</div>`;
const pill = (text, warning = false) =>
  `<span class="pill${warning ? " warning" : ""}">${esc(text)}</span>`;
const paragraphs = (values) =>
  list(values)
    .map(
      (x) =>
        `<p>${esc(typeof x === "string" ? x : x?.message || x?.notice || "")}</p>`,
    )
    .join("");
export function externalLink(url, title = "查看来源") {
  const safe = safeHttps(url);
  return safe
    ? `<a href="${esc(safe)}" target="_blank" rel="noopener noreferrer">${esc(title)} ↗</a>`
    : "";
}
export function date(value) {
  if (!value) return "未记录";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? "日期未知"
    : parsed.toLocaleDateString("zh-CN");
}
export function salary(min, max) {
  const format = (x) => Number(x).toLocaleString("zh-CN");
  if (min == null && max == null) return "薪资待确认";
  if (min == null) return `不高于 ${format(max)} 元/月`;
  if (max == null) return `${format(min)} 元/月起`;
  return `${format(min)}～${format(max)} 元/月`;
}
export function renderStats(state) {
  const active = state.jobs.filter((x) => x.status === "ACTIVE").length;
  return [
    [state.resumes.length, "简历"],
    [active, "未归档岗位"],
    [state.companies.length, "公司档案"],
    [state.applications.length, "跟进记录"],
  ]
    .map(
      ([number, label]) =>
        `<div class="stat"><strong>${number}</strong><span>${label}</span></div>`,
    )
    .join("");
}
export function renderJobs(jobs) {
  return jobs.length
    ? jobs
        .map((job) => {
          const archived = job.status === "ARCHIVED",
            expired = job.expiresAt && job.expiresAt < today();
          return `<article class="record"><h3>${esc(job.title)} ${pill(archived ? "已归档" : expired ? "已过截止日期" : "已收藏", archived || expired)}</h3>
      <p class="small muted">${esc(job.companyName)} · ${esc(job.location || "地点待确认")}<br>${esc(salary(job.salaryMin, job.salaryMax))}</p>
      <p class="small muted">采集 ${date(job.collectedAt)}${job.expiresAt ? ` · 截止 ${date(job.expiresAt)}` : ""} ${externalLink(job.sourceUrl)}</p>
      <details><summary>展开岗位描述</summary><div class="description">${esc(job.description)}</div></details>
      <div class="actions"><button data-action="analyze-job" data-id="${job.id}">分析此岗位</button><button data-action="follow-job" data-id="${job.id}" ${archived ? "disabled" : ""}>加入跟进</button><button data-action="archive-job" data-id="${job.id}">${archived ? "恢复收藏" : "归档"}</button></div></article>`;
        })
        .join("")
    : empty("暂无收藏岗位，可以先粘贴一个感兴趣的真实 JD。");
}
function evidenceHtml(evidence) {
  return list(evidence)
    .map(
      (item) =>
        `<div class="source"><h3>${pill(types[item.type] || "待核实")} ${esc(item.title)}</h3><p>${esc(item.content)}</p><span class="muted small">${esc(categories[item.category] || "其他")} · 记录 ${date(item.observedAt)} ${externalLink(item.sourceUrl)}</span></div>`,
    )
    .join("");
}
export function companyHtml(company, editable = true) {
  const assessment = company.evidenceAssessment || {};
  return `<article class="record"><h3>${esc(company.name)} ${pill("信誉信息待核实", true)}</h3><p class="small muted">${esc(company.industry || "行业未记录")} ${externalLink(company.website, "官方网站")}</p>
    <p class="small muted">事实 ${Number(assessment.factCount) || 0} · 主观看法 ${Number(assessment.opinionCount) || 0} · 待核实 ${Number(assessment.unverifiedCount) || 0} · 过期来源 ${Number(assessment.staleSourceCount) || 0}</p>
    <p class="small muted">${esc(assessment.notice || "信息不足，不能据此生成可靠的公司信誉评分。")}</p>${company.notice ? `<p class="small muted">${esc(company.notice)}</p>` : ""}
    ${list(company.evidence).length ? `<details><summary>查看 ${company.evidence.length} 条信息与来源</summary>${evidenceHtml(company.evidence)}</details>` : '<p class="small muted">还没有收集依据。</p>'}
    ${list(assessment.questions).length ? `<details><summary>需要进一步核实的问题</summary><ul class="small">${assessment.questions.map((x) => `<li>${esc(x)}</li>`).join("")}</ul></details>` : ""}
    ${editable ? `<div class="actions"><button data-action="edit-company" data-id="${company.id}">编辑档案</button><button data-action="analyze-company" data-id="${company.id}">整理公司信息</button></div>` : ""}</article>`;
}
export function renderCompanies(companies) {
  return companies.length
    ? companies.map((x) => companyHtml(x)).join("")
    : empty("暂无公司档案，添加你关心的公司并保留信息来源。");
}
export function renderApplications(applications) {
  return applications.length
    ? applications
        .map(
          (
            item,
          ) => `<article class="record"><h3>${esc(item.jobTitle)}</h3><p class="muted small">${esc(item.companyName)}</p>${pill(stages[item.stage] || item.stage)}
    <p class="small muted">${item.nextActionAt ? `下一步 ${esc(item.nextActionAt.replace("T", " ").slice(0, 16))}` : "未设置下一步时间"}</p>
    ${item.notes ? `<p class="small">${esc(item.notes)}</p>` : ""}<div class="actions"><button data-action="edit-application" data-id="${item.id}">更新进度</button></div></article>`,
        )
        .join("")
    : empty("尚无跟进记录，从收藏岗位中加入一个开始。");
}
export function evidenceRow(item = {}, index = 1) {
  const options = (map, current) =>
    Object.entries(map)
      .map(
        ([key, label]) =>
          `<option value="${key}" ${key === current ? "selected" : ""}>${esc(label)}</option>`,
      )
      .join("");
  return `<fieldset class="evidence-row"><legend>信息 ${index}</legend><div class="grid two"><label>信息性质<select data-field="type">${options(types, item.type || "UNVERIFIED")}</select></label><label>类别<select data-field="category">${options(categories, item.category || "OTHER")}</select></label></div>
    <label>标题<input data-field="title" required maxlength="160" value="${esc(item.title)}"></label><label>内容<textarea data-field="content" required maxlength="1200" rows="3">${esc(item.content)}</textarea></label>
    <label>来源地址（可选）<input data-field="sourceUrl" type="url" maxlength="1000" value="${esc(item.sourceUrl)}" placeholder="https://..."></label><label>记录日期<input data-field="observedAt" type="date" required value="${esc(item.observedAt || today())}"></label><div><button type="button" data-action="remove-evidence">移除此条</button></div></fieldset>`;
}
function section(title, body) {
  return body
    ? `<section class="result-section"><h3>${esc(title)}</h3>${body}</section>`
    : "";
}
function scoreCard(title, value, notice) {
  const known = typeof value === "number" && Number.isFinite(value);
  return `<div class="score-card"><span class="small">${esc(title)}</span><strong>${known ? esc(value) : "暂无评分"}${known ? '<span class="small"> / 100</span>' : ""}</strong><p>${esc(notice)}</p></div>`;
}
export function renderAgentResult(task) {
  const result = task.result || {},
    assistant = result.assistant || {};
  const quality = assistant.resumeQuality || result.resumeQuality;
  const hasCoverage =
    typeof result.matching?.coverage === "number" &&
    typeof result.score === "number";
  let html = result.summary ? `<p>${esc(result.summary)}</p>` : "";
  if (result.input)
    html =
      `<p class="small muted">本次使用简历 #${esc(result.input.resumeId)} · ${result.input.versionId ? `已保存版本 #${esc(result.input.versionId)}` : "原始主记录"}${result.input.frozen ? " · 提交时的内容快照" : ""}</p>` +
      html;
  if (quality || hasCoverage) {
    html +=
      '<div class="result-grid">' +
      (quality
        ? scoreCard(
            "简历完整度与表达质量",
            quality.score,
            quality.scoreMeaning ||
              quality.notice ||
              "基于原文与规则检查，不代表能力认证或录用概率。",
          )
        : "") +
      (hasCoverage
        ? scoreCard(
            "目标岗位关键词覆盖率",
            result.score,
            "只表示词语覆盖，不等于专业能力或录用概率。",
          )
        : "") +
      "</div>";
  }
  if (quality) {
    html += section(
      "简历改进线索",
      list(quality.dimensions)
        .map(
          (x) =>
            `<div class="source"><b>${esc(x.name || x.title || x.dimension || "检查维度")}</b>${typeof x.score === "number" ? ` · ${esc(x.score)}${typeof x.maximum === "number" ? ` / ${esc(x.maximum)}` : ""}` : ""}<p class="small muted">${esc(x.explanation || x.notice || x.message || x.reason || x.description || "")}</p>${x.quote ? `<blockquote>${esc(x.quote)}</blockquote>` : ""}</div>`,
        )
        .join("") + paragraphs(quality.missingInformation),
    );
  }
  html += section(
    "收藏岗位对比",
    list(assistant.jobMatches)
      .map(
        (job) =>
          `<article class="record"><h3>${esc(job.title)} · ${esc(job.companyName)} ${job.expired ? pill("已过截止日期", true) : ""}</h3><p>${typeof job.coverage === "number" ? `关键词覆盖 ${esc(job.coverage)}%` : "暂无法计算覆盖率"}</p><p class="small">已出现：${esc(list(job.matchedKeywords).join("、") || "无明确词语")}</p><p class="small">待核实：${esc(list(job.missingKeywords).join("、") || "暂无")}</p><p class="muted small">${esc(job.notice || "匹配仅供整理线索，请核实真实职责和招聘要求。")}</p>${externalLink(job.sourceUrl)}</article>`,
      )
      .join(""),
  );
  html += section(
    "公司信息与核实问题",
    list(assistant.companyEvidence)
      .map((x) => companyHtml(x, false))
      .join(""),
  );
  const advice = result.careerAdvice || {};
  html += section(
    "可能适合的岗位方向",
    list(advice.recommendedRoles)
      .map(
        (role) =>
          `<article class="record"><h3>${esc(role.title)}</h3><p>${esc(role.reason)}</p>${list(
            role.evidence,
          )
            .map((x) => `<blockquote>${esc(x)}</blockquote>`)
            .join(
              "",
            )}<p class="small muted">${esc(list(role.uncertainties).join("；"))}</p></article>`,
      )
      .join(""),
  );
  const priorities = list(advice.targetPlan?.priorities);
  html += section(
    "下一步调整方向",
    priorities
      .map(
        (x) =>
          `<div class="source"><b>${esc(x.title)}</b><p>${esc(x.action)}</p></div>`,
      )
      .join("") +
      (advice.targetPlan?.notice
        ? `<p class="small muted">${esc(advice.targetPlan.notice)}</p>`
        : ""),
  );
  if (assistant.applications) {
    const summary = assistant.applications;
    html += section(
      "跟进概览",
      `<p>共 ${Number(summary.total) || 0} 条跟进，${Array.isArray(summary.overdue) ? summary.overdue.length : Number(summary.overdue) || 0} 条需要检查计划时间。</p><p class="small muted">${Object.entries(
        summary.stageCounts || {},
      )
        .map(
          ([key, count]) => `${esc(stages[key] || key)} ${Number(count) || 0}`,
        )
        .join(
          " · ",
        )}</p><p class="small muted">${esc(summary.notice || "按已保存的进度整理。")}</p>`,
    );
  }
  html += section("说明与待核实信息", paragraphs(assistant.notices));
  const sources = list(result.sources);
  html += section(
    "检索来源",
    sources
      .map(
        (source) =>
          `<div class="source"><b>${esc(source.title || source.name || "来源")}</b><p class="small">${esc(source.snippet || source.description || "")}</p>${externalLink(source.url)}${source.publishedDate ? ` · ${date(source.publishedDate)}` : ""}</div>`,
      )
      .join(""),
  );
  const trace = list(result.agentTrace);
  html += section(
    "工具执行过程",
    trace.length
      ? `<details ${task.status === "SUCCEEDED" ? "" : "open"}><summary>查看 ${trace.length} 条执行记录</summary>${trace.map((x) => `<div class="trace-item"><strong>${esc(x.name)}</strong> · ${esc(x.status)}<br>${esc(x.message)}</div>`).join("")}</details>`
      : "",
  );
  return html || empty("任务正在运行，等待工具返回结果…");
}
