import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';
import {
  Smartphone,
  Plus,
  FlaskConical,
  CheckCircle2,
  XCircle,
  FileCode,
  Save,
  X,
  RefreshCw,
} from 'lucide-react';

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
        return { matched: false, error: 'SMS text does not match regex pattern' };
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.02em' }}>
              MFS SMS Regex Patterns
            </h1>
            <span className="status-pill info" style={{ fontSize: 11 }}>
              <span className="status-dot" />
              Parsing Engine
            </span>
          </div>
          <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
            Manage and test bKash, Nagad, Rocket, and Upay SMS parsing regular expressions used by Android matching services.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn btn-primary btn-sm" onClick={() => setShowAddModal(true)}>
            <Plus size={14} />
            Add New Pattern
          </button>
          <Link to="/dashboard" className="btn btn-secondary btn-sm">
            Overview Dashboard
          </Link>
        </div>
      </div>

      {/* Regex Testing Playground */}
      <div className="card" style={{ padding: 20 }}>
        <h3 style={{ marginTop: 0, fontSize: 15, fontWeight: 700, color: 'var(--brand-primary)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <FlaskConical size={16} />
          Live SMS Regex Tester Playground
        </h3>
        <p style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginBottom: 14 }}>
          Test raw notification SMS text against your saved regex patterns to verify capture group extraction (Amount, Sender, TrxID).
        </p>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              SELECT PATTERN TO TEST
            </label>
            <select
              className="select"
              value={selectedPatternId}
              onChange={e => setSelectedPatternId(e.target.value)}
            >
              {patterns.map(p => (
                <option key={p.id} value={p.id}>
                  [{p.mfs_name}] {p.pattern_name}
                </option>
              ))}
            </select>

            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', margin: '14px 0 6px' }}>
              SAMPLE RAW SMS TEXT
            </label>
            <textarea
              className="textarea"
              rows={3}
              value={testSms}
              onChange={e => setTestSms(e.target.value)}
              placeholder="Paste raw SMS message text here..."
              style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              EXTRACTION RESULTS
            </label>
            {testResult ? (
              testResult.matched ? (
                <div style={{ padding: 14, background: 'var(--success-subtle)', border: '1px solid var(--success)', borderRadius: 'var(--radius-md)', fontSize: 12.5 }}>
                  <div style={{ fontWeight: 700, color: 'var(--success)', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <CheckCircle2 size={15} />
                    MATCH SUCCESSFUL
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                    <div><strong>Parsed Amount (Group 1):</strong> <span style={{ color: 'var(--success)', fontWeight: 800 }}>৳{testResult.amount}</span></div>
                    <div><strong>Sender Number (Group 2):</strong> <code>{testResult.sender}</code></div>
                    <div><strong>Transaction ID (Group 3):</strong> <code style={{ fontWeight: 800 }}>{testResult.trxId}</code></div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>Full Match: <code>{testResult.fullMatch}</code></div>
                  </div>
                </div>
              ) : (
                <div style={{ padding: 14, background: 'var(--danger-subtle)', border: '1px solid var(--danger-border)', borderRadius: 'var(--radius-md)', fontSize: 12.5, color: 'var(--danger)' }}>
                  <div style={{ fontWeight: 700, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <XCircle size={15} />
                    NO MATCH
                  </div>
                  <div>{testResult.error}</div>
                </div>
              )
            ) : (
              <div style={{ padding: 14, background: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)', fontSize: 12.5, color: 'var(--text-muted)', border: '1px solid var(--border-default)' }}>
                Select a pattern and enter sample SMS to test extraction.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Regex Patterns Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-default)' }}>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
            <FileCode size={16} color="var(--brand-primary)" />
            Configured Regex Patterns ({patterns.length})
          </h3>
        </div>

        {loading ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
            <RefreshCw size={20} className="spin" style={{ margin: '0 auto 10px', display: 'block' }} />
            Loading SMS patterns...
          </div>
        ) : patterns.length === 0 ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-muted)' }}>No patterns configured yet.</div>
        ) : (
          <div className="enterprise-table-container">
            <table className="enterprise-table">
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
                        background: 'var(--bg-subtle)', border: '1px solid var(--border-default)'
                      }}>
                        {p.mfs_name}
                      </span>
                    </td>
                    <td><strong>{p.pattern_name}</strong></td>
                    <td><code style={{ fontSize: 11, wordBreak: 'break-all' }}>{p.regex_pattern}</code></td>
                    <td style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{p.description || '--'}</td>
                    <td>
                      <span className={`status-pill ${p.active ? 'success' : 'danger'}`}>
                        <span className="status-dot" />
                        {p.active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                    <td>
                      <button
                        className={`btn ${p.active ? 'btn-danger' : 'btn-secondary'} btn-sm`}
                        onClick={() => handleToggleActive(p.id, p.active)}
                      >
                        {p.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Pattern Modal */}
      {showAddModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
          <div className="card" style={{ maxWidth: 500, width: '100%', background: 'var(--bg-surface)', padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Plus size={16} color="var(--brand-primary)" />
                Add New MFS Regex Pattern
              </h3>
              <button className="btn-ghost btn-icon" onClick={() => setShowAddModal(false)}>
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleSavePattern}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: 5 }}>MFS Wallet *</label>
                  <select className="select" value={mfsName} onChange={e => setMfsName(e.target.value as any)}>
                    <option value="bKash">bKash</option>
                    <option value="Nagad">Nagad</option>
                    <option value="Rocket">Rocket</option>
                    <option value="Upay">Upay</option>
                  </select>
                </div>

                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: 5 }}>Pattern Name *</label>
                  <input className="input" value={patternName} onChange={e => setPatternName(e.target.value)} placeholder="e.g. bKash Cash In Standard" required />
                </div>

                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: 5 }}>Regex Pattern * (Group 1=Amount, Group 2=Sender, Group 3=TrxID)</label>
                  <textarea
                    className="textarea"
                    rows={3}
                    value={regexPattern}
                    onChange={e => setRegexPattern(e.target.value)}
                    placeholder="You have received Tk ([0-9,.]+) from ([0-9]+)\. TrxID ([A-Z0-9]+)"
                    style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}
                    required
                  />
                </div>

                <div>
                  <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: 5 }}>Description</label>
                  <input className="input" value={description} onChange={e => setDescription(e.target.value)} placeholder="Optional description..." />
                </div>
              </div>

              <div style={{ marginTop: 20, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowAddModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={saving}>
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
