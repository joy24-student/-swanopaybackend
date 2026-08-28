-- 1. Enable RLS
alter table merchants enable row level security;
alter table merchant_numbers enable row level security;
alter table orders enable row level security;
alter table payments enable row level security;
alter table sms_logs enable row level security;
alter table devices enable row level security;
alter table appeals enable row level security;
alter table notifications enable row level security;
alter table security_logs enable row level security;

-- 2. Helper function: get current user's merchant_id
create or replace function current_merchant_id()
returns uuid language sql stable as $$
  select id from merchants where user_id = auth.uid() limit 1;
$$;

-- 3. Merchants policies
create policy "Merchant own record" on merchants for all 
  using (user_id = auth.uid()) 
  with check (user_id = auth.uid());

-- 4. Merchant numbers policies
create policy "Merchant numbers access" on merchant_numbers for all 
  using (merchant_id = current_merchant_id()) 
  with check (merchant_id = current_merchant_id());

-- 5. Orders policies
create policy "Orders merchant access" on orders for all 
  using (merchant_id = current_merchant_id()) 
  with check (merchant_id = current_merchant_id());

-- Allow anonymous read of orders by ID for checkout widget
create policy "Orders anon read by id" on orders for select 
  using (status = 'PENDING' or status = 'PAID' or status = 'CANCELLED' or status = 'EXPIRED');

-- Allow anonymous customer to cancel pending order
create policy "Orders anon update cancel" on orders for update
  using (status = 'PENDING')
  with check (status = 'CANCELLED');

-- Allow anonymous customer to create a new pending order
create policy "Orders anon insert" on orders for insert
  with check (status = 'PENDING');

-- 6. Payments policies
create policy "Payments merchant access" on payments for all 
  using (merchant_id = current_merchant_id()) 
  with check (merchant_id = current_merchant_id());

-- 7. SMS logs policies
create policy "SMS logs insert by device" on sms_logs for insert 
  with check (
    exists (
      select 1 from devices 
      where id = device_id and user_id = auth.uid()
    )
  );

create policy "SMS logs read merchant" on sms_logs for select 
  using (merchant_id = current_merchant_id());

-- 8. Devices policies
create policy "Devices own" on devices for all 
  using (user_id = auth.uid()) 
  with check (user_id = auth.uid());

create policy "Devices merchant read" on devices for select 
  using (merchant_id = current_merchant_id());

-- 9. Appeals policies
create policy "Appeals merchant manage" on appeals for all 
  using (
    exists (
      select 1 from orders 
      where orders.id = appeals.order_id and orders.merchant_id = current_merchant_id()
    )
  ) 
  with check (
    order_id is null or exists (
      select 1 from orders 
      where orders.id = order_id and orders.merchant_id = current_merchant_id()
    )
  );

-- Allow anonymous insert for customer checkout widget (only if valid order_id is provided)
create policy "Appeals anon insert" on appeals for insert 
  with check (exists (select 1 from orders where id = order_id));

-- Allow anonymous select for customers to check appeal status
create policy "Appeals anon select" on appeals for select 
  using (true);

-- 10. Notifications policies
create policy "Notifications user" on notifications for all 
  using (user_id = auth.uid()) 
  with check (user_id = auth.uid());

-- 11. Security logs policies
create policy "Security logs merchant" on security_logs for select 
  using (merchant_id = current_merchant_id());

create policy "Security logs insert" on security_logs for insert 
  with check (true);

-- 12. Enable Realtime for orders table
alter publication supabase_realtime add table orders;
