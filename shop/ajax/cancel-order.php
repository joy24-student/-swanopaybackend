<?php
ob_start();
session_start();
require_once('../admin/inc/config.php');

header('Content-Type: application/json');

// Check if customer is logged in
if (!isset($_SESSION['customer'])) {
    echo json_encode(['status' => 'error', 'message' => 'You must be logged in to cancel orders']);
    exit;
}

$customer_id = $_SESSION['customer']['cust_id'];
$payment_id = isset($_POST['payment_id']) ? htmlspecialchars($_POST['payment_id']) : null;

if (!$payment_id) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid payment ID']);
    exit;
}

try {
    // Verify the order belongs to the customer and can be cancelled (within 24 hours and not shipped)
    $statement = $pdo->prepare("
        SELECT * FROM tbl_payment 
        WHERE payment_id = ? AND customer_id = ?
    ");
    $statement->execute(array($payment_id, $customer_id));
    $payment = $statement->fetch(PDO::FETCH_ASSOC);

    if (!$payment) {
        echo json_encode(['status' => 'error', 'message' => 'Order not found']);
        exit;
    }

    // Check if order can be cancelled (within 24 hours and not already shipped/delivered/cancelled)
    $order_time = strtotime($payment['payment_date']);
    $hours_elapsed = (time() - $order_time) / 3600;

    if ($hours_elapsed > 24) {
        echo json_encode(['status' => 'error', 'message' => 'Cancellation window has expired (24 hours). Please contact support.']);
        exit;
    }

    if ($payment['shipping_status'] !== 'Pending') {
        echo json_encode(['status' => 'error', 'message' => 'Cannot cancel orders that have already been shipped or delivered']);
        exit;
    }

    // Update payment status to Cancelled
    $update_stmt = $pdo->prepare("
        UPDATE tbl_payment 
        SET payment_status = 'Cancelled', shipping_status = 'Cancelled'
        WHERE payment_id = ?
    ");
    $update_stmt->execute(array($payment_id));

    // Log the cancellation
    error_log("Order cancelled - Payment ID: $payment_id, Customer: $customer_id, Time: " . date('Y-m-d H:i:s'));

    echo json_encode([
        'status' => 'success',
        'message' => 'Order cancelled successfully. Refund will be processed within 5-7 business days.'
    ]);
    exit;

} catch (PDOException $e) {
    error_log("Cancel order error: " . $e->getMessage());
    echo json_encode(['status' => 'error', 'message' => 'Database error: ' . htmlspecialchars($e->getMessage())]);
    exit;
}
?>
