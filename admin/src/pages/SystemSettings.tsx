import React, { useEffect, useState, useCallback } from 'react';
import {
  adminSupabase,
  DEFAULT_RADYMATE_GALLERY,
  fetchRadymateGalleryConfig,
  RadymateGalleryItem,
  saveRadymateGalleryConfig,
  uploadRadymateGalleryImage,
  upsertShowcaseConfig,
} from '../adminSupabaseClient';
import { Link } from 'react-router-dom';
import {
  Globe,
  CreditCard,
  Code2,
  Image as ImageIcon,
  Video,
  PhoneCall,
  HelpCircle,
  BookOpen,
  FileText,
  Tag,
  ShieldCheck,
  Check,
  AlertTriangle,
  Save,
  RefreshCw,
  Plus,
  Trash2,
  ExternalLink,
  RotateCcw,
  UploadCloud,
  Clock,
  Mail,
  MessageSquare,
  MapPin,
  CheckCircle2,
  X,
  Sliders,
  Sparkles,
  Lock,
} from 'lucide-react';

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
    "payment_url": "https://pay.swapnopay.top/pay/ORD-2026-9812",
    "order_id": "ORD-2026-9812",
    "amount": 1250.00,
    "currency": "BDT",
    "assigned_receiver": "01784992118",
    "payment_method": "bKash",
    "expires_at": "2026-09-16T04:04:12.000Z"
  }
}
\`\`\`

---

### 3.2 Verify Payment Status
Query current settlement and matching state for any existing order.

- **Method**: \`GET\`
- **Endpoint**: \`/v1/payment/verify?order_id=ORD-2026-9812\`

#### Response (200 OK):
\`\`\`json
{
  "status": "PAID",
  "order_id": "ORD-2026-9812",
  "amount": 1250.00,
  "trx_id": "9H8B7G6F5E",
  "sender_phone": "01712963652",
  "matched_at": "2026-09-16T03:54:19.421Z",
  "payment_method": "bKash"
}
\`\`\`

---

## 4. Instant Webhook Notifications

When an incoming carrier SMS is matched, SwapnoPay dispatches an encrypted HTTP POST webhook to your configured \`webhook_url\`.

### 4.1 Webhook Signature Verification
Every webhook includes the following security headers:
- \`X-SwapnoPay-Signature\`: HMAC-SHA256 hash of the raw JSON request body signed with your \`WEBHOOK_SECRET\`.
- \`X-SwapnoPay-Timestamp\`: Unix epoch in milliseconds.

#### Example Verification (Node.js):
\`\`\`javascript
import crypto from 'crypto';

export function verifyWebhook(rawBody, signatureHeader, webhookSecret) {
  const hmac = crypto.createHmac('sha256', webhookSecret);
  const digest = hmac.update(rawBody).digest('hex');
  return crypto.timingSafeEqual(Buffer.from(digest), Buffer.from(signatureHeader));
}
\`\`\`

#### Webhook Payload:
\`\`\`json
{
  "event": "payment.succeeded",
  "created_at": "2026-09-16T03:54:20.100Z",
  "data": {
    "order_id": "ORD-2026-9812",
    "amount": 1250.00,
    "currency": "BDT",
    "trx_id": "9H8B7G6F5E",
    "sender_phone": "01712963652",
    "receiver_phone": "01784992118",
    "payment_method": "bKash",
    "match_latency_ms": 7.4
  }
}
\`\`\`

---

## 5. Standard Error Codes

| Error Code | HTTP Status | Description |
|---|---|---|
| \`ERR_INVALID_HMAC\` | 401 | Webhook signature verification failed. Verify your secret. |
| \`ERR_ORDER_EXPIRED\` | 400 | Payment window expired (default 10 min). |
| \`ERR_DUPLICATE_IDEMPOTENCY\` | 409 | Request with this Idempotency-Key already processed. |
| \`ERR_INSUFFICIENT_AMOUNT\` | 422 | Paid amount less than order invoice. |
| \`ERR_GATEWAY_OFFLINE\` | 503 | No Android receiver device online for requested number. |
`;

const DEFAULT_CONFIG: SystemRemoteConfig = {
  developer_portal_url: "https://pay.swapnopay.top/portal.html",
  developer_docs_url: "https://pay.swapnopay.top/docs.html",
  api_portal_url: "https://pay.swapnopay.top/portal.html#api-keys",
  webhook_docs_url: "https://pay.swapnopay.top/docs.html#webhooks",
  support_hotline: "+880 1794 827103",
  support_email: "support@swapnopay.top",
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
    { question: "Do I need a merchant account?", answer: "No, SwapnoPay fully supports Personal, Agent, and Merchant accounts for bKash, Nagad, and Rocket." },
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
      content: "SwapnoPay uses automatic SMS pattern recognition to match incoming mobile payments (bKash, Nagad, Rocket) with merchant orders in real-time.\n\n1. When a customer initiates a payment on your site, an order is created with a unique amount and payment reference.\n2. Once payment is completed, your Android device receives the official gateway SMS.\n3. The SwapnoPay background processor parses the transaction ID, sender's phone, and exact amount from the SMS.\n4. If all parameters match, the order is instantly marked as PAID and webhook notifications are triggered."
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

type SettingsTab =
  | 'links'
  | 'subscription'
  | 'api_docs'
  | 'gallery'
  | 'video'
  | 'support_contacts'
  | 'faqs'
  | 'guides'
  | 'articles'
  | 'tickets';

export default function SystemSettings() {
  const [config, setConfig] = useState<SystemRemoteConfig>(DEFAULT_CONFIG);
  const [activeTab, setActiveTab] = useState<SettingsTab>('links');
  const [loading, setLoading] = useState(true);
  const [statusMsg, setStatusMsg] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [radymateGallery, setRadymateGallery] = useState<RadymateGalleryItem[]>(DEFAULT_RADYMATE_GALLERY);
  const [galleryUploadFile, setGalleryUploadFile] = useState<File | null>(null);
  const [galleryTitle, setGalleryTitle] = useState('Radymate E-commerce launch');
  const [galleryCaption, setGalleryCaption] = useState('One-click launch storefront');
  const [gallerySaving, setGallerySaving] = useState(false);
  const [newCategoryInput, setNewCategoryInput] = useState('');
  const [subConfig, setSubConfig] = useState({
    monthly_fee: 100,
    quarterly_fee: 250,
    yearly_fee: 650,
    trial_days: 90,
    is_trial_enabled: true,
    enforce_nid_verification: true,
  });
  const [subSaving, setSubSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await adminSupabase
          .from('showcase_config')
          .select('value')
          .eq('key', 'system_config')
          .maybeSingle();

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
            ticket_categories: Array.isArray(val.ticket_categories) ? val.ticket_categories : DEFAULT_CONFIG.ticket_categories,
          });
        }
      } catch (e) {
        console.warn('Supabase showcase_config read warning:', e);
      }

      try {
        const remoteGallery = await fetchRadymateGalleryConfig();
        setRadymateGallery(remoteGallery.items.length ? remoteGallery.items : DEFAULT_RADYMATE_GALLERY);
      } catch (e) {
        console.warn('Radymate gallery fetch warning:', e);
        setRadymateGallery(DEFAULT_RADYMATE_GALLERY);
      }

      try {
        const { data: subData } = await adminSupabase
          .from('platform_subscription_config')
          .select('*')
          .eq('id', 'default_config')
          .maybeSingle();

        if (subData) {
          setSubConfig({
            monthly_fee: Number(subData.monthly_fee) || 100,
            quarterly_fee: Number(subData.quarterly_fee) || 250,
            yearly_fee: Number(subData.yearly_fee) || 650,
            trial_days: Number(subData.trial_days) || 90,
            is_trial_enabled: subData.is_trial_enabled ?? true,
            enforce_nid_verification: subData.enforce_nid_verification ?? true,
          });
        }
      } catch (e) {
        console.warn('Subscription config read notice:', e);
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

  const handleSaveSubscriptionConfig = async () => {
    setSubSaving(true);
    setStatusMsg('Saving subscription & pricing settings...');
    try {
      const { error } = await adminSupabase
        .from('platform_subscription_config')
        .upsert({
          id: 'default_config',
          monthly_fee: Number(subConfig.monthly_fee),
          quarterly_fee: Number(subConfig.quarterly_fee),
          yearly_fee: Number(subConfig.yearly_fee),
          trial_days: Number(subConfig.trial_days),
          is_trial_enabled: subConfig.is_trial_enabled,
          enforce_nid_verification: subConfig.enforce_nid_verification,
          updated_at: new Date().toISOString(),
        });
      if (error) throw error;
      setStatusMsg('SUCCESS: Subscription pricing and trial period updated successfully!');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('ERROR: Failed to save subscription config: ' + err.message);
    } finally {
      setSubSaving(false);
    }
  };

  const handleSave = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsSaving(true);
    setStatusMsg('Saving system configuration to Supabase...');

    try {
      const payload: SystemRemoteConfig = {
        ...config,
        last_updated: Date.now(),
      };

      await upsertShowcaseConfig('system_config', payload);

      setStatusMsg('SUCCESS: Successfully saved and broadcasted via Supabase Realtime!');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('ERROR: Failed to save: ' + err.message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleGalleryUpload = async () => {
    if (!galleryUploadFile) {
      setStatusMsg('ERROR: Select an image before uploading to the Radymate gallery bucket.');
      return;
    }

    setGallerySaving(true);
    setStatusMsg('Uploading to Supabase Storage bucket...');

    try {
      const uploadedItem = await uploadRadymateGalleryImage(galleryUploadFile, galleryTitle, galleryCaption);
      const updatedGallery = [uploadedItem, ...radymateGallery];
      setRadymateGallery(updatedGallery);
      await saveRadymateGalleryConfig(updatedGallery);
      setStatusMsg('SUCCESS: Image uploaded to the Supabase bucket and synced to the Radymate gallery.');
      setGalleryUploadFile(null);
      setGalleryTitle('Radymate E-commerce launch');
      setGalleryCaption('One-click launch storefront');
    } catch (err: any) {
      setStatusMsg('ERROR: Upload failed: ' + (err?.message || 'Unknown error'));
    } finally {
      setGallerySaving(false);
    }
  };

  const handleGalleryReset = async () => {
    if (!window.confirm('Reset gallery to default showcase images?')) return;
    setGallerySaving(true);
    try {
      setRadymateGallery(DEFAULT_RADYMATE_GALLERY);
      await saveRadymateGalleryConfig(DEFAULT_RADYMATE_GALLERY);
      setStatusMsg('SUCCESS: Radymate gallery reset to default showcase images.');
    } catch (err: any) {
      setStatusMsg('ERROR: Failed to reset gallery: ' + (err?.message || 'Unknown error'));
    } finally {
      setGallerySaving(false);
    }
  };

  const handleAddVideoTutorial = () => {
    const newVid: VideoTutorial = {
      id: 'vid_' + Date.now(),
      title: 'New Video Tutorial #' + ((config.video_tutorials || []).length + 1),
      description: 'Step-by-step instructions for integration.',
      videoUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
      duration: '3:00 min',
      category: 'General',
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
      faqs: [...config.faqs, { question: 'New FAQ Question?', answer: 'Answer explanation here...' }],
    });
  };

  const handleRemoveFaq = (index: number) => {
    setConfig({
      ...config,
      faqs: config.faqs.filter((_, i) => i !== index),
    });
  };

  const handleAddGuide = () => {
    setConfig({
      ...config,
      guides: [...config.guides, { title: (config.guides.length + 1) + '. Guide Title', description: 'Step instructions here...' }],
    });
  };

  const handleRemoveGuide = (index: number) => {
    setConfig({
      ...config,
      guides: config.guides.filter((_, i) => i !== index),
    });
  };

  const handleAddArticle = () => {
    const newId = 'art_' + Date.now();
    setConfig({
      ...config,
      articles: [...config.articles, { id: newId, title: 'New Support Guide', category: 'General', content: 'Detailed guide content...' }],
    });
  };

  const handleRemoveArticle = (index: number) => {
    setConfig({
      ...config,
      articles: config.articles.filter((_, i) => i !== index),
    });
  };

  const handleAddCategory = () => {
    if (newCategoryInput && newCategoryInput.trim()) {
      if (!config.ticket_categories.includes(newCategoryInput.trim())) {
        setConfig({
          ...config,
          ticket_categories: [...config.ticket_categories, newCategoryInput.trim()],
        });
      }
      setNewCategoryInput('');
    }
  };

  const handleRemoveCategory = (index: number) => {
    setConfig({
      ...config,
      ticket_categories: config.ticket_categories.filter((_, i) => i !== index),
    });
  };

  const navTabs: { key: SettingsTab; label: string; icon: React.ComponentType<{ size?: number; color?: string }>; count?: number }[] = [
    { key: 'links', label: 'Developer Portal Links', icon: Globe },
    { key: 'subscription', label: 'Subscription & Pricing', icon: CreditCard },
    { key: 'api_docs', label: 'API Documentation CMS', icon: Code2 },
    { key: 'gallery', label: 'Radymate Showcase', icon: ImageIcon, count: radymateGallery.length },
    { key: 'video', label: 'Video Tutorials', icon: Video, count: (config.video_tutorials || []).length },
    { key: 'support_contacts', label: 'Support Contacts', icon: PhoneCall },
    { key: 'faqs', label: 'FAQs Manager', icon: HelpCircle, count: config.faqs.length },
    { key: 'guides', label: 'Integration Guides', icon: BookOpen, count: config.guides.length },
    { key: 'articles', label: 'Help Articles', icon: FileText, count: config.articles.length },
    { key: 'tickets', label: 'Report Categories', icon: Tag, count: config.ticket_categories.length },
  ];

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 320, gap: 12, color: 'var(--text-muted)' }}>
        <RefreshCw size={20} className="spin" color="var(--brand-primary)" />
        <span style={{ fontSize: 14, fontWeight: 500 }}>Loading system configuration & CMS...</span>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 80 }}>
      {/* ──────────────── Header ──────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 'var(--radius-md)',
                background: 'var(--brand-subtle)',
                color: 'var(--brand-primary)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Sliders size={18} />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.02em' }}>
              System Settings & CMS
            </h1>
            <span className="status-pill success" style={{ fontSize: 11 }}>
              <span className="status-dot" />
              Supabase Realtime Live
            </span>
          </div>
          <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
            Continuous configuration for Developer Portal URLs, Anti-Piracy Billing Gates, Video Guides, and Help Center synced in real-time.
          </p>
          {config.last_updated && (
            <p style={{ margin: '4px 0 0', color: 'var(--text-muted)', fontSize: 11 }}>
              Last saved: {new Date(config.last_updated).toLocaleString()}
            </p>
          )}
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <Link to="/support" className="btn btn-secondary btn-sm">
            <HelpCircle size={14} />
            Helpdesk Inbox
          </Link>
          <Link to="/gateway-settings" className="btn btn-secondary btn-sm">
            <CreditCard size={14} />
            Gateway Settings
          </Link>
          <Link to="/dashboard" className="btn btn-primary btn-sm">
            Overview Dashboard
          </Link>
        </div>
      </div>

      {/* ──────────────── Status Banner ──────────────── */}
      {statusMsg && (
        <div
          style={{
            padding: '12px 18px',
            background: statusMsg.startsWith('SUCCESS') || statusMsg.startsWith('✅') ? 'var(--success-subtle)' : 'var(--danger-subtle)',
            border: `1px solid ${statusMsg.startsWith('SUCCESS') || statusMsg.startsWith('✅') ? 'var(--success-border)' : 'var(--danger-border)'}`,
            borderRadius: 'var(--radius-md)',
            color: statusMsg.startsWith('SUCCESS') || statusMsg.startsWith('✅') ? 'var(--success-text)' : 'var(--danger-text)',
            fontWeight: 600,
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            animation: 'fadeIn 200ms ease',
          }}
        >
          {statusMsg.startsWith('SUCCESS') || statusMsg.startsWith('✅') ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
          <span>{statusMsg.replace(/^(SUCCESS:|ERROR:|✅|❌)\s*/, '')}</span>
          <button
            type="button"
            onClick={() => setStatusMsg('')}
            style={{ marginLeft: 'auto', background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit', display: 'flex' }}
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* ──────────────── Segmented Tab Navigation ──────────────── */}
      <div
        style={{
          display: 'flex',
          gap: 6,
          borderBottom: '1px solid var(--border-default)',
          overflowX: 'auto',
          paddingBottom: 2,
          scrollbarWidth: 'none',
        }}
      >
        {navTabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: '10px 14px',
                fontSize: 13,
                fontWeight: isActive ? 600 : 500,
                color: isActive ? 'var(--brand-primary)' : 'var(--text-secondary)',
                background: isActive ? 'var(--brand-subtle)' : 'transparent',
                border: 'none',
                borderBottom: isActive ? '2px solid var(--brand-primary)' : '2px solid transparent',
                borderRadius: 'var(--radius-sm) var(--radius-sm) 0 0',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                transition: 'all var(--transition-fast)',
              }}
            >
              <Icon size={15} />
              <span>{tab.label}</span>
              {tab.count !== undefined && (
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    padding: '1px 6px',
                    borderRadius: 'var(--radius-full)',
                    background: isActive ? 'var(--brand-primary)' : 'var(--bg-muted)',
                    color: isActive ? 'white' : 'var(--text-muted)',
                    fontFamily: 'var(--font-mono)',
                  }}
                >
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <form onSubmit={handleSave}>
        {/* ════════════════ TAB 1: DEVELOPER PORTAL LINKS ════════════════ */}
        {activeTab === 'links' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Globe size={16} color="var(--brand-primary)" />
                  Developer Portal & Public Endpoints
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Destination links opened when merchants tap Developer Portal in the Android app, SDK downloads, and integration docs.
                </p>
              </div>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 18 }}>
                <div>
                  <label className="form-label">
                    Developer Web Portal URL *
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      className="input"
                      value={config.developer_portal_url}
                      onChange={(e) => setConfig({ ...config, developer_portal_url: e.target.value })}
                      placeholder="https://pay.swapnopay.top/portal.html"
                      required
                    />
                    {config.developer_portal_url && (
                      <a
                        href={config.developer_portal_url}
                        target="_blank"
                        rel="noreferrer"
                        className="btn btn-ghost btn-sm"
                        style={{ position: 'absolute', right: 4 }}
                        title="Test link in new tab"
                      >
                        <ExternalLink size={13} />
                      </a>
                    )}
                  </div>
                  <span className="form-hint">Opened via the 'Developer Portal' main button on the mobile app More screen.</span>
                </div>

                <div>
                  <label className="form-label">
                    Developer API Documentation URL *
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      className="input"
                      value={config.developer_docs_url}
                      onChange={(e) => setConfig({ ...config, developer_docs_url: e.target.value })}
                      placeholder="https://pay.swapnopay.top/docs.html"
                      required
                    />
                    {config.developer_docs_url && (
                      <a
                        href={config.developer_docs_url}
                        target="_blank"
                        rel="noreferrer"
                        className="btn btn-ghost btn-sm"
                        style={{ position: 'absolute', right: 4 }}
                        title="Test link in new tab"
                      >
                        <ExternalLink size={13} />
                      </a>
                    )}
                  </div>
                  <span className="form-hint">Comprehensive REST API & SDK reference guide destination.</span>
                </div>

                <div>
                  <label className="form-label">
                    API Keys Management Portal URL
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      className="input"
                      value={config.api_portal_url}
                      onChange={(e) => setConfig({ ...config, api_portal_url: e.target.value })}
                      placeholder="https://pay.swapnopay.top/portal.html#api-keys"
                    />
                    {config.api_portal_url && (
                      <a
                        href={config.api_portal_url}
                        target="_blank"
                        rel="noreferrer"
                        className="btn btn-ghost btn-sm"
                        style={{ position: 'absolute', right: 4 }}
                        title="Test link in new tab"
                      >
                        <ExternalLink size={13} />
                      </a>
                    )}
                  </div>
                  <span className="form-hint">Direct link for merchants to generate and inspect their API keys.</span>
                </div>

                <div>
                  <label className="form-label">
                    Webhooks Guide & Sandbox Simulator URL
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      className="input"
                      value={config.webhook_docs_url}
                      onChange={(e) => setConfig({ ...config, webhook_docs_url: e.target.value })}
                      placeholder="https://pay.swapnopay.top/docs.html#webhooks"
                    />
                    {config.webhook_docs_url && (
                      <a
                        href={config.webhook_docs_url}
                        target="_blank"
                        rel="noreferrer"
                        className="btn btn-ghost btn-sm"
                        style={{ position: 'absolute', right: 4 }}
                        title="Test link in new tab"
                      >
                        <ExternalLink size={13} />
                      </a>
                    )}
                  </div>
                  <span className="form-hint">Instant webhook listener setup and HMAC signature verification guide.</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ════════════════ TAB 2: SUBSCRIPTION & PRICING ════════════════ */}
        {activeTab === 'subscription' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Header Card */}
            <div className="card">
              <div className="card-header">
                <div>
                  <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <CreditCard size={16} color="var(--brand-primary)" />
                    Subscription Pricing, Free Trial & Anti-Piracy Billing Gates
                  </h3>
                  <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                    Dynamically adjust monthly, quarterly, and yearly subscription fees, trial duration, and enforce 1-NID = 1-Account anti-abuse verification.
                  </p>
                </div>
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={handleSaveSubscriptionConfig}
                  disabled={subSaving}
                >
                  <Save size={13} />
                  {subSaving ? 'Saving...' : 'Save Pricing & Trial Settings'}
                </button>
              </div>

              <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                {/* Anti-Piracy Security Rules */}
                <div
                  style={{
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-md)',
                    padding: 18,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                    <ShieldCheck size={18} color="var(--brand-primary)" />
                    <h4 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: 'var(--text-primary)' }}>
                      Anti-Piracy & Anti-Abuse Rules Enforcement
                    </h4>
                  </div>
                  <ul style={{ margin: 0, paddingLeft: 22, fontSize: 13, color: 'var(--text-secondary)', lineHeight: '1.7' }}>
                    <li><strong>Strict 1 NID = 1 Account:</strong> The backend prevents any National ID (NID) number from being registered to more than one merchant account.</li>
                    <li><strong>Mandatory NID Verification Gate:</strong> If enabled, accounts without verified NID are gated from billing & automated matching services.</li>
                    <li><strong>Dynamic Paywall:</strong> When free trials and subscriptions expire, the mobile app automatically locks access with an un-bypassable billing paywall.</li>
                    <li><strong>Native Gateway Flow:</strong> All subscription payments are processed directly through SwapnoPay's own automated receiving gateway (bKash, Nagad, Rocket).</li>
                  </ul>
                </div>

                {/* 3 Pricing Plans */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
                  {/* Monthly Plan */}
                  <div
                    style={{
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-lg)',
                      padding: 18,
                      boxShadow: 'var(--shadow-xs)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--brand-primary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                        Monthly Plan
                      </span>
                      <span className="status-pill neutral" style={{ fontSize: 10 }}>
                        30 Days
                      </span>
                    </div>
                    <label className="form-label">
                      Monthly Fee (BDT ৳) *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <span style={{ position: 'absolute', left: 12, fontWeight: 700, color: 'var(--text-muted)' }}>৳</span>
                      <input
                        type="number"
                        className="input"
                        style={{ paddingLeft: 28, fontSize: 16, fontWeight: 700 }}
                        value={subConfig.monthly_fee}
                        onChange={(e) => setSubConfig({ ...subConfig, monthly_fee: Number(e.target.value) })}
                        min={1}
                        required
                      />
                    </div>
                    <span className="form-hint">Standard default: ৳100 / 30 Days</span>
                  </div>

                  {/* Quarterly Plan */}
                  <div
                    style={{
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-lg)',
                      padding: 18,
                      boxShadow: 'var(--shadow-xs)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--info)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                        Quarterly Plan
                      </span>
                      <span className="status-pill info" style={{ fontSize: 10 }}>
                        90 Days • Popular
                      </span>
                    </div>
                    <label className="form-label">
                      Quarterly Fee (BDT ৳) *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <span style={{ position: 'absolute', left: 12, fontWeight: 700, color: 'var(--text-muted)' }}>৳</span>
                      <input
                        type="number"
                        className="input"
                        style={{ paddingLeft: 28, fontSize: 16, fontWeight: 700 }}
                        value={subConfig.quarterly_fee}
                        onChange={(e) => setSubConfig({ ...subConfig, quarterly_fee: Number(e.target.value) })}
                        min={1}
                        required
                      />
                    </div>
                    <span className="form-hint">Standard default: ৳250 / 90 Days</span>
                  </div>

                  {/* Yearly Plan */}
                  <div
                    style={{
                      background: 'var(--bg-surface)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-lg)',
                      padding: 18,
                      boxShadow: 'var(--shadow-xs)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--success)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                        Yearly Plan
                      </span>
                      <span className="status-pill success" style={{ fontSize: 10 }}>
                        365 Days • Best Value
                      </span>
                    </div>
                    <label className="form-label">
                      Yearly Fee (BDT ৳) *
                    </label>
                    <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                      <span style={{ position: 'absolute', left: 12, fontWeight: 700, color: 'var(--text-muted)' }}>৳</span>
                      <input
                        type="number"
                        className="input"
                        style={{ paddingLeft: 28, fontSize: 16, fontWeight: 700 }}
                        value={subConfig.yearly_fee}
                        onChange={(e) => setSubConfig({ ...subConfig, yearly_fee: Number(e.target.value) })}
                        min={1}
                        required
                      />
                    </div>
                    <span className="form-hint">Standard default: ৳650 / 365 Days</span>
                  </div>
                </div>

                {/* Free Trial & Gate Controls */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
                  {/* Trial Card */}
                  <div
                    style={{
                      background: 'var(--bg-subtle)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-md)',
                      padding: 18,
                    }}
                  >
                    <label className="form-label" style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
                      Free Trial Duration (Days)
                    </label>
                    <input
                      type="number"
                      className="input"
                      value={subConfig.trial_days}
                      onChange={(e) => setSubConfig({ ...subConfig, trial_days: Number(e.target.value) })}
                      min={0}
                      required
                    />
                    <span className="form-hint">Default is 90 days (3 Months Free Trial) granted on KYC registration.</span>

                    <div style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 10 }}>
                      <label className="switch">
                        <input
                          type="checkbox"
                          checked={subConfig.is_trial_enabled}
                          onChange={(e) => setSubConfig({ ...subConfig, is_trial_enabled: e.target.checked })}
                        />
                        <span className="slider" />
                      </label>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>
                        Enable 3-Month Free Trial for newly registered accounts
                      </span>
                    </div>
                  </div>

                  {/* NID Enforcement Card */}
                  <div
                    style={{
                      background: 'var(--bg-subtle)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-md)',
                      padding: 18,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <Lock size={15} color="var(--danger)" />
                      <label className="form-label" style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>
                        Strict Identity & NID Verification Gate
                      </label>
                    </div>
                    <p style={{ fontSize: 12, color: 'var(--text-secondary)', margin: '0 0 12px 0' }}>
                      When enabled, merchants MUST complete NID document submission before buying subscriptions or using gateway endpoints.
                    </p>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <label className="switch">
                        <input
                          type="checkbox"
                          checked={subConfig.enforce_nid_verification}
                          onChange={(e) => setSubConfig({ ...subConfig, enforce_nid_verification: e.target.checked })}
                        />
                        <span className="slider" />
                      </label>
                      <span style={{ fontSize: 13, fontWeight: 700, color: subConfig.enforce_nid_verification ? 'var(--danger-text)' : 'var(--text-secondary)' }}>
                        Enforce Mandatory NID Verification (1 NID = 1 Account)
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ════════════════ TAB 3: API DOCUMENTATION CMS ════════════════ */}
        {activeTab === 'api_docs' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Code2 size={16} color="var(--brand-primary)" />
                  Developer API Documentation Specification (Markdown CMS)
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Edit the comprehensive API specification. Changes are immediately synced to both the Android App Developer Portal and Web Docs.
                </p>
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setConfig({ ...config, api_documentation: DEFAULT_API_DOCS_MARKDOWN })}
                >
                  <FileText size={13} />
                  Load Official API Spec Template
                </button>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => setConfig({ ...config, api_documentation: '' })}
                >
                  <RotateCcw size={13} />
                  Clear (Use System Default)
                </button>
              </div>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', gap: 16, alignItems: 'center', fontSize: 12, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
                <span className="status-pill neutral">
                  Characters: <strong>{(config.api_documentation || DEFAULT_API_DOCS_MARKDOWN).length.toLocaleString()}</strong>
                </span>
                <span className="status-pill neutral">
                  Lines: <strong>{(config.api_documentation || DEFAULT_API_DOCS_MARKDOWN).split('\n').length}</strong>
                </span>
                <span className={`status-pill ${config.api_documentation ? 'success' : 'neutral'}`}>
                  <span className="status-dot" />
                  {config.api_documentation ? 'Custom CMS Override Active' : 'System Default Template Active'}
                </span>
              </div>

              <textarea
                className="input"
                value={config.api_documentation}
                onChange={(e) => setConfig({ ...config, api_documentation: e.target.value })}
                placeholder="Leave empty to use built-in exhaustive API specification, or enter customized Markdown documentation here..."
                style={{
                  minHeight: 460,
                  fontFamily: 'var(--font-mono)',
                  fontSize: 12.5,
                  lineHeight: 1.6,
                  background: 'var(--bg-subtle)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-default)',
                  borderRadius: 'var(--radius-md)',
                  padding: 16,
                  resize: 'vertical',
                }}
              />
            </div>
          </div>
        )}

        {/* ════════════════ TAB 4: RADYMATE SHOWCASE GALLERY ════════════════ */}
        {activeTab === 'gallery' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Upload Box */}
            <div className="card">
              <div className="card-header">
                <div>
                  <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <ImageIcon size={16} color="var(--brand-primary)" />
                    Radymate Studio Gallery Management
                  </h3>
                  <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                    Upload storefront launch screenshots to the Supabase Storage bucket. The public landing page & gallery dynamically read these images.
                  </p>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={handleGalleryReset}
                  disabled={gallerySaving}
                >
                  <RotateCcw size={13} />
                  {gallerySaving ? 'Resetting...' : 'Reset to Default Showcase'}
                </button>
              </div>

              <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                  <div>
                    <label className="form-label">Gallery Title</label>
                    <input
                      className="input"
                      value={galleryTitle}
                      onChange={(e) => setGalleryTitle(e.target.value)}
                      placeholder="Radymate E-commerce launch"
                    />
                  </div>
                  <div>
                    <label className="form-label">Caption / Tagline</label>
                    <input
                      className="input"
                      value={galleryCaption}
                      onChange={(e) => setGalleryCaption(e.target.value)}
                      placeholder="One-click launch storefront"
                    />
                  </div>
                  <div style={{ gridColumn: '1 / -1' }}>
                    <label className="form-label">Upload Image to Supabase Storage</label>
                    <input
                      className="input"
                      type="file"
                      accept="image/*"
                      onChange={(e) => setGalleryUploadFile(e.target.files?.[0] || null)}
                    />
                    <span className="form-hint">
                      Target bucket: <strong>radymate-gallery</strong>. Uploaded images are publicly cached and served via Supabase CDN.
                    </span>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={handleGalleryUpload}
                    disabled={gallerySaving || !galleryUploadFile}
                  >
                    <UploadCloud size={15} />
                    {gallerySaving ? 'Uploading to Supabase...' : 'Upload & Save to Storage'}
                  </button>
                </div>
              </div>
            </div>

            {/* Gallery Grid */}
            <div className="card">
              <div className="card-header">
                <h3 className="card-title">
                  Current Gallery Images ({radymateGallery.length})
                </h3>
              </div>
              <div className="card-body">
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16 }}>
                  {radymateGallery.map((item, index) => (
                    <div
                      key={`${item.title}-${index}`}
                      style={{
                        border: '1px solid var(--border-default)',
                        borderRadius: 'var(--radius-md)',
                        overflow: 'hidden',
                        background: 'var(--bg-surface)',
                        boxShadow: 'var(--shadow-xs)',
                      }}
                    >
                      <img
                        src={item.image}
                        alt={item.title}
                        style={{ width: '100%', height: 130, objectFit: 'cover', display: 'block', background: 'var(--bg-subtle)' }}
                      />
                      <div style={{ padding: 12 }}>
                        <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.title}
                        </div>
                        <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 4, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.caption}
                        </div>
                        <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                          <span
                            className={`status-pill ${item.source === 'storage' ? 'success' : 'neutral'}`}
                            style={{ fontSize: 10 }}
                          >
                            {item.source === 'storage' ? 'Supabase Storage' : 'Default Demo'}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ════════════════ TAB 5: VIDEO TUTORIALS ════════════════ */}
        {activeTab === 'video' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Video size={16} color="var(--brand-primary)" />
                  Video Integration Tutorials Manager
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Manage video guides shown on the mobile Developer Portal and Web Documentation. Supports YouTube URLs and MP4 direct streams.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleAddVideoTutorial}
              >
                <Plus size={14} />
                Add Video Tutorial
              </button>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {(config.video_tutorials || []).map((vid: VideoTutorial, index: number) => (
                <div
                  key={vid.id || index}
                  style={{
                    padding: 18,
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-md)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span
                        style={{
                          width: 24,
                          height: 24,
                          borderRadius: '50%',
                          background: 'var(--brand-primary)',
                          color: 'white',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 12,
                          fontWeight: 700,
                        }}
                      >
                        {index + 1}
                      </span>
                      <strong style={{ fontSize: 14, color: 'var(--text-primary)' }}>
                        {vid.title || 'Untitled Tutorial'}
                      </strong>
                      {vid.category && (
                        <span className="status-pill info" style={{ fontSize: 11 }}>
                          {vid.category}
                        </span>
                      )}
                      {vid.duration && (
                        <span className="status-pill neutral" style={{ fontSize: 11 }}>
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
                          className="btn btn-secondary btn-sm"
                          title="Open Video Link"
                        >
                          <ExternalLink size={12} />
                          Test Link
                        </a>
                      )}
                      <button
                        type="button"
                        onClick={() => handleRemoveVideoTutorial(index)}
                        className="btn btn-danger btn-sm"
                        title="Delete tutorial"
                      >
                        <Trash2 size={12} />
                        Delete
                      </button>
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
                    <div>
                      <label className="form-label">Video Title *</label>
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
                      <label className="form-label">Category</label>
                      <input
                        className="input"
                        value={vid.category || ''}
                        onChange={(e) => {
                          const updated = [...(config.video_tutorials || [])];
                          updated[index] = { ...updated[index], category: e.target.value };
                          setConfig({ ...config, video_tutorials: updated });
                        }}
                        placeholder="e.g. Automation, Setup"
                      />
                    </div>
                    <div>
                      <label className="form-label">Duration Badge</label>
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

                  <div style={{ marginBottom: 12 }}>
                    <label className="form-label">Video URL / YouTube Link *</label>
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

                  <div>
                    <label className="form-label">Description</label>
                    <textarea
                      className="input"
                      value={vid.description}
                      onChange={(e) => {
                        const updated = [...(config.video_tutorials || [])];
                        updated[index] = { ...updated[index], description: e.target.value };
                        setConfig({ ...config, video_tutorials: updated, video_tutorial: updated[0] || config.video_tutorial });
                      }}
                      placeholder="Step-by-step video instructions..."
                      style={{ minHeight: 60 }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ════════════════ TAB 6: SUPPORT CONTACTS ════════════════ */}
        {activeTab === 'support_contacts' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <PhoneCall size={16} color="var(--brand-primary)" />
                  Official Help & Support Contact Details
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Contact details displayed on the merchant app Support screen, including hotline dialing, WhatsApp chat, and live announcements.
                </p>
              </div>
            </div>

            <div className="card-body" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 18 }}>
              <div>
                <label className="form-label">
                  Hotline Phone Number (Direct Dial) *
                </label>
                <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                  <input
                    className="input"
                    value={config.support_hotline}
                    onChange={(e) => setConfig({ ...config, support_hotline: e.target.value })}
                    placeholder="+880 1794 827103"
                    required
                  />
                  {config.support_hotline && (
                    <a
                      href={`tel:${config.support_hotline}`}
                      className="btn btn-ghost btn-sm"
                      style={{ position: 'absolute', right: 4 }}
                      title="Test direct dial"
                    >
                      <PhoneCall size={13} />
                    </a>
                  )}
                </div>
                <span className="form-hint">Tapped by merchants for immediate operator phone support.</span>
              </div>

              <div>
                <label className="form-label">
                  Support Email Inquiries *
                </label>
                <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                  <input
                    className="input"
                    value={config.support_email}
                    onChange={(e) => setConfig({ ...config, support_email: e.target.value })}
                    placeholder="support@swapnopay.top"
                    required
                  />
                  {config.support_email && (
                    <a
                      href={`mailto:${config.support_email}`}
                      className="btn btn-ghost btn-sm"
                      style={{ position: 'absolute', right: 4 }}
                      title="Send email"
                    >
                      <Mail size={13} />
                    </a>
                  )}
                </div>
                <span className="form-hint">Primary contact email for business inquiries and billing queries.</span>
              </div>

              <div>
                <label className="form-label">
                  WhatsApp Support Link / Number *
                </label>
                <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                  <input
                    className="input"
                    value={config.support_whatsapp}
                    onChange={(e) => setConfig({ ...config, support_whatsapp: e.target.value })}
                    placeholder="+8801712963652"
                    required
                  />
                  {config.support_whatsapp && (
                    <a
                      href={`https://wa.me/${config.support_whatsapp.replace(/[^0-9]/g, '')}`}
                      target="_blank"
                      rel="noreferrer"
                      className="btn btn-ghost btn-sm"
                      style={{ position: 'absolute', right: 4 }}
                      title="Open WhatsApp chat"
                    >
                      <MessageSquare size={13} />
                    </a>
                  )}
                </div>
                <span className="form-hint">Opens directly into WhatsApp Messenger for live chat assistance.</span>
              </div>

              <div>
                <label className="form-label">
                  Operating Hours
                </label>
                <input
                  className="input"
                  value={config.support_hours}
                  onChange={(e) => setConfig({ ...config, support_hours: e.target.value })}
                  placeholder="24/7 Chat & Ticket Support"
                />
                <span className="form-hint">Availability indicator shown to merchants.</span>
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <label className="form-label">
                  Main Headquarters Address
                </label>
                <input
                  className="input"
                  value={config.support_address}
                  onChange={(e) => setConfig({ ...config, support_address: e.target.value })}
                  placeholder="Level 14, Banani Tower, Dhaka, Bangladesh"
                />
                <span className="form-hint">Official company registration and office location.</span>
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <label className="form-label">
                  System Broadcast Announcement
                </label>
                <textarea
                  className="input"
                  value={config.system_notice}
                  onChange={(e) => setConfig({ ...config, system_notice: e.target.value })}
                  placeholder="Broadcast message shown to all merchants..."
                  style={{ minHeight: 70 }}
                />
                <span className="form-hint">Broadcast banner message displayed on top of the merchant dashboard.</span>
              </div>
            </div>
          </div>
        )}

        {/* ════════════════ TAB 7: FAQS ════════════════ */}
        {activeTab === 'faqs' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <HelpCircle size={16} color="var(--brand-primary)" />
                  Frequently Asked Questions (FAQs)
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Questions and answers displayed in the mobile Help Center accordion.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleAddFaq}
              >
                <Plus size={14} />
                Add FAQ Question
              </button>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {config.faqs.map((faq: FaqItem, index: number) => (
                <div
                  key={index}
                  style={{
                    padding: 16,
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-md)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
                      Question #{index + 1}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleRemoveFaq(index)}
                      className="btn btn-danger btn-sm"
                      title="Delete question"
                    >
                      <Trash2 size={12} />
                      Delete
                    </button>
                  </div>
                  <div style={{ marginBottom: 10 }}>
                    <label className="form-label">Question</label>
                    <input
                      className="input"
                      value={faq.question}
                      onChange={(e) => {
                        const updated = [...config.faqs];
                        updated[index].question = e.target.value;
                        setConfig({ ...config, faqs: updated });
                      }}
                      placeholder="Question..."
                    />
                  </div>
                  <div>
                    <label className="form-label">Answer Explanation</label>
                    <textarea
                      className="input"
                      value={faq.answer}
                      onChange={(e) => {
                        const updated = [...config.faqs];
                        updated[index].answer = e.target.value;
                        setConfig({ ...config, faqs: updated });
                      }}
                      placeholder="Answer explanation..."
                      style={{ minHeight: 60 }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ════════════════ TAB 8: INTEGRATION GUIDES ════════════════ */}
        {activeTab === 'guides' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <BookOpen size={16} color="var(--brand-primary)" />
                  Step-by-Step Integration Guides
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Interactive setup steps displayed under the Guides section in mobile Support.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleAddGuide}
              >
                <Plus size={14} />
                Add Guide Step
              </button>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {config.guides.map((guide: GuideItem, index: number) => (
                <div
                  key={index}
                  style={{
                    padding: 16,
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-md)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
                      Step #{index + 1}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleRemoveGuide(index)}
                      className="btn btn-danger btn-sm"
                      title="Delete step"
                    >
                      <Trash2 size={12} />
                      Delete
                    </button>
                  </div>
                  <div style={{ marginBottom: 10 }}>
                    <label className="form-label">Step Title</label>
                    <input
                      className="input"
                      value={guide.title}
                      onChange={(e) => {
                        const updated = [...config.guides];
                        updated[index].title = e.target.value;
                        setConfig({ ...config, guides: updated });
                      }}
                      placeholder="Step Title (e.g. 1. App Configuration)"
                    />
                  </div>
                  <div>
                    <label className="form-label">Step Instructions</label>
                    <textarea
                      className="input"
                      value={guide.description}
                      onChange={(e) => {
                        const updated = [...config.guides];
                        updated[index].description = e.target.value;
                        setConfig({ ...config, guides: updated });
                      }}
                      placeholder="Step detailed instructions..."
                      style={{ minHeight: 65 }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ════════════════ TAB 9: HELP ARTICLES ════════════════ */}
        {activeTab === 'articles' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <FileText size={16} color="var(--brand-primary)" />
                  Help Center Documentation Articles
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Detailed troubleshooting and configuration articles shown on mobile Support home.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleAddArticle}
              >
                <Plus size={14} />
                Add Help Article
              </button>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {config.articles.map((art: ArticleItem, index: number) => (
                <div
                  key={art.id || index}
                  style={{
                    padding: 18,
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-md)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className="status-pill neutral" style={{ fontSize: 11, fontFamily: 'var(--font-mono)' }}>
                        {art.id}
                      </span>
                      <strong style={{ fontSize: 14, color: 'var(--text-primary)' }}>
                        {art.title || 'Untitled Article'}
                      </strong>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveArticle(index)}
                      className="btn btn-danger btn-sm"
                      title="Delete article"
                    >
                      <Trash2 size={12} />
                      Delete
                    </button>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12, marginBottom: 12 }}>
                    <div>
                      <label className="form-label">Article Title</label>
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
                    </div>
                    <div>
                      <label className="form-label">Category</label>
                      <input
                        className="input"
                        value={art.category}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                          const updated = [...config.articles];
                          updated[index].category = e.target.value;
                          setConfig({ ...config, articles: updated });
                        }}
                        placeholder="Category (e.g. Automation, Disputes)..."
                      />
                    </div>
                  </div>

                  <div>
                    <label className="form-label">Full Article Content (Markdown supported)</label>
                    <textarea
                      className="input"
                      value={art.content}
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                        const updated = [...config.articles];
                        updated[index].content = e.target.value;
                        setConfig({ ...config, articles: updated });
                      }}
                      placeholder="Full article content (markdown & bullets supported)..."
                      style={{ minHeight: 90 }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ════════════════ TAB 10: REPORT CATEGORIES ════════════════ */}
        {activeTab === 'tickets' && (
          <div className="card">
            <div className="card-header">
              <div>
                <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Tag size={16} color="var(--brand-primary)" />
                  Submit Report & Ticket Categories
                </h3>
                <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                  Configurable categories merchants can pick when reporting an issue or opening a support dispute.
                </p>
              </div>
            </div>

            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              {/* Category Chips */}
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {config.ticket_categories.map((cat: string, index: number) => (
                  <div
                    key={index}
                    style={{
                      padding: '7px 12px',
                      background: 'var(--bg-subtle)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-full)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      fontSize: 13,
                      fontWeight: 600,
                      color: 'var(--text-primary)',
                      boxShadow: 'var(--shadow-xs)',
                    }}
                  >
                    <span>{cat}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveCategory(index)}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: 'var(--text-muted)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        padding: 2,
                        borderRadius: '50%',
                        transition: 'color var(--transition-fast)',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--danger)')}
                      onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--text-muted)')}
                      title="Remove category"
                    >
                      <X size={13} />
                    </button>
                  </div>
                ))}
              </div>

              {/* Add Category Inline */}
              <div style={{ display: 'flex', gap: 10, maxWidth: 420, alignItems: 'center' }}>
                <input
                  className="input"
                  value={newCategoryInput}
                  onChange={(e) => setNewCategoryInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddCategory();
                    }
                  }}
                  placeholder="Enter new category name..."
                />
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleAddCategory}
                  disabled={!newCategoryInput.trim()}
                >
                  <Plus size={14} />
                  Add
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ════════════════ STICKY BOTTOM SAVE BAR ════════════════ */}
        <div
          style={{
            position: 'fixed',
            bottom: 0,
            left: 0,
            right: 0,
            background: 'var(--bg-surface)',
            borderTop: '1px solid var(--border-default)',
            padding: '14px 28px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            boxShadow: '0 -4px 16px rgba(0, 0, 0, 0.06)',
            zIndex: 35,
            backdropFilter: 'blur(12px)',
          }}
        >
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => {
              if (window.confirm('Reset all CMS settings to system defaults?')) {
                setConfig(DEFAULT_CONFIG);
              }
            }}
          >
            <RotateCcw size={13} />
            Reset All Defaults
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              Realtime WebSocket broadcast active
            </span>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSaving}
              style={{ minWidth: 220, height: 38 }}
            >
              {isSaving ? (
                <>
                  <RefreshCw size={14} className="spin" />
                  Broadcasting to Mobile App...
                </>
              ) : (
                <>
                  <Save size={14} />
                  Save & Broadcast All Changes
                </>
              )}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
