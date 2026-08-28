import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

class MemoryKv {
  constructor() { this.values = new Map(); }
  async get(key, options) {
    const value = this.values.get(key) ?? null;
    return value && options?.type === "json" ? JSON.parse(value) : value;
  }
  async put(key, value) { this.values.set(key, value); }
  async delete(key) { this.values.delete(key); }
}

const env = () => ({ FORM_ROUTES: new MemoryKv(), PUBLIC_ORIGIN: "https://forms.swapnopay.top" });

test("health endpoint is available", async () => {
  const response = await worker.fetch(new Request("https://forms.swapnopay.top/health"), env());
  assert.equal(response.status, 200);
  assert.equal((await response.json()).service, "swapnopay-form-router");
});

test("unknown public routes return a branded 404", async () => {
  const response = await worker.fetch(new Request("https://forms.swapnopay.top/f/0123456789abcdef0123456789abcdef"), env());
  assert.equal(response.status, 404);
  assert.match(await response.text(), /Form not found/);
});

test("merchant-verified registration creates a stable route and proxies it", async () => {
  const testEnv = env();
  const originalFetch = globalThis.fetch;
  const formId = "7bf835b2-9f52-4baf-a8b5-0fd709985507";
  const publicId = formId.replaceAll("-", "");
  const calls = [];
  globalThis.fetch = async (input) => {
    const rawUrl = input instanceof URL ? input.href : typeof input === "string" ? input : input.url;
    const url = new URL(rawUrl);
    calls.push(url.toString());
    if (url.pathname === "/auth/v1/user") return Response.json({ id: "9880b3b6-c81c-441b-b6bc-45ce07ba4d3e" });
    if (url.pathname === "/rest/v1/payment_forms") return Response.json([{ id: formId, slug: "product-order", status: "PUBLISHED" }]);
    if (url.pathname === "/functions/v1/hosted-form") return new Response("<h1>Hosted form</h1>", { headers: { "content-type": "text/html" } });
    return new Response(null, { status: 404 });
  };
  try {
    const registration = await worker.fetch(new Request("https://forms.swapnopay.top/v1/routes", {
      method: "POST",
      headers: { authorization: `Bearer ${"a".repeat(40)}`, "content-type": "application/json" },
      body: JSON.stringify({
        project_url: "https://abcdefghijklmnopqrst.supabase.co",
        publishable_key: `sb_publishable_${"b".repeat(32)}`,
        form_id: formId,
        slug: "product-order",
      }),
    }), testEnv);
    assert.equal(registration.status, 201);
    const route = await registration.json();
    assert.equal(route.public_url, `https://forms.swapnopay.top/f/${publicId}`);

    const proxied = await worker.fetch(new Request(`https://forms.swapnopay.top/f/${publicId}?action=status&order_id=${formId}`), testEnv);
    assert.equal(proxied.status, 200);
    assert.equal(await proxied.text(), "<h1>Hosted form</h1>");
    assert.ok(calls.some((url) => url.includes(`/functions/v1/hosted-form?slug=product-order&action=status&order_id=${formId}`)));
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("registration rejects an unverified project", async () => {
  const response = await worker.fetch(new Request("https://forms.swapnopay.top/v1/routes", {
    method: "POST",
    headers: { authorization: `Bearer ${"a".repeat(40)}`, "content-type": "application/json" },
    body: JSON.stringify({ project_url: "https://attacker.example", publishable_key: "not-a-key", form_id: "bad", slug: "bad" }),
  }), env());
  assert.equal(response.status, 400);
});
