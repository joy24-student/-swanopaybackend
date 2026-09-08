<?php
// Include configuration and functions, start the session to get customer info
require_once("admin/inc/config.php");
require_once("admin/inc/functions.php");
session_start();

// Set the header to JSON so the frontend JavaScript knows how to interpret the response
header('Content-Type: application/json');

// --- 1. Security & Authentication Check ---
if (!isset($_SESSION['customer'])) {
    // If not logged in, return an error immediately
    echo json_encode(['status' => 'error', 'message' => 'You must be logged in to manage your wishlist.']);
    exit;
}

// Get required data from the POST request
$cust_id = $_SESSION['customer']['cust_id'];
$action = $_POST['action'] ?? '';
$product_id = $_POST['product_id'] ?? null;
// wishlist_id is only used for the 'remove' action
$wishlist_id = $_POST['wishlist_id'] ?? null;

// Normalize alias actions for backward compatibility
if ($action === 'remove_by_id') {
    $action = 'remove';
}

// Validate essential input conservatively:
// - 'add' requires product_id
// - 'remove' requires either wishlist_id or product_id
if (empty($action)) {
    echo json_encode(['status' => 'error', 'message' => 'Missing required action.']);
    exit;
}

if ($action === 'add' && empty($product_id)) {
    echo json_encode(['status' => 'error', 'message' => 'Missing product ID for add action.']);
    exit;
}

if ($action === 'remove' && empty($wishlist_id) && empty($product_id)) {
    echo json_encode(['status' => 'error', 'message' => 'Missing identifier: provide wishlist_id or product_id for removal.']);
    exit;
}

// --- 2. Database Action ---
try {
    if ($action === 'add') {
        // Check if item already exists to prevent duplicate entries (Good practice)
        $statement_check = $pdo->prepare("SELECT COUNT(*) FROM tbl_wishlist WHERE cust_id=? AND product_id=?");
        $statement_check->execute(array($cust_id, $product_id));
        if ($statement_check->fetchColumn() > 0) {
            echo json_encode(['status' => 'error', 'message' => 'Product is already in your wishlist.']);
            exit;
        }

        // Add to wishlist
        $statement_add = $pdo->prepare("INSERT INTO tbl_wishlist (cust_id, product_id, added_date) VALUES (?, ?, NOW())");
        $statement_add->execute(array($cust_id, $product_id));

        // Note: The 'added_date' is set to NOW() using the DATETIME default in the SQL
        echo json_encode(['status' => 'success', 'message' => 'Product successfully added to your wishlist!']);
        
    } elseif ($action === 'remove') {
        if (empty($wishlist_id)) {
            // Fallback removal method: delete based on the unique combination of customer and product
            $statement_remove = $pdo->prepare("DELETE FROM tbl_wishlist WHERE cust_id=? AND product_id=?");
            $statement_remove->execute(array($cust_id, $product_id));
        } else {
            // Primary removal method: delete by the specific wishlist_id (more precise)
            // We also check cust_id to ensure a user can only delete their own item
            $statement_remove = $pdo->prepare("DELETE FROM tbl_wishlist WHERE wishlist_id=? AND cust_id=?");
            $statement_remove->execute(array($wishlist_id, $cust_id));
        }
        
        if ($statement_remove->rowCount() > 0) {
            echo json_encode(['status' => 'success', 'message' => 'Product successfully removed from your wishlist.']);
        } else {
            echo json_encode(['status' => 'error', 'message' => 'Item not found or removal failed.']);
        }
    } else {
        echo json_encode(['status' => 'error', 'message' => 'Invalid action specified.']);
    }

} catch (PDOException $e) {
    // Catch and log any database exceptions
    error_log("Wishlist PDO Error: " . $e->getMessage());
    echo json_encode(['status' => 'error', 'message' => 'A severe database error occurred. Please try again.']);
}
?>
