<?php
/**
 * Cart Sync Simple Test
 * Add an item, check database - simple verification
 */

session_start();
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

// Only for logged-in users
if (!isset($_SESSION['customer']['cust_id'])) {
    die('<h2>Please <a href="login.php">login first</a> to test cart sync</h2>');
}

$cust_id = $_SESSION['customer']['cust_id'];

?>
<!DOCTYPE html>
<html>
<head>
    <title>Simple Cart Sync Test</title>
    <style>
        body { font-family: Arial; max-width: 900px; margin: 20px auto; padding: 20px; }
        .test-item { background: #f9f9f9; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #007bff; }
        .success { border-left-color: #28a745; background: #d4edda; }
        .error { border-left-color: #dc3545; background: #f8d7da; }
        button { padding: 10px 20px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; }
        button:hover { background: #0056b3; }
        table { width: 100%; border-collapse: collapse; margin: 15px 0; }
        th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #f8f9fa; }
    </style>
</head>
<body>
    <h1>Cart Sync Test - Step by Step</h1>
    
    <div class="test-item">
        <h3>Step 1: Check Your Login Status</h3>
        <p><strong>Customer ID:</strong> <?php echo $cust_id; ?></p>
        <p><strong>Customer Name:</strong> <?php echo $_SESSION['customer']['cust_name']; ?></p>
        <p class="success">✓ You are logged in</p>
    </div>

    <div class="test-item">
        <h3>Step 2: Current Session Cart</h3>
        <?php
        if (isset($_SESSION['cart_p_id']) && count($_SESSION['cart_p_id']) > 0) {
            echo '<p>Items in session: ' . count($_SESSION['cart_p_id']) . '</p>';
            echo '<table>';
            echo '<tr><th>Product</th><th>Qty</th><th>Price</th></tr>';
            foreach ($_SESSION['cart_p_id'] as $i => $pid) {
                echo '<tr>';
                echo '<td>' . ($_SESSION['cart_p_name'][$i] ?? 'Unknown') . '</td>';
                echo '<td>' . ($_SESSION['cart_p_qty'][$i] ?? 0) . '</td>';
                echo '<td>' . ($_SESSION['cart_p_current_price'][$i] ?? 0) . '</td>';
                echo '</tr>';
            }
            echo '</table>';
        } else {
            echo '<p>⚠️ Session cart is empty - add items first!</p>';
        }
        ?>
    </div>

    <div class="test-item">
        <h3>Step 3: Database Cart Contents</h3>
        <?php
        try {
            $stmt = $pdo->prepare("SELECT * FROM tbl_customer_carts WHERE customer_id = ? ORDER BY added_at DESC");
            $stmt->execute([$cust_id]);
            $items = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            if (count($items) > 0) {
                echo '<p class="success">Found ' . count($items) . ' item(s) in database</p>';
                echo '<table>';
                echo '<tr><th>Product</th><th>Qty</th><th>Price</th><th>Added At</th></tr>';
                foreach ($items as $item) {
                    echo '<tr>';
                    echo '<td>' . $item['product_name'] . '</td>';
                    echo '<td>' . $item['quantity'] . '</td>';
                    echo '<td>$' . $item['price_at_add'] . '</td>';
                    echo '<td>' . $item['added_at'] . '</td>';
                    echo '</tr>';
                }
                echo '</table>';
            } else {
                echo '<p class="error">⚠️ No items found in database for customer ' . $cust_id . '</p>';
                echo '<p>Add items to your cart and they should appear here automatically!</p>';
            }
        } catch (PDOException $e) {
            echo '<p class="error">✗ Error: ' . $e->getMessage() . '</p>';
        }
        ?>
    </div>

    <div class="test-item">
        <h3>Step 4: What to Do Next</h3>
        <ol>
            <li><a href="index.php">Go to store</a> and add a product to cart</li>
            <li>Come back to this page</li>
            <li>Check if item appears in "Database Cart Contents" above</li>
            <li>If yes ✓ - Sync is working!</li>
            <li>If no ✗ - See troubleshooting below</li>
        </ol>
    </div>

    <div class="test-item error">
        <h3>Troubleshooting</h3>
        <p><strong>If items not appearing in database:</strong></p>
        <ol>
            <li>Check error log: <code>C:\xampp\apache\logs\error.log</code></li>
            <li>Look for "addOrUpdateCartItem" errors</li>
            <li>Check if tbl_customer_carts table exists</li>
            <li>Verify functions.php has new functions</li>
            <li>Restart Apache</li>
            <li><a href="debug_cart_sync.php">Go to detailed debug page</a></li>
        </ol>
    </div>

    <hr>
    <p><a href="index.php">&lt; Back to Store</a> | <a href="debug_cart_sync.php">Detailed Debug Info &gt;</a></p>
</body>
</html>
