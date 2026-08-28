-- Run once per merchant project after replacing the two placeholders.
-- Secrets are encrypted by Supabase Vault; do not hard-code service-role keys.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select vault.create_secret(
  'https://PROJECT_REF.supabase.co/functions/v1/payment-receipt',
  'payment_receipt_function_url',
  'SwapnoPay verified-payment receipt worker URL'
);
select vault.create_secret(
  'REPLACE_WITH_PAYMENT_RECEIPT_WEBHOOK_SECRET',
  'payment_receipt_webhook_secret',
  'Authenticates database/cron calls to the receipt worker'
);

select cron.schedule(
  'dispatch-swapnopay-payment-receipts',
  '* * * * *',
  $$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'payment_receipt_function_url'),
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-webhook-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'payment_receipt_webhook_secret')
    ),
    body := jsonb_build_object('source', 'payment_receipt_outbox_retry', 'time', now()),
    timeout_milliseconds := 15000
  );
  $$
);
