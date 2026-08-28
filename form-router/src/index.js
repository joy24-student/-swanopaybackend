const ROUTE_PREFIX = "form-route:";
const MAX_REGISTRATION_BYTES = 16_384;
const ALLOWED_PROXY_QUERY = new Set(["action", "order_id", "submission_id", "field_id"]);

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (request.method === "OPTIONS") return optionsResponse();
      if (request.method === "GET" && url.pathname === "/health") {
        return json({ ok: true, service: "swapnopay-form-router" }, 200, { "Cache-Control": "no-store" });
      }
      if (request.method === "POST" && url.pathname === "/v1/routes") {
        return registerRoute(request, env);
      }
      const managementMatch = url.pathname.match(/^\/v1\/routes\/([0-9a-f]{32})$/i);
      if (request.method === "DELETE" && managementMatch) {
        return deleteRoute(request, env, managementMatch[1].toLowerCase());
      }
      const publicMatch = url.pathname.match(/^\/f\/([0-9a-f]{32})\/?$/i);
      if (publicMatch) return proxyHostedForm(request, env, publicMatch[1].toLowerCase());
      if (request.method === "GET" && url.pathname === "/") {
        return html("SwapnoPay Forms", "Open the complete form link supplied by the merchant.", 200);
      }
      return html("Form not found", "Check the form link and try again.", 404);
    } catch (error) {
      console.error("Form router request failed", error instanceof Error ? error.message : error);
      return html("Service unavailable", "The form service is temporarily unavailable. Please try again.", 503);
    }
  },
};

async function registerRoute(request, env) {
  const input = await readJsonObject(request);
  if (!input.ok) return json({ error: input.error }, input.status);
  const projectUrl = normalizeProjectUrl(input.value.project_url);
  const publishableKey = String(input.value.publishable_key || "").trim();
  const formId = normalizeUuid(input.value.form_id);
  const slug = normalizeSlug(input.value.slug);
  const bearer = bearerToken(request);
  if (!projectUrl || !formId || !slug || !validPublicKey(publishableKey)) {
    return json({ error: "Invalid project or form registration" }, 400);
  }
  if (!bearer) return json({ error: "Merchant authentication is required" }, 401);

  const verified = await verifyMerchantForm(projectUrl, publishableKey, bearer, formId, slug, true);
  if (!verified.ok) return json({ error: verified.error }, verified.status);

  const publicId = formId.replaceAll("-", "");
  const key = ROUTE_PREFIX + publicId;
  const existing = await env.FORM_ROUTES.get(key, { type: "json" });
  if (existing && (existing.projectUrl !== projectUrl || existing.formId !== formId)) {
    return json({ error: "Public route collision" }, 409);
  }
  const now = new Date().toISOString();
  const route = {
    version: 1,
    publicId,
    projectUrl,
    formId,
    slug,
    registeredBy: verified.userId,
    createdAt: existing?.createdAt || now,
    updatedAt: now,
  };
  await env.FORM_ROUTES.put(key, JSON.stringify(route));
  const publicOrigin = normalizePublicOrigin(env.PUBLIC_ORIGIN) || new URL(request.url).origin;
  return json({ public_id: publicId, public_url: `${publicOrigin}/f/${publicId}` }, existing ? 200 : 201, {
    "Cache-Control": "no-store",
  });
}

async function deleteRoute(request, env, publicId) {
  const key = ROUTE_PREFIX + publicId;
  const route = await env.FORM_ROUTES.get(key, { type: "json" });
  if (!route) return json({ error: "Route not found" }, 404);
  const input = await readJsonObject(request);
  if (!input.ok) return json({ error: input.error }, input.status);
  const publishableKey = String(input.value.publishable_key || "").trim();
  const bearer = bearerToken(request);
  if (!bearer || !validPublicKey(publishableKey)) return json({ error: "Merchant authentication is required" }, 401);
  const verified = await verifyMerchantForm(route.projectUrl, publishableKey, bearer, route.formId, route.slug, false);
  if (!verified.ok) return json({ error: verified.error }, verified.status);
  await env.FORM_ROUTES.delete(key);
  return new Response(null, { status: 204, headers: securityHeaders({ "Cache-Control": "no-store" }) });
}

async function proxyHostedForm(request, env, publicId) {
  if (!["GET", "POST"].includes(request.method)) return json({ error: "Method not allowed" }, 405);
  const route = await env.FORM_ROUTES.get(ROUTE_PREFIX + publicId, { type: "json", cacheTtl: 60 });
  if (!route || !normalizeProjectUrl(route.projectUrl) || !normalizeSlug(route.slug)) {
    return html("Form not found", "This form link is invalid or no longer active.", 404);
  }

  const incomingUrl = new URL(request.url);
  const target = new URL("/functions/v1/hosted-form", route.projectUrl);
  target.searchParams.set("slug", route.slug);
  for (const [name, value] of incomingUrl.searchParams) {
    if (ALLOWED_PROXY_QUERY.has(name)) target.searchParams.set(name, value.slice(0, 200));
  }

  const headers = new Headers();
  for (const name of ["accept", "content-type", "idempotency-key", "authorization", "apikey", "x-client-info"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("x-swapnopay-route", publicId);

  let upstream;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "POST" ? request.body : undefined,
      redirect: "manual",
    });
  } catch (error) {
    console.error("Hosted form origin failed", route.projectUrl, error instanceof Error ? error.message : error);
    return html("Service unavailable", "The merchant form is temporarily unavailable. Please retry shortly.", 502);
  }

  const responseHeaders = new Headers();
  for (const name of [
    "content-type", "cache-control", "content-security-policy", "referrer-policy", "permissions-policy",
    "x-content-type-options", "retry-after", "access-control-allow-origin", "access-control-allow-headers",
    "access-control-allow-methods", "location",
  ]) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  responseHeaders.set("X-Content-Type-Options", "nosniff");
  responseHeaders.set("X-Robots-Tag", "noindex, nofollow");
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

async function verifyMerchantForm(projectUrl, publishableKey, bearer, formId, slug, requirePublished) {
  const authHeaders = { apikey: publishableKey, Authorization: `Bearer ${bearer}`, Accept: "application/json" };
  let userResponse;
  try {
    userResponse = await fetch(`${projectUrl}/auth/v1/user`, { headers: authHeaders, redirect: "manual" });
  } catch {
    return { ok: false, status: 502, error: "Unable to verify the merchant project" };
  }
  if (!userResponse.ok) return { ok: false, status: 401, error: "Merchant session is invalid or expired" };
  const user = await userResponse.json().catch(() => ({}));
  if (!normalizeUuid(user.id)) return { ok: false, status: 401, error: "Merchant identity could not be verified" };

  const formUrl = new URL("/rest/v1/payment_forms", projectUrl);
  formUrl.searchParams.set("id", `eq.${formId}`);
  formUrl.searchParams.set("slug", `eq.${slug}`);
  formUrl.searchParams.set("select", "id,slug,status");
  const formResponse = await fetch(formUrl, { headers: authHeaders, redirect: "manual" }).catch(() => null);
  if (!formResponse) return { ok: false, status: 502, error: "Unable to verify form ownership" };
  if (!formResponse.ok) return { ok: false, status: 403, error: "Form ownership verification failed" };
  const rows = await formResponse.json().catch(() => []);
  const form = Array.isArray(rows) ? rows[0] : null;
  if (!form || normalizeUuid(form.id) !== formId || normalizeSlug(form.slug) !== slug) {
    return { ok: false, status: 403, error: "The authenticated merchant does not own this form" };
  }
  if (requirePublished && String(form.status || "").toUpperCase() !== "PUBLISHED") {
    return { ok: false, status: 409, error: "Publish the form before creating its public route" };
  }
  return { ok: true, userId: String(user.id) };
}

async function readJsonObject(request) {
  const declaredLength = Number(request.headers.get("content-length") || "0");
  if (declaredLength > MAX_REGISTRATION_BYTES) return { ok: false, status: 413, error: "Request is too large" };
  try {
    const text = await request.text();
    if (new TextEncoder().encode(text).byteLength > MAX_REGISTRATION_BYTES) return { ok: false, status: 413, error: "Request is too large" };
    const value = JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("object required");
    return { ok: true, value };
  } catch {
    return { ok: false, status: 400, error: "Request body must be a JSON object" };
  }
}

function normalizeProjectUrl(value) {
  try {
    const url = new URL(String(value || "").trim());
    if (url.protocol !== "https:" || url.username || url.password || url.port || url.pathname !== "/" || url.search || url.hash) return "";
    if (!/^[a-z0-9]{15,32}\.supabase\.co$/i.test(url.hostname)) return "";
    return url.origin;
  } catch {
    return "";
  }
}

function normalizePublicOrigin(value) {
  try {
    const url = new URL(String(value || "").trim());
    return url.protocol === "https:" && !url.username && !url.password && url.pathname === "/" && !url.search && !url.hash ? url.origin : "";
  } catch {
    return "";
  }
}

function normalizeUuid(value) {
  const text = String(value || "").trim().toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(text) ? text : "";
}

function normalizeSlug(value) {
  const text = String(value || "").trim().toLowerCase();
  return text.length <= 100 && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(text) ? text : "";
}

function validPublicKey(value) {
  return typeof value === "string" && value.length >= 20 && value.length <= 2_048 && /^[A-Za-z0-9._-]+$/.test(value);
}

function bearerToken(request) {
  const value = request.headers.get("authorization") || "";
  const match = value.match(/^Bearer\s+([^\s]{20,8192})$/i);
  return match ? match[1] : "";
}

function optionsResponse() {
  return new Response(null, {
    status: 204,
    headers: securityHeaders({
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "authorization, content-type, apikey, idempotency-key, x-client-info",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Max-Age": "86400",
    }),
  });
}

function securityHeaders(extra = {}) {
  return {
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    ...extra,
  };
}

function json(value, status, extra = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: securityHeaders({ "Content-Type": "application/json; charset=utf-8", "Access-Control-Allow-Origin": "*", ...extra }),
  });
}

function html(title, message, status) {
  const safeTitle = escapeHtml(title);
  const safeMessage = escapeHtml(message);
  return new Response(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>${safeTitle}</title><style>body{font:16px system-ui,sans-serif;background:#f7f8fc;color:#172033;margin:0;padding:32px}.card{max-width:560px;margin:10vh auto;background:white;border:1px solid #e1e5ee;border-radius:18px;padding:28px;box-shadow:0 12px 35px #17203312}h1{margin-top:0}</style></head><body><main class="card"><h1>${safeTitle}</h1><p>${safeMessage}</p></main></body></html>`, {
    status,
    headers: securityHeaders({
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
      "X-Robots-Tag": "noindex, nofollow",
    }),
  });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
}
