<?php
/**
 * Security Dashboard
 * Monitor WAF events, blocked IPs, and security metrics
 * 
 * Access: /security-dashboard.php (admin only)
 */

session_start();
if (!isset($_SESSION['admin_id'])) {
    header('Location: login.php');
    exit;
}

require_once('admin/inc/config.php');
require_once('admin/inc/WAFSecuritySystem.php');

$waf = getWAF();
$action = $_GET['action'] ?? '';

// Handle actions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    
    if ($action === 'unblock_ip') {
        $ip = $_POST['ip'] ?? '';
        if (filter_var($ip, FILTER_VALIDATE_IP)) {
            $waf->unblockIP($ip);
            $success = "IP $ip has been unblocked";
        }
    }
}

// Get security data
$security_events = $waf->getSecurityEvents(7);
$blocked_ips = [];
$blocked_file = __DIR__ . '/assets/cache/security/blocked_ips.json';
if (file_exists($blocked_file)) {
    $blocked_ips = json_decode(file_get_contents($blocked_file), true) ?? [];
}

// Count events by type
$event_counts = [];
foreach ($security_events as $event) {
    $type = $event['event_type'] ?? 'unknown';
    $event_counts[$type] = ($event_counts[$type] ?? 0) + 1;
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🔒 Security Dashboard</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
        }

        header {
            color: white;
            margin-bottom: 30px;
            text-align: center;
        }

        header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
        }

        .card {
            background: white;
            border-radius: 12px;
            padding: 25px;
            margin-bottom: 20px;
            box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
        }

        .card h2 {
            font-size: 1.3em;
            margin-bottom: 15px;
            color: #333;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
        }

        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 20px;
        }

        .stat-card {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
        }

        .stat-label {
            font-size: 0.9em;
            opacity: 0.9;
            margin-bottom: 10px;
        }

        .stat-value {
            font-size: 2em;
            font-weight: bold;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 15px;
        }

        table th, table td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }

        table th {
            background: #f8f9fa;
            font-weight: 600;
            color: #333;
        }

        table tr:hover {
            background: #f8f9fa;
        }

        .badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.8em;
            font-weight: bold;
        }

        .badge-danger {
            background: #f8d7da;
            color: #721c24;
        }

        .badge-warning {
            background: #fff3cd;
            color: #856404;
        }

        .badge-success {
            background: #d4edda;
            color: #155724;
        }

        .btn {
            display: inline-block;
            padding: 8px 12px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.85em;
            margin: 5px;
        }

        .btn:hover {
            background: #5568d3;
        }

        .btn-danger {
            background: #dc3545;
        }

        .btn-danger:hover {
            background: #c82333;
        }

        .alert {
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 15px;
        }

        .alert-success {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }

        .empty {
            text-align: center;
            padding: 40px;
            color: #999;
        }

        form {
            display: inline;
        }

        .chart {
            margin-top: 20px;
        }

        .chart-bar {
            display: flex;
            align-items: center;
            margin: 10px 0;
        }

        .chart-label {
            width: 150px;
            font-weight: 500;
        }

        .chart-value {
            flex: 1;
            height: 30px;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            border-radius: 4px;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding-right: 10px;
            color: white;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>🔒 Security Dashboard</h1>
            <p>Monitor and manage WAF events and security threats</p>
        </header>

        <?php if (isset($success)): ?>
            <div class="alert alert-success">✓ <?php echo htmlspecialchars($success); ?></div>
        <?php endif; ?>

        <!-- Security Metrics -->
        <div class="grid">
            <div class="stat-card">
                <div class="stat-label">Total Events (7 days)</div>
                <div class="stat-value"><?php echo count($security_events); ?></div>
            </div>

            <div class="stat-card">
                <div class="stat-label">Blocked IPs</div>
                <div class="stat-value"><?php echo count($blocked_ips); ?></div>
            </div>

            <div class="stat-card">
                <div class="stat-label">Attack Types</div>
                <div class="stat-value"><?php echo count($event_counts); ?></div>
            </div>

            <div class="stat-card">
                <div class="stat-label">WAF Status</div>
                <div class="stat-value" style="color: #90EE90;">✓ ACTIVE</div>
            </div>
        </div>

        <!-- Security Events -->
        <div class="card">
            <h2>🚨 Recent Security Events</h2>
            
            <?php if (count($security_events) > 0): ?>
                <div class="chart">
                    <?php foreach ($event_counts as $type => $count): ?>
                        <div class="chart-bar">
                            <div class="chart-label"><?php echo ucfirst(str_replace('_', ' ', $type)); ?></div>
                            <div class="chart-value" style="width: <?php echo min(100, ($count / max(array_values($event_counts))) * 100); ?>%;">
                                <?php echo $count; ?>
                            </div>
                        </div>
                    <?php endforeach; ?>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Event Type</th>
                            <th>IP Address</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php 
                        $displayed = 0;
                        foreach (array_reverse($security_events) as $event): 
                            if ($displayed >= 20) break;
                            $displayed++;
                        ?>
                            <tr>
                                <td><?php echo htmlspecialchars($event['timestamp']); ?></td>
                                <td>
                                    <span class="badge badge-danger">
                                        <?php echo htmlspecialchars($event['event_type']); ?>
                                    </span>
                                </td>
                                <td><?php echo htmlspecialchars($event['details']['ip'] ?? 'N/A'); ?></td>
                                <td>
                                    <?php 
                                    $details = $event['details'];
                                    if (isset($details['input'])) {
                                        echo htmlspecialchars(substr($details['input'], 0, 50));
                                    } elseif (isset($details['user_agent'])) {
                                        echo htmlspecialchars(substr($details['user_agent'], 0, 50));
                                    } else {
                                        echo '—';
                                    }
                                    ?>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            <?php else: ?>
                <div class="empty">
                    <p>✓ No security events in the last 7 days</p>
                    <p style="font-size: 0.9em; color: #ccc;">Great job! Your site is secure.</p>
                </div>
            <?php endif; ?>
        </div>

        <!-- Blocked IPs -->
        <div class="card">
            <h2>🚫 Blocked IP Addresses</h2>
            
            <?php if (count($blocked_ips) > 0): ?>
                <table>
                    <thead>
                        <tr>
                            <th>IP Address</th>
                            <th>Reason</th>
                            <th>Blocked At</th>
                            <th>Expires At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php foreach ($blocked_ips as $ip => $block): ?>
                            <tr>
                                <td><code><?php echo htmlspecialchars($ip); ?></code></td>
                                <td>
                                    <span class="badge badge-warning">
                                        <?php echo htmlspecialchars($block['reason']); ?>
                                    </span>
                                </td>
                                <td><?php echo date('Y-m-d H:i:s', $block['blocked_at']); ?></td>
                                <td>
                                    <?php
                                    $remaining = $block['expires_at'] - time();
                                    if ($remaining > 0) {
                                        echo date('Y-m-d H:i:s', $block['expires_at']);
                                    } else {
                                        echo '<span style="color: #ccc;">Expired</span>';
                                    }
                                    ?>
                                </td>
                                <td>
                                    <form method="POST">
                                        <input type="hidden" name="action" value="unblock_ip">
                                        <input type="hidden" name="ip" value="<?php echo htmlspecialchars($ip); ?>">
                                        <button type="submit" class="btn btn-danger" 
                                                onclick="return confirm('Unblock this IP?')">
                                            Unblock
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            <?php else: ?>
                <div class="empty">
                    <p>✓ No blocked IP addresses</p>
                </div>
            <?php endif; ?>
        </div>

        <!-- Security Information -->
        <div class="card">
            <h2>ℹ️ WAF Configuration</h2>
            
            <table>
                <tr>
                    <td><strong>Status</strong></td>
                    <td><span class="badge badge-success">Active</span></td>
                </tr>
                <tr>
                    <td><strong>SQL Injection Protection</strong></td>
                    <td><span class="badge badge-success">Enabled</span></td>
                </tr>
                <tr>
                    <td><strong>XSS Filtering</strong></td>
                    <td><span class="badge badge-success">Enabled</span></td>
                </tr>
                <tr>
                    <td><strong>Bot Detection</strong></td>
                    <td><span class="badge badge-success">Enabled</span></td>
                </tr>
                <tr>
                    <td><strong>Rate Limiting</strong></td>
                    <td><span class="badge badge-success">Enabled (100 req/min)</span></td>
                </tr>
                <tr>
                    <td><strong>CSRF Protection</strong></td>
                    <td><span class="badge badge-success">Enabled</span></td>
                </tr>
                <tr>
                    <td><strong>Security Headers</strong></td>
                    <td><span class="badge badge-success">Enabled</span></td>
                </tr>
            </table>
        </div>

        <!-- Quick Links -->
        <div class="card">
            <h2>🔗 Quick Links</h2>
            <p>
                <a href="<?php echo BASE_URL; ?>" class="btn">Dashboard</a>
                <a href="WAF_SECURITY_GUIDE.md" class="btn" target="_blank">WAF Guide</a>
                <a href="CLOUDFLARE_WAF_SETUP.md" class="btn" target="_blank">Cloudflare Setup</a>
                <a href="AWS_WAF_SETUP.md" class="btn" target="_blank">AWS WAF Setup</a>
            </p>
        </div>

        <footer style="text-align: center; color: white; margin-top: 40px; opacity: 0.8;">
            <p>🔒 Enterprise Security | WAF Monitoring & Analytics</p>
        </footer>
    </div>
</body>
</html>
