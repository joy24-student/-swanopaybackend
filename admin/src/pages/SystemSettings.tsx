import React, { useEffect, useState } from 'react';
import { ref, set, onValue } from 'firebase/database';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { rtdb, db } from '../firebaseConfig';
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
  title: string;
  description: string;
  videoUrl: string;
  duration: string;
  thumbnailUrl: string;
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
  faqs: FaqItem[];
  guides: GuideItem[];
  articles: ArticleItem[];
  ticket_categories: string[];
  last_updated?: number;
}

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
    title: "Complete Automatic Matching Walkthrough",
    description: "Step-by-step video guide to configure SMS listener, match payments, and link webhooks.",
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    duration: "3:45 min",
    thumbnailUrl: ""
  },
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
  const [activeTab, setActiveTab] = useState<'links' | 'support_contacts' | 'video' | 'faqs' | 'guides' | 'articles' | 'tickets'>('links');
  const [loading, setLoading] = useState(true);
  const [statusMsg, setStatusMsg] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const configRef = ref(rtdb, 'platform_owner/system_config');
    const unsub = onValue(configRef, (snap) => {
      if (snap.exists()) {
        const val = snap.val();
        setConfig({
          ...DEFAULT_CONFIG,
          ...val,
          video_tutorial: { ...DEFAULT_CONFIG.video_tutorial, ...(val.video_tutorial || {}) },
          faqs: Array.isArray(val.faqs) ? val.faqs : DEFAULT_CONFIG.faqs,
          guides: Array.isArray(val.guides) ? val.guides : DEFAULT_CONFIG.guides,
          articles: Array.isArray(val.articles) ? val.articles : DEFAULT_CONFIG.articles,
          ticket_categories: Array.isArray(val.ticket_categories) ? val.ticket_categories : DEFAULT_CONFIG.ticket_categories
        });
      } else {
        loadFirestoreConfig();
      }
      setLoading(false);
    }, (err) => {
      console.warn('RTDB system_config read error:', err);
      loadFirestoreConfig();
      setLoading(false);
    });

    return () => unsub();
  }, []);

  const loadFirestoreConfig = async () => {
    try {
      const snap = await getDoc(doc(db, 'system_config', 'links_and_support'));
      if (snap.exists()) {
        const val = snap.data() as any;
        setConfig({
          ...DEFAULT_CONFIG,
          ...val,
          video_tutorial: { ...DEFAULT_CONFIG.video_tutorial, ...(val.video_tutorial || {}) },
          faqs: Array.isArray(val.faqs) ? val.faqs : DEFAULT_CONFIG.faqs,
          guides: Array.isArray(val.guides) ? val.guides : DEFAULT_CONFIG.guides,
          articles: Array.isArray(val.articles) ? val.articles : DEFAULT_CONFIG.articles,
          ticket_categories: Array.isArray(val.ticket_categories) ? val.ticket_categories : DEFAULT_CONFIG.ticket_categories
        });
      }
    } catch (e) {
      console.error('Firestore config load error:', e);
    }
  };

  const handleSave = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setIsSaving(true);
    setStatusMsg('Broadcasting all links and support content to Firebase...');

    try {
      const payload: SystemRemoteConfig = {
        ...config,
        last_updated: Date.now()
      };

      await set(ref(rtdb, 'platform_owner/system_config'), payload);
      await set(ref(rtdb, 'platform_owner/support_content'), payload);
      await set(ref(rtdb, 'system_config'), payload);

      try {
        await setDoc(doc(db, 'system_config', 'links_and_support'), payload, { merge: true });
        await setDoc(doc(db, 'system_config', 'support_content'), payload, { merge: true });
      } catch (_) {}

      setStatusMsg('✅ Successfully saved and broadcasted to all Android app screens in real-time!');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('❌ Failed to save: ' + err.message);
    } finally {
      setIsSaving(false);
    }
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
          { key: 'support_contacts', label: '📞 Support Contacts' },
          { key: 'video', label: '🎥 Video Tutorial' },
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

        {activeTab === 'video' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0 }}>🎥 Video Tutorial Configuration</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Video Title *
                </label>
                <input
                  className="input"
                  value={config.video_tutorial.title}
                  onChange={(e) => setConfig({
                    ...config,
                    video_tutorial: { ...config.video_tutorial, title: e.target.value }
                  })}
                  placeholder="Video Title..."
                  required
                />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Video Description
                </label>
                <textarea
                  className="input"
                  value={config.video_tutorial.description}
                  onChange={(e) => setConfig({
                    ...config,
                    video_tutorial: { ...config.video_tutorial, description: e.target.value }
                  })}
                  placeholder="Video description..."
                  style={{ minHeight: 60, fontFamily: 'inherit' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Video URL / Stream Link (MP4 or YouTube) *
                </label>
                <input
                  className="input"
                  value={config.video_tutorial.videoUrl}
                  onChange={(e) => setConfig({
                    ...config,
                    video_tutorial: { ...config.video_tutorial, videoUrl: e.target.value }
                  })}
                  placeholder="https://example.com/video.mp4"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>
                  Video Duration Badge
                </label>
                <input
                  className="input"
                  value={config.video_tutorial.duration}
                  onChange={(e) => setConfig({
                    ...config,
                    video_tutorial: { ...config.video_tutorial, duration: e.target.value }
                  })}
                  placeholder="3:45 min"
                />
              </div>
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
              {config.faqs.map((faq, index) => (
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
              {config.guides.map((guide, index) => (
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
              {config.articles.map((art, index) => (
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
                      onChange={(e) => {
                        const updated = [...config.articles];
                        updated[index].title = e.target.value;
                        setConfig({ ...config, articles: updated });
                      }}
                      placeholder="Article Title..."
                    />
                    <input
                      className="input"
                      value={art.category}
                      onChange={(e) => {
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
                    onChange={(e) => {
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
              {config.ticket_categories.map((cat, index) => (
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
