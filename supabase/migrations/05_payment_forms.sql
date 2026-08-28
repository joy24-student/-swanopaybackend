-- 05. Payment Forms and Custom Form Submissions Schema
create table if not exists payment_forms (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  title text not null,
  description text,
  logo_url text,
  amount numeric(12,2) not null,
  fields jsonb not null default '[]', -- Array of field configurations: {id, type, label, options, required, image_url}
  created_at timestamptz default now()
);

create table if not exists form_submissions (
  id uuid primary key default gen_random_uuid(),
  form_id uuid references payment_forms on delete cascade not null,
  order_id uuid references orders on delete cascade not null,
  answers jsonb not null default '{}', -- Map of collected answers: {field_id: answer}
  created_at timestamptz default now()
);

-- Enable RLS policies
alter table payment_forms enable row level security;
alter table form_submissions enable row level security;

-- Policies for merchants
create policy "Allow select payment_forms to anyone" on payment_forms
  for select using (true);

create policy "Allow insert payment_forms for authenticated merchants" on payment_forms
  for insert with check (auth.uid() is not null);

create policy "Allow all payment_forms management for owners" on payment_forms
  for all using (
    merchant_id in (select id from merchants where user_id = auth.uid())
  );

create policy "Allow select submissions for owners" on form_submissions
  for select using (
    form_id in (
      select id from payment_forms 
      where merchant_id in (select id from merchants where user_id = auth.uid())
    )
  );

create policy "Allow insert submissions to anyone" on form_submissions
  for insert with check (true);

-- Indices for performance
create index if not exists idx_payment_forms_merchant on payment_forms(merchant_id);
create index if not exists idx_submissions_form on form_submissions(form_id);
create index if not exists idx_submissions_order on form_submissions(order_id);
