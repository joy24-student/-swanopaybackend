import React, { useEffect, useState } from 'react'
import { X } from 'lucide-react'

interface DetailDrawerProps {
  isOpen: boolean
  onClose: () => void
  title: string
  subtitle?: string
  badge?: React.ReactNode
  children?: React.ReactNode
  tabs?: { key: string; label: string; content: React.ReactNode }[]
  footer?: React.ReactNode
  width?: number | string
}

export default function DetailDrawer({
  isOpen,
  onClose,
  title,
  subtitle,
  badge,
  children,
  tabs,
  footer,
  width = 460
}: DetailDrawerProps) {
  const [selectedTab, setSelectedTab] = useState('')
  const activeTab = tabs?.find(tab => tab.key === selectedTab) || tabs?.[0]
  useEffect(() => { if (!isOpen) setSelectedTab('') }, [isOpen])
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <div
        className="drawer-panel"
        style={{ width }}
        onClick={e => e.stopPropagation()}
      >
        {/* Drawer Header */}
        <div style={{
          padding: '18px 22px',
          borderBottom: '1px solid var(--border-default)',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          background: 'var(--bg-surface)'
        }}>
          <div style={{ flex: 1, minWidth: 0, paddingRight: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.2px' }}>
                {title}
              </h3>
              {badge}
            </div>
            {subtitle && (
              <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
                {subtitle}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="btn-icon btn-ghost"
            style={{ borderRadius: 'var(--radius-sm)' }}
            title="Close drawer (Esc)"
          >
            <X size={18} />
          </button>
        </div>

        {tabs && tabs.length > 0 && (
          <div role="tablist" style={{ display: 'flex', gap: 8, padding: '12px 22px' }}>
            {tabs.map(tab => (
              <button key={tab.key} role="tab" aria-selected={activeTab?.key === tab.key}
                className={activeTab?.key === tab.key ? 'btn btn-primary' : 'btn btn-ghost'}
                onClick={() => setSelectedTab(tab.key)}>{tab.label}</button>
            ))}
          </div>
        )}
        {/* Drawer Scrollable Content */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '22px',
          background: 'var(--bg-canvas)'
        }}>
          {activeTab ? activeTab.content : children}
        </div>

        {/* Drawer Footer */}
        {footer && (
          <div style={{
            padding: '14px 22px',
            borderTop: '1px solid var(--border-default)',
            background: 'var(--bg-surface)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 10
          }}>
            {footer}
          </div>
        )}
      </div>
    </>
  )
}
