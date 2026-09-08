import React, { useEffect, useState } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';

export interface FaqItem {
  question: string;
  answer: string;
}

export interface GuideItem {
  title: string;
  description: string;
}

export interface ArticleItem {
  id: string;
  title: string;
  category: string;
  content: string;
}

export interface VideoTutorial {
  id?: string;
  title: string;
  description: string;
  videoUrl: string;
  duration: string;
  thumbnailUrl?: string;
  category?: string;
}

export interface SystemRemoteConfig {
  developer_portal_url: string;
  developer_docs_url: string;
  api_portal_url: string;
  webhook_docs_url: string;
  support_hotline: string;
  support_email: string;
  support_whatsapp: string;
  support_address: string;
  support_hours: string;
  system_notice: string;
  video_tutorial: VideoTutorial;
  video_tutorials: VideoTutorial[];
  api_documentation: string;
  faqs: FaqItem[];
  guides: GuideItem[];
  articles: ArticleItem[];
  ticket_categories: string[];
  last_updated?: number;
}

export const DEFAULT_API_DOCS_MARKDOWN = `# SwapnoPay Developer API & Webhook Specification (v2.0 Production)

Welcome to the SwapnoPay Official API Documentation. SwapnoPay is an enterprise-grade payment aggregation and automated SMS reconciliation gateway supporting bKash, Nagad, Rocket, and Upay across Personal, Merchant, and Agent numbers.

---

## 1. Base URLs & Environments

- **Production Gateway**: \`https://pay.swapnopay.top\`
- **Supabase Edge Functions**: \`https://<your-project>.supabase.co/functions/v1\`
- **WebSocket Gateway**: \`wss://pay.swapnopay.top\`
- **Sandbox Testing**: Enable Test Mode in your merchant dashboard to simulate carrier SMS triggers.

---

## 2. Authentication & Headers

All requests to the SwapnoPay API must include either your API Secret Key or a valid Supabase JWT Bearer token:

| Header Name | Type | Description |
|---|---|---|
| \`X-Admin-Secret\` | string | Your platform or merchant API secret key. |
| \`Authorization\` | string | \`Bearer <JWT_TOKEN>\` for authenticated merchant sessions. |
| \`apikey\` | string | Supabase anon/publishable key for client-side queries. |
| \`Idempotency-Key\` | string (UUID) | Unique request token to prevent double-charging or duplicate order creation. |
| \`Content-Type\` | string | Must be \`application/json\`. |

---

## 3. Core API Endpoints

### 3.1 Create Payment Order
Create a new checkout session and obtain a hosted payment URL.

- **Method**: \`POST\`
- **Endpoint**: \`/v1/payment/create\`
- **Edge Function Alternative**: \`POST /functions/v1/create-order\`

#### Request Body:
\`\`\`json
{
  "order_id": "ORD-2026-9812",
  "amount": 1250.00,
  "currency": "BDT",
  "customer_name": "Tanvir Hasan",
  "customer_email": "tanvir@example.com",
  "customer_phone": "01712963652",
  "payment_method": "bKash",
  "redirect_url": "https://merchant.example.com/checkout/success",
  "cancel_url": "https://merchant.example.com/checkout/cancel",
  "webhook_url": "https://merchant.example.com/api/webhooks/swapnopay"
}
\`\`\`

#### Response (200 OK):
\`\`\`json
{
  "status": "SUCCESS",
  "code": 200,
  "message": "Payment session initialized successfully",
  "data": {
    "order_id": "ORD-2026-9812",
    "payment_url": "https://pay.swapnopay.top/pay/ORD-2026-9812",
    "assigned_gateway_number": "01784992118",
    "gateway_type": "bKash Personal",
    "payable_amount": 1250.00,
    "expires_at": "2026-09-07T21:15:00Z"
  }
}
\`\`\`

---

### 3.2 Verify Payment & SMS Match
Verify incoming carrier transaction details against pending orders.

- **Method**: \`POST\`
- **Endpoint**: \`/v1/payment/verify\`

#### Request Body:
\`\`\`json
{
  "order_id": "ORD-2026-9812",
  "tran_id": "9H8B7G6F5E",
  "sender_phone": "01712963652",
  "amount": 1250.00,
  "payment_method": "bKash"
}
\`\`\`

#### Response (200 OK):
\`\`\`json
{
  "status": "PAID",
  "order_id": "ORD-2026-9812",
  "trx_id": "9H8B7G6F5E",
  "verified": true,
  "matched_at": "2026-09-07T20:16:30Z"
}
\`\`\`

---

### 3.3 Query Order Status
Poll or inspect live order settlement status.

- **Method**: \`GET\`
- **Endpoint**: \`/v1/payment/status/{orderId}\`

---

### 3.4 Hosted Form Dynamic Submission
Submit custom dynamic fields and uploaded proof attachments.

- **Method**: \`POST\`
- **Endpoint**: \`/v1/hosted-form/submit\`

---

## 4. Webhooks & HMAC Signature Security

SwapnoPay sends instant JSON HTTP POST notifications whenever an order changes state.

### 4.1 Signature Header
Every webhook request contains an HMAC SHA-256 signature:
\`\`\`http
X-Signature: sha256=4f6a9e1029c8b3...
\`\`\`

### 4.2 Webhook Event: \`payment.paid\`
\`\`\`json
{
  "event": "payment.paid",
  "timestamp": "2026-09-07T20:16:30Z",
  "data": {
    "order_id": "ORD-2026-9812",
    "status": "PAID",
    "amount": 1250.00,
    "currency": "BDT",
    "payment_method": "bKash",
    "trx_id": "9H8B7G6F5E",
    "sender_phone": "01712963652"
  }
}
\`\`\`

### 4.3 HMAC Verification Examples

#### Node.js / Express:
\`\`\`javascript
const crypto = require('crypto');

function verifySwapnoPayWebhook(rawBody, signatureHeader, secret) {
  const expected = 'sha256=' + crypto.createHmac('sha256', secret).update(rawBody, 'utf8').digest('hex');
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signatureHeader));
}
\`\`\`

#### Python / Flask / FastAPI:
\`\`\`python
import hmac, hashlib

def verify_swapnopay_signature(raw_body: bytes, signature_header: str, secret: str) -> bool:
    expected = "sha256=" + hmac.new(secret.encode('utf-8'), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header)
\`\`\`

#### PHP:
\`\`\`php
function verifySwapnoPayWebhook($rawBody, $signatureHeader, $secret) {
    $expected = 'sha256=' . hash_hmac('sha256', $rawBody, $secret);
    return hash_equals($expected, $signatureHeader);
}
\`\`\`

---

## 5. Error Codes

| Error Code | HTTP Status | Description |
|---|---|---|
| \`ERR_INVALID_HMAC\` | 401 | Webhook signature verification failed. Verify your secret. |
| \`ERR_ORDER_EXPIRED\` | 400 | Payment window expired (default 10 min). |
| \`ERR_DUPLICATE_IDEMPOTENCY\` | 409 | Request with this Idempotency-Key already processed. |
| \`ERR_INSUFFICIENT_AMOUNT\` | 422 | Paid amount less than order invoice. |
| \`ERR_GATEWAY_OFFLINE\` | 503 | No Android receiver device online for requested number. |
`;

const DEFAULT_CONFIG: SystemRemoteConfig = {
  developer_portal_url: "https://developer.swapnopay.app",
  developer_docs_url: "https://docs.swapnopay.app/api",
  api_portal_url: "https://developer.swapnopay.app/keys",
  webhook_docs_url: "https://docs.swapnopay.app/webhooks",
  support_hotline: "+880 1794 827103",
  support_email: "support@swapnopay.io",
  support_whatsapp: "+8801712963652",
  support_address: "Level 14, Banani Tower, Dhaka, Bangladesh",
  support_hours: "24/7 Chat & Ticket Support (9 AM - 11 PM Live Hotline)",
  system_notice: "Welcome to SwapnoPay! Automatic SMS matching and merchant ledger active.",
  video_tutorial: {
    id: "vid_bkash",
    title: "bKash Automatic SMS Matching",
    description: "Setup automated order matching with personal & merchant SIM.",
    videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    duration: "5:24 min",
    thumbnailUrl: "",
    category: "Automation"
  },
  video_tutorials: [
    {
      id: "vid_bkash",
      title: "bKash Automatic SMS Matching",
      description: "Setup automated order matching with personal & merchant SIM.",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      duration: "5:24 min",
      category: "Automation"
    },
    {
      id: "vid_sms_app",
      title: "SMS Gateway Background Service",
      description: "Configure battery optimization, background service & permissions.",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      duration: "3:15 min",
      category: "Setup"
    },
    {
      id: "vid_woo",
      title: "WooCommerce & Webhooks Setup",
      description: "Install SwapnoPay WordPress plugin and configure instant IPN webhooks.",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      duration: "4:50 min",
      category: "Integration"
    },
    {
      id: "vid_postgres",
      title: "Supabase Database & API Keys",
      description: "Manage API secret keys, RLS security policies, and edge functions.",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      duration: "6:10 min",
      category: "Security"
    }
  ],
  api_documentation: "",
  faqs: [
    { question: "Do I need a merchant account?", answer: "No, SmartPay fully supports Personal, Agent, and Merchant accounts for bKash, Nagad, and Rocket." },
    { question: "How fast does automatic matching take?", answer: "Typically 1 to 3 seconds after the mobile operator SMS is received on your Android device." },
    { question: "Can I use multiple SIM cards?", answer: "Yes! Dual-SIM Android devices are supported with simultaneous multi-gateway routing." },
    { question: "What if a customer pays the wrong amount?", answer: "The transaction is flagged as \"Unmatched\" in your ledger for quick 1-tap manual review and appeal resolution." }
  ],
  guides: [
    { title: "1. App Listener Configuration", description: "Install the app on the gateway Android phone and grant only the Receive SMS and notification permissions requested by the app." },
    { title: "2. Linking Supabase Backend", description: "From Supabase Project Settings, copy the Project URL and client-safe publishable/anon key into the Setup page. Keep privileged keys in Edge Function secrets only." },
    { title: "3. Testing Auto Matches", description: "Simulate or send a small transaction (e.g. 10 BDT Send Money). Verify the status updates in real-time under Dashboard logs." }
  ],
  articles: [
    {
      id: "matching",
      title: "How automatic payment matching works",
      category: "Automation",
      content: "SmartPay uses automatic SMS pattern recognition to match incoming mobile payments (bKash, Nagad, Rocket) with merchant orders in real-time.\n\n1. When a customer initiates a payment on your site, an order is created with a unique amount and payment reference.\n2. Once payment is completed, your Android device receives the official gateway SMS.\n3. The SmartPay background processor parses the transaction ID, sender's phone, and exact amount from the SMS.\n4. If all parameters match, the order is instantly marked as PAID and webhook notifications are triggered."
    },
    {
      id: "number",
      title: "How to add a new payment number",
      category: "Gateways",
      content: "To add a new merchant or personal account number to your gateway selection:\n\n1. Go to the 'Setup' tab from the bottom navigation.\n2. Select 'Payment Gateways' and tap on 'Add New Account' (+).\n3. Enter the account number, select the provider (bKash, Nagad, Rocket), and choose the account type (Merchant, Personal, Agent).\n4. Verify the connection by sending a test transaction or verifying the active status indicator."
    },
    {
      id: "appeal",
      title: "How to resolve an appeal",
      category: "Disputes",
      content: "When a customer submits an appeal due to an unmatched payment, follow these simple steps:\n\n1. Navigate to the Appeals screen or click the 'Appeals' alert card on your dashboard.\n2. Review the proof of payment submitted by the user (e.g. Transaction ID, screenshot).\n3. Search for the corresponding SMS in the 'SMS Logs' or select from the 'Unmatched Transactions' list.\n4. Tap 'Resolve and Match' to instantly credit the user's order and resolve the appeal."
    },
    {
      id: "unmatched",
      title: "Why is my payment not matched?",
      category: "Troubleshooting",
      content: "Payments may fail to match automatically for several common reasons:\n\n• Incorrect Amount: The customer paid a different amount than the order invoice.\n• Delay in SMS: The mobile operator SMS was delayed or not received by the local app.\n• Typos in Reference: The customer did not provide the correct transaction reference if using personal send money.\n• Permission Issues: The background listener app lacks permission to read incoming SMS. Ensure 'SMS Permission' is fully granted in settings."
    }
  ],
  ticket_categories: [
    "Payment Matching", "Gateway Setup", "API & Webhooks", "Billing & Plan", "Account & Verification", "Bug Report", "Fraud & Appeal", "Feature Request"
  ]
};

export default function SystemSettings() {
  const [config, setConfig] = useState<SystemRemoteConfig>(DEFAULT_CONFIG);
  const [activeTab, setActiveTab] = useState<'links' | 'api_docs' | 'support_contacts' | 'video' | 'faqs' | 'guides' | 'articles' | 'tickets'>('links');
  const [loading, setLoading] = useState(true);
  const [statusMsg, setStatusMsg] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await adminSupabase
          .from('showcase_config')
          .select('value')
          .eq('key', 'system_config')
          .single();
        if (data?.value) {
          const val = data.value;
          setConfig({
            ...DEFAULT_CONFIG,
            ...val,
            video_tutorial: { ...DEFAULT_CONFIG.video_tutorial, ...(val.video_tutorial || {}) },
            video_tutorials: Array.isArray(val.video_tutorials) && val.video_tutorials.length > 0 ? val.video_tutorials : DEFAULT_CONFIG.video_tutorials,
            api_documentation: typeof val.api_documentation === 'string' ? val.api_documentation : DEFAULT_CONFIG.api_documentation,
            faqs: Array.isArray(val.faqs) ? val.faqs : DEFAULT_CONFIG.faqs,
            guides: Array.isArray(val.guides) ? val.guides : DEFAULT_CONFIG.guides,
            articles: Array.isArray(val.articles) ? val.articles : DEFAULT_CONFIG.articles,
            ticket_categories: Array.isArray(val.ticket_categories) ? val.ticket_categories : DEFAULT_CONFIG.ticket_categories
          });
        }
      } catch (e) {
        console.warn('Supabase showcase_config read warning:', e);
      } finally {
        setLoading(false);
      }
    })();

    const channel = adminSupabase
      .channel('showcase_system_config')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'showcase_config', filter: 'key=eq.system_config' },
        (payload: any) => {
          if (payload.new?.value) {
            const val = payload.new.value;
            setConfig(prev => ({ ...prev, ...val }));
          }
        })
      .subscribe();

    return () => { adminSupabase.removeChannel(channel); };
  }, []);

  const handleSave = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsSaving(true);
    setStatusMsg('Saving system configuration to Supabase...');

    try {
      const payload: SystemRemoteConfig = {
        ...config,
        last_updated: Date.now()
      };

      const { error } = await adminSupabase
        .from('showcase_config')
        .upsert({ key: 'system_config', value: payload, updated_at: new Date().toISOString() });

      if (error) throw error;

      setStatusMsg('✅ Successfully saved and broadcasted via Supabase Realtime!');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('❌ Failed to save: ' + err.message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddVideoTutorial = () => {
    const newVid: VideoTutorial = {
      id: "vid_" + Date.now(),
      title: "New Video Tutorial #" + ((config.video_tutorials || []).length + 1),
      description: "Step-by-step instructions for integration.",
      videoUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      duration: "3:00 min",
      category: "General"
    };
    const updated = [...(config.video_tutorials || []), newVid];
    setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
  };

  const handleRemoveVideoTutorial = (index: number) => {
    const updated = (config.video_tutorials || []).filter((_, i) => i !== index);
    setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
  };

  const handleAddFaq = () => {
    setConfig({
      ...config,
      faqs: [...config.faqs, { question: 'New FAQ Question?', answer: 'Answer description here...' }]
    });
  };

  const handleRemoveFaq = (index: number) => {
    setConfig({
      ...config,
      faqs: config.faqs.filter((_, i) => i !== index)
    });
  };

  const handleAddGuide = () => {
    setConfig({
      ...config,
      guides: [...config.guides, { title: (config.guides.length + 1) + '. Guide Title', description: 'Step instructions here...' }]
    });
  };

  const handleRemoveGuide = (index: number) => {
    setConfig({
      ...config,
      guides: config.guides.filter((_, i) => i !== index)
    });
  };

  const handleAddArticle = () => {
    const newId = 'art_' + Date.now();
    setConfig({
      ...config,
      articles: [...config.articles, { id: newId, title: 'New Support Guide', category: 'General', content: 'Detailed guide content...' }]
    });
  };

  const handleRemoveArticle = (index: number) => {
    setConfig({
      ...config,
      articles: config.articles.filter((_, i) => i !== index)
    });
  };

  const handleAddCategory = () => {
    const name = prompt('Enter new ticket / report category name:');
    if (name && name.trim()) {
      setConfig({
        ...config,
        ticket_categories: [...config.ticket_categories, name.trim()]
      });
    }
  };

  const handleRemoveCategory = (index: number) => {
    setConfig({
      ...config,
      ticket_categories: config.ticket_categories.filter((_, i) => i !== index)
    });
  };

  if (loading) {
    return <div className="container"><div className="card">Loading remote system & support configuration...</div></div>;
  }

  return (
    <div className="container">
      <div className="header">
        <div>
          <h1>Admin Control Panel & CMS</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Customize Developer Portal links, Help & Support Screen (Video Tutorials, FAQs, Guides, Articles, Report Categories) synced in real-time with Android app.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/support"><button className="button" style={{ background: '#4F46E5' }}>🎫 Helpdesk Inbox</button></Link>
          <Link to="/dashboard"><button className="button" style={{ background: '#64748B' }}>Dashboard</button></Link>
        </div>
      </div>

      {statusMsg && (
        <div style={{
          padding: '12px 16px',
          background: statusMsg.startsWith('✅') ? '#ECFDF5' : '#FEF2F2',
          border: '1px solid ' + (statusMsg.startsWith('✅') ? '#10B981' : '#EF4444'),
          borderRadius: 8,
          color: statusMsg.startsWith('✅') ? '#065F46' : '#991B1B',
          marginBottom: 16,
          fontWeight: 600
        }}>
          {statusMsg}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        {[
          { key: 'links', label: '💻 Developer Portal Links' },
          { key: 'api_docs', label: '📚 API Documentation CMS' },
          { key: 'video', label: '🎥 Video Tutorials (' + ((config.video_tutorials || []).length) + ')' },
          { key: 'support_contacts', label: '📞 Support Contacts' },
          { key: 'faqs', label: '❓ FAQs (' + config.faqs.length + ')' },
          { key: 'guides', label: '📖 Guides (' + config.guides.length + ')' },
          { key: 'articles', label: '📄 Help Articles (' + config.articles.length + ')' },
          { key: 'tickets', label: '🎫 Report Categories (' + config.ticket_categories.length + ')' }
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key as any)}
            className="button"
            style={{
              background: activeTab === tab.key ? '#4F46E5' : '#E2E8F0',
              color: activeTab === tab.key ? 'white' : '#1E293B',
              fontWeight: 'bold',
              padding: '8px 14px',
              fontSize: 12.5
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSave}>
        {activeTab === 'links' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0 }}>💻 Developer Portal & API Links Customization</h3>
            <p style={{ fontSize: 12, color: '#64748B' }}>
              These URLs are dynamically opened when merchants tap Developer Portal in the More screen or integration guides.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Developer Portal URL (More Screen Button) *
                </label>
                <input
                  className="input"
                  value={config.developer_portal_url}
                  onChange={(e) => setConfig({ ...config, developer_portal_url: e.target.value })}
                  placeholder="https://developer.swapnopay.app"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Developer API Docs URL *
                </label>
                <input
                  className="input"
                  value={config.developer_docs_url}
                  onChange={(e) => setConfig({ ...config, developer_docs_url: e.target.value })}
                  placeholder="https://docs.swapnopay.app/api"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  API Keys Management Portal URL
                </label>
                <input
                  className="input"
                  value={config.api_portal_url}
                  onChange={(e) => setConfig({ ...config, api_portal_url: e.target.value })}
                  placeholder="https://developer.swapnopay.app/keys"
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Webhooks Guide & Sandbox URL
                </label>
                <input
                  className="input"
                  value={config.webhook_docs_url}
                  onChange={(e) => setConfig({ ...config, webhook_docs_url: e.target.value })}
                  placeholder="https://docs.swapnopay.app/webhooks"
                />
              </div>
            </div>
          </div>
        )}

        {activeTab === 'support_contacts' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0 }}>📞 Help & Support Contact Details</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Hotline Phone Number (Direct Dial) *
                </label>
                <input
                  className="input"
                  value={config.support_hotline}
                  onChange={(e) => setConfig({ ...config, support_hotline: e.target.value })}
                  placeholder="+880 1794 827103"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Support Email Inquiries *
                </label>
                <input
                  className="input"
                  value={config.support_email}
                  onChange={(e) => setConfig({ ...config, support_email: e.target.value })}
                  placeholder="support@swapnopay.io"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  WhatsApp Support Link / Number *
                </label>
                <input
                  className="input"
                  value={config.support_whatsapp}
                  onChange={(e) => setConfig({ ...config, support_whatsapp: e.target.value })}
                  placeholder="+8801712963652"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Operating Hours
                </label>
                <input
                  className="input"
                  value={config.support_hours}
                  onChange={(e) => setConfig({ ...config, support_hours: e.target.value })}
                  placeholder="24/7 Chat & Ticket Support"
                />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Main Headquarters Address
                </label>
                <input
                  className="input"
                  value={config.support_address}
                  onChange={(e) => setConfig({ ...config, support_address: e.target.value })}
                  placeholder="Level 14, Banani Tower, Dhaka, Bangladesh"
                />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  System Broadcast Announcement
                </label>
                <textarea
                  className="input"
                  value={config.system_notice}
                  onChange={(e) => setConfig({ ...config, system_notice: e.target.value })}
                  placeholder="Broadcast message shown to all merchants..."
                  style={{ minHeight: 60, fontFamily: 'inherit' }}
                />
              </div>
            </div>
          </div>
        )}

        {activeTab === 'api_docs' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
              <div>
                <h3 style={{ margin: 0 }}>📚 Developer API Documentation (Markdown CMS)</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>
                  Edit the comprehensive API specification. Changes are immediately synced to both the Android App Developer Portal (One-Click Copy & Reference) and the Web Docs Portal.
                </span>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="button"
                  onClick={() => setConfig({ ...config, api_documentation: DEFAULT_API_DOCS_MARKDOWN })}
                  style={{
                    background: '#EEF2FF',
                    color: '#4F46E5',
                    border: '1px solid #C7D2FE',
                    padding: '6px 12px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 12,
                    fontWeight: 700
                  }}
                >
                  Load Exhaustive API Docs Template 📄
                </button>
                <button
                  type="button"
                  onClick={() => setConfig({ ...config, api_documentation: '' })}
                  style={{
                    background: '#F1F5F9',
                    color: '#64748B',
                    border: '1px solid #CBD5E1',
                    padding: '6px 12px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 12,
                    fontWeight: 600
                  }}
                >
                  Clear (Use System Default)
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 16, marginBottom: 10, fontSize: 12, color: '#64748B' }}>
              <span>Character Count: <strong>{(config.api_documentation || DEFAULT_API_DOCS_MARKDOWN).length}</strong></span>
              <span>Lines: <strong>{(config.api_documentation || DEFAULT_API_DOCS_MARKDOWN).split('\n').length}</strong></span>
              <span>Status: <strong style={{ color: config.api_documentation ? '#10B981' : '#64748B' }}>{config.api_documentation ? 'Custom CMS Override Active' : 'System Default Active'}</strong></span>
            </div>

            <textarea
              className="input"
              value={config.api_documentation}
              onChange={(e) => setConfig({ ...config, api_documentation: e.target.value })}
              placeholder="Leave empty to use built-in exhaustive API specification, or enter customized Markdown documentation here..."
              style={{
                minHeight: 450,
                fontFamily: 'Fira Code, monospace',
                fontSize: 12.5,
                lineHeight: 1.5,
                background: '#0F172A',
                color: '#38BDF8',
                border: '1px solid #334155'
              }}
            />
          </div>
        )}

        {activeTab === 'video' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <div>
                <h3 style={{ margin: 0 }}>🎥 Video Integration Tutorials Manager</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>
                  Manage video tutorials shown on the Android Developer Portal and Web Documentation. Supports YouTube URLs and MP4 direct streams.
                </span>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleAddVideoTutorial}
                style={{ background: '#10B981', display: 'flex', alignItems: 'center', gap: 6 }}
              >
                + Add Video Tutorial
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {(config.video_tutorials || []).map((vid: VideoTutorial, index: number) => (
                <div key={vid.id || index} style={{ padding: 16, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 10 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ width: 24, height: 24, borderRadius: '50%', background: '#4F46E5', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700 }}>
                        {index + 1}
                      </span>
                      <strong style={{ fontSize: 14, color: '#1E293B' }}>{vid.title || 'Untitled Tutorial'}</strong>
                      {vid.category && (
                        <span style={{ background: '#EEF2FF', color: '#4F46E5', padding: '2px 8px', borderRadius: 6, fontSize: 11, fontWeight: 700 }}>
                          {vid.category}
                        </span>
                      )}
                      {vid.duration && (
                        <span style={{ background: '#FEF3C7', color: '#B45309', padding: '2px 8px', borderRadius: 6, fontSize: 11, fontWeight: 700 }}>
                          ⏱ {vid.duration}
                        </span>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 8 }}>
                      {vid.videoUrl && (
                        <a
                          href={vid.videoUrl}
                          target="_blank"
                          rel="noreferrer"
                          style={{
                            background: '#F1F5F9',
                            color: '#0284C7',
                            padding: '4px 10px',
                            borderRadius: 6,
                            fontSize: 11,
                            fontWeight: 700,
                            textDecoration: 'none',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4
                          }}
                        >
                          ▶ Test Link
                        </a>
                      )}
                      <button
                        type="button"
                        onClick={() => handleRemoveVideoTutorial(index)}
                        style={{ background: '#FEE2E2', color: '#EF4444', border: 'none', padding: '4px 10px', borderRadius: 6, cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                      >
                        Delete
                      </button>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 12, marginBottom: 10 }}>
                    <div>
                      <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                        Video Title *
                      </label>
                      <input
                        className="input"
                        value={vid.title}
                        onChange={(e) => {
                          const updated = [...(config.video_tutorials || [])];
                          updated[index] = { ...updated[index], title: e.target.value };
                          setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
                        }}
                        placeholder="e.g. bKash Automatic Matching Guide"
                        required
                      />
                    </div>
                    <div>
                      <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                        Category
                      </label>
                      <input
                        className="input"
                        value={vid.category || ''}
                        onChange={(e) => {
                          const updated = [...(config.video_tutorials || [])];
                          updated[index] = { ...updated[index], category: e.target.value };
                          setConfig({ ...config, video_tutorials: updated });
                        }}
                        placeholder="e.g. Automation, Gateways, Setup"
                      />
                    </div>
                    <div>
                      <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                        Duration Badge
                      </label>
                      <input
                        className="input"
                        value={vid.duration}
                        onChange={(e) => {
                          const updated = [...(config.video_tutorials || [])];
                          updated[index] = { ...updated[index], duration: e.target.value };
                          setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
                        }}
                        placeholder="e.g. 5:24 min"
                      />
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 10, marginBottom: 10 }}>
                    <div>
                      <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                        Video URL / YouTube Link *
                      </label>
                      <input
                        className="input"
                        value={vid.videoUrl}
                        onChange={(e) => {
                          const updated = [...(config.video_tutorials || [])];
                          updated[index] = { ...updated[index], videoUrl: e.target.value };
                          setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
                        }}
                        placeholder="https://www.youtube.com/watch?v=... or direct MP4 URL"
                        required
                      />
                    </div>
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                      Description
                    </label>
                    <textarea
                      className="input"
                      value={vid.description}
                      onChange={(e) => {
                        const updated = [...(config.video_tutorials || [])];
                        updated[index] = { ...updated[index], description: e.target.value };
                        setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
                      }}
                      placeholder="Step-by-step video instructions..."
                      style={{ minHeight: 50, fontFamily: 'inherit' }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'faqs' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div>
                <h3 style={{ margin: 0 }}>❓ Frequently Asked Questions (FAQs)</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>Add, edit, or remove FAQs displayed in the Help Center</span>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleAddFaq}
                style={{ background: '#10B981' }}
              >
                + Add FAQ
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {config.faqs.map((faq: FaqItem, index: number) => (
                <div key={index} style={{ padding: 14, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <strong style={{ fontSize: 13, color: '#1E293B' }}>Question #{index + 1}</strong>
                    <button
                      type="button"
                      onClick={() => handleRemoveFaq(index)}
                      style={{ background: '#FEE2E2', color: '#EF4444', border: 'none', padding: '4px 8px', borderRadius: 6, cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                    >
                      Delete
                    </button>
                  </div>
                  <input
                    className="input"
                    value={faq.question}
                    onChange={(e) => {
                      const updated = [...config.faqs];
                      updated[index].question = e.target.value;
                      setConfig({ ...config, faqs: updated });
                    }}
                    placeholder="Question..."
                    style={{ marginBottom: 8 }}
                  />
                  <textarea
                    className="input"
                    value={faq.answer}
                    onChange={(e) => {
                      const updated = [...config.faqs];
                      updated[index].answer = e.target.value;
                      setConfig({ ...config, faqs: updated });
                    }}
                    placeholder="Answer explanation..."
                    style={{ minHeight: 60, fontFamily: 'inherit' }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'guides' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div>
                <h3 style={{ margin: 0 }}>📖 Step-by-Step Integration Guides</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>Displayed under the Guides modal in mobile Support</span>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleAddGuide}
                style={{ background: '#10B981' }}
              >
                + Add Guide Step
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {config.guides.map((guide: GuideItem, index: number) => (
                <div key={index} style={{ padding: 14, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <strong style={{ fontSize: 13, color: '#1E293B' }}>Step #{index + 1}</strong>
                    <button
                      type="button"
                      onClick={() => handleRemoveGuide(index)}
                      style={{ background: '#FEE2E2', color: '#EF4444', border: 'none', padding: '4px 8px', borderRadius: 6, cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                    >
                      Delete
                    </button>
                  </div>
                  <input
                    className="input"
                    value={guide.title}
                    onChange={(e) => {
                      const updated = [...config.guides];
                      updated[index].title = e.target.value;
                      setConfig({ ...config, guides: updated });
                    }}
                    placeholder="Step Title (e.g. 1. App Configuration)"
                    style={{ marginBottom: 8 }}
                  />
                  <textarea
                    className="input"
                    value={guide.description}
                    onChange={(e) => {
                      const updated = [...config.guides];
                      updated[index].description = e.target.value;
                      setConfig({ ...config, guides: updated });
                    }}
                    placeholder="Step detailed instructions..."
                    style={{ minHeight: 60, fontFamily: 'inherit' }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'articles' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div>
                <h3 style={{ margin: 0 }}>📄 Help Center Articles</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>Primary article cards and searchable documentation on Support home</span>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleAddArticle}
                style={{ background: '#10B981' }}
              >
                + Add Article
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {config.articles.map((art: ArticleItem, index: number) => (
                <div key={art.id || index} style={{ padding: 14, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <strong style={{ fontSize: 13, color: '#1E293B' }}>Article #{index + 1} ({art.id})</strong>
                    <button
                      type="button"
                      onClick={() => handleRemoveArticle(index)}
                      style={{ background: '#FEE2E2', color: '#EF4444', border: 'none', padding: '4px 8px', borderRadius: 6, cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                    >
                      Delete
                    </button>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 10, marginBottom: 8 }}>
                    <input
                      className="input"
                      value={art.title}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                        const updated = [...config.articles];
                        updated[index].title = e.target.value;
                        setConfig({ ...config, articles: updated });
                      }}
                      placeholder="Article Title..."
                    />
                    <input
                      className="input"
                      value={art.category}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                        const updated = [...config.articles];
                        updated[index].category = e.target.value;
                        setConfig({ ...config, articles: updated });
                      }}
                      placeholder="Category..."
                    />
                  </div>
                  <textarea
                    className="input"
                    value={art.content}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                      const updated = [...config.articles];
                      updated[index].content = e.target.value;
                      setConfig({ ...config, articles: updated });
                    }}
                    placeholder="Full article content (markdown & bullets supported)..."
                    style={{ minHeight: 90, fontFamily: 'inherit' }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'tickets' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div>
                <h3 style={{ margin: 0 }}>🎫 Submit Report / Ticket Categories</h3>
                <span style={{ fontSize: 12, color: '#64748B' }}>Configurable categories merchants can pick when reporting an issue</span>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleAddCategory}
                style={{ background: '#10B981' }}
              >
                + Add Category
              </button>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {config.ticket_categories.map((cat: string, index: number) => (
                <div
                  key={index}
                  style={{
                    padding: '8px 12px',
                    background: '#EEF2FF',
                    border: '1px solid #C7D2FE',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8
                  }}
                >
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#3730A3' }}>{cat}</span>
                  <button
                    type="button"
                    onClick={() => handleRemoveCategory(index)}
                    style={{ background: 'transparent', border: 'none', color: '#EF4444', fontWeight: 800, cursor: 'pointer' }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 16 }}>
          <button
            type="button"
            onClick={() => {
              if (window.confirm('Reset all settings and CMS content to system defaults?')) {
                setConfig(DEFAULT_CONFIG);
              }
            }}
            style={{ background: '#F1F5F9', color: '#64748B', border: '1px solid #CBD5E1', padding: '10px 16px', borderRadius: 8, cursor: 'pointer', fontWeight: 600 }}
          >
            Reset All Defaults
          </button>
          <button
            type="submit"
            className="button"
            disabled={isSaving}
            style={{ background: '#10B981', padding: '12px 24px', fontSize: 14, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: 8 }}
          >
            {isSaving ? 'Broadcasting to Mobile...' : '💾 Save & Broadcast All Changes to Mobile App'}
          </button>
        </div>
      </form>
    </div>
  );
}
