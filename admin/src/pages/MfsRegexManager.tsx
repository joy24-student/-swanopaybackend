import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';

interface MfsPattern {
  id: string;
  mfs_name: 'bKash' | 'Nagad' | 'Rocket' | 'Upay';
  pattern_name: string;
  regex_pattern: string;
  description?: string;
  active: boolean;
  created_at: string;
}

export default function MfsRegexManager() {
  const [patterns, setPatterns] = useState<MfsPattern[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);

  // Form State
  const [mfsName, setMfsName] = useState<'bKash' | 'Nagad' | 'Rocket' | 'Upay'>('bKash');
  const [patternName, setPatternName] = useState('');
  const [regexPattern, setRegexPattern] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  // Regex Testing Playground State
  const [testSms, setTestSms] = useState(
    'You have received Tk 1,500.00 from 01711223344. TrxID 9B382A11'
  );
  const [selectedPatternId, setSelectedPatternId] = useState<string>('');

  const loadPatterns = async () => {
    try {
      const { data, error } = await adminSupabase
        .from('mfs_regex_patterns')
        .select('*')
        .order('created_at', { ascending: false });

      if (!error && data) {
        setPatterns(data as MfsPattern[]);
        if (data.length > 0 && !selectedPatternId) {
          setSelectedPatternId(data[0].id);
        }
      }
    } catch (err: any) {
      console.error('[RegexManager] load error:', err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPatterns();
  }, []);

  // Save new pattern to Admin Supabase
  const handleSavePattern = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!patternName.trim() || !regexPattern.trim()) {
      return alert('Pattern name and Regex pattern are required');
    }

    setSaving(true);
    try {
      const { error } = await adminSupabase.from('mfs_regex_patterns').insert({
        mfs_name: mfsName,
        pattern_name: patternName.trim(),
        regex_pattern: regexPattern.trim(),
        description: description.trim() || null,
        active: true,
      });

      if (error) throw new Error(error.message);
      setShowAddModal(false);
      setPatternName('');
      setRegexPattern('');
      setDescription('');
      await loadPatterns();
    } catch (err: any) {
      alert('Failed to save pattern: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // Toggle Active/Inactive
  const handleToggleActive = async (id: string, currentActive: boolean) => {
    try {
      const { error } = await adminSupabase
        .from('mfs_regex_patterns')
        .update({ active: !currentActive, updated_at: new Date().toISOString() })
        .eq('id', id);

      if (error) throw new Error(error.message);
      await loadPatterns();
    } catch (err: any) {
      alert('Failed to toggle status: ' + err.message);
    }
  };

  // Live Regex Tester Result Calculation
  const testResult = useMemo(() => {
    const patternObj = patterns.find(p => p.id === selectedPatternId);
    if (!patternObj || !testSms.trim()) return null;

    try {
      const regex = new RegExp(patternObj.regex_pattern, 'i');
      const match = testSms.match(regex);

      if (!match) {
        return { matched: false, error: 'SMS text does NOT match regex pattern' };
      }

      return {
        matched: true,
        amount: match[1] || 'Not captured in group 1',
        sender: match[2] || 'Not captured in group 2',
        trxId: match[3] || 'Not captured in group 3',
        fullMatch: match[0],
      };
    } catch (err: any) {
      return { matched: false, error: 'Invalid Regular Expression syntax: ' + err.message };
    }
  }, [patterns, selectedPatternId, testSms]);

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>📱 MFS SMS Regex Patterns</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Manage and test bKash, Nagad, Rocket, and Upay SMS parsing regular expressions used by Android matching services.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="button" onClick={() => setShowAddModal(true)} style={{ background: '#10B981' }}>
            ➕ Add New Pattern
          </button>
          <Link to="/dashboard">
            <button className="button" style={{ background: '#64748B' }}>Dashboard</button>
          </Link>
        </div>
      </div>

      {/* Regex Testing Playground */}
      <div className="card" style={{ marginBottom: 20, border: '2px solid #4F46E5', background: '#F8FAFC' }}>
        <h3 style={{ marginTop: 0, color: '#4F46E5' }}>🧪 Live SMS Regex Tester Playground</h3>
        <p style={{ fontSize: 12, color: '#64748B', marginBottom: 12 }}>
          Test raw notification SMS text against your saved regex patterns to verify capture group extraction (Amount, Sender, TrxID).
        </p>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Select Pattern to Test</label>
            <select
              className="input"
              value={selectedPatternId}
              onChange={e => setSelectedPatternId(e.target.value)}
            >
              {patterns.map(p => (
                <option key={p.id} value={p.id}>
                  [{p.mfs_name}] {p.pattern_name}
                </option>
              ))}
            </select>

            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, margin: '12px 0 4px' }}>Sample Raw SMS Text</label>
            <textarea
              className="input"
              rows={3}
              value={testSms}
              onChange={e => setTestSms(e.target.value)}
              placeholder="Paste raw SMS message text here..."
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Extraction Results</label>
            {testResult ? (
              testResult.matched ? (
                <div style={{ padding: 12, background: '#ECFDF5', border: '1px solid #10B981', borderRadius: 8, fontSize: 12 }}>
                  <div style={{ fontWeight: 800, color: '#065F46', marginBottom: 6 }}>✅ MATCH SUCCESSFUL</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <div><strong>Parsed Amount (Group 1):</strong> <span style={{ color: '#10B981', fontWeight: 800 }}>৳{testResult.amount}</span></div>
                    <div><strong>Sender Number (Group 2):</strong> <code>{testResult.sender}</code></div>
                    <div><strong>Transaction ID (Group 3):</strong> <code style={{ fontWeight: 800 }}>{testResult.trxId}</code></div>
                    <div style={{ fontSize: 10, color: '#64748B', marginTop: 4 }}>Full Match: <code>{testResult.fullMatch}</code></div>
                  </div>
                </div>
              ) : (
                <div style={{ padding: 12, background: '#FEF2F2', border: '1px solid #EF4444', borderRadius: 8, fontSize: 12, color: '#991B1B' }}>
                  <div style={{ fontWeight: 800, marginBottom: 4 }}>❌ NO MATCH</div>
                  <div>{testResult.error}</div>
                </div>
              )
            ) : (
              <div style={{ padding: 12, background: '#F1F5F9', borderRadius: 8, fontSize: 12, color: '#94A3B8' }}>Select a pattern and enter sample SMS to test extraction.</div>
            )}
          </div>
        </div>
      </div>

      {/* Regex Patterns Table */}
      <div className="card">
        <h3 style={{ marginTop: 0 }}>📋 Configured Regex Patterns ({patterns.length})</h3>
        {loading ? (
          <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading SMS patterns...</div>
        ) : patterns.length === 0 ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No patterns configured yet.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Wallet</th>
                <th>Pattern Name</th>
                <th>Regex Expression</th>
                <th>Description</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {patterns.map(p => (
                <tr key={p.id}>
                  <td>
                    <span style={{
                      fontWeight: 700, fontSize: 11, padding: '2px 8px', borderRadius: 4,
                      color: p.mfs_name === 'bKash' ? '#E2125A' : p.mfs_name === 'Nagad' ? '#EC5A24' : p.mfs_name === 'Rocket' ? '#8C3494' : '#10B981',
                      background: '#F8FAFC', border: '1px solid #E2E8F0'
                    }}>
                      {p.mfs_name}
                    </span>
                  </td>
                  <td><strong>{p.pattern_name}</strong></td>
                  <td><code style={{ fontSize: 11, wordBreak: 'break-all', background: '#F1F5F9', padding: '2px 6px', borderRadius: 4 }}>{p.regex_pattern}</code></td>
                  <td style={{ fontSize: 12, color: '#64748B' }}>{p.description || '--'}</td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
                      background: p.active ? '#ECFDF5' : '#FEF2F2',
                      color: p.active ? '#065F46' : '#991B1B'
                    }}>
                      {p.active ? 'ACTIVE' : 'INACTIVE'}
                    </span>
                  </td>
                  <td>
                    <button
                      className="button"
                      onClick={() => handleToggleActive(p.id, p.active)}
                      style={{ padding: '4px 10px', fontSize: 11, background: p.active ? '#EF4444' : '#10B981' }}
                    >
                      {p.active ? 'Deactivate' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Add Pattern Modal */}
      {showAddModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
          <div className="card" style={{ maxWidth: 500, width: '100%', background: 'white' }}>
            <h3 style={{ marginTop: 0 }}>➕ Add New MFS Regex Pattern</h3>
            <form onSubmit={handleSavePattern}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>MFS Wallet *</label>
                  <select className="input" value={mfsName} onChange={e => setMfsName(e.target.value as any)}>
                    <option value="bKash">bKash</option>
                    <option value="Nagad">Nagad</option>
                    <option value="Rocket">Rocket</option>
                    <option value="Upay">Upay</option>
                  </select>
                </div>

                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Pattern Name *</label>
                  <input className="input" value={patternName} onChange={e => setPatternName(e.target.value)} placeholder="e.g. bKash Cash In Standard" required />
                </div>

                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Regex Pattern * (Group 1=Amount, Group 2=Sender, Group 3=TrxID)</label>
                  <textarea
                    className="input"
                    rows={3}
                    value={regexPattern}
                    onChange={e => setRegexPattern(e.target.value)}
                    placeholder="You have received Tk ([0-9,.]+) from ([0-9]+)\. TrxID ([A-Z0-9]+)"
                    style={{ fontFamily: 'monospace', fontSize: 12 }}
                    required
                  />
                </div>

                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Description</label>
                  <input className="input" value={description} onChange={e => setDescription(e.target.value)} placeholder="Optional description..." />
                </div>
              </div>

              <div style={{ marginTop: 18, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" className="button" onClick={() => setShowAddModal(false)} style={{ background: '#64748B' }}>Cancel</button>
                <button type="submit" className="button" disabled={saving} style={{ background: '#10B981' }}>
                  {saving ? 'Saving...' : 'Save Pattern'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
