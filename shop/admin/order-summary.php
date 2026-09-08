
<?php
require_once('header.php');

// Get order ID
$order_id = $_GET['order_id'];

// Fetch shop information
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$shop_info = $statement->fetch(PDO::FETCH_ASSOC);

// Fetch order details
$statement = $pdo->prepare("SELECT * FROM tbl_order WHERE id = ?");
$statement->execute([$order_id]);
$order = $statement->fetch(PDO::FETCH_ASSOC);

// Fetch customer details
$statement = $pdo->prepare("
    SELECT c.* 
    FROM tbl_order o 
    JOIN tbl_customer c ON o.customer_id = c.cust_id 
    WHERE o.id = ?
");
$statement->execute([$order_id]);
$customer = $statement->fetch(PDO::FETCH_ASSOC);

// Fetch order items
$statement = $pdo->prepare("
    SELECT * FROM tbl_order_item 
    WHERE order_id = ?
");
$statement->execute([$order_id]);
$order_items = $statement->fetchAll(PDO::FETCH_ASSOC);
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Order Summary - #<?= $order_id ?></title>
    <style>
        body { font-family: Arial, sans-serif; }
        .invoice-box { max-width: 800px; margin: auto; padding: 30px; border: 1px solid #eee; }
        .header { text-align: center; margin-bottom: 40px; }
        .address-section { display: flex; margin-bottom: 30px; }
        .billing-address, .shipping-address { width: 50%; }
        .items-table { width: 100%; border-collapse: collapse; }
        .items-table th, .items-table td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        .summary-section { margin-top: 30px; text-align: right; }
        .summary-table { display: inline-block; }
        .summary-table td { padding: 5px 10px; }
        .print-button { text-align: center; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="invoice-box">
        <div class="header">
            <img src="<?= BASE_URL ?>assets/uploads/<?= $shop_info['logo'] ?>" alt="Logo" height="60">
            <h1><?= $shop_info['website_name'] ?></h1>
            <p>Order Summary</p>
        </div>

        <div class="order-info">
            <p><strong>Order ID:</strong> #<?= $order_id ?></p>
            <p><strong>Order Date:</strong> <?= $order['order_date'] ?></p>
            <p><strong>Payment Method:</strong> <?= $order['payment_method'] ?></p>
            <p><strong>Order Status:</strong> <?= $order['order_status'] ?></p>
        </div>

        <div class="address-section">
            <div class="billing-address">
                <h3>Billing Address</h3>
                <p><?= $customer['cust_b_name'] ?></p>
                <p><?= $customer['cust_b_cname'] ?></p>
                <p><?= $customer['cust_b_address'] ?></p>
                <p><?= $customer['cust_b_city'] ?>, <?= $customer['cust_b_state'] ?> <?= $customer['cust_b_zip'] ?></p>
                <p>Phone: <?= $customer['cust_b_phone'] ?></p>
            </div>
            
            <div class="shipping-address">
                <h3>Shipping Address</h3>
                <p><?= $customer['cust_s_name'] ?></p>
                <p><?= $customer['cust_s_cname'] ?></p>
                <p><?= $customer['cust_s_address'] ?></p>
                <p><?= $customer['cust_s_city'] ?>, <?= $customer['cust_s_state'] ?> <?= $customer['cust_s_zip'] ?></p>
                <p>Phone: <?= $customer['cust_s_phone'] ?></p>
            </div>
        </div>

        <table class="items-table">
            <thead>
                <tr>
                    <th>Product</th>
                    <th>Price</th>
                    <th>Qty</th>
                    <th>Total</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach($order_items as $item): ?>
                <tr>
                    <td><?= $item['product_name'] ?></td>
                    <td><?= $item['unit_price'] ?></td>
                    <td><?= $item['quantity'] ?></td>
                    <td><?= $item['total_price'] ?></td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>

        <div class="summary-section">
            <table class="summary-table">
                <tr>
                    <td>Subtotal:</td>
                    <td><?= $order['subtotal'] ?></td>
                </tr>
                <tr>
                    <td>Shipping:</td>
                    <td><?= $order['shipping_cost'] ?></td>
                </tr>
                <tr>
                    <td>Tax:</td>
                    <td><?= $order['tax'] ?></td>
                </tr>
                <tr>
                    <td>Discount:</td>
                    <td><?= $order['discount'] ?></td>
                </tr>
                <tr>
                    <td><strong>Grand Total:</strong></td>
                    <td><strong><?= $order['total'] ?></strong></td>
                </tr>
            </table>
        </div>
    </div>

    <div class="print-button">
        <button onclick="window.print()" class="btn btn-primary">
            <i class="fa fa-print"></i> Print Summary
        </button>
    </div>
</body>
</html>
<?php require_once('footer.php'); ?>
