import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { beforeEach, test } from "node:test";
import {
  clearCsrfToken,
  csrfFetch,
  fetchCsrfToken,
  withCsrfHeaders,
} from "../src/csrf.js";

const AUTH_URL = "https://api.example.test/api/auth";
const HEADER_NAME = "X-CSRF-TOKEN";
const TOKEN = "test-only-csrf-token";

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

async function storeTestToken() {
  await fetchCsrfToken(
    AUTH_URL,
    async () => jsonResponse(200, {
      headerName: HEADER_NAME,
      token: TOKEN,
    }),
  );
}

beforeEach(() => {
  clearCsrfToken();
});

test("CSRF token is fetched with credentials and kept out of the URL", async () => {
  const calls = [];

  await fetchCsrfToken(AUTH_URL, async (url, options) => {
    calls.push({ url, options });
    return jsonResponse(200, {
      headerName: HEADER_NAME,
      token: TOKEN,
    });
  });

  assert.deepEqual(calls, [
    {
      url: `${AUTH_URL}/csrf`,
      options: {
        method: "GET",
        credentials: "include",
        cache: "no-store",
      },
    },
  ]);
  assert.doesNotMatch(calls[0].url, new RegExp(TOKEN));
});

test("only unsafe application methods receive the server-provided header", async () => {
  await storeTestToken();

  for (const method of ["POST", "PUT", "PATCH", "DELETE"]) {
    assert.deepEqual(
      withCsrfHeaders(method, { "Content-Type": "application/json" }),
      {
        "Content-Type": "application/json",
        [HEADER_NAME]: TOKEN,
      },
    );
  }

  for (const method of ["GET", "HEAD"]) {
    assert.deepEqual(withCsrfHeaders(method, { Accept: "application/json" }), {
      Accept: "application/json",
    });
  }
});

test("unsafe requests fail closed when no in-memory token is available", () => {
  assert.throws(
    () => withCsrfHeaders("POST"),
    /安全校验信息不可用，请刷新页面后重试/,
  );
});

test("refresh discards the old token even when the replacement request fails", async () => {
  await storeTestToken();

  await assert.rejects(
    fetchCsrfToken(AUTH_URL, async () => jsonResponse(503, null)),
    /HTTP 503/,
  );
  assert.throws(
    () => withCsrfHeaders("DELETE"),
    /安全校验信息不可用，请刷新页面后重试/,
  );
});

test("a 403 response is returned once without replaying the write", async () => {
  await storeTestToken();
  const calls = [];

  const response = await csrfFetch(
    "/api/customers",
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    },
    async (url, options) => {
      calls.push({ url, options });
      return jsonResponse(403, {
        status: 403,
        message: "请求安全校验失败，请刷新页面后重试",
      });
    },
  );

  assert.equal(response.status, 403);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.headers[HEADER_NAME], TOKEN);
});

test("token storage has no browser persistence, logging, or DOM output path", async () => {
  const csrfSource = await readFile(
    new URL("../src/csrf.js", import.meta.url),
    "utf8",
  );
  const appSource = await readFile(
    new URL("../src/App.vue", import.meta.url),
    "utf8",
  );
  const templateSource = appSource.split("<template>")[1] ?? "";

  assert.doesNotMatch(
    csrfSource,
    /localStorage|sessionStorage|console\.|URLSearchParams/,
  );
  assert.doesNotMatch(templateSource, /csrf/i);
});
