<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
header('Content-Type: application/json');
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

// Database safeguards
try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS `tbl_customer_carts` (
      `cart_id` int(11) NOT NULL AUTO_INCREMENT,
      `customer_id` int(11) NOT NULL,
      `product_id` int(11) NOT NULL,
      `size_id` int(11) DEFAULT 0,
      `size_name` varchar(255) DEFAULT '',
      `color_id` int(11) DEFAULT 0,
      `color_name` varchar(255) DEFAULT '',
      `quantity` int(11) NOT NULL DEFAULT 1,
      `price_at_add` decimal(10,2) NOT NULL DEFAULT 0.00,
      `product_name` varchar(255) DEFAULT '',
      `product_photo` varchar(255) DEFAULT NULL,
      `added_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
      `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (`cart_id`),
      KEY `customer_id` (`customer_id`),
      KEY `product_id` (`product_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    $pdo->exec("CREATE TABLE IF NOT EXISTS `tbl_wishlist` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `cust_id` int(11) NOT NULL,
      `product_id` int(11) NOT NULL,
      `added_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`),
      KEY `cust_id` (`cust_id`),
      KEY `product_id` (`product_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
} catch (Exception $e) {}

$action = $_POST['action'] ?? $_GET['action'] ?? '';

// Helper function to calculate cart summary
function getCartSummaryState() {
    $cart_p_ids = array_values($_SESSION['cart_p_id'] ?? []);
    $cart_qtys = array_values($_SESSION['cart_p_qty'] ?? []);
    $cart_prices = array_values($_SESSION['cart_p_current_price'] ?? []);
    $cart_names = array_values($_SESSION['cart_p_name'] ?? []);

    $subtotal = 0;
    $total_items = 0;
    for ($i = 0; $i < count($cart_p_ids); $i++) {
        $qty = (int)($cart_qtys[$i] ?? 1);
        $price = (float)($cart_prices[$i] ?? 0);
        $subtotal += ($qty * $price);
        $total_items += $qty;
    }

    $discount = 0;
    $coupon_code = $_SESSION['cart_coupon']['code'] ?? '';
    if (!empty($_SESSION['cart_coupon'])) {
        $type = $_SESSION['cart_coupon']['type'] ?? 'percentage';
        $val = (float)($_SESSION['cart_coupon']['value'] ?? 0);
        if ($type === 'percentage') {
            $discount = ($subtotal * $val) / 100;
        } else {
            $discount = min($subtotal, $val);
        }
    }

    $shipping = 0; // Free shipping
    $tax = 0;
    $total = max(0, $subtotal - $discount + $shipping + $tax);

    return [
        'item_count' => count($cart_p_ids),
        'total_qty' => $total_items,
        'subtotal' => $subtotal,
        'discount' => $discount,
        'coupon_code' => $coupon_code,
        'shipping' => $shipping,
        'tax' => $tax,
        'total' => $total
    ];
}

switch ($action) {
    case 'update_qty':
        $index = isset($_POST['index']) ? (int)$_POST['index'] : null;
        $qty = max(1, (int)($_POST['qty'] ?? 1));
        $product_id = isset($_POST['product_id']) ? (int)$_POST['product_id'] : null;
        $size_id = (int)($_POST['size_id'] ?? 0);
        $color_id = (int)($_POST['color_id'] ?? 0);

        // Check stock availability in database if real product
        if ($product_id && $product_id < 900) {
            try {
                $stockCheck = $pdo->prepare("SELECT p_qty, p_name FROM tbl_product WHERE p_id = ? AND p_is_active = 1");
                $stockCheck->execute([$product_id]);
                $pData = $stockCheck->fetch(PDO::FETCH_ASSOC);
                if ($pData && (int)$pData['p_qty'] < $qty) {
                    echo json_encode([
                        'success' => false,
                        'message' => 'Only ' . $pData['p_qty'] . ' unit(s) available for "' . htmlspecialchars($pData['p_name']) . '".',
                        'max_qty' => (int)$pData['p_qty']
                    ]);
                    exit;
                }
            } catch (Exception $e) {}
        }

        if ($index !== null && isset($_SESSION['cart_p_id'])) {
            $keys = array_keys($_SESSION['cart_p_id']);
            if (isset($keys[$index])) {
                $session_key = $keys[$index];
                $_SESSION['cart_p_qty'][$session_key] = $qty;

                // Sync with DB if logged in
                if (isset($_SESSION['customer']['cust_id'])) {
                    $pid = $_SESSION['cart_p_id'][$session_key];
                    $sid = $_SESSION['cart_size_id'][$session_key] ?? 0;
                    $cid = $_SESSION['cart_color_id'][$session_key] ?? 0;
                    updateCartItemQuantity($pdo, $_SESSION['customer']['cust_id'], $pid, $sid, $cid, $qty);
                }

                $item_price = (float)($_SESSION['cart_p_current_price'][$session_key] ?? 0);
                $row_total = $item_price * $qty;
                $summary = getCartSummaryState();

                echo json_encode([
                    'success' => true,
                    'message' => 'Quantity updated',
                    'row_total' => $row_total,
                    'summary' => $summary
                ]);
                exit;
            }
        }
        echo json_encode(['success' => false, 'message' => 'Item not found.']);
        exit;

    case 'delete_item':
        $index = isset($_POST['index']) ? (int)$_POST['index'] : null;
        $product_id = isset($_POST['product_id']) ? (int)$_POST['product_id'] : null;
        $size_id = (int)($_POST['size_id'] ?? 0);
        $color_id = (int)($_POST['color_id'] ?? 0);

        if (isset($_SESSION['customer']['cust_id']) && $product_id !== null) {
            removeCartItem($pdo, $_SESSION['customer']['cust_id'], $product_id, $size_id, $color_id);
        }

        if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id'])) {
            $keys = array_keys($_SESSION['cart_p_id']);
            if ($index !== null && isset($keys[$index])) {
                $del_key = $keys[$index];
                unset($_SESSION['cart_p_id'][$del_key]);
                unset($_SESSION['cart_size_id'][$del_key]);
                unset($_SESSION['cart_size_name'][$del_key]);
                unset($_SESSION['cart_color_id'][$del_key]);
                unset($_SESSION['cart_color_name'][$del_key]);
                unset($_SESSION['cart_p_qty'][$del_key]);
                unset($_SESSION['cart_p_current_price'][$del_key]);
                unset($_SESSION['cart_p_name'][$del_key]);
                unset($_SESSION['cart_p_featured_photo'][$del_key]);
                if (isset($_SESSION['cart_p_old_price'][$del_key])) unset($_SESSION['cart_p_old_price'][$del_key]);
                if (isset($_SESSION['cart_p_subtitle'][$del_key])) unset($_SESSION['cart_p_subtitle'][$del_key]);
                if (isset($_SESSION['cart_p_badge'][$del_key])) unset($_SESSION['cart_p_badge'][$del_key]);
            }
        }

        // Re-index array keys to 1..N
        if (!empty($_SESSION['cart_p_id'])) {
            $_SESSION['cart_p_id'] = array_combine(range(1, count($_SESSION['cart_p_id'])), array_values($_SESSION['cart_p_id']));
            $_SESSION['cart_size_id'] = array_combine(range(1, count($_SESSION['cart_size_id'])), array_values($_SESSION['cart_size_id']));
            $_SESSION['cart_size_name'] = array_combine(range(1, count($_SESSION['cart_size_name'])), array_values($_SESSION['cart_size_name']));
            $_SESSION['cart_color_id'] = array_combine(range(1, count($_SESSION['cart_color_id'])), array_values($_SESSION['cart_color_id']));
            $_SESSION['cart_color_name'] = array_combine(range(1, count($_SESSION['cart_color_name'])), array_values($_SESSION['cart_color_name']));
            $_SESSION['cart_p_qty'] = array_combine(range(1, count($_SESSION['cart_p_qty'])), array_values($_SESSION['cart_p_qty']));
            $_SESSION['cart_p_current_price'] = array_combine(range(1, count($_SESSION['cart_p_current_price'])), array_values($_SESSION['cart_p_current_price']));
            $_SESSION['cart_p_name'] = array_combine(range(1, count($_SESSION['cart_p_name'])), array_values($_SESSION['cart_p_name']));
            $_SESSION['cart_p_featured_photo'] = array_combine(range(1, count($_SESSION['cart_p_featured_photo'])), array_values($_SESSION['cart_p_featured_photo']));
            if (!empty($_SESSION['cart_p_old_price'])) {
                $_SESSION['cart_p_old_price'] = array_combine(range(1, count($_SESSION['cart_p_old_price'])), array_values($_SESSION['cart_p_old_price']));
            }
            if (!empty($_SESSION['cart_p_subtitle'])) {
                $_SESSION['cart_p_subtitle'] = array_combine(range(1, count($_SESSION['cart_p_subtitle'])), array_values($_SESSION['cart_p_subtitle']));
            }
            if (!empty($_SESSION['cart_p_badge'])) {
                $_SESSION['cart_p_badge'] = array_combine(range(1, count($_SESSION['cart_p_badge'])), array_values($_SESSION['cart_p_badge']));
            }
        }

        $summary = getCartSummaryState();
        echo json_encode([
            'success' => true,
            'message' => 'Item removed from cart.',
            'summary' => $summary
        ]);
        exit;

    case 'bulk_delete':
        $indexes = $_POST['indexes'] ?? [];
        if (!is_array($indexes)) {
            $indexes = json_decode($indexes, true) ?: [];
        }

        if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id'])) {
            $keys = array_keys($_SESSION['cart_p_id']);
            foreach ($indexes as $idx) {
                $idx = (int)$idx;
                if (isset($keys[$idx])) {
                    $k = $keys[$idx];
                    if (isset($_SESSION['customer']['cust_id'])) {
                        $pid = $_SESSION['cart_p_id'][$k] ?? null;
                        $sid = $_SESSION['cart_size_id'][$k] ?? 0;
                        $cid = $_SESSION['cart_color_id'][$k] ?? 0;
                        if ($pid) removeCartItem($pdo, $_SESSION['customer']['cust_id'], $pid, $sid, $cid);
                    }
                    unset($_SESSION['cart_p_id'][$k]);
                    unset($_SESSION['cart_size_id'][$k]);
                    unset($_SESSION['cart_size_name'][$k]);
                    unset($_SESSION['cart_color_id'][$k]);
                    unset($_SESSION['cart_color_name'][$k]);
                    unset($_SESSION['cart_p_qty'][$k]);
                    unset($_SESSION['cart_p_current_price'][$k]);
                    unset($_SESSION['cart_p_name'][$k]);
                    unset($_SESSION['cart_p_featured_photo'][$k]);
                    if (isset($_SESSION['cart_p_old_price'][$k])) unset($_SESSION['cart_p_old_price'][$k]);
                    if (isset($_SESSION['cart_p_subtitle'][$k])) unset($_SESSION['cart_p_subtitle'][$k]);
                    if (isset($_SESSION['cart_p_badge'][$k])) unset($_SESSION['cart_p_badge'][$k]);
                }
            }

            if (!empty($_SESSION['cart_p_id'])) {
                $_SESSION['cart_p_id'] = array_combine(range(1, count($_SESSION['cart_p_id'])), array_values($_SESSION['cart_p_id']));
                $_SESSION['cart_size_id'] = array_combine(range(1, count($_SESSION['cart_size_id'])), array_values($_SESSION['cart_size_id']));
                $_SESSION['cart_size_name'] = array_combine(range(1, count($_SESSION['cart_size_name'])), array_values($_SESSION['cart_size_name']));
                $_SESSION['cart_color_id'] = array_combine(range(1, count($_SESSION['cart_color_id'])), array_values($_SESSION['cart_color_id']));
                $_SESSION['cart_color_name'] = array_combine(range(1, count($_SESSION['cart_color_name'])), array_values($_SESSION['cart_color_name']));
                $_SESSION['cart_p_qty'] = array_combine(range(1, count($_SESSION['cart_p_qty'])), array_values($_SESSION['cart_p_qty']));
                $_SESSION['cart_p_current_price'] = array_combine(range(1, count($_SESSION['cart_p_current_price'])), array_values($_SESSION['cart_p_current_price']));
                $_SESSION['cart_p_name'] = array_combine(range(1, count($_SESSION['cart_p_name'])), array_values($_SESSION['cart_p_name']));
                $_SESSION['cart_p_featured_photo'] = array_combine(range(1, count($_SESSION['cart_p_featured_photo'])), array_values($_SESSION['cart_p_featured_photo']));
            }
        }

        $summary = getCartSummaryState();
        echo json_encode([
            'success' => true,
            'message' => 'Selected items removed.',
            'summary' => $summary
        ]);
        exit;

    case 'move_to_wishlist':
        $index = isset($_POST['index']) ? (int)$_POST['index'] : null;
        $product_id = isset($_POST['product_id']) ? (int)$_POST['product_id'] : null;
        $product_name = $_POST['product_name'] ?? 'Item';

        // Add to customer wishlist in DB if logged in
        if (isset($_SESSION['customer']['cust_id']) && $product_id) {
            try {
                $statement_check = $pdo->prepare("SELECT COUNT(*) FROM tbl_wishlist WHERE cust_id=? AND product_id=?");
                $statement_check->execute([$_SESSION['customer']['cust_id'], $product_id]);
                if ($statement_check->fetchColumn() == 0) {
                    $statement_add = $pdo->prepare("INSERT INTO tbl_wishlist (cust_id, product_id, added_date) VALUES (?, ?, NOW())");
                    $statement_add->execute([$_SESSION['customer']['cust_id'], $product_id]);
                }
            } catch (Exception $e) {
                // Table might not exist or error, fallback to session
            }
        }
        
        // Always store in session wishlist as well
        if (!isset($_SESSION['wishlist']) || !is_array($_SESSION['wishlist'])) {
            $_SESSION['wishlist'] = [];
        }
        if ($product_id && !in_array($product_id, $_SESSION['wishlist'])) {
            $_SESSION['wishlist'][] = $product_id;
        }

        // Remove from cart
        if ($index !== null && isset($_SESSION['cart_p_id'])) {
            $keys = array_keys($_SESSION['cart_p_id']);
            if (isset($keys[$index])) {
                $del_key = $keys[$index];
                if (isset($_SESSION['customer']['cust_id'])) {
                    $pid = $_SESSION['cart_p_id'][$del_key] ?? null;
                    $sid = $_SESSION['cart_size_id'][$del_key] ?? 0;
                    $cid = $_SESSION['cart_color_id'][$del_key] ?? 0;
                    if ($pid) removeCartItem($pdo, $_SESSION['customer']['cust_id'], $pid, $sid, $cid);
                }
                unset($_SESSION['cart_p_id'][$del_key]);
                unset($_SESSION['cart_size_id'][$del_key]);
                unset($_SESSION['cart_size_name'][$del_key]);
                unset($_SESSION['cart_color_id'][$del_key]);
                unset($_SESSION['cart_color_name'][$del_key]);
                unset($_SESSION['cart_p_qty'][$del_key]);
                unset($_SESSION['cart_p_current_price'][$del_key]);
                unset($_SESSION['cart_p_name'][$del_key]);
                unset($_SESSION['cart_p_featured_photo'][$del_key]);
                if (isset($_SESSION['cart_p_old_price'][$del_key])) unset($_SESSION['cart_p_old_price'][$del_key]);
                if (isset($_SESSION['cart_p_subtitle'][$del_key])) unset($_SESSION['cart_p_subtitle'][$del_key]);
                if (isset($_SESSION['cart_p_badge'][$del_key])) unset($_SESSION['cart_p_badge'][$del_key]);
            }

            if (!empty($_SESSION['cart_p_id'])) {
                $_SESSION['cart_p_id'] = array_combine(range(1, count($_SESSION['cart_p_id'])), array_values($_SESSION['cart_p_id']));
                $_SESSION['cart_size_id'] = array_combine(range(1, count($_SESSION['cart_size_id'])), array_values($_SESSION['cart_size_id']));
                $_SESSION['cart_size_name'] = array_combine(range(1, count($_SESSION['cart_size_name'])), array_values($_SESSION['cart_size_name']));
                $_SESSION['cart_color_id'] = array_combine(range(1, count($_SESSION['cart_color_id'])), array_values($_SESSION['cart_color_id']));
                $_SESSION['cart_color_name'] = array_combine(range(1, count($_SESSION['cart_color_name'])), array_values($_SESSION['cart_color_name']));
                $_SESSION['cart_p_qty'] = array_combine(range(1, count($_SESSION['cart_p_qty'])), array_values($_SESSION['cart_p_qty']));
                $_SESSION['cart_p_current_price'] = array_combine(range(1, count($_SESSION['cart_p_current_price'])), array_values($_SESSION['cart_p_current_price']));
                $_SESSION['cart_p_name'] = array_combine(range(1, count($_SESSION['cart_p_name'])), array_values($_SESSION['cart_p_name']));
                $_SESSION['cart_p_featured_photo'] = array_combine(range(1, count($_SESSION['cart_p_featured_photo'])), array_values($_SESSION['cart_p_featured_photo']));
            }
        }

        $summary = getCartSummaryState();
        echo json_encode([
            'success' => true,
            'message' => htmlspecialchars($product_name) . ' moved to Wishlist!',
            'summary' => $summary
        ]);
        exit;

    case 'apply_coupon':
        $code = strtoupper(trim($_POST['code'] ?? ''));
        if (empty($code)) {
            echo json_encode(['success' => false, 'message' => 'Please enter a promo code.']);
            exit;
        }

        $summary = getCartSummaryState();
        $subtotal = $summary['subtotal'];

        // 1. Check database tbl_coupon
        $found_coupon = null;
        try {
            $stmt = $pdo->prepare("SELECT * FROM tbl_coupon WHERE BINARY coupon_code = ? AND status = 'active' LIMIT 1");
            $stmt->execute([$code]);
            $found_coupon = $stmt->fetch(PDO::FETCH_ASSOC);
        } catch (Exception $e) {
            // DB coupon query error fallback
        }

        if ($found_coupon) {
            // Check dates & minimum order
            $today = date('Y-m-d');
            if (!empty($found_coupon['start_date']) && $today < $found_coupon['start_date']) {
                echo json_encode(['success' => false, 'message' => 'This promo code is not active yet.']);
                exit;
            }
            if (!empty($found_coupon['end_date']) && $today > $found_coupon['end_date']) {
                echo json_encode(['success' => false, 'message' => 'This promo code has expired.']);
                exit;
            }
            if (!empty($found_coupon['minimum_order']) && $subtotal < (float)$found_coupon['minimum_order']) {
                echo json_encode(['success' => false, 'message' => 'Minimum order amount for this coupon is ৳ ' . number_format($found_coupon['minimum_order'])]);
                exit;
            }

            $_SESSION['cart_coupon'] = [
                'code' => $found_coupon['coupon_code'],
                'type' => $found_coupon['discount_type'],
                'value' => (float)$found_coupon['discount_value']
            ];
            $_SESSION['coupon'] = [
                'code' => $found_coupon['coupon_code'],
                'type' => $found_coupon['discount_type'],
                'discount' => (float)$found_coupon['discount_value']
            ];

            $new_summary = getCartSummaryState();
            echo json_encode([
                'success' => true,
                'message' => 'Promo code "' . $code . '" applied successfully!',
                'summary' => $new_summary
            ]);
            exit;
        }

        // 2. Built-in promotional coupons for ShopNext
        $builtin = [
            'SAVE10' => ['type' => 'percentage', 'value' => 10, 'label' => '10% OFF'],
            'SAVE15' => ['type' => 'percentage', 'value' => 15, 'label' => '15% OFF'],
            'SAVE20' => ['type' => 'percentage', 'value' => 20, 'label' => '20% OFF'],
            'SHOPNEXT' => ['type' => 'fixed', 'value' => 500, 'label' => '৳ 500 OFF'],
            'FREESHIP' => ['type' => 'fixed', 'value' => 0, 'label' => 'Free Express Shipping'],
            'DISCOUNT18' => ['type' => 'fixed', 'value' => 18000, 'label' => '৳ 18,000 Special Saving']
        ];

        if (isset($builtin[$code])) {
            $_SESSION['cart_coupon'] = [
                'code' => $code,
                'type' => $builtin[$code]['type'],
                'value' => $builtin[$code]['value'],
                'label' => $builtin[$code]['label']
            ];
            $_SESSION['coupon'] = [
                'code' => $code,
                'type' => $builtin[$code]['type'],
                'discount' => $builtin[$code]['value']
            ];

            $new_summary = getCartSummaryState();
            echo json_encode([
                'success' => true,
                'message' => 'Coupon "' . $code . '" (' . $builtin[$code]['label'] . ') applied!',
                'summary' => $new_summary
            ]);
            exit;
        }

        echo json_encode(['success' => false, 'message' => 'Invalid promo code. Try SAVE10 or SHOPNEXT']);
        exit;

    case 'remove_coupon':
        unset($_SESSION['cart_coupon']);
        $new_summary = getCartSummaryState();
        echo json_encode([
            'success' => true,
            'message' => 'Coupon removed.',
            'summary' => $new_summary
        ]);
        exit;

    case 'clear_cart':
        unset($_SESSION['cart_p_id']);
        unset($_SESSION['cart_size_id']);
        unset($_SESSION['cart_size_name']);
        unset($_SESSION['cart_color_id']);
        unset($_SESSION['cart_color_name']);
        unset($_SESSION['cart_p_qty']);
        unset($_SESSION['cart_p_current_price']);
        unset($_SESSION['cart_p_name']);
        unset($_SESSION['cart_p_featured_photo']);
        unset($_SESSION['cart_p_old_price']);
        unset($_SESSION['cart_p_subtitle']);
        unset($_SESSION['cart_p_badge']);
        unset($_SESSION['cart_coupon']);

        if (isset($_SESSION['customer']['cust_id'])) {
            try {
                $stmt = $pdo->prepare("DELETE FROM tbl_customer_carts WHERE customer_id = ?");
                $stmt->execute([$_SESSION['customer']['cust_id']]);
            } catch (Exception $e) {}
        }

        echo json_encode([
            'success' => true,
            'message' => 'Cart has been cleared.',
            'summary' => getCartSummaryState()
        ]);
        exit;

    default:
        echo json_encode(['success' => false, 'message' => 'Invalid action.']);
        exit;
}
