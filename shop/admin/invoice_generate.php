<?php
ob_start();

require_once('header.php');
require_once('inc/config.php'); // Include database connection

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

// This page generates an invoice, it does not handle new payments or session management
// Thus, session handling and cart clearing functions are not needed here.
?>
<div class="row">
    <div class="col-md-12 text-center">

        <?php if (isset($_GET['method']) && isset($_GET['tran_id'])): ?>
            <?php
            $method = $_GET['method'];
            $tran_id = $_GET['tran_id'];

            // Fetch payment details
            $stmt = $pdo->prepare("SELECT * FROM tbl_payment WHERE payment_id = ?"); // Use payment_id for both COD and others
            $stmt->execute([$tran_id]);
            $payment = $stmt->fetch(PDO::FETCH_ASSOC);

            if ($payment) {
                $payment_id = $payment['id']; // Get the primary ID
                $cust_id = $payment['customer_id'];
                $paid_amount = $payment['paid_amount'];

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

                // Fetch order items associated with this payment ID
                $stmt_order = $pdo->prepare("SELECT * FROM tbl_order WHERE payment_id = ?");
                $stmt_order->execute([$tran_id]);
                $order_items = $stmt_order->fetchAll(PDO::FETCH_ASSOC);
                // Provide a standard variable name used later in templates
                $orders = is_array($order_items) ? $order_items : [];

                // --- DISPLAY INVOICE ---
            ?>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
            <div class="page">
                <div class="container">
                    <div class="row">
                        <div class="col-md-12 text-center">
                            <div class="invoice-container" id="printableArea">
                                <br>
                                <center>
                                    <?php
                                    // Fetch logo from tbl_settings
                                    $stmt_settings = $pdo->prepare("SELECT logo FROM tbl_settings WHERE id = 1");
                                    $stmt_settings->execute();
                                    $settings = $stmt_settings->fetch(PDO::FETCH_ASSOC);
                                    $logo = $settings['logo'] ?? 'default_logo.png'; // Fallback to a default image if not found
                                    ?>
                                    <img src="../assets/uploads/<?php echo $logo; ?>" alt="logo image" class="img-fluid" style="max-width:220px; height:auto; object-fit:contain;" />
                                </center>
                                <?php if ($method !== 'cod'): ?>
                                    <div class="success-payment">
                                        <h2 class="text-success">
                                            <i class="fa fa-check-circle success-icon" style="font-size: 80px; color: #28a745; margin-bottom: 20px;"></i>
                                            Payment Successful
                                        </h2>
                                        <p>Thank you for your purchase. Below is your invoice:</p>
                                    </div>
                                <?php endif; ?>
                                <div id="qrcode" style="width:100px; height:100px; padding: 10px;float:right; margin-top: -100px;"></div>
                                <script>
                                    document.addEventListener("DOMContentLoaded", function() {
                                        var pageUrl = '<?php echo BASE_URL; ?>admin/invoice_generate.php?method=<?php echo $method; ?>&tran_id=<?php echo $tran_id; ?>';
                                        if (pageUrl) {
                                            new QRCode(document.getElementById("qrcode"), {
                                                text: pageUrl,
                                                width: 80,
                                                height: 80,
                                                colorDark: "#000000",
                                                colorLight: "#ffffff",
                                                correctLevel: QRCode.CorrectLevel.H
                                            });
                                        }
                                    });
                                </script>

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
                                            <td><?= htmlspecialchars($payment['payment_method']) ?></td>
                                        </tr>
                                        <tr>
                                            <th>Payment Status</th>
                                            <td><?= htmlspecialchars($payment['payment_status']) ?></td>
                                        </tr>
                                        <tr>
                                            <?php if ($method !== 'cod'): ?> <th>Payment Date</th>
                                            <td><?= htmlspecialchars($payment['payment_date']) ?></td>
                                        </tr>
                                        <?php endif; ?>
                                        <tr>
                                            <th>Total Amount</th>
                                            <td><?= LANG_VALUE_1 . number_format($payment['paid_amount'], 2) ?></td>
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
                                                    <td><?= LANG_VALUE_1 . number_format($order['unit_price'] * $order['quantity'], 2) ?></td>
                                                </tr>
                                            <?php endforeach; ?>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            <br><br>
                            <button onclick="printInvoice()" class="btn btn-secondary">
                                <i class="fa fa-print"></i> Print Invoice
                            </button>
                            <br><br>
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
            <div class="alert alert-danger text-center">No payment method or transaction ID detected.</div>
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
        // Optionally, reload to restore the page as it was before printing,
        // but for an invoice page, simply returning might be enough.
        // If there are dynamic elements that need resetting, location.reload() is good.
        // For a static invoice view, it might be unnecessary.
        // location.reload();
    }
</script>
<?php require_once('footer.php'); ?>
