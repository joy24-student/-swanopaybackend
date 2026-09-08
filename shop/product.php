<?php
// PHP Logic at the top (unmodified from original)
require_once("admin/inc/config.php");
require_once("admin/inc/functions.php");
require_once("admin/inc/CSRF_Protect.php");
require_once("admin/inc/seo_helpers.php");

// Handle both old-style (?id=) and new SEO-friendly URLs
$p_id = null;

if (isset($_REQUEST['slug'])) {
    // New SEO-friendly URL format: /product/product-name-123
    $slug = $_REQUEST['slug'];
    $p_id = getProductIdBySlug($slug, $pdo);
    
    if (!$p_id) {
        // Product not found with this slug - return 404
        header("HTTP/1.1 404 Not Found");
        header('location: index.php');
        exit;
    }
} elseif (isset($_REQUEST['id'])) {
    // Old-style URL format: ?id=123
    // For backward compatibility, but ideally should redirect to new format
    $p_id = (int)$_REQUEST['id'];
    
    // Verify product exists
    $statement = $pdo->prepare("SELECT p_id, p_name FROM tbl_product WHERE p_id=? AND p_is_active=1");
    $statement->execute(array($p_id));
    
    if ($statement->rowCount() == 0) {
        header('location: index.php');
        exit;
    }
    
    // Get product name to redirect to new URL format
    $row = $statement->fetch(PDO::FETCH_ASSOC);
    $newUrl = getProductURL($p_id, $row['p_name'], BASE_URL);
    redirect301($newUrl);
    exit;
} else {
    header('location: index.php');
    exit;
}

// Fetch product details
$statement = $pdo->prepare("SELECT * FROM tbl_product WHERE p_id=? AND p_is_active=1");
$statement->execute(array($p_id));
$total = $statement->rowCount();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);

if ($total == 0) {
    header("HTTP/1.1 404 Not Found");
    header('location: index.php');
    exit;
}
$error_message='';
$success_message="";
$csrf = new CSRF_Protect();
$error_message1 = '';
$success_message1 = '';


foreach($result as $row) {
    $p_id = $row['p_id'];
    $p_name = $row['p_name'];
    $p_old_price = $row['p_old_price'];
    $p_current_price = $row['p_current_price'];
    $p_qty = $row['p_qty'];
    $p_featured_photo = $row['p_featured_photo'];
    $p_description = $row['p_description'];
    $p_short_description = $row['p_short_description'];
    $p_feature = $row['p_feature'];
    $p_condition = $row['p_condition'];
    $p_return_policy = $row['p_return_policy'];
    $p_total_view = $row['p_total_view'];
    $p_is_featured = $row['p_is_featured'];
    $p_is_active = $row['p_is_active'];
    $ecat_id = $row['ecat_id'];
    $p_video_link = $row['p_video_link'];
    $p_is_top_sale = $row['p_is_top_sale'] ?? 0;
    $p_is_official = $row['p_is_official'] ?? 0;
}


// Update product view count
$statement = $pdo->prepare("UPDATE tbl_product SET p_total_view=? WHERE p_id=?");
$statement->execute(array($p_total_view+1, $p_id));


$i=1;
$statement = $pdo->prepare("SELECT * FROM tbl_language");
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);
foreach ($result as $row) {
	define('LANG_VALUE_'.$i,$row['lang_value']);
	$i++;
}
// Fetch settings
$statement_settings = $pdo->prepare("SELECT review_feature_on_off, estimated_delivery_time_local, estimated_delivery_time_international, contact_phone, gemini_api_key, flash_sale_end_time, free_delivery_threshold_qty, product_voucher_code, product_voucher_discount, hide_banner_desktop, hide_banner_mobile, hide_free_delivery_desktop, hide_free_delivery_mobile FROM tbl_settings WHERE id=1");
$statement_settings->execute();
$settings_data = $statement_settings->fetch(PDO::FETCH_ASSOC);
$review_feature_on_off = $settings_data['review_feature_on_off'] ?? 1;
$estimated_delivery_time_local = $settings_data['estimated_delivery_time_local'] ?? '3-5 business days';
$estimated_delivery_time_international = $settings_data['estimated_delivery_time_international'] ?? '10-20 business days';
$gemini_api_key = $settings_data['gemini_api_key'] ?? '';
$flash_sale_end_time_str = $settings_data['flash_sale_end_time'] ?? null;
$free_delivery_threshold_qty = $settings_data['free_delivery_threshold_qty'] ?? 5;
$product_voucher_code = $settings_data['product_voucher_code'] ?? 'SAVE10';
$product_voucher_discount = $settings_data['product_voucher_discount'] ?? 10.00;
$multi_vendor_on_off = $settings_data['multi_vendor_on_off'] ?? 0;

// Convert flash sale end time
$flash_sale_end_timestamp = null;
if ($flash_sale_end_time_str) {
    try {
        $dateTime = new DateTime($flash_sale_end_time_str);
        $flash_sale_end_timestamp = $dateTime->getTimestamp() * 1000;
    } catch (Exception $e) {
        $flash_sale_end_timestamp = null;
    }
}
// Existing product fetch...
// ... (Add this below)

// 1. Fetch Merchant Data
$statement = $pdo->prepare("SELECT t1.*, t2.cust_name as vendor_name, t2.cust_id as vendor_id 
                             FROM tbl_product t1 
                             JOIN tbl_customer t2 ON t1.vendor_id = t2.cust_id 
                             WHERE t1.p_id=?");
$statement->execute(array($p_id));
$v_row = $statement->fetch(PDO::FETCH_ASSOC);
$vendor_name = $v_row['vendor_name'] ?? 'Official Store';
$vendor_id = $v_row['vendor_id'] ?? 0;
// 1. Store Identity
$vendor_name = $settings_data['meta_title_home'] ?? 'Official Store';
$vendor_id = 0;

// 2. Fetch Feature Toggles from tbl_settings
$stat_set = $pdo->prepare("SELECT multi_vendor_on_off, coin_payment_on_off, desktop_advanced_layout_on_off FROM tbl_settings WHERE id=1");
$stat_set->execute();
$set_row = $stat_set->fetch(PDO::FETCH_ASSOC);

$is_multi_vendor = $set_row['multi_vendor_on_off'];
$is_coin_enabled = $set_row['coin_payment_on_off'];
$is_advanced_layout = $set_row['desktop_advanced_layout_on_off'];

// Get all photos of the product
$statement = $pdo->prepare("SELECT * FROM tbl_product_photo WHERE p_id=?");
$statement->execute(array($p_id));
$additional_photos = $statement->fetchAll(PDO::FETCH_ASSOC);

// Initialize result_photo array with the featured photo
$result_photo = [['photo' => $p_featured_photo]];

// Add additional photos to the array, ensuring correct path
foreach ($additional_photos as $photo) {
    if (strpos($photo['photo'], 'product_photos/') === 0) {
        $result_photo[] = ['photo' => $photo['photo']];
    } else {
        $result_photo[] = ['photo' => 'product_photos/' . $photo['photo']];
    }
}

$statement = $pdo->prepare("SELECT
        s.size_id,
        s.size_name
    FROM tbl_product_size ps
    JOIN tbl_size s ON ps.size_id = s.size_id
    WHERE ps.p_id=?");
$statement->execute(array($p_id));
$product_sizes = $statement->fetchAll(PDO::FETCH_ASSOC);

$statement = $pdo->prepare("SELECT
        c.color_id,
        c.color_name
    FROM tbl_product_color pc
    JOIN tbl_color c ON pc.color_id = c.color_id
    WHERE pc.p_id=?");
$statement->execute(array($p_id));
$product_colors = $statement->fetchAll(PDO::FETCH_ASSOC);

$default_size_name = !empty($product_sizes) ? $product_sizes[0]['size_name'] : '';
$default_color_name = !empty($product_colors) ? $product_colors[0]['color_name'] : '';

$avg_rating = 0;
$total_reviews_count = 0;
if ($review_feature_on_off == 1) {
    $statement_rating = $pdo->prepare("SELECT AVG(rating) AS avg_rating, COUNT(review_id) AS total_count FROM tbl_review WHERE product_id=? AND status='Approved'");
    $statement_rating->execute(array($p_id));
    $rating_result = $statement_rating->fetch(PDO::FETCH_ASSOC);
    if ($rating_result['total_count'] > 0) {
        $avg_rating = round($rating_result['avg_rating']);
        $total_reviews_count = $rating_result['total_count'];
    }
}

if(isset($_POST['form_add_to_cart']) || isset($_POST['form_buy_now'])) {
    $valid = 1;
    $p_qty_added = (int)($_POST['p_qty'] ?? 1);
    $size_id_added = $_POST['size_id'] ?? 0;
    $size_name_added = $_POST['size_name'] ?? '';
    $color_id_added = $_POST['color_id'] ?? 0;
    $color_name_added = $_POST['color_name'] ?? '';

    if ($p_qty_added <= 0 || $p_qty_added > $p_qty) {
        $valid = 0;
        $error_message .= 'Invalid quantity or quantity exceeds stock.<br>';
    }

    if ($valid == 1) {
        $item_found = false;
        $existing_index = -1;

        if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id'])) {
            foreach ($_SESSION['cart_p_id'] as $key => $value) {
                if ($value == $p_id &&
                    ($_SESSION['cart_size_id'][$key] == $size_id_added) &&
                    ($_SESSION['cart_color_id'][$key] == $color_id_added)) {

                    $existing_index = $key;
                    $item_found = true;
                    break;
                }
            }
        }

        if ($item_found) {
            if (($_SESSION['cart_p_qty'][$existing_index] + $p_qty_added) > $p_qty) {
                $error_message .= 'Adding this quantity would exceed available stock.<br>';
            } else {
                $_SESSION['cart_p_qty'][$existing_index] += $p_qty_added;
                
                // Update database if customer is logged in
                if (isset($_SESSION['customer']['cust_id'])) {
                    require_once('admin/inc/functions.php');
                    updateCartItemQuantity($pdo, $_SESSION['customer']['cust_id'], $p_id, $size_id_added, $color_id_added, $_SESSION['cart_p_qty'][$existing_index]);
                }
                
                $success_message .= 'Quantity updated in cart successfully!';
            }
        } else {
            $next_index = 1;
            if (isset($_SESSION['cart_p_id']) && is_array($_SESSION['cart_p_id']) && !empty($_SESSION['cart_p_id'])) {
                $next_index = max(array_keys($_SESSION['cart_p_id'])) + 1;
            }

            $_SESSION['cart_p_id'][$next_index] = $p_id;
            $_SESSION['cart_size_id'][$next_index] = $size_id_added;
            $_SESSION['cart_size_name'][$next_index] = $size_name_added;
            $_SESSION['cart_color_id'][$next_index] = $color_id_added;
            $_SESSION['cart_color_name'][$next_index] = $color_name_added;
            $_SESSION['cart_p_qty'][$next_index] = $p_qty_added;
            $_SESSION['cart_p_current_price'][$next_index] = $p_current_price;
            $_SESSION['cart_p_name'][$next_index] = $p_name;
            $_SESSION['cart_p_featured_photo'][$next_index] = $p_featured_photo;

            // Add to database if customer is logged in
            if (isset($_SESSION['customer']['cust_id'])) {
                require_once('admin/inc/functions.php');
                addOrUpdateCartItem($pdo, $_SESSION['customer']['cust_id'], $p_id, $size_id_added, $size_name_added, $color_id_added, $color_name_added, $p_qty_added, $p_current_price, $p_name, $p_featured_photo);
            }

            $success_message .= 'Product added to cart successfully!';
        }

        if (isset($_POST['form_buy_now']) && empty($error_message)) {
            header('location: '.BASE_URL.'cart.php');
            exit;
        }
    }
}
// Initialize variables needed for the new logic
$is_product_in_wishlist = false;
$wishlist_id_for_product = null;

// Fix for "Undefined variable" error at line 2242 (or wherever it was referenced)
$wishlist_product_ids_current_user = [];

if (isset($_SESSION['customer'])) {
    $cust_id = $_SESSION['customer']['cust_id'];

    // 1. Check if the current product is in the user's wishlist
    // FIX: Selects 'wishlist_id' (correct column) instead of 'wishlist_data'
    $statement_wishlist_check = $pdo->prepare("SELECT wishlist_id FROM tbl_wishlist WHERE cust_id=? AND product_id=?");

    try {
        $statement_wishlist_check->execute(array($cust_id, $p_id));
        $wishlist_row = $statement_wishlist_check->fetch(PDO::FETCH_ASSOC);

        if ($wishlist_row) {
            $is_product_in_wishlist = true;
            $wishlist_id_for_product = $wishlist_row['wishlist_id'];
        }
    } catch (PDOException $e) {
        error_log("Wishlist check error: " . $e->getMessage());
        $error_message .= "A database error occurred while checking wishlist status.";
    }

    // 2. Fetch all wishlist product IDs for the current user (for related product cards)
    $statement_all_wishlist = $pdo->prepare("SELECT product_id FROM tbl_wishlist WHERE cust_id=?");
    $statement_all_wishlist->execute(array($cust_id));
    $wishlist_product_ids_current_user = $statement_all_wishlist->fetchAll(PDO::FETCH_COLUMN);
}
// --- END: Wishlist Status Check ---


$user_name_for_ai = isset($_SESSION['customer']['cust_name']) ? $_SESSION['customer']['cust_name'] : '';
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?php echo htmlspecialchars($p_name); ?> - Product Details</title>
    <!-- Include your CSS files here -->
    <!-- For example: -->
    <!-- <link rel="stylesheet" href="assets/css/bootstrap.min.css"> -->
    <!-- <link rel="stylesheet" href="assets/css/font-awesome.min.css"> -->
    <!-- <link rel="stylesheet" href="assets/css/style.css"> -->
    <!-- Add Font Awesome for icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
    <!-- Tailwind CSS (if used) -->
    <script src="https://cdn.tailwindcss.com"></script>
   <style>
/* ============================================
   MODERN COMPACT PRODUCT PAGE
   ============================================ */

/* ============================================
   MODERN COMPACT PRODUCT PAGE - FULL CSS
   ============================================ */

/* ========== CSS VARIABLES & RESET ========== */
:root {
    --primary: #e74c3c;
    --primary-dark: #c0392b;
    --secondary: #3498db;
    --success: #27ae60;
    --warning: #f39c12;
    --danger: #e74c3c;
    --dark: #2c3e50;
    --light: #f8f9fa;
    --gray: #95a5a6;
    --border: #e0e0e0;
    --shadow: 0 4px 12px rgba(0,0,0,0.08);
    --shadow-lg: 0 8px 24px rgba(0,0,0,0.12);
    --radius-sm: 8px;
    --radius-md: 12px;
    --radius-lg: 16px;
    --transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    
    /* Gradient Variables */
    --gradient-primary: linear-gradient(135deg, #e74c3c 0%, #ff6b6b 100%);
    --gradient-secondary: linear-gradient(135deg, #3498db 0%, #2ecc71 100%);
    --gradient-dark: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
    --gradient-light: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
    --gradient-premium: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    --gradient-orange: linear-gradient(135deg, #ff6b6b 0%, #ffa726 100%);
    --gradient-green: linear-gradient(135deg, #2ecc71 0%, #27ae60 100%);
    --gradient-blue: linear-gradient(135deg, #3498db 0%, #2980b9 100%);
    --gradient-purple: linear-gradient(135deg, #9b59b6 0%, #8e44ad 100%);
    --gradient-gold: linear-gradient(135deg, #ffd700 0%, #ffa500 100%);
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

html, body {
    overflow-x: hidden;
    scroll-behavior: smooth;
    font-size: 14px;
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
}

body {
    background: linear-gradient(135deg, #f5f7fa 0%, #e4e8f0 100%);
    min-height: 100vh;
    color: #333;
    line-height: 1.6;
}

/* ========== CUSTOM SCROLLBAR ========== */
::-webkit-scrollbar {
    width: 6px;
    height: 6px;
}

::-webkit-scrollbar-track {
    background: rgba(0,0,0,0.05);
    border-radius: 3px;
}

::-webkit-scrollbar-thumb {
    background: var(--primary);
    border-radius: 3px;
    transition: var(--transition);
}

::-webkit-scrollbar-thumb:hover {
    background: var(--primary-dark);
}

/* ========== ANIMATIONS ========== */
@keyframes fadeIn {
    from { opacity: 0; transform: translateY(10px); }
    to { opacity: 1; transform: translateY(0); }
}

@keyframes slideInLeft {
    from { opacity: 0; transform: translateX(-20px); }
    to { opacity: 1; transform: translateX(0); }
}

@keyframes slideInRight {
    from { opacity: 0; transform: translateX(20px); }
    to { opacity: 1; transform: translateX(0); }
}

@keyframes scaleIn {
    from { opacity: 0; transform: scale(0.9); }
    to { opacity: 1; transform: scale(1); }
}

@keyframes pulse {
    0% { transform: scale(1); }
    50% { transform: scale(1.05); }
    100% { transform: scale(1); }
}

@keyframes shimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-5px); }
}

@keyframes bounceIn {
    0% { opacity: 0; transform: scale(0.3); }
    50% { opacity: 1; transform: scale(1.05); }
    70% { transform: scale(0.9); }
    100% { transform: scale(1); }
}

/* --- Fly-to-basket styles --- */
#fly-basket {
    position: fixed;
    right: 22px;
    top: 110px;
    width: 64px;
    height: 64px;
    border-radius: 10px;
    background: linear-gradient(180deg,#fff 0%, #f1f1f1 100%);
    box-shadow: 0 6px 18px rgba(0,0,0,0.12);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 12000;
    cursor: default;
}
#fly-basket .basket-icon {
    width: 36px;
    height: 36px;
    fill: #333;
    transition: transform 0.25s ease;
}
#fly-basket .basket-lid {
    position: absolute;
    top: 6px;
    right: 8px;
    width: 36px;
    height: 8px;
    background: #444;
    border-radius: 3px;
    transform-origin: right center;
}
@media (max-width: 991px) {
    /* Mobile: smaller basket positioned above bottom nav */
    #fly-basket {
        display: flex;
        right: 12px;
        top: auto;
        bottom: 76px;
        width: 48px;
        height: 48px;
        border-radius: 8px;
    }
    #fly-basket .basket-icon { width: 28px; height: 28px; }
    #fly-basket .basket-lid { top: 4px; right: 6px; width: 28px; height: 6px; }
}

/* show/hide animation helper */
#fly-basket { opacity: 0; transform: translateY(-6px) scale(0.96); transition: opacity 260ms ease, transform 260ms cubic-bezier(.2,.9,.3,1); }
#fly-basket.visible { opacity: 1; transform: translateY(0) scale(1); }

@keyframes gradientFlow {
    0% { background-position: 0% 50%; }
    50% { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

@keyframes slideUp {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}

@keyframes ripple {
    0% { transform: scale(0); opacity: 1; }
    100% { transform: scale(4); opacity: 0; }
}

/* ========== LAYOUT CONTAINERS ========== */
.product-page-container {
    display: block;
    width: 100%;
    padding: 0;
}

@media (min-width: 768px) {
    .product-page-container {
        display: grid;
        grid-template-columns: 55% 45%;
        gap: 20px;
        max-width: 1200px;
        margin: 0 auto;
        padding: 15px;
        min-height: 100vh;
        align-items: start;
    }
    
    .left-panel {
        position: sticky;
        top: 15px;
        height: calc(100vh - 30px);
        overflow-y: auto;
        padding-right: 15px;
        animation: slideInLeft 0.6s ease-out;
    }
    
    .right-panel {
        height: calc(100vh - 30px);
        overflow-y: auto;
        padding: 0 15px;
        animation: slideInRight 0.6s ease-out;
    }
}

/* ========== MOBILE HEADER ========== */
.mobile-header {
    position: sticky;
    top: 0;
    background: rgba(255,255,255,0.95);
    backdrop-filter: blur(10px);
    padding: 12px 16px;
    border-bottom: 1px solid var(--border);
    z-index: 1000;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 2px 10px rgba(0,0,0,0.08);
}

.mobile-header .back-btn {
    background: none;
    border: none;
    font-size: 20px;
    color: var(--dark);
    padding: 8px;
    cursor: pointer;
    transition: var(--transition);
    border-radius: 50%;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.mobile-header .back-btn:hover {
    background: rgba(231, 76, 60, 0.1);
    color: var(--primary);
    transform: translateX(-2px);
}

.mobile-actions {
    display: flex;
    gap: 12px;
    align-items: center;
}

.mobile-action-btn {
    background: none;
    border: none;
    font-size: 18px;
    color: var(--dark);
    padding: 8px;
    cursor: pointer;
    position: relative;
    transition: var(--transition);
    border-radius: 50%;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.mobile-action-btn:hover {
    background: rgba(231, 76, 60, 0.1);
    color: var(--primary);
    transform: translateY(-2px);
}

.wishlist-badge {
    position: absolute;
    top: 0;
    right: 0;
    background: var(--primary);
    color: white;
    font-size: 10px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
}

/* ========== DESKTOP HEADER ========== */
.top-header.desktop-only {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 60px;
    background: rgba(255,255,255,0.95);
    backdrop-filter: blur(10px);
    box-shadow: 0 4px 30px rgba(0,0,0,0.1);
    z-index: 1001;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 25px;
    border-bottom: 1px solid rgba(0,0,0,0.05);
}

.top-header.desktop-only a {
    color: var(--dark);
    font-size: 1.3em;
    text-decoration: none;
    font-weight: 700;
    transition: var(--transition);
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 15px;
    border-radius: var(--radius-sm);
}

.top-header.desktop-only a:hover {
    color: var(--primary);
    background: rgba(231, 76, 60, 0.1);
    transform: translateY(-2px);
}

/* ========== PAGE BANNER ========== */
.page-banner {
    background-size: cover;
    background-position: center center;
    background-repeat: no-repeat;
    height: 200px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    border-radius: 0 0 20px 20px;
    overflow: hidden;
    margin-bottom: 20px;
}

.page-banner .overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, rgba(0,0,0,0.5) 0%, rgba(0,0,0,0.2) 100%);
    z-index: 1;
}

.page-banner .inner {
    position: relative;
    z-index: 2;
    color: white;
    text-align: center;
    padding: 20px;
}

.page-banner h1 {
    font-size: 2.5em;
    font-weight: 800;
    margin: 0;
    text-shadow: 2px 4px 8px rgba(0,0,0,0.3);
    letter-spacing: -0.5px;
}

/* ========== PRODUCT GALLERY CARD ========== */
.product-gallery-card {
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    padding: 16px;
    box-shadow: var(--shadow);
    position: relative;
    overflow: hidden;
    transition: var(--transition);
    animation: scaleIn 0.5s ease-out;
}

.product-gallery-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: var(--gradient-primary);
    z-index: 1;
}

.product-gallery-card:hover {
    box-shadow: var(--shadow-lg);
    transform: translateY(-2px);
}

.main-image-container {
    position: relative;
    width: 100%;
    height: 350px;
    border-radius: var(--radius-md);
    overflow: hidden;
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
    margin-bottom: 12px;
}

@media (min-width: 768px) {
    .main-image-container {
        height: 400px;
    }
}

.main-image-slide {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    opacity: 0;
    transition: opacity 0.5s ease-in-out;
}

.main-image-slide.active {
    opacity: 1;
}

.slider-nav {
    position: absolute;
    top: 50%;
    left: 0;
    right: 0;
    display: flex;
    justify-content: space-between;
    padding: 0 12px;
    transform: translateY(-50%);
    z-index: 10;
    pointer-events: none;
}

.nav-btn {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: rgba(255,255,255,0.9);
    border: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: var(--transition);
    backdrop-filter: blur(10px);
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    pointer-events: auto;
    color: var(--dark);
}

.nav-btn:hover {
    background: white;
    transform: scale(1.1);
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    color: var(--primary);
}

.slider-dots {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-top: 12px;
}

.dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #ddd;
    cursor: pointer;
    transition: var(--transition);
}

.dot.active {
    background: var(--primary);
    transform: scale(1.3);
    box-shadow: 0 0 8px rgba(231, 76, 60, 0.5);
}

.thumbnail-strip {
    display: flex;
    gap: 8px;
    margin-top: 12px;
    overflow-x: auto;
    padding-bottom: 8px;
    -webkit-overflow-scrolling: touch;
}

.thumbnail {
    width: 60px;
    height: 60px;
    border-radius: var(--radius-sm);
    border: 2px solid transparent;
    object-fit: cover;
    cursor: pointer;
    transition: var(--transition);
    flex-shrink: 0;
    background: white;
}

.thumbnail:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}

.thumbnail.active {
    border-color: var(--primary);
    transform: scale(1.05);
    box-shadow: 0 4px 12px rgba(231, 76, 60, 0.2);
}

/* Gallery action buttons (small overlay below the gallery) */
.gallery-actions {
    position: absolute;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: 10px;
    z-index: 20;
}
.gallery-actions button {
    background: rgba(255,255,255,0.95);
    border: 1px solid rgba(0,0,0,0.06);
    width: 44px;
    height: 44px;
    border-radius: 10px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform .15s ease, background .15s ease;
    box-shadow: 0 6px 18px rgba(0,0,0,0.08);
}
.gallery-actions button:hover { transform: translateY(-4px); }
.gallery-actions button.active { background: var(--primary); color: #fff; border-color: rgba(0,0,0,0.06); }

/* ========== RELATED PRODUCTS SECTION ========== */
.related-products-section {
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    padding: 20px;
    box-shadow: var(--shadow);
    position: relative;
    overflow: hidden;
    margin-top: 20px;
    animation: fadeIn 0.5s ease-out 0.2s both;
}

.related-products-section::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: var(--gradient-secondary);
}

.section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    animation: fadeIn 0.5s ease-out;
}

.section-header h3 {
    font-size: 16px;
    font-weight: 600;
    color: var(--dark);
    display: flex;
    align-items: center;
    gap: 8px;
}

.section-header h3::before {
    content: '✨';
    font-size: 18px;
}

.view-all-link {
    font-size: 13px;
    color: var(--primary);
    text-decoration: none;
    font-weight: 500;
    transition: var(--transition);
    padding: 6px 12px;
    border-radius: 20px;
    background: rgba(231, 76, 60, 0.1);
}

.view-all-link:hover {
    background: rgba(231, 76, 60, 0.2);
    transform: translateX(3px);
}

.related-products-scroll {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    padding-bottom: 12px;
    -webkit-overflow-scrolling: touch;
}

.related-product-card {
    flex: 0 0 160px;
    text-decoration: none;
    color: inherit;
    background: white;
    border-radius: var(--radius-md);
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    transition: var(--transition);
    animation: fadeIn 0.5s ease-out;
    position: relative;
}

.related-product-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 20px rgba(0,0,0,0.15);
}

.related-product-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--gradient-primary);
    opacity: 0;
    transition: opacity 0.4s ease;
    z-index: 1;
    pointer-events: none;
}

.related-product-card:hover::before {
    opacity: 0.1;
}

.related-product-image {
    width: 160px;
    height: 160px;
    position: relative;
    overflow: hidden;
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
}

.related-product-image img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.5s ease;
}

.related-product-card:hover .related-product-image img {
    transform: scale(1.1);
}

.product-badge {
    position: absolute;
    top: 10px;
    right: 10px;
    background: var(--gradient-primary);
    color: white;
    padding: 4px 8px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 700;
    z-index: 2;
    box-shadow: 0 3px 10px rgba(231, 76, 60, 0.3);
    animation: pulse 2s infinite;
}

.wishlist-badge-sm {
    position: absolute;
    top: 10px;
    left: 10px;
    background: rgba(255, 255, 255, 0.95);
    border-radius: 50%;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: var(--transition);
    z-index: 2;
    color: var(--gray);
    border: 2px solid white;
    box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.wishlist-badge-sm:hover {
    background: var(--primary);
    color: white;
    transform: scale(1.1);
}

.wishlist-badge-sm.active {
    background: var(--primary);
    color: white;
}

.product-info-small {
    padding: 12px;
    position: relative;
    z-index: 2;
}

.product-name-small {
    font-size: 13px;
    font-weight: 500;
    color: var(--dark);
    line-height: 1.4;
    margin-bottom: 8px;
    height: 2.8em;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
}

.product-price-small {
    display: flex;
    align-items: baseline;
    gap: 6px;
}

.current-price-small {
    font-size: 15px;
    font-weight: 700;
    color: var(--primary);
}

.old-price-small {
    font-size: 12px;
    color: var(--gray);
    text-decoration: line-through;
}

/* ========== PRODUCT INFO CARD ========== */
.product-info-card {
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    padding: 24px;
    box-shadow: var(--shadow);
    position: relative;
    overflow: hidden;
    animation: scaleIn 0.5s ease-out;
}

.product-info-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: var(--gradient-premium);
}

.product-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    animation: fadeIn 0.5s ease-out 0.1s both;
}

.product-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--dark);
    line-height: 1.3;
    flex: 1;
    margin-right: 12px;
}

.wishlist-toggle {
    background: none;
    border: none;
    font-size: 24px;
    color: #ddd;
    cursor: pointer;
    padding: 8px;
    transition: var(--transition);
    position: relative;
    border-radius: 50%;
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.wishlist-toggle:hover {
    background: rgba(231, 76, 60, 0.1);
    transform: scale(1.1);
}

.wishlist-toggle.active {
    color: var(--primary);
    animation: float 3s ease-in-out infinite;
}

/* ========== TAGS CONTAINER ========== */
.tags-container {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
    animation: fadeIn 0.5s ease-out 0.2s both;
}

.tag {
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    transition: var(--transition);
    position: relative;
    overflow: hidden;
    border: none;
    color: white;
}

.tag::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(45deg, transparent 30%, rgba(255,255,255,0.3) 50%, transparent 70%);
    animation: shimmer 2s infinite;
}

.tag-hot {
    background: var(--gradient-orange);
}

.tag-official {
    background: var(--gradient-green);
}

.tag-sale {
    background: var(--gradient-primary);
}

.tag-new {
    background: var(--gradient-blue);
}

/* ========== PRODUCT META ========== */
.product-meta {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 20px;
    padding-bottom: 20px;
    border-bottom: 1px solid var(--border);
    animation: fadeIn 0.5s ease-out 0.3s both;
}

.rating-display {
    display: flex;
    align-items: center;
    gap: 8px;
}

.stars {
    color: var(--warning);
    font-size: 16px;
    display: flex;
    gap: 2px;
}

.rating-text {
    font-size: 13px;
    color: var(--dark);
    font-weight: 500;
}

.sold-count {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--gray);
}

.stock-status {
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
    background: linear-gradient(135deg, rgba(39, 174, 96, 0.1) 0%, rgba(39, 174, 96, 0.2) 100%);
    color: var(--success);
}

/* ========== PRICE SECTION ========== */
.price-section {
    margin-bottom: 20px;
    animation: fadeIn 0.5s ease-out 0.4s both;
}

.price-display {
    display: flex;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 8px;
}

.current-price {
    font-size: 32px;
    font-weight: 800;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    position: relative;
    animation: bounceIn 0.6s ease-out;
}

.old-price {
    font-size: 18px;
    color: var(--gray);
    text-decoration: line-through;
}

.discount-tag {
    padding: 8px 16px;
    border-radius: var(--radius-sm);
    background: var(--gradient-primary);
    color: white;
    font-size: 14px;
    font-weight: 700;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    animation: pulse 2s infinite;
}

/* ========== FLASH SALE BANNER ========== */
.flash-sale-banner {
    background: linear-gradient(135deg, #fff8e1 0%, #ffe0b2 100%);
    border-radius: var(--radius-md);
    padding: 16px;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    animation: fadeIn 0.5s ease-out 0.5s both;
    position: relative;
    overflow: hidden;
    border: 2px solid #ff9800;
}

.flash-sale-banner::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(45deg, transparent 30%, rgba(255,255,255,0.3) 50%, transparent 70%);
    animation: shimmer 3s infinite;
}

.flash-icon {
    font-size: 24px;
    color: #e65100;
    animation: pulse 1.5s infinite;
}

.flash-text {
    flex: 1;
    font-size: 14px;
    font-weight: 600;
    color: #e65100;
}

.countdown-timer {
    font-size: 18px;
    font-weight: 800;
    color: #d32f2f;
    background: white;
    padding: 8px 16px;
    border-radius: var(--radius-sm);
    box-shadow: 0 2px 8px rgba(211, 47, 47, 0.2);
    min-width: 140px;
    text-align: center;
}

/* ========== PRODUCT OPTIONS ========== */
.options-container {
    margin-bottom: 20px;
    animation: fadeIn 0.5s ease-out 0.6s both;
}

.option-group {
    margin-bottom: 16px;
    background: rgba(255,255,255,0.5);
    padding: 16px;
    border-radius: var(--radius-md);
    border: 1px solid var(--border);
    transition: var(--transition);
    position: relative;
}

.option-group:hover {
    border-color: var(--primary);
    box-shadow: 0 4px 12px rgba(231, 76, 60, 0.1);
    transform: translateY(-2px);
}

.option-label {
    display: block;
    font-size: 14px;
    font-weight: 600;
    color: var(--dark);
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.option-select {
    width: 100%;
    padding: 12px 16px;
    border: 2px solid var(--border);
    border-radius: var(--radius-sm);
    font-size: 14px;
    background: white;
    appearance: none;
    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23333' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
    background-repeat: no-repeat;
    background-position: right 12px center;
    background-size: 16px;
    transition: var(--transition);
    cursor: pointer;
}

.option-select:focus {
    border-color: var(--primary);
    box-shadow: 0 0 0 3px rgba(231, 76, 60, 0.1);
    outline: none;
}

/* ========== QUANTITY SELECTOR ========== */
.quantity-selector {
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
    border-radius: var(--radius-md);
    padding: 20px;
    margin-bottom: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    animation: fadeIn 0.5s ease-out 0.7s both;
    border: 2px solid transparent;
    transition: var(--transition);
}

.quantity-selector:hover {
    border-color: var(--primary);
    box-shadow: 0 4px 12px rgba(231, 76, 60, 0.1);
}

.quantity-label {
    font-size: 14px;
    font-weight: 600;
    color: var(--dark);
    display: flex;
    align-items: center;
    gap: 8px;
}

.quantity-controls {
    display: flex;
    align-items: center;
    gap: 16px;
    background: white;
    padding: 8px;
    border-radius: var(--radius-sm);
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.qty-btn {
    width: 36px;
    height: 36px;
    border-radius: var(--radius-sm);
    border: 2px solid var(--border);
    background: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    font-size: 18px;
    font-weight: 600;
    color: var(--dark);
    transition: var(--transition);
    position: relative;
    overflow: hidden;
}

.qty-btn::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    background: rgba(231, 76, 60, 0.2);
    border-radius: 50%;
    transform: translate(-50%, -50%);
    transition: width 0.6s, height 0.6s;
}

.qty-btn:hover::after {
    width: 200px;
    height: 200px;
}

.qty-btn:hover {
    border-color: var(--primary);
    color: var(--primary);
    transform: scale(1.1);
}

.qty-input {
    width: 50px;
    text-align: center;
    border: none;
    font-size: 18px;
    font-weight: 700;
    color: var(--dark);
    background: none;
    -moz-appearance: textfield;
}

.qty-input::-webkit-outer-spin-button,
.qty-input::-webkit-inner-spin-button {
    -webkit-appearance: none;
    margin: 0;
}

/* ========== FREE DELIVERY INFO ========== */
.free-delivery-info {
    background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
    border-radius: var(--radius-md);
    padding: 20px;
    margin-bottom: 20px;
    border: 2px solid #ff9800;
    transition: var(--transition);
    animation: fadeIn 0.5s ease-out 0.8s both;
    position: relative;
    overflow: hidden;
}

.free-delivery-info::before {
    content: '🚚';
    position: absolute;
    top: -20px;
    right: -20px;
    font-size: 80px;
    opacity: 0.1;
    transform: rotate(15deg);
}

.delivery-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    color: #e65100;
    font-weight: 600;
    font-size: 15px;
}

.delivery-details {
    font-size: 13px;
    color: #e65100;
    line-height: 1.5;
}

.qty-needed {
    font-weight: 700;
    color: #d32f2f;
}

/* ========== ACTION BUTTONS ========== */
.action-buttons {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    margin-bottom: 20px;
    animation: fadeIn 0.5s ease-out 0.9s both;
}

@media (max-width: 480px) {
    .action-buttons {
        grid-template-columns: 1fr;
    }
}

.action-btn {
    padding: 16px 24px;
    border-radius: var(--radius-md);
    font-size: 15px;
    font-weight: 700;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    transition: var(--transition);
    position: relative;
    overflow: hidden;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.action-btn::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.3);
    transform: translate(-50%, -50%);
    transition: width 0.6s, height 0.6s;
}

.action-btn:hover::after {
    width: 300px;
    height: 300px;
}

.btn-cart {
    background: var(--gradient-primary);
    color: white;
    box-shadow: 0 4px 15px rgba(231, 76, 60, 0.3);
}

.btn-cart:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(231, 76, 60, 0.4);
}

.btn-buy {
    background: var(--gradient-secondary);
    color: white;
    box-shadow: 0 4px 15px rgba(39, 174, 96, 0.3);
}

.btn-buy:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(39, 174, 96, 0.4);
}

.btn-buy.flash {
    animation: pulse 1.5s infinite;
}

.btn-wishlist {
    grid-column: span 2;
    background: white;
    color: var(--primary);
    border: 2px solid var(--primary);
    box-shadow: 0 2px 8px rgba(231, 76, 60, 0.1);
}

.btn-wishlist:hover {
    background: var(--primary);
    color: white;
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(231, 76, 60, 0.3);
}

/* ========== VOUCHER SECTION ========== */
.voucher-section {
    background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
    border-radius: var(--radius-md);
    padding: 20px;
    margin-bottom: 20px;
    border: 2px solid #2196F3;
    animation: fadeIn 0.5s ease-out 1s both;
    position: relative;
    overflow: hidden;
}

.voucher-section::before {
    content: '🎁';
    position: absolute;
    top: -20px;
    left: -20px;
    font-size: 80px;
    opacity: 0.1;
    transform: rotate(-15deg);
}

.voucher-section .section-header {
    margin-bottom: 15px;
}

.voucher-section p {
    color: #1976d2;
    font-size: 14px;
    margin-bottom: 15px;
}

.collect-voucher-btn {
    background: var(--gradient-orange);
    color: white;
    padding: 12px 24px;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 600;
    border: none;
    cursor: pointer;
    transition: var(--transition);
    display: inline-flex;
    align-items: center;
    gap: 8px;
    box-shadow: 0 4px 12px rgba(255, 152, 0, 0.3);
}

.collect-voucher-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(255, 152, 0, 0.4);
}

.collect-voucher-btn:disabled {
    opacity: 0.7;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}

.voucher-display-grid {
    display: none;
    grid-template-columns: 1fr auto;
    gap: 10px;
    align-items: center;
    background: #fff;
    padding: 15px;
    border-radius: var(--radius-sm);
    border: 2px dashed #2196F3;
    margin-top: 15px;
    animation: slideUp 0.5s ease-out;
}

.voucher-code {
    font-family: 'Courier New', monospace;
    font-size: 18px;
    font-weight: 700;
    color: var(--primary);
    letter-spacing: 1px;
    padding: 8px 12px;
    background: rgba(231, 76, 60, 0.1);
    border-radius: var(--radius-sm);
}

.copy-coupon-btn {
    background: #17a2b8;
    color: white;
    padding: 10px 20px;
    border-radius: var(--radius-sm);
    font-size: 13px;
    font-weight: 600;
    border: none;
    cursor: pointer;
    transition: var(--transition);
    display: flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
}

.copy-coupon-btn:hover {
    background: #138496;
    transform: translateY(-2px);
}

/* ========== DELIVERY INFO CARD ========== */
.delivery-info-card {
    background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
    border-radius: var(--radius-md);
    padding: 20px;
    margin-bottom: 20px;
    border: 2px solid #4CAF50;
    animation: fadeIn 0.5s ease-out 1.1s both;
    position: relative;
    overflow: hidden;
}

.delivery-info-card::before {
    content: '🚚';
    position: absolute;
    top: -20px;
    right: -20px;
    font-size: 80px;
    opacity: 0.1;
    transform: rotate(15deg);
}

.delivery-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    color: #2e7d32;
    font-weight: 600;
}

.delivery-details {
    font-size: 13px;
    color: #2e7d32;
    line-height: 1.5;
}

.delivery-details p {
    margin-bottom: 8px;
}

.delivery-details strong {
    color: #1b5e20;
}

/* ========== SELLER CONTACT SECTION ========== */
.seller-contact-section {
    background: linear-gradient(135deg, #f3e5f5 0%, #e1bee7 100%);
    border-radius: var(--radius-md);
    padding: 20px;
    margin-bottom: 20px;
    border: 2px solid #8e44ad;
    animation: fadeIn 0.5s ease-out 1.2s both;
}

.seller-contact-section .section-header {
    margin-bottom: 20px;
}

.vendor-card {
    display: grid;
    gap: 20px;
}

.vendor-info {
    display: flex;
    align-items: center;
    gap: 15px;
}

.vendor-avatar {
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background: var(--gradient-purple);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 24px;
    box-shadow: 0 4px 12px rgba(142, 68, 173, 0.3);
}

.vendor-info h4 {
    font-size: 16px;
    font-weight: 600;
    color: var(--dark);
    margin-bottom: 4px;
}

.vendor-info p {
    font-size: 12px;
    color: var(--gray);
}

.direct-chat-grid {
    display: grid;
    gap: 15px;
}

.ask-ai-btn {
    background: var(--gradient-purple);
    color: white;
    padding: 14px 20px;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 600;
    border: none;
    cursor: pointer;
    transition: var(--transition);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    box-shadow: 0 4px 12px rgba(142, 68, 173, 0.3);
}

.ask-ai-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(142, 68, 173, 0.4);
}

.ai-response-area {
    display: none;
    padding: 15px;
    background: white;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border);
    font-size: 13px;
    line-height: 1.5;
    animation: fadeIn 0.5s ease-out;
}

/* ========== TABS NAVIGATION ========== */
.tabs-navigation {
    position: sticky;
    top: 0;
    background: rgba(255,255,255,0.95);
    backdrop-filter: blur(10px);
    z-index: 50;
    border-bottom: 2px solid var(--border);
    margin-bottom: 20px;
    animation: fadeIn 0.5s ease-out;
}

@media (min-width: 768px) {
    .tabs-navigation {
        top: 15px;
    }
}

.tabs-list {
    display: flex;
    list-style: none;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    background: var(--gradient-light);
    border-radius: var(--radius-md);
    padding: 4px;
    margin: 0;
}

.tab-item {
    flex: 1;
    min-width: 120px;
}

.tab-button {
    width: 100%;
    padding: 14px 20px;
    background: none;
    border: none;
    font-size: 14px;
    font-weight: 600;
    color: var(--gray);
    cursor: pointer;
    white-space: nowrap;
    border-radius: var(--radius-sm);
    transition: var(--transition);
    position: relative;
    overflow: hidden;
}

.tab-button::before {
    content: '';
    position: absolute;
    bottom: 0;
    left: 50%;
    width: 0;
    height: 3px;
    background: var(--gradient-primary);
    transform: translateX(-50%);
    transition: width 0.3s ease;
}

.tab-button.active {
    color: var(--primary);
    background: rgba(231, 76, 60, 0.1);
}

.tab-button.active::before {
    width: 100%;
}

.tab-button:hover:not(.active) {
    color: var(--dark);
    background: rgba(0,0,0,0.05);
}

/* ========== TAB CONTENT ========== */
.tab-content {
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    padding: 24px;
    box-shadow: var(--shadow);
    animation: fadeIn 0.5s ease-out;
    min-height: 400px;
}

.tab-pane {
    display: none;
    animation: fadeIn 0.5s ease-out;
}

.tab-pane.active {
    display: block;
}

/* Description Tab */
.description-content {
    font-size: 14px;
    line-height: 1.6;
    color: var(--dark);
}

.features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 12px;
    margin-top: 20px;
}

.feature-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: rgba(231, 76, 60, 0.05);
    border-radius: var(--radius-sm);
    transition: var(--transition);
}

.feature-item:hover {
    background: rgba(231, 76, 60, 0.1);
    transform: translateX(5px);
}

.feature-icon {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: var(--gradient-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 14px;
    flex-shrink: 0;
}

/* Features Tab */
.specs-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 12px;
}

.spec-item {
    display: flex;
    justify-content: space-between;
    padding: 12px 0;
    border-bottom: 1px solid var(--border);
}

.spec-label {
    font-weight: 500;
    color: var(--dark);
    flex: 0 0 150px;
}

.spec-value {
    color: var(--gray);
    text-align: right;
    flex: 1;
}

/* Reviews Tab */
.reviews-summary {
    display: flex;
    gap: 30px;
    margin-bottom: 30px;
    padding-bottom: 20px;
    border-bottom: 2px solid var(--border);
}

.average-rating {
    text-align: center;
    min-width: 120px;
}

.rating-large {
    font-size: 48px;
    font-weight: 800;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.rating-stars-lg {
    color: var(--warning);
    font-size: 20px;
    margin: 8px 0;
}

.review-count {
    font-size: 13px;
    color: var(--gray);
}

.rating-distribution {
    flex: 1;
    font-size: 12px;
}

.review-item {
    padding: 20px;
    background: white;
    border-radius: var(--radius-md);
    margin-bottom: 16px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    transition: var(--transition);
}

.review-item:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.review-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.reviewer-info {
    display: flex;
    align-items: center;
    gap: 15px;
}

.reviewer-avatar {
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background: var(--gradient-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-weight: 600;
    font-size: 20px;
    flex-shrink: 0;
}

.reviewer-name {
    font-weight: 600;
    color: var(--dark);
    font-size: 14px;
}

.review-date {
    font-size: 12px;
    color: var(--gray);
}

.review-text {
    font-size: 14px;
    color: var(--dark);
    line-height: 1.5;
}

/* Video Tab */
.video-container {
    position: relative;
    border-radius: var(--radius-md);
    overflow: hidden;
    box-shadow: 0 15px 40px rgba(0,0,0,0.15);
    padding-bottom: 56.25%; /* 16:9 aspect ratio */
    height: 0;
}

/* ========== MOBILE BOTTOM NAVIGATION ========== */
.mobile-bottom-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(255,255,255,0.98);
    backdrop-filter: blur(10px);
    border-top: 2px solid var(--border);
    display: flex;
    padding: 10px 12px;
    gap: 10px;
    z-index: 1000;
    box-shadow: 0 -4px 20px rgba(0,0,0,0.1);
}

@media (min-width: 768px) {
    .mobile-bottom-nav {
        display: none;
    }
}

.mobile-nav-btn {
    flex: 1;
    padding: 14px;
    border-radius: var(--radius-md);
    border: none;
    font-size: 14px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    cursor: pointer;
    transition: var(--transition);
    position: relative;
    overflow: hidden;
}

.mobile-nav-btn::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.3);
    transform: translate(-50%, -50%);
    transition: width 0.6s, height 0.6s;
}

.mobile-nav-btn:hover::after {
    width: 200px;
    height: 200px;
}

.mobile-nav-btn:active {
    transform: scale(0.95);
}

.mobile-nav-btn-cart {
    background: var(--gradient-primary);
    color: white;
    box-shadow: 0 4px 15px rgba(231, 76, 60, 0.3);
}

.mobile-nav-btn-buy {
    background: var(--gradient-secondary);
    color: white;
    box-shadow: 0 4px 15px rgba(39, 174, 96, 0.3);
}

.mobile-nav-btn-buy.flash {
    animation: pulse 1.5s infinite;
}

.mobile-nav-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
}

/* ========== AI CHAT MODAL ========== */
.modal-ai-chat {
    display: none;
    position: fixed;
    z-index: 2000;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0,0,0,0.7);
    animation: fadeIn 0.3s ease-out;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
}

.modal-content-ai-chat {
    background-color: #fff;
    padding: 30px;
    border-radius: 20px;
    width: 95%;
    max-width: 800px;
    height: 85vh;
    max-height: 85vh;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    position: relative;
    animation: slideUp 0.4s ease-out;
    display: flex;
    flex-direction: column;
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 25px;
    padding-bottom: 20px;
    border-bottom: 2px solid var(--border);
}

.modal-header h2 {
    margin: 0;
    color: var(--dark);
    font-size: 1.8em;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 10px;
}

.close-button-ai-chat {
    color: #95a5a6;
    font-size: 28px;
    font-weight: 300;
    cursor: pointer;
    transition: var(--transition);
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
}

.close-button-ai-chat:hover {
    color: var(--primary);
    background-color: #f8f9fa;
}

.chat-container {
    flex-grow: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border-radius: var(--radius-md);
    border: 1px solid var(--border);
    background: #fafafa;
}

.chat-messages {
    flex-grow: 1;
    overflow-y: auto;
    padding: 25px;
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.chat-message {
    max-width: 80%;
    padding: 18px 25px;
    border-radius: 20px;
    position: relative;
    animation: fadeIn 0.3s ease-out;
    line-height: 1.5;
    word-wrap: break-word;
}

.chat-message.user {
    background: var(--gradient-primary);
    color: white;
    align-self: flex-end;
    border-bottom-right-radius: 5px;
}

.chat-message.ai {
    background: #fff;
    color: #333;
    align-self: flex-start;
    border-bottom-left-radius: 5px;
    box-shadow: 0 4px 15px rgba(0,0,0,0.08);
}

.chat-input-area {
    display: flex;
    gap: 15px;
    padding: 20px;
    background: #fff;
    border-top: 1px solid var(--border);
}

.chat-input-area input {
    flex-grow: 1;
    padding: 16px 20px;
    border: 2px solid var(--border);
    border-radius: var(--radius-md);
    font-size: 1em;
    transition: var(--transition);
}

.chat-input-area input:focus {
    border-color: var(--primary);
    box-shadow: 0 0 0 3px rgba(231, 76, 60, 0.1);
    outline: none;
}

.chat-input-area button {
    padding: 16px 30px;
    background: var(--gradient-primary);
    color: white;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    font-weight: 600;
    transition: var(--transition);
    display: flex;
    align-items: center;
    gap: 10px;
}

.chat-input-area button:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(231, 76, 60, 0.3);
}

/* ========== CUSTOM ALERT MODAL ========== */
.custom-modal {
    display: none;
    position: fixed;
    z-index: 3000;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0,0,0,0.5);
    animation: fadeIn 0.3s ease-out;
    display: flex;
    align-items: center;
    justify-content: center;
}

.custom-modal-content {
    background-color: #fff;
    padding: 30px;
    border-radius: var(--radius-lg);
    width: 90%;
    max-width: 400px;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    position: relative;
    animation: slideUp 0.4s ease-out;
    text-align: center;
}

.close-modal {
    color: #95a5a6;
    font-size: 24px;
    font-weight: 300;
    cursor: pointer;
    transition: var(--transition);
    position: absolute;
    top: 15px;
    right: 20px;
}

.close-modal:hover {
    color: var(--primary);
}

#custom-alert-message {
    font-size: 16px;
    line-height: 1.5;
    color: var(--dark);
    margin: 20px 0;
}

/* ========== UTILITY CLASSES ========== */
.hidden {
    display: none !important;
}

.mobile-only {
    display: none !important;
}

.desktop-only {
    display: none !important;
}

@media (max-width: 767px) {
    .mobile-only {
        display: block !important;
    }
    .desktop-only {
        display: none !important;
    }
}

@media (min-width: 768px) {
    .mobile-only {
        display: none !important;
    }
    .desktop-only {
        display: block !important;
    }
}

.fade-in {
    animation: fadeIn 0.5s ease-out;
}

.slide-in-left {
    animation: slideInLeft 0.5s ease-out;
}

.slide-in-right {
    animation: slideInRight 0.5s ease-out;
}

.scale-in {
    animation: scaleIn 0.5s ease-out;
}

.pulse {
    animation: pulse 2s infinite;
}

.float {
    animation: float 3s ease-in-out infinite;
}

.gradient-text {
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

/* ========== LOADING STATES ========== */
.skeleton {
    background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
    background-size: 200% 100%;
    animation: shimmer 1.5s infinite;
    border-radius: 4px;
}

.skeleton-title {
    height: 24px;
    width: 70%;
    margin-bottom: 12px;
}

.skeleton-price {
    height: 36px;
    width: 40%;
    margin-bottom: 16px;
}

.skeleton-button {
    height: 48px;
    border-radius: var(--radius-md);
}

/* ========== PRINT STYLES ========== */
@media print {
    .mobile-header,
    .mobile-bottom-nav,
    .action-buttons,
    .tabs-navigation,
    .slider-nav,
    .wishlist-toggle,
    .collect-voucher-btn,
             .ask-ai-btn {
        display: none !important;
    }
    
    body {
        background: white;
        color: black;
    }
    
    .product-page-container {
        display: block;
        max-width: 100%;
    }
    
    .product-gallery-card,
    .product-info-card,
    .tab-content {
        box-shadow: none;
        border: 1px solid #ddd;
        margin-bottom: 20px;
    }
}

/* ========== RESPONSIVE ADJUSTMENTS ========== */
@media (max-width: 480px) {
    :root {
        font-size: 13px;
    }
    
    .page-banner {
        height: 150px;
    }
    
    .page-banner h1 {
        font-size: 1.8em;
    }
    
    .main-image-container {
        height: 280px;
    }
    
    .thumbnail {
        width: 50px;
        height: 50px;
    }
    
    .related-product-card {
        flex: 0 0 140px;
    }
    
    .related-product-image {
        width: 140px;
        height: 140px;
    }
    
    .current-price {
        font-size: 28px;
    }
    
    .action-btn {
        padding: 14px 20px;
        font-size: 14px;
    }
    
    .tab-content {
        padding: 16px;
    }
    
    .tabs-list {
        padding: 2px;
    }
    
    .tab-button {
        padding: 10px 12px;
        font-size: 13px;
        min-width: 100px;
    }
}

@media (min-width: 768px) and (max-width: 1024px) {
    .product-page-container {
        grid-template-columns: 1fr;
        max-width: 800px;
    }
    
    .left-panel,
    .right-panel {
        position: static;
        height: auto;
    }
}

@media (min-width: 1200px) {
    .product-page-container {
        gap: 30px;
        max-width: 1400px;
    }
    
    .left-panel {
        gap: 30px;
    }
    
    .right-panel {
        gap: 30px;
    }
    
    .related-product-card {
        flex: 0 0 180px;
    }
    
    .related-product-image {
        width: 180px;
        height: 180px;
    }
}

/* ========== DARK MODE SUPPORT ========== */
@media (prefers-color-scheme: dark) {
    :root {
        --light: #1a1a1a;
        --dark: #ffffff;
        --gray: #b0b0b0;
        --border: #333333;
        --shadow: 0 4px 12px rgba(0,0,0,0.3);
        --gradient-light: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
    }
    
    body {
        background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
        color: #ffffff;
    }
    
    .gradient-text {
        background: linear-gradient(135deg, #ff6b6b 0%, #ffa726 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
    }
    
    .product-gallery-card,
    .product-info-card,
    .related-products-section,
    .tab-content,
    .voucher-section,
    .delivery-info-card,
    .seller-contact-section {
        background: linear-gradient(135deg, #2d3748 0%, #1e293b 100%);
        color: white;
    }
    
    .option-select,
    .quantity-controls,
    .chat-input-area input {
        background: #2d3748;
        border-color: #4a5568;
        color: white;
    }
    
    .chat-message.ai {
        background: #2d3748;
        color: white;
    }
}
   </style> 

    
</head>
<body <?php if (!empty($user_name_for_ai)): ?>data-user-name="<?php echo htmlspecialchars($user_name_for_ai); ?>"<?php endif; ?>>
<center>
    <!-- Mobile Header -->
    <header class="mobile-header">
        <button class="back-btn" onclick="window.history.back()">
            <i class="fas fa-arrow-left"></i>
        </button>
        <div class="mobile-actions">
            <button class="mobile-action-btn">
                <i class="fas fa-search"></i>
            </button>
        </div>
    </header>

    <!-- Desktop Header -->
    <header class="top-header desktop-only">
        <a href="<?php echo BASE_URL; ?>index.php"><i class="fas fa-home"></i> Home</a>
        <a href="<?php echo BASE_URL; ?>cart.php"><i class="fas fa-shopping-cart"></i> Cart</a>
    </header>

    <!-- Floating Basket (receives flying product) -->
    <div id="fly-basket" aria-hidden="true">
        <!-- basket SVG -->
        <svg class="basket-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path d="M7 7l1.5-3h7L17 7h4v2H3V7h4z" />
            <path d="M5 9h14l-1.5 9.5a1 1 0 0 1-1 .8H7.5a1 1 0 0 1-1-.8L5 9z" />
        </svg>
        <div class="basket-lid"></div>
    </div>

    <!-- Page Banner -->
    <?php
    { 
        // Extend settings SELECT to include new visibility flags and assign variables
        $statement_settings = $pdo->prepare("SELECT review_feature_on_off, estimated_delivery_time_local, estimated_delivery_time_international, contact_phone, gemini_api_key, flash_sale_end_time, free_delivery_threshold_qty, product_voucher_code, product_voucher_discount, hide_banner_desktop, hide_banner_mobile, hide_free_delivery_desktop, hide_free_delivery_mobile FROM tbl_settings WHERE id=1");
        $statement_settings->execute();
        $settings_data = $statement_settings->fetch(PDO::FETCH_ASSOC);
        $review_feature_on_off = $settings_data['review_feature_on_off'] ?? 1;
        $estimated_delivery_time_local = $settings_data['estimated_delivery_time_local'] ?? '3-5 business days';
        $estimated_delivery_time_international = $settings_data['estimated_delivery_time_international'] ?? '10-20 business days';
        $gemini_api_key = $settings_data['gemini_api_key'] ?? '';
        $flash_sale_end_time_str = $settings_data['flash_sale_end_time'] ?? null;
        $free_delivery_threshold_qty = $settings_data['free_delivery_threshold_qty'] ?? 5;
        $product_voucher_code = $settings_data['product_voucher_code'] ?? 'SAVE10';
        $product_voucher_discount = $settings_data['product_voucher_discount'] ?? 10.00;

        // New visibility settings
        $hide_banner_desktop = $settings_data['hide_banner_desktop'] ?? 0;
        $hide_banner_mobile = $settings_data['hide_banner_mobile'] ?? 0;
        $hide_free_delivery_desktop = $settings_data['hide_free_delivery_desktop'] ?? 0;
        $hide_free_delivery_mobile = $settings_data['hide_free_delivery_mobile'] ?? 0;
    }

    { 
        // Render page banner conditionally: compute classes or skip if both hidden
        $banner_hidden_both = ($hide_banner_desktop && $hide_banner_mobile);
        $banner_classes = '';
        if ($hide_banner_desktop && !$hide_banner_mobile) $banner_classes = 'mobile-only';
        if ($hide_banner_mobile && !$hide_banner_desktop) $banner_classes = 'desktop-only';
    }
    ?>
    <?php if (!$banner_hidden_both): ?>
        <div class="page-banner <?php echo $banner_classes; ?>" style="background-color:#444;background-image: url(<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($p_featured_photo); ?>);">
            <div class="overlay"></div>
            <div class="inner">
                <h1><?php echo htmlspecialchars($p_name); ?></h1>
            </div>
        </div>
    <?php endif; ?>

    <div class="product-page-container">
        <!-- Left Column: Image Gallery & Related Products -->
        <div class="left-panel">
            <!-- Product Gallery Card -->
            <div class="product-gallery-card">
                <div class="main-image-container">
                    <?php foreach ($result_photo as $index => $photo): ?>
                        <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($photo['photo'] ?? ''); ?>"
                             alt="<?php echo htmlspecialchars($p_name); ?>"
                             class="main-image-slide <?php echo $index === 0 ? 'active' : ''; ?>"
                             data-index="<?php echo $index; ?>">
                    <?php endforeach; ?>
                    
                    <div class="slider-nav" <?php echo count($result_photo) <= 1 ? 'style="display:none;"' : ''; ?>>
                        <button class="nav-btn prev-slide"><i class="fas fa-chevron-left"></i></button>
                        <button class="nav-btn next-slide"><i class="fas fa-chevron-right"></i></button>
                    </div>
                </div>
                
                <div class="slider-dots" <?php echo count($result_photo) <= 1 ? 'style="display:none;"' : ''; ?>>
                    <?php foreach ($result_photo as $index => $photo): ?>
                        <span class="dot <?php echo $index === 0 ? 'active' : ''; ?>" data-index="<?php echo $index; ?>"></span>
                    <?php endforeach; ?>
                </div>
                
                <div class="thumbnail-strip">
                    <?php foreach ($result_photo as $index => $photo): ?>
                        <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($photo['photo'] ?? ''); ?>"
                             class="thumbnail <?php echo $index === 0 ? 'active' : ''; ?>"
                             data-index="<?php echo $index; ?>"
                             alt="Thumbnail">
                    <?php endforeach; ?>
                </div>

                <!-- Gallery action buttons: wishlist + compare -->
                <div class="gallery-actions">
                    <button type="button" class="gallery-wishlist-btn" data-product-id="<?php echo htmlspecialchars($p_id); ?>" title="Add to wishlist">
                        <i class="<?php echo $is_product_in_wishlist ? 'fas' : 'far'; ?> fa-heart"></i>
                    </button>
                    <button type="button" class="gallery-compare-btn" data-product-id="<?php echo htmlspecialchars($p_id); ?>" title="Add to compare">
                        <i class="fas fa-exchange-alt"></i>
                    </button>
                </div>
            </div>

            <!-- Related Products Section -->
            <?php
            $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE ecat_id=? AND p_id != ? AND p_is_active=1 ORDER BY p_id DESC LIMIT 9");
            $statement->execute(array($ecat_id, $p_id));
            $related_products = $statement->fetchAll(PDO::FETCH_ASSOC);
            if (!empty($related_products)):
            ?>
            <div class="related-products-section">
                <div class="section-header">
                    <h3><i class="fas fa-th-large"></i> Related Products</h3>
                    <a href="<?php echo BASE_URL; ?>category.php?id=<?php echo $ecat_id; ?>" class="view-all-link">
                        View All <i class="fas fa-arrow-right"></i>
                    </a>
                </div>
                
                <div class="related-products-scroll">
                    <?php
                    $statement_settings_related = $pdo->prepare("SELECT review_feature_on_off FROM tbl_settings WHERE id=1");
                    $statement_settings_related->execute();
                    $settings_data_related = $statement_settings_related->fetch(PDO::FETCH_ASSOC);
                    $review_feature_on_off_related = $settings_data_related['review_feature_on_off'] ?? 1;

                    foreach ($related_products as $rel_row):
                        $avg_rating_rel = 0;
                        $total_reviews_count_rel = 0;
                        if ($review_feature_on_off_related == 1) {
                            $statement_rating_rel = $pdo->prepare("SELECT AVG(rating) AS avg_rating, COUNT(*) AS total_count FROM tbl_review WHERE product_id=? AND status='Approved'");
                            $statement_rating_rel->execute(array($rel_row['p_id']));
                            $rating_result_rel = $statement_rating_rel->fetch(PDO::FETCH_ASSOC);
                            if ($rating_result_rel['total_count'] > 0) {
                                $avg_rating_rel = round($rating_result_rel['avg_rating']);
                                $total_reviews_count_rel = $rating_result_rel['total_count'];
                            }
                        }
                        $is_rel_in_wishlist = is_array($wishlist_product_ids_current_user) && in_array($rel_row['p_id'], $wishlist_product_ids_current_user);
                    ?>
                    <a href="<?php echo BASE_URL; ?>product.php?id=<?php echo htmlspecialchars($rel_row['p_id']); ?>" class="related-product-card">
                        <div class="related-product-image">
                            <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($rel_row['p_featured_photo']); ?>" 
                                 alt="<?php echo htmlspecialchars($rel_row['p_name']); ?>" 
                                 loading="lazy">
                            
                            <?php if($rel_row['p_old_price'] > 0 && $rel_row['p_old_price'] > $rel_row['p_current_price']): ?>
                                <div class="product-badge">
                                    <?php echo round((($rel_row['p_old_price'] - $rel_row['p_current_price']) / $rel_row['p_old_price']) * 100); ?>% OFF
                                </div>
                            <?php endif; ?>
                            
                            <?php if(isset($_SESSION['customer'])): ?>
                                <button type="button" class="wishlist-badge-sm <?php echo $is_rel_in_wishlist ? 'active' : ''; ?>" 
                                        data-product-id="<?php echo htmlspecialchars($rel_row['p_id']); ?>">
                                    <i class="<?php echo $is_rel_in_wishlist ? 'fas' : 'far'; ?> fa-heart"></i>
                                </button>
                            <?php endif; ?>
                        </div>
                        
                        <div class="product-info-small">
                            <h4 class="product-name-small"><?php echo htmlspecialchars($rel_row['p_name']); ?></h4>
                            <div class="product-price-small">
                                <span class="current-price-small"><?php echo LANG_VALUE_1; ?><?php echo number_format($rel_row['p_current_price'], 2); ?></span>
                                <?php if($rel_row['p_old_price'] > 0): ?>
                                    <span class="old-price-small"><?php echo LANG_VALUE_1; ?><?php echo number_format($rel_row['p_old_price'], 2); ?></span>
                                <?php endif; ?>
                            </div>
                        </div>
                    </a>
                    <?php endforeach; ?>
                </div>
            </div>
            <?php endif; ?>
        </div>

        <!-- Right Column: Product Info & Tabs -->
        <div class="right-panel">
            <!-- Product Info Card -->
            <div class="product-info-card">
                <div class="product-header">
                    <h1 class="product-title"><?php echo htmlspecialchars($p_name); ?></h1>
                    <button class="wishlist-toggle <?php echo $is_product_in_wishlist ? 'active' : ''; ?>" 
                            data-product-id="<?php echo htmlspecialchars($p_id); ?>">
                        <i class="<?php echo $is_product_in_wishlist ? 'fas' : 'far'; ?> fa-heart"></i>
                    </button>
                </div>

                <div class="tags-container">
                    <?php if ($p_is_top_sale): ?>
                        <span class="tag tag-hot">TOP SALE</span>
                    <?php endif; ?>
                    <?php if ($p_is_official): ?>
                        <span class="tag tag-official"><i class="fas fa-check-circle"></i> Official</span>
                    <?php endif; ?>
                    <?php if($p_old_price > 0): ?>
                        <span class="tag tag-sale">SAVE <?php echo round((($p_old_price - $p_current_price) / $p_old_price) * 100); ?>%</span>
                    <?php endif; ?>
                    <span class="tag tag-new">NEW</span>
                </div>

                <div class="product-meta">
                    <div class="rating-display">
                        <?php if ($review_feature_on_off == 1): ?>
                            <div class="stars">
                                <?php
                                if($avg_rating == 0) {
                                    echo '<span class="no-rating">No reviews yet</span>';
                                } else {
                                    for($i=1;$i<=5;$i++) {
                                        if($i <= $avg_rating) {
                                            echo '<i class="fas fa-star"></i>';
                                        } else {
                                            echo '<i class="far fa-star"></i>';
                                        }
                                    }
                                }
                                ?>
                            </div>
                            <span class="rating-text"><?php echo $avg_rating; ?>/5</span>
                        <?php endif; ?>
                    </div>
                    
                    <div class="sold-count">
                        <i class="fas fa-shopping-bag"></i>
                        <span><?php echo $p_qty; ?> in stock</span>
                    </div>
                    
                    <div class="stock-status">
                        <?php if($p_qty == 0): ?>
                            <span>Out of Stock</span>
                        <?php else: ?>
                            <span>In Stock</span>
                        <?php endif; ?>
                    </div>
                </div>

                <div class="price-section">
                    <div class="price-display">
                        <span class="current-price"><?php echo LANG_VALUE_1; ?><?php echo number_format($p_current_price, 2); ?></span>
                        <?php if($p_old_price > 0): ?>
                            <span class="old-price"><?php echo LANG_VALUE_1; ?><?php echo number_format($p_old_price, 2); ?></span>
                            <span class="discount-tag">
                                <i class="fas fa-tag"></i>
                                <?php echo round((($p_old_price - $p_current_price) / $p_old_price) * 100); ?>% OFF
                            </span>
                        <?php endif; ?>
                    </div>
                </div>

                <?php if ($flash_sale_end_timestamp): ?>
                <div class="flash-sale-banner">
                    <i class="fas fa-bolt flash-icon"></i>
                    <span class="flash-text">Flash Sale Ends In:</span>
                    <span id="flash-sale-countdown" class="countdown-timer" 
                          data-end-time="<?php echo $flash_sale_end_timestamp; ?>">
                        Loading...
                    </span>
                </div>
                <?php endif; ?>

                <div class="product-short-description mb-4">
                    <?php echo html_entity_decode($p_short_description); ?>
                </div>

                <form action="" method="post" class="add-to-cart-form">
                    <?php $csrf->echoInputField(); ?>
                    <input type="hidden" name="p_name" value="<?php echo htmlspecialchars($p_name); ?>">
                    <input type="hidden" name="p_current_price" value="<?php echo htmlspecialchars($p_current_price); ?>">
                    <input type="hidden" name="p_featured_photo" value="<?php echo htmlspecialchars($p_featured_photo); ?>">

                    <div class="options-container">
                        <?php if (!empty($product_sizes)): ?>
                        <div class="option-group">
                            <label class="option-label">
                                <i class="fas fa-ruler-combined"></i> Size
                            </label>
                            <select name="size_id" id="size_id" class="option-select">
                                <?php foreach ($product_sizes as $size): ?>
                                    <option value="<?php echo htmlspecialchars($size['size_id']); ?>" 
                                            data-size-name="<?php echo htmlspecialchars($size['size_name']); ?>">
                                        <?php echo htmlspecialchars($size['size_name']); ?>
                                    </option>
                                <?php endforeach; ?>
                            </select>
                            <input type="hidden" name="size_name" id="size_name_hidden" 
                                   value="<?php echo htmlspecialchars($default_size_name); ?>">
                        </div>
                        <?php endif; ?>

                        <?php if (!empty($product_colors)): ?>
                        <div class="option-group">
                            <label class="option-label">
                                <i class="fas fa-palette"></i> Color
                            </label>
                            <select name="color_id" id="color_id" class="option-select">
                                <?php foreach ($product_colors as $color): ?>
                                    <option value="<?php echo htmlspecialchars($color['color_id']); ?>" 
                                            data-color-name="<?php echo htmlspecialchars($color['color_name']); ?>">
                                        <?php echo htmlspecialchars($color['color_name']); ?>
                                    </option>
                                <?php endforeach; ?>
                            </select>
                            <input type="hidden" name="color_name" id="color_name_hidden" 
                                   value="<?php echo htmlspecialchars($default_color_name); ?>">
                        </div>
                        <?php endif; ?>
                    </div>

                    <div class="quantity-selector">
                        <span class="quantity-label">
                            <i class="fas fa-box"></i> Quantity
                        </span>
                        <div class="quantity-controls">
                            <button type="button" class="qty-btn minus">-</button>
                            <input type="number" name="p_qty" id="p_qty" class="qty-input"
                                   value="1" min="1" max="<?php echo $p_qty; ?>">
                            <button type="button" class="qty-btn plus">+</button>
                        </div>
                    </div>

                    <div class="free-delivery-info <?php echo $free_delivery_classes; ?>">
                        <div class="delivery-header">
                            <i class="fas fa-shipping-fast"></i> Free Delivery Available!
                        </div>
                        <p class="delivery-details">
                            Buy <span class="qty-needed"><?php echo htmlspecialchars($free_delivery_threshold_qty); ?></span> 
                            or more for <strong>FREE DELIVERY!</strong>
                        </p>
                    </div>

                    <div class="action-buttons" >
                        <?php if($p_qty > 0): ?>
                            <button type="submit" class="action-btn btn-cart" name="form_add_to_cart">
                                <i class="fas fa-cart-plus"></i> Add to Cart
                            </button>
                            <button type="submit" class="action-btn btn-buy <?php echo $flash_sale_end_timestamp ? 'flash' : ''; ?>" 
                                    name="form_buy_now">
                                <i class="fas fa-bolt"></i> Buy Now
                            </button>
                        <?php else: ?>
                            <button type="button" class="action-btn btn-cart" disabled>
                                <i class="fas fa-times-circle"></i> Out of Stock
                            </button>
                        <?php endif; ?>
                    </div>
                </form>

                <!-- Voucher Section -->
                <?php if($product_voucher_code): ?>
                <div class="voucher-section">
                    <div class="section-header">
                        <h3><i class="fas fa-gift"></i> Special Offer</h3>
                    </div>
                    <p>Use this exclusive voucher code for extra discount:</p>
                    <div class="voucher-display-grid">
                        <span class="voucher-code" id="couponCodeDisplay"><?php echo htmlspecialchars($product_voucher_code); ?></span>
                        <button class="btn btn-sm btn-info copy-coupon-btn" 
                                data-clipboard-text="<?php echo htmlspecialchars($product_voucher_code); ?>">
                            Copy Code
                        </button>
                    </div>
                    <p class="text-xs text-gray mt-2">
                        Discount: <strong><?php echo LANG_VALUE_1; ?><?php echo number_format($product_voucher_discount, 2); ?></strong>
                    </p>
                </div>
                <?php endif; ?>

                <!-- Delivery Info -->
                <div class="delivery-info-card">
                    <div class="delivery-header">
                        <i class="fas fa-truck"></i> Delivery Information
                    </div>
                    <div class="delivery-details">
                        <p><strong>Local:</strong> <?php echo htmlspecialchars($estimated_delivery_time_local); ?></p>
                        <p><strong>International:</strong> <?php echo htmlspecialchars($estimated_delivery_time_international); ?></p>
                        <p class="text-xs">Delivery times may vary based on location.</p>
                    </div>
                </div>

                <!-- Seller Contact -->
                <?php if($multi_vendor_on_off == 0): ?>
                <div class="seller-contact-section">
                    <div class="section-header">
                        <h3><i class="fas fa-store"></i> Seller Information</h3>
                    </div>
                    <div class="vendor-card">
                        <div class="vendor-info">
                            <div class="vendor-avatar">
                                <i class="fas fa-user-circle"></i>
                            </div>
                            <div>
                                <h4><?php echo htmlspecialchars($vendor_name); ?></h4>
                                <p class="text-sm text-gray">Verified Merchant</p>
                            </div>
                        </div>
                        <div class="direct-chat-grid">
                            <button class="ask-ai-btn"
                                    data-product-id="<?php echo htmlspecialchars($p_id); ?>"
                                    data-product-name="<?php echo htmlspecialchars($p_name); ?>"
                                    data-user-name="<?php echo htmlspecialchars($user_name_for_ai); ?>"
                                    <?php if (empty($gemini_api_key)): ?>style="display:none;"<?php endif; ?>>
                                <i class="fas fa-robot"></i> Ask AI Assistant
                            </button>
                            <div class="ai-response-area" style="display:none;"></div>
                        </div>
                    </div>
                </div>
                <?php endif; ?>
            </div>

            <!-- Tabs Navigation -->
            <div class="tabs-navigation">
                <ul class="tabs-list">
                    <li class="tab-item">
                        <button type="button" class="tab-button active" data-target="description-tab">Description</button>
                    </li>
                    <li class="tab-item">
                        <button type="button" class="tab-button" data-target="features-tab">Features</button>
                    </li>
                    <li class="tab-item">
                        <button type="button" class="tab-button" data-target="policy-tab">Return Policy</button>
                    </li>
                    <?php if ($review_feature_on_off == 1): ?>
                    <li class="tab-item">
                        <button type="button" class="tab-button" data-target="reviews-tab">
                            Reviews (<?php echo $total_reviews_count; ?>)
                        </button>
                    </li>
                    <?php endif; ?>
                    <?php if (!empty($p_video_link)): ?>
                    <li class="tab-item">
                        <button type="button" class="tab-button" data-target="video-tab">Video</button>
                    </li>
                    <?php endif; ?>
                </ul>
            </div>

            <!-- Tab Content -->
            <div class="tab-content">
                <!-- Description Tab -->
                <div id="description-tab" class="tab-pane active">
                    <div class="description-content">
                        <?php echo html_entity_decode($p_description); ?>
                    </div>
                    
                    <div class="features-grid">
                        <?php
                        // Parse features into list items
                        $features = explode("\n", $p_feature);
                        foreach ($features as $feature):
                            if(trim($feature) != ''):
                        ?>
                        <div class="feature-item">
                            <div class="feature-icon">
                                <i class="fas fa-check"></i>
                            </div>
                            <span><?php echo html_entity_decode(trim($feature)); ?></span>
                        </div>
                        <?php endif; endforeach; ?>
                    </div>
                </div>

                <!-- Features Tab -->
                <div id="features-tab" class="tab-pane">
                    <div class="specs-grid">
                        <?php
                        // More detailed features parsing
                        $detailed_features = explode("\n", $p_feature);
                        foreach ($detailed_features as $feature):
                            if(trim($feature) != ''):
                                $parts = explode(":", $feature, 2);
                                if(count($parts) == 2):
                        ?>
                        <div class="spec-item">
                            <span class="spec-label"><?php echo html_entity_decode(trim($parts[0])); ?>:</span>
                            <span class="spec-value"><?php echo html_entity_decode(trim($parts[1])); ?></span>
                        </div>
                        <?php else: ?>
                        <div class="spec-item">
                            <span class="spec-value"><?php echo html_entity_decode(trim($feature)); ?></span>
                        </div>
                        <?php endif; endif; endforeach; ?>
                    </div>
                </div>

                <!-- Return Policy Tab -->
                <div id="policy-tab" class="tab-pane">
                    <div class="description-content">
                        <?php echo html_entity_decode($p_return_policy); ?>
                    </div>
                </div>

                <!-- Reviews Tab -->
                <?php if ($review_feature_on_off == 1): ?>
                <div id="reviews-tab" class="tab-pane">
                    <div class="reviews-summary">
                        <div class="average-rating">
                            <div class="rating-large"><?php echo number_format($avg_rating, 1); ?></div>
                            <div class="rating-stars-lg">
                                <?php for($i=1;$i<=5;$i++): ?>
                                    <?php if($i <= $avg_rating): ?>
                                        <i class="fas fa-star"></i>
                                    <?php else: ?>
                                        <i class="far fa-star"></i>
                                    <?php endif; ?>
                                <?php endfor; ?>
                            </div>
                            <div class="review-count"><?php echo $total_reviews_count; ?> reviews</div>
                        </div>
                        
                        <div class="rating-distribution">
                            <!-- Rating distribution chart would go here -->
                            <p>Based on <?php echo $total_reviews_count; ?> verified reviews</p>
                        </div>
                    </div>

                    <div class="reviews-list">
                        <?php
                        $statement_reviews = $pdo->prepare("SELECT r.*, c.cust_name FROM tbl_review r JOIN tbl_customer c ON r.cust_id = c.cust_id WHERE r.product_id=? AND r.status='Approved' ORDER BY r.created_at DESC LIMIT 10");
                        $statement_reviews->execute(array($p_id));
                        $reviews = $statement_reviews->fetchAll(PDO::FETCH_ASSOC);

                        if (!empty($reviews)):
                            foreach ($reviews as $review):
                        ?>
                        <div class="review-item">
                            <div class="review-header">
                                <div class="reviewer-info">
                                    <div class="reviewer-avatar">
                                        <?php echo strtoupper(substr($review['cust_name'], 0, 1)); ?>
                                    </div>
                                    <div>
                                        <div class="reviewer-name"><?php echo htmlspecialchars($review['cust_name']); ?></div>
                                        <div class="stars">
                                            <?php for($i=1;$i<=5;$i++): ?>
                                                <?php if($i <= $review['rating']): ?>
                                                    <i class="fas fa-star"></i>
                                                <?php else: ?>
                                                    <i class="far fa-star"></i>
                                                <?php endif; ?>
                                            <?php endfor; ?>
                                        </div>
                                    </div>
                                </div>
                                <span class="review-date"><?php echo date('M d, Y', strtotime($review['created_at'])); ?></span>
                            </div>
                            
                            <h5 class="review-title"><?php echo htmlspecialchars($review['review_title']); ?></h5>
                            <p class="review-text"><?php echo nl2br(htmlspecialchars($review['comment'])); ?></p>
                        </div>
                        <?php endforeach; else: ?>
                            <div class="text-center">
                                <i class="fas fa-comment-slash" style="font-size: 48px; color: #ddd;"></i>
                                <p>No reviews yet for this product.</p>
                            </div>
                        <?php endif; ?>
                    </div>

                    <?php if(isset($_SESSION['customer'])): ?>
                    <div class="review-form mt-6">
                        <h4>Write Your Review</h4>
                        <!-- Review form would go here -->
                    </div>
                    <?php else: ?>
                        <div class="text-center mt-6">
                            <p>Please <a href="<?php echo BASE_URL; ?>login.php">login</a> to write a review.</p>
                        </div>
                    <?php endif; ?>
                </div>
                <?php endif; ?>

                <!-- Video Tab -->
                <?php if (!empty($p_video_link)): ?>
                <div id="video-tab" class="tab-pane">
                    <?php
                    $youtube_id = '';
                    if (strpos($p_video_link, 'youtube.com/watch?v=') !== false) {
                        parse_str( parse_url( $p_video_link, PHP_URL_QUERY ), $vars );
                        $youtube_id = $vars['v'];
                    } elseif (strpos($p_video_link, 'youtu.be/') !== false) {
                        $youtube_id = substr(parse_url($p_video_link, PHP_URL_PATH), 1);
                    }
                    if ($youtube_id):
                    ?>
                    <div class="video-container">
                        <iframe src="https://www.youtube.com/embed/<?php echo htmlspecialchars($youtube_id); ?>" 
                                frameborder="0" 
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                                allowfullscreen>
                        </iframe>
                    </div>
                    <?php else: ?>
                        <p>Video link is not available.</p>
                    <?php endif; ?>
                </div>
                <?php endif; ?>
            </div>
        </div>
    </div>
</center>

<!-- Mobile Bottom Navigation -->
<div class="mobile-bottom-nav">
    <?php if($p_qty > 0): ?>
        <form action="" method="post" class="w-1/2">
            <?php $csrf->echoInputField(); ?>
            <input type="hidden" name="p_id" value="<?php echo htmlspecialchars($p_id); ?>">
            <input type="hidden" name="p_qty" id="bottom_nav_p_qty_add" value="1">
            <input type="hidden" name="size_id" id="bottom_nav_size_id_add" 
                   value="<?php echo !empty($product_sizes) ? $product_sizes[0]['size_id'] : 0; ?>">
            <input type="hidden" name="size_name" id="bottom_nav_size_name_add" 
                   value="<?php echo htmlspecialchars($default_size_name); ?>">
            <input type="hidden" name="color_id" id="bottom_nav_color_id_add" 
                   value="<?php echo !empty($product_colors) ? $product_colors[0]['color_id'] : 0; ?>">
            <input type="hidden" name="color_name" id="bottom_nav_color_name_add" 
                   value="<?php echo htmlspecialchars($default_color_name); ?>">
            <button type="submit" name="form_add_to_cart" class="mobile-nav-btn mobile-nav-btn-cart">
                <i class="fas fa-cart-plus"></i> Add to Cart
            </button>
        </form>
        
        <form action="" method="post" class="w-1/2">
            <?php $csrf->echoInputField(); ?>
            <input type="hidden" name="p_id" value="<?php echo htmlspecialchars($p_id); ?>">
            <input type="hidden" name="p_qty" id="bottom_nav_p_qty_buy" value="1">
            <input type="hidden" name="size_id" id="bottom_nav_size_id_buy" 
                   value="<?php echo !empty($product_sizes) ? $product_sizes[0]['size_id'] : 0; ?>">
            <input type="hidden" name="size_name" id="bottom_nav_size_name_buy" 
                   value="<?php echo htmlspecialchars($default_size_name); ?>">
            <input type="hidden" name="color_id" id="bottom_nav_color_id_buy" 
                   value="<?php echo !empty($product_colors) ? $product_colors[0]['color_id'] : 0; ?>">
            <input type="hidden" name="color_name" id="bottom_nav_color_name_buy" 
                   value="<?php echo htmlspecialchars($default_color_name); ?>">
            <button type="submit" name="form_buy_now" 
                    class="mobile-nav-btn mobile-nav-btn-buy <?php echo $flash_sale_end_timestamp ? 'flash' : ''; ?>">
                <i class="fas fa-bolt"></i> Buy Now
            </button>
        </form>
    <?php else: ?>
        <button type="button" class="mobile-nav-btn mobile-nav-btn-cart" disabled style="width:100%;">
            <i class="fas fa-times-circle"></i> Out of Stock
        </button>
    <?php endif; ?>
</div>

<!-- AI Chat Modal -->
<div id="aiChatModal" class="modal-ai-chat" style="display:none;">
    <div class="modal-content-ai-chat">
        <span class="close-button-ai-chat">&times;</span>
        <div class="modal-header">
            <h2><i class="fas fa-robot"></i> AI Assistant</h2>
        </div>
        <div class="chat-container">
            <div id="chatMessages" class="chat-messages">
                <div class="chat-message ai">
                    Hello! I'm your AI assistant. How can I help you with this product?
                </div>
            </div>
            <div class="chat-input-area">
                <input type="text" id="chatInput" placeholder="Ask about specifications, delivery, or usage..." />
                <button id="sendChatBtn">
                    <i class="fas fa-paper-plane"></i> Send
                </button>
            </div>
        </div>
    </div>
</div>

<!-- Custom Alert Modal (added) -->
<div id="custom-alert-modal" class="custom-modal" style="display:none;">
    <div class="custom-modal-content">
        <span class="close-modal">&times;</span>
        <div id="custom-alert-message"></div>
    </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function() {
    // Helper function to show a custom alert modal
    const customAlert = (message) => {
        const modal = document.getElementById('custom-alert-modal');
        const messageElement = document.getElementById('custom-alert-message');
        messageElement.textContent = message;
        modal.style.display = 'block';

        const closeModal = () => {
            modal.style.display = 'none';
        };

        const closeButton = modal.querySelector('.close-modal');
        closeButton.onclick = closeModal;
        window.onclick = function(event) {
            if (event.target == modal) {
                closeModal();
            }
        };
    };

    // ========== IMAGE SLIDER FUNCTIONALITY ==========
    (function initGallerySlider(){
        const sliderContainer = document.querySelector('.product-gallery-card');
        const slides = Array.from(document.querySelectorAll('.main-image-slide'));
        const thumbs = Array.from(document.querySelectorAll('.thumbnail'));
        const prevBtn = document.querySelector('.prev-slide');
        const nextBtn = document.querySelector('.next-slide');
        const dots = Array.from(document.querySelectorAll('.dot'));

        if (slides.length === 0) return;

        let currentIndex = 0;
        let autoInterval = null;
        const AUTO_INTERVAL_MS = 5000;

        function updateUI(index) {
            index = ((index % slides.length) + slides.length) % slides.length;
            slides.forEach((s,i) => {
                s.classList.toggle('active', i === index);
                s.style.opacity = i === index ? '1' : '0';
            });
            if (thumbs.length === slides.length) {
                thumbs.forEach((t,i) => t.classList.toggle('active', i === index));
            }
            if (dots.length === slides.length) {
                dots.forEach((d,i) => d.classList.toggle('active', i === index));
            }
            currentIndex = index;
        }

        function next() { updateUI(currentIndex + 1); }
        function prev() { updateUI(currentIndex - 1); }

        // Prev/Next handlers
        prevBtn?.addEventListener('click', (e) => { e.preventDefault(); prev(); restartAuto(); });
        nextBtn?.addEventListener('click', (e) => { e.preventDefault(); next(); restartAuto(); });

        // Thumbnails & dots
        thumbs.forEach(t => t.addEventListener('click', () => { updateUI(parseInt(t.dataset.index || 0)); restartAuto(); }));
        dots.forEach(d => d.addEventListener('click', () => { updateUI(parseInt(d.dataset.index || 0)); restartAuto(); }));

        // Auto slide with pause on hover/focus
        function startAuto() {
            stopAuto();
            if (slides.length > 1) autoInterval = setInterval(next, AUTO_INTERVAL_MS);
        }
        function stopAuto() { if (autoInterval) { clearInterval(autoInterval); autoInterval = null; } }
        function restartAuto() { stopAuto(); startAuto(); }

        sliderContainer?.addEventListener('mouseenter', stopAuto);
        sliderContainer?.addEventListener('mouseleave', startAuto);
        sliderContainer?.addEventListener('focusin', stopAuto);
        sliderContainer?.addEventListener('focusout', startAuto);

        // Init
        updateUI(0);
        startAuto();
    })();
    // ========== END IMAGE SLIDER FUNCTIONALITY ==========


    // ========== GALLERY WISHLIST & COMPARE BUTTONS ==========
    // Wishlist: POST to wishlist_action.php (reuse same behavior as other wishlist buttons)
    document.querySelectorAll('.gallery-wishlist-btn').forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            const btn = this;
            const productId = btn.dataset.productId;
            const isAdding = !btn.classList.contains('active');

            <?php if (!isset($_SESSION['customer'])): ?>
                customAlert('Please login to add products to your wishlist.');
                setTimeout(() => { window.location.href = '<?php echo BASE_URL; ?>login.php'; }, 1200);
                return;
            <?php endif; ?>

            // Optimistic UI
            btn.classList.toggle('active', isAdding);
            const icon = btn.querySelector('i');
            if (icon) {
                icon.classList.toggle('far', !isAdding);
                icon.classList.toggle('fas', isAdding);
            }

            fetch('<?php echo BASE_URL; ?>wishlist_action.php', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: `product_id=${encodeURIComponent(productId)}&action=${isAdding ? 'add' : 'remove'}&csrf_token=<?php echo $_SESSION['csrf_token'] ?? ''; ?>`
            })
            .then(r => r.json())
            .then(data => {
                if (data.status !== 'success') {
                    // revert UI
                    btn.classList.toggle('active', !isAdding);
                    if (icon) {
                        icon.classList.toggle('far', isAdding);
                        icon.classList.toggle('fas', !isAdding);
                    }
                    customAlert('Error: ' + (data.message || 'Failed to update wishlist.'));
                } else {
                    // sync other wishlist buttons
                    document.querySelectorAll(`[data-product-id="${productId}"]`).forEach(el => {
                        if (el.classList) el.classList.toggle('active', isAdding);
                        const elIcon = el.querySelector('i');
                        if (elIcon) {
                            elIcon.classList.toggle('far', !isAdding);
                            elIcon.classList.toggle('fas', isAdding);
                        }
                    });
                    customAlert(data.message || (isAdding ? 'Added to wishlist' : 'Removed from wishlist'));
                }
            })
            .catch(() => {
                // revert on error
                btn.classList.toggle('active', !isAdding);
                if (icon) {
                    icon.classList.toggle('far', isAdding);
                    icon.classList.toggle('fas', !isAdding);
                }
                customAlert('Network error. Please try again.');
            });
        });
    });

    // Compare: store product ids in localStorage
    document.querySelectorAll('.gallery-compare-btn').forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            const btn = this;
            const pid = String(btn.dataset.productId);
            const key = 'compare_product_ids';
            let list = JSON.parse(localStorage.getItem(key) || '[]');
            const exists = list.includes(pid);

            if (exists) {
                list = list.filter(x => x !== pid);
                btn.classList.remove('active');
                customAlert('Removed from compare');
            } else {
                list.push(pid);
                btn.classList.add('active');
                customAlert('Added to compare');
            }
            localStorage.setItem(key, JSON.stringify(list));
        });
    });
    // ========== END GALLERY WISHLIST & COMPARE ==========

    // ========== TAB SWITCHING FUNCTIONALITY ==========
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabPanes = document.querySelectorAll('.tab-pane');

    tabButtons.forEach(button => {
        button.addEventListener('click', (e) => {
            e.preventDefault();
            // Remove active class from all tabs
            tabButtons.forEach(btn => btn.classList.remove('active'));
            tabPanes.forEach(pane => pane.classList.remove('active'));
            
            // Add active class to clicked tab
            button.classList.add('active');
            const targetId = button.getAttribute('data-target');
            const targetEl = document.getElementById(targetId);
            if (targetEl) targetEl.classList.add('active');
        });
    });
    // ========== END TAB SWITCHING ==========
});
</script>

<script>
// Quantity buttons and fly-to-cart animation
document.addEventListener('DOMContentLoaded', function() {
    const qtyInput = document.getElementById('p_qty');
    const minusBtn = document.querySelector('.qty-btn.minus');
    const plusBtn = document.querySelector('.qty-btn.plus');
    const bottomQtyAdd = document.getElementById('bottom_nav_p_qty_add');
    const bottomQtyBuy = document.getElementById('bottom_nav_p_qty_buy');

    function clampQty(val) {
        const min = parseInt(qtyInput.getAttribute('min')) || 1;
        const max = parseInt(qtyInput.getAttribute('max')) || 999999;
        if (isNaN(val)) return min;
        return Math.max(min, Math.min(max, val));
    }

    if (minusBtn && plusBtn && qtyInput) {
        minusBtn.addEventListener('click', function() {
            let v = parseInt(qtyInput.value || '1');
            v = clampQty(v - 1);
            qtyInput.value = v;
            if (bottomQtyAdd) bottomQtyAdd.value = v;
            if (bottomQtyBuy) bottomQtyBuy.value = v;
        });
        plusBtn.addEventListener('click', function() {
            let v = parseInt(qtyInput.value || '1');
            v = clampQty(v + 1);
            qtyInput.value = v;
            if (bottomQtyAdd) bottomQtyAdd.value = v;
            if (bottomQtyBuy) bottomQtyBuy.value = v;
        });
        // also sanitize manual input
        qtyInput.addEventListener('change', function() {
            let v = parseInt(qtyInput.value || '1');
            v = clampQty(v);
            qtyInput.value = v;
            if (bottomQtyAdd) bottomQtyAdd.value = v;
            if (bottomQtyBuy) bottomQtyBuy.value = v;
        });
    }

    // Fly to basket animation helper (two-stage: arc then drop) and lid pulse
    function animateToCart(imgEl, onComplete) {
        const basket = document.getElementById('fly-basket');
        const fallback = document.querySelector('.cart-link') || document.querySelector('.top-header .fa-shopping-cart') || document.querySelector('.mobile-action-btn .fa-shopping-cart');
        const cartTarget = basket || fallback;
        if (!imgEl || !cartTarget) {
            if (typeof onComplete === 'function') onComplete();
            return;
        }

        // Show basket (smoothly) when animation starts
        if (basket) {
            basket.classList.add('visible');
        }

        const imgRect = imgEl.getBoundingClientRect();
        // recompute target rect after showing basket
        const cartRect = cartTarget.getBoundingClientRect();

        const flyer = imgEl.cloneNode(true);
        flyer.style.position = 'fixed';
        flyer.style.left = imgRect.left + 'px';
        flyer.style.top = imgRect.top + 'px';
        flyer.style.width = imgRect.width + 'px';
        flyer.style.height = imgRect.height + 'px';
        flyer.style.boxShadow = '0 12px 30px rgba(0,0,0,0.25)';
        flyer.style.transition = 'transform 640ms cubic-bezier(.2,.8,.2,1), opacity 640ms';
        flyer.style.zIndex = 99999;
        flyer.style.pointerEvents = 'none';
        flyer.style.borderRadius = '8px';
        flyer.style.overflow = 'hidden';
        document.body.appendChild(flyer);

        // compute mid point above basket for arc
        const midX = (imgRect.left + (cartRect.left + cartRect.width)/2) / 2;
        const midY = Math.min(imgRect.top, cartRect.top) - Math.max(80, window.innerHeight * 0.08); // dynamic arc height

        const centerX = (cartRect.left + cartRect.width/2) - (imgRect.left + imgRect.width/2);
        const centerY = (cartRect.top + cartRect.height/2) - (imgRect.top + imgRect.height/2);

        // stage 1: move to mid (arc)
        const translateMidX = midX - imgRect.left;
        const translateMidY = midY - imgRect.top;
        flyer.style.transform = `translate(${translateMidX}px, ${translateMidY}px) scale(0.78) rotate(8deg)`;
        flyer.style.opacity = '0.98';

        // after arc, do quick drop into basket
        setTimeout(() => {
            flyer.style.transition = 'transform 420ms cubic-bezier(.3,.0,.2,1), opacity 420ms';
            flyer.style.transform = `translate(${centerX}px, ${centerY}px) scale(0.22) rotate(28deg)`;
            flyer.style.opacity = '0.6';
        }, 620);

        // cleanup and basket lid pulse, then auto-hide basket
        setTimeout(() => {
            try {
                // animate lid if basket exists
                if (basket) {
                    const lid = basket.querySelector('.basket-lid');
                    if (lid) {
                        lid.animate([
                            { transform: 'rotate(0deg)' },
                            { transform: 'rotate(-36deg)' },
                            { transform: 'rotate(0deg)' }
                        ], { duration: 480, easing: 'cubic-bezier(.2,.9,.2,1)' });
                    }
                    // small jolt on basket icon
                    const icon = basket.querySelector('.basket-icon') || basket;
                    if (icon && icon.animate) {
                        icon.animate([
                            { transform: 'translateY(0) scale(1)' },
                            { transform: 'translateY(-8px) scale(1.08)' },
                            { transform: 'translateY(0) scale(1)' }
                        ], { duration: 420, easing: 'ease-out' });
                    }
                } else {
                    // fallback pulse on other cart target
                    if (cartTarget.animate) {
                        cartTarget.animate([
                            { transform: 'scale(1)' },
                            { transform: 'scale(1.24)' },
                            { transform: 'scale(1)' }
                        ], { duration: 380, easing: 'ease-out' });
                    }
                }
            } catch (err) {
                // ignore
            }

            flyer.remove();

            // hide basket automatically after a short delay
            if (basket) {
                setTimeout(() => {
                    basket.classList.remove('visible');
                }, 700);
            }

            if (typeof onComplete === 'function') onComplete();
        }, 1100);
    }

    // Find currently active main image
    function getActiveProductImage() {
        return document.querySelector('.main-image-container .main-image-slide.active') || document.querySelector('.main-image-container img');
    }

    // Intercept top Add to Cart button to animate then submit
    const topAddBtn = document.querySelector('.add-to-cart-form .btn-cart');
    const addForm = document.querySelector('.add-to-cart-form');
    if (topAddBtn && addForm) {
        topAddBtn.addEventListener('click', function(e) {
            // prevent normal submit to show animation
            e.preventDefault();
            const img = getActiveProductImage();
                animateToCart(img, function() {
                    // Ensure the server sees which submit button was used (name/value)
                    try {
                        const btnName = topAddBtn.getAttribute('name') || 'form_add_to_cart';
                        const btnValue = topAddBtn.getAttribute('value') || '1';
                        // create hidden input so PHP sees the submit name when using form.submit()
                        const hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = btnName;
                        hidden.value = btnValue;
                        addForm.appendChild(hidden);
                    } catch (err) {
                        // ignore
                    }
                    // optimistic increment cart count in header if present
                    const cartCount = document.querySelector('.cart-count');
                    if (cartCount) {
                        const n = parseInt(cartCount.textContent || '0');
                        cartCount.textContent = n + 1;
                    }
                    // submit the form after animation
                    addForm.submit();
                });
        });
    }

    // Also animate for mobile bottom Add to Cart buttons
    const mobileAddBtns = document.querySelectorAll('.mobile-nav-btn-cart');
    mobileAddBtns.forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            // find image and animate, but let the form submit afterwards (forms are separate)
            const img = getActiveProductImage();
            animateToCart(img);
            // allow default submit to proceed
        });
    });
});
</script>

<script>
// AI chat modal handlers
document.addEventListener('DOMContentLoaded', function() {
    const askBtns = document.querySelectorAll('.ask-ai-btn');
    const modal = document.getElementById('aiChatModal');
    const chatMessages = document.getElementById('chatMessages');
    const chatInput = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendChatBtn');
    const closeBtn = document.querySelector('.close-button-ai-chat');

    function openModal(productId) {
        if (!modal) return;
        modal.style.display = 'block';
        modal.dataset.productId = productId;
        // reset chat
        if (chatMessages) chatMessages.innerHTML = '<div class="chat-message ai">Hello! I\'m your AI assistant. How can I help you with this product?</div>';
        if (chatInput) { chatInput.value = ''; chatInput.focus(); }
    }

    function closeModal() {
        if (!modal) return;
        modal.style.display = 'none';
    }

    askBtns.forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            const productId = btn.getAttribute('data-product-id') || btn.dataset.productId || '';
            openModal(productId);
        });
    });

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    window.addEventListener('click', function(ev) {
        if (ev.target === modal) closeModal();
    });

    function appendMessage(text, who='ai') {
        if (!chatMessages) return;
        const div = document.createElement('div');
        div.className = 'chat-message ' + (who === 'user' ? 'user' : 'ai');
        div.innerHTML = text;
        chatMessages.appendChild(div);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    if (sendBtn) {
        sendBtn.addEventListener('click', function() {
            const text = chatInput ? chatInput.value.trim() : '';
            if (!text) return;
            const productId = modal ? (modal.dataset.productId || '') : (document.querySelector('.ask-ai-btn')?.dataset?.productId || '');
            appendMessage(text, 'user');
            if (chatInput) chatInput.value = '';
            // show loading indicator
            appendMessage('Thinking...', 'ai');

            const formData = new FormData();
            formData.append('prompt', text);
            formData.append('product_id', productId);

            fetch('<?php echo BASE_URL; ?>gemini_chat.php', {
                method: 'POST',
                body: formData,
                credentials: 'same-origin'
            }).then(r => r.json()).then(data => {
                // remove last loading message
                const msgs = chatMessages.querySelectorAll('.chat-message.ai');
                if (msgs.length) msgs[msgs.length-1].remove();

                if (data && data.status === 'success' && data.response) {
                    appendMessage(data.response, 'ai');
                } else {
                    appendMessage('Sorry, I could not get an answer. ' + (data.message || ''), 'ai');
                }
            }).catch(err => {
                const msgs = chatMessages.querySelectorAll('.chat-message.ai');
                if (msgs.length) msgs[msgs.length-1].remove();
                appendMessage('Network error. Please try again later.', 'ai');
                console.error(err);
            });
        });
    }
});
</script>