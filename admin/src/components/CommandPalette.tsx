import React, { useEffect, useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Search,
  LayoutDashboard,
  Users,
  ShieldCheck,
  CreditCard,
  BarChart3,
  FileText,
  HelpCircle,
  Activity,
  Database,
  Settings,
  Sun,
  Moon,
  PlusCircle,
  ArrowRight,
  Sparkles,
  Megaphone
} from 'lucide-react'

interface CommandPaletteProps {
  isOpen: boolean
  onClose: () => void
  onOpenAddMerchant?: () => void
}

interface CommandItem {
  id: string
  title: string
  subtitle?: string
  category: 'Pages' | 'Actions' | 'Settings'
  icon: React.ReactNode
  action: () => void
}

export default function CommandPalette({ isOpen, onClose, onOpenAddMerchant }: CommandPaletteProps) {
  const [query, setQuery] = useState('')
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    if (isOpen) {
      setQuery('')
      setSelectedIndex(0)
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [isOpen])

  // Toggle Theme helper
  const toggleTheme = () => {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
    if (isDark) {
      document.documentElement.removeAttribute('data-theme')
      localStorage.setItem('swapnopay_admin_theme', 'light')
    } else {
      document.documentElement.setAttribute('data-theme', 'dark')
      localStorage.setItem('swapnopay_admin_theme', 'dark')
    }
    onClose()
  }

  const allItems: CommandItem[] = [
    {
      id: 'page-dashboard',
      title: 'Dashboard Overview',
      subtitle: 'Platform overview, GMV metrics, and recent events',
      category: 'Pages',
      icon: <LayoutDashboard size={16} />,
      action: () => { navigate('/dashboard'); onClose(); }
    },
    {
      id: 'page-merchants',
      title: 'Merchants & Stores',
      subtitle: 'View and manage registered store owners and databases',
      category: 'Pages',
      icon: <Users size={16} />,
      action: () => { navigate('/merchants'); onClose(); }
    },
    {
      id: 'page-broadcasts',
      title: 'Broadcast Notifications',
      subtitle: 'Dispatch announcements and alerts directly to merchant apps',
      category: 'Pages',
      icon: <Megaphone size={16} />,
      action: () => { navigate('/broadcasts'); onClose(); }
    },
    {
      id: 'page-kyc',
      title: 'KYC Reviews & Verifications',
      subtitle: 'Inspect submitted NID documents and approve merchants',
      category: 'Pages',
      icon: <ShieldCheck size={16} />,
      action: () => { navigate('/kyc-reviews'); onClose(); }
    },
    {
      id: 'page-gateway',
      title: 'Payment Gateway Configuration',
      subtitle: 'Manage bKash, Nagad, Rocket, Upay kill-switches and numbers',
      category: 'Pages',
      icon: <CreditCard size={16} />,
      action: () => { navigate('/gateway-settings'); onClose(); }
    },
    {
      id: 'page-analytics',
      title: 'Transactions & Analytics',
      subtitle: 'Real-time payment transaction ledger and conversion charts',
      category: 'Pages',
      icon: <BarChart3 size={16} />,
      action: () => { navigate('/analytics'); onClose(); }
    },
    {
      id: 'page-submissions',
      title: 'Form Submissions',
      subtitle: 'Audit customer form checkouts and payments',
      category: 'Pages',
      icon: <FileText size={16} />,
      action: () => { navigate('/submissions'); onClose(); }
    },
    {
      id: 'page-health',
      title: 'System Health & Latency',
      subtitle: 'Check central Supabase database latency and gateway status',
      category: 'Pages',
      icon: <Activity size={16} />,
      action: () => { navigate('/health'); onClose(); }
    },
    {
      id: 'page-connect-supabase',
      title: 'Connect Central Supabase',
      subtitle: 'Configure platform database keys and pooler URIs',
      category: 'Pages',
      icon: <Database size={16} />,
      action: () => { navigate('/connect-supabase'); onClose(); }
    },
    {
      id: 'page-support',
      title: 'Support Helpdesk & Tickets',
      subtitle: 'Resolve merchant support requests and notifications',
      category: 'Pages',
      icon: <HelpCircle size={16} />,
      action: () => { navigate('/support'); onClose(); }
    },
    {
      id: 'page-settings',
      title: 'System CMS & Remote Config',
      subtitle: 'Update app version policies, docs URLs, and announcement banners',
      category: 'Pages',
      icon: <Settings size={16} />,
      action: () => { navigate('/settings'); onClose(); }
    },
    {
      id: 'action-add-merchant',
      title: 'Create New Merchant Account',
      subtitle: 'Manually register a merchant with custom Supabase database',
      category: 'Actions',
      icon: <PlusCircle size={16} />,
      action: () => {
        onClose()
        if (onOpenAddMerchant) onOpenAddMerchant()
        else navigate('/merchants')
      }
    },
    {
      id: 'action-theme-toggle',
      title: 'Toggle Theme (Light / Dark)',
      subtitle: 'Switch between minimal neutral light and deep dark palettes',
      category: 'Settings',
      icon: <Sparkles size={16} />,
      action: toggleTheme
    }
  ]

  const filteredItems = allItems.filter(item => {
    if (!query.trim()) return true
    const q = query.toLowerCase()
    return item.title.toLowerCase().includes(q) || (item.subtitle && item.subtitle.toLowerCase().includes(q))
  })

  // Keyboard navigation inside palette
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      onClose()
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex(prev => (prev + 1) % Math.max(1, filteredItems.length))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex(prev => (prev - 1 + filteredItems.length) % Math.max(1, filteredItems.length))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      if (filteredItems[selectedIndex]) {
        filteredItems[selectedIndex].action()
      }
    }
  }

  if (!isOpen) return null

  return (
    <div className="command-palette-backdrop" onClick={onClose}>
      <div className="command-palette-modal" onClick={e => e.stopPropagation()}>
        {/* Search Input Bar */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '14px 18px',
          borderBottom: '1px solid var(--border-default)',
          background: 'var(--bg-surface)'
        }}>
          <Search size={18} color="var(--text-muted)" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={e => { setQuery(e.target.value); setSelectedIndex(0); }}
            onKeyDown={handleKeyDown}
            placeholder="Type a command or search pages, actions, settings..."
            style={{
              flex: 1,
              border: 'none',
              outline: 'none',
              background: 'transparent',
              fontSize: 14,
              fontFamily: 'inherit',
              color: 'var(--text-primary)'
            }}
          />
          <kbd className="search-kbd" style={{ fontSize: 10 }}>ESC to close</kbd>
        </div>

        {/* Results List */}
        <div style={{ maxHeight: 360, overflowY: 'auto', padding: '8px' }}>
          {filteredItems.length === 0 ? (
            <div style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              No commands found for "{query}"
            </div>
          ) : (
            filteredItems.map((item, index) => {
              const isSelected = index === selectedIndex
              return (
                <div
                  key={item.id}
                  onClick={item.action}
                  onMouseEnter={() => setSelectedIndex(index)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-sm)',
                    background: isSelected ? 'var(--brand-subtle)' : 'transparent',
                    color: isSelected ? 'var(--brand-primary)' : 'var(--text-primary)',
                    cursor: 'pointer',
                    transition: 'all 120ms ease'
                  }}
                >
                  <div style={{
                    width: 28,
                    height: 28,
                    borderRadius: 'var(--radius-xs)',
                    background: isSelected ? 'var(--brand-primary)' : 'var(--bg-subtle)',
                    color: isSelected ? 'white' : 'var(--text-secondary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    {item.icon}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, lineHeight: 1.3 }}>
                      {item.title}
                    </div>
                    {item.subtitle && (
                      <div style={{ fontSize: 11.5, color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {item.subtitle}
                      </div>
                    )}
                  </div>
                  {isSelected && (
                    <ArrowRight size={14} color="var(--brand-primary)" style={{ opacity: 0.8 }} />
                  )}
                </div>
              )
            })
          )}
        </div>

        {/* Footer info */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 16px',
          background: 'var(--bg-subtle)',
          borderTop: '1px solid var(--border-subtle)',
          fontSize: 11,
          color: 'var(--text-muted)'
        }}>
          <div style={{ display: 'flex', gap: 14 }}>
            <span><kbd className="search-kbd">↑</kbd> <kbd className="search-kbd">↓</kbd> to navigate</span>
            <span><kbd className="search-kbd">↵</kbd> to select</span>
          </div>
          <span>SwapnoPay Central Console</span>
        </div>
      </div>
    </div>
  )
}

