// Only authentication is stored locally. Resumes and employer notes stay on the server.
const TOKEN_KEY = "resume-fit-user-token";
export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}
export class ApiClient {
  constructor(onExpired) {
    this.token = localStorage.getItem(TOKEN_KEY) || "";
    this.epoch = 0;
    this.controller = new AbortController();
    this.onExpired = onExpired;
  }
  setToken(token) {
    this.controller.abort();
    this.controller = new AbortController();
    this.epoch++;
    this.token = token || "";
    if (this.token) localStorage.setItem(TOKEN_KEY, this.token);
    else localStorage.removeItem(TOKEN_KEY);
  }
  async request(path, { method = "GET", body, anonymous = false } = {}) {
    const epoch = this.epoch;
    let response;
    try {
      response = await fetch(path, {
        method,
        cache: "no-store",
        signal: AbortSignal.any([
          this.controller.signal,
          AbortSignal.timeout(30000),
        ]),
        headers: {
          Accept: "application/json",
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
          ...(!anonymous && this.token
            ? { Authorization: `Bearer ${this.token}` }
            : {}),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
    } catch (error) {
      if (error.name === "AbortError") throw error;
      if (error.name === "TimeoutError")
        throw new ApiError("请求等待超过 30 秒，请检查服务后重试。");
      throw new ApiError("无法连接服务，请检查网络后重试。");
    }
    if (epoch !== this.epoch)
      throw new DOMException("账号已切换", "AbortError");
    if (response.status === 401 && !anonymous) {
      this.setToken("");
      this.onExpired();
      throw new ApiError("登录已失效，请重新登录。", 401);
    }
    let payload;
    try {
      payload = await response.json();
    } catch {
      throw new ApiError("服务未返回有效数据，请稍后重试。", response.status);
    }
    if (epoch !== this.epoch)
      throw new DOMException("账号已切换", "AbortError");
    if (!response.ok || payload.success === false) {
      const fallback =
        response.status === 403
          ? "没有操作权限，当前登录已保留。"
          : response.status === 409
            ? "此记录已被其他页面更新。请刷新后重新编辑，避免覆盖新内容。"
            : response.status === 429
              ? "操作较频繁或额度已用完，请稍后重试。"
              : "请求失败，请稍后重试。";
      throw new ApiError(
        response.status === 409 ? fallback : payload.message || fallback,
        response.status,
      );
    }
    return payload.data;
  }
}

export function splitList(value, maxItems, maxLength, label) {
  const values = [
    ...new Set(
      String(value || "")
        .split(/[,，、;；\n]+/)
        .map((x) => x.trim())
        .filter(Boolean),
    ),
  ];
  if (values.length > maxItems || values.some((x) => x.length > maxLength))
    throw new Error(
      `${label}最多 ${maxItems} 项，每项不超过 ${maxLength} 字。`,
    );
  return values;
}
export function optionalNumber(value, label = "金额") {
  if (value === "" || value == null) return null;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0 || parsed > 2000000)
    throw new Error(`${label}请填写 0～2000000 的整数，未知时留空。`);
  return parsed;
}
export function salaryRange(values) {
  const salaryMin = optionalNumber(values.salaryMin, "最低月薪"),
    salaryMax = optionalNumber(values.salaryMax, "最高月薪");
  if (salaryMin !== null && salaryMax !== null && salaryMin > salaryMax)
    throw new Error("最低月薪不能高于最高月薪。");
  return { salaryMin, salaryMax };
}
export function safeHttps(value) {
  if (!value) return "";
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      !host.includes(".") ||
      host === "localhost" ||
      host.endsWith(".localhost") ||
      host.endsWith(".local") ||
      /^127\.|^10\.|^192\.168\.|^169\.254\.|^0\.|^172\.(1[6-9]|2\d|3[01])\./.test(
        host,
      )
    )
      return "";
    return url.href;
  } catch {
    return "";
  }
}
export function sourceUrl(value) {
  const text = String(value || "").trim();
  if (text && !safeHttps(text))
    throw new Error("来源和官网请填写可公开访问的 HTTPS 地址，不含账号密码。");
  return text;
}
export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
export const isAborted = (error) => error?.name === "AbortError";
