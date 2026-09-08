<?php
/**
 * This script creates the tbl_customer_carts table if it doesn't exist.
 * Run this once to set up the database table for storing customer cart data.
 */

require_once('admin/inc/config.php');

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
    
    echo "<div style='padding: 20px; background-color: #d4edda; border: 1px solid #c3e6cb; border-radius: 5px; color: #155724;'>";
    echo "<h3>✓ Success!</h3>";
    echo "<p>The <strong>tbl_customer_carts</strong> table has been created successfully.</p>";
    echo "<p>You can now safely delete this file: <code>create_cart_table.php</code></p>";
    echo "<p><a href='index.php' style='color: #004085;'>Go back to homepage</a></p>";
    echo "</div>";
    
} catch (PDOException $e) {
    echo "<div style='padding: 20px; background-color: #f8d7da; border: 1px solid #f5c6cb; border-radius: 5px; color: #721c24;'>";
    echo "<h3>✗ Error</h3>";
    echo "<p>Error creating table: " . htmlspecialchars($e->getMessage()) . "</p>";
    echo "</div>";
}
?>
