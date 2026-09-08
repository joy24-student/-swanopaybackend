<?php
// Add at the very top
ob_start();
if (isset($_GET['session_id'])) {
    session_id($_GET['session_id']);
    session_start();
} else {
    session_start();
}

require_once('header.php');
require_once('admin/inc/config.php'); // Include database connection

// Function to fetch country name
function getCountryName($pdo, $country_id) {
    // Assuming country_id is used for lookup
    $statement = $pdo->prepare("SELECT country_name FROM tbl_country WHERE country_id = ?");
    $statement->execute([$country_id]);
    $result = $statement->fetch(PDO::FETCH_ASSOC);
    return $result ? $result['country_name'] : 'N/A';
}

// Function to format currency
function formatCurrency($amount) {
    // Assuming LANG_VALUE_1 holds the currency symbol (e.g., '$')
    return LANG_VALUE_1 . number_format($amount, 2);
}

// Function to clear all cart and checkout session variables
function clearCartSessions() {
    // Clear the main cart content arrays
    unset($_SESSION['cart_p_id']);
    unset($_SESSION['cart_size_id']);
    unset($_SESSION['cart_size_name']);
    unset($_SESSION['cart_color_id']);
    unset($_SESSION['cart_color_name']);
    unset($_SESSION['cart_p_qty']);
    unset($_SESSION['cart_p_current_price']);
    
    // Clear related checkout and payment data
    unset($_SESSION['final_total']);
    unset($_SESSION['shipping_cost']);
    unset($_SESSION['coupon_code']);
    unset($_SESSION['coupon_discount']);
    unset($_SESSION['coupon_id']);
    unset($_SESSION['payment_data']); 
    unset($_SESSION['billing_address_details']);
    unset($_SESSION['shipping_address_details']);
}
?>
<div class="row">
    <div class="col-md-12 text-center">

        <?php if (isset($_GET['method'])): ?>
        
            <?php if ($_GET['method'] == 'cod'): ?>
                <?php 
                // === COD SUCCESS: Clear Cart Here ===
                clearCartSessions();
                if (isset($_SESSION['customer']['cust_id']) && !empty($_SESSION['customer']['cust_id'])) {
                    if (function_exists('clearCartFromDatabase')) {
                        clearCartFromDatabase($pdo, $_SESSION['customer']['cust_id']);
                    } else {
                        error_log('clearCartFromDatabase function not found during COD success.');
                    }
                }
                // ===================================
                ?>
                <div class="success-box">
                    <i class="fa fa-check-circle success-icon"></i>
                    <h3>Order Placed Successfully!</h3>
                    <div class="alert alert-success">
                        <h4>Cash on Delivery</h4>
                        <p>Your order #<?= htmlspecialchars($_GET['tran_id'] ?? '') ?> has been confirmed.</p>
                        <p>Please have <b><?= LANG_VALUE_1 . htmlspecialchars($_GET['amount'] ?? '0.00') ?></b> ready when your order is delivered.</p>
                    </div>
                </div>
                <div class="mt-4">
                    <a href="dashboard.php" class="btn btn-success"><i class="fa fa-user"></i> <?= LANG_VALUE_91 ?></a>
                    <a href="index.php" class="btn btn-primary"><i class="fa fa-home"></i> Continue Shopping</a>
                </div>
            <?php elseif (($_GET['method'] == 'sslcommerz' || $_GET['method'] == 'swapnopay' || $_GET['method'] == 'SwapnoPay') && (isset($_GET['tran_id']) || isset($_GET['payment_id']))): ?>
                <?php
                $tran_id = strip_tags($_GET['tran_id'] ?? ($_GET['payment_id'] ?? ''));
                $method_param = strtolower($_GET['method']);

                // Fetch payment details - query by payment_id or txnid
                $stmt = $pdo->prepare("SELECT * FROM tbl_payment WHERE payment_id = ? OR txnid = ? LIMIT 1");
                $stmt->execute([$tran_id, $tran_id]);
                $payment = $stmt->fetch(PDO::FETCH_ASSOC);

                if ($payment) {
                    $payment_id = $payment['id']; // Get the primary ID
                    $cust_id = $payment['customer_id'];
                    $paid_amount = $payment['paid_amount'];

                    // Status is strictly updated by verified server-to-server webhook or SMS confirmation.
                    // payment_success.php only displays the current state of the order.

                    // Ensure we have the customer record to display email safely
                    $customer = null;
                    if (!empty($cust_id)) {
                        $stmt_c = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id = ? LIMIT 1");
                        $stmt_c->execute([$cust_id]);
                        $customer = $stmt_c->fetch(PDO::FETCH_ASSOC);
                    }
                    if (!$customer) {
                        $customer = ['cust_email' => ''];
                    }

                    // =================================================================
                    // === PAYMENT SUCCESS: Clear Cart Here ===
                    // =================================================================
                    clearCartSessions();
                    // Also clear persistent DB cart for this customer if logged in
                    $db_clear_cust_id = $cust_id ?? ($payment['customer_id'] ?? null);
                    if (!empty($db_clear_cust_id)) {
                        if (function_exists('clearCartFromDatabase')) {
                            clearCartFromDatabase($pdo, $db_clear_cust_id);
                        } else {
                            error_log('clearCartFromDatabase function not found during payment success.');
                        }
                    }
                    // =================================================================

                    // Fetch order items associated with this payment ID
                    $stmt_order = $pdo->prepare("SELECT * FROM tbl_order WHERE payment_id = ?");
                    $stmt_order->execute([$tran_id]);
                    $order_items = $stmt_order->fetchAll(PDO::FETCH_ASSOC);
                    // Provide a standard variable name used later in templates
                    $orders = is_array($order_items) ? $order_items : [];

                    // --- DISPLAY SUCCESS MESSAGE AND INVOICE ---
                ?>
             <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
                <div class="page">
                    <div class="container">
                        <div class="row">

                            <div class="col-md-12 text-center">
                                <div class="success-payment" id="printableArea">
                                    <br>
                                    <center>
                                        <img src="assets/uploads/<?php echo $logo; ?>" alt="logo image" class="img-fluid" style="max-width:220px; height:auto; object-fit:contain;" />
                                    </center>
                                    <div class="success-payment">
                                        <h2 class="text-success">                     <i class="fa fa-check-circle success-icon" style="font-size: 80px; color: #28a745; margin-bottom: 20px;"></i>
 Payment Successful </h2>
                                        <div id="qrcode" style="width:200px; height:200px; padding: 20px;float:right;"></div>
                                        <script>
                                            document.addEventListener("DOMContentLoaded", function() {
                                                // Build a verification URL that opens this success page with the tran_id
                                                var pageUrl = <?= isset($tran_id) ? json_encode(BASE_URL . 'payment_success.php?method=' . urlencode($_GET['method'] ?? 'swapnopay') . '&tran_id=' . urlencode($tran_id)) : 'null' ?>;
                                                if (pageUrl) {
                                                    new QRCode(document.getElementById("qrcode"), {
                                                        text: pageUrl,
                                                        width: 100,
                                                        height: 100,
                                                        colorDark: "#000000",
                                                        colorLight: "#ffffff",
                                                        correctLevel: QRCode.CorrectLevel.H
                                                    });
                                                }
                                            });
                                        </script>
                                        <p>Thank you for your purchase. Below is your invoice:</p>
                                    </div>
                                    <div class="invoice-box table-responsive">
                                        <h4 class="text-left">Invoice</h4>
                                        <table class="table table-bordered">
                                            <tr>
                                                <th>Transaction ID</th>
                                                <td><?= htmlspecialchars($tran_id) ?></td>
                                            </tr>
                                            <tr>
                                                <th>Customer Name</th>
                                                <td><?= htmlspecialchars($payment['customer_name']) ?></td>
                                            </tr>
                                            <tr>
                                                <th>Email</th>
                                                <td><?= htmlspecialchars($customer['cust_email']) ?></td>
                                            </tr>
                                            <tr>
                                                <th>Phone</th>
                                                <td><?= htmlspecialchars($payment['billing_phone'] ?? 'N/A') ?></td>
                                            </tr>
                                            <tr>
                                                <th>Payment Method</th>
                                                <td>
                                                    <span class="badge" style="background:#059669; color:#fff; padding:6px 12px; font-size:13px; font-weight:bold; border-radius:4px;">
                                                        <?= htmlspecialchars($payment['payment_method'] ?? 'SwapnoPay') ?>
                                                    </span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <th>Payment Status</th>
                                                <td><span style="color:#16a34a; font-weight:bold;"><?= htmlspecialchars($payment['payment_status']) ?></span></td>
                                            </tr>
                                            <tr>
                                                <th>Payment Date</th>
                                                <td><?= htmlspecialchars($payment['payment_date']) ?></td>
                                            </tr>
                                            <tr>
                                                <th>Total Amount</th>
                                                <td><b><?= LANG_VALUE_1 . number_format($payment['paid_amount'], 2) ?></b></td>
                                            </tr>
                                        </table>

                                        <h4 class="text-left mt-4">Ordered Products</h4>
                                        <table class="table table-bordered">
                                            <thead>
                                                <tr>
                                                    <th>Product</th>
                                                    <th>Size</th>
                                                    <th>Color</th>
                                                    <th>Quantity</th>
                                                    <th>Unit Price</th>
                                                    <th>Total</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <?php foreach ($orders as $order): ?>
                                                    <tr>
                                                        <td><?= htmlspecialchars($order['product_name']) ?></td>
                                                        <td><?= htmlspecialchars($order['size']) ?></td>
                                                        <td><?= htmlspecialchars($order['color']) ?></td>
                                                        <td><?= htmlspecialchars($order['quantity']) ?></td>
                                                        <td><?= LANG_VALUE_1 . number_format($order['unit_price'], 2) ?></td>
                                                        <td><?= LANG_VALUE_1 . number_format(((float)$order['quantity'] * (float)$order['unit_price']), 2) ?></td>
                                                    </tr>
                                                <?php endforeach; ?>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                                <br><br>
                                <button onclick="printInvoice()" class="btn btn-secondary">
                                    <i class="fa fa-print"></i> Print Invoice
                                </button><br><br>
                                <div class="mt-4">
                                    <a href="dashboard.php" class="btn btn-success"><i class="fa fa-user"></i> <?= LANG_VALUE_91 ?></a>
                                    <a href="index.php" class="btn btn-primary"><i class="fa fa-home"></i> Continue Shopping</a>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <?php
                } else {
                    echo '<div class="alert alert-danger text-center">Payment record not found. Please contact support.</div>';
                }
                ?>
            <?php else: ?>
                <div class="alert alert-danger text-center">Invalid payment method or missing transaction ID.</div>
            <?php endif; ?>
        <?php else: ?>
            <div class="alert alert-danger text-center">No payment method detected.</div>
        <?php endif; ?>
    </div>
</div>
<script>
    function printInvoice() {
        var printContents = document.getElementById('printableArea').innerHTML;
        var originalContents = document.body.innerHTML;
        document.body.innerHTML = printContents;
        window.print();
        document.body.innerHTML = originalContents;
        location.reload();
    }
</script>
<?php require_once('footer.php'); ?>