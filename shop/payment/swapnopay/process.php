<?php
ob_start();
session_start();
require_once("../../admin/inc/config.php");
require_once("../../admin/inc/functions.php");

// Validate customer session and cart
if (!isset($_SESSION['customer']) || !isset($_SESSION['cart_p_id']) || empty($_SESSION['cart_p_id'])) {
    header('location: ../../cart.php');
    exit;
}

$payment_data = $_SESSION['payment_data'] ?? [];
$billing_details = $_SESSION['billing_address_details'] ?? [];
$shipping_details = $_SESSION['shipping_address_details'] ?? [];

$selected_method = strip_tags($_POST['mfs_provider'] ?? 'bKash');
if (!in_array($selected_method, ['bKash', 'Nagad', 'Rocket', 'Upay'])) {
    $selected_method = 'bKash';
}

$customer_note = strip_tags($_POST['customer_note'] ?? '');
$tran_id = 'SWP-' . time() . '-' . mt_rand(1000, 9999);
$order_number = 'ORD-' . date('Ymd') . '-' . mt_rand(1000, 9999);
$payment_date = date('Y-m-d H:i:s');
$total_amount = (float)($payment_data['overall_total'] ?? 0);

// ----------------------------------------------------------------------------
// 1. Insert into Supabase Orders & Order Items
// ----------------------------------------------------------------------------
$supabase_url = defined('SUPABASE_URL') ? SUPABASE_URL : getenv('SUPABASE_URL');
$supabase_key = defined('SUPABASE_SERVICE_KEY') && !empty(SUPABASE_SERVICE_KEY) 
    ? SUPABASE_SERVICE_KEY 
    : (defined('SUPABASE_ANON_KEY') ? SUPABASE_ANON_KEY : getenv('SUPABASE_ANON_KEY'));

$supabase_order_id = null;

if (!empty($supabase_url) && !empty($supabase_key)) {
    try {
        $clean_supabase_url = rtrim($supabase_url, '/');

        // Fetch merchant ID
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
            $order_payload = json_encode([
                'merchant_id' => $merchant_id,
                'tran_id' => $tran_id,
                'order_number' => $order_number,
                'amount' => $total_amount,
                'total_amount' => $total_amount,
                'subtotal' => (float)($payment_data['paid_amount'] ?? $total_amount),
                'shipping_cost' => (float)($payment_data['shipping_cost'] ?? 0),
                'discount_amount' => (float)($payment_data['coupon_discount'] ?? 0),
                'cus_phone' => (string)($shipping_details['phone'] ?? ($billing_details['phone'] ?? '01700000000')),
                'cus_name' => (string)($payment_data['customer_name'] ?? 'Customer'),
                'cus_email' => (string)($payment_data['customer_email'] ?? ''),
                'shipping_address' => (string)($shipping_details['address'] ?? ''),
                'shipping_city' => (string)($shipping_details['city'] ?? ''),
                'payment_method' => $selected_method,
                'status' => 'PENDING',
                'order_status' => 'PENDING',
                'customer_note' => $customer_note,
                'expires_at' => date('c', strtotime('+15 minutes')) // 15-minute verification window
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
            curl_close($ch_order);

            $inserted_order = json_decode($res_order, true);
            $supabase_order_id = !empty($inserted_order[0]['id']) ? $inserted_order[0]['id'] : null;

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
        error_log("SwapnoPay Supabase order creation error: " . $e->getMessage());
    }
}

// ----------------------------------------------------------------------------
// 2. Insert into Legacy Tables for Local Compatibility
// ----------------------------------------------------------------------------
try {
    $stmt_pay = $pdo->prepare("INSERT INTO tbl_payment (
        customer_id, customer_name, customer_email, payment_date, 
        txnid, paid_amount, shipping_cost, coupon_code, coupon_discount, 
        payment_method, payment_status, shipping_status, payment_id, payment_note, 
        card_number, bank_transaction_info,                 
        billing_name, billing_email, billing_phone, billing_street, billing_city, 
        billing_state, billing_country, billing_zip, shipping_name, shipping_email, 
        shipping_phone, shipping_street, shipping_city, shipping_state, shipping_country, shipping_zip
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

    $stmt_pay->execute([
        $payment_data['customer_id'] ?? 0,
        $payment_data['customer_name'] ?? 'Customer',
        $payment_data['customer_email'] ?? '',
        $payment_date,
        '',
        $total_amount,
        $payment_data['shipping_cost'] ?? 0,
        $payment_data['coupon_code'] ?? '',
        $payment_data['coupon_discount'] ?? 0,
        $selected_method,
        'Pending',
        'Pending',
        $tran_id,
        $customer_note,
        '', '',
        $billing_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',
        $billing_details['phone'] ?? '',
        $billing_details['address'] ?? '',
        $billing_details['city'] ?? '',
        $billing_details['state'] ?? '',
        $billing_details['country_id'] ?? '',
        $billing_details['zip'] ?? '',
        $shipping_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',
        $shipping_details['phone'] ?? '',
        $shipping_details['address'] ?? '',
        $shipping_details['city'] ?? '',
        $shipping_details['state'] ?? '',
        $shipping_details['country_id'] ?? '',
        $shipping_details['zip'] ?? ''
    ]);

    foreach($payment_data['cart_p_id'] as $key => $product_id) {
        $stmt_item = $pdo->prepare("INSERT INTO tbl_order (
            cust_id, product_id, product_name, size, color, quantity, unit_price, payment_id, coupon_code, coupon_discount
        ) VALUES (?,?,?,?,?,?,?,?,?,?)");
        $stmt_item->execute([
            $payment_data['customer_id'] ?? 0,           
            $product_id,
            $payment_data['cart_p_name'][$key] ?? '',
            $payment_data['cart_size_name'][$key] ?? '',
            $payment_data['cart_color_name'][$key] ?? '',
            $payment_data['cart_p_qty'][$key] ?? 1,
            $payment_data['cart_p_current_price'][$key] ?? 0,
            $tran_id,
            $payment_data['coupon_code'] ?? '',
            $payment_data['coupon_discount'] ?? 0
        ]);
    }
} catch (Exception $e) {
    error_log("Legacy payment table insert skipped: " . $e->getMessage());
}

// Store pending order details in session
$_SESSION['pending_tran_id'] = $tran_id;
$_SESSION['pending_order_number'] = $order_number;
$_SESSION['pending_method'] = $selected_method;
$_SESSION['pending_amount'] = $total_amount;

// Redirect to real-time verification screen
header("Location: verify.php?tran_id={$tran_id}&method={$selected_method}");
exit;

