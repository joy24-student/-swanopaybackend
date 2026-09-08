import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';

interface FormSubmission {
  id: string;
  createdAt: string;
  merchantId?: string;
  formId?: string;
  customer_name?: string;
  customer_phone?: string;
  customer_email?: string;
  amount_bdt?: number;
  payment_status?: string;
  submission?: any;
}

export default function Submissions() {
  const [rows, setRows] = useState<FormSubmission[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selectedSub, setSelectedSub] = useState<FormSubmission | null>(null);

  useEffect(() => {
    (async () => {
      let list: FormSubmission[] = [];

      try {
        const { data } = await adminSupabase
          .from('form_submissions')
          .select('*')
          .order('created_at', { ascending: false });
        if (data && data.length > 0) {
          list = data.map(d => ({
            id: d.id,
            createdAt: d.created_at,
            merchantId: d.merchant_id || d.form_id,
            formId: d.form_id,
            customer_name: d.customer_name,
            customer_phone: d.customer_phone,
            customer_email: d.customer_email,
            amount_bdt: d.amount_bdt,
            payment_status: d.payment_status,
            submission: d.answers || {},
          }));
        }
      } catch (e) {
        console.error('[Submissions] Fetch error:', e);
      }

      setRows(list);
      setLoading(false);
    })();
  }, []);

  const filteredRows = useMemo(() => {
    if (!search.trim()) return rows;
    const q = search.toLowerCase();
    return rows.filter(
      r =>
        (r.merchantId || '').toLowerCase().includes(q) ||
        (r.formId || '').toLowerCase().includes(q) ||
        (r.customer_name || '').toLowerCase().includes(q) ||
        (r.customer_phone || '').includes(q) ||
        (r.customer_email || '').toLowerCase().includes(q)
    );
  }, [rows, search]);

  const handleExportCSV = () => {
    if (filteredRows.length === 0) return alert('No submissions to export');
    const headers = ['Submitted At', 'Merchant ID', 'Form ID', 'Customer Name', 'Customer Phone', 'Customer Email', 'Amount BDT', 'Payment Status'];
    const csvRows = filteredRows.map(r => [
      new Date(r.createdAt).toLocaleString(),
      r.merchantId || '',
      r.formId || '',
      `"${r.customer_name || ''}"`,
      r.customer_phone || '',
      r.customer_email || '',
      r.amount_bdt || 0,
      r.payment_status || 'NOT_REQUIRED',
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...csvRows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `form_submissions_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>📄 Hosted Form Submissions ({rows.length})</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Inspect submissions captured across all merchant payment forms.
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

      {/* Search */}
      <div className="card" style={{ marginBottom: 16, padding: 12 }}>
        <input
          className="input"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="🔍 Search submissions by Merchant, Form ID, Customer Name, Phone, or Email..."
        />
      </div>

      {/* Submissions Table */}
      <div className="card">
        {loading ? (
          <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading submissions...</div>
        ) : filteredRows.length === 0 ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No form submissions found.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Submitted At</th>
                <th>Merchant</th>
                <th>Form ID</th>
                <th>Customer</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map(r => (
                <tr key={r.id}>
                  <td style={{ fontSize: 11, color: '#64748B' }}>{new Date(r.createdAt).toLocaleString()}</td>
                  <td><code style={{ fontSize: 11 }}>{r.merchantId?.slice(0, 10) || '--'}</code></td>
                  <td><code style={{ fontSize: 11 }}>{r.formId?.slice(0, 10) || '--'}</code></td>
                  <td>
                    <div><strong>{r.customer_name || '--'}</strong></div>
                    <div style={{ fontSize: 11, color: '#64748B' }}>{r.customer_phone || r.customer_email || ''}</div>
                  </td>
                  <td style={{ fontWeight: 700 }}>৳{Number(r.amount_bdt || 0).toLocaleString()}</td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 10, fontWeight: 700,
                      background: r.payment_status === 'PAID' ? '#ECFDF5' : '#F1F5F9',
                      color: r.payment_status === 'PAID' ? '#065F46' : '#475569'
                    }}>
                      {r.payment_status || 'NOT_REQUIRED'}
                    </span>
                  </td>
                  <td>
                    <button
                      className="button"
                      style={{ padding: '4px 10px', fontSize: 11, background: '#4F46E5' }}
                      onClick={() => setSelectedSub(r)}
                    >
                      Inspect Answers
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Answers Inspector Modal */}
      {selectedSub && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
          <div className="card" style={{ maxWidth: 540, width: '100%', background: 'white', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, borderBottom: '1px solid #E2E8F0', paddingBottom: 10 }}>
              <h3 style={{ margin: 0 }}>📄 Submission Answers</h3>
              <button onClick={() => setSelectedSub(null)} style={{ background: 'none', border: 'none', fontSize: 18, cursor: 'pointer', color: '#64748B' }}>✕</button>
            </div>
            <pre style={{ padding: 14, background: '#F8FAFC', borderRadius: 8, fontSize: 12, overflowX: 'auto', border: '1px solid #E2E8F0', color: '#1E293B' }}>
              {JSON.stringify(selectedSub.submission, null, 2)}
            </pre>
            <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
              <button className="button" onClick={() => setSelectedSub(null)} style={{ background: '#64748B' }}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
