<?php
ob_start();
// Start or resume session. Check for session_id if it's passed via GET (common for gateway redirects)
if (isset($_GET['session_id'])) {
    session_id($_GET['session_id']);
    session_start();
} else {
    session_start();
}

// =========================================================================
// === NOTE: CART/CHECKOUT DATA IS RETAINED ON PAYMENT FAILURE ===
// The previous code block to unset session variables has been removed.
// This allows the user to return to cart or checkout and try again.
// =========================================================================

require_once('header.php');
// Include database connection config
require_once('admin/inc/config.php');

// Get URL parameters
$tran_id = $_GET['tran_id'] ?? 'N/A';
$method = $_GET['method'] ?? 'Unknown';
$fail_reason = $_GET['reason'] ?? 'Transaction failed or was canceled.';

// You may need to fetch the payment record here to get the paid_amount if it was recorded
// before being failed by the gateway, but for a typical failure page, a simple message often suffices.

$paid_amount = '0.00'; // Default
$customer_name = $_SESSION['customer']['cust_name'] ?? 'Customer';

if ($tran_id !== 'N/A' && $method == 'sslcommerz') {
    // Attempt to fetch the initial order details from tbl_payment if the transaction ID is present
    $stmt = $pdo->prepare("SELECT paid_amount, txnid, payment_method FROM tbl_payment WHERE txnid = ?");
    $stmt->execute([$tran_id]);
    $payment = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if ($payment) {
        // Use the amount from the database record if found
        $paid_amount = $payment['paid_amount'];
        
        // OPTIONAL: You might also want to set the payment and shipping status 
        // in tbl_payment to 'Failed' here if it wasn't handled by the IPN.
    }
}
?>

<div class="page-banner" style="background-image: url(assets/uploads/<?php echo $banner_checkout; ?>);">
    <div class="inner">
        <h1>Payment Failed</h1>
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                
                <div class="failure-box text-center">
                    <i class="fa fa-times-circle failure-icon" style="color: #dc3545; font-size: 80px; margin-bottom: 20px;"></i>
                    <h3 style="color: #dc3545;">Payment Unsuccessful!</h3>
                    
                    <div class="alert alert-danger" role="alert" style="padding: 20px; border-radius: 8px;">
                        <h4 class="alert-heading">Transaction Could Not Be Completed</h4>
                        
                        <?php if ($tran_id !== 'N/A'): ?>
                            <p><strong>Transaction ID:</strong> <span style="font-weight: bold;"><?= htmlspecialchars($tran_id) ?></span></p>
                        <?php endif; ?>

                        <?php if ($method !== 'Unknown'): ?>
                            <p><strong>Payment Method:</strong> <?= htmlspecialchars($method) ?></p>
                        <?php endif; ?>
                        
                        <hr>
                        
                        <p class="mb-0">
                            <strong>Reason:</strong> <?= htmlspecialchars($fail_reason) ?>
                        </p>
                        
                        <?php if ($paid_amount > 0): ?>
                            <p class="mt-2">
                                An attempt was made to pay <b><?= LANG_VALUE_1 . number_format($paid_amount, 2) ?></b>.
                            </p>
                        <?php endif; ?>
                    </div>
                    
                    <p class="lead mt-4">
                        Your cart contents are saved. Please check your payment details or try a different method to complete your order.
                    </p>

                    <div class="mt-4">
                        <a href="cart.php" class="btn btn-warning" style="margin-right: 15px;">
                            <i class="fa fa-shopping-cart"></i> Return to Cart
                        </a>
                        <!-- Sending them back to checkout allows them to try the payment again -->
                        <a href="checkout.php" class="btn btn-primary">
                            <i class="fa fa-credit-card"></i> Try Checkout Again
                        </a>
                    </div>
                    <div class="mt-3">
                         <a href="contact.php" class="btn btn-default" style="border: 1px solid #ccc;">
                            <i class="fa fa-envelope"></i> Contact Support
                        </a>
                    </div>
                </div>

            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>