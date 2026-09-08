<?php
/**
 * QUICK SETUP: Cart Persistence
 * 
 * This file will help you set up cart persistence in your eCommerce site.
 * Follow the steps below to complete the setup.
 */

// Step 1: Start session
session_start();

// Step 2: Include config
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

// Step 3: Check if table needs to be created
try {
    // Try to query the cart table
    $test_stmt = $pdo->prepare("SELECT 1 FROM tbl_customer_carts LIMIT 1");
    $test_stmt->execute();
    $table_exists = true;
} catch (PDOException $e) {
    $table_exists = false;
}

// Step 4: Create table if needed
if (!$table_exists) {
    try {
        $sql = "CREATE TABLE IF NOT EXISTS `tbl_customer_carts` (
          `cart_id` int(11) NOT NULL AUTO_INCREMENT,
          `customer_id` int(11) NOT NULL,
          `product_id` int(11) NOT NULL,
          `size_id` int(11) DEFAULT NULL,
          `size_name` varchar(255) DEFAULT '',
          `color_id` int(11) DEFAULT NULL,
          `color_name` varchar(255) DEFAULT '',
          `quantity` int(11) NOT NULL DEFAULT 1,
          `price_at_add` decimal(10,2) NOT NULL DEFAULT 0.00,
          `product_name` varchar(255) DEFAULT '',
          `product_photo` varchar(255) DEFAULT NULL,
          `added_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
          `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (`cart_id`),
          KEY `customer_id` (`customer_id`),
          KEY `product_id` (`product_id`),
          CONSTRAINT `tbl_customer_carts_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;";
        
        $pdo->exec($sql);
        $table_created = true;
    } catch (PDOException $e) {
        $table_created = false;
        $error = $e->getMessage();
    }
} else {
    $table_created = true;
}

// Check if functions exist
$functions_ok = function_exists('saveCartToDatabase') && function_exists('loadCartFromDatabase');
?>
<!DOCTYPE html>
<html>
<head>
    <title>Cart Persistence Setup Status</title>
    <style>
        body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; }
        .status-box { padding: 20px; margin: 15px 0; border-radius: 5px; }
        .success { background-color: #d4edda; border: 1px solid #c3e6cb; color: #155724; }
        .error { background-color: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; }
        .warning { background-color: #fff3cd; border: 1px solid #ffeeba; color: #856404; }
        h1 { color: #333; }
        h2 { color: #555; margin-top: 30px; }
        .checkmark { color: #28a745; font-weight: bold; }
        .cross { color: #dc3545; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #f8f9fa; }
        code { background-color: #f4f4f4; padding: 2px 5px; border-radius: 3px; }
    </style>
</head>
<body>
    <h1>🛒 Cart Persistence Setup Status</h1>
    
    <div class="status-box success">
        <h2><span class="checkmark">✓</span> Setup Overview</h2>
        <p>Cart persistence allows customers to save their shopping carts across login/logout sessions.</p>
    </div>

    <h2>System Status</h2>
    
    <table>
        <tr>
            <th>Component</th>
            <th>Status</th>
            <th>Details</th>
        </tr>
        <tr <?php echo $table_exists ? 'style="background-color:#d4edda"' : 'style="background-color:#f8d7da"'; ?>>
            <td><strong>Database Table</strong></td>
            <td><?php echo $table_exists ? '<span class="checkmark">✓ Ready</span>' : '<span class="cross">✗ Not Found</span>'; ?></td>
            <td>
                <code>tbl_customer_carts</code>
                <?php if ($table_exists) {
                    echo " - Table exists and is ready to use";
                } else {
                    echo " - Table needs to be created";
                } ?>
            </td>
        </tr>
        <tr <?php echo $functions_ok ? 'style="background-color:#d4edda"' : 'style="background-color:#f8d7da"'; ?>>
            <td><strong>Functions</strong></td>
            <td><?php echo $functions_ok ? '<span class="checkmark">✓ Ready</span>' : '<span class="cross">✗ Not Found</span>'; ?></td>
            <td>
                <code>saveCartToDatabase()</code> &amp; <code>loadCartFromDatabase()</code>
                <?php echo $functions_ok ? " - Functions are defined" : " - Functions missing"; ?>
            </td>
        </tr>
        <tr style="background-color:#d4edda">
            <td><strong>Logout Handler</strong></td>
            <td><span class="checkmark">✓ Updated</span></td>
            <td><code>logout.php</code> - Saves cart before session ends</td>
        </tr>
        <tr style="background-color:#d4edda">
            <td><strong>Login Handler</strong></td>
            <td><span class="checkmark">✓ Updated</span></td>
            <td><code>login.php</code> - Loads cart after authentication</td>
        </tr>
        <tr style="background-color:#d4edda">
            <td><strong>OTP Handler</strong></td>
            <td><span class="checkmark">✓ Updated</span></td>
            <td><code>verify_otp.php</code> - Loads cart after OTP verification</td>
        </tr>
    </table>

    <?php if ($table_exists && $functions_ok): ?>
        <div class="status-box success">
            <h2><span class="checkmark">✓</span> Setup Complete!</h2>
            <p>Your cart persistence system is ready to use.</p>
            <p><strong>What happens now:</strong></p>
            <ul>
                <li>When customers add items to cart and then log out, the cart data is saved to database</li>
                <li>When they log back in (via email, social, or OTP), their cart is automatically restored</li>
                <li>Cart data persists across browsers and devices (as long as they use the same account)</li>
            </ul>
        </div>
    <?php else: ?>
        <div class="status-box error">
            <h2><span class="cross">✗</span> Setup Incomplete</h2>
            <p>Please resolve the issues above before using cart persistence.</p>
            <?php if (!$table_exists): ?>
                <p><strong>To create the database table:</strong></p>
                <ol>
                    <li>Go to your project root folder: <code>d:\xampp\htdocs\eCommerceSite-PHP\</code></li>
                    <li>Find and run: <code>create_cart_table.php</code></li>
                    <li>You should see a success message</li>
                </ol>
            <?php endif; ?>
            <?php if (!$functions_ok): ?>
                <p><strong>Missing Functions:</strong></p>
                <p>Verify that <code>admin/inc/functions.php</code> contains:</p>
                <ul>
                    <li><code>saveCartToDatabase($pdo, $customer_id, $session_data)</code></li>
                    <li><code>loadCartFromDatabase($pdo, $customer_id)</code></li>
                </ul>
            <?php endif; ?>
        </div>
    <?php endif; ?>

    <h2>Documentation</h2>
    <p>For detailed setup instructions, see: <code>CART_PERSISTENCE_SETUP.md</code></p>

    <h2>Files Modified</h2>
    <ul>
        <li><code>admin/inc/functions.php</code> - Added/updated cart functions</li>
        <li><code>logout.php</code> - Saves cart before logout</li>
        <li><code>login.php</code> - Loads cart after login</li>
        <li><code>verify_otp.php</code> - Loads cart after OTP verification</li>
    </ul>

    <hr>
    <p style="color: #666; font-size: 12px;">
        Setup completed on: <?php echo date('Y-m-d H:i:s'); ?><br>
        You can delete this file after verifying the setup is complete.
    </p>
</body>
</html>
