-- =============================================================================
-- Migration 25: Fix Support Chat, Support Tickets, Feature Requests & OAuth Tables
-- Ensures non-recursive RLS policies and full access for platform helpdesk & OAuth
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.support_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id text NOT NULL,
  business_name text DEFAULT 'My Business',
  email text,
  phone text,
  category text DEFAULT 'GENERAL',
  subject text NOT NULL,
  description text NOT NULL,
  status text DEFAULT 'OPEN',
  admin_reply text,
  replied_at timestamptz,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.feature_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id text NOT NULL,
  business_name text DEFAULT 'My Business',
  email text,
  category text DEFAULT 'GENERAL',
  priority text DEFAULT 'MEDIUM',
  title text NOT NULL,
  description text NOT NULL,
  status text DEFAULT 'UNDER_REVIEW',
  admin_notes text,
  upvotes integer DEFAULT 1,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.live_chat_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id text NOT NULL,
  sender text NOT NULL DEFAULT 'MERCHANT',
  message text NOT NULL,
  created_at timestamptz DEFAULT now()
);

ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.feature_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_chat_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "allow_all_support_tickets" ON public.support_tickets;
CREATE POLICY "allow_all_support_tickets" ON public.support_tickets
  FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "allow_all_feature_requests" ON public.feature_requests;
CREATE POLICY "allow_all_feature_requests" ON public.feature_requests
  FOR ALL USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "allow_all_live_chat_messages" ON public.live_chat_messages;
CREATE POLICY "allow_all_live_chat_messages" ON public.live_chat_messages
  FOR ALL USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.support_tickets TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.feature_requests TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.live_chat_messages TO anon, authenticated, service_role;
