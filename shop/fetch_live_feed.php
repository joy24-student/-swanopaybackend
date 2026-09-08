<?php
require_once('admin/inc/config.php');

// Define Currency Symbol manually or include header if strictly needed
// Assuming LANG_VALUE_1 is available or hardcoded
$currency = "BDT"; 

// Fetch ONE random product that is active
$sql_rand = defined('SQL_RAND') ? SQL_RAND : 'RAND()';
$stmt = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_active=1 ORDER BY {$sql_rand} LIMIT 1");
$stmt->execute();
$product = $stmt->fetch(PDO::FETCH_ASSOC);

if($product) {
    $response = [
        'success' => true,
        'product' => [
            'id'    => $product['p_id'],
            'name'  => $product['p_name'],
            'image' => 'assets/uploads/' . $product['p_featured_photo'],
            'link'  => 'product.php?id=' . $product['p_id'],
            'price' => $currency . number_format($product['p_current_price']),
            'old_price' => ($product['p_old_price'] > 0) ? $currency . number_format($product['p_old_price']) : null,
            'discount_badge' => ($product['p_old_price'] > $product['p_current_price']) ? '<div class="discount-badge">Sale</div>' : ''
        ]
    ];
} else {
    $response = ['success' => false];
}

header('Content-Type: application/json');
echo json_encode($response);
?>