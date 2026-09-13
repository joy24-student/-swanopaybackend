<?php
ob_start();
// Check if session is already active before starting
if (session_status() == PHP_SESSION_NONE) {
    session_start();
}

// Include database config and functions using require_once to prevent redeclaration errors
require_once("admin/inc/config.php");
require_once("admin/inc/functions.php"); 
require_once("admin/inc/CSRF_Protect.php");
$csrf = new CSRF_Protect();
$error_message = '';
$success_message = '';
$error_message1 = '';
$success_message1 = '';

// Getting all language variables
$i=1;
$statement = $pdo->prepare("SELECT * FROM tbl_language ORDER BY lang_id");
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);                           
foreach ($result as $row) {
    define('LANG_VALUE_'.$i,$row['lang_value']);
    $i++;
}

// Fetch general website settings
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings = $statement->fetch(PDO::FETCH_ASSOC); 

// Assign settings variables
$logo = $settings['logo'] ?? 'default_logo.png';
$favicon = $settings['favicon'] ?? 'default_favicon.png';
$contact_email = $settings['contact_email'] ?? 'Not added';
$contact_phone = $settings['contact_phone'] ?? 'Not added ';
$meta_title_home = $settings['meta_title_home'] ?? 'E-commerce Website';
$meta_keyword_home = $settings['meta_keyword_home'] ?? 'ecommerce, shop, online store';
$meta_description_home = $settings['meta_description_home'] ?? 'Your one-stop online shop.';
$before_head = $settings['before_head'] ?? '';
$after_body = $settings['after_body'] ?? '';

// New settings for feature visibility
$wishlist_feature_on_off = $settings['wishlist_feature_on_off'] ?? 1;
$compare_feature_on_off = $settings['compare_feature_on_off'] ?? 0;
$store_feature_on_off = $settings['store_feature_on_off'] ?? 0;


// Order expiration belongs in a scheduled, transactional job. Page views never mutate orders.

// Meta tags for dynamic pages
$cur_page = basename($_SERVER["SCRIPT_NAME"]); 
$page_meta_title = $meta_title_home;
$page_meta_keyword = $meta_keyword_home;
$page_meta_description = $meta_description_home;
$og_photo = $logo; 
$og_title = $meta_title_home;
$og_slug = $cur_page;
$og_description = $meta_description_home;

// Override meta tags for specific pages
if ($cur_page == 'about.php') {
    $stmt_page = $pdo->prepare("SELECT about_meta_title, about_meta_keyword, about_meta_description FROM tbl_page WHERE id=1");
    $stmt_page->execute();
    $page_data = $stmt_page->fetch(PDO::FETCH_ASSOC);
    if ($page_data) {
        $page_meta_title = $page_data['about_meta_title'];
        $page_meta_keyword = $page_data['about_meta_keyword'];
        $page_meta_description = $page_data['about_meta_description'];
    }
}

if ($cur_page == 'product.php' && isset($_REQUEST['id'])) {
    $statement_product = $pdo->prepare("SELECT p_name, p_featured_photo, p_description FROM tbl_product WHERE p_id=?");
    $statement_product->execute(array($_REQUEST['id']));
    $product_row = $statement_product->fetch(PDO::FETCH_ASSOC);
    if ($product_row) {
        $og_photo = $product_row['p_featured_photo'];
        $og_title = $product_row['p_name'];
        $og_slug = 'product.php?id=' . $_REQUEST['id'];
        $og_description = substr(strip_tags($product_row['p_description']), 0, 200) . '...';
        $page_meta_title = $og_title; 
        $page_meta_description = $og_description;
    }
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <base href="<?php echo htmlspecialchars(BASE_URL, ENT_QUOTES, 'UTF-8'); ?>">
    <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8"/>
    <meta name="robots" content="index, follow">

    <title><?php echo htmlspecialchars($page_meta_title); ?></title>
    <meta name="keywords" content="<?php echo htmlspecialchars($page_meta_keyword); ?>">
    <meta name="description" content="<?php echo htmlspecialchars($page_meta_description); ?>">

    <link rel="icon" type="image/png" href="assets/uploads/<?php echo htmlspecialchars($favicon); ?>">

    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"> 
    <link rel="stylesheet" href="assets/css/owl.carousel.min.css">
    <link rel="stylesheet" href="assets/css/owl.theme.default.min.css">
    <link rel="stylesheet" href="assets/css/jquery.bxslider.min.css">
    <link rel="stylesheet" href="assets/css/magnific-popup.css">
    <link rel="stylesheet" href="assets/css/rating.css">
    <link rel="stylesheet" href="assets/css/spacing.css">
    <link rel="stylesheet" href="assets/css/bootstrap-touch-slider.css">
    <link rel="stylesheet" href="assets/css/animate.min.css">
    <link rel="stylesheet" href="assets/css/tree-menu.css">
    <link rel="stylesheet" href="assets/css/select2.min.css">
    <link href="https://cdn.jsdelivr.net/npm/summernote@0.8.18/dist/summernote-bs4.min.css" rel="stylesheet">
    <link rel="stylesheet" href="assets/css/main.css">
    <link rel="stylesheet" href="assets/css/responsive.css">
    <link rel="stylesheet" href="assets/css/style.css">

    <?php if ($cur_page == 'blog-single.php' || $cur_page == 'product.php'): ?>
        <meta property="og:title" content="<?php echo htmlspecialchars($og_title); ?>">
        <meta property="og:type" content="website">
        <meta property="og:url" content="<?php echo htmlspecialchars(BASE_URL . $og_slug); ?>">
        <meta property="og:description" content="<?php echo htmlspecialchars($og_description); ?>">
        <meta property="og:image" content="assets/uploads/<?php echo htmlspecialchars($og_photo); ?>">
    <?php endif; ?>

    <?php echo $before_head; ?>

    <style>
        /* General Reset & Body */
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', sans-serif;
            overflow-x: hidden;
            padding-top: 0;
            padding-bottom: 0;
        }

        /* Main Header Styles */
        .main-header {
            background-color: #ffffff;
            border-bottom: 1px solid #e0e0e0;
            box-shadow: 0 2px 10px rgba(0,0,0,0.08);
            padding: 10px 20px;
            position: relative;
            z-index: 990;
        }

        .main-header .container-fluid {
            width: 100%;
            padding-right: 15px;
            padding-left: 15px;
            margin-right: auto;
            margin-left: auto;
        }

        /* Desktop Header Content */
        .desktop-header-content {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 20px;
        }
        
        /* NEW: Wrapper for Hamburger and Logo */
        .desktop-logo-wrapper {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        /* NEW: Desktop Menu Toggle Button */
        #desktop-menu-btn {
            background: none;
            border: none;
            font-size: 1.5rem;
            color: #333;
            cursor: pointer;
            padding: 5px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: color 0.2s;
        }
        #desktop-menu-btn:hover {
            color: #007bff;
        }

        .header-logo a {
            display: block;
        }

        .header-logo img {
            max-height: 45px;
            width: auto;
        }

        /* Desktop Search Bar */
        .header-search {
            flex-grow: 1;
            max-width: 600px;
            border: 2px solid #007bff;
            border-radius: 8px;
            overflow: hidden;
            transition: all 0.3s ease;
            display: none;
        }

        .header-search.active {
            display: flex;
        }

        .header-search:focus-within {
            box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.25);
        }

        .header-search input {
            flex-grow: 1;
            padding: 10px 15px;
            border: none;
            outline: none;
            font-size: 1em;
            color: #333;
            background-color: #f8f8f8;
        }

        .header-search button {
            background-color: #007bff;
            color: white;
            border: none;
            padding: 10px 15px;
            cursor: pointer;
            font-size: 1.1em;
            transition: background-color 0.2s ease;
        }
        .header-search button:hover {
            background-color: #0056b3;
        }

        /* Search Suggestions Dropdown */
        .search-suggestions {
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            background: white;
            border: 1px solid #ddd;
            border-top: none;
            max-height: 400px;
            overflow-y: auto;
            z-index: 1000;
            display: none;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            border-radius: 0 0 4px 4px;
        }

        .search-suggestions.active {
            display: block;
        }

        .suggestion-item {
            padding: 10px 15px;
            border-bottom: 1px solid #f0f0f0;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 10px;
            transition: background-color 0.2s;
        }

        .suggestion-item:hover {
            background-color: #f8f9fa;
        }

        .suggestion-item img {
            width: 40px;
            height: 40px;
            object-fit: cover;
            border-radius: 4px;
        }

        .suggestion-content {
            flex: 1;
        }

        .suggestion-name {
            font-weight: 500;
            color: #333;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .suggestion-price {
            font-size: 0.85em;
            color: #e74c3c;
            font-weight: 600;
        }

        .header-search {
            position: relative;
        }

        .header-action-icons {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .header-action-icons .icon-btn {
            background: none;
            border: none;
            font-size: 1.3em;
            color: #555;
            cursor: pointer;
            padding: 8px;
            border-radius: 50%;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            justify-content: center;
            width: 40px;
            height: 40px;
        }

        .header-action-icons .icon-btn:hover {
            background-color: #f0f0f0;
            color: #007bff;
        }
        .header-action-icons .icon-btn.active {
            color: #007bff;
        }

        .header-user-actions {
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .header-user-actions .user-link {
            color: #333;
            text-decoration: none;
            font-size: 0.95em;
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 12px;
            border-radius: 6px;
            transition: all 0.2s ease;
        }

        .header-user-actions .user-link:hover {
            background-color: #f0f0f0;
            color: #007bff;
        }

        .header-user-actions .cart-link {
            position: relative;
            color: #333;
            text-decoration: none;
            font-size: 1.5em;
            padding: 5px;
            border-radius: 50%;
            transition: all 0.2s ease;
        }

        .header-user-actions .cart-link:hover {
            background-color: #f0f0f0;
            color: #e74c3c;
        }

        .header-user-actions .cart-count {
            background-color: #e74c3c;
            color: white;
            border-radius: 50%;
            padding: 3px 7px;
            font-size: 0.7em;
            position: absolute;
            top: -5px;
            right: -5px;
            min-width: 20px;
            text-align: center;
            border: 1px solid #fff;
        }

        /* Mobile Header Content */
        .mobile-header-content {
            display: none;
            justify-content: space-between;
            align-items: center;
        }

        .mobile-header-content .mobile-logo img {
            max-height: 40px;
        }

        .mobile-icon-btn {
            background: none;
            border: none;
            font-size: 1.4em;
            color: #333;
            cursor: pointer;
            padding: 8px;
            border-radius: 50%;
            transition: background-color 0.2s ease;
        }

        .mobile-icon-btn:hover {
            background-color: #f0f0f0;
        }

        .mobile-header-right {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        /* Mobile Search Bar */
        .mobile-search-bar-expanded {
            display: none;
            margin-top: 10px;
            width: 100%;
            border: 1px solid #ddd;
            border-radius: 8px;
            overflow: hidden;
        }

        .mobile-search-bar-expanded.active {
            display: flex;
        }

        .mobile-search-bar-expanded input {
            flex-grow: 1;
            padding: 8px 12px;
            border: none;
            outline: none;
            font-size: 0.9em;
            background-color: #f8f8f8;
        }

        .mobile-search-bar-expanded button {
            background-color: #007bff;
            color: white;
            border: none;
            padding: 8px 12px;
            cursor: pointer;
            font-size: 1em;
            transition: background-color 0.2s ease;
        }


        /* Desktop Vertical Navigation (Sidebar) */
        .desktop-sidebar {
            position: fixed;
            top: 0;
            left: -250px; /* Hidden by default */
            width: 250px;
            height: 100%;
            background-color: #2c3e50;
            color: #ecf0f1;
            padding-top: 20px;
            transition: left 0.3s ease-in-out;
            z-index: 1000;
            box-shadow: 2px 0 10px rgba(0,0,0,0.2);
            display: flex;
            flex-direction: column;
        }

        .desktop-sidebar.active {
            left: 0; 
        }

        .sidebar-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0 15px 20px 15px;
            border-bottom: 1px solid rgba(255,255,255,0.1);
            margin-bottom: 20px;
        }

        .sidebar-logo img {
            max-height: 50px;
            width: auto;
        }

        .sidebar-toggle-btn {
            background: none;
            border: none;
            color: #ecf0f1;
            font-size: 1.5em;
            cursor: pointer;
            padding: 5px;
        }

        .sidebar-menu {
            list-style: none;
            padding: 0;
            margin: 0;
            flex-grow: 1;
            overflow-y: auto;
        }

        .sidebar-menu li {
            position: relative;
        }

        .sidebar-menu li a {
            display: flex;
            align-items: center;
            padding: 12px 15px;
            color: #ecf0f1;
            text-decoration: none;
            transition: background-color 0.2s ease, color 0.2s ease;
            font-size: 1.1em;
        }

        .sidebar-menu li a i {
            margin-right: 10px;
            width: 20px;
            text-align: center;
        }

        .sidebar-menu li a:hover {
            background-color: #34495e;
            color: #fff;
        }

        /* Submenu arrows */
        .sidebar-menu .submenu-arrow,
        .sidebar-menu .submenu-arrow-level-1,
        .sidebar-menu .submenu-arrow-level-2 {
            margin-left: auto;
            transition: transform 0.3s ease;
            font-size: 0.8em;
        }

        .sidebar-menu li.has-submenu.open > a .submenu-arrow,
        .sidebar-menu li.has-submenu-level-1.open > a .submenu-arrow-level-1,
        .sidebar-menu li.has-submenu-level-2.open > a .submenu-arrow-level-2 {
            transform: rotate(90deg);
        }


        .sidebar-menu .submenu,
        .sidebar-menu .submenu-level-2,
        .sidebar-menu .submenu-level-3 {
            list-style: none;
            padding: 0;
            margin: 0;
            background-color: #34495e;
            display: none;
            overflow: hidden;
            transition: max-height 0.3s ease-out;
            max-height: 0;
        }

        .sidebar-menu .submenu.open,
        .sidebar-menu .submenu-level-2.open,
        .sidebar-menu .submenu-level-3.open {
            max-height: 500px;
            display: block;
        }

        .sidebar-menu .submenu li a,
        .sidebar-menu .submenu-level-2 li a,
        .sidebar-menu .submenu-level-3 li a {
            padding-left: 35px;
            font-size: 1em;
        }

        .sidebar-menu .submenu-level-2 li a {
            padding-left: 50px;
        }

        .sidebar-menu .submenu-level-3 li a {
            padding-left: 65px;
        }

        .sidebar-menu .submenu li a:hover,
        .sidebar-menu .submenu-level-2 li a:hover,
        .sidebar-menu .submenu-level-3 li a:hover {
            background-color: #49637c;
        }

        /* Mobile Menu Overlay */
        .mobile-menu-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0, 0, 0, 0.5);
            z-index: 999;
            display: none;
        }

        /* Main content wrapper */
        .content-wrapper-main {
            transition: margin-left 0.3s ease-in-out;
            margin-left: 0;
            padding-top: 0;
            padding-bottom: 0;
        }
        
        /* Content Pushed State */
        .content-wrapper-main.sidebar-active {
            margin-left: 250px;
        }

        /* Mobile Bottom Navigation Bar */
        .mobile-bottom-nav {
            display: none;
            position: fixed;
            bottom: 0;
            left: 0;
            width: 100%;
            background-color: #fff;
            border-top: 1px solid #eee;
            box-shadow: 0 -2px 10px rgba(0,0,0,0.1);
            z-index: 100;
            justify-content: space-around;
            align-items: center;
            padding: 8px 0;
            box-sizing: border-box;
        }

        .mobile-bottom-nav .nav-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-decoration: none;
            color: #7f8c8d;
            font-size: 0.8em;
            font-weight: 500;
            transition: color 0.2s ease;
            position: relative;
            padding: 5px 0;
            flex: 1;
        }

        .mobile-bottom-nav .nav-item i {
            font-size: 1.4em;
            margin-bottom: 3px;
            transition: color 0.2s ease;
        }

        .mobile-bottom-nav .nav-item:hover {
            color: #007bff;
        }

        .mobile-bottom-nav .nav-item.active {
            color: #007bff;
        }

        .mobile-bottom-nav .nav-item.active i {
            color: #007bff;
        }

        .mobile-bottom-nav .cart-count-bottom {
            background-color: #e74c3c;
            color: white;
            border-radius: 50%;
            padding: 2px 6px;
            font-size: 0.7em;
            position: absolute;
            top: 0px;
            right: 15px;
            min-width: 18px;
            text-align: center;
            transform: translateX(50%);
        }


        /* Responsive Overrides */
        @media (max-width: 991px) { /* Tablets and Mobile */
            .desktop-header-content {
                display: none;
            }
            .mobile-header-content {
                display: flex;
            }
            .main-header {
                padding: 8px 15px;
                position: sticky;
                top: 0;
                width: 100%;
                box-sizing: border-box;
            }

            .mobile-search-bar-expanded.active {
                display: flex;
            }

            .desktop-sidebar {
                left: -250px;
                box-shadow: 3px 0 15px rgba(0,0,0,0.3);
            }

            .mobile-bottom-nav {
                display: flex;
            }

            body {
                padding-top: 60px;
                padding-bottom: 60px;
            }
            .content-wrapper-main {
                min-height: calc(100vh - 60px - 60px);
            }
        }

        @media (min-width: 992px) { /* Desktop */
            .desktop-header-content {
                display: flex;
            }
            .mobile-header-content, .mobile-search-bar-expanded {
                display: none !important;
            }
            .desktop-sidebar {
                position: fixed;
            }
            .content-wrapper-main {
                min-height: 100vh;
                padding-bottom: 0;
                padding-top: 0;
            }
            body {
                padding-top: 0;
                padding-bottom: 0;
            }
                /* Fixed header on desktop: full width by default, shifts when sidebar active */
                .main-header {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    width: 100%;
                    z-index: 995;
                    transition: left 0.25s ease, width 0.25s ease;
                }
                /* When sidebar is visible, push header right and reduce width */
                .main-header.with-sidebar {
                    left: 250px;
                    width: calc(100% - 250px);
                }
                /* Ensure page content is pushed below the fixed header */
                .content-wrapper-main {
                    margin-top: 65px; /* default header height fallback */
                }
        }


        /* --- Horizontal Scroll Wrapper --- */
.horizontal-scroll-wrapper {
    display: flex;
    overflow-x: auto;
    gap: 20px;
    padding-bottom: 20px;
    scrollbar-width: thin; /* Firefox */
}
.horizontal-scroll-wrapper::-webkit-scrollbar {
    height: 6px;
}
.horizontal-scroll-wrapper::-webkit-scrollbar-thumb {
    background: #007bff;
    border-radius: 10px;
}
/* Fixed width for items in scroll */
.horizontal-scroll-wrapper .product-card,
.horizontal-scroll-wrapper .cat-item {
    flex: 0 0 240px; /* Adjust width as needed */
    width: 240px;
}

/* --- Round Category Images --- */
.cat-item.round-style .cat-img-box {
    width: 120px;
    height: 120px;
    margin: 0 auto 15px auto;
}
.cat-item.round-style .cat-img-box img {
    border-radius: 50%;
    width: 100%;
    height: 100%;
    object-fit: cover;
    box-shadow: 0 4px 10px rgba(0,0,0,0.1);
}

/* --- Product Buttons Invisible until Hover --- */
.product-card .hover-btns {
    opacity: 0;
    visibility: hidden;
    transform: translateY(20px);
    transition: all 0.3s ease-in-out;
    bottom: 10px; /* Position it */
}
.product-card:hover .hover-btns {
    opacity: 1;
    visibility: visible;
    transform: translateY(0);
}

/* --- Scroll Top Button --- */
#scrollTopBtn {
    display: none;
    position: fixed;
    bottom: 20px;
    right: 30px;
    z-index: 9999;
    border: none;
    outline: none;
    background-color: #007bff;
    color: white;
    cursor: pointer;
    padding: 15px;
    border-radius: 50%;
    font-size: 18px;
    box-shadow: 0 4px 10px rgba(0,0,0,0.3);
}
#scrollTopBtn:hover {
    background-color: #555;
}

/* --- Pop-up Modal --- */
.custom-popup {
    display: none;
    position: fixed;
    z-index: 10000;
    left: 0; top: 0;
    width: 100%; height: 100%;
    overflow: auto;
    background-color: rgba(0,0,0,0.6);
    animation: fadeIn 0.5s;
}
.custom-popup-content {
    background-color: #fff;
    margin: 10% auto;
    padding: 20px;
    border: 1px solid #888;
    width: 90%;
    max-width: 500px;
    position: relative;
    border-radius: 8px;
    text-align: center;
    animation: slideDown 0.5s;
}
.close-popup {
    position: absolute;
    top: 5px; right: 15px;
    color: #aaa;
    font-size: 28px;
    font-weight: bold;
    cursor: pointer;
}
@keyframes fadeIn { from {opacity: 0} to {opacity: 1} }
@keyframes slideDown { from {transform: translateY(-50px)} to {transform: translateY(0)} }

/* --- Extra Footer Links --- */
.extra-footer-section {
    background: #232f3e;
    color: #fff;
    padding: 30px 0;
    margin-top: 30px;
}
.extra-footer-section a { color: #ccc; text-decoration: none; display: block; margin-bottom: 8px; }
.extra-footer-section a:hover { color: #fff; padding-left: 5px; transition: 0.2s; }
.extra-footer-section h4 { color: #fff; margin-bottom: 20px; font-size: 16px; font-weight: bold; text-transform: uppercase; }
@media(max-width: 768px) { .extra-footer-section { display: none; } }
    </style>
    <!-- jQuery Library -->
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
</head>
<body>
<?php echo $after_body; ?>

<header class="main-header">
    <div class="container-fluid">
        <div class="desktop-header-content">
            <div class="desktop-logo-wrapper">
                <button id="desktop-menu-btn" title="Toggle Menu">
                    <i class="fas fa-bars"></i>
                </button>
                <div class="header-logo">
                    <a href="<?php echo BASE_URL; ?>">
                        <img src="assets/uploads/<?php echo htmlspecialchars($logo); ?>" alt="Logo">
                    </a>
                </div>
            </div>
            
            <div class="header-action-icons">
                <button id="desktop-search-toggle" class="icon-btn" title="Search">
                    <i class="fas fa-search"></i>
                </button>

                <?php if ($wishlist_feature_on_off == 1): ?>
                <a href="<?php echo BASE_URL; ?>wishlist.php" class="icon-btn" title="Wishlist">
                    <i class="fas fa-heart"></i>
                </a>
                <?php endif; ?>

                <?php if ($compare_feature_on_off == 1): ?>
                <a href="<?php echo BASE_URL; ?>compare.php" class="icon-btn" title="Compare Products">
                    <i class="fas fa-balance-scale"></i>
                </a>
                <?php endif; ?>

                <?php if ($store_feature_on_off == 1): ?>
                <a href="<?php echo BASE_URL; ?>stores.php" class="icon-btn" title="Stores">
                    <i class="fas fa-store"></i>
                </a>
                <?php endif; ?>
            </div>

            <div class="header-search" id="desktop-search-bar">
                <input type="text" id="desktop-search-input" placeholder="Search for products..." autocomplete="off">
                <div class="search-suggestions" id="desktop-search-suggestions"></div>
                <button id="desktop-search-submit-btn"><i class="fas fa-search"></i></button>
            </div>

            <div class="header-user-actions">
                <?php if(isset($_SESSION['customer'])): ?>
                    <a href="<?php echo BASE_URL; ?>dashboard.php" class="user-link">
                        <i class="fas fa-user"></i> <span>Hi, <?php echo htmlspecialchars($_SESSION['customer']['cust_name']); ?></span>
                    </a>
                <?php else: ?>
                    <a href="<?php echo BASE_URL; ?>login.php" class="user-link"><i class="fas fa-sign-in-alt"></i> <span>Login</span></a>
                    <a href="<?php echo BASE_URL; ?>registration.php" class="user-link"><i class="fas fa-user-plus"></i> <span>Register</span></a>
                <?php endif; ?>
                <a href="<?php echo BASE_URL; ?>cart.php" class="cart-link">
                    <i class="fas fa-shopping-cart"></i>
                    <span class="cart-count"><?php echo (isset($_SESSION['cart_p_id']) ? count($_SESSION['cart_p_id']) : 0); ?></span>
                </a>
            </div>
        </div>

        <div class="mobile-header-content">
            <button id="mobile-menu-toggle" class="mobile-icon-btn"><i class="fas fa-bars"></i></button>
            <a href="<?php echo BASE_URL; ?>" class="mobile-logo">
                <img src="assets/uploads/<?php echo htmlspecialchars($logo); ?>" alt="Logo">
            </a>
            <div class="mobile-header-right">
                <button id="mobile-search-toggle" class="mobile-icon-btn"><i class="fas fa-search"></i></button>
                <a href="<?php echo BASE_URL; ?>cart.php" class="cart-link mobile-icon-btn">
                    <i class="fas fa-shopping-cart"></i>
                    <span class="cart-count"><?php echo (isset($_SESSION['cart_p_id']) ? count($_SESSION['cart_p_id']) : 0); ?></span>
                </a>
            </div>
        </div>
        <div class="mobile-search-bar-expanded" id="mobile-search-bar-expanded">
            <input type="text" id="mobile-search-input" placeholder="Search products...">
            <button id="mobile-search-submit-btn-mobile"><i class="fas fa-search"></i></button>
        </div>
    </div>
</header>

<div class="desktop-sidebar">
    <div class="sidebar-header">
        <a href="<?php echo BASE_URL; ?>" class="sidebar-logo">
            <img src="assets/uploads/<?php echo htmlspecialchars($logo); ?>" alt="Logo">
        </a>
        <button class="sidebar-toggle-btn" id="sidebar-toggle-btn">
            <i class="fas fa-times"></i> 
        </button>
    </div>
    <ul class="sidebar-menu">
        <li><a href="<?php echo BASE_URL; ?>"><i class="fas fa-home"></i> Home</a></li>
        <li><a href="<?php echo BASE_URL; ?>dashboard.php"><i class="fas fa-tachometer-alt"></i> Dashboard</a></li>
        <li class="has-submenu">
            <a href="#"><i class="fas fa-th-list"></i> Categories <i class="fas fa-chevron-down submenu-arrow"></i></a>
            <ul class="submenu">
                <?php
                $statement_tcat = $pdo->prepare("SELECT * FROM tbl_top_category WHERE show_on_menu=1 ORDER BY tcat_id ASC");
                $statement_tcat->execute();
                $result_tcat = $statement_tcat->fetchAll(PDO::FETCH_ASSOC);
                foreach ($result_tcat as $row_tcat) {
                    ?>
                    <li class="has-submenu-level-1">
                        <a href="<?php echo BASE_URL; ?>product-category.php?id=<?php echo $row_tcat['tcat_id']; ?>&type=top-category">
                            <span class="lbl"><?php echo htmlspecialchars($row_tcat['tcat_name']); ?></span>
                            <?php
                            $statement_mcat_check = $pdo->prepare("SELECT COUNT(*) FROM tbl_mid_category WHERE tcat_id=?");
                            $statement_mcat_check->execute(array($row_tcat['tcat_id']));
                            $has_mid_categories = $statement_mcat_check->fetchColumn() > 0;
                            if ($has_mid_categories): ?>
                                <i class="fas fa-chevron-right submenu-arrow-level-1"></i>
                            <?php endif; ?>
                        </a>
                        <?php
                        $statement_mcat = $pdo->prepare("SELECT * FROM tbl_mid_category WHERE tcat_id=? ORDER BY mcat_id ASC");
                        $statement_mcat->execute(array($row_tcat['tcat_id']));
                        $result_mcat = $statement_mcat->fetchAll(PDO::FETCH_ASSOC);
                        if (!empty($result_mcat)): ?>
                            <ul class="submenu-level-2">
                                <?php foreach ($result_mcat as $row_mcat): ?>
                                    <li class="has-submenu-level-2">
                                        <a href="<?php echo BASE_URL; ?>product-category.php?id=<?php echo $row_mcat['mcat_id']; ?>&type=mid-category">
                                            <span class="lbl lbl1"><?php echo htmlspecialchars($row_mcat['mcat_name']); ?></span>
                                            <?php
                                            $statement_ecat_check = $pdo->prepare("SELECT COUNT(*) FROM tbl_end_category WHERE mcat_id=?");
                                            $statement_ecat_check->execute(array($row_mcat['mcat_id']));
                                            $has_end_categories = $statement_ecat_check->fetchColumn() > 0;
                                            if ($has_end_categories): ?>
                                                <i class="fas fa-chevron-right submenu-arrow-level-2"></i>
                                            <?php endif; ?>
                                        </a>
                                        <?php
                                        $statement_ecat = $pdo->prepare("SELECT * FROM tbl_end_category WHERE mcat_id=? ORDER BY ecat_id ASC");
                                        $statement_ecat->execute(array($row_mcat['mcat_id']));
                                        $result_ecat = $statement_ecat->fetchAll(PDO::FETCH_ASSOC);
                                        if (!empty($result_ecat)): ?>
                                            <ul class="submenu-level-3">
                                                <?php foreach ($result_ecat as $row_ecat): ?>
                                                    <li>
                                                        <a href="<?php echo BASE_URL; ?>product-category.php?id=<?php echo $row_ecat['ecat_id']; ?>&type=end-category">
                                                            <span class="lbl lbl1"><?php echo htmlspecialchars($row_ecat['ecat_name']); ?></span>
                                                        </a>
                                                    </li>
                                                <?php endforeach; ?>
                                            </ul>
                                        <?php endif; ?>
                                    </li>
                                <?php endforeach; ?>
                            </ul>
                        <?php endif; ?>
                    </li>
                    <?php
                }
                ?>
            </ul>
        </li>
        <li><a href="<?php echo BASE_URL; ?>cart.php"><i class="fas fa-shopping-cart"></i> Cart</a></li>
        <?php if ($wishlist_feature_on_off == 1): ?>
        <li><a href="<?php echo BASE_URL; ?>wishlist.php"><i class="fas fa-heart"></i> Wishlist</a></li>
        <?php endif; ?>
        <?php if ($compare_feature_on_off == 1): ?>
        <li><a href="<?php echo BASE_URL; ?>compare.php"><i class="fas fa-balance-scale"></i> Compare</a></li>
        <?php endif; ?>
        <?php if ($store_feature_on_off == 1): ?>
        <li><a href="<?php echo BASE_URL; ?>stores.php"><i class="fas fa-store"></i> Stores</a></li>
        <?php endif; ?>
        <li><a href="<?php echo BASE_URL; ?>customer-order.php"><i class="fas fa-box-open"></i> My Orders</a></li>
        <li><a href="<?php echo BASE_URL; ?>contact.php"><i class="fas fa-envelope"></i> Contact Us</a></li>
        <?php if(isset($_SESSION['customer'])): ?>
            <li><a href="<?php echo BASE_URL; ?>customer-profile-update.php"><i class="fas fa-user-circle"></i> Profile</a></li>
            <li><a href="<?php echo BASE_URL; ?>logout.php"><i class="fas fa-sign-out-alt"></i> Logout</a></li>
        <?php else: ?>
            <li><a href="<?php echo BASE_URL; ?>login.php"><i class="fas fa-sign-in-alt"></i> Login</a></li>
            <li><a href="<?php echo BASE_URL; ?>registration.php"><i class="fas fa-user-plus"></i> Register</a></li>
        <?php endif; ?>
    </ul>
</div>

<div class="mobile-menu-overlay" id="mobile-menu-overlay"></div>

<div class="content-wrapper-main">
    <div class="mobile-bottom-nav">
        <a href="<?php echo BASE_URL; ?>" class="nav-item <?php echo ($cur_page == 'index.php' || $cur_page == '') ? 'active' : ''; ?>">
            <i class="fas fa-home"></i>
            <span>Home</span>
        </a>
        <a href="<?php echo BASE_URL; ?>product-category.php?id=1&type=top-category" class="nav-item <?php echo (strpos($cur_page, 'product-category.php') !== false) ? 'active' : ''; ?>">
            <i class="fas fa-th-list"></i>
            <span>Categories</span>
        </a>
        <?php if ($store_feature_on_off == 1): ?>
        <a href="<?php echo BASE_URL; ?>stores.php" class="nav-item <?php echo ($cur_page == 'stores.php') ? 'active' : ''; ?>">
            <i class="fas fa-store"></i>
            <span>Stores</span>
        </a>
        <?php endif; ?>
        <a href="<?php echo BASE_URL; ?>cart.php" class="nav-item <?php echo ($cur_page == 'cart.php') ? 'active' : ''; ?>">
            <i class="fas fa-shopping-cart"></i>
            <span>Cart</span>
            <span class="cart-count-bottom"><?php echo (isset($_SESSION['cart_p_id']) ? count($_SESSION['cart_p_id']) : 0); ?></span>
        </a>
        <?php if(isset($_SESSION['customer'])): ?>
            <a href="<?php echo BASE_URL; ?>dashboard.php" class="nav-item <?php echo ($cur_page == 'dashboard.php' || $cur_page == 'customer-profile-update.php' || $cur_page == 'customer-order.php') ? 'active' : ''; ?>">
                <i class="fas fa-user"></i>
                <span>Profile</span>
            </a>
        <?php else: ?>
            <a href="<?php echo BASE_URL; ?>login.php" class="nav-item <?php echo ($cur_page == 'login.php' || $cur_page == 'registration.php') ? 'active' : ''; ?>">
                <i class="fas fa-sign-in-alt"></i>
                <span>Login</span>
            </a>
        <?php endif; ?>
    </div>

<script>
document.addEventListener('DOMContentLoaded', function() {
    const mobileMenuToggle = document.getElementById('mobile-menu-toggle');
    const desktopMenuBtn = document.getElementById('desktop-menu-btn'); // NEW BUTTON
    const desktopSidebar = document.querySelector('.desktop-sidebar');
    const contentWrapperMain = document.querySelector('.content-wrapper-main');
    const mobileMenuOverlay = document.getElementById('mobile-menu-overlay');
    const sidebarCloseBtn = document.getElementById('sidebar-toggle-btn');
    
    const mobileSearchToggle = document.getElementById('mobile-search-toggle');
    const mobileSearchBarExpanded = document.getElementById('mobile-search-bar-expanded');

    const desktopSearchToggle = document.getElementById('desktop-search-toggle');
    const desktopSearchBar = document.getElementById('desktop-search-bar');
    const desktopSearchInput = document.getElementById('desktop-search-input');
    const desktopSearchSubmitBtn = document.getElementById('desktop-search-submit-btn');
    const mobileSearchInput = document.getElementById('mobile-search-input');
    const mobileSearchSubmitBtnMobile = document.getElementById('mobile-search-submit-btn-mobile');

    const hasSubmenus = document.querySelectorAll('.sidebar-menu li.has-submenu, .sidebar-menu li.has-submenu-level-1, .sidebar-menu li.has-submenu-level-2');

    function openSidebar() {
        desktopSidebar.classList.add('active');
        contentWrapperMain.classList.add('sidebar-active');
        mobileMenuOverlay.style.display = 'block';
        // adjust header and content after opening
        adjustHeaderForSidebar();
    }

    function closeSidebar() {
        desktopSidebar.classList.remove('active');
        contentWrapperMain.classList.remove('sidebar-active');
        mobileMenuOverlay.style.display = 'none';
        
        hasSubmenus.forEach(item => {
            if (item.classList.contains('open')) {
                item.classList.remove('open');
                const submenu = item.querySelector('ul');
                if (submenu) {
                    submenu.style.maxHeight = '0';
                    setTimeout(() => {
                        submenu.style.display = 'none';
                    }, 300);
                }
            }
        });
        // adjust header and content after closing
        adjustHeaderForSidebar();
    }

    // Toggle sidebar on mobile menu button click
    if (mobileMenuToggle) {
        mobileMenuToggle.addEventListener('click', function() {
            if (desktopSidebar.classList.contains('active')) {
                closeSidebar();
            } else {
                openSidebar();
            }
        });
    }

    // NEW: Toggle sidebar on DESKTOP menu button click
    if (desktopMenuBtn) {
        desktopMenuBtn.addEventListener('click', function() {
            if (desktopSidebar.classList.contains('active')) {
                closeSidebar();
            } else {
                openSidebar();
            }
        });
    }

    // Close sidebar when clicking the close button inside sidebar
    if (sidebarCloseBtn) {
        sidebarCloseBtn.addEventListener('click', function() {
            closeSidebar();
        });
    }

    // Close sidebar when clicking outside
    if (mobileMenuOverlay) {
        mobileMenuOverlay.addEventListener('click', function() {
            closeSidebar();
        });
    }

    hasSubmenus.forEach(item => {
        const link = item.querySelector('a');
        const submenu = item.querySelector('ul');

        if (link && submenu) {
            link.addEventListener('click', function(e) {
                const isToggleLink = link.getAttribute('href') === '#' || 
                                     link.getAttribute('href').startsWith('javascript:') ||
                                     link.querySelector('.submenu-arrow') ||
                                     link.querySelector('.submenu-arrow-level-1') ||
                                     link.querySelector('.submenu-arrow-level-2');

                if (isToggleLink) {
                    e.preventDefault();
                    const parentUl = item.closest('ul');
                    if (parentUl) {
                        parentUl.querySelectorAll('li.has-submenu.open, li.has-submenu-level-1.open, li.has-submenu-level-2.open').forEach(otherItem => {
                            if (otherItem !== item) {
                                otherItem.classList.remove('open');
                                const otherSubmenu = otherItem.querySelector('ul');
                                if (otherSubmenu) {
                                    otherSubmenu.style.maxHeight = '0';
                                    setTimeout(() => {
                                        otherSubmenu.style.display = 'none';
                                    }, 300);
                                }
                            }
                        });
                    }

                    item.classList.toggle('open');
                    if (item.classList.contains('open')) {
                        submenu.style.display = 'block';
                        submenu.style.maxHeight = submenu.scrollHeight + 'px';
                    } else {
                        submenu.style.maxHeight = '0';
                        setTimeout(() => {
                            submenu.style.display = 'none';
                        }, 300);
                    }
                }
            });
        }
    });

    // Search Autocomplete Functionality
    let searchTimeout;
    const desktopSuggestionsDiv = document.getElementById('desktop-search-suggestions');

    if (desktopSearchInput && desktopSuggestionsDiv) {
        desktopSearchInput.addEventListener('input', function() {
            const query = this.value.trim();

            clearTimeout(searchTimeout);

            if (query.length < 2) {
                desktopSuggestionsDiv.classList.remove('active');
                desktopSuggestionsDiv.innerHTML = '';
                return;
            }

            searchTimeout = setTimeout(() => {
                fetch(`<?php echo BASE_URL; ?>search_suggestions.php?query=${encodeURIComponent(query)}`)
                    .then(response => {
                        if (!response.ok) throw new Error('Network response was not ok');
                        return response.json();
                    })
                    .then(data => {
                        if (Array.isArray(data) && data.length > 0 && !data[0].error) {
                            let html = '';
                            data.forEach(product => {
                                const imagePath = product.image ? `<?php echo BASE_URL; ?>assets/uploads/${product.image}` : '<?php echo BASE_URL; ?>assets/images/no-image.png';
                                const name = product.name.replace(/</g, '&lt;').replace(/>/g, '&gt;');
                                html += `
                                    <div class="suggestion-item" onclick="window.location.href='${product.url}';" style="cursor: pointer;">
                                        <img src="${imagePath}" alt="${name}" onerror="this.src='<?php echo BASE_URL; ?>assets/images/no-image.png'">
                                        <div class="suggestion-content">
                                            <div class="suggestion-name">${name}</div>
                                            <div class="suggestion-price">$${parseFloat(product.price).toFixed(2)}</div>
                                        </div>
                                    </div>
                                `;
                            });
                            desktopSuggestionsDiv.innerHTML = html;
                            desktopSuggestionsDiv.classList.add('active');
                        } else {
                            desktopSuggestionsDiv.innerHTML = '<div class="suggestion-item" style="color: #999; cursor: default;">No products found</div>';
                            desktopSuggestionsDiv.classList.add('active');
                        }
                    })
                    .catch(error => {
                        console.error('Search error:', error);
                        desktopSuggestionsDiv.classList.remove('active');
                    });
            }, 300); // Debounce for 300ms
        });

        // Close suggestions when clicking outside
        document.addEventListener('click', function(e) {
            if (!e.target.closest('#desktop-search-bar')) {
                desktopSuggestionsDiv.classList.remove('active');
            }
        });
    }

    function performSearch(inputElement) {
        const searchText = inputElement.value.trim();
        if (searchText) {
            window.location.href = `<?php echo BASE_URL; ?>search-result.php?search_text=${encodeURIComponent(searchText)}`;
        } else {
            inputElement.focus();
        }
    }

    if (desktopSearchToggle) {
        desktopSearchToggle.addEventListener('click', function() {
            desktopSearchBar.classList.toggle('active');
            this.classList.toggle('active');
            if (desktopSearchBar.classList.contains('active')) {
                desktopSearchInput.focus();
            } else {
                desktopSearchInput.value = '';
            }
        });
    }

    if (desktopSearchSubmitBtn) {
        desktopSearchSubmitBtn.addEventListener('click', function() {
            performSearch(desktopSearchInput);
        });
        desktopSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                performSearch(desktopSearchInput);
            }
        });
    }

    if (mobileSearchToggle) {
        mobileSearchToggle.addEventListener('click', function() {
            mobileSearchBarExpanded.classList.toggle('active');
            if (mobileSearchBarExpanded.classList.contains('active')) {
                mobileSearchInput.focus();
            } else {
                mobileSearchInput.value = '';
            }
        });
    }

    if (mobileSearchSubmitBtnMobile) {
        mobileSearchSubmitBtnMobile.addEventListener('click', function() {
            performSearch(mobileSearchInput);
        });
        mobileSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                performSearch(mobileSearchInput);
            }
        });
    }

    // Ensure sidebar is closed on larger screens (default closed) and on resize
    window.addEventListener('resize', function() {
        if (window.innerWidth >= 992) {
            desktopSidebar.classList.remove('active');
            contentWrapperMain.classList.remove('sidebar-active');
            mobileSearchBarExpanded.classList.remove('active');
        } else {
            desktopSidebar.classList.remove('active');
            contentWrapperMain.classList.remove('sidebar-active');
            desktopSearchBar.classList.remove('active');
            desktopSearchToggle.classList.remove('active');
        }
        // adjust header sizing and content spacing on resize
        adjustHeaderForSidebar();
    });

    // Initial check for desktop view to ensure sidebar is closed by default
    if (window.innerWidth >= 992) {
        desktopSidebar.classList.remove('active');
        contentWrapperMain.classList.remove('sidebar-active');
    }
    // helper to adjust header position/width and content top margin
    const mainHeader = document.querySelector('.main-header');
    function adjustHeaderForSidebar() {
        if (!mainHeader || !contentWrapperMain) return;
        // compute header height and apply as top margin to content
        const headerHeight = mainHeader.offsetHeight || 65;
        if (window.innerWidth >= 992) {
            if (desktopSidebar.classList.contains('active')) {
                mainHeader.classList.add('with-sidebar');
                mainHeader.style.left = '250px';
                mainHeader.style.width = 'calc(100% - 250px)';
            } else {
                mainHeader.classList.remove('with-sidebar');
                mainHeader.style.left = '0';
                mainHeader.style.width = '100%';
            }
            contentWrapperMain.style.marginTop = headerHeight + 'px';
        } else {
            // mobile: ensure header occupies full width and content margin
            mainHeader.classList.remove('with-sidebar');
            mainHeader.style.left = '0';
            mainHeader.style.width = '100%';
            // on mobile header is sticky; leave small margin if necessary
            contentWrapperMain.style.marginTop = headerHeight + 'px';
        }
    }

    // ensure correct layout on initial load
    adjustHeaderForSidebar();
});// Scroll Top Button Logic
var mybutton = document.getElementById("scrollTopBtn");
window.onscroll = function() {scrollFunction()};
function scrollFunction() {
  if (mybutton) {
      if (document.body.scrollTop > 20 || document.documentElement.scrollTop > 20) {
        mybutton.style.display = "block";
      } else {
        mybutton.style.display = "none";
      }
  }
}
function topFunction() {
  window.scrollTo({top: 0, behavior: 'smooth'});
}

// Pop-up Logic (One time per user)
document.addEventListener("DOMContentLoaded", function(){
    <?php if(($settings['popup_on_off']??0) == 1): ?>
    if(!localStorage.getItem('popupShown')) {
        setTimeout(function(){
            var popup = document.getElementById('promoPopup');
            if(popup) popup.style.display = "block";
        }, 2000);
    }
    <?php endif; ?>

    var closeBtn = document.getElementsByClassName("close-popup")[0];
    if(closeBtn) {
        closeBtn.onclick = function() {
            document.getElementById('promoPopup').style.display = "none";
            localStorage.setItem('popupShown', 'true');
        }
    }
});
</script>

<?php if(($settings['show_scroll_top_btn']??0) == 1): ?>
    <button onclick="topFunction()" id="scrollTopBtn" title="Go to top"><i class="fas fa-arrow-up"></i></button>
<?php endif; ?>

<?php if(($settings['popup_on_off']??0) == 1): ?>
<div id="promoPopup" class="custom-popup">
  <div class="custom-popup-content">
    <span class="close-popup">&times;</span>
    <?php if(!empty($settings['popup_photo'])): ?>
        <a href="<?php echo $settings['popup_link'] ?? '#'; ?>">
            <img src="assets/uploads/<?php echo $settings['popup_photo']; ?>" style="width:100%; border-radius:5px;">
        </a>
    <?php endif; ?>
    <div style="margin-top:15px; color:#333;">
        <?php echo $settings['popup_text'] ?? ''; ?>
    </div>
  </div>
</div>
<?php endif; ?>
</body>
</html>
