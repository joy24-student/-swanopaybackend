<?php
/**
 * Cart Sync Debug & Verification Script
 * This script helps diagnose why cart data isn't being synced to database
 */

session_start();
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

?>
<!DOCTYPE html>
<html>
<head>
    <title>Cart Sync Debug</title>
    <style>
        body { font-family: Arial, sans-serif; max-width: 1000px; margin: 20px auto; padding: 20px; }
        .status { padding: 15px; margin: 10px 0; border-radius: 5px; }
        .success { background: #d4edda; border: 1px solid #c3e6cb; color: #155724; }
        .error { background: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; }
        .warning { background: #fff3cd; border: 1px solid #ffeeba; color: #856404; }
        .info { background: #d1ecf1; border: 1px solid #bee5eb; color: #0c5460; }
        h2 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
        table { width: 100%; border-collapse: collapse; margin: 15px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #f8f9fa; }
        code { background: #f4f4f4; padding: 2px 5px; border-radius: 3px; }
        .test-section { background: #f9f9f9; padding: 15px; margin: 20px 0; border-radius: 5px; }
    </style>
</head>
<body>
    <h1>🛠️ Cart Sync Verification Dashboard</h1>
    
    <!-- Session Status -->
    <h2>1. Session Status</h2>
    <?php
    if (isset($_SESSION['customer']['cust_id'])) {
        echo '<div class="status success">';
        echo '<strong>✓ Logged In</strong>';
        echo '<br>Customer ID: ' . $_SESSION['customer']['cust_id'];
        echo '<br>Customer Name: ' . $_SESSION['customer']['cust_name'];
        echo '</div>';
        $is_logged_in = true;
    } else {
        echo '<div class="status error">';
        echo '<strong>✗ Not Logged In</strong>';
        echo '<br>Cart sync ONLY works for logged-in users!';
        echo '<br><a href="login.php">Go to Login</a>';
        echo '</div>';
        $is_logged_in = false;
    }
    ?>

    <!-- Database Connection Status -->
    <h2>2. Database Connection</h2>
    <?php
    try {
        $test_stmt = $pdo->prepare("SELECT 1");
        $test_stmt->execute();
        echo '<div class="status success">';
        echo '<strong>✓ Database Connected</strong>';
        echo '<br>PDO connection is active';
        echo '</div>';
        $db_ok = true;
    } catch (PDOException $e) {
        echo '<div class="status error">';
        echo '<strong>✗ Database Error</strong>';
        echo '<br>Error: ' . $e->getMessage();
        echo '</div>';
        $db_ok = false;
    }
    ?>

    <!-- Cart Table Status -->
    <h2>3. Cart Table Status</h2>
    <?php
    try {
        $test_stmt = $pdo->prepare("SELECT 1 FROM tbl_customer_carts LIMIT 1");
        $test_stmt->execute();
        echo '<div class="status success">';
        echo '<strong>✓ Table Exists</strong>';
        echo '<br>tbl_customer_carts table is present';
        
        // Get table info
        $info_stmt = $pdo->prepare("SELECT COUNT(*) as total FROM tbl_customer_carts");
        $info_stmt->execute();
        $info = $info_stmt->fetch(PDO::FETCH_ASSOC);
        echo '<br>Total items in table: ' . $info['total'];
        
        if ($is_logged_in) {
            $cust_stmt = $pdo->prepare("SELECT COUNT(*) as total FROM tbl_customer_carts WHERE customer_id = ?");
            $cust_stmt->execute([$_SESSION['customer']['cust_id']]);
            $cust_info = $cust_stmt->fetch(PDO::FETCH_ASSOC);
            echo '<br>Items for current customer: ' . $cust_info['total'];
        }
        echo '</div>';
        $table_ok = true;
    } catch (PDOException $e) {
        echo '<div class="status error">';
        echo '<strong>✗ Table Not Found</strong>';
        echo '<br>Error: ' . $e->getMessage();
        echo '<br>Solution: Run create_cart_table.php first';
        echo '</div>';
        $table_ok = false;
    }
    ?>

    <!-- Functions Check -->
    <h2>4. Required Functions</h2>
    <?php
    $functions = ['addOrUpdateCartItem', 'removeCartItem', 'updateCartItemQuantity', 'loadCartFromDatabase'];
    $all_functions_ok = true;
    
    foreach ($functions as $func) {
        if (function_exists($func)) {
            echo '<div class="status success">';
            echo '<strong>✓ ' . $func . '()</strong>';
            echo '<br>Function is defined and available';
            echo '</div>';
        } else {
            echo '<div class="status error">';
            echo '<strong>✗ ' . $func . '()</strong>';
            echo '<br>Function NOT found - update admin/inc/functions.php';
            echo '</div>';
            $all_functions_ok = false;
        }
    }
    ?>

    <!-- Session Cart Contents -->
    <h2>5. Session Cart Contents</h2>
    <?php
    if (isset($_SESSION['cart_p_id']) && count($_SESSION['cart_p_id']) > 0) {
        echo '<div class="status info">';
        echo '<strong>Session Cart Items:</strong>';
        echo '<br>Total items: ' . count($_SESSION['cart_p_id']);
        echo '</div>';
        
        echo '<table>';
        echo '<tr><th>Index</th><th>Product ID</th><th>Name</th><th>Size</th><th>Color</th><th>Qty</th><th>Price</th></tr>';
        
        foreach ($_SESSION['cart_p_id'] as $i => $p_id) {
            echo '<tr>';
            echo '<td>' . $i . '</td>';
            echo '<td>' . $p_id . '</td>';
            echo '<td>' . ($_SESSION['cart_p_name'][$i] ?? 'N/A') . '</td>';
            echo '<td>' . ($_SESSION['cart_size_name'][$i] ?? 'N/A') . '</td>';
            echo '<td>' . ($_SESSION['cart_color_name'][$i] ?? 'N/A') . '</td>';
            echo '<td>' . ($_SESSION['cart_p_qty'][$i] ?? 'N/A') . '</td>';
            echo '<td>' . ($_SESSION['cart_p_current_price'][$i] ?? 'N/A') . '</td>';
            echo '</tr>';
        }
        echo '</table>';
    } else {
        echo '<div class="status warning">';
        echo '<strong>No Items in Session</strong>';
        echo '<br>Session cart is empty - add items first';
        echo '</div>';
    }
    ?>

    <!-- Database Cart Contents -->
    <h2>6. Database Cart Contents</h2>
    <?php
    if ($is_logged_in && $table_ok) {
        try {
            $cust_id = $_SESSION['customer']['cust_id'];
            $stmt = $pdo->prepare("SELECT * FROM tbl_customer_carts WHERE customer_id = ? ORDER BY added_at DESC LIMIT 10");
            $stmt->execute([$cust_id]);
            $items = $stmt->fetchAll(PDO::FETCH_ASSOC);
            
            if (count($items) > 0) {
                echo '<div class="status success">';
                echo '<strong>✓ Database Cart Items:</strong>';
                echo '<br>Found ' . count($items) . ' item(s) for customer ' . $cust_id;
                echo '</div>';
                
                echo '<table>';
                echo '<tr><th>ID</th><th>Product ID</th><th>Name</th><th>Size</th><th>Color</th><th>Qty</th><th>Price</th><th>Added</th></tr>';
                
                foreach ($items as $item) {
                    echo '<tr>';
                    echo '<td>' . $item['cart_id'] . '</td>';
                    echo '<td>' . $item['product_id'] . '</td>';
                    echo '<td>' . $item['product_name'] . '</td>';
                    echo '<td>' . $item['size_name'] . '</td>';
                    echo '<td>' . $item['color_name'] . '</td>';
                    echo '<td>' . $item['quantity'] . '</td>';
                    echo '<td>' . $item['price_at_add'] . '</td>';
                    echo '<td>' . $item['added_at'] . '</td>';
                    echo '</tr>';
                }
                echo '</table>';
            } else {
                echo '<div class="status warning">';
                echo '<strong>No Items in Database</strong>';
                echo '<br>Customer ' . $cust_id . ' has no saved cart items';
                echo '<br>Add items to cart - they should appear here automatically';
                echo '</div>';
            }
        } catch (PDOException $e) {
            echo '<div class="status error">';
            echo '<strong>Query Error</strong>';
            echo '<br>' . $e->getMessage();
            echo '</div>';
        }
    } else {
        echo '<div class="status warning">';
        echo '<strong>Cannot Check Database</strong>';
        echo '<br>Must be logged in and table must exist';
        echo '</div>';
    }
    ?>

    <!-- Test Section -->
    <h2>7. Test Cart Synchronization</h2>
    <div class="test-section">
        <?php
        if (!$is_logged_in) {
            echo '<div class="status error">';
            echo '<strong>Cannot Test - Not Logged In</strong>';
            echo '<br>Please <a href="login.php">login first</a>';
            echo '</div>';
        } elseif (!$table_ok) {
            echo '<div class="status error">';
            echo '<strong>Cannot Test - Table Missing</strong>';
            echo '<br>Please <a href="create_cart_table.php">create the table</a>';
            echo '</div>';
        } else {
            echo '<p>Add an item to cart and come back here to verify it was saved to database.</p>';
            echo '<p><a href="index.php" class="btn">Go Shopping</a></p>';
        }
        ?>
    </div>

    <!-- Summary -->
    <h2>8. Summary</h2>
    <?php
    $all_ok = $is_logged_in && $db_ok && $table_ok && $all_functions_ok;
    
    if ($all_ok) {
        echo '<div class="status success">';
        echo '<strong>✓ Everything is Ready!</strong>';
        echo '<br>System is configured correctly';
        echo '<br>Add items to cart and they should appear in database automatically';
        echo '</div>';
    } else {
        echo '<div class="status error">';
        echo '<strong>✗ Issues Found</strong>';
        echo '<br>Fix the issues above before using cart sync';
        echo '<ul>';
        if (!$is_logged_in) echo '<li>Login to your account first</li>';
        if (!$db_ok) echo '<li>Check database connection in admin/inc/config.php</li>';
        if (!$table_ok) echo '<li>Run create_cart_table.php to create table</li>';
        if (!$all_functions_ok) echo '<li>Update admin/inc/functions.php with new functions</li>';
        echo '</ul>';
        echo '</div>';
    }
    ?>

    <hr>
    <p style="font-size: 12px; color: #666;">
        <strong>Debug Info:</strong><br>
        Date: <?php echo date('Y-m-d H:i:s'); ?><br>
        PHP Version: <?php echo phpversion(); ?><br>
        Database: ecommerceweb
    </p>
</body>
</html>
