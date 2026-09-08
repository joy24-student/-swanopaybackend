<?php
// Define log file path
$log_file = __DIR__ . '/ipn_log.txt';
$log_data = date('Y-m-d H:i:s') . " --- SSLCOMMERZ IPN HIT ---\n";

// Log all POST data received
$log_data .= "POST Data: " . print_r($_POST, true) . "\n";

include("../../admin/inc/config.php");

// Check for required data
if (empty($_POST['tran_id']) || empty($_POST['status'])) {
    $log_data .= "RESULT: Invalid IPN data (Missing tran_id or status)\n";
    file_put_contents($log_file, $log_data, FILE_APPEND);
    die("Invalid IPN data"); // This line is crucial for SSLCommerz
}

$tran_id = $_POST['tran_id'];
$status = $_POST['status'];

$payment_status = ($status == 'VALID') ? 'Completed' : 'Failed';
$shipping_status = ($status == 'VALID') ? 'Processing' : 'Pending';

$bank_tran_id = $_POST['bank_tran_id'] ?? '';
$card_type = $_POST['card_type'] ?? '';

// Update payment record in database
try {
    $statement = $pdo->prepare("UPDATE tbl_payment SET 
        payment_status = ?, 
        shipping_status = ?, 
        bank_transaction_info = ?, 
        ssl_payment_method = ?
        WHERE txnid = ?");

    $statement->execute([
        $payment_status,
        $shipping_status,
        $bank_tran_id,
        $card_type,
        $tran_id
    ]);

    $log_data .= "RESULT: SUCCESS. Status updated to: " . $payment_status . " for TXNID: " . $tran_id . "\n";

} catch (\PDOException $e) {
    $log_data .= "RESULT: DB ERROR. " . $e->getMessage() . "\n";
}

// Send success response
$log_data .= "---------------------------------------\n";
file_put_contents($log_file, $log_data, FILE_APPEND);

echo "IPN Processed"; // This exact response is required by SSLCommerz
?>