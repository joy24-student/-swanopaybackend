<?php
if (session_status() == PHP_SESSION_NONE) {
    session_start();
}
require_once __DIR__ . '/admin/inc/config.php';
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Authenticating... - ShopNext</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif; }
        body {
            background: linear-gradient(135deg, #f8fafc 0%, #edf2f7 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .auth-card {
            background: #ffffff;
            border-radius: 24px;
            padding: 40px 48px;
            max-width: 440px;
            width: 100%;
            text-align: center;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.06);
            border: 1px solid #f1f5f9;
        }
        .brand-logo {
            display: inline-flex;
            align-items: center;
            gap: 10px;
            font-size: 24px;
            font-weight: 800;
            color: #0f172a;
            margin-bottom: 28px;
            text-decoration: none;
        }
        .brand-logo span { color: #f59e0b; }
        .spinner {
            width: 52px;
            height: 52px;
            border: 4px solid #f1f5f9;
            border-top: 4px solid #fab802;
            border-radius: 50%;
            animation: spin 0.8s cubic-bezier(0.6, 0.2, 0.4, 0.8) infinite;
            margin: 0 auto 24px;
        }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        h2 { font-size: 20px; font-weight: 700; color: #1e293b; margin-bottom: 8px; }
        p { font-size: 14px; color: #64748b; line-height: 1.5; }
        .error-box {
            display: none;
            background: #fef2f2;
            color: #ef4444;
            border: 1px solid #fecaca;
            border-radius: 12px;
            padding: 14px;
            font-size: 13px;
            margin-top: 16px;
        }
        .btn-retry {
            display: inline-block;
            margin-top: 18px;
            background: #fab802;
            color: #1e293b;
            font-weight: 700;
            padding: 10px 24px;
            border-radius: 10px;
            text-decoration: none;
            font-size: 14px;
        }
    </style>
</head>
<body>

<div class="auth-card">
    <div class="brand-logo">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#fab802" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/>
        </svg>
        Shop<span>Next</span>
    </div>

    <div class="spinner" id="spinner"></div>
    <h2 id="statusTitle">Connecting with Google</h2>
    <p id="statusMsg">Please wait while we verify your Google credentials and prepare your account...</p>

    <div class="error-box" id="errorBox"></div>
</div>

<script>
    const SUPABASE_URL = 'https://oaudxkhxwdrdsybyaheb.supabase.co';
    const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9hdWR4a2h4d2RyZHN5YnlhaGViIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkzOTY3NjAsImV4cCI6MjEwNDk3Mjc2MH0.bCvAIA-54s91_nN9jp_qz3aNDX622QMbBhGxpsLcfW0';
    const supabase = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

    async function checkAuthSession() {
        try {
            // Supabase checks URL hash fragments automatically
            const { data: { session }, error } = await supabase.auth.getSession();
            
            if (error) {
                showError(error.message);
                return;
            }

            if (!session) {
                // If not parsed immediately, wait 600ms and try once more
                setTimeout(async () => {
                    const retry = await supabase.auth.getSession();
                    if (retry.data && retry.data.session) {
                        syncAndRedirect(retry.data.session.user);
                    } else {
                        showError('Could not retrieve your Google session. Please try again.');
                    }
                }, 600);
                return;
            }

            syncAndRedirect(session.user);
        } catch (err) {
            showError(err.message || 'An unexpected error occurred.');
        }
    }

    async function syncAndRedirect(user) {
        document.getElementById('statusTitle').innerText = 'Setting up Account';
        document.getElementById('statusMsg').innerText = 'Synchronizing profile details with ShopNext...';

        const meta = user.user_metadata || {};
        const payload = {
            supabase_uid: user.id,
            email: user.email,
            name: meta.full_name || meta.name || user.email.split('@')[0],
            avatar_url: meta.avatar_url || meta.picture || '',
            google_id: user.identities?.[0]?.id || user.id
        };

        try {
            const resp = await fetch('supabase_auth_sync.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const result = await resp.json();
            if (result.success) {
                document.getElementById('statusTitle').innerText = 'Success!';
                document.getElementById('statusMsg').innerText = 'Redirecting to your destination...';
                window.location.href = result.redirect || 'dashboard.php';
            } else {
                showError(result.message || 'Database synchronization failed.');
            }
        } catch (e) {
            showError('Communication error syncing account with server.');
        }
    }

    function showError(msg) {
        document.getElementById('spinner').style.display = 'none';
        document.getElementById('statusTitle').innerText = 'Authentication Failed';
        document.getElementById('statusMsg').innerText = 'We were unable to sign you in using Google.';
        const errBox = document.getElementById('errorBox');
        errBox.style.display = 'block';
        errBox.innerHTML = msg + '<br><a href="login.php" class="btn-retry">Back to Sign In</a>';
    }

    // Trigger on page load
    window.addEventListener('load', checkAuthSession);
</script>
</body>
</html>

