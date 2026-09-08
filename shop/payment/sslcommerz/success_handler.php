<?php
ob_start();
session_start();
session_regenerate_id(true);

include("../../admin/inc/config.php");

// --- 1. Get Transaction ID and Settings ---
$tran_id = $_GET['tran_id'] ?? '';

if (empty($tran_id)) {
    // If no transaction ID, redirect to a safe page or failure page
    header('location: ../../payment_fail.php');
    exit;
}

// Get SSLCommerz credentials and mode
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings = $statement->fetch(PDO::FETCH_ASSOC);

// Define API endpoint based on environment
$query_api_url = ($settings['sslcz_mode'] == 'sandbox')
    ? "https://sandbox.sslcommerz.com/validator/api/merchantTransIDvalidationAPI.php"
    : "https://securepay.sslcommerz.com/validator/api/merchantTransIDvalidationAPI.php";

// --- 2. Prepare Validation Data ---
$validation_data = [
    'store_id'     => $settings['sslcz_store_id'],
    'store_passwd' => $settings['sslcz_store_password'],
    'tran_id'      => $tran_id,
    'v'            => '1', // Version
    'format'       => 'json'
];

// --- 3. Perform Server-to-Server Validation ---
$handle = curl_init();
curl_setopt($handle, CURLOPT_URL, $query_api_url);
curl_setopt($handle, CURLOPT_TIMEOUT, 30);
curl_setopt($handle, CURLOPT_CONNECTTIMEOUT, 30);
curl_setopt($handle, CURLOPT_POST, 1);
curl_setopt($handle, CURLOPT_POSTFIELDS, $validation_data);
curl_setopt($handle, CURLOPT_RETURNTRANSFER, true);
curl_setopt($handle, CURLOPT_SSL_VERIFYPEER, FALSE); // Should be TRUE in production, FALSE for simple test

$content = curl_exec($handle);
curl_close($handle);

$response = json_decode($content, true);

// --- 4. Process Validation Response ---
if (isset($response['status']) && $response['status'] == 'VALID') {
    // Get details for updating payment info
    $bank_tran_id = $response['bank_tran_id'] ?? 'N/A';
    $card_type = $response['card_type'] ?? 'N/A';
    $paid_amount = $response['amount'] ?? 0;
    
    // Check if the order amount matches the validated amount to prevent fraud
    $statement = $pdo->prepare("SELECT paid_amount FROM tbl_payment WHERE txnid = ?");
    $statement->execute([$tran_id]);
    $db_payment = $statement->fetch(PDO::FETCH_ASSOC);

    // Basic amount check (Note: For absolute security, this needs to be float/decimal comparison)
    if ($db_payment && $db_payment['paid_amount'] == $paid_amount) {

        // A. Update the payment status to 'Completed'
        $statement = $pdo->prepare("UPDATE tbl_payment SET 
            payment_status = ?, 
            shipping_status = ?, 
            bank_transaction_info = ?, 
            ssl_payment_method = ?
            WHERE txnid = ?");
        
        $statement->execute([
            'Completed',
            'Processing', // Or your desired shipping status
            $bank_tran_id,
            $card_type,
            $tran_id
        ]);
        
        // B. Redirect the customer to the final success display page
        header("location: ../../payment_success.php?method=sslcommerz&tran_id=" . $tran_id . "&session_id=" . session_id());
        exit;
    } else {
        // Amount mismatch or order not found (Security Risk)
        error_log("SSLCOMMERZ SECURITY ALERT: Amount mismatch or order missing for TXNID: " . $tran_id);
        header("location: ../../payment_fail.php");
        exit;
    }
} else {
    // Payment was not VALID (e.g., FAILED, CANCELLED, PENDING, or an API error)
    // You can update the status to 'Failed' here if you want to be proactive.
    $statement = $pdo->prepare("UPDATE tbl_payment SET payment_status = ? WHERE txnid = ?");
    $statement->execute(['Failed', $tran_id]);
    
    header("location: ../../payment_fail.php");
    exit;
}

?>