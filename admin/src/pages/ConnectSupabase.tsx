import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

interface Organization {
  id: string;
  name: string;
  slug: string;
}

interface Project {
  id: string;
  name: string;
  organization_id: string;
  region: string;
  status: string;
}

export default function ConnectSupabase() {
  const [searchParams] = useSearchParams();
  const [controlPlaneUrl, setControlPlaneUrl] = useState<string>(
    localStorage.getItem('control_plane_url') || 'https://api.swapnopay.com'
  );
  const [userId, setUserId] = useState<string>(
    localStorage.getItem('merchant_user_id') || 'admin_merchant_01'
  );
  const [orgSlug, setOrgSlug] = useState<string>('');
  
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<'NOT_CONNECTED' | 'CONNECTED' | 'PROVISIONING'>('NOT_CONNECTED');
  
  const [orgs, setOrgs] = useState<Organization[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedRef, setSelectedRef] = useState<string>('');
  const [provisioningMsg, setProvisioningMsg] = useState<string>('');
  const [completedCredentials, setCompletedCredentials] = useState<{ projectUrl: string; publishableKey: string } | null>(null);

  useEffect(() => {
    localStorage.setItem('control_plane_url', controlPlaneUrl);
  }, [controlPlaneUrl]);

  useEffect(() => {
    localStorage.setItem('merchant_user_id', userId);
  }, [userId]);

  // Handle return from Supabase OAuth Callback
  useEffect(() => {
    const txId = searchParams.get('tx_id');
    const status = searchParams.get('status');
    if (txId || status === 'connected') {
      setConnectionStatus('CONNECTED');
      fetchOrganizationsAndProjects();
    }
  }, [searchParams]);

  // 1. Kick off OAuth 2.0 PKCE Flow
  const handleConnectSupabase = async () => {
    setLoading(true);
    setError(null);
    try {
      const cleanUrl = controlPlaneUrl.trim().replace(/\/$/, '');
      const redirectBack = window.location.origin + window.location.pathname;

      const res = await fetch(`${cleanUrl}/functions/v1/oauth-start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          user_id: userId,
          organization_slug: orgSlug.trim() || undefined,
          redirect_back: redirectBack,
        }),
      });

      const data = await res.json();
      if (!res.ok || !data.authorize_url) {
        throw new Error(data.error || 'Failed to generate OAuth redirect URL');
      }

      // Redirect user to Supabase OAuth Authorization Screen
      window.location.href = data.authorize_url;
    } catch (err: any) {
      setError(err.message || 'OAuth initialization error');
      setLoading(false);
    }
  };

  // 2. Fetch Organizations & Projects via Supabase Management API Edge Function
  const fetchOrganizationsAndProjects = async () => {
    setLoading(true);
    setError(null);
    try {
      const cleanUrl = controlPlaneUrl.trim().replace(/\/$/, '');
      const res = await fetch(`${cleanUrl}/functions/v1/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ user_id: userId }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || 'Failed to fetch Supabase projects');
      }

      setOrgs(data.organizations || []);
      setProjects(data.projects || []);
      setConnectionStatus('CONNECTED');
      if (data.projects && data.projects.length > 0) {
        setSelectedRef(data.projects[0].id);
      }
    } catch (err: any) {
      setError(err.message || 'Could not retrieve projects');
    } finally {
      setLoading(false);
    }
  };

  // 3. Provision Project (Select Existing or Create New)
  const handleProvisionProject = async (isNew: boolean = false) => {
    setLoading(true);
    setError(null);
    setConnectionStatus('PROVISIONING');

    try {
      const cleanUrl = controlPlaneUrl.trim().replace(/\/$/, '');
      let targetRef = selectedRef;

      if (isNew) {
        setProvisioningMsg('Creating new Supabase project on your account...');
        const targetOrg = orgSlug || (orgs[0]?.slug || 'personal');
        
        const createRes = await fetch(`${cleanUrl}/functions/v1/provision`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            user_id: userId,
            action: 'CREATE_PROJECT',
            organization_slug: targetOrg,
            project_name: `SwapnoPay Merchant Store (${Math.floor(Math.random() * 1000)})`,
          }),
        });

        const createData = await createRes.json();
        if (!createRes.ok || !createData.project_ref) {
          throw new Error(createData.error || 'Failed to initiate project creation');
        }
        targetRef = createData.project_ref;
      }

      // Poll Health until ACTIVE_HEALTHY
      setProvisioningMsg('Waiting for project database provision to complete (ACTIVE_HEALTHY)...');
      let isHealthy = false;
      let attempts = 0;

      while (!isHealthy && attempts < 15) {
        attempts++;
        await new Promise((r) => setTimeout(r, 4000));
        
        const healthRes = await fetch(`${cleanUrl}/functions/v1/provision`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            user_id: userId,
            action: 'CHECK_HEALTH',
            project_ref: targetRef,
          }),
        });

        const healthData = await healthRes.json();
        if (healthData.status === 'ACTIVE_HEALTHY') {
          isHealthy = true;
        } else {
          setProvisioningMsg(`Provisioning database in progress (check ${attempts}/15)...`);
        }
      }

      // Apply Schema and Fetch API Keys
      setProvisioningMsg('Applying database tables & securing RLS policies...');
      const finalizeRes = await fetch(`${cleanUrl}/functions/v1/provision`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          user_id: userId,
          action: 'APPLY_SCHEMA_AND_FINALIZE',
          project_ref: targetRef,
        }),
      });

      const finalizeData = await finalizeRes.json();
      if (!finalizeRes.ok || !finalizeData.publishable_key) {
        throw new Error(finalizeData.error || 'Failed to retrieve project credentials');
      }

      setCompletedCredentials({
        projectUrl: finalizeData.project_url,
        publishableKey: finalizeData.publishable_key,
      });
      setConnectionStatus('CONNECTED');
    } catch (err: any) {
      setError(err.message || 'Provisioning failed');
      setConnectionStatus('CONNECTED');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: 840 }}>
      <div className="header">
        <div>
          <h1 style={{ margin: 0, fontSize: 24, display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ color: '#3ECF8E' }}>⚡</span> Supabase Integration (OAuth 2.0)
          </h1>
          <p style={{ margin: '4px 0 0 0', color: '#64748b', fontSize: 14 }}>
            Manage merchant Supabase organizations and projects using Management API tokens.
          </p>
        </div>
        <Link to="/dashboard">
          <button className="button" style={{ background: '#475569' }}>Back to Dashboard</button>
        </Link>
      </div>

      {/* Configuration Settings */}
      <div className="card" style={{ marginBottom: 20 }}>
        <h3 style={{ marginTop: 0, fontSize: 16 }}>1. Environment Setup</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 'bold', marginBottom: 4 }}>
              Control Plane Endpoint
            </label>
            <input
              className="input"
              value={controlPlaneUrl}
              onChange={(e) => setControlPlaneUrl(e.target.value)}
              placeholder="https://api.swapnopay.com"
            />
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 'bold', marginBottom: 4 }}>
              Merchant User ID
            </label>
            <input
              className="input"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="admin_merchant_01"
            />
          </div>
        </div>
        <div style={{ marginTop: 12 }}>
          <label style={{ display: 'block', fontSize: 12, fontWeight: 'bold', marginBottom: 4 }}>
            Optional Preferred Organization Slug
          </label>
          <input
            className="input"
            value={orgSlug}
            onChange={(e) => setOrgSlug(e.target.value)}
            placeholder="my-org-slug (optional pre-selection)"
          />
        </div>
      </div>

      {/* OAuth Action Banner & Button */}
      <div
        className="card"
        style={{
          background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
          color: 'white',
          border: '1px solid #334155',
          marginBottom: 20,
          padding: 24,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 20, color: '#3ECF8E' }}>
              Connect Your Supabase Account
            </h2>
            <p style={{ margin: '6px 0 0 0', color: '#94a3b8', fontSize: 13, maxWidth: 480 }}>
              Authorize SwapnoPay via OAuth 2.0 PKCE to manage database instances, execute setup scripts, and fetch API keys automatically.
            </p>
          </div>

          <button
            onClick={handleConnectSupabase}
            disabled={loading}
            style={{
              background: '#3ECF8E',
              color: '#0f172a',
              border: 'none',
              borderRadius: 10,
              padding: '12px 24px',
              fontSize: 15,
              fontWeight: 'bold',
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              boxShadow: '0 4px 14px rgba(62, 207, 142, 0.35)',
              opacity: loading ? 0.7 : 1,
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M13.435 2.148a1.2 1.2 0 0 0-1.785.49L7.202 11.558a.6.6 0 0 0 .543.842h6.141l-3.321 9.452a1.2 1.2 0 0 0 1.785-.49l4.448-8.92a.6.6 0 0 0-.543-.842h-6.141l3.321-9.452z"/>
            </svg>
            {loading ? 'Redirecting to Supabase...' : 'Connect Supabase'}
          </button>
        </div>
      </div>

      {error && (
        <div
          className="card"
          style={{
            background: '#fef2f2',
            border: '1px solid #fca5a5',
            color: '#991b1b',
            marginBottom: 20,
            fontSize: 14,
          }}
        >
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Connection & Management API Panel */}
      {connectionStatus === 'CONNECTED' && (
        <div className="card" style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h3 style={{ margin: 0, fontSize: 16, color: '#10b981' }}>
              ✓ Supabase Management API Authorized
            </h3>
            <button className="button" onClick={fetchOrganizationsAndProjects} disabled={loading}>
              Refresh Projects
            </button>
          </div>

          {orgs.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: 13, color: '#475569' }}>Your Organizations:</h4>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                {orgs.map((o) => (
                  <span
                    key={o.id}
                    style={{
                      background: '#f1f5f9',
                      padding: '4px 10px',
                      borderRadius: 6,
                      fontSize: 12,
                      fontWeight: 'bold',
                      color: '#334155',
                    }}
                  >
                    {o.name} ({o.slug})
                  </span>
                ))}
              </div>
            </div>
          )}

          {projects.length > 0 ? (
            <div>
              <h4 style={{ margin: '0 0 8px 0', fontSize: 13, color: '#475569' }}>Select Existing Project:</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                {projects.map((p) => (
                  <label
                    key={p.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justify: 'space-between',
                      padding: 12,
                      borderRadius: 8,
                      border: selectedRef === p.id ? '2px solid #3ECF8E' : '1px solid #e2e8f0',
                      background: selectedRef === p.id ? '#f0fdf4' : 'white',
                      cursor: 'pointer',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <input
                        type="radio"
                        name="projectSelect"
                        checked={selectedRef === p.id}
                        onChange={() => setSelectedRef(p.id)}
                      />
                      <div>
                        <strong style={{ fontSize: 14 }}>{p.name}</strong>
                        <div style={{ fontSize: 11, color: '#64748b' }}>
                          Ref: {p.id} | Region: {p.region} | Status: {p.status}
                        </div>
                      </div>
                    </div>
                  </label>
                ))}
              </div>

              <div style={{ display: 'flex', gap: 12 }}>
                <button
                  className="button"
                  onClick={() => handleProvisionProject(false)}
                  disabled={loading || !selectedRef}
                  style={{ background: '#10b981' }}
                >
                  Link & Provision Selected Project
                </button>

                <button
                  className="button"
                  onClick={() => handleProvisionProject(true)}
                  disabled={loading}
                  style={{ background: '#6366f1' }}
                >
                  + Create Brand New Supabase Project
                </button>
              </div>
            </div>
          ) : (
            <div>
              <p style={{ fontSize: 13, color: '#64748b' }}>No projects found under this Supabase account.</p>
              <button
                className="button"
                onClick={() => handleProvisionProject(true)}
                disabled={loading}
                style={{ background: '#6366f1' }}
              >
                + Create New Supabase Project
              </button>
            </div>
          )}
        </div>
      )}

      {/* Provisioning Progress */}
      {connectionStatus === 'PROVISIONING' && (
        <div className="card" style={{ textAlign: 'center', padding: 32 }}>
          <div style={{ fontSize: 24, marginBottom: 12 }}>⏳</div>
          <h3 style={{ margin: 0, color: '#4f46e5' }}>Provisioning Database</h3>
          <p style={{ color: '#64748b', fontSize: 14, marginTop: 8 }}>{provisioningMsg}</p>
        </div>
      )}

      {/* Finalized Credentials Card */}
      {completedCredentials && (
        <div
          className="card"
          style={{
            background: '#f0fdf4',
            border: '1px solid #86efac',
            marginBottom: 20,
          }}
        >
          <h3 style={{ marginTop: 0, color: '#166534', fontSize: 16 }}>
            🎉 Supabase Connection Ready & Linked!
          </h3>
          <div style={{ fontSize: 13, color: '#15803d', display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div>
              <strong>Project URL:</strong>{' '}
              <code style={{ background: '#dcfce7', padding: '2px 6px', borderRadius: 4 }}>
                {completedCredentials.projectUrl}
              </code>
            </div>
            <div>
              <strong>Publishable / Anon Key:</strong>{' '}
              <code style={{ background: '#dcfce7', padding: '2px 6px', borderRadius: 4, wordBreak: 'break-all' }}>
                {completedCredentials.publishableKey}
              </code>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
