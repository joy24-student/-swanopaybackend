<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
header('Content-Type: application/json');
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

$p_id = (int)($_POST['product_id'] ?? $_GET['product_id'] ?? 0);
$p_qty_added = max(1, (int)($_POST['quantity'] ?? 1));

if ($p_id <= 0) {
    echo json_encode(['success' => false, 'message' => 'Invalid product.']);
    exit;
}

$stmt = $pdo->prepare("SELECT p_id, p_name, p_current_price, p_featured_photo, p_qty FROM tbl_product WHERE p_id = ? AND p_is_active = 1 LIMIT 1");
$stmt->execute([$p_id]);
$product = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$product) {
    echo json_encode(['success' => false, 'message' => 'Product not found.']);
    exit;
}

if ($product['p_qty'] < $p_qty_added) {
    echo json_encode(['success' => false, 'message' => 'Item is out of stock.']);
    exit;
}

// Initialize session arrays if needed
if (!isset($_SESSION['cart_p_id']) || !is_array($_SESSION['cart_p_id'])) {
    $_SESSION['cart_p_id'] = [];
    $_SESSION['cart_size_id'] = [];
    $_SESSION['cart_size_name'] = [];
    $_SESSION['cart_color_id'] = [];
    $_SESSION['cart_color_name'] = [];
    $_SESSION['cart_p_qty'] = [];
    $_SESSION['cart_p_current_price'] = [];
    $_SESSION['cart_p_name'] = [];
    $_SESSION['cart_p_featured_photo'] = [];
}

$size_id = (int)($_POST['size_id'] ?? 0);
$size_name = trim($_POST['size_name'] ?? '');
$color_id = (int)($_POST['color_id'] ?? 0);
$color_name = trim($_POST['color_name'] ?? '');

$item_found = false;
$found_index = -1;

foreach ($_SESSION['cart_p_id'] as $k => $id) {
    if ($id == $p_id && ($_SESSION['cart_size_id'][$k] ?? 0) == $size_id && ($_SESSION['cart_color_id'][$k] ?? 0) == $color_id) {
        $item_found = true;
        $found_index = $k;
        break;
    }
}

if ($item_found) {
    $_SESSION['cart_p_qty'][$found_index] += $p_qty_added;
    if (isset($_SESSION['customer']['cust_id'])) {
        updateCartItemQuantity($pdo, $_SESSION['customer']['cust_id'], $p_id, $size_id, $color_id, $_SESSION['cart_p_qty'][$found_index]);
    }
} else {
    $next_index = !empty($_SESSION['cart_p_id']) ? max(array_keys($_SESSION['cart_p_id'])) + 1 : 1;
    $_SESSION['cart_p_id'][$next_index] = $product['p_id'];
    $_SESSION['cart_size_id'][$next_index] = $size_id;
    $_SESSION['cart_size_name'][$next_index] = $size_name;
    $_SESSION['cart_color_id'][$next_index] = $color_id;
    $_SESSION['cart_color_name'][$next_index] = $color_name;
    $_SESSION['cart_p_qty'][$next_index] = $p_qty_added;
    $_SESSION['cart_p_current_price'][$next_index] = $product['p_current_price'];
    $_SESSION['cart_p_name'][$next_index] = $product['p_name'];
    $_SESSION['cart_p_featured_photo'][$next_index] = $product['p_featured_photo'];

    if (isset($_SESSION['customer']['cust_id'])) {
        addOrUpdateCartItem($pdo, $_SESSION['customer']['cust_id'], $p_id, $size_id, $size_name, $color_id, $color_name, $p_qty_added, $product['p_current_price'], $product['p_name'], $product['p_featured_photo']);
    }
}

$total_cart_count = 0;
foreach ($_SESSION['cart_p_qty'] as $q) {
    $total_cart_count += (int)$q;
}

echo json_encode([
    'success' => true,
    'message' => htmlspecialchars($product['p_name']) . ' added to cart!',
    'cart_count' => $total_cart_count,
    'product_name' => $product['p_name'],
    'product_price' => $product['p_current_price']
]);

