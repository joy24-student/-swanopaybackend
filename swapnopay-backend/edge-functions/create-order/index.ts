// @ts-ignore
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
// @ts-ignore
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

declare const Deno: any;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, idempotency-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (req.method !== "POST") {
      return jsonResponse({ error: "Method not allowed" }, 405);
    }
    const contentLength = Number(req.headers.get("content-length") || "0");
    if (contentLength > 16_384) {
      return jsonResponse({ error: "Request body is too large" }, 413);
    }
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const body = await req.json();
    const authHeader = req.headers.get("authorization") || "";
    const bearerKey = authHeader.toLowerCase().startsWith("bearer ") ? authHeader.substring(7).trim() : "";
    const xApiKey = req.headers.get("x-api-key")?.trim() || "";

    // Prioritize header-based API key authentication over request body credentials
    const secretKey = (xApiKey || bearerKey || body.apiKey || body.merchantSecret)?.trim();
    if (!xApiKey && !bearerKey && (body.apiKey || body.merchantSecret)) {
      console.warn("[create-order] Security notice: API key provided in body. Pass via X-API-Key or Authorization header instead.");
    }

    const { 
      tran_id, 
      amount, 
      cus_phone, 
      cus_email, 
      cus_name,
      product_name, 
      callback_url, 
      success_url, 
      fail_url, 
      cancel_url, 
      payment_method 
    } = body;

    // Validate request parameters
    const numericAmount = Number(amount);
    const validPaymentMethods = new Set(["bKash", "Nagad", "Rocket", "Upay"]);
    if (!secretKey) {
      return new Response(
        JSON.stringify({ error: "Missing required API key/merchantSecret. Provide in header (x-api-key) or body." }), 
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }
    if (!tran_id || !cus_phone) {
      return new Response(
        JSON.stringify({ error: "Missing required fields: tran_id, amount, cus_phone" }), 
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }
    if (!/^[A-Za-z0-9_-]{1,100}$/.test(String(tran_id))) {
      return jsonResponse({ error: "tran_id must contain only letters, numbers, underscores or hyphens" }, 400);
    }
    if (!Number.isFinite(numericAmount) || numericAmount <= 0 || numericAmount > 10_000_000) {
      return jsonResponse({ error: "amount must be a positive number within the supported limit" }, 400);
    }
    if (!/^\+?[0-9][0-9 -]{8,18}[0-9]$/.test(String(cus_phone))) {
      return jsonResponse({ error: "cus_phone is invalid" }, 400);
    }
    if (payment_method && !validPaymentMethods.has(payment_method)) {
      return jsonResponse({ error: "Unsupported payment_method" }, 400);
    }
    for (const [field, value] of Object.entries({ callback_url, success_url, fail_url, cancel_url })) {
      if (value && !isSafePublicHttpsUrl(String(value))) {
        return jsonResponse({ error: `${field} must be a public HTTPS URL` }, 400);
      }
    }

    // 1. Authenticate merchant by API key / secret key
    let { data: merchant, error: merchantErr } = await supabase
      .from("merchants")
      .select("id, default_number")
      .eq("webhook_secret", secretKey)
      .maybeSingle();

    if (!merchant) {
      try {
        const apiKeyLookup = await supabase.from("merchants")
          .select("id, default_number").eq("api_key", secretKey).maybeSingle();
        if (!apiKeyLookup.error && apiKeyLookup.data) {
          merchant = apiKeyLookup.data;
          merchantErr = null;
        }
      } catch {
        // Table does not contain optional api_key column
      }
    }

    if (merchantErr || !merchant) {
      return new Response(
        JSON.stringify({ error: "Unauthorized: Invalid API secret key" }), 
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { data: gatewaySettings } = await supabase.from("payment_gateway_settings")
      .select("gateway_enabled,success_callback_url,failure_callback_url,cancel_callback_url")
      .eq("merchant_id", merchant.id).maybeSingle();
    if (gatewaySettings?.gateway_enabled === false) {
      return jsonResponse({ error: "This merchant is not accepting gateway payments" }, 409);
    }
    for (const configuredUrl of [gatewaySettings?.success_callback_url, gatewaySettings?.failure_callback_url, gatewaySettings?.cancel_callback_url]) {
      if (configuredUrl && !isSafePublicHttpsUrl(String(configuredUrl))) {
        return jsonResponse({ error: "Merchant callback configuration is invalid" }, 409);
      }
    }

    const forwardedFor = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown-ip";
    const clientHash = await sha256(forwardedFor);
    const { data: rateAllowed, error: rateError } = await supabase.rpc("check_order_rate_limit", {
      p_merchant_id: merchant.id,
      p_client_hash: clientHash,
      p_limit: 30,
      p_window_seconds: 60,
    });
    if (rateError) {
      console.error("Persistent rate-limit check failed:", rateError);
      return jsonResponse({ error: "Order service is temporarily unavailable" }, 503);
    }
    if (!rateAllowed) {
      return new Response(JSON.stringify({ error: "Rate limit exceeded" }), {
        status: 429,
        headers: { ...corsHeaders, "Content-Type": "application/json", "Retry-After": "60" },
      });
    }

    const { data: existingOrder } = await supabase
      .from("orders")
      .select("id,expires_at,amount,cus_phone,payment_method")
      .eq("merchant_id", merchant.id)
      .eq("tran_id", tran_id)
      .maybeSingle();
    if (existingOrder) {
      const mismatchedReplay = Number(existingOrder.amount) !== numericAmount ||
        normalizePhone(existingOrder.cus_phone) !== normalizePhone(cus_phone) ||
        (payment_method && existingOrder.payment_method !== payment_method);
      if (mismatchedReplay) return jsonResponse({ error: "tran_id was already used with different order data" }, 409);
      let existingNumberQuery = supabase.from("merchant_numbers").select("number")
        .eq("merchant_id", merchant.id).eq("active", true);
      if (existingOrder.payment_method) existingNumberQuery = existingNumberQuery.eq("type", existingOrder.payment_method);
      const { data: existingNumbers } = await existingNumberQuery
        .order("is_default", { ascending: false }).order("created_at", { ascending: true }).limit(1);
      return jsonResponse({
        status: "SUCCESS",
        order_id: existingOrder.id,
        merchantNumber: existingNumbers?.[0]?.number || merchant.default_number,
        expiresAt: existingOrder.expires_at,
        idempotent: true,
      }, 200);
    }

    // 2. Determine default receiving payment number based on payment method
    let numberQuery = supabase.from("merchant_numbers")
      .select("number,type").eq("merchant_id", merchant.id).eq("active", true);
    if (payment_method) numberQuery = numberQuery.eq("type", payment_method);
    const { data: numberRecords } = await numberQuery
      .order("is_default", { ascending: false }).order("created_at", { ascending: true }).limit(1);
    const numberRecord = numberRecords?.[0];
    const receivingNumber = numberRecord?.number || merchant.default_number;
    const resolvedPaymentMethod = numberRecord?.type || payment_method || null;
    if (!receivingNumber) {
      return jsonResponse({ error: "No active receiving number is configured for this merchant" }, 409);
    }

    // 3. Create the order
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString(); // Expire order in 15 mins
    const orderData = {
      merchant_id: merchant.id,
      tran_id,
      amount: numericAmount,
      cus_phone,
      cus_email: cus_email || null,
      cus_name: cus_name || "Customer",
      status: "PENDING",
      product_name: product_name || "Checkout Payment",
      callback_url: callback_url || null,
      success_url: success_url || gatewaySettings?.success_callback_url || null,
      fail_url: fail_url || gatewaySettings?.failure_callback_url || null,
      cancel_url: cancel_url || gatewaySettings?.cancel_callback_url || null,
      payment_method: resolvedPaymentMethod,
      expires_at: expiresAt,
    };

    const { data: order, error: orderErr } = await supabase
      .from("orders")
      .insert(orderData)
      .select("id")
      .single();

    if (orderErr) {
      console.error("Database order insertion error:", orderErr.code);
      const policyFailure = /gateway|payment amount|daily payment limit|payment number/i.test(orderErr.message || "");
      return new Response(
        JSON.stringify({ error: policyFailure ? "Order is outside the merchant's configured gateway policy" : "Failed to create order record" }),
        { status: policyFailure ? 409 : 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({
        status: "SUCCESS",
        order_id: order.id,
        merchantNumber: receivingNumber,
        expiresAt,
      }), 
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err) {
    console.error("Internal order creation error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }), 
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});

function jsonResponse(body: Record<string, unknown>, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function isSafePublicHttpsUrl(value: string): boolean {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password) return false;
    const hostname = url.hostname.toLowerCase();
    if (hostname === "localhost" || hostname.endsWith(".local")) return false;
    if (/^(127\.|10\.|192\.168\.|169\.254\.)/.test(hostname)) return false;
    const private172 = hostname.match(/^172\.(\d{1,3})\./);
    if (private172 && Number(private172[1]) >= 16 && Number(private172[1]) <= 31) return false;
    if (hostname === "::1" || hostname.startsWith("fc") || hostname.startsWith("fd") || hostname.startsWith("fe80:")) return false;
    return true;
  } catch {
    return false;
  }
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function normalizePhone(value: unknown): string {
  const digits = String(value || "").replace(/[^0-9]/g, "");
  return digits.length >= 10 ? digits.slice(-10) : digits;
}
