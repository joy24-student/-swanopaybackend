<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

$is_ajax = isset($_REQUEST['ajax']) || 
           (isset($_SERVER['HTTP_X_REQUESTED_WITH']) && strtolower($_SERVER['HTTP_X_REQUESTED_WITH']) === 'xmlhttprequest') ||
           (isset($_SERVER['HTTP_ACCEPT']) && strpos($_SERVER['HTTP_ACCEPT'], 'application/json') !== false);

// Check if parameters are provided
$product_id = $_REQUEST['id'] ?? null;
$size_id = $_REQUEST['size'] ?? 0;
$color_id = $_REQUEST['color'] ?? 0;
$item_index = isset($_REQUEST['index']) ? (int)$_REQUEST['index'] : null;

if ($product_id === null && $item_index === null) {
    if ($is_ajax) {
        header('Content-Type: application/json');
        echo json_encode(['success' => false, 'message' => 'Missing product ID or index.']);
        exit;
    }
    header('location: cart.php');
    exit;
}

// Delete from database if customer is logged in
if (isset($_SESSION['customer']['cust_id']) && $product_id !== null) {
    removeCartItem($pdo, $_SESSION['customer']['cust_id'], $product_id, $size_id, $color_id);
}

// Re-index and filter session items
if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id'])) {
    $arr_cart_p_id = array_values($_SESSION['cart_p_id']);
    $arr_cart_size_id = array_values($_SESSION['cart_size_id'] ?? []);
    $arr_cart_size_name = array_values($_SESSION['cart_size_name'] ?? []);
    $arr_cart_color_id = array_values($_SESSION['cart_color_id'] ?? []);
    $arr_cart_color_name = array_values($_SESSION['cart_color_name'] ?? []);
    $arr_cart_p_qty = array_values($_SESSION['cart_p_qty'] ?? []);
    $arr_cart_p_current_price = array_values($_SESSION['cart_p_current_price'] ?? []);
    $arr_cart_p_name = array_values($_SESSION['cart_p_name'] ?? []);
    $arr_cart_p_featured_photo = array_values($_SESSION['cart_p_featured_photo'] ?? []);

    $_SESSION['cart_p_id'] = [];
    $_SESSION['cart_size_id'] = [];
    $_SESSION['cart_size_name'] = [];
    $_SESSION['cart_color_id'] = [];
    $_SESSION['cart_color_name'] = [];
    $_SESSION['cart_p_qty'] = [];
    $_SESSION['cart_p_current_price'] = [];
    $_SESSION['cart_p_name'] = [];
    $_SESSION['cart_p_featured_photo'] = [];

    $k = 1;
    $count = count($arr_cart_p_id);
    for ($i = 0; $i < $count; $i++) {
        $match = false;
        if ($item_index !== null && $i === $item_index) {
            $match = true;
        } elseif ($product_id !== null && $arr_cart_p_id[$i] == $product_id && 
                  ($arr_cart_size_id[$i] ?? 0) == $size_id && 
                  ($arr_cart_color_id[$i] ?? 0) == $color_id) {
            $match = true;
        }

        if ($match) {
            continue;
        } else {
            $_SESSION['cart_p_id'][$k] = $arr_cart_p_id[$i];
            $_SESSION['cart_size_id'][$k] = $arr_cart_size_id[$i] ?? 0;
            $_SESSION['cart_size_name'][$k] = $arr_cart_size_name[$i] ?? '';
            $_SESSION['cart_color_id'][$k] = $arr_cart_color_id[$i] ?? 0;
            $_SESSION['cart_color_name'][$k] = $arr_cart_color_name[$i] ?? '';
            $_SESSION['cart_p_qty'][$k] = $arr_cart_p_qty[$i] ?? 1;
            $_SESSION['cart_p_current_price'][$k] = $arr_cart_p_current_price[$i] ?? 0;
            $_SESSION['cart_p_name'][$k] = $arr_cart_p_name[$i] ?? '';
            $_SESSION['cart_p_featured_photo'][$k] = $arr_cart_p_featured_photo[$i] ?? '';
            $k++;
        }
    }
}

// Calculate remaining total and count
$total_qty = 0;
$total_price = 0;
if (!empty($_SESSION['cart_p_id'])) {
    foreach ($_SESSION['cart_p_qty'] as $idx => $q) {
        $total_qty += (int)$q;
        $total_price += ((float)($_SESSION['cart_p_current_price'][$idx] ?? 0)) * (int)$q;
    }
}

if ($is_ajax) {
    header('Content-Type: application/json');
    echo json_encode([
        'success' => true,
        'message' => 'Item removed from cart.',
        'cart_count' => $total_qty,
        'item_count' => count($_SESSION['cart_p_id'] ?? []),
        'subtotal' => $total_price
    ]);
    exit;
}

header('location: cart.php');
exit;