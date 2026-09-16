import React, { useState, useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { useNotifications } from './ToastProvider'
import CommandPalette from './CommandPalette'
import AddMerchantModal from './AddMerchantModal'
import {
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
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  Sun,
  Moon,
  Search,
  Bell,
  Plus,
  LogOut,
  ExternalLink,
  ChevronDown,
  Layers
} from 'lucide-react'

interface AdminLayoutProps {
  children: React.ReactNode
}

export default function AdminLayout({ children }: AdminLayoutProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, signOut } = useAuth()
  const { unreadCount, notifications } = useNotifications()

  // Layout state
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(() => {
    return localStorage.getItem('swapnopay_sidebar_collapsed') === 'true'
  })
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false)
  const [isAddMerchantOpen, setIsAddMerchantOpen] = useState(false)
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false)
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [quickActionOpen, setQuickActionOpen] = useState(false)
  const [isDarkMode, setIsDarkMode] = useState<boolean>(() => {
    return (
      localStorage.getItem('swapnopay_admin_theme') === 'dark' ||
      (!localStorage.getItem('swapnopay_admin_theme') && window.matchMedia('(prefers-color-scheme: dark)').matches)
    )
  })

  // Apply dark mode attribute
  useEffect(() => {
    if (isDarkMode) {
      document.documentElement.setAttribute('data-theme', 'dark')
      localStorage.setItem('swapnopay_admin_theme', 'dark')
    } else {
      document.documentElement.removeAttribute('data-theme')
      localStorage.setItem('swapnopay_admin_theme', 'light')
    }
  }, [isDarkMode])

  // Save sidebar collapse state
  const toggleSidebar = () => {
    setIsSidebarCollapsed(prev => {
      const next = !prev
      localStorage.setItem('swapnopay_sidebar_collapsed', String(next))
      return next
    })
  }

  // Keyboard shortcut for Command Palette (Cmd+K / Ctrl+K) and Sidebar ([)
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setIsCommandPaletteOpen(prev => !prev)
      } else if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b') {
        e.preventDefault()
        toggleSidebar()
      }
    }
    window.addEventListener('keydown', handleGlobalKeyDown)
    return () => window.removeEventListener('keydown', handleGlobalKeyDown)
  }, [])

  // Navigation Groups (Linear structure)
  const navGroups = [
    {
      group: 'Core',
      items: [
        {
          to: '/dashboard',
          label: 'Dashboard',
          icon: <LayoutDashboard size={17} />
        },
        {
          to: '/merchants',
          label: 'Merchants',
          icon: <Users size={17} />
        },
        {
          to: '/kyc-reviews',
          label: 'KYC Reviews',
          icon: <ShieldCheck size={17} />,
          badge: unreadCount > 0 ? unreadCount : undefined,
          badgeAlert: true
        },
        {
          to: '/analytics',
          label: 'Transactions',
          icon: <BarChart3 size={17} />
        }
      ]
    },
    {
      group: 'Gateway & Rules',
      items: [
        {
          to: '/gateway-settings',
          label: 'Payment Gateway',
          icon: <CreditCard size={17} />
        },
        {
          to: '/mfs-patterns',
          label: 'MFS Regex Rules',
          icon: <SlidersHorizontal size={17} />
        },
        {
          to: '/submissions',
          label: 'Submissions',
          icon: <FileText size={17} />
        }
      ]
    },
    {
      group: 'System & Support',
      items: [
        {
          to: '/support',
          label: 'Support Tickets',
          icon: <HelpCircle size={17} />,
          badge: unreadCount > 0 ? unreadCount : undefined
        },
        {
          to: '/health',
          label: 'System Health',
          icon: <Activity size={17} />
        },
        {
          to: '/connect-supabase',
          label: 'Connect Supabase',
          icon: <Database size={17} />
        },
        {
          to: '/settings',
          label: 'CMS & Settings',
          icon: <Settings size={17} />
        }
      ]
    }
  ]

  // Compute breadcrumbs
  const getBreadcrumbs = () => {
    const path = location.pathname
    if (path === '/dashboard') return ['Platform', 'Dashboard']
    if (path.startsWith('/merchants')) return ['Platform', 'Merchants', path.split('/')[2] ? 'Detail' : 'Catalog']
    if (path === '/kyc-reviews') return ['Compliance', 'KYC Reviews']
    if (path === '/gateway-settings') return ['Gateway', 'Payment Config']
    if (path === '/analytics') return ['Analytics', 'Transactions Ledger']
    if (path === '/submissions') return ['Gateway', 'Form Submissions']
    if (path === '/mfs-patterns') return ['Engine', 'MFS Regex Patterns']
    if (path === '/support') return ['Helpdesk', 'Support Tickets']
    if (path === '/health') return ['Monitoring', 'System Health & Latency']
    if (path === '/connect-supabase') return ['Infrastructure', 'Central Supabase']
    if (path === '/settings') return ['Configuration', 'System CMS']
    return ['Platform', 'Console']
  }

  const breadcrumbs = getBreadcrumbs()

  return (
    <div className="app-layout">
      {/* ──────────────── Collapsible Sidebar (Linear Style) ──────────────── */}
      <aside className={`app-sidebar ${isSidebarCollapsed ? 'collapsed' : ''}`}>
        {/* Workspace Selector */}
        <div
          className="sidebar-workspace"
          title={isSidebarCollapsed ? 'SwapnoPay Central Platform' : undefined}
          onClick={() => navigate('/dashboard')}
        >
          <div className="workspace-logo-box">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M12 2L15 9L22 12L15 15L12 22L9 15L2 12L9 9L12 2Z" fill="white" />
            </svg>
          </div>
          {!isSidebarCollapsed && (
            <div className="workspace-info">
              <div className="workspace-title">SwapnoPay Central</div>
              <div className="workspace-env-badge">
                <span className="workspace-env-dot" />
                Live Production
              </div>
            </div>
          )}
        </div>

        {/* Grouped Nav List */}
        <nav className="sidebar-nav-container">
          {navGroups.map((group, gIdx) => (
            <div key={gIdx}>
              {!isSidebarCollapsed && (
                <div className="nav-group-label">{group.group}</div>
              )}
              <ul className="nav-list">
                {group.items.map((item, iIdx) => {
                  const isActive =
                    location.pathname === item.to ||
                    (item.to !== '/dashboard' && location.pathname.startsWith(item.to))
                  return (
                    <li key={iIdx}>
                      <Link
                        to={item.to}
                        className={`nav-item-link ${isActive ? 'active' : ''}`}
                        title={isSidebarCollapsed ? item.label : undefined}
                      >
                        <span style={{ display: 'flex', alignItems: 'center', opacity: isActive ? 1 : 0.8 }}>
                          {item.icon}
                        </span>
                        {!isSidebarCollapsed && <span>{item.label}</span>}
                        {!isSidebarCollapsed && item.badge !== undefined && (
                          <span className={`nav-badge ${item.badgeAlert ? 'alert' : ''}`}>
                            {item.badge}
                          </span>
                        )}
                      </Link>
                    </li>
                  )
                })}
              </ul>
            </div>
          ))}
        </nav>

        {/* Sidebar Footer */}
        <div className="sidebar-footer">
          {/* Quick theme toggle & collapse buttons */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: isSidebarCollapsed ? 'center' : 'space-between',
            padding: '2px 4px'
          }}>
            <button
              onClick={() => setIsDarkMode(!isDarkMode)}
              className="btn-icon btn-ghost"
              title={isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            >
              {isDarkMode ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <button
              onClick={toggleSidebar}
              className="btn-icon btn-ghost"
              title={isSidebarCollapsed ? 'Expand sidebar (Ctrl+B)' : 'Collapse sidebar (Ctrl+B)'}
            >
              {isSidebarCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
            </button>
          </div>

          {/* Admin User Profile Pill */}
          {!isSidebarCollapsed ? (
            <div
              onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '6px 8px',
                borderRadius: 'var(--radius-sm)',
                cursor: 'pointer',
                background: profileDropdownOpen ? 'var(--bg-subtle)' : 'transparent',
                transition: 'background var(--transition-fast)'
              }}
            >
              <div style={{
                width: 32,
                height: 32,
                borderRadius: 'var(--radius-sm)',
                background: 'var(--brand-primary)',
                color: 'white',
                fontWeight: 700,
                fontSize: 13,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}>
                A
              </div>
              <div style={{ flex: 1, minWidth: 0, textAlign: 'left' }}>
                <div style={{ fontSize: 12.5, fontWeight: 700, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  Platform Admin
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {user?.email || 'admin@swapnopay.top'}
                </div>
              </div>
              <ChevronDown size={14} color="var(--text-muted)" />
            </div>
          ) : (
            <div
              onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
              title={user?.email || 'Platform Admin'}
              style={{
                width: 34,
                height: 34,
                borderRadius: 'var(--radius-sm)',
                background: 'var(--brand-primary)',
                color: 'white',
                fontWeight: 700,
                fontSize: 13,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto',
                cursor: 'pointer'
              }}
            >
              A
            </div>
          )}
        </div>
      </aside>

      {/* ──────────────── Main Content Area ──────────────── */}
      <div className="app-main">
        {/* Sticky Top Header */}
        <header className="app-header">
          {/* Left: Breadcrumbs */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 500, color: 'var(--text-muted)' }}>
            <Layers size={15} color="var(--brand-primary)" />
            {breadcrumbs.map((crumb, idx) => (
              <React.Fragment key={idx}>
                {idx > 0 && <span style={{ color: 'var(--text-subtle)', margin: '0 2px' }}>/</span>}
                <span style={{
                  color: idx === breadcrumbs.length - 1 ? 'var(--text-primary)' : 'var(--text-muted)',
                  fontWeight: idx === breadcrumbs.length - 1 ? 700 : 500
                }}>
                  {crumb}
                </span>
              </React.Fragment>
            ))}
          </div>

          {/* Center: Global Command Palette Search */}
          <div className="search-trigger" onClick={() => setIsCommandPaletteOpen(true)}>
            <Search size={15} />
            <span>Search or jump to...</span>
            <kbd className="search-kbd">⌘K</kbd>
          </div>

          {/* Right Header Actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {/* Quick Create Action */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setQuickActionOpen(!quickActionOpen)}
                className="btn btn-primary btn-sm"
                style={{ gap: 6 }}
              >
                <Plus size={14} />
                <span>Quick Action</span>
                <ChevronDown size={12} />
              </button>

              {quickActionOpen && (
                <div style={{
                  position: 'absolute',
                  right: 0,
                  top: 38,
                  width: 220,
                  background: 'var(--bg-surface)',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-lg)',
                  border: '1px solid var(--border-default)',
                  padding: 6,
                  zIndex: 50
                }}>
                  <div
                    onClick={() => { setQuickActionOpen(false); setIsAddMerchantOpen(true); }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '8px 12px',
                      fontSize: 12.5,
                      fontWeight: 600,
                      color: 'var(--text-primary)',
                      borderRadius: 'var(--radius-xs)',
                      cursor: 'pointer'
                    }}
                    className="nav-item-link"
                  >
                    <Users size={14} />
                    <span>Add New Merchant</span>
                  </div>
                  <div
                    onClick={() => { setQuickActionOpen(false); navigate('/kyc-reviews'); }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '8px 12px',
                      fontSize: 12.5,
                      fontWeight: 600,
                      color: 'var(--text-primary)',
                      borderRadius: 'var(--radius-xs)',
                      cursor: 'pointer'
                    }}
                    className="nav-item-link"
                  >
                    <ShieldCheck size={14} />
                    <span>Review Pending KYC</span>
                  </div>
                  <div
                    onClick={() => { setQuickActionOpen(false); navigate('/gateway-settings'); }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '8px 12px',
                      fontSize: 12.5,
                      fontWeight: 600,
                      color: 'var(--text-primary)',
                      borderRadius: 'var(--radius-xs)',
                      cursor: 'pointer'
                    }}
                    className="nav-item-link"
                  >
                    <CreditCard size={14} />
                    <span>Configure Gateway</span>
                  </div>
                </div>
              )}
            </div>

            {/* Notification Bell */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => setNotificationsOpen(!notificationsOpen)}
                className="btn-icon btn-ghost"
                title="Notifications"
                style={{ position: 'relative' }}
              >
                <Bell size={17} />
                {unreadCount > 0 && (
                  <span style={{
                    position: 'absolute',
                    top: 6,
                    right: 6,
                    width: 8,
                    height: 8,
                    background: 'var(--danger)',
                    borderRadius: '50%',
                    border: '2px solid var(--bg-surface)'
                  }} />
                )}
              </button>

              {notificationsOpen && (
                <div style={{
                  position: 'absolute',
                  right: 0,
                  top: 42,
                  width: 320,
                  background: 'var(--bg-surface)',
                  borderRadius: 'var(--radius-lg)',
                  boxShadow: 'var(--shadow-lg)',
                  border: '1px solid var(--border-default)',
                  padding: 12,
                  zIndex: 50
                }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    paddingBottom: 8,
                    borderBottom: '1px solid var(--border-subtle)',
                    marginBottom: 8
                  }}>
                    <span style={{ fontSize: 13, fontWeight: 700 }}>Notifications</span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{unreadCount} unread</span>
                  </div>
                  {notifications && notifications.length > 0 ? (
                    <div style={{ maxHeight: 240, overflowY: 'auto' }}>
                      {notifications.slice(0, 5).map((n: any, idx: number) => (
                        <div key={idx} style={{ padding: '8px 0', borderBottom: '1px solid var(--border-subtle)', fontSize: 12 }}>
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{n.title || 'System Notification'}</div>
                          <div style={{ color: 'var(--text-muted)', fontSize: 11 }}>{n.message}</div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div style={{ padding: '20px 0', textAlign: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
                      No new notifications
                    </div>
                  )}
                  <Link
                    to="/support"
                    onClick={() => setNotificationsOpen(false)}
                    style={{
                      display: 'block',
                      textAlign: 'center',
                      padding: '6px 0',
                      marginTop: 8,
                      fontSize: 11.5,
                      fontWeight: 600,
                      color: 'var(--brand-primary)',
                      textDecoration: 'none'
                    }}
                  >
                    View Helpdesk & Tickets →
                  </Link>
                </div>
              )}
            </div>

            {/* Profile Dropdown */}
            {profileDropdownOpen && (
              <div style={{
                position: 'fixed',
                bottom: 60,
                left: isSidebarCollapsed ? 76 : 16,
                width: 230,
                background: 'var(--bg-surface)',
                borderRadius: 'var(--radius-md)',
                boxShadow: 'var(--shadow-lg)',
                border: '1px solid var(--border-default)',
                padding: 6,
                zIndex: 60
              }}>
                <div style={{ padding: '8px 10px', fontSize: 11.5, color: 'var(--text-muted)', borderBottom: '1px solid var(--border-subtle)' }}>
                  Signed in as<br />
                  <strong style={{ color: 'var(--text-primary)' }}>{user?.email || 'admin@swapnopay.top'}</strong>
                </div>
                <Link
                  to="/settings"
                  onClick={() => setProfileDropdownOpen(false)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 10px',
                    fontSize: 12.5,
                    color: 'var(--text-primary)',
                    textDecoration: 'none',
                    borderRadius: 'var(--radius-xs)',
                    fontWeight: 500
                  }}
                  className="nav-item-link"
                >
                  <Settings size={14} />
                  <span>Platform Settings</span>
                </Link>
                <Link
                  to="/gateway-settings"
                  onClick={() => setProfileDropdownOpen(false)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 10px',
                    fontSize: 12.5,
                    color: 'var(--text-primary)',
                    textDecoration: 'none',
                    borderRadius: 'var(--radius-xs)',
                    fontWeight: 500
                  }}
                  className="nav-item-link"
                >
                  <CreditCard size={14} />
                  <span>Gateway Credentials</span>
                </Link>
                <div
                  onClick={() => { setProfileDropdownOpen(false); signOut(); }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 10px',
                    fontSize: 12.5,
                    color: 'var(--danger-text)',
                    cursor: 'pointer',
                    borderRadius: 'var(--radius-xs)',
                    fontWeight: 600
                  }}
                  className="nav-item-link"
                >
                  <LogOut size={14} />
                  <span>Sign Out</span>
                </div>
              </div>
            )}
          </div>
        </header>

        {/* Main Content Area */}
        <main className="app-content">
          {children}
        </main>
      </div>

      {/* Global Command Palette */}
      <CommandPalette
        isOpen={isCommandPaletteOpen}
        onClose={() => setIsCommandPaletteOpen(false)}
        onOpenAddMerchant={() => setIsAddMerchantOpen(true)}
      />

      {/* Global Add Merchant Modal */}
      <AddMerchantModal
        isOpen={isAddMerchantOpen}
        onClose={() => setIsAddMerchantOpen(false)}
        onMerchantCreated={() => {
          setIsAddMerchantOpen(false)
          navigate('/merchants')
        }}
      />
    </div>
  )
}
