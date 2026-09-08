<?php
ob_start();
session_start();
include("../../admin/inc/config.php");
include("../../admin/inc/functions.php");

// Validate access - make sure a customer is logged in and the cart is not empty
if(!isset($_SESSION['customer']) || !isset($_SESSION['cart_p_id'])) {
    header('location: ../../login.php');
    exit;
}

// Retrieve all the data prepared by checkout.php from the session
$payment_data = $_SESSION['payment_data'] ?? [];
$billing_details = $_SESSION['billing_address_details'] ?? [];
$shipping_details = $_SESSION['shipping_address_details'] ?? [];

// Get additional form data
$customer_note = strip_tags($_POST['customer_note'] ?? '');
$payment_date = date('Y-m-d H:i:s');
// Create a unique ID for this COD order
$payment_id = 'COD-' . time() . '-' . ($payment_data['customer_id'] ?? 'NA') . mt_rand(1000, 9999); 


// --- 1. INSERT INTO tbl_payment (32 COLUMNS) ---
try {
    $statement = $pdo->prepare("INSERT INTO tbl_payment (
        customer_id, customer_name, customer_email, payment_date, 
        txnid, paid_amount, shipping_cost, coupon_code, coupon_discount, 
        payment_method, payment_status, shipping_status, payment_id, payment_note, 
        card_number, bank_transaction_info,                 
        billing_name, billing_email, billing_phone, billing_street, billing_city, 
        billing_state, billing_country, billing_zip, shipping_name, shipping_email, 
        shipping_phone, shipping_street, shipping_city, shipping_state, shipping_country, shipping_zip
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

    $statement->execute([
        $payment_data['customer_id'] ?? 0,
        $payment_data['customer_name'] ?? 'Customer',
        $payment_data['customer_email'] ?? '',
        $payment_date,
        '',                                     // txnid (Empty for COD)
        $payment_data['overall_total'] ?? 0,
        $payment_data['shipping_cost'] ?? 0,
        $payment_data['coupon_code'] ?? '',
        $payment_data['coupon_discount'] ?? 0,
        'Cash on Delivery',
        'Pending',                              // Payment status
        'Pending',                              // Shipping status
        $payment_id,
        $customer_note,
        '',                                     // card_number (Empty for COD)
        '',                                     // bank_transaction_info (Empty for COD)
        // Billing Address Data
        $billing_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',
        $billing_details['phone'] ?? '',
        $billing_details['address'] ?? '',
        $billing_details['city'] ?? '',
        $billing_details['state'] ?? '',
        $billing_details['country_id'] ?? '',
        $billing_details['zip'] ?? '',
        // Shipping Address Data
        $shipping_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',
        $shipping_details['phone'] ?? '',
        $shipping_details['address'] ?? '',
        $shipping_details['city'] ?? '',
        $shipping_details['state'] ?? '',
        $shipping_details['country_id'] ?? '',
        $shipping_details['zip'] ?? ''
    ]);
} catch (Exception $e) {
    error_log("tbl_payment insert skipped or failed: " . $e->getMessage());
}

// --- 2. INSERT INTO tbl_order & update stock ---
foreach($payment_data['cart_p_id'] as $key => $product_id) {
    try {
        $statement = $pdo->prepare("INSERT INTO tbl_order (
            cust_id, product_id, product_name, size, color, quantity, unit_price, payment_id, coupon_code, coupon_discount
        ) VALUES (?,?,?,?,?,?,?,?,?,?)");
        $statement->execute([
            $payment_data['customer_id'] ?? 0,           
            $product_id,
            $payment_data['cart_p_name'][$key] ?? '',
            $payment_data['cart_size_name'][$key] ?? '',
            $payment_data['cart_color_name'][$key] ?? '',
            $payment_data['cart_p_qty'][$key] ?? 1,
            $payment_data['cart_p_current_price'][$key] ?? 0,
            $payment_id,
            $payment_data['coupon_code'] ?? '',
            $payment_data['coupon_discount'] ?? 0
        ]);

        $statement = $pdo->prepare("UPDATE tbl_product SET p_qty = GREATEST(0, p_qty - ?) WHERE p_id = ?");
        $statement->execute([$payment_data['cart_p_qty'][$key] ?? 1, $product_id]);
    } catch (Exception $e) {
        error_log("tbl_order insert skipped or failed: " . $e->getMessage());
    }
}

// --- SUPABASE REALTIME SYNC: Push to Merchant Supabase orders & order_items ---
$supabase_url = defined('SUPABASE_URL') ? SUPABASE_URL : getenv('SUPABASE_URL');
$supabase_key = defined('SUPABASE_SERVICE_KEY') && !empty(SUPABASE_SERVICE_KEY) 
    ? SUPABASE_SERVICE_KEY 
    : (defined('SUPABASE_ANON_KEY') ? SUPABASE_ANON_KEY : getenv('SUPABASE_ANON_KEY'));

if (!empty($supabase_url) && !empty($supabase_key)) {
    try {
        $order_number = 'ORD-' . date('Ymd') . '-' . mt_rand(1000, 9999);
        $clean_supabase_url = rtrim($supabase_url, '/');

        // 1. Get Merchant ID
        $ch_m = curl_init("{$clean_supabase_url}/rest/v1/merchants?select=id&limit=1");
        curl_setopt($ch_m, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch_m, CURLOPT_HTTPHEADER, [
            "apikey: {$supabase_key}",
            "Authorization: Bearer {$supabase_key}"
        ]);
        $res_m = curl_exec($ch_m);
        curl_close($ch_m);
        $merchants = json_decode($res_m, true);
        $merchant_id = !empty($merchants[0]['id']) ? $merchants[0]['id'] : null;

        if ($merchant_id) {
            // 2. Insert into Supabase orders
            $order_payload = json_encode([
                'merchant_id' => $merchant_id,
                'tran_id' => $payment_id,
                'order_number' => $order_number,
                'amount' => (float)$payment_data['overall_total'],
                'total_amount' => (float)$payment_data['overall_total'],
                'subtotal' => (float)($payment_data['paid_amount'] ?? $payment_data['overall_total']),
                'shipping_cost' => (float)($payment_data['shipping_cost'] ?? 0),
                'discount_amount' => (float)($payment_data['coupon_discount'] ?? 0),
                'cus_phone' => (string)($shipping_details['phone'] ?? ($billing_details['phone'] ?? '01700000000')),
                'cus_name' => (string)($payment_data['customer_name'] ?? 'Customer'),
                'cus_email' => (string)($payment_data['customer_email'] ?? ''),
                'shipping_address' => (string)($shipping_details['address'] ?? ''),
                'shipping_city' => (string)($shipping_details['city'] ?? ''),
                'payment_method' => 'COD',
                'status' => 'PENDING',
                'order_status' => 'CONFIRMED',
                'customer_note' => $customer_note,
                'expires_at' => date('c', strtotime('+7 days'))
            ]);

            $ch_order = curl_init("{$clean_supabase_url}/rest/v1/orders");
            curl_setopt($ch_order, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch_order, CURLOPT_POST, true);
            curl_setopt($ch_order, CURLOPT_POSTFIELDS, $order_payload);
            curl_setopt($ch_order, CURLOPT_HTTPHEADER, [
                "apikey: {$supabase_key}",
                "Authorization: Bearer {$supabase_key}",
                "Content-Type: application/json",
                "Prefer: return=representation"
            ]);
            $res_order = curl_exec($ch_order);
            $http_code = curl_getinfo($ch_order, CURLINFO_HTTP_CODE);
            curl_close($ch_order);

            $inserted_order = json_decode($res_order, true);
            $supabase_order_id = !empty($inserted_order[0]['id']) ? $inserted_order[0]['id'] : null;

            // 3. Insert line items if order created
            if ($supabase_order_id) {
                $items_payload = [];
                foreach($payment_data['cart_p_id'] as $key => $pid) {
                    $items_payload[] = [
                        'order_id' => $supabase_order_id,
                        'product_name' => (string)($payment_data['cart_p_name'][$key] ?? 'Product'),
                        'size' => (string)($payment_data['cart_size_name'][$key] ?? ''),
                        'color' => (string)($payment_data['cart_color_name'][$key] ?? ''),
                        'quantity' => (int)($payment_data['cart_p_qty'][$key] ?? 1),
                        'unit_price' => (float)($payment_data['cart_p_current_price'][$key] ?? 0),
                        'total_price' => (float)(($payment_data['cart_p_qty'][$key] ?? 1) * ($payment_data['cart_p_current_price'][$key] ?? 0))
                    ];
                }

                $ch_items = curl_init("{$clean_supabase_url}/rest/v1/order_items");
                curl_setopt($ch_items, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch_items, CURLOPT_POST, true);
                curl_setopt($ch_items, CURLOPT_POSTFIELDS, json_encode($items_payload));
                curl_setopt($ch_items, CURLOPT_HTTPHEADER, [
                    "apikey: {$supabase_key}",
                    "Authorization: Bearer {$supabase_key}",
                    "Content-Type: application/json"
                ]);
                curl_exec($ch_items);
                curl_close($ch_items);
            }
        }
    } catch (Exception $e) {
        error_log("Supabase realtime order push error: " . $e->getMessage());
    }
}

// --- Handle Coupon Usage ---
if(!empty($payment_data['coupon_id'])) {
    $statement = $pdo->prepare("UPDATE tbl_coupon SET used_count = used_count + 1 WHERE coupon_id = ?");
    $statement->execute([$payment_data['coupon_id']]);
    try {
        $statement = $pdo->prepare("UPDATE tbl_coupon SET used_count = used_count + 1 WHERE coupon_id = ?");
        $statement->execute([$payment_data['coupon_id']]);
    } catch (Exception $e) {}
}

// --- Clear all session data related to the cart and payment ---
unset($_SESSION['cart_p_id']);
unset($_SESSION['cart_size_id']);
unset($_SESSION['cart_size_name']);
unset($_SESSION['cart_color_id']);
unset($_SESSION['cart_color_name']);
unset($_SESSION['cart_p_qty']);
unset($_SESSION['cart_p_current_price']);
unset($_SESSION['cart_p_name']);
unset($_SESSION['cart_p_featured_photo']);
unset($_SESSION['coupon_code']);
unset($_SESSION['coupon_discount']);
unset($_SESSION['coupon_id']);
unset($_SESSION['shipping_cost']);
unset($_SESSION['overall_total']);
unset($_SESSION['payment_data']);

// --- Redirect to success page with the unique payment_id ---
header('location: ../../payment_success.php?method=cod&amount='. $payment_data['overall_total'] . '&payment_id=' . $payment_id);
exit;

?>