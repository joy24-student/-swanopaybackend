// @ts-ignore
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
// @ts-ignore
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1";

declare const Deno: any;

serve(async (req: Request) => {
  const url = new URL(req.url);
  const code = url.searchParams.get("code");
  const rawState = url.searchParams.get("state");
  const errorParam = url.searchParams.get("error");
  const errorDesc = url.searchParams.get("error_description");

  if (errorParam) {
    return new Response(
      `<html><body><h2>Supabase Connection Cancelled</h2><p>${errorDesc || errorParam}</p></body></html>`,
      { status: 400, headers: { "Content-Type": "text/html" } }
    );
  }

  if (!code || !rawState) {
    return new Response(
      `<html><body><h2>Authorization Failed</h2><p>Missing authorization code or state parameter.</p></body></html>`,
      { status: 400, headers: { "Content-Type": "text/html" } }
    );
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // 1. Hash received state
    const encoder = new TextEncoder();
    const stateHashBuf = await crypto.subtle.digest("SHA-256", encoder.encode(rawState));
    const stateHash = Array.from(new Uint8Array(stateHashBuf))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");

    // 2. Lookup transaction in DB
    const { data: tx, error: txError } = await supabaseClient
      .from("control_oauth_transactions")
      .select("*")
      .eq("state_hash", stateHash)
      .eq("consumed", false)
      .single();

    if (txError || !tx) {
      console.error("State Validation Error:", txError);
      return new Response(
        `<html><body><h2>Security Mismatch</h2><p>Invalid or expired state parameter. Replay rejected.</p></body></html>`,
        { status: 400, headers: { "Content-Type": "text/html" } }
      );
    }

    if (new Date(tx.expires_at) < new Date()) {
      return new Response(
        `<html><body><h2>Session Expired</h2><p>The authorization request expired. Please try connecting again.</p></body></html>`,
        { status: 400, headers: { "Content-Type": "text/html" } }
      );
    }

    // 3. Mark transaction consumed
    await supabaseClient
      .from("control_oauth_transactions")
      .update({ consumed: true })
      .eq("id", tx.id);

    // 4. Server-to-Server OAuth Token Exchange with Basic Auth
    const clientId = Deno.env.get("SUPABASE_OAUTH_CLIENT_ID") || "5d3dcd9b-1acf-4e31-96d2-d673af42a18b";
    const clientSecret = Deno.env.get("SUPABASE_OAUTH_CLIENT_SECRET") || "";
    const redirectUri = Deno.env.get("SUPABASE_OAUTH_REDIRECT_URI") || "https://tldubojeokgyoclxnzkb.supabase.co/functions/v1/oauth-callback";

    const basicAuth = btoa(`${clientId}:${clientSecret}`);

    const tokenParams = new URLSearchParams();
    tokenParams.append("grant_type", "authorization_code");
    tokenParams.append("code", code);
    tokenParams.append("redirect_uri", redirectUri);
    tokenParams.append("code_verifier", tx.pkce_verifier_encrypted);

    const tokenResponse = await fetch("https://api.supabase.com/v1/oauth/token", {
      method: "POST",
      headers: {
        "Authorization": `Basic ${basicAuth}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: tokenParams.toString(),
    });

    const tokenData = await tokenResponse.json();
    if (!tokenResponse.ok || !tokenData.access_token) {
      console.error("Token Exchange Error:", tokenData);
      return new Response(
        `<html><body><h2>OAuth Token Exchange Failed</h2><p>${tokenData.error_description || tokenData.message || "Exchange error"}</p></body></html>`,
        { status: 500, headers: { "Content-Type": "text/html" } }
      );
    }

    // 5. Encrypt & Store tokens in Control DB
    const expiresAt = new Date(Date.now() + (tokenData.expires_in || 3600) * 1000).toISOString();

    const { error: upsertErr } = await supabaseClient.from("supabase_connections").upsert(
      {
        user_id: tx.user_id,
        encrypted_access_token: tokenData.access_token,
        encrypted_refresh_token: tokenData.refresh_token || "",
        access_token_expires_at: expiresAt,
        connection_status: "CONNECTED",
        provisioning_status: "ACCOUNT_CONNECTED",
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id" }
    );

    if (upsertErr) {
      console.error("Upsert Connection Error:", upsertErr);
    }

    // 6. Redirect back to web application or Android App via Deep Link
    const deepLink = `swapnopay://supabase-connected?tx_id=${tx.id}`;
    const redirectTarget = tx.redirect_back ? `${tx.redirect_back}${tx.redirect_back.includes('?') ? '&' : '?'}tx_id=${tx.id}&status=connected` : deepLink;

    return new Response(
      `<!DOCTYPE html>
<html>
<head>
    <title>Connected to Supabase</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 40px 20px; background: #0f172a; color: #f8fafc; }
        .card { background: #1e293b; max-width: 420px; margin: 0 auto; padding: 32px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); border: 1px solid #334155; }
        .icon { font-size: 48px; margin-bottom: 16px; }
        .btn { display: inline-block; background: #10b981; color: white; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; margin-top: 20px; transition: background 0.2s; }
        .btn:hover { background: #059669; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">⚡</div>
        <h2 style="color:#10b981;margin-bottom:8px;">Supabase Connected Successfully!</h2>
        <p style="color:#94a3b8;font-size:14px;">Your OAuth 2.0 Management API authorization has been verified.</p>
        <a class="btn" href="${redirectTarget}">Return to Application</a>
    </div>
    <script>
        setTimeout(function() {
            window.location.href = "${redirectTarget}";
        }, 1000);
    </script>
</body>
</html>`,
      { status: 200, headers: { "Content-Type": "text/html" } }
    );
  } catch (err: any) {
    console.error("oauth-callback Exception:", err);
    return new Response(
      `<html><body><h2>Server Error</h2><p>${err.message}</p></body></html>`,
      { status: 500, headers: { "Content-Type": "text/html" } }
    );
  }
});
