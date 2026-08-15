const CSRF_PROTECTED_METHODS = new Set([
  "POST",
  "PUT",
  "PATCH",
  "DELETE",
]);

const MISSING_CSRF_TOKEN_MESSAGE =
  "安全校验信息不可用，请刷新页面后重试";

let csrfToken = null;

export function clearCsrfToken() {
  csrfToken = null;
}

export async function fetchCsrfToken(authUrl, fetchImpl = globalThis.fetch) {
  clearCsrfToken();

  const response = await fetchImpl(`${authUrl}/csrf`, {
    method: "GET",
    credentials: "include",
    cache: "no-store",
  });
  const responseData = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(responseData?.message || `HTTP ${response.status}`);
  }

  if (
    typeof responseData?.headerName !== "string" ||
    !responseData.headerName ||
    typeof responseData?.token !== "string" ||
    !responseData.token
  ) {
    throw new Error("CSRF 响应格式不正确");
  }

  csrfToken = {
    headerName: responseData.headerName,
    token: responseData.token,
  };
}

export function withCsrfHeaders(method, headers = {}) {
  const requestHeaders = { ...headers };
  const normalizedMethod = String(method ?? "GET").toUpperCase();

  if (!CSRF_PROTECTED_METHODS.has(normalizedMethod)) {
    return requestHeaders;
  }

  if (!csrfToken) {
    throw new Error(MISSING_CSRF_TOKEN_MESSAGE);
  }

  requestHeaders[csrfToken.headerName] = csrfToken.token;
  return requestHeaders;
}

export function csrfFetch(
  input,
  options = {},
  fetchImpl = globalThis.fetch,
) {
  const method = String(options.method ?? "GET").toUpperCase();

  return fetchImpl(input, {
    ...options,
    headers: withCsrfHeaders(method, options.headers),
  });
}
