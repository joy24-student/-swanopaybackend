-- Migration: 20260819_control_plane_schema.sql
-- Control Plane Schema for Developer Supabase Project
-- Manages OAuth transactions, token storage, and customer project provisioning

-- 1. OAuth State & PKCE Tracking
CREATE TABLE IF NOT EXISTS control_oauth_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    state_hash TEXT NOT NULL UNIQUE,
    pkce_verifier_encrypted TEXT NOT NULL,
    redirect_back TEXT,
    consumed BOOLEAN DEFAULT false,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_oauth_tx_state ON control_oauth_transactions(state_hash);

-- 2. Customer Supabase Connections & Management Tokens
CREATE TABLE IF NOT EXISTS supabase_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL UNIQUE,
    organization_slug TEXT,
    selected_project_ref TEXT,
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    access_token_expires_at TIMESTAMPTZ NOT NULL,
    oauth_scopes TEXT[] DEFAULT '{}',
    connection_status TEXT DEFAULT 'ACTIVE',
    provisioning_status TEXT DEFAULT 'NOT_STARTED',
    publishable_key TEXT,
    project_url TEXT,
    last_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_supabase_conn_user ON supabase_connections(user_id);
CREATE INDEX IF NOT EXISTS idx_supabase_conn_project ON supabase_connections(selected_project_ref);

-- Enable Row Level Security (RLS)
ALTER TABLE control_oauth_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE supabase_connections ENABLE ROW LEVEL SECURITY;

-- Security Policies (Service Role / Edge Functions access only)
CREATE POLICY "Service Role full access on control_oauth_transactions"
    ON control_oauth_transactions FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on supabase_connections"
    ON supabase_connections FOR ALL
    USING (auth.role() = 'service_role');

-- Users can view their own connection status (excluding token details)
CREATE POLICY "Users read own connection status"
    ON supabase_connections FOR SELECT
    USING (auth.uid()::text = user_id);
