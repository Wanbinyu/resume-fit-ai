const ADMIN_TOKEN_KEY = "resume-fit-ai-admin-token-v1";
const form = document.querySelector("#statsLoginForm");
const tokenInput = document.querySelector("#statsToken");
const message = document.querySelector("#statsMessage");
const dashboard = document.querySelector("#statsDashboard");
const refreshButton = document.querySelector("#refreshStatsBtn");

const output = {
  usageUsers: document.querySelector("#usageUsers"),
  usageClicks: document.querySelector("#usageClicks"),
  dailyAccepted: document.querySelector("#dailyAccepted"),
  dailyLimit: document.querySelector("#dailyLimit"),
  succeededRequests: document.querySelector("#succeededRequests"),
  failedRequests: document.querySelector("#failedRequests"),
  averageDuration: document.querySelector("#averageDuration"),
  lastGeneratedAt: document.querySelector("#lastGeneratedAt"),
  uptime: document.querySelector("#uptime")
};

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = tokenInput.value.trim();
  if (!token) return;
  sessionStorage.setItem(ADMIN_TOKEN_KEY, token);
  await loadStats(token);
});

refreshButton.addEventListener("click", () => loadStats(sessionStorage.getItem(ADMIN_TOKEN_KEY) || ""));

async function loadStats(token) {
  if (!token) return;
  message.textContent = "正在读取统计...";
  try {
    const response = await fetch("/api/admin/stats", {
      headers: { Authorization: `Bearer ${token}` }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(response.status === 401 ? "统计令牌不正确。" : data.error || "统计读取失败。");
    renderStats(data);
    dashboard.hidden = false;
    message.textContent = "统计已更新。";
  } catch (error) {
    dashboard.hidden = true;
    message.textContent = error.message;
  }
}

function renderStats(data) {
  output.usageUsers.textContent = formatNumber(data.fullGeneration?.approximateUniqueBrowsers);
  output.usageClicks.textContent = formatNumber(data.fullGeneration?.totalClicks);
  output.dailyAccepted.textContent = formatNumber(data.dailyUsage?.accepted);
  output.dailyLimit.textContent = `每日上限 ${formatNumber(data.dailyUsage?.limit)}`;
  output.succeededRequests.textContent = formatNumber(data.requests?.succeeded);
  output.failedRequests.textContent = formatNumber(data.requests?.failed);
  output.averageDuration.textContent = formatDuration(data.requests?.averageDurationMs);
  output.lastGeneratedAt.textContent = formatDateTime(data.fullGeneration?.lastGeneratedAt);
  output.uptime.textContent = formatUptime(data.uptimeSeconds);
}

function formatNumber(value) {
  return new Intl.NumberFormat("zh-CN").format(Math.max(0, Number(value) || 0));
}

function formatDuration(value) {
  const milliseconds = Math.max(0, Number(value) || 0);
  return milliseconds >= 1000 ? `${(milliseconds / 1000).toFixed(1)} 秒` : `${Math.round(milliseconds)} 毫秒`;
}

function formatDateTime(value) {
  if (!value) return "暂无";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "暂无" : new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function formatUptime(value) {
  const totalMinutes = Math.floor(Math.max(0, Number(value) || 0) / 60);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  return [days ? `${days} 天` : "", hours ? `${hours} 小时` : "", `${minutes} 分钟`].filter(Boolean).join(" ");
}

const storedToken = sessionStorage.getItem(ADMIN_TOKEN_KEY) || "";
if (storedToken) {
  tokenInput.value = storedToken;
  loadStats(storedToken);
}
