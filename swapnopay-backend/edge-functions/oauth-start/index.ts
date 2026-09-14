import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { user_id, organization_slug, redirect_back } = await req.json().catch(() => ({ user_id: null, organization_slug: null, redirect_back: null }));
    if (!user_id) {
      return new Response(JSON.stringify({ error: "user_id is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const clientId = Deno.env.get("SUPABASE_OAUTH_CLIENT_ID") || "5d3dcd9b-1acf-4e31-96d2-d673af42a18b";
    const redirectUri = Deno.env.get("SUPABASE_OAUTH_REDIRECT_URI") || "https://tldubojeokgyoclxnzkb.supabase.co/functions/v1/oauth-callback";

    // 1. Generate Secure State & PKCE
    const stateBytes = new Uint8Array(24);
    crypto.getRandomValues(stateBytes);
    const rawState = btoa(String.fromCharCode(...stateBytes))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=/g, "");

    const verifierBytes = new Uint8Array(32);
    crypto.getRandomValues(verifierBytes);
    const codeVerifier = btoa(String.fromCharCode(...verifierBytes))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=/g, "");

    // SHA256 Code Challenge
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    const hash = await crypto.subtle.digest("SHA-256", data);
    const codeChallenge = btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=/g, "");

    // 2. Hash state for verification lookup
    const stateHashBuf = await crypto.subtle.digest("SHA-256", encoder.encode(rawState));
    const stateHash = Array.from(new Uint8Array(stateHashBuf))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");

    // 3. Save to control_oauth_transactions DB
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString(); // 15 mins
    const { error: dbError } = await supabaseClient.from("control_oauth_transactions").insert({
      user_id,
      state_hash: stateHash,
      pkce_verifier_encrypted: codeVerifier, // Secure in Control Plane DB
      redirect_back: redirect_back || null,
      consumed: false,
      expires_at: expiresAt,
    });

    if (dbError) {
      console.error("DB Save Error:", dbError);
      return new Response(JSON.stringify({ error: "Failed to store OAuth transaction" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 4. Construct Authorization URL
    let authorizeUrl =
      `https://api.supabase.com/v1/oauth/authorize?` +
      `client_id=${encodeURIComponent(clientId)}&` +
      `redirect_uri=${encodeURIComponent(redirectUri)}&` +
      `response_type=code&` +
      `state=${encodeURIComponent(rawState)}&` +
      `code_challenge=${encodeURIComponent(codeChallenge)}&` +
      `code_challenge_method=S256`;

    if (organization_slug) {
      authorizeUrl += `&organization_slug=${encodeURIComponent(organization_slug)}`;
    }

    return new Response(JSON.stringify({ authorize_url: authorizeUrl, state: rawState }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  } catch (err: any) {
    console.error("oauth-start Exception:", err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
