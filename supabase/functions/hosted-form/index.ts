import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type Json = Record<string, unknown>;
type FormField = Json & { id?: string; type?: string; label?: string; required?: boolean };

const baseHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, x-client-info, content-type, idempotency-key",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Cache-Control": "no-store",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
};

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: baseHeaders });
  const url = new URL(request.url);
  const slug = url.searchParams.get("slug")?.trim() || "";
  const formId = url.searchParams.get("id")?.trim() || "";
  if (!slug && !formId) return json({ error: "A valid form slug or id parameter is required" }, 400);

  const database = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  let formQuery = database.from("payment_forms")
    .select("id,merchant_id,title,description,slug,fields,products,pages,theme,status,submissions_count,total_revenue,logo_url,banner_url");

  if (slug) {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/i.test(slug) || slug.length > 100) return json({ error: "Invalid form slug" }, 400);
    formQuery = formQuery.eq("slug", slug);
  } else if (isUuid(formId)) {
    formQuery = formQuery.eq("id", formId);
  } else {
    return json({ error: "Invalid form identifier" }, 400);
  }

  const { data: form, error: formError } = await formQuery.maybeSingle();
  if (formError || !form) return json({ error: "Published form not found" }, 404);

  if (request.method === "GET" && url.searchParams.get("action") === "attachment") {
    return signedAttachment(database, form, request, url.searchParams.get("submission_id") || "", url.searchParams.get("field_id") || "");
  }
  if (form.status !== "PUBLISHED") return json({ error: "Published form not found" }, 404);

  if (request.method === "GET" && url.searchParams.get("action") === "status") {
    return paymentStatus(database, form.id, url.searchParams.get("order_id") || "");
  }

  // CSV Backend live record lookup
  if (request.method === "GET" && url.searchParams.get("action") === "csv_lookup") {
    return csvLookup(form, url);
  }

  // Check form closing status (deadline timeline and submission count limit)
  const theme = asObject(form.theme);
  const closingStatus = checkFormClosingStatus(form, theme);

  if (request.method === "GET") {
    if (closingStatus.closed) {
      return new Response(renderClosedForm(String(form.title || "Form"), closingStatus.message), { status: 410, headers: htmlHeaders(randomToken()) });
    }

    try {
      await database.rpc("record_hosted_form_view", { p_form_id: form.id });
    } catch (viewErr) {
      console.warn("Unable to record view count:", viewErr);
    }
    const nonce = randomToken();

    // Check if Custom Web App / Custom HTML mode is enabled
    const isCustomWebApp = theme.is_custom_web_app === true || theme.isCustomWebApp === true || theme.enable_custom_html === true || theme.enableCustomHtml === true;
    if (isCustomWebApp) {
      const { data: merchant } = await database.from("merchants")
        .select("business_name,phone,business_type,website,email,webhook_secret")
        .eq("id", form.merchant_id).maybeSingle();
      const { data: methods } = await database.from("merchant_numbers").select("type")
        .eq("merchant_id", form.merchant_id).eq("active", true);
      return new Response(renderCustomWebApp(form, merchant, methods || [], nonce), { headers: htmlHeaders(nonce) });
    }

    const { data: methods } = await database.from("merchant_numbers").select("type")
      .eq("merchant_id", form.merchant_id).eq("active", true);
    return new Response(renderForm(form, methods || [], nonce), { headers: htmlHeaders(nonce) });
  }

  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // Reject POST submission if form has closed
  if (closingStatus.closed) {
    return json({ error: closingStatus.message }, 409);
  }

  if (url.searchParams.get("action") === "upload") return uploadAttachment(database, form, request);
  if (Number(request.headers.get("content-length") || "0") > 65_536) return json({ error: "Submission is too large" }, 413);

  let input: Json;
  try {
    const body = await request.text();
    if (new TextEncoder().encode(body).byteLength > 65_536) return json({ error: "Submission is too large" }, 413);
    input = asObject(JSON.parse(body));
  } catch {
    return json({ error: "Request body must be a valid JSON object" }, 400);
  }

  // Honeypot anti-spam check
  if (input._hp_check || input._honeypot || input._website_hp) {
    return json({ error: "Invalid submission" }, 400);
  }

  try {
    const requestId = String(request.headers.get("idempotency-key") || input.request_id || "");
    if (!isUuid(requestId)) return json({ error: "A valid idempotency key is required" }, 400);
    const fields = Array.isArray(form.fields) ? (form.fields as FormField[]).slice(0, 200) : [];
    const validation = validateAnswers(fields, asObject(input.answers), theme, String(form.id), requestId);
    if (!validation.ok) return json({ error: "Validation failed", fields: validation.errors }, 422);

    // CSV backend record validation on submission
    const enableCsv = theme.enable_csv_backend === true || theme.enableCsvBackend === true;
    const enforceCsvMatch = theme.csv_enforce_match === true || theme.enforce_csv_record_exists === true;
    const lookupFieldId = String(theme.csv_target_lookup_field_id || theme.csvTargetLookupFieldId || "");
    const lookupColumn = String(theme.csv_lookup_column || theme.csvLookupColumn || "");
    if (enableCsv && enforceCsvMatch && lookupFieldId && lookupColumn) {
      const submittedLookup = String(validation.answers[lookupFieldId] || "").trim().toLowerCase();
      const rawCsv = String(theme.csv_raw_data || theme.csvRawData || "");
      const parsedCsv = parseCsv(rawCsv);
      const matched = parsedCsv.rows.some((row) => String(row[lookupColumn] || "").trim().toLowerCase() === submittedLookup);
      if (!matched) {
        return json({ error: `No matching record found in database for '${validation.answers[lookupFieldId]}'` }, 422);
      }
    }

    const name = findAnswer(fields, validation.answers, "NAME").slice(0, 120);
    const phone = findAnswer(fields, validation.answers, "PHONE").replace(/[^0-9+]/g, "").slice(0, 20);
    const email = findAnswer(fields, validation.answers, "EMAIL").trim().slice(0, 254);
    const paymentRequired = theme.enable_payment !== false;
    if (paymentRequired && !/^\+?[0-9]{10,15}$/.test(phone)) return json({ error: "A valid phone field is required for payment" }, 422);
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "Invalid email address" }, 422);

    const products = Array.isArray(form.products) ? (form.products as Json[]).slice(0, 100) : [];
    const product = products.find((item) => String(item.id) === String(input.product_id));
    if (paymentRequired && products.length > 0 && !product) return json({ error: "Select a valid product" }, 422);
    const quantityMinimum = theme.enforce_quantity_range === false ? 1 : Math.max(1, Number(theme.min_quantity || 1));
    const quantityMaximum = theme.enforce_quantity_range === false ? 1000 : Math.max(quantityMinimum, Number(theme.max_quantity || 1000));
    const fieldQuantity = findAnswer(fields, validation.answers, "QUANTITY");
    const requestedQuantity = Number(fieldQuantity || input.quantity || quantityMinimum);
    if (paymentRequired && products.length > 0 && (!Number.isInteger(requestedQuantity) || requestedQuantity < quantityMinimum || requestedQuantity > quantityMaximum)) {
      return json({ error: `Quantity must be a whole number from ${quantityMinimum} to ${quantityMaximum}` }, 422);
    }
    const quantity = clampInteger(requestedQuantity, quantityMinimum, quantityMaximum);
    const customAmount = Number(findAnswer(fields, validation.answers, "CUSTOM_AMOUNT") || input.amount);
    const unitPrice = product ? Number(product.sale_price || product.price) : customAmount;
    const subtotal = paymentRequired ? unitPrice * quantity : 0;
    const taxPercent = Math.min(100, Math.max(0, Number(theme.tax_percent || 0)));
    const amount = Math.round((subtotal + subtotal * taxPercent / 100) * 100) / 100;
    if (paymentRequired && (!Number.isFinite(amount) || amount <= 0 || amount > 10_000_000)) return json({ error: "Invalid payment amount" }, 422);

    const clientHash = await sha256(clientAddress(request));
    const { data: existingSubmission } = await database.from("form_submissions").select("id")
      .eq("form_id", form.id).eq("request_id", requestId).maybeSingle();
    if (!existingSubmission && theme.enable_anti_spam !== false) {
      const { data: allowed, error: rateError } = await database.rpc("check_order_rate_limit", {
        p_merchant_id: form.merchant_id, p_client_hash: clientHash, p_limit: 10, p_window_seconds: 300,
      });
      if (rateError || allowed !== true) return json({ error: "Too many submissions; try again later" }, 429, { "Retry-After": "300" });
    }

    const requestedMethod = normalizePaymentMethod(String(input.payment_method || theme.payment_provider || "AUTO"));
    const { data, error } = await database.rpc("create_hosted_form_submission", {
      p_form_id: form.id, p_request_id: requestId, p_customer_name: name, p_customer_phone: phone,
      p_customer_email: email, p_amount: paymentRequired ? amount : 0, p_payment_method: requestedMethod,
      p_payment_required: paymentRequired, p_answers: validation.answers, p_client_hash: clientHash,
    });
    if (error) {
      console.error("Hosted-form transaction failed", error.code, error.message);
      const message = error.message.toLowerCase();
      if (message.includes("limit reached")) return json({ error: closingStatus.message || "This form is no longer accepting responses" }, 409);
      if (message.includes("already submitted")) return json({ error: "A response was already submitted from this client" }, 409);
      if (message.includes("payment number")) return json({ error: "The merchant has not configured this payment method" }, 409);
      if (message.includes("gateway is disabled")) return json({ error: "This merchant is not accepting payments right now" }, 409);
      if (message.includes("payment amount") || message.includes("daily payment limit")) return json({ error: "This payment is outside the merchant's configured transaction policy" }, 409);
      return json({ error: "Unable to save this response" }, 503);
    }
    const result = Array.isArray(data) ? data[0] : data;
    if (result.created !== false) {
      const notificationTask = sendSubmissionNotifications(database, form, { ...result, answers: validation.answers }, { name, phone, email });
      const edgeRuntime = (globalThis as any).EdgeRuntime;
      if (edgeRuntime?.waitUntil) edgeRuntime.waitUntil(notificationTask);
      else await notificationTask;
    }
    return json({
      submission_id: result.submission_id, order_id: result.order_id, transaction_id: result.transaction_id,
      payment_status: result.payment_status, amount: Number(result.amount || 0), payment_number: result.payment_number,
      payment_method: result.payment_method, expires_at: result.expires_at, payment_required: paymentRequired,
    }, 201);
  } catch (error) {
    console.error("Hosted form request failed", error);
    return json({ error: "Unable to process this response" }, 500);
  }
});

function checkFormClosingStatus(form: Json, theme: Json): { closed: boolean; message: string; reason: string } {
  const defaultMsg = "This form is no longer accepting responses.";
  const customMsg = String(theme.closed_message || theme.closedMessage || defaultMsg);

  // 1. Max submission response limit
  const closeAfterLimit = theme.close_after_limit === true || theme.closeAfterLimit === true;
  const maxResponses = Math.max(1, Number(theme.max_responses || theme.maxResponses || 1000));
  if (closeAfterLimit && Number(form.submissions_count || 0) >= maxResponses) {
    return { closed: true, message: customMsg, reason: "limit" };
  }

  // 2. Closing Timeline Deadline
  const enableTimeline = theme.enable_closing_timeline === true || theme.enableClosingTimeline === true;
  const deadlineEpoch = Number(theme.closing_deadline_epoch || theme.closingDeadlineEpoch || 0);
  if (enableTimeline && deadlineEpoch > 0 && Date.now() > deadlineEpoch) {
    return { closed: true, message: customMsg, reason: "deadline" };
  }

  return { closed: false, message: "", reason: "" };
}

function parseCsv(raw: string): { headers: string[]; rows: Record<string, string>[] } {
  if (!raw || !raw.trim()) return { headers: [], rows: [] };
  const cleanRaw = raw.replace(/^\uFEFF/, "");
  const lines = cleanRaw.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n").filter((l) => l.trim().length > 0);
  if (lines.length === 0) return { headers: [], rows: [] };

  const firstLine = lines[0];
  const delimiter = (firstLine.includes("\t") && !firstLine.includes(",")) ? "\t" : (firstLine.includes(";") && !firstLine.includes(",")) ? ";" : ",";

  const parseLine = (line: string): string[] => {
    const entries: string[] = [];
    let current = "";
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const char = line[i];
      if (char === '"' || char === "'") {
        if (inQuotes && line[i + 1] === char) {
          current += char;
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (char === delimiter && !inQuotes) {
        entries.push(current.trim());
        current = "";
      } else {
        current += char;
      }
    }
    entries.push(current.trim());
    return entries;
  };

  const headers = parseLine(lines[0]).map((h) => h.replace(/^["']|["']$/g, "").trim());
  const rows: Record<string, string>[] = [];
  for (let i = 1; i < lines.length; i++) {
    const values = parseLine(lines[i]);
    const row: Record<string, string> = {};
    headers.forEach((h, idx) => {
      row[h] = values[idx] !== undefined ? values[idx].replace(/^["']|["']$/g, "").trim() : "";
    });
    rows.push(row);
  }
  return { headers, rows };
}

function csvLookup(form: Json, url: URL): Response {
  const theme = asObject(form.theme);
  const enableCsv = theme.enable_csv_backend === true || theme.enableCsvBackend === true;
  if (!enableCsv) return json({ error: "CSV backend is not enabled for this form" }, 400);

  const query = String(url.searchParams.get("query") || url.searchParams.get("lookup_value") || "").trim();
  if (!query) return json({ error: "Lookup query parameter is required" }, 400);

  const lookupCol = String(url.searchParams.get("lookup_column") || theme.csv_lookup_column || theme.csvLookupColumn || "").trim();
  const rawCsv = String(theme.csv_raw_data || theme.csvRawData || "");
  const parsed = parseCsv(rawCsv);
  const normalizedQuery = query.toLowerCase();

  const match = parsed.rows.find((row) => {
    if (lookupCol && row[lookupCol] !== undefined) {
      return String(row[lookupCol] || "").trim().toLowerCase() === normalizedQuery;
    }
    return Object.values(row).some((val) => String(val || "").trim().toLowerCase() === normalizedQuery);
  });

  return json({
    ok: true,
    found: Boolean(match),
    record: match || null,
    headers: parsed.headers,
  }, 200);
}

async function paymentStatus(database: any, formId: string, orderId: string): Promise<Response> {
  if (!isUuid(orderId)) return json({ error: "Invalid order" }, 400);
  const { data, error } = await database.from("orders")
    .select("id,status,tran_id,amount,expires_at,metadata")
    .eq("id", orderId).contains("metadata", { form_id: formId }).maybeSingle();
  if (error || !data) return json({ error: "Order not found" }, 404);
  return json({ order_id: data.id, status: data.status, transaction_id: data.tran_id, amount: data.amount, expires_at: data.expires_at }, 200);
}

async function uploadAttachment(database: any, form: Json, request: Request): Promise<Response> {
  const declaredLength = Number(request.headers.get("content-length") || "0");
  if (declaredLength > 10_500_000) return json({ error: "Attachment is too large" }, 413);
  try {
    const body = await request.formData();
    const requestId = String(request.headers.get("idempotency-key") || body.get("request_id") || "");
    const fieldId = String(body.get("field_id") || "");
    const file = body.get("file");
    if (!isUuid(requestId) || !/^[a-zA-Z0-9_-]{1,100}$/.test(fieldId)) return json({ error: "Invalid upload request" }, 400);
    if (!(file instanceof File) || file.size <= 0) return json({ error: "Select a non-empty file" }, 400);
    const fields = Array.isArray(form.fields) ? (form.fields as FormField[]).slice(0, 200) : [];
    const field = fields.find((item) => String(item.id) === fieldId && ["FILE_UPLOAD", "CAMERA_UPLOAD"].includes(String(item.type || "").toUpperCase()));
    if (!field) return json({ error: "Upload field not found" }, 404);
    const { data: finalizedSubmission } = await database.from("form_submissions").select("id")
      .eq("form_id", form.id).eq("request_id", requestId).maybeSingle();
    if (finalizedSubmission) return json({ error: "This submission is already finalized" }, 409);
    const theme = asObject(form.theme);
    const configuredLimit = Number(field.max_file_size_bytes ?? theme.max_file_size_bytes ?? 5_242_880);
    const maxBytes = theme.enforce_file_size_limit === false ? 10_485_760 : Math.min(10_485_760, Math.max(1_024, Number.isFinite(configuredLimit) ? configuredLimit : 5_242_880));
    if (file.size > maxBytes) return json({ error: `Attachment exceeds the ${Math.ceil(maxBytes / 1_048_576)} MB limit` }, 413);
    const extension = file.name.includes(".") ? file.name.split(".").pop()!.toLowerCase().replace(/[^a-z0-9]/g, "") : "";
    const allowed = Array.isArray(field.allowed_file_extensions)
      ? field.allowed_file_extensions.map((item) => String(item).toLowerCase().replace(/^\./, "")).filter(Boolean)
      : [];
    if (!extension || (allowed.length > 0 && !allowed.includes(extension))) return json({ error: "Unsupported attachment type" }, 415);
    const clientHash = await sha256(`upload:${clientAddress(request)}`);
    if (theme.enable_anti_spam !== false) {
      const { data: allowedRequest, error: rateError } = await database.rpc("check_order_rate_limit", {
        p_merchant_id: form.merchant_id, p_client_hash: clientHash, p_limit: 20, p_window_seconds: 300,
      });
      if (rateError || allowedRequest !== true) return json({ error: "Too many uploads; try again later" }, 429, { "Retry-After": "300" });
    }
    const safeName = file.name.replace(/[^a-zA-Z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").slice(-120) || `attachment.${extension}`;
    const objectPath = `${String(form.id)}/${requestId}/${fieldId}-${randomToken().slice(0, 16)}-${safeName}`;
    const { error } = await database.storage.from("form-uploads").upload(objectPath, file, {
      contentType: file.type || "application/octet-stream", upsert: false, cacheControl: "0",
    });
    if (error) {
      console.error("Hosted-form upload failed", error.message);
      return json({ error: "Unable to store this attachment" }, 503);
    }
    return json({ file: { object_path: objectPath, file_name: file.name.slice(0, 255), size: file.size, mime_type: (file.type || "application/octet-stream").slice(0, 150) } }, 201);
  } catch (error) {
    console.error("Hosted-form attachment request failed", error);
    return json({ error: "Unable to process this attachment" }, 400);
  }
}

async function signedAttachment(database: any, form: Json, request: Request, submissionId: string, fieldId: string): Promise<Response> {
  const token = (request.headers.get("authorization") || "").replace(/^Bearer\s+/i, "").trim();
  if (!token || !isUuid(submissionId) || !/^[a-zA-Z0-9_-]{1,100}$/.test(fieldId)) return json({ error: "Unauthorized" }, 401);
  const { data: authData, error: authError } = await database.auth.getUser(token);
  if (authError || !authData?.user) return json({ error: "Unauthorized" }, 401);
  const { data: merchant } = await database.from("merchants").select("id")
    .eq("id", form.merchant_id).eq("user_id", authData.user.id).maybeSingle();
  if (!merchant) return json({ error: "Forbidden" }, 403);
  const { data: submission } = await database.from("form_submissions").select("answers")
    .eq("id", submissionId).eq("form_id", form.id).maybeSingle();
  if (!submission) return json({ error: "Attachment not found" }, 404);
  const metadata = asObject(asObject(submission.answers)[fieldId]);
  const objectPath = String(metadata.object_path || "");
  if (!objectPath.startsWith(`${String(form.id)}/`) || objectPath.length > 600) return json({ error: "Attachment not found" }, 404);
  const downloadName = String(metadata.file_name || "attachment").replace(/[\r\n"\\/]/g, "-").slice(0, 255) || "attachment";
  const { data, error } = await database.storage.from("form-uploads").createSignedUrl(objectPath, 60, {
    download: downloadName,
  });
  if (error || !data?.signedUrl) return json({ error: "Unable to open this attachment" }, 503);
  return json({ url: data.signedUrl, expires_in: 60, file_name: downloadName }, 200);
}

async function sendSubmissionNotifications(
  database: any,
  form: Json,
  submission: Json,
  customer: { name: string; phone: string; email: string },
): Promise<void> {
  try {
    const theme = asObject(form.theme);
    const { data: merchant } = await database.from("merchants")
      .select("user_id,business_name,webhook_secret")
      .eq("id", form.merchant_id).maybeSingle();

    // 1. In-app merchant notification
    if (merchant?.user_id) {
      await database.from("notifications").insert({
        user_id: merchant.user_id,
        type: "FORM_SUBMISSION",
        title: `New response: ${String(form.title || "Hosted form").slice(0, 120)}`,
        body: `${customer.name || "Anonymous"} submitted a response${Number(submission.amount || 0) > 0 ? ` for BDT ${Number(submission.amount).toFixed(2)}` : ""}.`,
      });
    }

    const jobs: Promise<unknown>[] = [];

    // 2. Email notification via Resend
    const recipientEmail = String(theme.notification_email || "").trim();
    const resendKey = Deno.env.get("RESEND_API_KEY") || "";
    if (theme.email_notifications === true && resendKey && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(recipientEmail)) {
      const safeTitle = escapeHtml(String(form.title || "Hosted form"));
      const safeCustomer = escapeHtml(customer.name || "Anonymous");
      const safeContact = escapeHtml(customer.email || customer.phone || "Not provided");
      jobs.push(fetchWithRetry("https://api.resend.com/emails", {
        method: "POST",
        headers: { authorization: `Bearer ${resendKey}`, "content-type": "application/json" },
        body: JSON.stringify({
          from: Deno.env.get("FORM_NOTIFICATION_FROM") || "SwapnoPay Forms <forms@swapnopay.com>",
          to: [recipientEmail],
          subject: `New form response: ${String(form.title || "Hosted form").replace(/[\r\n]/g, " ").slice(0, 120)}`,
          html: `<h2>${safeTitle}</h2><p><strong>${safeCustomer}</strong> submitted a new response.</p><p>Contact: ${safeContact}</p><p>Amount: BDT ${Number(submission.amount || 0).toFixed(2)}</p><p>Submission ID: ${escapeHtml(String(submission.submission_id || ""))}</p>`,
        }),
      }));
    }

    // 3. SMS notification
    const recipientPhone = String(theme.notification_sms_number || "").replace(/[^0-9+]/g, "");
    const smsUrl = Deno.env.get("FORM_SMS_PROVIDER_URL") || "";
    const smsToken = Deno.env.get("FORM_SMS_PROVIDER_TOKEN") || "";
    if (theme.sms_notifications === true && smsUrl && /^\+?[0-9]{10,15}$/.test(recipientPhone) && safeRedirectUrl(smsUrl)) {
      jobs.push(fetchWithRetry(smsUrl, {
        method: "POST",
        headers: { authorization: smsToken ? `Bearer ${smsToken}` : "", "content-type": "application/json" },
        body: JSON.stringify({
          to: recipientPhone,
          message: `New response on ${String(form.title || "Hosted form").slice(0, 80)} from ${customer.name || customer.phone || "Anonymous"}. Submission ${String(submission.submission_id || "").slice(0, 36)}.`,
          submission_id: submission.submission_id,
          form_id: form.id,
        }),
      }));
    }

    // 4. Discord Webhook notification
    const discordUrl = String(theme.discord_webhook_url || theme.discordWebhookUrl || "").trim();
    if (discordUrl && safeRedirectUrl(discordUrl)) {
      jobs.push(fetchWithRetry(discordUrl, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          content: `🔔 **New Form Submission** for **${String(form.title || "Form")}**`,
          embeds: [{
            title: String(form.title || "Form Submission"),
            color: 5983424,
            fields: [
              { name: "Customer", value: customer.name || "Anonymous", inline: true },
              { name: "Phone", value: customer.phone || "Not provided", inline: true },
              { name: "Amount", value: Number(submission.amount || 0) > 0 ? `BDT ${Number(submission.amount).toFixed(2)}` : "Free", inline: true },
              { name: "Submission ID", value: String(submission.submission_id || "").slice(0, 36), inline: false },
            ],
            timestamp: new Date().toISOString(),
          }],
        }),
      }));
    }

    // 5. Slack Webhook notification
    const slackUrl = String(theme.slack_webhook_url || theme.slackWebhookUrl || "").trim();
    if (slackUrl && safeRedirectUrl(slackUrl)) {
      jobs.push(fetchWithRetry(slackUrl, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          text: `🔔 New Form Submission: *${String(form.title || "Form")}* from *${customer.name || customer.phone || "Anonymous"}*`,
          blocks: [
            {
              type: "section",
              text: {
                type: "mrkdwn",
                text: `*New Form Submission received*\n*Form:* ${escapeHtml(String(form.title || "Form"))}\n*Customer:* ${escapeHtml(customer.name || "Anonymous")} (${escapeHtml(customer.phone || customer.email || "No contact")})\n*Amount:* BDT ${Number(submission.amount || 0).toFixed(2)}`,
              },
            },
          ],
        }),
      }));
    }

    // 6. Generic HTTP Webhook with HMAC-SHA256 signature
    const genericWebhookUrl = String(theme.webhook_url || theme.webhookUrl || theme.payment_callback_url || "").trim();
    if (genericWebhookUrl && safeRedirectUrl(genericWebhookUrl)) {
      const webhookPayload = JSON.stringify({
        event: "form.submission.created",
        created_at: new Date().toISOString(),
        form: { id: form.id, title: form.title, slug: form.slug },
        submission: {
          id: submission.submission_id,
          order_id: submission.order_id,
          transaction_id: submission.transaction_id,
          amount: Number(submission.amount || 0),
          payment_status: submission.payment_status,
          payment_method: submission.payment_method,
          answers: submission.answers || {},
        },
        customer,
      });
      const webhookSecret = String(merchant?.webhook_secret || theme.webhook_secret || "");
      const headers: Record<string, string> = { "content-type": "application/json" };
      if (webhookSecret) {
        const signature = await hmacSha256(webhookSecret, webhookPayload);
        headers["x-swapnopay-signature"] = `sha256=${signature}`;
      }
      jobs.push(fetchWithRetry(genericWebhookUrl, { method: "POST", headers, body: webhookPayload }));
    }

    const outcomes = await Promise.allSettled(jobs);
    outcomes.filter((item) => item.status === "rejected").forEach((item) => console.error("Form notification delivery failed", (item as PromiseRejectedResult).reason));
  } catch (error) {
    console.error("Unable to dispatch form notifications", error);
  }
}

async function fetchWithRetry(url: string, init: RequestInit): Promise<void> {
  let lastError: unknown = new Error("Notification delivery failed");
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url, init);
      if (response.ok) return;
      lastError = new Error(`Notification provider returned HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt < 2) await new Promise((resolve) => setTimeout(resolve, 500 * (attempt + 1)));
  }
  throw lastError;
}

function renderCustomWebApp(form: Json, merchant: Json | null, methodRows: Json[], nonce: string): string {
  const title = escapeHtml(String(form.title || "Custom Web App"));
  const description = escapeHtml(String(form.description || ""));
  const theme = asObject(form.theme);
  const rawHtml = String(theme.custom_html_content || theme.customHtmlContent || theme.custom_html || `<div class="custom-web-card"><h2>{{form_title}}</h2><p>{{form_description}}</p><div class="merchant-badge">🏪 {{merchant_name}} | 📞 {{merchant_phone}}</div><div class="stats-grid"><div class="stat-box"><span class="stat-num">{{total_products}}</span><span>Products</span></div><div class="stat-box"><span class="stat-num">{{total_customers}}</span><span>Customers</span></div><div class="stat-box"><span class="stat-num">{{total_sales}}</span><span>Total Sales</span></div></div></div>`);
  const rawCss = sanitizeCss(String(theme.custom_css || theme.customCss || ""));
  const rawJs = String(theme.custom_js || theme.customJs || "");

  const products = Array.isArray(form.products) ? (form.products as Json[]) : [];
  const variableMap: Record<string, string> = {
    merchant_name: String(merchant?.business_name || "SwapnoPay Merchant"),
    merchant_phone: String(merchant?.phone || ""),
    business_category: String(merchant?.business_type || "Business"),
    merchant_website: String(merchant?.website || ""),
    merchant_email: String(merchant?.email || ""),
    form_title: String(form.title || ""),
    form_description: String(form.description || ""),
    submissions_count: String(form.submissions_count || 0),
    total_sales: `BDT ${Number(form.total_revenue || 0).toFixed(2)}`,
    total_products: String(products.length),
    total_customers: String(form.submissions_count || 0),
    currency: "BDT",
    current_time: new Date().toLocaleString("en-US", { timeZone: "Asia/Dhaka" }),
  };

  const customVars = Array.isArray(theme.custom_variables) ? theme.custom_variables : Array.isArray(theme.customVariables) ? theme.customVariables : [];
  for (const v of customVars) {
    const item = asObject(v);
    const key = String(item.key || "").replace(/[^a-zA-Z0-9_]/g, "");
    if (key) variableMap[key] = String(item.example_value ?? item.defaultValue ?? "");
  }

  const resolveVars = (tmpl: string) => tmpl.replace(/{{\s*([a-zA-Z0-9_]+)\s*}}/g, (_, key) => variableMap[key] ?? "");
  const resolvedHtml = resolveVars(rawHtml);
  const resolvedCss = resolveVars(rawCss);
  const resolvedJs = resolveVars(rawJs);

  const enableCsv = theme.enable_csv_backend === true || theme.enableCsvBackend === true;
  const rawCsv = String(theme.csv_raw_data || theme.csvRawData || "");
  const parsedCsv = enableCsv ? parseCsv(rawCsv) : { headers: [], rows: [] };
  const csvDataJson = JSON.stringify(parsedCsv.rows.slice(0, 500));
  const csvLookupCol = JSON.stringify(String(theme.csv_lookup_column || theme.csvLookupColumn || ""));
  const csvTargetLookupId = JSON.stringify(String(theme.csv_target_lookup_field_id || theme.csvTargetLookupFieldId || ""));
  const csvMappings = JSON.stringify(asObject(theme.csv_column_mappings || theme.csvColumnMappings));

  const enableClosing = theme.enable_closing_timeline === true || theme.enableClosingTimeline === true;
  const deadlineEpoch = Number(theme.closing_deadline_epoch || theme.closingDeadlineEpoch || 0);
  const showCountdown = theme.show_countdown_timer !== false && theme.showCountdownTimer !== false;
  const closedMessage = escapeHtml(String(theme.closed_message || theme.closedMessage || "This form is no longer accepting responses."));

  const closingBannerHtml = enableClosing && showCountdown ? `
    <div id="form-closing-timer-banner" style="max-width:720px;margin:0 auto 20px auto;background:#FEF3C7;color:#92400E;border:1.5px solid #F59E0B;border-radius:14px;padding:12px 18px;display:flex;align-items:center;justify-content:space-between;font-weight:600;font-size:14px;">
      <span>⏳ Form Submissions Close In:</span>
      <span id="countdown-val" style="font-family:monospace;font-size:15px;font-weight:800;color:#B45309;">Loading...</span>
    </div>
  ` : "";

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${title}</title>
  <style>
    :root{--primary:#5B4BDB;--bg:#F6F7FB}
    *{box-sizing:border-box}
    body{font-family:Inter,system-ui,-apple-system,sans-serif;background:var(--bg);margin:0;padding:min(24px,5vw);color:#172033}
    .custom-web-card{max-width:720px;margin:0 auto;background:#fff;border-radius:20px;padding:clamp(18px,4vw,32px);box-shadow:0 10px 35px #17203318}
    .merchant-badge{display:inline-block;background:#EEF2FF;color:#4F46E5;padding:6px 14px;border-radius:20px;font-size:13px;font-weight:600;margin:12px 0}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin-top:20px}
    .stat-box{background:#F1F5F9;padding:14px;border-radius:12px;text-align:center;font-size:12px}
    .stat-num{display:block;font-size:20px;font-weight:bold;color:#4F46E5;margin-bottom:4px}
    button{background:#5B4BDB;color:#fff;border:0;padding:13px 20px;border-radius:10px;font-weight:700;cursor:pointer}
    button:disabled{opacity:.5;cursor:not-allowed}
    ${resolvedCss}
  </style>
</head>
<body>
  ${closingBannerHtml}
  ${resolvedHtml}

  <script nonce="${nonce}">
    window.csvBackendData = ${csvDataJson};
    window.csvLookupColumn = ${csvLookupCol};
    window.csvTargetLookupFieldId = ${csvTargetLookupId};
    window.csvColumnMappings = ${csvMappings};

    window.swapnopaySubmit = async function(answers, options) {
      options = options || {};
      const requestId = crypto.randomUUID();
      const payload = {
        request_id: requestId,
        answers: answers || {},
        product_id: options.product_id || null,
        quantity: options.quantity || 1,
        amount: options.amount || null,
        payment_method: options.payment_method || 'AUTO'
      };
      const response = await fetch(location.href, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'idempotency-key': requestId },
        body: JSON.stringify(payload)
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || (data.fields ? Object.values(data.fields)[0] : 'Submission failed'));
      return data;
    };

    (function() {
      const deadlineEpoch = ${deadlineEpoch};
      const enableTimeline = ${enableClosing};
      if (enableTimeline && deadlineEpoch > 0) {
        function updateCountdown() {
          const now = Date.now();
          const diff = deadlineEpoch - now;
          const banner = document.getElementById('form-closing-timer-banner');
          const val = document.getElementById('countdown-val');
          if (diff <= 0) {
            if (val) val.innerText = "EXPIRED";
            if (banner) {
              banner.style.background = "#FEE2E2";
              banner.style.borderColor = "#EF4444";
              banner.style.color = "#991B1B";
              banner.innerHTML = "🔒 <strong>${closedMessage}</strong>";
            }
            document.querySelectorAll('button[type="submit"], .submit-btn, .pay-btn, .order-btn').forEach(function(b) {
              b.disabled = true;
              b.innerText = "Form Closed";
            });
          } else {
            const d = Math.floor(diff / (1000 * 60 * 60 * 24));
            const h = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
            const s = Math.floor((diff % (1000 * 60)) / 1000);
            if (val) val.innerText = (d > 0 ? d + "d " : "") + h + "h " + m + "m " + s + "s";
          }
        }
        setInterval(updateCountdown, 1000);
        updateCountdown();
      }
    })();

    ${resolvedJs}
  </script>
</body>
</html>`;
}

function renderForm(form: Json, methodRows: Json[], nonce: string): string {
  const title = escapeHtml(String(form.title || "Form"));
  const description = escapeHtml(String(form.description || ""));
  const fields = Array.isArray(form.fields) ? (form.fields as FormField[]).slice(0, 200) : [];
  const products = Array.isArray(form.products) ? (form.products as Json[]).slice(0, 100) : [];
  const pages = Array.isArray(form.pages) ? form.pages as Json[] : [];
  const theme = asObject(form.theme);
  const primary = safeColor(theme.primary_color, "#5B4BDB");
  const background = safeColor(theme.background_color, "#F6F7FB");
  const gradientStart = safeColor(theme.gradient_start, "#5B7FFF");
  const gradientEnd = safeColor(theme.gradient_end, "#7C4DFF");
  const bodyBackground = String(theme.background_style || "SOLID") === "GRADIENT"
    ? `linear-gradient(135deg,${gradientStart},${gradientEnd})` : background;
  const bannerUrl = safeRedirectUrl(String(form.banner_url || ""));
  const logoUrl = safeRedirectUrl(String(form.logo_url || ""));
  const width = Math.min(1200, Math.max(320, Number(theme.form_width || 720)));
  const radius = Math.min(40, Math.max(0, Number(theme.border_radius || 16)));
  const pageMargin = Math.min(64, Math.max(8, Number(theme.page_margin || 24)));
  const fontFamily = (() => {
    switch (String(theme.font_family || "Inter").toUpperCase()) {
      case "SERIF": return "Georgia,'Times New Roman',serif";
      case "MONOSPACE": return "ui-monospace,SFMono-Regular,Consolas,monospace";
      case "SYSTEM": return "system-ui,-apple-system,Segoe UI,sans-serif";
      default: return "Inter,system-ui,sans-serif";
    }
  })();
  const buttonRadius = String(theme.button_shape || "ROUNDED").toUpperCase() === "PILL" ? 999 : String(theme.button_shape || "ROUNDED").toUpperCase() === "SQUARE" ? 0 : 10;
  const customCss = sanitizeCss(String(theme.custom_css || theme.customCss || ""));
  const customTemplate = sanitizeCustomHtml(String(theme.custom_html || theme.customHtmlContent || ""));
  const customVariableDefaults = {
    form_title: String(form.title || "").slice(0, 200),
    form_description: String(form.description || "").slice(0, 1_000),
    ...Object.fromEntries(
      (Array.isArray(theme.custom_variables) ? (theme.custom_variables as Json[]).slice(0, 100) : [])
        .map((item) => [String(item.key || "").replace(/[^a-zA-Z0-9_]/g, "").slice(0, 64), String(item.example_value || "").slice(0, 500)])
        .filter(([key]) => key.length > 0),
    ),
  };
  const redirectType = String(theme.redirect_type || "SUCCESS_MSG");
  const redirectUrl = safeRedirectUrl(String(theme.redirect_url || ""));
  const redirectDelay = Math.min(300, Math.max(0, Number(theme.redirect_delay_seconds || 0)));
  const paymentRequired = theme.enable_payment !== false;
  const waitForPayment = theme.require_payment_before_submit !== false;
  const openInNewTab = theme.open_in_new_tab === true;
  const timerSeconds = theme.enable_timer === true ? Math.min(86_400, Math.max(60, Number(theme.timer_minutes || 30) * 60)) : 0;
  const minQuantity = theme.enforce_quantity_range === false ? 1 : Math.max(1, Number(theme.min_quantity || 1));
  const maxQuantity = theme.enforce_quantity_range === false ? 1000 : Math.max(minQuantity, Number(theme.max_quantity || 1000));
  const methods = [...new Set(methodRows.map((row) => String(row.type || "")).filter(Boolean))];
  const provider = normalizePaymentMethod(String(theme.payment_provider || "AUTO"));
  const usableMethods = provider === "AUTO" ? methods : methods.filter((item) => item.toUpperCase() === provider.toUpperCase());
  const renderedFields = theme.shuffle_questions === true ? shuffle(fields) : fields;
  const requiredIndicator = theme.required_indicator !== false;
  const enforceRequired = theme.enforce_required_fields !== false;
  const hasQuantityField = fields.some((field) => String(field.type || "").toUpperCase() === "QUANTITY");
  const productInput = !paymentRequired ? "" : products.length > 0
    ? `<label for="product_id">Product</label><select id="product_id" name="product_id" required><option value="">Select a product</option>${products.map((product) => `<option value="${escapeHtml(String(product.id || ""))}">${escapeHtml(String(product.title || "Product"))} — BDT ${Number(product.sale_price || product.price || 0).toFixed(2)}</option>`).join("")}</select>${hasQuantityField ? "" : `<label for="quantity">Quantity</label><input id="quantity" name="quantity" type="number" min="${minQuantity}" max="${maxQuantity}" value="${minQuantity}" required>`}`
    : "";
  const methodInput = paymentRequired && usableMethods.length > 0
    ? `<label for="payment_method">Payment method</label><select id="payment_method" name="payment_method" required>${usableMethods.map((method) => `<option value="${escapeHtml(method)}">${escapeHtml(method)}</option>`).join("")}</select>` : "";
  const submitButton = `<button id="submit" type="submit">${paymentRequired ? "Continue to payment" : "Submit response"}</button>`;
  const pageList = pages.length > 0 ? pages.slice(0, 25) : [{ title: "Form details", subtitle: "" }];
  const multiPage = theme.multi_page === true && pageList.length > 1;
  const progressStyle = String(theme.progress_tracker_style || "BAR").toUpperCase();
  const progressMarkup = !multiPage || progressStyle === "HIDE" ? "" : progressStyle === "NUMBER"
    ? `<p class="progress-number" id="form-progress" aria-live="polite">Step 1 of ${pageList.length}</p>`
    : `<div class="progress-track" role="progressbar" aria-label="Form progress" aria-valuemin="1" aria-valuemax="${pageList.length}" aria-valuenow="1"><div id="form-progress" style="width:${100 / pageList.length}%"></div></div>`;
  const groupedFields = pageList.map(() => [] as FormField[]);
  renderedFields.forEach((field) => {
    const requestedIndex = Math.trunc(Number(field.page_index || 0));
    const pageIndex = Math.min(groupedFields.length - 1, Math.max(0, Number.isFinite(requestedIndex) ? requestedIndex : 0));
    groupedFields[pageIndex].push(field);
  });
  const fieldsMarkup = multiPage
    ? pageList.map((page, pageIndex) => {
      const isLast = pageIndex === pageList.length - 1;
      const heading = escapeHtml(String(page.title || `Page ${pageIndex + 1}`));
      const subtitle = escapeHtml(String(page.subtitle || ""));
      const previous = pageIndex > 0 ? `<button class="secondary" type="button" data-page-previous>Previous</button>` : "";
      const next = !isLast ? `<button type="button" data-page-next>Continue</button>` : submitButton;
      return `<section class="form-page" data-form-page="${pageIndex}"${pageIndex === 0 ? "" : " hidden"}><h2>${heading}</h2>${subtitle ? `<p>${subtitle}</p>` : ""}${groupedFields[pageIndex].map((field) => renderConditionalField(field, enforceRequired, requiredIndicator, theme)).join("")}${isLast ? productInput + methodInput : ""}<div class="page-nav">${previous}${next}</div></section>`;
    }).join("")
    : `${renderedFields.map((field) => renderConditionalField(field, enforceRequired, requiredIndicator, theme)).join("")}${productInput}${methodInput}${submitButton}`;

  // Hide form header feature
  const hideHeader = theme.hide_form_header === true || theme.hideFormHeader === true;
  const headerMarkup = hideHeader ? "" : `
    ${bannerUrl ? `<img class="banner" src="${escapeHtml(bannerUrl)}" alt="">` : ""}
    ${logoUrl ? `<img class="logo" src="${escapeHtml(logoUrl)}" alt="">` : ""}
    <h1>${title}</h1>
    ${description ? `<p>${description}</p>` : ""}
  `;

  // Closing timeline countdown banner
  const enableClosing = theme.enable_closing_timeline === true || theme.enableClosingTimeline === true;
  const deadlineEpoch = Number(theme.closing_deadline_epoch || theme.closingDeadlineEpoch || 0);
  const showCountdown = theme.show_countdown_timer !== false && theme.showCountdownTimer !== false;
  const closedMessage = escapeHtml(String(theme.closed_message || theme.closedMessage || "This form is no longer accepting responses."));

  const closingBannerMarkup = enableClosing && showCountdown ? `
    <div id="form-closing-timer-banner" style="background:#FEF3C7;color:#92400E;border:1.5px solid #F59E0B;border-radius:12px;padding:12px 16px;display:flex;align-items:center;justify-content:space-between;font-weight:600;font-size:13px;margin-bottom:18px;">
      <span>⏳ Submissions Close In:</span>
      <span id="countdown-val" style="font-family:monospace;font-size:14px;font-weight:800;color:#B45309;">Loading...</span>
    </div>
  ` : "";

  // CSV backend dataset injection
  const enableCsv = theme.enable_csv_backend === true || theme.enableCsvBackend === true;
  const rawCsv = String(theme.csv_raw_data || theme.csvRawData || "");
  const parsedCsv = enableCsv ? parseCsv(rawCsv) : { headers: [], rows: [] };
  const csvDataJson = JSON.stringify(parsedCsv.rows.slice(0, 500));
  const csvLookupCol = JSON.stringify(String(theme.csv_lookup_column || theme.csvLookupColumn || ""));
  const csvTargetLookupId = JSON.stringify(String(theme.csv_target_lookup_field_id || theme.csvTargetLookupFieldId || ""));
  const csvMappings = JSON.stringify(asObject(theme.csv_column_mappings || theme.csvColumnMappings));

  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>
  :root{--primary:${primary};--bg:${background};--radius:${radius}px}*{box-sizing:border-box}body{font-family:${fontFamily};background:${bodyBackground};background-attachment:fixed;margin:0;padding:min(${pageMargin}px,5vw);color:#172033}.card{max-width:${width}px;margin:auto;background:#fff;padding:clamp(18px,4vw,32px);border-radius:var(--radius);box-shadow:0 10px 35px #17203318}.banner{width:100%;max-height:260px;object-fit:cover;border-radius:calc(var(--radius) * .7);margin-bottom:18px}.logo{width:72px;height:72px;object-fit:contain;margin-bottom:12px}h1{margin-top:0}.form-page[hidden],[data-condition-field][hidden]{display:none}label,.label{display:block;margin:14px 0 6px;font-weight:650}input,select,textarea,button{width:100%;padding:13px;border-radius:10px;border:1px solid #ccd2df;font:inherit;background:#fff}textarea{min-height:100px;resize:vertical}button{margin-top:20px;background:var(--primary);color:#fff;border:0;border-radius:${buttonRadius}px;font-weight:750;cursor:pointer}button:disabled{opacity:.6;cursor:wait}.page-nav{display:flex;gap:12px;align-items:center}.page-nav button{flex:1}.secondary{background:#eef1f6;color:#172033}.progress-track{height:8px;background:#eef1f6;border-radius:99px;overflow:hidden;margin:18px 0}.progress-track div{height:100%;background:var(--primary);transition:width .2s ease}.progress-number{color:#667085;font-size:.875rem;font-weight:650;margin:16px 0}.choice{display:flex;gap:8px;align-items:center;margin:8px 0}.choice input{width:auto}.helper{display:block;color:#667085;font-size:.875rem;margin-top:5px}.code-block{margin:14px 0}.error{color:#b42318;margin-top:12px}.success{color:#067647}.payment{background:#f8f7ff;border:1px solid #d9d5ff;border-radius:12px;padding:16px;margin-top:16px;white-space:pre-wrap}</style>${customCss ? `<style>${customCss}</style>` : ""}</head><body><main class="card">${closingBannerMarkup}${headerMarkup}${progressMarkup}<form id="form" novalidate><input type="hidden" name="_hp_check" value="">${fieldsMarkup}</form><div id="result" role="status" aria-live="polite"></div></main><script nonce="${nonce}">
  const form=document.querySelector('#form'),result=document.querySelector('#result'),submit=document.querySelector('#submit');const customTemplate=${JSON.stringify(customTemplate)},customDefaults=${JSON.stringify(customVariableDefaults)},redirectType=${JSON.stringify(redirectType)},redirectUrl=${JSON.stringify(redirectUrl)},redirectDelay=${redirectDelay},openInNewTab=${openInNewTab},timerSeconds=${timerSeconds},waitForPayment=${waitForPayment};
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fieldValue=id=>{const controls=[...document.querySelectorAll('[data-field-id="'+CSS.escape(id)+'"]')].filter(el=>!el.disabled);if(!controls.length)return'';if(controls[0].type==='radio')return controls.find(el=>el.checked)?.value||'';if(controls[0].type==='checkbox')return controls[0].checked?'true':'false';if(controls[0].multiple)return[...controls[0].selectedOptions].map(option=>option.value).join(', ');return controls[0].value||''};
  function renderCustomBlocks(){const ctx={...customDefaults,name:'',email:'',phone:''};document.querySelectorAll('[data-field-id]').forEach(el=>{const value=fieldValue(el.dataset.fieldId),key='field_'+String(el.dataset.fieldLabel||el.dataset.fieldId).toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'');ctx[key]=value;if(el.dataset.fieldType==='NAME')ctx.name=value;if(el.dataset.fieldType==='EMAIL')ctx.email=value;if(el.dataset.fieldType==='PHONE')ctx.phone=value});document.querySelectorAll('[data-code-template]').forEach(block=>{block.innerHTML=block.dataset.codeTemplate.replace(/{{\s*([a-zA-Z0-9_]+)\s*}}/g,(_,key)=>esc(ctx[key]??''))})}
  function applyConditions(){document.querySelectorAll('[data-condition-field]').forEach(wrapper=>{const dependsOn=wrapper.dataset.dependsOn||'',expected=wrapper.dataset.conditionValue||'',actual=fieldValue(dependsOn),operator=wrapper.dataset.conditionOperator||'EQUALS';const visible=!dependsOn||(operator==='NOT_EQUALS'?actual!==expected:operator==='CONTAINS'?actual.includes(expected):actual===expected);wrapper.hidden=!visible;wrapper.querySelectorAll('input,select,textarea').forEach(control=>control.disabled=!visible)})}
  document.querySelectorAll('select[data-field-type="MULTI_SELECT"]').forEach(select=>{const hidden=document.createElement('input');hidden.type='hidden';hidden.dataset.fieldId=select.dataset.fieldId;hidden.dataset.fieldLabel=select.dataset.fieldLabel;hidden.dataset.fieldType=select.dataset.fieldType;select.closest('[data-condition-field]').append(hidden);const sync=()=>hidden.value=JSON.stringify([...select.selectedOptions].map(option=>option.value));select.addEventListener('change',sync);sync()});
  form.addEventListener('input',()=>{applyConditions();renderCustomBlocks()});form.addEventListener('change',()=>{applyConditions();renderCustomBlocks()});applyConditions();renderCustomBlocks();
  const formPages=[...document.querySelectorAll('[data-form-page]')];let activePage=0;function showPage(index){activePage=Math.max(0,Math.min(formPages.length-1,index));formPages.forEach((page,pageIndex)=>page.hidden=pageIndex!==activePage);const progress=document.querySelector('#form-progress');if(progress?.parentElement?.getAttribute('role')==='progressbar'){progress.style.width=((activePage+1)/formPages.length*100)+'%';progress.parentElement.setAttribute('aria-valuenow',String(activePage+1))}else if(progress){progress.textContent='Step '+(activePage+1)+' of '+formPages.length}window.scrollTo({top:0,behavior:'smooth'})}form.querySelectorAll('[data-page-next]').forEach(button=>button.addEventListener('click',()=>{const current=formPages[activePage],invalid=[...current.querySelectorAll('input,select,textarea')].find(control=>!control.disabled&&!control.checkValidity());if(invalid){invalid.reportValidity();return}showPage(activePage+1)}));form.querySelectorAll('[data-page-previous]').forEach(button=>button.addEventListener('click',()=>showPage(activePage-1)));
  function contextFrom(payload,response){const ctx={...customDefaults,name:'',email:'',phone:'',submission_id:response.submission_id||'',transaction_id:response.transaction_id||'',payment_status:response.payment_status||'NOT_REQUIRED',payment_amount:response.amount||0,payment_method:response.payment_method||'',created_at:new Date().toISOString(),all_fields:JSON.stringify(payload.answers)};document.querySelectorAll('[data-field-id]').forEach(el=>{const key='field_'+String(el.dataset.fieldLabel||el.dataset.fieldId).toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'');const value=payload.answers[el.dataset.fieldId]||'';ctx[key]=value;if(el.dataset.fieldType==='NAME')ctx.name=value;if(el.dataset.fieldType==='EMAIL')ctx.email=value;if(el.dataset.fieldType==='PHONE')ctx.phone=value});return ctx}
  function complete(payload,response){const ctx=contextFrom(payload,response);if(redirectType==='CUSTOM_HTML'&&customTemplate){document.querySelector('main').innerHTML=customTemplate.replace(/{{\s*([a-zA-Z0-9_]+)\s*}}/g,(_,key)=>esc(ctx[key]));return}if(redirectType==='REDIRECT_URL'&&redirectUrl){setTimeout(()=>openInNewTab?window.open(redirectUrl,'_blank','noopener,noreferrer'):location.assign(redirectUrl),redirectDelay*1000);result.className='success';result.textContent='Response accepted. Redirecting…';return}result.className='success';result.textContent='Response submitted successfully.'}
  async function poll(payload,response){const deadline=Date.now()+15*60*1000;while(Date.now()<deadline){await new Promise(resolve=>setTimeout(resolve,4000));const statusUrl=new URL(location.href);statusUrl.searchParams.set('action','status');statusUrl.searchParams.set('order_id',response.order_id);const statusResponse=await fetch(statusUrl,{headers:{accept:'application/json'}});if(!statusResponse.ok)continue;const status=await statusResponse.json();if(status.status==='PAID'){response.payment_status='PAID';complete(payload,response);return}if(status.status==='EXPIRED'||status.status==='CANCELLED'){result.className='error';result.textContent='Payment '+status.status.toLowerCase()+'. Please submit again.';submit.disabled=false;return}}result.className='error';result.textContent='Payment confirmation timed out. Check your transaction from the merchant dashboard.';submit.disabled=false}
  let uploadInProgress=false;document.querySelectorAll('input[type="file"][data-field-id]').forEach(input=>input.addEventListener('change',()=>{delete input.dataset.uploaded;input.closest('[data-condition-field]')?.querySelector('[data-upload-answer-for="'+CSS.escape(input.dataset.fieldId)+'"]')?.remove()}));form.addEventListener('submit',async event=>{const pending=[...document.querySelectorAll('input[type="file"][data-field-id]')].filter(input=>!input.disabled&&input.files?.length&&!input.dataset.uploaded);if(!pending.length)return;event.preventDefault();event.stopImmediatePropagation();if(uploadInProgress)return;uploadInProgress=true;submit.disabled=true;result.className='';result.textContent='Uploading attachments securely...';try{for(const input of pending){const file=input.files[0],max=Number(input.dataset.maxFileBytes||5242880);if(file.size>max)throw new Error('Attachment exceeds the configured size limit');const uploadUrl=new URL(location.href);uploadUrl.searchParams.set('action','upload');const body=new FormData();body.set('request_id',pendingRequestId);body.set('field_id',input.dataset.fieldId);body.set('file',file);const response=await fetch(uploadUrl,{method:'POST',headers:{'idempotency-key':pendingRequestId},body});const data=await response.json();if(!response.ok)throw new Error(data.error||'Attachment upload failed');input.dataset.uploaded='true';const hidden=document.createElement('input');hidden.type='hidden';hidden.dataset.fieldId=input.dataset.fieldId;hidden.dataset.fieldLabel=input.dataset.fieldLabel;hidden.dataset.fieldType=input.dataset.fieldType;hidden.dataset.uploadAnswerFor=input.dataset.fieldId;hidden.value=JSON.stringify(data.file);input.closest('[data-condition-field]').append(hidden)}uploadInProgress=false;submit.disabled=false;result.textContent='';form.requestSubmit()}catch(error){uploadInProgress=false;submit.disabled=false;result.className='error';result.textContent=error?.message||'Attachment upload failed'}},true);
  if(!waitForPayment)poll=(payload,response)=>{result.className='payment';result.textContent='Your response is recorded. Send BDT '+response.amount+' to '+response.payment_method+' '+response.payment_number+'\nReference: '+response.transaction_id+'\nPayment will be confirmed in the merchant dashboard.'};
  if(timerSeconds>0)setTimeout(()=>{if(!submit.disabled){submit.disabled=true;result.className='error';result.textContent='This response session has expired. Reload the page to start again.'}},timerSeconds*1000);

  // Closing timeline countdown timer in standard form
  (function() {
    const deadlineEpoch = ${deadlineEpoch};
    const enableTimeline = ${enableClosing};
    if (enableTimeline && deadlineEpoch > 0) {
      function updateCountdown() {
        const now = Date.now();
        const diff = deadlineEpoch - now;
        const banner = document.getElementById('form-closing-timer-banner');
        const val = document.getElementById('countdown-val');
        if (diff <= 0) {
          if (val) val.innerText = "EXPIRED";
          if (banner) {
            banner.style.background = "#FEE2E2";
            banner.style.borderColor = "#EF4444";
            banner.style.color = "#991B1B";
            banner.innerHTML = "🔒 <strong>${closedMessage}</strong>";
          }
          if (submit) {
            submit.disabled = true;
            submit.style.opacity = '0.5';
            submit.style.cursor = 'not-allowed';
            submit.innerText = "Form Closed";
          }
        } else {
          const d = Math.floor(diff / (1000 * 60 * 60 * 24));
          const h = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
          const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
          const s = Math.floor((diff % (1000 * 60)) / 1000);
          if (val) val.innerText = (d > 0 ? d + "d " : "") + h + "h " + m + "m " + s + "s";
        }
      }
      setInterval(updateCountdown, 1000);
      updateCountdown();
    }
  })();

  // CSV backend dataset & autofill
  (function() {
    const csvBackendData = ${csvDataJson};
    const csvLookupCol = ${csvLookupCol};
    const csvTargetLookupId = ${csvTargetLookupId};
    const csvColumnMappings = ${csvMappings};

    if (csvBackendData.length > 0 && csvLookupCol && csvTargetLookupId) {
      const lookupInput = document.querySelector('[data-field-id="' + CSS.escape(csvTargetLookupId) + '"]');
      if (lookupInput) {
        let timer;
        lookupInput.addEventListener('input', () => {
          clearTimeout(timer);
          timer = setTimeout(() => {
            const query = (lookupInput.value || '').trim().toLowerCase();
            if (!query) {
              lookupInput.style.borderColor = '';
              return;
            }
            const matched = csvBackendData.find(row => String(row[csvLookupCol] || '').trim().toLowerCase() === query);
            if (matched) {
              Object.entries(csvColumnMappings).forEach(([colName, fieldId]) => {
                if (fieldId === csvTargetLookupId) return;
                const targetEl = document.querySelector('[data-field-id="' + CSS.escape(fieldId) + '"]');
                if (targetEl && matched[colName] !== undefined) {
                  targetEl.value = matched[colName];
                  targetEl.dispatchEvent(new Event('input', { bubbles: true }));
                  targetEl.dispatchEvent(new Event('change', { bubbles: true }));
                }
              });
              lookupInput.style.borderColor = '#10B981';
            } else {
              lookupInput.style.borderColor = '';
            }
          }, 250);
        });
      }
    }
  })();

  form.addEventListener('submit',event=>{if(!form.checkValidity()){event.preventDefault();event.stopImmediatePropagation();form.reportValidity()}},true);
  let pendingRequestId=crypto.randomUUID();form.addEventListener('submit',async event=>{event.preventDefault();submit.disabled=true;result.className='';result.textContent='Submitting securely…';const values=new FormData(form),answers={};document.querySelectorAll('[data-field-id]').forEach(el=>{if(el.type==='checkbox'){answers[el.dataset.fieldId]=el.checked?'true':'false'}else if(el.type==='radio'){if(el.checked)answers[el.dataset.fieldId]=el.value}else{answers[el.dataset.fieldId]=el.value}});const requestId=pendingRequestId;const payload={request_id:requestId,answers,product_id:values.get('product_id'),quantity:values.get('quantity'),payment_method:values.get('payment_method')};try{const response=await fetch(location.href,{method:'POST',headers:{'content-type':'application/json','idempotency-key':requestId},body:JSON.stringify(payload)});const data=await response.json();if(!response.ok){const fieldMessage=data.fields&&typeof data.fields==='object'?Object.values(data.fields)[0]:'';result.className='error';result.textContent=fieldMessage||data.error||'Submission failed';submit.disabled=false;return}pendingRequestId=crypto.randomUUID();document.querySelectorAll('input[type="file"][data-field-id]').forEach(input=>delete input.dataset.uploaded);document.querySelectorAll('[data-upload-answer-for]').forEach(input=>input.remove());if(!data.payment_required){complete(payload,data);return}result.className='payment';result.textContent='Send BDT '+data.amount+' to '+data.payment_method+' '+data.payment_number+'\nReference: '+data.transaction_id+'\nWaiting for payment confirmation…';poll(payload,data)}catch(_){result.className='error';result.textContent='Network error. Please retry; the idempotency key prevents duplicates.';submit.disabled=false}});
  </script></body></html>`;
}

function renderConditionalField(field: FormField, enforceRequired: boolean, requiredIndicator: boolean, theme: Json): string {
  const content = renderField(field, enforceRequired, requiredIndicator, theme);
  if (!content) return "";
  return `<div data-condition-field="${escapeHtml(String(field.id || ""))}" data-depends-on="${escapeHtml(String(field.depends_on_field_id || ""))}" data-condition-operator="${escapeHtml(String(field.condition_operator || "EQUALS").toUpperCase())}" data-condition-value="${escapeHtml(String(field.condition_value || ""))}">${content}</div>`;
}

function renderField(field: FormField, enforceRequired: boolean, requiredIndicator: boolean, theme: Json): string {
  const id = String(field.id || ""); if (!id || !/^[a-zA-Z0-9_-]{1,100}$/.test(id)) return "";
  const type = String(field.type || "NAME").toUpperCase();
  if (["PRODUCT", "PRODUCT_LIST", "DISCOUNT", "SHIPPING", "TAX", "TIP", "CURRENCY"].includes(type)) return "";
  if (["IMAGE", "MEDIA_IMAGE", "VIDEO", "MEDIA_VIDEO", "PDF", "MEDIA_PDF"].includes(type)) { const mediaUrl = safeRedirectUrl(String(field.media_url || field.mediaUrl || "")); return mediaUrl ? `<p><a href="${escapeHtml(mediaUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(String(field.label || "View media"))}</a></p>` : ""; }
  if (type === "CUSTOM_CODE") { const css = sanitizeCss(String(field.custom_code_css || "")), html = sanitizeCustomHtml(String(field.custom_code_html || "")); return `${css ? `<style>${css}</style>` : ""}<section class="code-block" data-code-template="${escapeHtml(html)}"></section>`; }
  const label = escapeHtml(String(field.label || "Field")), placeholder = escapeHtml(String(field.placeholder || ""));
  const shownLabel = `${label}${requiredIndicator && field.required === true ? " *" : ""}`;
  const required = enforceRequired && field.required === true ? " required" : "", data = `data-field-id="${escapeHtml(id)}" data-field-label="${label}" data-field-type="${type}"`;
  const helper = String(field.helper_text || "").trim() ? `<small class="helper">${escapeHtml(String(field.helper_text))}</small>` : "";
  const defaultValue = escapeHtml(String(field.default_value || ""));
  const minLength = Math.min(10_000, Math.max(0, Number(field.min_length || 0)));
  const maxLength = Math.min(10_000, Math.max(0, Number(field.max_length || 0)));
  const lengthRules = `${minLength > 0 ? ` minlength="${minLength}"` : ""}${maxLength > 0 ? ` maxlength="${maxLength}"` : ""}`;
  if (["FILE_UPLOAD", "CAMERA_UPLOAD"].includes(type)) {
    const extensions = Array.isArray(field.allowed_file_extensions) ? field.allowed_file_extensions.map((item) => `.${String(item).toLowerCase().replace(/[^a-z0-9]/g, "")}`).filter((item) => item.length > 1) : [];
    const configuredLimit = Number(field.max_file_size_bytes ?? 5_242_880);
    const maxBytes = theme.enforce_file_size_limit === false ? 10_485_760 : Math.min(10_485_760, Math.max(1_024, Number.isFinite(configuredLimit) ? configuredLimit : 5_242_880));
    return `<label for="${id}">${shownLabel}</label><input id="${id}" type="file" ${data}${required}${extensions.length ? ` accept="${escapeHtml(extensions.join(","))}"` : ""} data-max-file-bytes="${maxBytes}">${helper}`;
  }
  if (["DROPDOWN", "MULTI_SELECT"].includes(type)) { const options = Array.isArray(field.options) ? field.options : []; const multiple = type === "MULTI_SELECT"; return `<label for="${id}">${shownLabel}</label><select id="${id}" ${data}${required}${multiple ? " multiple" : ""}>${multiple ? "" : '<option value="">Select an option</option>'}${options.map((option) => `<option value="${escapeHtml(String(option))}"${String(option) === String(field.default_value || "") ? " selected" : ""}>${escapeHtml(String(option))}</option>`).join("")}</select>${helper}`; }
  if (type === "RADIO") { const options = Array.isArray(field.options) ? field.options : []; return `<fieldset><legend class="label">${shownLabel}</legend>${options.map((option, index) => `<label class="choice"><input type="radio" name="field_${id}" value="${escapeHtml(String(option))}" ${data}${required && index === 0 ? " required" : ""}${String(option) === String(field.default_value || "") ? " checked" : ""}>${escapeHtml(String(option))}</label>`).join("")}${helper}</fieldset>`; }
  if (["CHECKBOX", "TOGGLE"].includes(type)) return `<label class="choice"><input id="${id}" type="checkbox" ${data}${required}${String(field.default_value || "").toLowerCase() === "true" ? " checked" : ""}>${shownLabel}</label>${helper}`;
  if (["ADDRESS", "NOTES"].includes(type)) return `<label for="${id}">${shownLabel}</label><textarea id="${id}" placeholder="${placeholder}" ${data}${required}${lengthRules}>${defaultValue}</textarea>${helper}`;
  const inputType = type === "DATE" ? "date" : type === "EMAIL" ? "email" : type === "PHONE" ? "tel" : ["CUSTOM_AMOUNT", "QUANTITY"].includes(type) ? "number" : type === "WEBSITE" ? "url" : "text";
  let min = field.min_value === null || field.min_value === undefined ? NaN : Number(field.min_value);
  let max = field.max_value === null || field.max_value === undefined ? NaN : Number(field.max_value);
  if (type === "QUANTITY") {
    const globalMin = theme.enforce_quantity_range === false ? 1 : Math.max(1, Number(theme.min_quantity || 1));
    const globalMax = theme.enforce_quantity_range === false ? 1000 : Math.max(globalMin, Number(theme.max_quantity || 1000));
    min = theme.enforce_quantity_range === false || !Number.isFinite(min) ? globalMin : Math.max(globalMin, min);
    max = theme.enforce_quantity_range === false || !Number.isFinite(max) ? globalMax : Math.min(globalMax, max);
  }
  const numeric = inputType === "number" ? `${Number.isFinite(min) ? ` min="${min}"` : ""}${Number.isFinite(max) ? ` max="${max}"` : ""} step="${type === "QUANTITY" ? "1" : "0.01"}"` : "";
  return `<label for="${id}">${shownLabel}</label><input id="${id}" type="${inputType}" value="${defaultValue}" placeholder="${placeholder}" ${data}${required}${lengthRules}${numeric}>${helper}`;
}

function validateAnswers(fields: FormField[], input: Json, theme: Json, formId: string, requestId: string): { ok: boolean; answers: Json; errors: Json } {
  const answers: Json = {}, errors: Json = {};
  for (const field of fields.slice(0, 200)) {
    const id = String(field.id || ""), type = String(field.type || "").toUpperCase();
    if (!id || type === "CUSTOM_CODE" || type.startsWith("MEDIA_") || ["IMAGE", "VIDEO", "PDF", "PRODUCT", "PRODUCT_LIST", "DISCOUNT", "SHIPPING", "TAX", "TIP", "CURRENCY"].includes(type)) continue;
    if (!conditionMatches(field, input)) continue;
    if (["FILE_UPLOAD", "CAMERA_UPLOAD"].includes(type)) {
      const raw = input[id];
      const metadata = typeof raw === "string" ? parseObject(raw) : asObject(raw);
      const objectPath = String(metadata.object_path || "");
      const required = theme.enforce_required_fields !== false && field.required === true;
      if (!objectPath) {
        if (required) errors[id] = `${String(field.label || "Attachment")} is required`;
        continue;
      }
      const expectedPrefix = `${formId}/${requestId}/${id}-`;
      if (!objectPath.startsWith(expectedPrefix) || objectPath.length > 600) {
        errors[id] = "Invalid attachment reference";
        continue;
      }
      answers[id] = {
        object_path: objectPath,
        file_name: String(metadata.file_name || "").slice(0, 255),
        size: Math.max(0, Number(metadata.size || 0)),
        mime_type: String(metadata.mime_type || "application/octet-stream").slice(0, 150),
      };
      continue;
    }
    if (type === "MULTI_SELECT") {
      const values = Array.isArray(input[id]) ? (input[id] as unknown[]).map(String) : parseArray(String(input[id] || ""));
      const options = Array.isArray(field.options) ? field.options.map(String) : [];
      const selected = values.map((item) => item.trim()).filter(Boolean).slice(0, 100);
      if (theme.enforce_required_fields !== false && field.required === true && selected.length === 0) errors[id] = `${String(field.label || "Field")} is required`;
      else if (selected.some((item) => !options.includes(item))) errors[id] = "Select valid options";
      else answers[id] = selected;
      continue;
    }
    const value = String(input[id] ?? "").trim().slice(0, 10_000); answers[id] = value;
    const required = theme.enforce_required_fields !== false && field.required === true;
    if (required && (!value || (type === "CHECKBOX" && value !== "true"))) errors[id] = `${String(field.label || "Field")} is required`;
    if (!value) continue;
    if (theme.strict_format_validation !== false && type === "EMAIL" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) errors[id] = "Enter a valid email address";
    if (theme.strict_format_validation !== false && type === "PHONE" && !/^\+?[0-9][0-9 -]{8,18}[0-9]$/.test(value)) errors[id] = "Enter a valid phone number";
    const validationPattern = String(field.validation_regex || "");
    if (validationPattern) {
      const validationRegex = compileSafeValidationRegex(validationPattern);
      if (!validationRegex) errors[id] = "This field has an invalid validation configuration";
      else if (!validationRegex.test(value)) errors[id] = String(field.custom_error_message || `Invalid value for ${String(field.label || "field")}`).slice(0, 300);
    }
    if (["CUSTOM_AMOUNT", "QUANTITY"].includes(type)) {
      const number = Number(value);
      const min = field.min_value === null || field.min_value === undefined ? NaN : Number(field.min_value);
      const max = field.max_value === null || field.max_value === undefined ? NaN : Number(field.max_value);
      if (!Number.isFinite(number) || (type === "QUANTITY" && !Number.isInteger(number))) errors[id] = "Enter a valid number";
      else if ((type !== "QUANTITY" || theme.enforce_quantity_range !== false) && Number.isFinite(min) && number < min) errors[id] = `Minimum value is ${min}`;
      else if ((type !== "QUANTITY" || theme.enforce_quantity_range !== false) && Number.isFinite(max) && number > max) errors[id] = `Maximum value is ${max}`;
    }
    const options = Array.isArray(field.options) ? field.options.map(String) : [];
    if (["DROPDOWN", "RADIO"].includes(type) && options.length > 0 && !options.includes(value)) errors[id] = "Select a valid option";
    const minLength = Number(field.min_length || 0), maxLength = Number(field.max_length || 0);
    if (minLength > 0 && value.length < minLength) errors[id] = `Minimum length is ${minLength}`;
    if (maxLength > 0 && value.length > maxLength) errors[id] = `Maximum length is ${maxLength}`;
  }
  return { ok: Object.keys(errors).length === 0, answers, errors };
}

function findAnswer(fields: FormField[], answers: Json, type: string): string { const field = fields.find((item) => String(item.type || "").toUpperCase() === type); return field ? String(answers[String(field.id)] || "") : ""; }
function conditionMatches(field: FormField, answers: Json): boolean { const dependsOn = String(field.depends_on_field_id || ""); if (!dependsOn) return true; const actual = String(answers[dependsOn] ?? ""), expected = String(field.condition_value ?? ""); switch (String(field.condition_operator || "EQUALS").toUpperCase()) { case "NOT_EQUALS": return actual !== expected; case "CONTAINS": return actual.includes(expected); default: return actual === expected; } }
function shuffle<T>(items: T[]): T[] { const copy = [...items]; for (let index = copy.length - 1; index > 0; index--) { const random = new Uint32Array(1); crypto.getRandomValues(random); const target = random[0] % (index + 1); [copy[index], copy[target]] = [copy[target], copy[index]]; } return copy; }
function renderClosedForm(title: string, message: string = "This form is no longer accepting responses."): string {
  const safeTitle = escapeHtml(title);
  const safeMessage = escapeHtml(message);
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${safeTitle} — Closed</title><style>body{font-family:Inter,system-ui,-apple-system,sans-serif;background:#F6F7FB;margin:0;padding:24px;display:flex;align-items:center;justify-content:center;min-height:90vh;color:#1E293B}.card{background:#fff;border-radius:20px;padding:36px 32px;max-width:520px;width:100%;box-shadow:0 12px 40px rgba(0,0,0,0.06);text-align:center}.icon{font-size:48px;margin-bottom:16px}h1{font-size:1.5rem;margin:0 0 12px 0;color:#0F172A}p{font-size:1rem;color:#64748B;line-height:1.6;margin:0}</style></head><body><main class="card"><div class="icon">🔒</div><h1>${safeTitle}</h1><p>${safeMessage}</p></main></body></html>`;
}
function asObject(value: unknown): Json { return value && typeof value === "object" && !Array.isArray(value) ? value as Json : {}; }
function parseObject(value: string): Json { try { return asObject(JSON.parse(value)); } catch { return {}; } }
function parseArray(value: string): string[] { try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.map(String) : []; } catch { return []; } }
function compileSafeValidationRegex(pattern: string): RegExp | null {
  if (!pattern || pattern.length > 256 || /\\[1-9]|\(\?(?!:)|\([^)]*[+*][^)]*\)[+*{]/.test(pattern)) return null;
  try { return new RegExp(`^(?:${pattern})$`, "u"); } catch { return null; }
}
function clampInteger(value: unknown, min: number, max: number): number { const number = Math.trunc(Number(value || min)); return Math.min(max, Math.max(min, Number.isFinite(number) ? number : min)); }
function normalizePaymentMethod(value: string): string { const match = ["bKash", "Nagad", "Rocket", "Upay"].find((method) => method.toUpperCase() === value.toUpperCase()); return match || "AUTO"; }
function safeColor(value: unknown, fallback: string): string { const text = String(value || ""); return /^#[0-9a-fA-F]{6}$/.test(text) ? text : fallback; }
function safeRedirectUrl(value: string): string { try { const url = new URL(value); return url.protocol === "https:" && !url.username && !url.password ? url.toString() : ""; } catch { return ""; } }
function sanitizeCss(value: string): string { return value.slice(0, 100_000).replace(/<\/style/gi, "<\\/style").replace(/@import/gi, "/* import blocked */").replace(/url\s*\(\s*['\"]?javascript:/gi, "url(blocked:"); }
function sanitizeCustomHtml(value: string): string { return value.slice(0, 200_000).replace(/<\s*(script|iframe|object|embed|base|meta|link)[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, "").replace(/<\s*(script|iframe|object|embed|base|meta|link)\b[^>]*\/?>/gi, "").replace(/\son[a-z]+\s*=\s*(['\"])[\s\S]*?\1/gi, "").replace(/\s(href|src)\s*=\s*(['\"])\s*javascript:[\s\S]*?\2/gi, ""); }
function escapeHtml(value: string): string { return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]!)); }
function clientAddress(request: Request): string { return (request.headers.get("x-forwarded-for") || request.headers.get("cf-connecting-ip") || "unknown").split(",")[0].trim(); }
async function sha256(value: string): Promise<string> { const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)); return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join(""); }
async function hmacSha256(secret: string, data: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, enc.encode(data));
  return Array.from(new Uint8Array(signature)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function randomToken(): string { return crypto.randomUUID().replaceAll("-", ""); }
function isUuid(value: string): boolean { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function htmlHeaders(nonce: string): HeadersInit { return { ...baseHeaders, "Content-Type": "text/html; charset=utf-8", "Content-Security-Policy": `default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}'; connect-src 'self'; img-src https: data:; form-action 'self'; base-uri 'none'; frame-ancestors https:` }; }
function json(body: Json, status: number, extra: HeadersInit = {}): Response { return new Response(JSON.stringify(body), { status, headers: { ...baseHeaders, ...extra, "Content-Type": "application/json; charset=utf-8" } }); }
