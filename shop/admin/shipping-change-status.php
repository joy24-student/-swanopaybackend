<?php
ob_start();
session_start();

// Start session with provided session_id if available
if (isset($_GET['session_id'])) {
    session_id($_GET['session_id']);
    session_start();
} else {
    session_start();
}

include("inc/config.php");
include("inc/functions.php");

// Check if the user is logged in or not
if (!isset($_SESSION['user'])) {
    header('location: login.php');
    exit;
}

$payment_id = $_REQUEST['id'];
$task = $_REQUEST['task'];

if ($task == 'Completed') {
    $statement = $pdo->prepare("UPDATE tbl_payment SET shipping_status=?, delivery_time=? WHERE payment_id=?");
    $statement->execute(['Shipped', date('Y-m-d H:i:s'), $payment_id]);

    // Fetch customer and order details for email
    $statement = $pdo->prepare("SELECT
                            t1.customer_id,
                            t1.customer_name,
                            t1.customer_email,
                            t1.customer_phone,
                            t1.paid_amount,
                            t1.payment_method,
                            t1.payment_date,
                            t1.txnid,
                            t1.shipping_cost,
                            t1.coupon_discount,
                            t1.customer_address,
                            t1.customer_city,
                            t1.customer_state,
                            t1.customer_zip,
                            t1.customer_country,
                            t2.product_name,
                            t2.size,
                            t2.color,
                            t2.quantity,
                            t2.unit_price
                            FROM tbl_payment t1
                            JOIN tbl_order t2
                            ON t1.payment_id = t2.payment_id
                            WHERE t1.payment_id=?");
    $statement->execute(array($payment_id));
    $result = $statement->fetchAll(PDO::FETCH_ASSOC);

    $customer_name = $result[0]['customer_name'];
    $customer_email = $result[0]['customer_email'];
    $customer_phone = $result[0]['customer_phone']; // Added for potential SMS
    $paid_amount = $result[0]['paid_amount'];
    $payment_method = $result[0]['payment_method'];
    $payment_date = $result[0]['payment_date'];
    $txnid = $result[0]['txnid'];
    $shipping_cost = $result[0]['shipping_cost'];
    $coupon_discount = $result[0]['coupon_discount'];
    $customer_address = $result[0]['customer_address'];
    $customer_city = $result[0]['customer_city'];
    $customer_state = $result[0]['customer_state'];
    $customer_zip = $result[0]['customer_zip'];
    $customer_country = getCountryName($pdo, $result[0]['customer_country']); // Assuming getCountryName exists

    // Prepare product details for email
    $product_details_html = '<table border="1" width="100%" cellpadding="5" cellspacing="0">
        <tr>
            <th>Product Name</th>
            <th>Size</th>
            <th>Color</th>
            <th>Quantity</th>
            <th>Unit Price</th>
        </tr>';
    foreach ($result as $row) {
        $product_details_html .= '<tr>
            <td>' . $row['product_name'] . '</td>
            <td>' . $row['size'] . '</td>
            <td>' . $row['color'] . '</td>
            <td>' . $row['quantity'] . '</td>
            <td>' . formatCurrency($row['unit_price']) . '</td>
        </tr>';
    }
    $product_details_html .= '</table>';

    // Get Admin Email from settings for "From" address
    $statement = $pdo->prepare("SELECT contact_email, base_url FROM tbl_settings WHERE id=1");
    $statement->execute();
    $settings_row = $statement->fetch(PDO::FETCH_ASSOC);
    $admin_email = $settings_row['contact_email'];
    $base_url = $settings_row['base_url'];

    // Construct invoice URL
    $invoice_url = $base_url . 'payment_success.php?method=' . ($payment_method == 'Cash on Delivery' ? 'cod' : 'sslcommerz') . '&tran_id=' . $payment_id . '&session_id=' . session_id();


    $subject = 'Your Order Has Been Shipped! - Order ID: ' . $txnid;
    $message = '
        <html>
        <head>
            <title>Order Shipped Notification</title>
        </head>
        <body>
            <p>Dear ' . $customer_name . ',</p>
            <p>Your order with Transaction ID <strong>' . $txnid . '</strong> has been shipped!</p>
            <p><strong>Order Details:</strong></p>
            ' . $product_details_html . '
            <p><strong>Total Paid Amount:</strong> ' . formatCurrency($paid_amount) . '</p>
            <p><strong>Shipping Cost:</strong> ' . formatCurrency($shipping_cost) . '</p>
            <p><strong>Coupon Discount:</strong> ' . formatCurrency($coupon_discount) . '</p>
            <p><strong>Payment Method:</strong> ' . $payment_method . '</p>
            <p><strong>Shipping Address:</strong><br>
                ' . $customer_address . ', ' . $customer_city . ', ' . $customer_state . '<br>
                ' . $customer_zip . ', ' . $customer_country . '
            </p>
            <p>Your estimated delivery time is ' . date('Y-m-d H:i:s') . ' (actual delivery might vary slightly).</p>
            <p>You can view your invoice here: <a href="' . $invoice_url . '">' . $invoice_url . '</a></p>
            <p>Thank you for shopping with us!</p>
            <p>Sincerely,<br>Your E-commerce Team</p>
        </body>
        </html>
    ';

    $headers = "From: " . $admin_email . "\r\n" .
               "Reply-To: " . $admin_email . "\r\n" .
               "X-Mailer: PHP/" . phpversion() . "\r\n" .
               "MIME-Version: 1.0\r\n" .
               "Content-Type: text/html; charset=ISO-8859-1\r\n";

    // Send email to customer
    mail($customer_email, $subject, $message, $headers);

    // TODO: Implement SMS notification here if a third-party SMS API is integrated.
    // For example: sendSMS($customer_phone, "Your order " . $txnid . " has been shipped!");

    $_SESSION['success_message'] = 'Shipping status updated to "Shipped" and notification email sent to customer.';
}

header('location: order.php');
exit;
?>