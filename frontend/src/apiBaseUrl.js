const DEFAULT_API_BASE_URL = "/api";
const INVALID_API_BASE_URL_MESSAGE =
  "VITE_API_BASE_URL must be /api or an absolute HTTP/HTTPS URL ending in /api";

export function normalizeApiBaseUrl(value) {
  const candidate = value?.trim() ?? "";

  if (!candidate || candidate === "/api" || candidate === "/api/") {
    return DEFAULT_API_BASE_URL;
  }

  if (
    candidate.startsWith("/") ||
    candidate.includes("?") ||
    candidate.includes("#")
  ) {
    throw new Error(INVALID_API_BASE_URL_MESSAGE);
  }

  let url;
  try {
    url = new URL(candidate);
  } catch {
    throw new Error(INVALID_API_BASE_URL_MESSAGE);
  }

  if (
    (url.protocol !== "http:" && url.protocol !== "https:") ||
    url.username ||
    url.password ||
    (url.pathname !== "/api" && url.pathname !== "/api/")
  ) {
    throw new Error(INVALID_API_BASE_URL_MESSAGE);
  }

  return `${url.origin}${DEFAULT_API_BASE_URL}`;
}
