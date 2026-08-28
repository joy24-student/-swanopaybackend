import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

async function getValidAccessToken(supabaseClient: any, userId: string): Promise<string> {
  const { data: conn, error } = await supabaseClient
    .from("supabase_connections")
    .select("*")
    .eq("user_id", userId)
    .single();

  if (error || !conn) {
    throw new Error("No active Supabase connection found for user.");
  }

  const isExpired = new Date(conn.access_token_expires_at) <= new Date(Date.now() + 60000); // 1-min buffer
  if (!isExpired) {
    return conn.encrypted_access_token;
  }

  // Refresh Token Exchange
  console.log("Access token expired. Refreshing token for user:", userId);
  const clientId = Deno.env.get("SUPABASE_OAUTH_CLIENT_ID") || "swapnopay_client_id";
  const clientSecret = Deno.env.get("SUPABASE_OAUTH_CLIENT_SECRET") || "swapnopay_client_secret";
  const basicAuth = btoa(`${clientId}:${clientSecret}`);

  const refreshParams = new URLSearchParams();
  refreshParams.append("grant_type", "refresh_token");
  refreshParams.append("refresh_token", conn.encrypted_refresh_token);

  const res = await fetch("https://api.supabase.com/v1/oauth/token", {
    method: "POST",
    headers: {
      "Authorization": `Basic ${basicAuth}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: refreshParams.toString(),
  });

  const refreshed = await res.json();
  if (!res.ok || !refreshed.access_token) {
    throw new Error(`Token Refresh Failed: ${refreshed.error_description || "Invalid refresh token"}`);
  }

  const newExpiresAt = new Date(Date.now() + (refreshed.expires_in || 3600) * 1000).toISOString();
  await supabaseClient
    .from("supabase_connections")
    .update({
      encrypted_access_token: refreshed.access_token,
      encrypted_refresh_token: refreshed.refresh_token || conn.encrypted_refresh_token,
      access_token_expires_at: newExpiresAt,
      updated_at: new Date().toISOString(),
    })
    .eq("user_id", userId);

  return refreshed.access_token;
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { user_id } = await req.json().catch(() => ({ user_id: null }));
    if (!user_id) {
      return new Response(JSON.stringify({ error: "user_id is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const accessToken = await getValidAccessToken(supabaseClient, user_id);

    // Query Management API for Organizations & Projects
    const [orgsRes, projectsRes] = await Promise.all([
      fetch("https://api.supabase.com/v1/organizations", {
        headers: { Authorization: `Bearer ${accessToken}` },
      }),
      fetch("https://api.supabase.com/v1/projects", {
        headers: { Authorization: `Bearer ${accessToken}` },
      }),
    ]);

    const orgs = orgsRes.ok ? await orgsRes.json() : [];
    const projects = projectsRes.ok ? await projectsRes.json() : [];

    return new Response(
      JSON.stringify({
        organizations: orgs,
        projects: projects.map((p: any) => ({
          id: p.id,
          name: p.name,
          organization_id: p.organization_id,
          region: p.region,
          status: p.status,
        })),
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (err: any) {
    console.error("projects Edge Function Error:", err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
