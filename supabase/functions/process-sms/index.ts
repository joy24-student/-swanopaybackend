import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: { "Access-Control-Allow-Origin": "*" } });
  }

  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
    }
    const expectedWebhookSecret = Deno.env.get("PROCESS_SMS_WEBHOOK_SECRET");
    if (!expectedWebhookSecret) {
      console.error("PROCESS_SMS_WEBHOOK_SECRET is not configured");
      return new Response(JSON.stringify({ error: "Webhook authentication is not configured" }), { status: 503 });
    }
    if (req.headers.get("x-webhook-secret") !== expectedWebhookSecret) {
      return new Response(JSON.stringify({ error: "Unauthorized webhook" }), { status: 401 });
    }
    const payload = await req.json();
    const { record } = payload; // Triggered by DB webhook insert on sms_logs

    if (!record) {
      return new Response(JSON.stringify({ error: "No record found" }), { status: 400 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { 
      parsed_amount, 
      parsed_sender, 
      parsed_trx_id, 
      parsed_timestamp, 
      merchant_id, 
      sms_hash, 
      id: sms_log_id 
    } = record;

    if (!Number.isFinite(Number(parsed_amount)) || Number(parsed_amount) <= 0 || !parsed_sender || !parsed_trx_id || !merchant_id || !sms_hash || !sms_log_id) {
      return new Response(JSON.stringify({ status: "ignored", reason: "Missing parsed SMS parameters" }), { status: 200 });
    }

    // 1. Check duplicate payments
    const { data: existingPayment } = await supabase
      .from("payments")
      .select("id")
      .eq("sms_hash", sms_hash)
      .maybeSingle();

    if (existingPayment) {
      const { error: duplicateLogError } = await supabase
        .from("sms_logs")
        .update({ processed: true, status: "duplicate" })
        .eq("id", sms_log_id);
      if (duplicateLogError) throw duplicateLogError;
      return new Response(JSON.stringify({ status: "duplicate" }), { status: 200 });
    }

    // 2. Find matching order: same amount, matching customer phone, status PENDING, and not expired
    const now = new Date().toISOString();
    let matchedOrders: any[] | null = null;

    const { data: exactOrders } = await supabase
      .from("orders")
      .select("*")
      .eq("merchant_id", merchant_id)
      .eq("amount", parsed_amount)
      .eq("cus_phone", parsed_sender)
      .eq("status", "PENDING")
      .gte("expires_at", now)
      .order("created_at", { ascending: true })
      .limit(2);

    if (exactOrders && exactOrders.length === 1) {
      matchedOrders = exactOrders;
    } else {
      // Country-code formatting may differ between checkout and an official SMS.
      // Match only a full normalized subscriber number. Masked/last-four values
      // remain UNMATCHED for merchant review; amount + four digits is not enough
      // evidence for an automatic PAID transition.
      const normalizedSender = normalizePhone(parsed_sender);
      if (normalizedSender) {
        const { data: candidateOrders } = await supabase.from("orders").select("*")
          .eq("merchant_id", merchant_id).eq("amount", parsed_amount).eq("status", "PENDING")
          .gte("expires_at", now).order("created_at", { ascending: true }).limit(50);
        const normalizedMatches = (candidateOrders || []).filter((candidate: any) =>
          normalizePhone(candidate.cus_phone) === normalizedSender
        );
        if (normalizedMatches.length === 1) matchedOrders = normalizedMatches;
      }
    }

    if (!matchedOrders || matchedOrders.length === 0) {
      // Insert as unmatched payment log
      const { error: unmatchedPaymentError } = await supabase.from("payments").insert({
        merchant_id,
        trx_id: parsed_trx_id,
        amount: parsed_amount,
        sender_number: parsed_sender,
        sms_timestamp: parsed_timestamp ? new Date(parsed_timestamp).toISOString() : now,
        sms_hash,
        status: "UNMATCHED",
      });
      if (unmatchedPaymentError) throw unmatchedPaymentError;

      const { error: unmatchedLogError } = await supabase
        .from("sms_logs")
        .update({ processed: true, status: "unmatched" })
        .eq("id", sms_log_id);
      if (unmatchedLogError) throw unmatchedLogError;
      return new Response(JSON.stringify({ status: "unmatched" }), { status: 200 });
    }

    const order = matchedOrders[0];

    // 3. Perform atomic database match update
    const { error: matchError } = await supabase.rpc("match_payment_atomic", {
      p_order_id: order.id,
      p_payment_amount: parsed_amount,
      p_sender_number: parsed_sender,
      p_trx_id: parsed_trx_id,
      p_sms_hash: sms_hash,
      p_sms_log_id: sms_log_id,
    });

    if (matchError) {
      console.error("Atomic matching database error:", matchError);
      return new Response(JSON.stringify({ error: "Failed atomic update" }), { status: 500 });
    }

    // 4. Send Webhook notification (Async)
    if (order.callback_url && isSafePublicHttpsUrl(order.callback_url)) {
      try {
        await sendWebhook(order, parsed_trx_id, parsed_amount, parsed_sender, supabase);
      } catch (webhookErr) {
        console.error("Webhook dispatch failed:", webhookErr);
      }
    }

    // A database trigger now enqueues the receipt after this atomic PAID commit.
    // The receipt worker handles retries and covers both automatic matches and
    // merchant-approved appeals without storing mail credentials in this project.
    return new Response(JSON.stringify({ status: "matched", order_id: order.id }), { status: 200 });

  } catch (err) {
    console.error("Internal processing error:", err);
    return new Response(JSON.stringify({ error: "Internal server error" }), { status: 500 });
  }
});

async function sendWebhook(order: any, trxId: string, amount: number, sender: string, supabase: any) {
  const { data: merchant } = await supabase
    .from("merchants")
    .select("webhook_secret")
    .eq("id", order.merchant_id)
    .single();

  const secret = merchant?.webhook_secret || "";
  const payload = {
    tran_id: order.tran_id,
    order_id: order.id,
    status: "PAID",
    amount: order.amount,
    currency: "BDT",
    paid_amount: amount,
    payment_method: order.payment_method || "bKash",
    sender_number: sender,
    trx_id: trxId,
    payment_time: new Date().toISOString(),
    cus_name: order.cus_name,
    cus_email: order.cus_email,
    cus_phone: order.cus_phone,
    product_name: order.product_name,
    metadata: order.metadata,
  };

  const body = JSON.stringify(payload);
  
  // Sign webhook payload using HMAC-SHA256
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw", 
    encoder.encode(secret), 
    { name: "HMAC", hash: "SHA-256" }, 
    false, 
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(body));
  const hexSig = Array.from(new Uint8Array(signature))
    .map(b => b.toString(16).padStart(2, "0"))
    .join("");

  let attempt = 0;
  const maxAttempts = 3;
  let delay = 1000;
  let success = false;

  while (attempt < maxAttempts && !success) {
    try {
      const response = await fetch(order.callback_url, {
        method: "POST",
        headers: { 
          "Content-Type": "application/json", 
          "X-Signature": hexSig 
        },
        body,
        signal: AbortSignal.timeout(4000) // 4 second timeout
      });
      if (response.ok) {
        success = true;
        console.log(`Webhook successfully delivered on attempt ${attempt + 1}`);
      } else {
        console.warn(`Webhook attempt ${attempt + 1} returned status ${response.status}`);
      }
    } catch (fetchErr) {
      console.warn(`Webhook attempt ${attempt + 1} failed: ${fetchErr.message}`);
    }
    if (!success) {
      attempt++;
      if (attempt < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, delay));
        delay *= 2;
      }
    }
  }
}

function isSafePublicHttpsUrl(value: string): boolean {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password) return false;
    const hostname = url.hostname.toLowerCase();
    if (hostname === "localhost" || hostname.endsWith(".local")) return false;
    if (/^(127\.|10\.|192\.168\.|169\.254\.)/.test(hostname)) return false;
    const private172 = hostname.match(/^172\.(\d{1,3})\./);
    return !(private172 && Number(private172[1]) >= 16 && Number(private172[1]) <= 31);
  } catch {
    return false;
  }
}

function normalizePhone(value: string): string {
  const digits = String(value || "").replace(/[^0-9]/g, "");
  return digits.length >= 10 ? digits.slice(-10) : "";
}
