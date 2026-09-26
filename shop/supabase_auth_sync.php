<?php
// Supabase Authentication Synchronization Endpoint
if (session_status() == PHP_SESSION_NONE) {
    session_start();
}
header('Content-Type: application/json');

require_once __DIR__ . '/admin/inc/config.php';
require_once __DIR__ . '/admin/inc/functions.php';

$raw = file_get_contents('php://input');
$data = json_decode($raw, true);

if (!$data) {
    $data = $_POST;
}

$email = filter_var(trim($data['email'] ?? ''), FILTER_VALIDATE_EMAIL);
$supabase_uid = trim($data['supabase_uid'] ?? '');
$name = trim(strip_tags($data['name'] ?? 'Google User'));
$avatar_url = trim($data['avatar_url'] ?? '');
$google_id = trim($data['google_id'] ?? '');

if (!$email) {
    echo json_encode(['success' => false, 'message' => 'Valid email is required.']);
    exit;
}

try {
    // 1. Check if user exists by email, supabase_uid, or google_id
    $stmt = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email = ? OR (supabase_uid IS NOT NULL AND supabase_uid = ?) OR (google_id IS NOT NULL AND google_id = ?) LIMIT 1");
    $stmt->execute([$email, $supabase_uid, $google_id]);
    $customer = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($customer) {
        // Update user record with latest supabase_uid, google_id, avatar_url and ensure active
        $updateStmt = $pdo->prepare("UPDATE tbl_customer SET 
            supabase_uid = COALESCE(NULLIF(?, ''), supabase_uid),
            google_id = COALESCE(NULLIF(?, ''), google_id),
            avatar_url = COALESCE(NULLIF(?, ''), avatar_url),
            cust_status = 1
            WHERE cust_id = ?");
        $updateStmt->execute([$supabase_uid, $google_id, $avatar_url, $customer['cust_id']]);

        // Refresh user record
        $stmt = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id = ?");
        $stmt->execute([$customer['cust_id']]);
        $customer = $stmt->fetch(PDO::FETCH_ASSOC);
    } else {
        // Create new customer
        $token = md5(time() . $email);
        $datetime = date('Y-m-d H:i:s');
        $timestamp = time();

        $insertStmt = $pdo->prepare("INSERT INTO tbl_customer (
            cust_name, cust_email, cust_password, cust_token,
            cust_datetime, cust_timestamp, cust_status,
            supabase_uid, google_id, avatar_url
        ) VALUES (?, ?, '', ?, ?, ?, 1, ?, ?, ?) RETURNING cust_id");

        $insertStmt->execute([
            $name ?: 'Google User',
            $email,
            $token,
            $datetime,
            $timestamp,
            $supabase_uid,
            $google_id,
            $avatar_url
        ]);

        $newId = $insertStmt->fetchColumn();
        if (!$newId) {
            $newId = $pdo->lastInsertId();
        }

        $stmt = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id = ?");
        $stmt->execute([$newId]);
        $customer = $stmt->fetch(PDO::FETCH_ASSOC);
    }

    if ($customer) {
        unset($customer['cust_password']);
        $_SESSION['customer'] = $customer;

        // Restore cart from DB
        if (function_exists('loadCartFromDatabase')) {
            loadCartFromDatabase($pdo, $customer['cust_id']);
        }

        $redirect = !empty($_SESSION['cart_p_id']) ? BASE_URL . 'checkout.php' : BASE_URL . 'dashboard.php';
        echo json_encode(['success' => true, 'redirect' => $redirect]);
        exit;
    } else {
        echo json_encode(['success' => false, 'message' => 'Failed to initialize customer account.']);
        exit;
    }
} catch (Exception $e) {
    error_log("Supabase Auth Sync Error: " . $e->getMessage());
    echo json_encode(['success' => false, 'message' => 'Server error during sync: ' . $e->getMessage()]);
    exit;
}

