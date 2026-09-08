<?php
ob_start();
session_start();

// Regenerate session ID for security
session_regenerate_id(true);

// Validate customer session
if (!isset($_SESSION['customer']) || empty($_SESSION['customer']['cust_id'])) {
    header('Location: ../../login.php');
    exit;
}

// Validate cart contents
if (empty($_SESSION['cart_p_id'])) {
    header('Location: ../../cart.php');
    exit;
}

// Include database config and functions (assuming functions.php is used elsewhere if needed)
include("../../admin/inc/config.php"); 
// include("../../admin/inc/functions.php"); // Uncomment if you have this file and need it here

// Get SSLCommerz credentials and other settings from tbl_settings
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings = $statement->fetch(PDO::FETCH_ASSOC);

// === 1. GATHER ALL REQUIRED DATA FROM SESSION AND POST ===
$payment_date = date('Y-m-d H:i:s');
$tran_id = 'SSL_' . uniqid(); // Unique transaction ID for this payment attempt
$final_total = $_POST['final_total']; // Final total from checkout form submission

// Customer details (from customer session)
$customer_details = $_SESSION['customer'] ?? []; // Ensure it's an array, even if empty

// Billing and Shipping details (from checkout.php session setup)
// Add robust fallbacks to ensure emails are always strings (even empty)
$billing_details = $_SESSION['billing_address_details'] ?? [];
$billing_details['email'] = $billing_details['email'] ?? $customer_details['cust_email'] ?? '';

$shipping_details = $_SESSION['shipping_address_details'] ?? [];
$shipping_details['email'] = $shipping_details['email'] ?? $customer_details['cust_email'] ?? '';

// Payment-specific details (from cart session, often aggregated into $_SESSION['payment_data'])
$shipping_cost = $_SESSION['shipping_cost'] ?? 0;
// Check if coupon details are directly in $_SESSION['coupon'] or separately
$coupon_code = $_SESSION['coupon']['code'] ?? '';
$coupon_discount = $_SESSION['coupon']['discount'] ?? 0;
$coupon_id = $_SESSION['coupon']['coupon_id'] ?? null; // For updating coupon usage later


// === 2. INSERT PAYMENT RECORD INTO tbl_payment ===
// Ensure relevant columns (e.g., card_number, bank_tran_id, customer_note) are NULLable in your DB schema
// Assuming customer_note is also nullable for SSLCommerz if not explicitly passed
$statement = $pdo->prepare("INSERT INTO tbl_payment (
    customer_id, customer_name, customer_email,
    payment_date, txnid, paid_amount,
    shipping_cost, coupon_code, coupon_discount,
    payment_method, payment_status, shipping_status, payment_id,
    billing_name, billing_email, billing_phone, billing_street, billing_city, billing_state, billing_country, billing_zip,
    shipping_name, shipping_email, shipping_phone, shipping_street, shipping_city, shipping_state, shipping_country, shipping_zip,
    customer_note  -- Include customer_note if it exists in your tbl_payment and you wish to store it
) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"); // Adjust placeholders if customer_note is removed

$statement->execute([
    $customer_details['cust_id'] ?? null,
    $customer_details['cust_name'] ?? '',
    $customer_details['cust_email'] ?? '',
    $payment_date,
    $tran_id,
    $final_total,
    $shipping_cost,
    $coupon_code,
    $coupon_discount,
    'SSLCommerz',    // Payment Method
    'Completed',       // Initial payment status (will be updated by SSLCommerz callback)
    'Pending',       // Initial shipping status
    $tran_id,       // payment_id (using the generated transaction ID)
    
    // Billing Details (ensure these variables are set, using ?? '' for safety)
    $billing_details['name'] ?? '',
    $billing_details['email'], // Now robustly set above
    $billing_details['phone'] ?? '',
    $billing_details['address'] ?? '',
    $billing_details['city'] ?? '',
    $billing_details['state'] ?? '',
    $billing_details['country_id'] ?? '', // Assuming this stores an ID or code compatible with DB
    $billing_details['zip'] ?? '',
    
    // Shipping Details (ensure these variables are set, using ?? '' for safety)
    $shipping_details['name'] ?? '',
    $shipping_details['email'], // Now robustly set above
    $shipping_details['phone'] ?? '',
    $shipping_details['address'] ?? '',
    $shipping_details['city'] ?? '',
    $shipping_details['state'] ?? '',
    $shipping_details['country_id'] ?? '', // Assuming this stores an ID or code compatible with DB
    $shipping_details['zip'] ?? '',
    null // customer_note for SSLCommerz is often null or empty
]);

$payment_id = $tran_id; // Use the transaction ID as the payment_id for tbl_order


// === 3. SAVE ORDER ITEMS INTO tbl_order AND UPDATE PRODUCT STOCK ===
foreach ($_SESSION['cart_p_id'] as $key => $product_id) {
    // Validate that all necessary cart item data exists for this product
    if (!isset(
        $_SESSION['cart_p_name'][$key],
        $_SESSION['cart_size_name'][$key],
        $_SESSION['cart_color_name'][$key],
        $_SESSION['cart_p_qty'][$key],
        $_SESSION['cart_p_current_price'][$key]
    )) {
        error_log("Missing cart item data for product ID: " . $product_id . " at key: " . $key . " in SSLCommerz process.");
        continue; // Skip incomplete items to prevent errors
    }

    $statement = $pdo->prepare("INSERT INTO tbl_order (
        product_id, product_name, size, color, quantity, unit_price, payment_id, cust_id
    ) VALUES (?,?,?,?,?,?,?,?)"); // Corrected: Added cust_id and its placeholder
    
    $statement->execute([
        $product_id,
        $_SESSION['cart_p_name'][$key],
        $_SESSION['cart_size_name'][$key],
        $_SESSION['cart_color_name'][$key],
        $_SESSION['cart_p_qty'][$key],
        $_SESSION['cart_p_current_price'][$key],
        $payment_id,
        $customer_details['cust_id'] // Corrected: Pass the customer ID
    ]);
    
    // Update product stock (p_qty)
    // Update product stock (p_qty)
$statement = $pdo->prepare("UPDATE tbl_product SET p_qty = p_qty - ? WHERE p_id = ?");
$statement->execute([$_SESSION['cart_p_qty'][$key] ?? 0, $product_id]); // Added null coalescing for safety
}

// Update coupon usage count if a coupon was applied
if ($coupon_id && $coupon_code) {
    $statement = $pdo->prepare("UPDATE tbl_coupon SET used_count = used_count + 1 WHERE coupon_id = ?");
    $statement->execute([$coupon_id]);
}

// === 4. PREPARE AND SEND DATA TO SSLCommerz API ===
$post_data = [];
$post_data['store_id'] = $settings['sslcz_store_id'] ?? '';
$post_data['store_passwd'] = $settings['sslcz_store_pass'] ?? '';
$post_data['total_amount'] = $final_total;
$post_data['currency'] = "BDT";
$post_data['tran_id'] = $tran_id;

// Safe URL construction
$success_params = [
    'method' => 'sslcommerz',
    'amount' => $final_total,
    'tran_id' => $tran_id,
    'session_id' => session_id()
];

$post_data['success_url'] = BASE_URL . 'payment_success.php?' . http_build_query($success_params);
$post_data['cancel_url'] = BASE_URL . 'payment_cancel.php';
$post_data['fail_url'] = BASE_URL . 'payment_fail.php';
$post_data['ipn_url'] = BASE_URL . 'ipn_listener.php'; // <--- THIS IS THE FIX
# Customer Info (using billing details, which now have robust email)
$post_data['cus_name'] = $billing_details['name'] ?? '';
$post_data['cus_email'] = $billing_details['email']; // Robustly set above
$post_data['cus_add1'] = $billing_details['address'] ?? '';
$post_data['cus_city'] = $billing_details['city'] ?? '';
$post_data['cus_state'] = $billing_details['state'] ?? '';
$post_data['cus_postcode'] = $billing_details['zip'] ?? '';
$post_data['cus_country'] = $billing_details['country_id'] ?? ''; // SSLCommerz might need country name/code, adjust if necessary
$post_data['cus_phone'] = $billing_details['phone'] ?? '';

# Shipment Info (using shipping details, which now have robust email)
$post_data['ship_name'] = $shipping_details['name'] ?? '';
$post_data['ship_add1'] = $shipping_details['address'] ?? '';
$post_data['ship_city'] = $shipping_details['city'] ?? '';
$post_data['ship_state'] = $shipping_details['state'] ?? '';
$post_data['ship_postcode'] = $shipping_details['zip'] ?? '';
$post_data['ship_country'] = $shipping_details['country_id'] ?? ''; // SSLCommerz might need country name/code, adjust if necessary

# Product Info (generic for an e-commerce order)
$post_data['product_name'] = 'E-commerce Order';
$post_data['product_category'] = 'Mixed Goods';
$post_data['product_profile'] = 'physical-goods';


// Define API URL based on sandbox or live mode setting
$direct_api_url = ($settings['sslcz_mode'] == 'sandbox')
    ? "https://sandbox.sslcommerz.com/gwprocess/v3/api.php"
    : "https://securepay.sslcommerz.com/gwprocess/v3/api.php";

// Initialize cURL for API request
$handle = curl_init();
curl_setopt($handle, CURLOPT_URL, $direct_api_url);
curl_setopt($handle, CURLOPT_TIMEOUT, 30);
curl_setopt($handle, CURLOPT_CONNECTTIMEOUT, 30);
curl_setopt($handle, CURLOPT_POST, 1);
curl_setopt($handle, CURLOPT_POSTFIELDS, $post_data);
curl_setopt($handle, CURLOPT_RETURNTRANSFER, true);
// IMPORTANT: For production, set CURLOPT_SSL_VERIFYPEER to TRUE and ensure proper CA certs are configured
// For local development with self-signed certs, FALSE might be necessary but is INSECURE for production.
curl_setopt($handle, CURLOPT_SSL_VERIFYPEER, FALSE); 

$content = curl_exec($handle);
$code = curl_getinfo($handle, CURLINFO_HTTP_CODE);

if ($code == 200 && !curl_errno($handle)) {
    curl_close($handle);
    $sslcz = json_decode($content, true);

    if (isset($sslcz['GatewayPageURL']) && $sslcz['GatewayPageURL'] != "") {
        // === Clear all session data related to the cart and payment ===
        
        // === END CART CLEARING ===

        // Redirect to SSLCommerz payment page
        echo "<meta http-equiv='refresh' content='0;url=" . $sslcz['GatewayPageURL'] . "'>";
        exit;
    } else {
        // Handle JSON parsing error or missing GatewayPageURL from SSLCommerz
        error_log(message: "SSLCommerz API Error - Invalid GatewayPageURL or JSON: " . ($content ?? 'No content received'));
        // User-friendly message
        echo "An error occurred with the payment gateway. Please contact support or try again later. (Error: Invalid Gateway URL)";
        exit;
    }
} else {
    // Handle cURL connection error (network issues, etc.)
    $curl_error = curl_error($handle);
    $curl_errno = curl_errno($handle);
    curl_close($handle);
    error_log("SSLCommerz Connection Error: " . $curl_error . " (Code: " . $curl_errno . ") HTTP Code: " . $code);
    echo "Payment processing currently unavailable due to a connection error. Please try again later. (Error: " . $curl_error . ")";
    exit;
}
?>