import React, { useEffect, useState } from 'react';
import {
  adminSupabase,
  APP_GALLERY_KEY,
  LANDING_PAGE_KEY,
  RADYMATE_GALLERY_KEY,
  AppGalleryConfig,
  AppGalleryItem,
  LandingPageConfig,
  RadymateGalleryItem,
  DEFAULT_APP_GALLERY,
  DEFAULT_LANDING_PAGE_CONFIG,
  DEFAULT_RADYMATE_GALLERY,
  fetchAppGalleryConfig,
  saveAppGalleryConfig,
  fetchLandingPageConfig,
  saveLandingPageConfig,
  fetchRadymateGalleryConfig,
  saveRadymateGalleryConfig,
  uploadAppGalleryScreenshot,
  uploadRadymateGalleryImage,
} from '../adminSupabaseClient';
import {
  Sparkles,
  Smartphone,
  Image as ImageIcon,
  Sliders,
  ExternalLink,
  Save,
  RotateCcw,
  Plus,
  Trash2,
  ArrowUp,
  ArrowDown,
  Upload,
  CheckCircle2,
  AlertCircle,
  Eye,
  Link2,
  Download,
  Play,
  Layers,
  ChevronLeft,
  ChevronRight,
  TrendingUp,
  Check,
  X,
} from 'lucide-react';

export default function LandingPageControl() {
  const [activeTab, setActiveTab] = useState<'app_gallery' | 'hero_cta' | 'radymate' | 'metrics'>('app_gallery');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [statusMsg, setStatusMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // App Gallery state
  const [appGallery, setAppGallery] = useState<AppGalleryConfig>(DEFAULT_APP_GALLERY);
  const [editingCardId, setEditingCardId] = useState<string | null>(null);
  const [isUploadingScreenshot, setIsUploadingScreenshot] = useState<string | null>(null);
  const [previewScrollIndex, setPreviewScrollIndex] = useState(0);

  // Landing Page Hero & CTA state
  const [landingConfig, setLandingConfig] = useState<LandingPageConfig>(DEFAULT_LANDING_PAGE_CONFIG);

  // Radymate Gallery state
  const [radymateItems, setRadymateItems] = useState<RadymateGalleryItem[]>(DEFAULT_RADYMATE_GALLERY);
  const [radymateTitle, setRadymateTitle] = useState('');
  const [radymateCaption, setRadymateCaption] = useState('');
  const [radymateFile, setRadymateFile] = useState<File | null>(null);
  const [isUploadingRadymate, setIsUploadingRadymate] = useState(false);

  // 1. Initial Fetch
  useEffect(() => {
    let isMounted = true;
    (async () => {
      try {
        const [appGal, landing, rmd] = await Promise.all([
          fetchAppGalleryConfig(),
          fetchLandingPageConfig(),
          fetchRadymateGalleryConfig(),
        ]);
        if (isMounted) {
          setAppGallery(appGal);
          setLandingConfig(landing);
          setRadymateItems(rmd.items);
        }
      } catch (err: any) {
        console.warn('[LandingPageControl] Load warning:', err.message);
      } finally {
        if (isMounted) setLoading(false);
      }
    })();

    // Supabase Realtime channel subscription
    const channel = adminSupabase
      .channel('landing_page_control_sync')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'showcase_config' }, (payload) => {
        const key = payload.new?.key;
        const val = payload.new?.value;
        if (!val) return;
        if (key === APP_GALLERY_KEY && Array.isArray(val.items)) {
          setAppGallery(prev => ({ ...prev, ...val }));
        } else if (key === LANDING_PAGE_KEY) {
          setLandingConfig(prev => ({ ...prev, ...val }));
        } else if (key === RADYMATE_GALLERY_KEY && Array.isArray(val.items)) {
          setRadymateItems(val.items);
        }
      })
      .subscribe();

    return () => {
      isMounted = false;
      adminSupabase.removeChannel(channel);
    };
  }, []);

  // Helpers for Status Message
  const showFeedback = (type: 'success' | 'error', text: string) => {
    setStatusMsg({ type, text });
    setTimeout(() => {
      setStatusMsg(prev => (prev?.text === text ? null : prev));
    }, 5000);
  };

  // ── Save Handlers ──────────────────────────────────────────────────────────

  const handleSaveAppGallery = async () => {
    setSaving(true);
    try {
      await saveAppGalleryConfig(appGallery);
      showFeedback('success', '✅ 3D App Gallery saved and broadcasted to public landing page in real-time!');
    } catch (err: any) {
      showFeedback('error', '❌ Failed to save App Gallery: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleSaveHeroAndCTA = async () => {
    setSaving(true);
    try {
      await saveLandingPageConfig(landingConfig);
      showFeedback('success', '✅ Landing Page Hero, Download Links & Metrics synced successfully!');
    } catch (err: any) {
      showFeedback('error', '❌ Failed to save Landing Page config: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleSaveRadymateGallery = async (items: RadymateGalleryItem[]) => {
    setSaving(true);
    try {
      await saveRadymateGalleryConfig(items);
      setRadymateItems(items);
      showFeedback('success', '✅ Radymate storefront gallery updated and synced!');
    } catch (err: any) {
      showFeedback('error', '❌ Failed to save Radymate gallery: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // ── App Gallery Item Actions ───────────────────────────────────────────────

  const handleToggleCardActive = (id: string) => {
    setAppGallery(prev => ({
      ...prev,
      items: prev.items.map(item => (item.id === id ? { ...item, active: !item.active } : item)),
    }));
  };

  const handleMoveCard = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= appGallery.items.length) return;
    const newItems = [...appGallery.items];
    const temp = newItems[index];
    newItems[index] = newItems[targetIndex];
    newItems[targetIndex] = temp;
    setAppGallery(prev => ({ ...prev, items: newItems }));
  };

  const handleDeleteCard = (id: string) => {
    if (!window.confirm('Are you sure you want to remove this card from the App Showcase?')) return;
    setAppGallery(prev => ({
      ...prev,
      items: prev.items.filter(item => item.id !== id),
    }));
  };

  const handleAddCard = () => {
    const newId = 'card-' + Date.now().toString(36);
    const newCard: AppGalleryItem = {
      id: newId,
      badge: 'Feature',
      title: 'New App Feature',
      description: 'Highlight high-converting capability of SwapnoPay.',
      screen_bg: 'linear-gradient(135deg, #111827, #1e293b 55%, #0f172a)',
      widgets: [
        { label: 'Speed', value: '100%' },
        { label: 'Status', value: 'Live' },
        { label: 'Security', value: 'Bank' },
      ],
      active: true,
    };
    setAppGallery(prev => ({
      ...prev,
      items: [...prev.items, newCard],
    }));
    setEditingCardId(newId);
  };

  const handleCardFieldChange = (id: string, field: keyof AppGalleryItem, value: any) => {
    setAppGallery(prev => ({
      ...prev,
      items: prev.items.map(item => (item.id === id ? { ...item, [field]: value } : item)),
    }));
  };

  const handleWidgetChange = (cardId: string, widgetIndex: number, key: 'label' | 'value', val: string) => {
    setAppGallery(prev => ({
      ...prev,
      items: prev.items.map(item => {
        if (item.id !== cardId) return item;
        const newWidgets = [...(item.widgets || [{ label: '', value: '' }, { label: '', value: '' }, { label: '', value: '' }])];
        newWidgets[widgetIndex] = { ...newWidgets[widgetIndex], [key]: val };
        return { ...item, widgets: newWidgets };
      }),
    }));
  };

  const handleUploadScreenshot = async (cardId: string, file: File) => {
    setIsUploadingScreenshot(cardId);
    try {
      const url = await uploadAppGalleryScreenshot(file, 'app-showcase');
      handleCardFieldChange(cardId, 'image', url);
      showFeedback('success', '✅ Screenshot uploaded to Supabase Storage and attached to card!');
    } catch (err: any) {
      showFeedback('error', '❌ Screenshot upload failed: ' + err.message);
    } finally {
      setIsUploadingScreenshot(null);
    }
  };

  const handleResetAppGallery = async () => {
    if (!window.confirm('Reset all 3D App Gallery cards to the default showcase preset?')) return;
    setAppGallery(DEFAULT_APP_GALLERY);
    await saveAppGalleryConfig(DEFAULT_APP_GALLERY);
    showFeedback('success', '✅ App Gallery reset to default canonical showcase.');
  };

  // ── Radymate Actions ───────────────────────────────────────────────────────

  const handleAddRadymateItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!radymateFile) {
      alert('Please select an image to upload.');
      return;
    }
    setIsUploadingRadymate(true);
    try {
      const newItem = await uploadRadymateGalleryImage(
        radymateFile,
        radymateTitle || 'Radymate Storefront',
        radymateCaption || 'E-commerce showcase preview'
      );
      const updated = [newItem, ...radymateItems];
      await handleSaveRadymateGallery(updated);
      setRadymateTitle('');
      setRadymateCaption('');
      setRadymateFile(null);
    } catch (err: any) {
      showFeedback('error', '❌ Radymate upload error: ' + err.message);
    } finally {
      setIsUploadingRadymate(false);
    }
  };

  const handleDeleteRadymateItem = async (index: number) => {
    if (!window.confirm('Delete this Radymate storefront card?')) return;
    const updated = radymateItems.filter((_, i) => i !== index);
    await handleSaveRadymateGallery(updated);
  };

  const handleResetRadymate = async () => {
    if (!window.confirm('Reset Radymate storefront gallery to defaults?')) return;
    await handleSaveRadymateGallery(DEFAULT_RADYMATE_GALLERY);
  };

  if (loading) {
    return (
      <div className="container">
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <Smartphone size={32} style={{ margin: '0 auto 12px', opacity: 0.6 }} />
          <div>Loading Landing Page & App Showcase Configuration...</div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* ── Top Header ────────────────────────────────────────────────────────── */}
      <div className="header" style={{ marginBottom: 0 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              width: 38,
              height: 38,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #f59e0b, #d97706)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#000',
              fontWeight: 900
            }}>
              <Sparkles size={20} />
            </div>
            <div>
              <h1 style={{ margin: 0, fontSize: '1.45rem', fontWeight: 800 }}>
                Landing Page &amp; App Showcase Control
              </h1>
              <p style={{ margin: '3px 0 0', color: '#64748B', fontSize: 13 }}>
                Live visual management of the 3D App Showcase Carousel, hero copy, APK download buttons, and Radymate storefronts.
              </p>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <a
            href="/"
            target="_blank"
            rel="noopener noreferrer"
            className="button"
            style={{
              background: '#0F172A',
              color: '#F8FAFC',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              textDecoration: 'none',
              padding: '8px 14px',
              fontSize: 13,
            }}
          >
            <Eye size={15} color="#F59E0B" />
            <span>Open Landing Page</span>
            <ExternalLink size={13} style={{ opacity: 0.7 }} />
          </a>

          <button
            type="button"
            className="button"
            onClick={activeTab === 'app_gallery' ? handleSaveAppGallery : activeTab === 'radymate' ? () => handleSaveRadymateGallery(radymateItems) : handleSaveHeroAndCTA}
            disabled={saving}
            style={{
              background: '#10B981',
              color: '#FFFFFF',
              fontWeight: 700,
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '8px 18px',
              fontSize: 13,
            }}
          >
            <Save size={16} />
            {saving ? 'Saving to Supabase...' : 'Save All Changes'}
          </button>
        </div>
      </div>

      {/* ── Status Feedback Alert ────────────────────────────────────────────── */}
      {statusMsg && (
        <div
          style={{
            padding: '12px 16px',
            borderRadius: 10,
            background: statusMsg.type === 'success' ? '#ECFDF5' : '#FEF2F2',
            border: `1px solid ${statusMsg.type === 'success' ? '#A7F3D0' : '#FECACA'}`,
            color: statusMsg.type === 'success' ? '#065F46' : '#991B1B',
            fontWeight: 600,
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span>{statusMsg.text}</span>
          <button
            onClick={() => setStatusMsg(null)}
            style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'inherit' }}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* ── Navigation Tabs ─────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid #E2E8F0', paddingBottom: 10, flexWrap: 'wrap' }}>
        {[
          { key: 'app_gallery', label: `📱 3D App Showcase Carousel (${appGallery.items.length})` },
          { key: 'hero_cta', label: '⚡ Hero & APK Download Links' },
          { key: 'radymate', label: `🖼️ Radymate Storefronts (${radymateItems.length})` },
          { key: 'metrics', label: '📊 Platform Metrics & Uptime' },
        ].map(t => (
          <button
            key={t.key}
            onClick={() => setActiveTab(t.key as any)}
            className="button"
            style={{
              background: activeTab === t.key ? '#4F46E5' : '#F1F5F9',
              color: activeTab === t.key ? '#FFFFFF' : '#334155',
              fontWeight: 700,
              padding: '9px 16px',
              fontSize: 13,
              borderRadius: 8,
              border: activeTab === t.key ? 'none' : '1px solid #E2E8F0',
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* ── TAB 1: 3D APP SHOWCASE GALLERY ──────────────────────────────────── */}
      {activeTab === 'app_gallery' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          {/* Section Titles Settings */}
          <div className="card" style={{ padding: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#1E293B', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Sliders size={16} color="#4F46E5" />
                App Gallery Section Headers
              </h3>
              <button
                type="button"
                className="button"
                onClick={handleResetAppGallery}
                style={{ background: '#F1F5F9', color: '#64748B', fontSize: 12, padding: '5px 10px', display: 'flex', alignItems: 'center', gap: 4 }}
              >
                <RotateCcw size={12} /> Reset to Defaults
              </button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Section Heading
                </label>
                <input
                  className="input"
                  value={appGallery.section_title}
                  onChange={e => setAppGallery({ ...appGallery, section_title: e.target.value })}
                  placeholder="See SwapnoPay in action."
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Section Subheading / Tagline
                </label>
                <input
                  className="input"
                  value={appGallery.section_subtitle}
                  onChange={e => setAppGallery({ ...appGallery, section_subtitle: e.target.value })}
                  placeholder="Explore the app experience built for modern businesses."
                />
              </div>
            </div>
          </div>

          {/* Live Visual 3D Carousel Preview */}
          <div className="card" style={{ padding: 20, background: 'linear-gradient(135deg, #090B10, #121622)', border: '1px solid rgba(245, 158, 11, 0.3)', color: '#FFFFFF' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 800, color: '#F59E0B', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
                  Live 3D Preview (Interactive)
                </div>
                <h4 style={{ margin: '2px 0 0', fontSize: 17, fontWeight: 800, color: '#FFFFFF' }}>
                  {appGallery.section_title}
                </h4>
                <p style={{ margin: '2px 0 0', fontSize: 12, color: '#94A3B8' }}>
                  {appGallery.section_subtitle}
                </p>
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                <button
                  type="button"
                  onClick={() => setPreviewScrollIndex(prev => Math.max(0, prev - 1))}
                  disabled={previewScrollIndex === 0}
                  className="button"
                  style={{ background: 'rgba(255,255,255,0.1)', color: '#FFFFFF', padding: '6px 10px', borderRadius: 20 }}
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  type="button"
                  onClick={() => setPreviewScrollIndex(prev => Math.min(appGallery.items.length - 1, prev + 1))}
                  disabled={previewScrollIndex >= appGallery.items.length - 1}
                  className="button"
                  style={{ background: 'rgba(255,255,255,0.1)', color: '#FFFFFF', padding: '6px 10px', borderRadius: 20 }}
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>

            {/* Scrollable Track Container */}
            <div style={{
              display: 'flex',
              gap: 16,
              overflowX: 'auto',
              padding: '10px 4px 20px',
              scrollbarWidth: 'thin',
            }}>
              {appGallery.items.filter(item => item.active).map((item, idx) => (
                <div
                  key={item.id}
                  style={{
                    minWidth: 260,
                    maxWidth: 260,
                    borderRadius: 20,
                    background: item.highlight ? 'rgba(245, 158, 11, 0.08)' : 'rgba(15, 23, 42, 0.95)',
                    border: item.highlight ? '2px solid rgba(245, 158, 11, 0.6)' : '1px solid rgba(255,255,255,0.12)',
                    padding: 14,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    boxShadow: '0 12px 30px rgba(0,0,0,0.5)',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <div>
                    {/* Screen View */}
                    <div style={{
                      borderRadius: 14,
                      background: item.image ? '#0B0F19' : (item.screen_bg || 'linear-gradient(135deg, #111827, #1e293b)'),
                      height: 140,
                      overflow: 'hidden',
                      position: 'relative',
                      border: '1px solid rgba(255,255,255,0.08)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}>
                      {item.image ? (
                        <img
                          src={item.image}
                          alt={item.title}
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                        />
                      ) : (
                        <div style={{ display: 'flex', gap: 6, padding: '0 8px', width: '100%', justifyContent: 'center' }}>
                          {(item.widgets || []).map((w, wIdx) => (
                            <div
                              key={wIdx}
                              style={{
                                background: 'rgba(15,23,42,0.75)',
                                border: '1px solid rgba(255,255,255,0.1)',
                                borderRadius: 8,
                                padding: '6px 4px',
                                textAlign: 'center',
                                flex: 1,
                              }}
                            >
                              <strong style={{ display: 'block', fontSize: 12, color: '#F59E0B' }}>{w.value}</strong>
                              <span style={{ fontSize: 9, textTransform: 'uppercase', opacity: 0.7, color: '#94A3B8' }}>{w.label}</span>
                            </div>
                          ))}
                        </div>
                      )}
                      <div style={{
                        position: 'absolute',
                        top: 8,
                        left: 8,
                        padding: '2px 8px',
                        borderRadius: 12,
                        background: 'rgba(245, 158, 11, 0.9)',
                        color: '#000',
                        fontSize: 10,
                        fontWeight: 900,
                        textTransform: 'uppercase',
                      }}>
                        {item.badge}
                      </div>
                    </div>

                    <div style={{ marginTop: 12 }}>
                      <div style={{ fontWeight: 800, fontSize: 13, color: '#FFFFFF' }}>{item.title}</div>
                      <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 3, lineHeight: 1.4 }}>{item.description}</div>
                    </div>
                  </div>

                  <div style={{ marginTop: 12, paddingTop: 8, borderTop: '1px solid rgba(255,255,255,0.06)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 10, color: '#F59E0B', fontWeight: 700 }}>Card #{idx + 1}</span>
                    <button
                      type="button"
                      onClick={() => setEditingCardId(item.id)}
                      style={{ background: 'transparent', border: 'none', color: '#60A5FA', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}
                    >
                      Edit ✏️
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Cards Manager List */}
          <div className="card" style={{ padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#1E293B' }}>
                  Manage App Showcase Cards ({appGallery.items.length})
                </h3>
                <p style={{ margin: '2px 0 0', color: '#64748B', fontSize: 12 }}>
                  Drag or move cards, upload mobile screenshots directly, or customize labels and mini metrics.
                </p>
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="button"
                  onClick={handleAddCard}
                  className="button"
                  style={{ background: '#4F46E5', color: '#FFF', display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, fontSize: 13 }}
                >
                  <Plus size={15} /> Add Showcase Card
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {appGallery.items.map((card, index) => {
                const isExpanded = editingCardId === card.id;
                return (
                  <div
                    key={card.id}
                    style={{
                      border: `1px solid ${card.active ? '#CBD5E1' : '#E2E8F0'}`,
                      borderRadius: 12,
                      background: card.active ? '#FFFFFF' : '#F8FAFC',
                      padding: 16,
                      opacity: card.active ? 1 : 0.7,
                      transition: 'all 0.2s ease',
                    }}
                  >
                    {/* Card Summary Header */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        {/* Order Controls */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                          <button
                            type="button"
                            onClick={() => handleMoveCard(index, 'up')}
                            disabled={index === 0}
                            style={{ background: 'transparent', border: 'none', cursor: index === 0 ? 'default' : 'pointer', color: index === 0 ? '#CBD5E1' : '#475569', padding: 0 }}
                          >
                            <ArrowUp size={14} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleMoveCard(index, 'down')}
                            disabled={index === appGallery.items.length - 1}
                            style={{ background: 'transparent', border: 'none', cursor: index === appGallery.items.length - 1 ? 'default' : 'pointer', color: index === appGallery.items.length - 1 ? '#CBD5E1' : '#475569', padding: 0 }}
                          >
                            <ArrowDown size={14} />
                          </button>
                        </div>

                        {/* Thumbnail / Badge */}
                        <div style={{
                          width: 44,
                          height: 44,
                          borderRadius: 8,
                          background: card.image ? '#0F172A' : '#4F46E5',
                          overflow: 'hidden',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#FFF',
                          fontWeight: 800,
                          fontSize: 12,
                        }}>
                          {card.image ? (
                            <img src={card.image} alt={card.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                            card.badge.slice(0, 3)
                          )}
                        </div>

                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <strong style={{ fontSize: 14, color: '#1E293B' }}>{card.title || 'Untitled Card'}</strong>
                            <span style={{
                              background: '#FEF3C7',
                              color: '#B45309',
                              padding: '2px 8px',
                              borderRadius: 6,
                              fontSize: 11,
                              fontWeight: 700,
                            }}>
                              {card.badge}
                            </span>
                            {card.highlight && (
                              <span style={{
                                background: '#ECFDF5',
                                color: '#059669',
                                padding: '2px 6px',
                                borderRadius: 6,
                                fontSize: 10,
                                fontWeight: 700,
                              }}>
                                ★ POPULAR
                              </span>
                            )}
                          </div>
                          <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>{card.description}</div>
                        </div>
                      </div>

                      {/* Header Actions */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: '#475569', cursor: 'pointer' }}>
                          <input
                            type="checkbox"
                            checked={card.active}
                            onChange={() => handleToggleCardActive(card.id)}
                          />
                          Active
                        </label>

                        <button
                          type="button"
                          onClick={() => setEditingCardId(isExpanded ? null : card.id)}
                          className="button"
                          style={{
                            background: isExpanded ? '#4F46E5' : '#F1F5F9',
                            color: isExpanded ? '#FFF' : '#1E293B',
                            fontSize: 12,
                            padding: '5px 12px',
                          }}
                        >
                          {isExpanded ? 'Collapse ▲' : 'Edit Details ▼'}
                        </button>

                        <button
                          type="button"
                          onClick={() => handleDeleteCard(card.id)}
                          style={{
                            background: '#FEE2E2',
                            color: '#DC2626',
                            border: 'none',
                            borderRadius: 6,
                            padding: '6px 10px',
                            cursor: 'pointer',
                          }}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>

                    {/* Expanded Edit Form */}
                    {isExpanded && (
                      <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #E2E8F0', display: 'flex', flexDirection: 'column', gap: 14 }}>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr', gap: 12 }}>
                          <div>
                            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                              Badge Label *
                            </label>
                            <input
                              className="input"
                              value={card.badge}
                              onChange={e => handleCardFieldChange(card.id, 'badge', e.target.value)}
                              placeholder="e.g. Dashboard"
                            />
                          </div>
                          <div>
                            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                              Card Title *
                            </label>
                            <input
                              className="input"
                              value={card.title}
                              onChange={e => handleCardFieldChange(card.id, 'title', e.target.value)}
                              placeholder="e.g. Executive Dashboard"
                            />
                          </div>
                          <div style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 6 }}>
                            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 700, color: '#D97706', cursor: 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={card.highlight || false}
                                onChange={e => handleCardFieldChange(card.id, 'highlight', e.target.checked)}
                              />
                              Highlight as Featured
                            </label>
                          </div>
                        </div>

                        <div>
                          <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                            Description *
                          </label>
                          <input
                            className="input"
                            value={card.description}
                            onChange={e => handleCardFieldChange(card.id, 'description', e.target.value)}
                            placeholder="e.g. Instant overview of sales, profit, and merchant health."
                          />
                        </div>

                        {/* Image Upload & URL */}
                        <div style={{ background: '#F8FAFC', padding: 14, borderRadius: 10, border: '1px solid #E2E8F0' }}>
                          <label style={{ display: 'block', fontSize: 12, fontWeight: 800, marginBottom: 8, color: '#1E293B' }}>
                            Card Visual Screenshot (Supabase Storage: radymate-gallery)
                          </label>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, alignItems: 'center' }}>
                            <div>
                              <label style={{ display: 'block', fontSize: 11, color: '#64748B', marginBottom: 4 }}>
                                Paste Direct Image URL (HTTPS)
                              </label>
                              <input
                                className="input"
                                value={card.image || ''}
                                onChange={e => handleCardFieldChange(card.id, 'image', e.target.value)}
                                placeholder="https://... / screenshot.jpg"
                              />
                            </div>

                            <div>
                              <label style={{ display: 'block', fontSize: 11, color: '#64748B', marginBottom: 4 }}>
                                OR Upload Screenshot from Device
                              </label>
                              <input
                                type="file"
                                accept="image/*"
                                className="input"
                                disabled={isUploadingScreenshot === card.id}
                                onChange={e => {
                                  const file = e.target.files?.[0];
                                  if (file) handleUploadScreenshot(card.id, file);
                                }}
                              />
                              {isUploadingScreenshot === card.id && (
                                <span style={{ fontSize: 11, color: '#4F46E5', marginTop: 4, display: 'block' }}>
                                  Uploading to Supabase storage...
                                </span>
                              )}
                            </div>
                          </div>

                          {card.image && (
                            <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 10 }}>
                              <img src={card.image} alt="Preview" style={{ height: 48, borderRadius: 6, objectFit: 'cover' }} />
                              <button
                                type="button"
                                onClick={() => handleCardFieldChange(card.id, 'image', '')}
                                style={{ background: 'transparent', border: 'none', color: '#EF4444', fontSize: 11, cursor: 'pointer' }}
                              >
                                Remove Image (Use Mini Metric Mockup)
                              </button>
                            </div>
                          )}
                        </div>

                        {/* Mini Widgets (Used when no screenshot is uploaded) */}
                        {!card.image && (
                          <div style={{ background: '#EEF2FF', padding: 14, borderRadius: 10, border: '1px solid #C7D2FE' }}>
                            <label style={{ display: 'block', fontSize: 12, fontWeight: 800, marginBottom: 8, color: '#4338CA' }}>
                              3-Pill Mockup Metrics (Shown in Card Screen)
                            </label>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
                              {[0, 1, 2].map(wIdx => {
                                const w = (card.widgets && card.widgets[wIdx]) || { label: '', value: '' };
                                return (
                                  <div key={wIdx} style={{ background: '#FFF', padding: 8, borderRadius: 8, border: '1px solid #CBD5E1' }}>
                                    <div style={{ fontSize: 10, fontWeight: 700, color: '#64748B', marginBottom: 2 }}>
                                      Pill #{wIdx + 1}
                                    </div>
                                    <input
                                      className="input"
                                      style={{ marginBottom: 4, padding: '4px 8px', fontSize: 12 }}
                                      value={w.value}
                                      onChange={e => handleWidgetChange(card.id, wIdx, 'value', e.target.value)}
                                      placeholder="Value (e.g. ৳ 28K)"
                                    />
                                    <input
                                      className="input"
                                      style={{ padding: '4px 8px', fontSize: 11 }}
                                      value={w.label}
                                      onChange={e => handleWidgetChange(card.id, wIdx, 'label', e.target.value)}
                                      placeholder="Label (e.g. Sales)"
                                    />
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 2: HERO & DOWNLOAD LINKS ────────────────────────────────────── */}
      {activeTab === 'hero_cta' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div className="card" style={{ padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#1E293B' }}>
                  ⚡ Landing Page Hero &amp; Announcement Banner
                </h3>
                <p style={{ margin: '2px 0 0', color: '#64748B', fontSize: 12 }}>
                  Directly customize the top announcement badge, hero headline, and marketing copy in real-time.
                </p>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleSaveHeroAndCTA}
                disabled={saving}
                style={{ background: '#10B981', color: '#FFF', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }}
              >
                <Save size={15} /> Save Hero Settings
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 16 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Top Announcement Tag
                </label>
                <input
                  className="input"
                  value={landingConfig.announcement_badge}
                  onChange={e => setLandingConfig({ ...landingConfig, announcement_badge: e.target.value })}
                  placeholder="NEXT-GEN BANGLADESH PAYMENT PLATFORM"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Announcement Banner Text
                </label>
                <input
                  className="input"
                  value={landingConfig.announcement_text}
                  onChange={e => setLandingConfig({ ...landingConfig, announcement_text: e.target.value })}
                  placeholder="PAYMENTS FOR BANGLADESH"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Hero Headline Prefix
                </label>
                <input
                  className="input"
                  value={landingConfig.hero_title}
                  onChange={e => setLandingConfig({ ...landingConfig, hero_title: e.target.value })}
                  placeholder="Payments."
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Hero Highlighted Word (Gold Gradient)
                </label>
                <input
                  className="input"
                  value={landingConfig.hero_highlight}
                  onChange={e => setLandingConfig({ ...landingConfig, hero_highlight: e.target.value })}
                  placeholder="Reimagined."
                />
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Hero Paragraph / Marketing Description
                </label>
                <textarea
                  className="input"
                  rows={3}
                  value={landingConfig.hero_subtitle}
                  onChange={e => setLandingConfig({ ...landingConfig, hero_subtitle: e.target.value })}
                  placeholder="Payment gateway automation, POS billing, inventory and digital ledger for businesses in Bangladesh..."
                  style={{ fontFamily: 'inherit' }}
                />
              </div>
            </div>
          </div>

          {/* Download & Action Buttons */}
          <div className="card" style={{ padding: 20 }}>
            <h3 style={{ margin: '0 0 14px', fontSize: 15, fontWeight: 800, color: '#1E293B', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Download size={16} color="#F59E0B" />
              Download URLs &amp; Action CTA Links
            </h3>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Android APK Direct Download URL *
                </label>
                <input
                  className="input"
                  value={landingConfig.apk_download_url}
                  onChange={e => setLandingConfig({ ...landingConfig, apk_download_url: e.target.value })}
                  placeholder="/swapnopay-debug.apk or hosted CDN URL"
                />
                <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 4 }}>
                  Default is <code>/swapnopay-debug.apk</code> or any external HTTPS link.
                </span>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Google Play Store App URL
                </label>
                <input
                  className="input"
                  value={landingConfig.play_store_url}
                  onChange={e => setLandingConfig({ ...landingConfig, play_store_url: e.target.value })}
                  placeholder="https://play.google.com/store/apps/details?id=com.example.lenden23"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Web Merchant Portal URL
                </label>
                <input
                  className="input"
                  value={landingConfig.web_portal_url}
                  onChange={e => setLandingConfig({ ...landingConfig, web_portal_url: e.target.value })}
                  placeholder="https://pay.swapnopay.top/portal.html"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Developer API Documentation URL
                </label>
                <input
                  className="input"
                  value={landingConfig.docs_url}
                  onChange={e => setLandingConfig({ ...landingConfig, docs_url: e.target.value })}
                  placeholder="https://pay.swapnopay.top/docs.html"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Hero Video Tutorial URL (Modal)
                </label>
                <input
                  className="input"
                  value={landingConfig.demo_video_url}
                  onChange={e => setLandingConfig({ ...landingConfig, demo_video_url: e.target.value })}
                  placeholder="https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Security &amp; Version Badge
                </label>
                <input
                  className="input"
                  value={landingConfig.status_text}
                  onChange={e => setLandingConfig({ ...landingConfig, status_text: e.target.value })}
                  placeholder="Android Version 1.0.0 • Offline Ready & Bank-Grade Encrypted"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 3: RADYMATE STOREFRONT GALLERY ───────────────────────────────── */}
      {activeTab === 'radymate' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          {/* Add New Radymate Item */}
          <div className="card" style={{ padding: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#1E293B' }}>
                  🖼️ Radymate E-Commerce Storefront Gallery
                </h3>
                <p style={{ margin: '2px 0 0', color: '#64748B', fontSize: 12 }}>
                  Storefront preview photos uploaded directly to the <strong>radymate-gallery</strong> Supabase storage bucket.
                </p>
              </div>
              <button
                type="button"
                className="button"
                onClick={handleResetRadymate}
                style={{ background: '#F1F5F9', color: '#64748B', fontSize: 12, padding: '5px 10px', display: 'flex', alignItems: 'center', gap: 4 }}
              >
                <RotateCcw size={12} /> Reset to Defaults
              </button>
            </div>

            <form onSubmit={handleAddRadymateItem} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: 12, alignItems: 'flex-end' }}>
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Storefront Title
                </label>
                <input
                  className="input"
                  value={radymateTitle}
                  onChange={e => setRadymateTitle(e.target.value)}
                  placeholder="e.g. Modern Fashion Store"
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Storefront Caption / Subtitle
                </label>
                <input
                  className="input"
                  value={radymateCaption}
                  onChange={e => setRadymateCaption(e.target.value)}
                  placeholder="e.g. One-click mobile conversion"
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4, color: '#475569' }}>
                  Upload Screenshot Image
                </label>
                <input
                  type="file"
                  accept="image/*"
                  className="input"
                  onChange={e => setRadymateFile(e.target.files?.[0] || null)}
                  required
                />
              </div>

              <button
                type="submit"
                className="button"
                disabled={isUploadingRadymate || !radymateFile}
                style={{ background: '#10B981', color: '#FFF', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }}
              >
                <Upload size={15} />
                {isUploadingRadymate ? 'Uploading...' : 'Upload & Save'}
              </button>
            </form>
          </div>

          {/* Radymate Items Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
            {radymateItems.map((item, index) => (
              <div
                key={index}
                className="card"
                style={{ padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
              >
                <div style={{ height: 160, background: '#0F172A', position: 'relative' }}>
                  <img
                    src={item.image}
                    alt={item.title}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                  <div style={{
                    position: 'absolute',
                    top: 8,
                    left: 8,
                    padding: '2px 8px',
                    borderRadius: 12,
                    background: 'rgba(15,23,42,0.85)',
                    color: item.source === 'storage' ? '#10B981' : '#F59E0B',
                    fontSize: 10,
                    fontWeight: 800,
                  }}>
                    {item.source === 'storage' ? 'Supabase Storage' : 'Default Preset'}
                  </div>
                </div>

                <div style={{ padding: 14, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                  <div>
                    <strong style={{ fontSize: 13, color: '#1E293B', display: 'block' }}>{item.title}</strong>
                    <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 2 }}>{item.caption}</span>
                  </div>

                  <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                    <button
                      type="button"
                      onClick={() => handleDeleteRadymateItem(index)}
                      style={{
                        background: '#FEE2E2',
                        color: '#EF4444',
                        border: 'none',
                        padding: '4px 10px',
                        borderRadius: 6,
                        cursor: 'pointer',
                        fontSize: 11,
                        fontWeight: 700,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <Trash2 size={12} /> Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── TAB 4: METRICS & UPTIME ─────────────────────────────────────────── */}
      {activeTab === 'metrics' && (
        <div className="card" style={{ padding: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div>
              <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#1E293B' }}>
                📊 Platform Metrics &amp; Live Counter Badges
              </h3>
              <p style={{ margin: '2px 0 0', color: '#64748B', fontSize: 12 }}>
                Update key trust statistics displayed across the landing page and footer.
              </p>
            </div>
            <button
              type="button"
              className="button"
              onClick={handleSaveHeroAndCTA}
              disabled={saving}
              style={{ background: '#10B981', color: '#FFF', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }}
            >
              <Save size={15} /> Save Metrics
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
            <div style={{ background: '#EEF2FF', padding: 16, borderRadius: 12, border: '1px solid #C7D2FE' }}>
              <div style={{ fontSize: 11, fontWeight: 800, color: '#4338CA', textTransform: 'uppercase', marginBottom: 4 }}>
                Settlement Speed
              </div>
              <input
                className="input"
                value={landingConfig.metric_settlement}
                onChange={e => setLandingConfig({ ...landingConfig, metric_settlement: e.target.value })}
                placeholder="2.4s"
              />
              <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 4 }}>
                Shown on floating cards &amp; hero pills.
              </span>
            </div>

            <div style={{ background: '#ECFDF5', padding: 16, borderRadius: 12, border: '1px solid #A7F3D0' }}>
              <div style={{ fontSize: 11, fontWeight: 800, color: '#065F46', textTransform: 'uppercase', marginBottom: 4 }}>
                SMS Match Rate
              </div>
              <input
                className="input"
                value={landingConfig.metric_match_rate}
                onChange={e => setLandingConfig({ ...landingConfig, metric_match_rate: e.target.value })}
                placeholder="99.8%"
              />
              <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 4 }}>
                Displayed in accuracy comparisons.
              </span>
            </div>

            <div style={{ background: '#FEF3C7', padding: 16, borderRadius: 12, border: '1px solid #FDE68A' }}>
              <div style={{ fontSize: 11, fontWeight: 800, color: '#B45309', textTransform: 'uppercase', marginBottom: 4 }}>
                Platform Uptime
              </div>
              <input
                className="input"
                value={landingConfig.metric_uptime}
                onChange={e => setLandingConfig({ ...landingConfig, metric_uptime: e.target.value })}
                placeholder="99.99%"
              />
              <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 4 }}>
                Displayed in enterprise trust section.
              </span>
            </div>

            <div style={{ background: '#F1F5F9', padding: 16, borderRadius: 12, border: '1px solid #CBD5E1' }}>
              <div style={{ fontSize: 11, fontWeight: 800, color: '#1E293B', textTransform: 'uppercase', marginBottom: 4 }}>
                Active Merchants
              </div>
              <input
                className="input"
                value={landingConfig.metric_merchants}
                onChange={e => setLandingConfig({ ...landingConfig, metric_merchants: e.target.value })}
                placeholder="12,400+"
              />
              <span style={{ fontSize: 11, color: '#64748B', display: 'block', marginTop: 4 }}>
                Platform network size.
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
