<?php
ob_start();
// Check if session is already active before starting
if (session_status() == PHP_SESSION_NONE) {
    session_start();
}

// Include necessary configuration and functions
require_once 'admin/inc/config.php';
require_once 'admin/inc/functions.php'; // Ensure functions.php is included for saveCartToDatabase

// IMPORTANT: If a customer is logged in and has items in their session cart,
// save these items to the database BEFORE unsetting the customer session.
if (isset($_SESSION['customer']['cust_id']) && !empty($_SESSION['customer']['cust_id'])) {
    // Only attempt to save if there's *any* cart data in session, not just cart_p_id
    // This makes the check more robust as other cart arrays might be empty but present.
    $has_cart_data = false;
    if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id']) && count($_SESSION['cart_p_id']) > 0) {
        $has_cart_data = true;
    }

    if ($has_cart_data) {
        // Pass the PDO object and customer ID to the save function
        if (function_exists('saveCartToDatabase')) {
            saveCartToDatabase($pdo, $_SESSION['customer']['cust_id'], $_SESSION);
            error_log("Cart saved to database for customer ID: " . $_SESSION['customer']['cust_id']);
        } else {
            error_log("Error: saveCartToDatabase function not found in inc/functions.php during logout.");
        }
    } else {
        // Even if cart is empty, still call save to ensure database is consistent
        if (function_exists('saveCartToDatabase')) {
            saveCartToDatabase($pdo, $_SESSION['customer']['cust_id'], $_SESSION);
        }
    }
}

// Unset the customer session to log them out
unset($_SESSION['customer']);

// Clear all cart related session variables after saving to DB (or if not logged in)
// This ensures the session cart is empty after logout.
unset($_SESSION['cart_p_id']);
unset($_SESSION['cart_size_id']);
unset($_SESSION['cart_size_name']);
unset($_SESSION['cart_color_id']);
unset($_SESSION['cart_color_name']);
unset($_SESSION['cart_p_qty']);
unset($_SESSION['cart_p_current_price']);
unset($_SESSION['cart_p_name']);
unset($_SESSION['cart_p_featured_photo']);

// Redirect to login page or home page after successful logout
header("location: ".BASE_URL."login.php"); // Redirect to login.php after logout
exit;
?>
