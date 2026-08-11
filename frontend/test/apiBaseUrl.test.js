import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("production source uses the configured API base URL", async () => {
  const appSource = await readFile(
    new URL("../src/App.vue", import.meta.url),
    "utf8",
  );

  assert.doesNotMatch(appSource, /https?:\/\/localhost:\d+/);
  assert.match(appSource, /import\.meta\.env\.VITE_API_BASE_URL/);
});

test("empty API base URL uses the relative /api default", async () => {
  const { normalizeApiBaseUrl } = await import("../src/apiBaseUrl.js");

  assert.equal(normalizeApiBaseUrl(undefined), "/api");
  assert.equal(normalizeApiBaseUrl(""), "/api");
  assert.equal(normalizeApiBaseUrl("   "), "/api");
  assert.equal(normalizeApiBaseUrl("/api"), "/api");
  assert.equal(normalizeApiBaseUrl("/api/"), "/api");
});

test("absolute API base URL is normalized", async () => {
  const { normalizeApiBaseUrl } = await import("../src/apiBaseUrl.js");

  assert.equal(
    normalizeApiBaseUrl("https://api.example.test/api"),
    "https://api.example.test/api",
  );
  assert.equal(
    normalizeApiBaseUrl("HTTPS://API.EXAMPLE.TEST:443/api/"),
    "https://api.example.test/api",
  );
  assert.equal(
    normalizeApiBaseUrl("http://api.example.test:8081/api"),
    "http://api.example.test:8081/api",
  );
});

test("unsupported API base URL is rejected", async () => {
  const { normalizeApiBaseUrl } = await import("../src/apiBaseUrl.js");
  const unsupportedValues = [
    "/other",
    "/api/v1",
    "//api.example.test/api",
    "ftp://api.example.test/api",
    "https://user:password@api.example.test/api",
    "https://api.example.test/api?region=test",
    "https://api.example.test/api#section",
    "https://api.example.test/other",
  ];

  for (const value of unsupportedValues) {
    assert.throws(() => normalizeApiBaseUrl(value), /VITE_API_BASE_URL/);
  }
});

test("Vite proxies the relative /api path during local development", async () => {
  const { default: config } = await import("../vite.config.js");
  const proxyTarget = new URL(config.server?.proxy?.["/api"]);

  assert.equal(proxyTarget.protocol, "http:");
  assert.equal(proxyTarget.hostname, "localhost");
  assert.equal(proxyTarget.port, "8080");
  assert.equal(proxyTarget.pathname, "/");
});
