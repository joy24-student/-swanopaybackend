<?php
ob_start();
session_start();
require_once("../../admin/inc/config.php");
require_once("../../admin/inc/functions.php");

$tran_id = strip_tags($_GET['tran_id'] ?? ($_SESSION['pending_tran_id'] ?? ''));
$method = strip_tags($_GET['method'] ?? ($_SESSION['pending_method'] ?? 'bKash'));
$amount = (float)($_SESSION['pending_amount'] ?? 0);

if (empty($tran_id)) {
    header('location: ../../checkout.php');
    exit;
}

// Fetch merchant payment receiving number
$receiving_number = '01700000000';
$account_type = 'Personal';

$supabase_url = defined('SUPABASE_URL') ? SUPABASE_URL : getenv('SUPABASE_URL');
$supabase_key = defined('SUPABASE_SERVICE_KEY') && !empty(SUPABASE_SERVICE_KEY) 
    ? SUPABASE_SERVICE_KEY 
    : (defined('SUPABASE_ANON_KEY') ? SUPABASE_ANON_KEY : getenv('SUPABASE_ANON_KEY'));

if (!empty($supabase_url) && !empty($supabase_key)) {
    $clean_supabase_url = rtrim($supabase_url, '/');
    $ch = curl_init("{$clean_supabase_url}/rest/v1/merchant_numbers?type=eq.{$method}&active=eq.true&select=number,is_default&limit=1");
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        "apikey: {$supabase_key}",
        "Authorization: Bearer {$supabase_key}"
    ]);
    $res = curl_exec($ch);
    curl_close($ch);
    $numbers = json_decode($res, true);
    if (!empty($numbers[0]['number'])) {
        $receiving_number = $numbers[0]['number'];
    }
}

// Method branding colors
$branding = [
    'bKash' => ['bg' => '#e2136e', 'icon' => 'bKash', 'textColor' => '#fff'],
    'Nagad' => ['bg' => '#f7941d', 'icon' => 'Nagad', 'textColor' => '#fff'],
    'Rocket' => ['bg' => '#8c3494', 'icon' => 'Rocket', 'textColor' => '#fff'],
    'Upay' => ['bg' => '#ffcb05', 'icon' => 'Upay', 'textColor' => '#000']
];
$brand = $branding[$method] ?? $branding['bKash'];

// Handle Manual TrxID Submission
$trx_message = '';
if (isset($_POST['submit_trx'])) {
    $user_trx = strip_tags($_POST['trx_id'] ?? '');
    $user_sender = strip_tags($_POST['sender_number'] ?? '');
    if (!empty($user_trx)) {
        if (!empty($supabase_url) && !empty($supabase_key)) {
            // Update order with customer-provided TrxID for faster matching/appeal
            $patch_payload = json_encode([
                'sender_number' => $user_sender,
                'matched_trx_id' => $user_trx
            ]);
            $ch = curl_init("{$clean_supabase_url}/rest/v1/orders?tran_id=eq.{$tran_id}");
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_CUSTOMREQUEST, 'PATCH');
            curl_setopt($ch, CURLOPT_POSTFIELDS, $patch_payload);
            curl_setopt($ch, CURLOPT_HTTPHEADER, [
                "apikey: {$supabase_key}",
                "Authorization: Bearer {$supabase_key}",
                "Content-Type: application/json"
            ]);
            curl_exec($ch);
            curl_close($ch);
        }
        $trx_message = 'TrxID submitted! Verifying transaction with your payment SMS...';
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verify Payment - <?php echo htmlspecialchars($method); ?></title>
    <link rel="stylesheet" href="../../assets/css/bootstrap.min.css">
    <link rel="stylesheet" href="../../assets/css/font-awesome.min.css">
    <script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>
    <style>
        body {
            background-color: #f4f6f9;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            padding: 20px;
        }
        .pay-card {
            background: #ffffff;
            max-width: 460px;
            width: 100%;
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.08);
            overflow: hidden;
            text-align: center;
        }
        .pay-header {
            background: <?php echo $brand['bg']; ?>;
            color: <?php echo $brand['textColor']; ?>;
            padding: 24px;
        }
        .pay-header h2 {
            margin: 0;
            font-size: 24px;
            font-weight: 700;
        }
        .pay-body {
            padding: 24px;
        }
        .amount-box {
            background: #f8fafc;
            border: 2px dashed #cbd5e1;
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 20px;
        }
        .amount-val {
            font-size: 32px;
            font-weight: 800;
            color: #0f172a;
        }
        .number-box {
            background: #f1f5f9;
            border-radius: 10px;
            padding: 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
        }
        .number-val {
            font-size: 20px;
            font-weight: 700;
            color: #1e293b;
            letter-spacing: 1px;
        }
        .btn-copy {
            background: <?php echo $brand['bg']; ?>;
            color: #fff;
            border: none;
            border-radius: 6px;
            padding: 6px 14px;
            font-size: 13px;
            cursor: pointer;
            font-weight: 600;
        }
        .instructions {
            text-align: left;
            font-size: 13px;
            color: #64748b;
            line-height: 1.6;
            margin-bottom: 20px;
            background: #fafafa;
            padding: 14px;
            border-radius: 8px;
        }
        .instructions ol {
            margin: 0;
            padding-left: 20px;
        }
        .pulsing-dot {
            display: inline-block;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #10b981;
            animation: pulse 1.5s infinite;
            margin-right: 6px;
        }
        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }
        .status-badge {
            display: inline-flex;
            align-items: center;
            background: #ecfdf5;
            color: #065f46;
            padding: 6px 16px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 20px;
        }
        .form-control-custom {
            width: 100%;
            padding: 12px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            margin-bottom: 12px;
            font-size: 14px;
            text-align: center;
        }
        .btn-verify {
            width: 100%;
            padding: 12px;
            background: #0f172a;
            color: #fff;
            border: none;
            border-radius: 8px;
            font-weight: 700;
            cursor: pointer;
        }
    </style>
</head>
<body>

<div class="pay-card">
    <div class="pay-header">
        <h2>Pay with <?php echo htmlspecialchars($method); ?></h2>
        <p style="margin: 4px 0 0; opacity: 0.9; font-size: 14px;">Instant SMS Automated Verification</p>
    </div>

    <div class="pay-body">
        <div class="status-badge">
            <span class="pulsing-dot"></span> Waiting for payment SMS detection...
        </div>

        <div class="amount-box">
            <div style="font-size: 12px; text-transform: uppercase; color: #64748b; font-weight: 600;">Total Amount</div>
            <div class="amount-val">৳ <?php echo number_format($amount, 2); ?></div>
        </div>

        <div class="number-box">
            <div>
                <div style="font-size: 11px; text-transform: uppercase; color: #64748b;">Send Money To (<?php echo $account_type; ?>)</div>
                <div class="number-val" id="recNumber"><?php echo htmlspecialchars($receiving_number); ?></div>
            </div>
            <button type="button" class="btn-copy" onclick="copyNumber()">Copy</button>
        </div>

        <div class="instructions">
            <ol>
                <li>Open your <b><?php echo htmlspecialchars($method); ?></b> app or dial USSD.</li>
                <li>Choose <b>Send Money</b> and enter the number above.</li>
                <li>Enter exact amount <b>৳ <?php echo number_format($amount, 2); ?></b>.</li>
                <li>Use Reference: <b><?php echo substr($tran_id, -6); ?></b></li>
                <li>Once sent, this screen will <b>automatically update</b> via live SMS sync!</li>
            </ol>
        </div>

        <?php if (!empty($trx_message)): ?>
            <div class="alert alert-info" style="font-size: 13px;"><?php echo htmlspecialchars($trx_message); ?></div>
        <?php endif; ?>

        <form method="post" style="border-top: 1px solid #f1f5f9; padding-top: 16px;">
            <p style="font-size: 12px; color: #64748b; margin-bottom: 8px;">Already sent money? Enter TrxID to speed up verification:</p>
            <input type="text" name="sender_number" class="form-control-custom" placeholder="Your Sender Phone (01XXXXXXXXX)" required>
            <input type="text" name="trx_id" class="form-control-custom" placeholder="Transaction ID (e.g. 9A8B7C6D)" required>
            <button type="submit" name="submit_trx" class="btn-verify">Submit Transaction ID</button>
        </form>
    </div>
</div>

<script>
const tranId = "<?php echo $tran_id; ?>";
const amount = "<?php echo $amount; ?>";
const supabaseUrl = "<?php echo $supabase_url; ?>";
const supabaseAnonKey = "<?php echo $supabase_key; ?>";

function copyNumber() {
    const num = document.getElementById('recNumber').innerText;
    navigator.clipboard.writeText(num).then(() => {
        alert("Number copied: " + num);
    });
}

function handlePaymentSuccess() {
    window.location.href = "../../payment_success.php?method=swapnopay&amount=" + amount + "&payment_id=" + encodeURIComponent(tranId);
}

// 1. Supabase Realtime WebSocket Connection
if (supabaseUrl && supabaseAnonKey && typeof supabase !== 'undefined') {
    try {
        const client = supabase.createClient(supabaseUrl, supabaseAnonKey);
        client
            .channel('order-status-' + tranId)
            .on('postgres_changes', {
                event: 'UPDATE',
                schema: 'public',
                table: 'orders',
                filter: 'tran_id=eq.' + tranId
            }, (payload) => {
                if (payload.new && payload.new.status === 'PAID') {
                    handlePaymentSuccess();
                }
            })
            .subscribe((status) => {
                console.log("Supabase Realtime Status:", status);
            });
    } catch(e) {
        console.error("Supabase Realtime error:", e);
    }
}

// 2. HTTP Polling Fallback (every 3 seconds)
setInterval(() => {
    fetch("check_status.php?tran_id=" + encodeURIComponent(tranId))
        .then(res => res.json())
        .then(data => {
            if (data && data.status === 'PAID') {
                handlePaymentSuccess();
            }
        })
        .catch(err => console.log("Poll error:", err));
}, 3000);
</script>

</body>
</html>

