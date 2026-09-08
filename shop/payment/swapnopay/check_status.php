<?php
ob_start();
session_start();
require_once("../../admin/inc/config.php");

header('Content-Type: application/json');

$tran_id = strip_tags($_GET['tran_id'] ?? '');
if (empty($tran_id)) {
    echo json_encode(['status' => 'ERROR', 'message' => 'Missing tran_id']);
    exit;
}

$supabase_url = defined('SUPABASE_URL') ? SUPABASE_URL : getenv('SUPABASE_URL');
$supabase_key = defined('SUPABASE_SERVICE_KEY') && !empty(SUPABASE_SERVICE_KEY) 
    ? SUPABASE_SERVICE_KEY 
    : (defined('SUPABASE_ANON_KEY') ? SUPABASE_ANON_KEY : getenv('SUPABASE_ANON_KEY'));

if (!empty($supabase_url) && !empty($supabase_key)) {
    $clean_supabase_url = rtrim($supabase_url, '/');
    $ch = curl_init("{$clean_supabase_url}/rest/v1/orders?tran_id=eq.{$tran_id}&select=status,paid_at,payment_method,amount&limit=1");
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        "apikey: {$supabase_key}",
        "Authorization: Bearer {$supabase_key}"
    ]);
    $res = curl_exec($ch);
    curl_close($ch);
    $orders = json_decode($res, true);
    if (!empty($orders[0])) {
        echo json_encode([
            'status' => $orders[0]['status'],
            'amount' => $orders[0]['amount'],
            'paid_at' => $orders[0]['paid_at']
        ]);
        exit;
    }
}

// Fallback to local table
try {
    $stmt = $pdo->prepare("SELECT payment_status, paid_amount FROM tbl_payment WHERE payment_id = ? LIMIT 1");
    $stmt->execute([$tran_id]);
    $row = $stmt->fetch();
    if ($row) {
        $st = ($row['payment_status'] === 'Completed') ? 'PAID' : 'PENDING';
        echo json_encode(['status' => $st, 'amount' => $row['paid_amount']]);
        exit;
    }
} catch (Exception $e) {}

echo json_encode(['status' => 'PENDING']);

