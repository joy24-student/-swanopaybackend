-- 1. Create MFS Regex Patterns Table
create table if not exists mfs_regex_patterns (
  id uuid primary key default gen_random_uuid(),
  mfs_name text not null check (mfs_name in ('bKash','Nagad','Rocket','Upay')),
  pattern_name text not null, -- 'traditional', 'cash-in', 'received', etc.
  regex_pattern text not null, -- The regular expression pattern
  active boolean default true,
  created_at timestamptz default now()
);

-- Enable RLS
alter table mfs_regex_patterns enable row level security;

-- Allow anonymous read access so clients (and checkout widget) can read patterns without JWT tokens
create policy "Allow anonymous read access" on mfs_regex_patterns for select using (true);

-- Allow authenticated merchants/admins to manage patterns
create policy "Allow authenticated write access" on mfs_regex_patterns for all 
  using (auth.role() = 'authenticated') 
  with check (auth.role() = 'authenticated');

-- 2. Seed MFS Regex Patterns Data
insert into mfs_regex_patterns (mfs_name, pattern_name, regex_pattern) values
  ('bKash', 'traditional', 'You have received Tk (\d+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('bKash', 'cash-in', 'Cash In Tk ([\d,]+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('Nagad', 'received', 'Money Received\..*?Amount:\s*Tk (\d+\.?\d*).*?Sender:\s*(\d+).*?TxnID:\s*(\w+).*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('Rocket', 'cash-in', 'Cash-In from A/C:\s*\*+\d+\s*Tk([\d,]+\.?\d*)[\s\S]*?TxnId:(\d+)\s+Date:(\d{2}-[A-Z]{3}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*[ap]m)'),
  ('Upay', 'received', '(?:Money Received|Received Taka)[\s\S]*?Taka\s*([\d,]+\.?\d*)[\s\S]*?from\s*(\d+)[\s\S]*?TrxID\s*(\w+)[\s\S]*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})');
