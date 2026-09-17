-- Migration 22: Add missing Android RPCs
-- Provides get_daily_revenue, cancel_order, and extend_order RPC functions for SwapnoPay Android client and portal

CREATE OR REPLACE FUNCTION public.get_daily_revenue(merchant_id_param uuid)
RETURNS numeric LANGUAGE sql STABLE SECURITY DEFINER AS $$
  SELECT COALESCE(SUM(amount), 0.00)
  FROM public.orders
  WHERE merchant_id = merchant_id_param
    AND status = 'PAID'
    AND paid_at >= date_trunc('day', now());
$$;

CREATE OR REPLACE FUNCTION public.cancel_order(order_id_param uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
  UPDATE public.orders 
  SET status = 'CANCELLED' 
  WHERE id = order_id_param 
    AND status = 'PENDING';
END;
$$;

CREATE OR REPLACE FUNCTION public.extend_order(order_id_param uuid, new_expiry_param bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
  UPDATE public.orders 
  SET expires_at = to_timestamp(new_expiry_param / 1000) 
  WHERE id = order_id_param 
    AND status = 'PENDING';
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_daily_revenue(uuid) TO authenticated, service_role, anon;
GRANT EXECUTE ON FUNCTION public.cancel_order(uuid) TO authenticated, service_role, anon;
GRANT EXECUTE ON FUNCTION public.extend_order(uuid, bigint) TO authenticated, service_role, anon;
