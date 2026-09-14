import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), {
        status: 405,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const authorization = req.headers.get("Authorization");
    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      throw new Error("Required Supabase environment variables are not configured");
    }
    if (!authorization?.startsWith("Bearer ")) {
      return new Response(JSON.stringify({ error: "Authentication required" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authorization } },
    });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Invalid or expired user session" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const supabase = createClient(
      supabaseUrl,
      serviceRoleKey
    );

    const { data: merchant, error: merchantError } = await supabase
      .from("merchants")
      .select("id")
      .eq("user_id", user.id)
      .single();
    if (merchantError || !merchant) {
      return new Response(JSON.stringify({ error: "Merchant account not found" }), {
        status: 403,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = await req.json();
    const { appeal_id, action, order_id } = body; // action is "APPROVED" or "REJECTED"

    if (!appeal_id || !action) {
      return new Response(
        JSON.stringify({ error: "Missing required fields: appeal_id, action" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (action !== "APPROVED" && action !== "REJECTED") {
      return new Response(
        JSON.stringify({ error: "Invalid action value. Must be APPROVED or REJECTED" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 1. Fetch appeal details
    const { data: appeal, error: appealErr } = await supabase
      .from("appeals")
      .select("*")
      .eq("id", appeal_id)
      .single();

    if (appealErr || !appeal) {
      return new Response(
        JSON.stringify({ error: "Dispute appeal not found" }),
        { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    if (!appeal.order_id || (order_id && order_id !== appeal.order_id)) {
      return new Response(
        JSON.stringify({ error: "Appeal is not linked to the requested order." }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }
    const targetOrderId = appeal.order_id;

    const { data: targetOrder, error: orderLookupError } = await supabase
      .from("orders")
      .select("*")
      .eq("id", targetOrderId)
      .eq("merchant_id", merchant.id)
      .single();
    if (orderLookupError || !targetOrder) {
      return new Response(
        JSON.stringify({ error: "Appeal does not belong to the authenticated merchant" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // Resolve the appeal, order, and payment in one locked database transaction.
    const { error: resolveError } = await supabase.rpc("resolve_appeal_atomic", {
      p_appeal_id: appeal_id,
      p_action: action,
      p_order_id: targetOrderId,
      p_resolved_by: user.id,
    });
    if (resolveError) {
      return new Response(
        JSON.stringify({ error: "Appeal could not be resolved in its current state" }),
        { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // The transaction is committed before external webhook delivery.
    if (action === "APPROVED") {
      const order = targetOrder;
      if (order.callback_url && isSafePublicHttpsUrl(order.callback_url)) {
        try {
          await triggerManualMatchWebhook(order, appeal.trx_id, order.amount, appeal.cus_phone || order.cus_phone, supabase);
        } catch (webhookErr) {
          console.error("Manual match webhook dispatch failed:", webhookErr);
        }
      }
    }

    return new Response(
      JSON.stringify({ status: "SUCCESS", message: `Appeal successfully resolved as ${action}` }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );

  } catch (err) {
    console.error("Internal dispute resolution error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});

async function triggerManualMatchWebhook(order: any, trxId: string, amount: number, sender: string, supabase: any) {
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
    payment_method: order.payment_method || "MFS_MANUAL",
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

  await fetch(order.callback_url, {
    method: "POST",
    headers: { 
      "Content-Type": "application/json", 
      "X-Signature": hexSig 
    },
    body,
    signal: AbortSignal.timeout(5_000),
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
    return !(hostname === "::1" || hostname.startsWith("fc") || hostname.startsWith("fd") || hostname.startsWith("fe80:"));
  } catch {
    return false;
  }
}
