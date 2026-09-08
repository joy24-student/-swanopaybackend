import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase, fetchPaymentEvents } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';

interface PaymentEvent {
  id: string;
  order_id: string;
  tran_id?: string;
  trx_id?: string;
  status: 'PAID' | 'FAILED' | 'CANCELLED' | 'PENDING';
  amount: number;
  currency: string;
  payment_method?: string;
  sender_number?: string;
  payment_time?: string;
  merchant_id?: string;
  merchant_name?: string;
  project_ref?: string;
  cus_name?: string;
  cus_email?: string;
  product_name?: string;
  recorded_at: string;
}

export default function PaymentAnalytics() {
  const [events, setEvents] = useState<PaymentEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [methodFilter, setMethodFilter] = useState<string>('ALL');
  const [selectedEvent, setSelectedEvent] = useState<PaymentEvent | null>(null);

  // Initial fetch + realtime subscription
  useEffect(() => {
    fetchPaymentEvents(200)
      .then(data => setEvents(data as PaymentEvent[]))
      .catch(err => console.error('[Analytics] fetch error:', err.message))
      .finally(() => setLoading(false));

    const channel = adminSupabase
      .channel('payment_events_realtime')
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'payment_events' },
        payload => {
          const newEvent = payload.new as PaymentEvent;
          setEvents(prev => [newEvent, ...prev.slice(0, 199)]);
        }
      )
      .subscribe();

    return () => {
      adminSupabase.removeChannel(channel);
    };
  }, []);

  // Filtered events calculation
  const filteredEvents = useMemo(() => {
    return events.filter(e => {
      if (statusFilter !== 'ALL' && e.status !== statusFilter) return false;
      if (methodFilter !== 'ALL' && e.payment_method !== methodFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesOrder = e.order_id?.toLowerCase().includes(q);
        const matchesTran = e.tran_id?.toLowerCase().includes(q);
        const matchesTrx = e.trx_id?.toLowerCase().includes(q);
        const matchesCustomer = e.cus_name?.toLowerCase().includes(q) || e.cus_email?.toLowerCase().includes(q);
        const matchesMerchant = e.merchant_name?.toLowerCase().includes(q);
        if (!matchesOrder && !matchesTran && !matchesTrx && !matchesCustomer && !matchesMerchant) {
          return false;
        }
      }
      return true;
    });
  }, [events, statusFilter, methodFilter, searchQuery]);

  // Aggregate metrics calculation
  const stats = useMemo(() => {
    const paid = events.filter(e => e.status === 'PAID');
    const failed = events.filter(e => e.status === 'FAILED');
    const totalVolume = paid.reduce((sum, e) => sum + Number(e.amount || 0), 0);
    const successRate = events.length > 0 ? ((paid.length / events.length) * 100).toFixed(1) : '0';
    const avgTx = paid.length > 0 ? (totalVolume / paid.length).toFixed(2) : '0.00';

    return {
      totalTransactions: events.length,
      paidCount: paid.length,
      failedCount: failed.length,
      totalVolumeBDT: totalVolume.toLocaleString('en-US', { minimumFractionDigits: 2 }),
      successRate,
      avgTxBDT: Number(avgTx).toLocaleString('en-US', { minimumFractionDigits: 2 }),
    };
  }, [events]);

  // Export to CSV
  const handleExportCSV = () => {
    if (filteredEvents.length === 0) return alert('No events to export');
    const headers = ['Recorded At', 'Order ID', 'Merchant', 'Status', 'Amount BDT', 'Method', 'Trx ID', 'Customer Name', 'Customer Email'];
    const rows = filteredEvents.map(e => [
      new Date(e.recorded_at).toLocaleString(),
      e.order_id,
      `"${e.merchant_name || 'Merchant'}"`,
      e.status,
      e.amount,
      e.payment_method || 'MFS',
      e.trx_id || '',
      `"${e.cus_name || ''}"`,
      e.cus_email || '',
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `swapnopay_analytics_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PAID':
        return <span style={{ background: '#ECFDF5', color: '#065F46', padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: 11 }}>PAID</span>;
      case 'FAILED':
        return <span style={{ background: '#FEF2F2', color: '#991B1B', padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: 11 }}>FAILED</span>;
      case 'CANCELLED':
        return <span style={{ background: '#FEF3C7', color: '#92400E', padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: 11 }}>CANCELLED</span>;
      default:
        return <span style={{ background: '#F1F5F9', color: '#475569', padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: 11 }}>PENDING</span>;
    }
  };

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>📊 Payment Analytics & Ledger</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Real-time platform transaction stream from admin database with instant search and export.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="button" onClick={handleExportCSV} style={{ background: '#059669' }}>
            📥 Export CSV
          </button>
          <Link to="/dashboard">
            <button className="button" style={{ background: '#64748B' }}>Dashboard</button>
          </Link>
        </div>
      </div>

      {/* Metrics Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total Volume (BDT)', value: `৳${stats.totalVolumeBDT}`, color: '#10B981', icon: '💰' },
          { label: 'Successful Payments', value: stats.paidCount, color: '#059669', icon: '✅' },
          { label: 'Success Rate', value: `${stats.successRate}%`, color: '#4F46E5', icon: '📈' },
          { label: 'Average Transaction', value: `৳${stats.avgTxBDT}`, color: '#7C3AED', icon: '📊' },
          { label: 'Failed Payments', value: stats.failedCount, color: '#EF4444', icon: '❌' },
        ].map(({ label, value, color, icon }) => (
          <div key={label} className="card" style={{ margin: 0, padding: 16, borderLeft: `4px solid ${color}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>{label}</span>
              <span style={{ fontSize: 16 }}>{icon}</span>
            </div>
            <div style={{ fontSize: 22, fontWeight: 800, color, marginTop: 6 }}>{value}</div>
          </div>
        ))}
      </div>

      {/* Filters Bar */}
      <div className="card" style={{ marginBottom: 16, padding: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 12 }}>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, color: '#475569', marginBottom: 4 }}>Search Transactions</label>
            <input
              className="input"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Search by Order ID, Trx ID, Customer, or Merchant..."
            />
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, color: '#475569', marginBottom: 4 }}>Status Filter</label>
            <select className="input" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
              <option value="ALL">All Statuses</option>
              <option value="PAID">PAID</option>
              <option value="FAILED">FAILED</option>
              <option value="CANCELLED">CANCELLED</option>
              <option value="PENDING">PENDING</option>
            </select>
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, color: '#475569', marginBottom: 4 }}>Payment Method</label>
            <select className="input" value={methodFilter} onChange={e => setMethodFilter(e.target.value)}>
              <option value="ALL">All Methods</option>
              <option value="bKash">bKash</option>
              <option value="Nagad">Nagad</option>
              <option value="Rocket">Rocket</option>
              <option value="Upay">Upay</option>
            </select>
          </div>
        </div>
      </div>

      {/* Transactions Table */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>📋 Transactions Ledger ({filteredEvents.length})</h3>
          <span style={{ fontSize: 12, color: '#10B981', fontWeight: 600 }}>● Live Realtime Stream</span>
        </div>

        {loading ? (
          <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading real-time payment ledger...</div>
        ) : filteredEvents.length === 0 ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No payment events match the selected filters.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Order ID</th>
                <th>Merchant</th>
                <th>Amount</th>
                <th>Method</th>
                <th>Status</th>
                <th>Trx ID</th>
                <th>Customer</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredEvents.map(e => (
                <tr key={e.id}>
                  <td style={{ fontSize: 11, color: '#64748B' }}>{new Date(e.recorded_at).toLocaleString()}</td>
                  <td style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 12 }}>{e.order_id ? `#${e.order_id.slice(0, 10)}...` : '--'}</td>
                  <td><strong>{e.merchant_name || 'Merchant'}</strong></td>
                  <td style={{ fontWeight: 800, color: '#0F172A' }}>৳{Number(e.amount || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
                  <td>
                    <span style={{
                      fontWeight: 700, fontSize: 11,
                      color: e.payment_method === 'bKash' ? '#E2125A' : e.payment_method === 'Nagad' ? '#EC5A24' : e.payment_method === 'Rocket' ? '#8C3494' : '#10B981'
                    }}>
                      {e.payment_method || 'MFS'}
                    </span>
                  </td>
                  <td>{getStatusBadge(e.status)}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: 11 }}>{e.trx_id || '--'}</td>
                  <td style={{ fontSize: 12 }}>{e.cus_name || e.cus_email || '--'}</td>
                  <td>
                    <button
                      className="button"
                      style={{ padding: '4px 10px', fontSize: 11, background: '#4F46E5' }}
                      onClick={() => setSelectedEvent(e)}
                    >
                      Details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Transaction Detail Modal */}
      {selectedEvent && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
          <div className="card" style={{ maxWidth: 540, width: '100%', background: 'white', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, borderBottom: '1px solid #E2E8F0', paddingBottom: 10 }}>
              <h3 style={{ margin: 0 }}>💳 Transaction Details</h3>
              <button onClick={() => setSelectedEvent(null)} style={{ background: 'none', border: 'none', fontSize: 18, cursor: 'pointer', color: '#64748B' }}>✕</button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
              <div><strong>Order ID:</strong> <code>{selectedEvent.order_id}</code></div>
              <div><strong>Order Reference:</strong> {selectedEvent.tran_id || 'N/A'}</div>
              <div><strong>Status:</strong> {getStatusBadge(selectedEvent.status)}</div>
              <div><strong>Amount:</strong> <span style={{ fontWeight: 800, color: '#10B981', fontSize: 16 }}>৳{Number(selectedEvent.amount || 0).toLocaleString()} {selectedEvent.currency || 'BDT'}</span></div>
              <div><strong>Payment Method:</strong> {selectedEvent.payment_method || 'MFS Wallet'}</div>
              <div><strong>Provider Trx ID:</strong> <code>{selectedEvent.trx_id || 'N/A'}</code></div>
              <div><strong>Sender Number (Masked):</strong> {selectedEvent.sender_number || 'N/A'}</div>
              <div><strong>Merchant:</strong> {selectedEvent.merchant_name || 'N/A'} (ID: {selectedEvent.merchant_id || 'N/A'})</div>
              <div><strong>Customer Name:</strong> {selectedEvent.cus_name || 'N/A'}</div>
              <div><strong>Customer Email:</strong> {selectedEvent.cus_email || 'N/A'}</div>
              <div><strong>Product:</strong> {selectedEvent.product_name || 'N/A'}</div>
              <div><strong>Recorded At:</strong> {new Date(selectedEvent.recorded_at).toLocaleString()}</div>
            </div>
            <div style={{ marginTop: 18, display: 'flex', justifyContent: 'flex-end' }}>
              <button className="button" onClick={() => setSelectedEvent(null)} style={{ background: '#64748B' }}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
