<?php
ob_start();
session_start();
include("../../admin/inc/config.php");
include("../../admin/inc/functions.php");

// Validate access - make sure a customer is logged in and the cart is not empty
if(!isset($_SESSION['customer']) || !isset($_SESSION['cart_p_id'])) {
    header('location: ../../login.php');
    exit;
require_once __DIR__ . '/../../admin/inc/config.php';
require_once __DIR__ . '/../../admin/inc/CSRF_Protect.php';
$csrf = new CSRF_Protect();
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { http_response_code(405); header('Allow: POST'); exit; }
if (!$csrf->checkToken()) { http_response_code(403); exit('Your checkout session expired. Return to checkout and try again.'); }
if (empty($_SESSION['customer']['cust_id'])) { header('Location: ../../login.php'); exit; }
$token = (string)($_POST['checkout_token'] ?? '');
if (isset($_SESSION['completed_checkouts'][$token])) {
    header('Location: ../../payment_success.php?method=cod&payment_id=' . rawurlencode($_SESSION['completed_checkouts'][$token])); exit;
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


// --- INSERT INTO tbl_payment (32 COLUMNS) ---
// This includes previous fixes for required fields like card_number and bank_transaction_info
$statement = $pdo->prepare("INSERT INTO tbl_payment (
    customer_id, customer_name, customer_email, payment_date, 
    txnid, paid_amount, shipping_cost, coupon_code, coupon_discount, 
    payment_method, payment_status, shipping_status, payment_id, payment_note, 
    
    card_number, bank_transaction_info,                 
    
    billing_name, billing_email, billing_phone, billing_street, billing_city, 
    billing_state, billing_country, billing_zip, shipping_name, shipping_email, 
    shipping_phone, shipping_street, shipping_city, shipping_state, shipping_country, shipping_zip
) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
// --- INSERT INTO tbl_payment (32 COLUMNS) for legacy compatibility ---
if (!$token || !hash_equals($_SESSION['checkout_token'] ?? '',$token) || empty($_SESSION['payment_data']['cart_p_id'])) { http_response_code(409); exit('This checkout is no longer valid. Return to your cart.'); }
$data = $_SESSION['payment_data'];
$customer = $_SESSION['customer'];
$billing = $_SESSION['billing_address_details'] ?? [];
$shipping = $_SESSION['shipping_address_details'] ?? [];
$paymentId = 'COD-' . bin2hex(random_bytes(16));
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
    $payment_data['customer_id'],
    $payment_data['customer_name'],
    $payment_data['customer_email'],
    $payment_date,
    '',                                     // txnid (Empty for COD)
    $payment_data['overall_total'],
    $payment_data['shipping_cost'],
    $payment_data['coupon_code'],
    $payment_data['coupon_discount'],
    'Cash on Delivery',
    'Pending',                              // Payment status
    'Pending',                              // Shipping status
    $payment_id,
    $customer_note,
    
    '',                                     // card_number (Empty for COD)
    '',                                     // bank_transaction_info (Empty for COD)
    
    // --- Billing Address Data ---
    $billing_details['name'],
    $payment_data['customer_email'],        // billing_email
    $billing_details['phone'],
    $billing_details['address'],            // billing_street
    $billing_details['city'],
    $billing_details['state'],
    $billing_details['country_id'],         // billing_country
    $billing_details['zip'],
    // --- Shipping Address Data ---
    $shipping_details['name'],
    $payment_data['customer_email'],        // shipping_email
    $shipping_details['phone'],
    $shipping_details['address'],           // shipping_street
    $shipping_details['city'],
    $shipping_details['state'],
    $shipping_details['country_id'],        // shipping_country
    $shipping_details['zip']
]);


// --- INSERT INTO tbl_order (10 COLUMNS) ---
foreach($payment_data['cart_p_id'] as $key => $product_id) {
    // --- Insert into tbl_order ---
    // FIX: Column 'p_price' changed to 'unit_price' (from tbl_order schema)
    // FIX: Added 'coupon_code' and 'coupon_discount'
    $statement = $pdo->prepare("INSERT INTO tbl_order (
        cust_id,
        product_id, 
        product_name, 
        size, 
        color, 
        quantity, 
        unit_price,              
        payment_id,
        coupon_code,
        coupon_discount
    ) VALUES (?,?,?,?,?,?,?,?,?,?)");
    
    $statement->execute([
        $payment_data['customer_id'],           
        $product_id,
        $payment_data['cart_p_name'][$key],
        $payment_data['cart_size_name'][$key],
        $payment_data['cart_color_name'][$key],
        $payment_data['cart_p_qty'][$key],
        $payment_data['cart_p_current_price'][$key], // Value is the current price
        $payment_data['customer_id'] ?? 0,
        $payment_data['customer_name'] ?? 'Walk-in Customer',
        $payment_data['customer_email'] ?? '',
        $payment_date,
        '',                                     // txnid (Empty for COD)
        $payment_data['overall_total'],
        $payment_data['shipping_cost'] ?? 0,
        $payment_data['coupon_code'] ?? '',
        $payment_data['coupon_discount'] ?? 0,
        'Cash on Delivery',
        'Pending',                              // Payment status
        'Pending',                              // Shipping status
        $payment_id,
        $payment_data['coupon_code'],
        $payment_data['coupon_discount']
        $customer_note,
        '',                                     // card_number (Empty for COD)
        '',                                     // bank_transaction_info (Empty for COD)
        $billing_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',  // billing_email
        $billing_details['phone'] ?? '',
        $billing_details['address'] ?? '',      // billing_street
        $billing_details['city'] ?? '',
        $billing_details['state'] ?? '',
        $billing_details['country_id'] ?? '',   // billing_country
        $billing_details['zip'] ?? '',
        $shipping_details['name'] ?? '',
        $payment_data['customer_email'] ?? '',  // shipping_email
        $shipping_details['phone'] ?? '',
        $shipping_details['address'] ?? '',     // shipping_street
        $shipping_details['city'] ?? '',
        $shipping_details['state'] ?? '',
        $shipping_details['country_id'] ?? '',  // shipping_country
        $shipping_details['zip'] ?? ''
    ]);
} catch (Exception $e) {
    error_log("tbl_payment insert skipped or failed: " . $e->getMessage());
}

    // --- Update product stock ---
    $statement = $pdo->prepare("UPDATE tbl_product SET p_qty = p_qty - ? WHERE p_id = ?");
    $statement->execute([$payment_data['cart_p_qty'][$key], $product_id]);
// --- INSERT INTO tbl_order & update stock for legacy compatibility ---
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

        $statement = $pdo->prepare("UPDATE tbl_product SET p_qty = p_qty - ? WHERE p_id = ?");
        $statement->execute([$payment_data['cart_p_qty'][$key] ?? 1, $product_id]);
    } catch (Exception $e) {
        error_log("tbl_order insert skipped or failed: " . $e->getMessage());
    $pdo->beginTransaction();
    $settings = $pdo->query('SELECT cod_enabled,payment_methods FROM tbl_settings WHERE id=1')->fetch();
    if (!(int)$settings['cod_enabled'] || !in_array('Cash on Delivery',array_map('trim',explode(',',$settings['payment_methods'])),true)) throw new RuntimeException('Cash on delivery is unavailable.');
    if (empty($shipping['name']) || empty($shipping['phone']) || empty($shipping['address'])) throw new RuntimeException('Complete your shipping address before placing the order.');
    $items = []; $subtotal = 0; $requested = [];
    foreach ($data['cart_p_id'] as $key=>$id) {
        $qty = filter_var($data['cart_p_qty'][$key] ?? null,FILTER_VALIDATE_INT);
        if (!$qty || $qty < 1 || $qty > 100000) throw new RuntimeException('Check the quantities in your cart.');
        $requested[(int)$id] = ($requested[(int)$id] ?? 0) + $qty;
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
    ksort($requested); // A consistent lock order avoids competing checkout deadlocks.
    foreach ($requested as $id=>$qty) {
        $stmt=$pdo->prepare('SELECT p_id,p_name,p_current_price,p_qty,p_is_active FROM tbl_product WHERE p_id=? FOR UPDATE');
        $stmt->execute([$id]); $product=$stmt->fetch();
        if (!$product || !(int)$product['p_is_active'] || (int)$product['p_qty'] < $qty) throw new RuntimeException('An item is no longer available in the requested quantity. Please review your cart.');
        $price=(int)round((float)$product['p_current_price'] * 100);
        $subtotal += $price*$qty;
        $items[$id]=['product'=>$product,'price'=>$price];
    }
    $country=$shipping['country_id'] ?? $customer['cust_s_country'] ?? $customer['cust_country'];
    $stmt=$pdo->prepare('SELECT amount FROM tbl_shipping_cost WHERE country_id=?'); $stmt->execute([$country]);
    $cost=$stmt->fetchColumn();
    if ($cost === false) $cost=$pdo->query('SELECT amount FROM tbl_shipping_cost_all WHERE sca_id=1')->fetchColumn();
    $shippingCents=max(0,(int)round((float)$cost*100)); $discount=0; $coupon=null;
    if (!empty($data['coupon_code'])) {
        $stmt=$pdo->prepare("SELECT * FROM tbl_coupon WHERE coupon_code=? AND status='Active' FOR UPDATE"); $stmt->execute([$data['coupon_code']]); $coupon=$stmt->fetch();
        $today=date('Y-m-d');
        if (!$coupon || substr($coupon['start_date'],0,10)>$today || substr($coupon['end_date'],0,10)<$today || ($coupon['usage_limit']>0 && $coupon['used_count'] >= $coupon['usage_limit']) || (float)$coupon['minimum_order']*100>$subtotal) throw new RuntimeException('Your coupon is no longer available. Please review checkout.');
        $discount=$coupon['discount_type']==='percentage' ? (int)round($subtotal*(float)$coupon['discount_value']/100) : (int)round((float)$coupon['discount_value']*100);
        $discount=min($subtotal,max(0,$discount));
    }
    $total=($subtotal+$shippingCents-$discount)/100;
    if (abs($total-(float)($data['overall_total'] ?? -1))>0.009) throw new RuntimeException('Prices or delivery charges changed. Return to checkout to review the new total.');
    $stmt=$pdo->prepare('INSERT INTO tbl_payment(customer_id,customer_name,customer_email,payment_date,txnid,paid_amount,shipping_cost,coupon_code,coupon_discount,payment_method,payment_status,shipping_status,payment_id,payment_note,billing_name,billing_email,billing_phone,billing_street,billing_city,billing_state,billing_country,billing_zip,shipping_name,shipping_email,shipping_phone,shipping_street,shipping_city,shipping_state,shipping_country,shipping_zip) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');
    $stmt->execute([$customer['cust_id'],$customer['cust_name'],$customer['cust_email'],date('Y-m-d H:i:s'),'',$total,$shippingCents/100,$coupon['coupon_code'] ?? '',$discount/100,'Cash on Delivery','Pending','Pending',$paymentId,substr(strip_tags($_POST['customer_note'] ?? ''),0,2000),
        $billing['name'] ?? '',$customer['cust_email'],$billing['phone'] ?? '',$billing['address'] ?? '',$billing['city'] ?? '',$billing['state'] ?? '',$billing['country_id'] ?? '',$billing['zip'] ?? '',
        $shipping['name'],$customer['cust_email'],$shipping['phone'],$shipping['address'],$shipping['city'] ?? '',$shipping['state'] ?? '',$country,$shipping['zip'] ?? '']);
    foreach ($data['cart_p_id'] as $key=>$id) {
        $item=$items[(int)$id]; $qty=(int)$data['cart_p_qty'][$key];
        $pdo->prepare('INSERT INTO tbl_order(cust_id,product_id,product_name,size,color,quantity,unit_price,payment_id,coupon_code,coupon_discount) VALUES (?,?,?,?,?,?,?,?,?,?)')->execute([$customer['cust_id'],$id,$item['product']['p_name'],$data['cart_size_name'][$key] ?? '',$data['cart_color_name'][$key] ?? '',$qty,$item['price']/100,$paymentId,$coupon['coupon_code'] ?? '',0]);
    }
    foreach ($requested as $id=>$qty) $pdo->prepare('UPDATE tbl_product SET p_qty=p_qty-? WHERE p_id=?')->execute([$qty,$id]);
    if ($coupon) $pdo->prepare('UPDATE tbl_coupon SET used_count=used_count+1 WHERE coupon_id=?')->execute([$coupon['coupon_id']]);
    $pdo->prepare('DELETE FROM tbl_customer_carts WHERE customer_id=?')->execute([$customer['cust_id']]);
    $pdo->commit();
    $_SESSION['completed_checkouts'][$token]=$paymentId;
    $_SESSION['completed_checkouts']=array_slice($_SESSION['completed_checkouts'],-10,null,true);
    foreach (array_keys($_SESSION) as $key) if (str_starts_with($key,'cart_') || in_array($key,['payment_data','coupon','checkout_token'],true)) unset($_SESSION[$key]);
    header('Location: ../../payment_success.php?method=cod&payment_id=' . rawurlencode($paymentId)); exit;
} catch (Throwable $error) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('COD checkout failed: ' . $error->getCode());
    http_response_code(409);
    $message=$error instanceof PDOException ? 'Your order could not be saved. Please try again.' : $error->getMessage();
    echo htmlspecialchars($message,ENT_QUOTES,'UTF-8') . ' <a href="../../checkout.php">Return to checkout</a>';
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