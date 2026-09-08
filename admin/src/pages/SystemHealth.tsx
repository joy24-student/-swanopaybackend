import React, { useEffect, useState, useCallback } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';

interface HealthStatus {
  service: string;
  status: 'ONLINE' | 'OFFLINE' | 'DEGRADED' | 'CHECKING';
  latencyMs?: number;
  details?: string;
  url?: string;
}

export default function SystemHealth() {
  const { session } = useAuth();
  const [backendUrl, setBackendUrl] = useState(
    localStorage.getItem('swapnopay_backend_url') || ((import.meta as any).env?.VITE_BACKEND_URL as string) || 'https://pay.swapnopay.top'
  );
  const [adminSecret, setAdminSecret] = useState(
    sessionStorage.getItem('swapnopay_admin_secret') || localStorage.getItem('swapnopay_admin_secret') || ''
  );
  const [loading, setLoading] = useState(false);
  const [lastCheckTime, setLastCheckTime] = useState<string | null>(null);

  const [services, setServices] = useState<HealthStatus[]>([
    { service: 'Admin Supabase DB', status: 'CHECKING', details: 'Platform Owner Database' },
    { service: 'SwapnoPay Backend Server', status: 'CHECKING', details: 'Express.js + Socket.io Server' },
    { service: 'Gateway Receipt Service', status: 'CHECKING', details: 'Gmail OAuth Email Receipts' },
  ]);

  const [backendMeta, setBackendMeta] = useState<{ clientsCount?: number; version?: string } | null>(null);

  const runDiagnostics = useCallback(async () => {
    setLoading(true);
    setLastCheckTime(new Date().toLocaleTimeString());

    const updated: HealthStatus[] = [];

    // 1. Test Admin Supabase Database
    const startDb = performance.now();
    try {
      const { error } = await adminSupabase.from('gateway_config').select('id').limit(1);
      const latencyDb = Math.round(performance.now() - startDb);
      if (error) {
        updated.push({ service: 'Admin Supabase DB', status: 'OFFLINE', latencyMs: latencyDb, details: error.message });
      } else {
        updated.push({ service: 'Admin Supabase DB', status: 'ONLINE', latencyMs: latencyDb, details: 'Connected to platform owner database' });
      }
    } catch (e: any) {
      updated.push({ service: 'Admin Supabase DB', status: 'OFFLINE', details: e.message || 'Connection failed' });
    }

    // 2. Test SwapnoPay Backend Server (/healthz)
    const cleanBackend = backendUrl.trim().replace(/\/$/, '');
    const startBackend = performance.now();
    try {
      const res = await fetch(`${cleanBackend}/healthz`, { signal: AbortSignal.timeout(4000) });
      const latencyBackend = Math.round(performance.now() - startBackend);
      if (res.ok) {
        const data = await res.json();
        setBackendMeta({ clientsCount: data.socket_io_clients, version: data.version });
        updated.push({ service: 'SwapnoPay Backend Server', status: 'ONLINE', latencyMs: latencyBackend, details: `Socket.io active clients: ${data.socket_io_clients ?? 0}`, url: `${cleanBackend}/healthz` });
      } else {
        updated.push({ service: 'SwapnoPay Backend Server', status: 'DEGRADED', latencyMs: latencyBackend, details: `HTTP ${res.status}` });
      }
    } catch (e: any) {
      updated.push({ service: 'SwapnoPay Backend Server', status: 'OFFLINE', details: 'Backend unreachable. Ensure server is running.' });
    }

    // 3. Test Gateway Admin Health Endpoint (/v1/admin/health)
    try {
      const startAdmin = performance.now();
      const authHeaders: Record<string, string> = {};
      if (session?.access_token) {
        authHeaders['Authorization'] = `Bearer ${session.access_token}`;
      } else if (adminSecret) {
        authHeaders['X-Admin-Secret'] = adminSecret;
      }

      const res = await fetch(`${cleanBackend}/v1/admin/health`, {
        headers: authHeaders,
        signal: AbortSignal.timeout(4000),
      });
      const latencyAdmin = Math.round(performance.now() - startAdmin);
      if (res.ok) {
        const data = await res.json();
        const receiptStatus = data.gateway_service === 'ok' ? 'ONLINE' : 'DEGRADED';
        updated.push({ service: 'Gateway Receipt Service', status: receiptStatus, latencyMs: latencyAdmin, details: `Status: ${data.gateway_service}` });
      } else {
        updated.push({ service: 'Gateway Receipt Service', status: 'OFFLINE', details: 'Admin authorization required' });
      }
    } catch {
      updated.push({ service: 'Gateway Receipt Service', status: 'OFFLINE', details: 'Gateway health endpoint offline' });
    }

    setServices(updated);
    setLoading(false);
  }, [backendUrl, adminSecret, session]);

  useEffect(() => {
    runDiagnostics();
  }, [runDiagnostics]);

  const saveSettings = () => {
    localStorage.setItem('swapnopay_backend_url', backendUrl);
    if (adminSecret) {
      sessionStorage.setItem('swapnopay_admin_secret', adminSecret);
      localStorage.setItem('swapnopay_admin_secret', adminSecret);
    } else {
      sessionStorage.removeItem('swapnopay_admin_secret');
      localStorage.removeItem('swapnopay_admin_secret');
    }
    alert('Settings updated!');
    runDiagnostics();
  };

  const getStatusBadge = (status: HealthStatus['status']) => {
    switch (status) {
      case 'ONLINE':
        return <span style={{ background: '#ECFDF5', color: '#065F46', padding: '4px 12px', borderRadius: 20, fontWeight: 700, fontSize: 12 }}>● ONLINE</span>;
      case 'DEGRADED':
        return <span style={{ background: '#FEF3C7', color: '#92400E', padding: '4px 12px', borderRadius: 20, fontWeight: 700, fontSize: 12 }}>▲ DEGRADED</span>;
      case 'OFFLINE':
        return <span style={{ background: '#FEF2F2', color: '#991B1B', padding: '4px 12px', borderRadius: 20, fontWeight: 700, fontSize: 12 }}>✖ OFFLINE</span>;
      default:
        return <span style={{ background: '#F1F5F9', color: '#475569', padding: '4px 12px', borderRadius: 20, fontWeight: 700, fontSize: 12 }}>⏳ CHECKING</span>;
    }
  };

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>📡 System Health & Diagnostics</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Monitor real-time status of backend services, database latency, and Socket.io active clients.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="button" onClick={runDiagnostics} disabled={loading} style={{ background: '#4F46E5' }}>
            {loading ? 'Testing...' : '🔄 Run Diagnostics'}
          </button>
          <Link to="/dashboard">
            <button className="button" style={{ background: '#64748B' }}>Dashboard</button>
          </Link>
        </div>
      </div>

      {/* Backend Settings Bar */}
      <div className="card" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>⚙️ Backend Connection Settings</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 120px', gap: 12, alignItems: 'end' }}>
          <div>
            <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>SwapnoPay Backend URL</label>
            <input className="input" value={backendUrl} onChange={e => setBackendUrl(e.target.value)} placeholder="http://localhost:4000" />
          </div>
          <div>
            <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Admin Secret Header</label>
            <input className="input" type="password" value={adminSecret} onChange={e => setAdminSecret(e.target.value)} placeholder="ADMIN_SECRET value" />
            <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>
              Admin Authorization {session?.access_token && <span style={{ color: '#10B981' }}>(Active Session)</span>}
            </label>
            <input className="input" type="password" value={adminSecret} onChange={e => setAdminSecret(e.target.value)} placeholder={session?.access_token ? "Authenticated via JWT session (override optional)" : "ADMIN_SECRET value"} />
          </div>
          <button className="button" onClick={saveSettings} style={{ background: '#10B981', height: 38 }}>
            Save
          </button>
        </div>
      </div>

      {/* Status Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12, marginBottom: 20 }}>
        <div className="card" style={{ margin: 0, padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B' }}>SYSTEM STATUS</div>
          <div style={{ fontSize: 20, fontWeight: 800, color: services.every(s => s.status === 'ONLINE') ? '#10B981' : '#F59E0B', marginTop: 4 }}>
            {services.every(s => s.status === 'ONLINE') ? 'ALL SYSTEMS OPERATIONAL' : 'PARTIAL DISRUPTION'}
          </div>
        </div>
        <div className="card" style={{ margin: 0, padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B' }}>SOCKET.IO CLIENTS</div>
          <div style={{ fontSize: 22, fontWeight: 800, color: '#4F46E5', marginTop: 4 }}>
            {backendMeta?.clientsCount ?? '--'}
          </div>
        </div>
        <div className="card" style={{ margin: 0, padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B' }}>BACKEND VERSION</div>
          <div style={{ fontSize: 22, fontWeight: 800, color: '#7C3AED', marginTop: 4 }}>
            {backendMeta?.version ? `v${backendMeta.version}` : 'v2.0.0'}
          </div>
        </div>
        <div className="card" style={{ margin: 0, padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B' }}>LAST DIAGNOSTIC</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: '#334155', marginTop: 8 }}>
            {lastCheckTime || 'Just now'}
          </div>
        </div>
      </div>

      {/* Services Health Table */}
      <div className="card">
        <h3 style={{ marginTop: 0 }}>🖥 Connected Services Status</h3>
        <table className="table">
          <thead>
            <tr>
              <th>Service</th>
              <th>Status</th>
              <th>Latency</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {services.map(s => (
              <tr key={s.service}>
                <td>
                  <strong style={{ fontSize: 14 }}>{s.service}</strong>
                  {s.url && <div style={{ fontSize: 10, color: '#94A3B8' }}>{s.url}</div>}
                </td>
                <td>{getStatusBadge(s.status)}</td>
                <td style={{ fontWeight: 700, color: s.latencyMs && s.latencyMs < 150 ? '#10B981' : '#F59E0B' }}>
                  {s.latencyMs ? `${s.latencyMs} ms` : '--'}
                </td>
                <td style={{ fontSize: 12, color: '#64748B' }}>{s.details || '--'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
