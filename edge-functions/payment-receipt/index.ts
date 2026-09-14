import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type Json = Record<string, unknown>;

const jsonHeaders = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };

serve(async (request) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const receiptServiceUrl = (Deno.env.get("SWAPNOPAY_RECEIPT_SERVICE_URL") || "").replace(/\/$/, "");
  const receiptApiKey = Deno.env.get("SWAPNOPAY_RECEIPT_API_KEY") || "";

  if (request.method === "GET") return authenticatedHealth(request, supabaseUrl, receiptServiceUrl, Boolean(receiptApiKey));
  if (request.method !== "POST") return response({ error: "Method not allowed" }, 405);

  const expected = Deno.env.get("PAYMENT_RECEIPT_WEBHOOK_SECRET") || "";
  if (!expected || request.headers.get("x-webhook-secret") !== expected) {
    return response({ error: "Unauthorized webhook" }, 401);
  }

  if (!supabaseUrl || !serviceRoleKey || !receiptServiceUrl || !receiptApiKey) {
    return response({ error: "Receipt delivery is not configured" }, 503);
  }

  const database = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false } });
  const projectRef = projectReference(supabaseUrl);
  const { data: claimed, error: claimError } = await database.rpc("claim_payment_receipts", {
    p_limit: 10,
    p_lease_seconds: 120,
  });
  if (claimError) {
    console.error("Unable to claim payment receipt events", claimError.code);
    return response({ error: "Unable to claim receipt events" }, 503);
  }

  const results: Json[] = [];
  for (const event of claimed || []) {
    try {
      const { data: order, error: orderError } = await database.from("orders")
        .select("id,merchant_id,tran_id,amount,cus_phone,cus_email,cus_name,status,product_name,payment_method,matched_trx_id,manual_match,paid_at,created_at")
        .eq("id", event.order_id).eq("merchant_id", event.merchant_id).maybeSingle();
      if (orderError || !order || order.status !== "PAID" || !order.paid_at) {
        throw new Error("The merchant database no longer contains a verified PAID order");
      }

      const [{ data: merchant }, { data: settings }] = await Promise.all([
        database.from("merchants").select("id,business_name,email,user_id").eq("id", event.merchant_id).maybeSingle(),
        database.from("payment_gateway_settings").select("customer_receipts_enabled,merchant_receipts_enabled,merchant_receipt_email")
          .eq("merchant_id", event.merchant_id).maybeSingle(),
      ]);
      if (!merchant) throw new Error("Merchant record not found");

      let merchantEmail = String(settings?.merchant_receipt_email || merchant.email || "").trim();
      if (!merchantEmail && merchant.user_id) {
        const { data: userData } = await database.auth.admin.getUserById(merchant.user_id);
        merchantEmail = String(userData?.user?.email || "").trim();
      }
      const customerEnabled = settings?.customer_receipts_enabled !== false;
      const merchantEnabled = settings?.merchant_receipts_enabled !== false;
      const customerEmail = customerEnabled ? validEmail(order.cus_email) : "";
      const receiptMerchantEmail = merchantEnabled ? validEmail(merchantEmail) : "";

      if (!customerEmail && !receiptMerchantEmail) {
        await complete(database, event.id, true, { skipped: "No enabled valid receipt recipients" });
        results.push({ event_id: event.id, status: "SKIPPED" });
        continue;
      }

      const deliveryPayload = {
        schema_version: 1,
        source: "merchant_database",
        project_ref: projectRef,
        event_id: event.id,
        event_type: "PAYMENT_PAID",
        merchant: {
          id: merchant.id,
          business_name: String(merchant.business_name || "Merchant").slice(0, 160),
          receipt_email: receiptMerchantEmail || null,
        },
        order: {
          id: order.id,
          transaction_id: String(order.tran_id || "").slice(0, 120),
          provider_transaction_id: String(order.matched_trx_id || "").slice(0, 160),
          amount: Number(order.amount),
          currency: "BDT",
          payment_method: String(order.payment_method || "MFS").slice(0, 40),
          customer_name: String(order.cus_name || "Customer").slice(0, 160),
          customer_email: customerEmail || null,
          customer_phone: String(order.cus_phone || "").slice(0, 30),
          product_name: String(order.product_name || "Payment").slice(0, 200),
          paid_at: order.paid_at,
          verification: order.manual_match ? "MERCHANT_APPROVED_APPEAL" : "ATOMIC_SMS_MATCH",
        },
      };

      const outbound = await fetch(`${receiptServiceUrl}/v1/payment-receipts`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${receiptApiKey}`,
          "Content-Type": "application/json",
          "Idempotency-Key": `${projectRef}:${event.id}`,
          "User-Agent": "SwapnoPay-Merchant-Database/1.0",
        },
        body: JSON.stringify(deliveryPayload),
        signal: AbortSignal.timeout(12_000),
      });
      const providerBody = await safeJson(outbound);
      if (!outbound.ok) throw new Error(`Receipt service returned HTTP ${outbound.status}`);

      await complete(database, event.id, true, providerBody);
      results.push({ event_id: event.id, status: "SENT" });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Receipt delivery failed";
      console.error("Payment receipt event failed", event.id, message);
      await complete(database, event.id, false, {}, message);
      results.push({ event_id: event.id, status: "FAILED" });
    }
  }

  return response({ processed: results.length, results }, 200);
});

async function authenticatedHealth(request: Request, supabaseUrl: string, receiptServiceUrl: string, hasReceiptApiKey: boolean) {
  const authorization = request.headers.get("authorization") || "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";
  if (!authorization.startsWith("Bearer ") || !supabaseUrl || !anonKey) return response({ error: "Authentication required" }, 401);
  const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
  const { data: { user }, error: userError } = await userClient.auth.getUser();
  if (userError || !user) return response({ error: "Invalid or expired session" }, 401);
  const { data: merchant } = await userClient.from("merchants").select("id").eq("user_id", user.id).maybeSingle();
  if (!merchant) return response({ error: "Merchant account not found" }, 403);

  const { data: recent, error: outboxError } = await userClient.from("payment_receipt_outbox")
    .select("status").eq("merchant_id", merchant.id).order("created_at", { ascending: false }).limit(100);
  const summary = { sent: 0, pending: 0, failed: 0 };
  for (const row of recent || []) {
    if (row.status === "SENT") summary.sent += 1;
    else if (row.status === "FAILED") summary.failed += 1;
    else summary.pending += 1;
  }

  let providerHealthy = false;
  if (receiptServiceUrl) {
    try {
      const provider = await fetch(`${receiptServiceUrl}/healthz`, { signal: AbortSignal.timeout(4_000) });
      providerHealthy = provider.ok;
    } catch { providerHealthy = false; }
  }
  const workerConfigured = Boolean(receiptServiceUrl && hasReceiptApiKey && Deno.env.get("PAYMENT_RECEIPT_WEBHOOK_SECRET"));
  return response({
    ready: workerConfigured && providerHealthy && !outboxError,
    merchant_database: !outboxError,
    worker_configured: workerConfigured,
    oracle_receipt_service: providerHealthy,
    outbox: summary,
  }, 200);
}

async function complete(database: any, eventId: string, succeeded: boolean, providerResponse: Json, error?: string) {
  const { error: completionError } = await database.rpc("complete_payment_receipt", {
    p_event_id: eventId,
    p_succeeded: succeeded,
    p_provider_response: providerResponse,
    p_error: error || null,
  });
  if (completionError) throw new Error("Unable to persist receipt delivery result");
}

function projectReference(url: string): string {
  try {
    const hostname = new URL(url).hostname;
    return hostname.endsWith(".supabase.co") ? hostname.slice(0, -".supabase.co".length) : hostname;
  } catch {
    return "unknown";
  }
}

function validEmail(value: unknown): string {
  const email = String(value || "").trim().toLowerCase();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 254 ? email : "";
}

async function safeJson(res: Response): Promise<Json> {
  const text = (await res.text()).slice(0, 16_384);
  try { return JSON.parse(text) as Json; } catch { return { response: text }; }
}

function response(body: Json, status: number) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}
