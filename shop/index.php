<?php require_once('header.php'); ?>

<?php
// -------------------------------------------------------------------------
// 1. FETCH GLOBAL SETTINGS SAFELY
// -------------------------------------------------------------------------
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC);

if (!$settings_data) { $settings_data = []; }

function get_safe_setting($data, $key, $default) {
    return (isset($data[$key]) && $data[$key] !== '') ? $data[$key] : $default;
}

// Toggles
$slider_on      = get_safe_setting($settings_data, 'home_slider_on_off', 1);
$features_on    = get_safe_setting($settings_data, 'home_features_on_off', 1);
$category_on    = get_safe_setting($settings_data, 'home_category_on_off', 1);
$featured_on    = get_safe_setting($settings_data, 'home_featured_product_on_off', 1);
$latest_on      = get_safe_setting($settings_data, 'home_latest_product_on_off', 1);
$popular_on     = get_safe_setting($settings_data, 'home_popular_product_on_off', 1);
$cta_on         = get_safe_setting($settings_data, 'home_welcome_on_off', 1);
$sticky_nav_on  = get_safe_setting($settings_data, 'home_sticky_nav_on_off', 1);
// Admin-controllable number of featured products to show (use existing setting)
$featured_product_count = (int) get_safe_setting($settings_data, 'total_featured_product_home', 8);

// Flash sale end timestamp (ms) for client-side countdown
$flash_sale_end_ts = !empty($settings_data['flash_sale_end_time']) ? (int) (strtotime($settings_data['flash_sale_end_time']) * 1000) : null;

// Flash sale end timestamp (ms since epoch) from admin-setting
$flash_sale_end_ts = !empty($settings_data['flash_sale_end_time']) ? (int) strtotime($settings_data['flash_sale_end_time']) * 1000 : 0;

// Orders
$slider_order   = get_safe_setting($settings_data, 'home_slider_order', 1);
$features_order = get_safe_setting($settings_data, 'home_features_order', 2);
$category_order = get_safe_setting($settings_data, 'home_category_order', 3);
$flash_order    = get_safe_setting($settings_data, 'home_flash_order', 4);
$featured_order = get_safe_setting($settings_data, 'home_featured_product_order', 5);
$latest_order   = get_safe_setting($settings_data, 'home_latest_product_order', 6);
$popular_order  = get_safe_setting($settings_data, 'home_popular_product_order', 7);
$sticky_nav_order = get_safe_setting($settings_data, 'home_sticky_nav_order', 8);

$homepage_layout = [];

// --- HELPER: Render Gradient Tags ---
function renderProductTags($row) {
    $html = '<div class="product-tags">';
    $count = 0;
    // Example logic using existing fields
    if($row['p_is_featured'] == 1 && $count < 2) { $html .= '<span class="p-tag tag-premium"><i class="fa fa-star"></i> Featured</span>'; $count++; }
    if($row['p_old_price'] > $row['p_current_price'] && $count < 2) { $html .= '<span class="p-tag tag-topsale"><i class="fa fa-fire"></i> Sale</span>'; $count++; }
    $html .= '</div>';
    return $html;
}
?>

<style>
/* ============================================
   MODERN HOMEPAGE REDESIGN - IMPROVED DESIGN
   ============================================ */

:root {
    --primary: #e74c3c;
    --primary-dark: #c0392b;
    --secondary: #3498db;
    --success: #27ae60;
    --warning: #ff9f43;
    --danger: #ff6b6b;
    --purple: #9b59b6;
    --teal: #1abc9c;
    --orange: #e67e22;
    --pink: #fd79a8;
    --dark: #2c3e50;
    --darker: #1a252f;
    --light: #f8f9fa;
    --gray: #95a5a6;
    --gray-light: #ecf0f1;
    --border: #dfe6e9;
    
    /* Gradient Variables */
    --gradient-primary: linear-gradient(135deg, #e74c3c 0%, #ff7979 100%);
    --gradient-secondary: linear-gradient(135deg, #3498db 0%, #2ecc71 100%);
    --gradient-warning: linear-gradient(135deg, #ff9f43 0%, #ffbe76 100%);
    --gradient-success: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%);
    --gradient-purple: linear-gradient(135deg, #9b59b6 0%, #8e44ad 100%);
    --gradient-teal: linear-gradient(135deg, #1abc9c 0%, #16a085 100%);
    --gradient-orange: linear-gradient(135deg, #e67e22 0%, #d35400 100%);
    --gradient-pink: linear-gradient(135deg, #fd79a8 0%, #e84393 100%);
    --gradient-dark: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
    --gradient-light: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
    --gradient-gold: linear-gradient(135deg, #ffd700 0%, #ffa500 100%);
    --gradient-silver: linear-gradient(135deg, #bdc3c7 0%, #95a5a6 100%);
    
    /* Card Shadows */
    --shadow-sm: 0 2px 8px rgba(0,0,0,0.05);
    --shadow-md: 0 4px 12px rgba(0,0,0,0.08);
    --shadow-lg: 0 8px 24px rgba(0,0,0,0.12);
    --shadow-xl: 0 12px 36px rgba(0,0,0,0.15);
    
    /* Transitions */
    --transition-fast: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    --transition-normal: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    --transition-slow: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
    
    /* Border Radius */
    --radius-sm: 8px;
    --radius-md: 12px;
    --radius-lg: 16px;
    --radius-xl: 24px;
    --radius-circle: 50%;
}

/* ========== BASE RESET & LAYOUT ========== */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

html, body {
    overflow-x: hidden;
    scroll-behavior: smooth;
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
}

body {
    background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
    min-height: 100vh;
    color: var(--dark);
    line-height: 1.6;
}

.main-layout-container {
    width: 100%;
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 15px;
}

.content-flow {
    margin-bottom: 30px;
    animation: fadeInUp 0.6s ease-out;
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
    overflow: hidden;
    border: 1px solid rgba(255,255,255,0.3);
}

/* ========== CUSTOM SCROLLBAR ========== */
::-webkit-scrollbar {
    width: 8px;
    height: 8px;
}

::-webkit-scrollbar-track {
    background: rgba(0,0,0,0.05);
    border-radius: 4px;
}

::-webkit-scrollbar-thumb {
    background: var(--gradient-primary);
    border-radius: 4px;
    transition: var(--transition-normal);
}

::-webkit-scrollbar-thumb:hover {
    background: var(--primary-dark);
}

/* ========== ANIMATIONS ========== */
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@keyframes fadeInDown {
    from {
        opacity: 0;
        transform: translateY(-20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@keyframes slideInLeft {
    from {
        opacity: 0;
        transform: translateX(-30px);
    }
    to {
        opacity: 1;
        transform: translateX(0);
    }
}

@keyframes slideInRight {
    from {
        opacity: 0;
        transform: translateX(30px);
    }
    to {
        opacity: 1;
        transform: translateX(0);
    }
}

@keyframes scaleIn {
    from {
        opacity: 0;
        transform: scale(0.9);
    }
    to {
        opacity: 1;
        transform: scale(1);
    }
}

@keyframes pulse {
    0% { transform: scale(1); }
    50% { transform: scale(1.05); }
    100% { transform: scale(1); }
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-10px); }
}

@keyframes shimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
}

@keyframes gradientFlow {
    0% { background-position: 0% 50%; }
    50% { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
}

@keyframes bounce {
    0%, 20%, 50%, 80%, 100% { transform: translateY(0); }
    40% { transform: translateY(-10px); }
    60% { transform: translateY(-5px); }
}

@keyframes ripple {
    0% {
        transform: scale(0);
        opacity: 1;
    }
    100% {
        transform: scale(4);
        opacity: 0;
    }
}

@keyframes cardHover {
    0% { transform: translateY(0) rotate(0); }
    100% { transform: translateY(-8px) rotate(0.5deg); }
}

@keyframes badgeGlow {
    0%, 100% { box-shadow: 0 0 10px rgba(231, 76, 60, 0.5); }
    50% { box-shadow: 0 0 20px rgba(231, 76, 60, 0.8); }
}

/* ========== HERO SLIDER ========== */
.hero-wrapper {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 20px;
    height: 400px;
    margin-bottom: 30px;
    animation: fadeInUp 0.8s ease-out;
}

@media (max-width: 768px) {
    .hero-wrapper {
        grid-template-columns: 1fr;
        height: auto;
    }
}

.hero-slider-area {
    position: relative;
    border-radius: var(--radius-lg);
    overflow: hidden;
    box-shadow: var(--shadow-lg);
}

.hero-slider-area .carousel {
    height: 100%;
    border-radius: var(--radius-lg);
}

.hero-slider-area .carousel-inner {
    height: 100%;
    border-radius: var(--radius-lg);
}

.hero-slider-area .item {
    height: 100%;
    position: relative;
}

.hero-slider-area img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.8s ease;
}

.hero-slider-area .item:hover img {
    transform: scale(1.05);
}

.slider-text {
    position: absolute;
    padding: 30px;
    color: white;
    text-shadow: 2px 2px 8px rgba(0,0,0,0.3);
    max-width: 60%;
    z-index: 10;
    animation: fadeInUp 0.8s ease-out 0.3s both;
}

.slider-text.left {
    left: 30px;
    top: 50%;
    transform: translateY(-50%);
    text-align: left;
}

.slider-text.center {
    left: 50%;
    top: 50%;
    transform: translate(-50%, -50%);
    text-align: center;
}

.slider-text.right {
    right: 30px;
    top: 50%;
    transform: translateY(-50%);
    text-align: right;
}

.slider-text h2 {
    font-size: 2.5em;
    font-weight: 800;
    margin-bottom: 20px;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    animation: textShine 3s ease-in-out infinite alternate;
}

@keyframes textShine {
    0% { background-position: 0% 50%; }
    100% { background-position: 100% 50%; }
}

.slider-text .btn {
    background: var(--gradient-primary);
    border: none;
    padding: 12px 30px;
    border-radius: var(--radius-md);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    transition: var(--transition-normal);
    position: relative;
    overflow: hidden;
    z-index: 1;
}

.slider-text .btn::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
    transition: left 0.7s ease;
    z-index: -1;
}

.slider-text .btn:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 25px rgba(231, 76, 60, 0.4);
}

.slider-text .btn:hover::before {
    left: 100%;
}

.hero-promo-area {
    border-radius: var(--radius-lg);
    overflow: hidden;
    box-shadow: var(--shadow-lg);
    position: relative;
}

.hero-promo-area > div {
    height: 100%;
    position: relative;
    overflow: hidden;
}

.hero-promo-area > div::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(135deg, rgba(0,0,0,0.6) 0%, transparent 50%);
}

.hero-promo-area h4 {
    font-size: 1.5em;
    font-weight: 700;
    position: relative;
    z-index: 2;
}

.carousel-indicators {
    bottom: 20px;
}

.carousel-indicators li {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: rgba(255,255,255,0.5);
    border: 2px solid transparent;
    transition: var(--transition-fast);
}

.carousel-indicators li.active {
    background: var(--primary);
    transform: scale(1.3);
    border-color: white;
}

/* ========== FEATURES ICONS ROW ========== */
.features-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: 15px;
    padding: 25px;
    background: var(--gradient-light);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
    animation: slideInUp 0.6s ease-out;
}

.feature-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-decoration: none;
    color: var(--dark);
    padding: 20px 15px;
    border-radius: var(--radius-md);
    transition: var(--transition-normal);
    background: white;
    position: relative;
    overflow: hidden;
    border: 1px solid var(--border);
}

.feature-item::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--gradient-primary);
    transform: scaleX(0);
    transform-origin: left;
    transition: transform 0.3s ease;
}

.feature-item:hover {
    transform: translateY(-5px);
    box-shadow: var(--shadow-lg);
    border-color: var(--primary);
}

.feature-item:hover::before {
    transform: scaleX(1);
}

.feature-icon-circle {
    width: 60px;
    height: 60px;
    border-radius: var(--radius-circle);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    margin-bottom: 15px;
    background: var(--gradient-light);
    color: var(--primary);
    transition: var(--transition-normal);
    border: 2px solid transparent;
}

.feature-item:hover .feature-icon-circle {
    background: var(--gradient-primary);
    color: white;
    transform: rotateY(180deg) scale(1.1);
}

.feature-label {
    font-size: 14px;
    font-weight: 600;
    text-align: center;
    transition: var(--transition-fast);
}

.feature-item:hover .feature-label {
    color: var(--primary);
}

/* ========== CATEGORIES SECTION ========== */
.content-flow[style*="background-color"] {
    background: var(--gradient-light) !important;
    border: 1px solid rgba(255,255,255,0.3);
}

.section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 25px 30px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    position: relative;
    overflow: hidden;
}

.section-header::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: linear-gradient(45deg, transparent 30%, rgba(255,255,255,0.1) 50%, transparent 70%);
    animation: shimmer 3s infinite;
}

.section-title {
    font-size: 1.8em;
    font-weight: 700;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    position: relative;
    z-index: 1;
}

.text-shine {
    background: linear-gradient(45deg, #ffd700, #ffa500, #ffd700);
    background-size: 200% auto;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    animation: textShine 2s linear infinite;
}

.view-all-btn,
.view-all-btn[class*="btn"] {
    background: rgba(255,255,255,0.2);
    color: white;
    border: 1px solid rgba(255,255,255,0.3);
    padding: 8px 20px;
    border-radius: 20px;
    font-weight: 600;
    text-decoration: none;
    transition: var(--transition-normal);
    backdrop-filter: blur(10px);
    position: relative;
    z-index: 1;
}

.view-all-btn:hover {
    background: rgba(255,255,255,0.3);
    transform: translateX(5px);
    color: white;
}

.horizontal-scroll-wrapper {
    display: flex;
    gap: 20px;
    padding: 25px 30px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: thin;
}

.horizontal-scroll-wrapper::-webkit-scrollbar {
    height: 6px;
}

.horizontal-scroll-wrapper::-webkit-scrollbar-thumb {
    background: var(--gradient-primary);
    border-radius: 3px;
}

.cat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-decoration: none;
    color: var(--dark);
    transition: var(--transition-normal);
    flex-shrink: 0;
    width: 140px;
}

.cat-item.round-style {
    text-align: center;
}

.cat-img-box {
    width: 120px;
    height: 120px;
    border-radius: var(--radius-circle);
    overflow: hidden;
    margin-bottom: 15px;
    border: 4px solid transparent;
    background: var(--gradient-light);
    transition: var(--transition-normal);
    position: relative;
    box-shadow: var(--shadow-md);
}

.cat-img-box::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--gradient-primary);
    opacity: 0;
    transition: opacity 0.3s ease;
    border-radius: inherit;
    z-index: 1;
}

.cat-img-box img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: var(--transition-normal);
    position: relative;
    z-index: 2;
}

.cat-item:hover .cat-img-box {
    border-color: var(--primary);
    transform: rotate(15deg) scale(1.05);
    box-shadow: 0 10px 25px rgba(231, 76, 60, 0.3);
}

.cat-item:hover .cat-img-box::before {
    opacity: 0.2;
}

.cat-item:hover .cat-img-box img {
    transform: scale(1.1);
}

.cat-name {
    font-weight: 600;
    font-size: 14px;
    text-align: center;
    transition: var(--transition-fast);
    color: var(--dark);
}

.cat-item:hover .cat-name {
    color: var(--primary);
    transform: translateY(2px);
}

/* ========== MODERN PRODUCT CARD DESIGN ========== */
.grid-5-col {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 14px;
    padding: 16px;
    animation: fadeIn 0.6s ease-out;
}

@media (max-width: 1200px) {
    .grid-5-col {
        grid-template-columns: repeat(4, 1fr);
    }
}

@media (max-width: 992px) {
    .grid-5-col {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (max-width: 768px) {
    .grid-5-col {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 480px) {
    .grid-5-col {
        grid-template-columns: 1fr;
    }
}

.product-card {
    background: white;
    border-radius: calc(var(--radius-md));
    overflow: hidden;
    box-shadow: var(--shadow-sm);
    transition: var(--transition-normal);
    position: relative;
    animation: scaleIn 0.4s ease-out;
    border: 1px solid rgba(0,0,0,0.05);
}

.product-card:hover {
    transform: translateY(-8px);
    box-shadow: var(--shadow-xl);
    animation: cardHover 0.6s ease forwards;
}

.product-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--gradient-primary);
    z-index: 2;
    transform: scaleX(0);
    transform-origin: left;
    transition: transform 0.5s ease;
}

.product-card:hover::before {
    transform: scaleX(1);
}

.discount-badge {
    position: absolute;
    top: 15px;
    right: 15px;
    background: var(--gradient-primary);
    color: white;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 700;
    z-index: 3;
    animation: badgeGlow 2s infinite;
    box-shadow: 0 4px 12px rgba(231, 76, 60, 0.3);
}

/* Product Tags */
.product-tags {
    position: absolute;
    top: 15px;
    left: 15px;
    display: flex;
    flex-direction: column;
    gap: 5px;
    z-index: 3;
}

.tag {
    padding: 4px 10px;
    border-radius: 12px;
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    color: white;
    animation: fadeInDown 0.5s ease-out;
}

.tag-hot {
    background: var(--gradient-primary);
}

.tag-new {
    background: var(--gradient-success);
}

.tag-official {
    background: var(--gradient-purple);
}

.tag-sale {
    background: var(--gradient-warning);
}

.p-img-box {
    position: relative;
    padding-bottom: 86%;
    overflow: hidden;
    background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
}

.p-img-box img {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: var(--transition-slow);
    transform-origin: center;
}

.product-card:hover .p-img-box img {
    transform: scale(1.1) rotate(1deg);
}

.hover-btns-container {
    position: absolute;
    bottom: 20px;
    left: 50%;
    transform: translateX(-50%) translateY(20px);
    display: flex;
    gap: 10px;
    opacity: 0;
    transition: var(--transition-normal);
    z-index: 3;
}

.product-card:hover .hover-btns-container {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

.btn-action {
    width: 40px;
    height: 40px;
    border-radius: var(--radius-circle);
    background: white;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--dark);
    font-size: 16px;
    cursor: pointer;
    transition: var(--transition-fast);
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    text-decoration: none;
    position: relative;
    overflow: hidden;
}

.btn-action::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    background: var(--gradient-primary);
    border-radius: 50%;
    transform: translate(-50%, -50%);
    transition: width 0.6s, height 0.6s;
    z-index: -1;
}

.btn-action:hover {
    color: white;
    transform: translateY(-2px) scale(1.1);
    box-shadow: 0 6px 20px rgba(231, 76, 60, 0.3);
}

.btn-action:hover::after {
    width: 120px;
    height: 120px;
}

.btn-action:nth-child(1)::after {
    background: var(--gradient-primary);
}

.btn-action:nth-child(2)::after {
    background: var(--gradient-pink);
}

.p-details {
    padding: 12px;
    position: relative;
    background: white;
}

.p-title {
    margin-bottom: 12px;
}

.p-title a {
    font-size: 15px;
    font-weight: 600;
    color: var(--dark);
    text-decoration: none;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    transition: var(--transition-fast);
}

.p-title a:hover {
    color: var(--primary);
}

.meta-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
}

.stock-status {
    font-size: 11px;
    font-weight: 600;
    padding: 4px 10px;
    border-radius: 12px;
    display: inline-flex;
    align-items: center;
    gap: 5px;
}

.stock-in {
    background: rgba(39, 174, 96, 0.1);
    color: var(--success);
}

.stock-out {
    background: rgba(231, 76, 60, 0.1);
    color: var(--primary);
}

.review-stars {
    color: var(--warning);
    font-size: 12px;
}

.price-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 15px;
}

.p-price {
    font-size: 18px;
    font-weight: 800;
    color: var(--primary);
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.p-old {
    font-size: 14px;
    color: var(--gray);
    text-decoration: line-through;
}

/* ========== FLASH SALE SECTION ========== */
.content-flow:has(#flash-timer-display) {
    background: linear-gradient(135deg, #fff8e1 0%, #ffe0b2 100%) !important;
    border: 2px solid #ff9800;
    animation: pulse 3s infinite;
}

#flash-timer-display {
    font-family: 'Courier New', monospace;
    font-weight: 800;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    animation: pulse 1s infinite;
    font-size: 16px;
}

/* ========== STICKY NAVIGATION ========== */
.content-flow + div[style*="position: sticky"] {
    position: sticky;
    top: 0;
    z-index: 1000;
    background: rgba(255,255,255,0.95);
    backdrop-filter: blur(10px);
    padding: 15px 0;
    border-bottom: 1px solid var(--border);
    box-shadow: 0 4px 20px rgba(0,0,0,0.1);
    animation: slideInDown 0.5s ease-out;
}

@keyframes slideInDown {
    from {
        opacity: 0;
        transform: translateY(-20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

/* ========== DYNAMIC PRODUCTS GRID ========== */
#dynamic-products-grid {
    animation: fadeIn 0.8s ease-out;
}

/* ========== LIVE FEED ANIMATION ========== */
@keyframes cardFlip {
    0% {
        transform: rotateY(0deg);
        opacity: 1;
    }
    50% {
        transform: rotateY(90deg);
        opacity: 0.5;
    }
    100% {
        transform: rotateY(0deg);
        opacity: 1;
    }
}

/* ========== BUTTON STYLES ========== */
.btn {
    border: none;
    border-radius: var(--radius-md);
    padding: 10px 20px;
    font-weight: 600;
    transition: var(--transition-normal);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    position: relative;
    overflow: hidden;
}

.btn::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 0;
    height: 0;
    background: rgba(255,255,255,0.2);
    border-radius: 50%;
    transform: translate(-50%, -50%);
    transition: width 0.6s, height 0.6s;
}

.btn:hover::before {
    width: 300px;
    height: 300px;
}

.btn-primary {
    background: var(--gradient-primary);
    color: white;
    box-shadow: 0 4px 15px rgba(231, 76, 60, 0.3);
}

.btn-primary:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 25px rgba(231, 76, 60, 0.4);
}

.btn-default {
    background: var(--gradient-light);
    color: var(--dark);
    border: 1px solid var(--border);
}

.btn-default:hover {
    background: white;
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
}

.btn-sm {
    padding: 8px 16px;
    font-size: 13px;
}

.btn-xs {
    padding: 6px 12px;
    font-size: 12px;
}

/* ========== DESKTOP FOOTER ========== */
.desktop-footer {
    background: var(--gradient-dark);
    color: white;
    padding: 50px 0;
    margin-top: 50px;
    border-top: 3px solid var(--primary);
}

.desktop-footer .container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 20px;
}

.desktop-footer .row {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 40px;
}

@media (max-width: 992px) {
    .desktop-footer .row {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 576px) {
    .desktop-footer .row {
        grid-template-columns: 1fr;
    }
}

.desktop-footer h4 {
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 20px;
    color: white;
    position: relative;
    padding-bottom: 10px;
}

.desktop-footer h4::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 40px;
    height: 3px;
    background: var(--gradient-primary);
    border-radius: 2px;
}

.desktop-footer a {
    display: block;
    color: rgba(255,255,255,0.8);
    text-decoration: none;
    margin-bottom: 12px;
    transition: var(--transition-fast);
    font-size: 14px;
}

.desktop-footer a:hover {
    color: white;
    transform: translateX(5px);
}

.desktop-footer p {
    color: rgba(255,255,255,0.8);
    font-size: 14px;
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    gap: 10px;
}

.desktop-footer i {
    color: var(--primary);
    width: 20px;
}

/* ========== UTILITY CLASSES ========== */
.text-shine {
    background: linear-gradient(45deg, #ffd700, #ffa500, #ffd700);
    background-size: 200% auto;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    animation: textShine 2s linear infinite;
}

.fade-in {
    animation: fadeIn 0.6s ease-out;
}

.slide-in-left {
    animation: slideInLeft 0.6s ease-out;
}

.slide-in-right {
    animation: slideInRight 0.6s ease-out;
}

.scale-in {
    animation: scaleIn 0.6s ease-out;
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

/* ========== LOADING SKELETONS ========== */
.skeleton {
    background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
    background-size: 200% 100%;
    animation: shimmer 1.5s infinite;
    border-radius: 4px;
}

.skeleton-title {
    height: 20px;
    width: 70%;
    margin-bottom: 10px;
}

.skeleton-price {
    height: 24px;
    width: 40%;
}

.skeleton-image {
    padding-bottom: 100%;
    width: 100%;
}

/* ========== RESPONSIVE ADJUSTMENTS ========== */
@media (max-width: 768px) {
    .hero-wrapper {
        grid-template-columns: 1fr;
        height: auto;
    }
    
    .hero-slider-area,
    .hero-promo-area {
        height: 300px;
    }
    
    .slider-text {
        max-width: 90%;
        padding: 20px;
    }
    
    .slider-text h2 {
        font-size: 1.8em;
    }
    
    .features-row {
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
        padding: 15px;
    }
    
    .section-header {
        padding: 20px;
    }
    
    .section-title {
        font-size: 1.4em;
    }
    
    .horizontal-scroll-wrapper {
        padding: 20px;
    }
    
    .cat-item {
        width: 120px;
    }
    
    .cat-img-box {
        width: 100px;
        height: 100px;
    }
}

@media (max-width: 480px) {
    .features-row {
        grid-template-columns: repeat(2, 1fr);
    }
    
    .slider-text h2 {
        font-size: 1.5em;
    }
    
    .slider-text .btn {
        padding: 10px 20px;
        font-size: 14px;
    }
    
    .grid-5-col {
        grid-template-columns: repeat(2, 1fr);
        gap: 15px;
        padding: 15px;
    }
    
    .product-card {
        border-radius: var(--radius-md);
    }
    
    .p-details {
        padding: 15px;
    }
    
    .p-title a {
        font-size: 14px;
    }
    
    .p-price {
        font-size: 16px;
    }
}

/* ========== PRINT STYLES ========== */
@media print {
    .product-card,
    .content-flow,
    .hero-wrapper {
        break-inside: avoid;
        box-shadow: none;
        border: 1px solid #ddd;
    }
    
    .btn-action,
    .hover-btns-container,
    .discount-badge,
    .tag {
        display: none;
    }
}

/* ========== DARK MODE SUPPORT ========== */
@media (prefers-color-scheme: dark) {
    :root {
        --light: #1a1a1a;
        --dark: #ffffff;
        --gray: #b0b0b0;
        --border: #333333;
        --gradient-light: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
    }
    
    body {
        background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
        color: white;
    }
    
    .content-flow {
        background: var(--gradient-dark);
        border-color: #333;
    }
    
    .product-card {
        background: #2d3748;
        border-color: #4a5568;
    }
    
    .p-details {
        background: #2d3748;
    }
    
    .p-title a {
        color: white;
    }
    
    .feature-item {
        background: #2d3748;
        border-color: #4a5568;
        color: white;
    }
    
    .desktop-footer {
        background: var(--gradient-dark);
    }
}
</style>

<div class="main-layout-container">

<?php
// =========================================================================
//  SECTION 1: HERO SLIDER
// =========================================================================
if ($slider_on == 1) {
    ob_start(); 
    ?>
    <div class="content-flow">
        <div class="hero-wrapper">
            <div class="hero-slider-area">
                <div id="homeSlider" class="carousel slide" data-ride="carousel" style="height:100%;">
                    <ol class="carousel-indicators">
                        <?php
                        $stmt = $pdo->prepare("SELECT * FROM tbl_slider ORDER BY id ASC");
                        $stmt->execute();
                        $count = $stmt->rowCount();
                        for($i=0; $i<$count; $i++) { echo '<li data-target="#homeSlider" data-slide-to="'.$i.'" class="'.($i==0?'active':'').'"></li>'; }
                        ?>
                    </ol>
                    <div class="carousel-inner" style="height:100%;">
                        <?php
                        $stmt->execute();
                        $sliders = $stmt->fetchAll(PDO::FETCH_ASSOC);
                        $i=0;
                        foreach($sliders as $slide):
                        ?>
                        <div class="item <?php echo ($i==0)?'active':''; ?>" style="height:100%;">
                            <img src="assets/uploads/<?php echo htmlspecialchars($slide['photo']); ?>" alt="Slider" style="width:100%; height:100%; object-fit:cover;">
                            <?php if(!empty($slide['heading'])): ?>
                            <div class="slider-text <?php echo $slide['position']; ?>">
                                <h2 class="text-shine"><?php echo htmlspecialchars($slide['heading']); ?></h2>
                                <?php if(!empty($slide['button_text'])): ?>
                                    <a href="<?php echo htmlspecialchars($slide['button_url']); ?>" class="btn btn-primary"><?php echo htmlspecialchars($slide['button_text']); ?></a>
                                <?php endif; ?>
                            </div>
                            <?php endif; ?>
                        </div>
                        <?php $i++; endforeach; ?>
                    </div>
                </div>
            </div>
            
            <div class="hero-promo-area">
                 <div style="flex:1; background: url('assets/uploads/<?php echo $settings_data['slider_side_banner_img'] ?? 'default.jpg'; ?>') center/cover; border-radius:12px; position:relative; overflow:hidden;">
                    <div style="position:absolute; bottom:0; left:0; width:100%; background: linear-gradient(to top, rgba(0,0,0,0.8), transparent); padding:15px; color:#fff;">
                        <h4 style="margin:0; font-size:16px;"><?php echo $settings_data['slider_side_banner_text'] ?? 'Special Offer'; ?></h4>
                    </div>
                 </div>
            </div>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $slider_order, 'html' => ob_get_clean()];
}

// =========================================================================
//  SECTION 2: FEATURES ICONS
// =========================================================================
if ($features_on == 1) {
    ob_start();
    ?>
    <div class="content-flow">
        <div class="features-row">
            <?php 
            try {
                $stmt = $pdo->prepare("SELECT * FROM tbl_features ORDER BY order_no ASC");
                $stmt->execute();
                foreach($stmt->fetchAll(PDO::FETCH_ASSOC) as $f): ?>
                <a href="<?php echo $f['link']; ?>" class="feature-item">
                    <div class="feature-icon-circle"><i class="fa <?php echo $f['icon']; ?>"></i></div>
                    <span class="feature-label"><?php echo $f['title']; ?></span>
                </a>
                <?php endforeach;
            } catch (Exception $e) { }
            ?>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $features_order, 'html' => ob_get_clean()];
}
// =========================================================================
//  SECTION 3: BROWSE CATEGORIES (Horizontal Scroll + Round)
// =========================================================================
if ($category_on == 1) {
    ob_start();
    ?>
    <div class="content-flow" style="background-color: <?php echo $settings_data['bg_color_categories'] ?? '#fff'; ?>;">
        <div class="section-header">
            <h3 class="section-title">Browse Categories</h3>
            <a href="#" class="view-all-btn">View All</a>
        </div>
        <div class="horizontal-scroll-wrapper" style="padding: 0 20px 20px 20px;">
            <?php
            $stmt = $pdo->prepare("SELECT * FROM tbl_top_category WHERE show_on_menu=1 ORDER BY tcat_id ASC");
            $stmt->execute();
            foreach($stmt->fetchAll(PDO::FETCH_ASSOC) as $cat): 
                $cat_img = (isset($cat['photo']) && !empty($cat['photo'])) ? 'assets/uploads/'.$cat['photo'] : '';
            ?>
                <a href="product-category.php?id=<?php echo $cat['tcat_id']; ?>&type=top-category" class="cat-item round-style">
                    <div class="cat-img-box">
                        <?php if($cat_img): ?>
                            <img src="<?php echo $cat_img; ?>" alt="<?php echo $cat['tcat_name']; ?>">
                        <?php else: ?>
                            <img src="assets/uploads/default_cat.jpg" alt="Default">
                        <?php endif; ?>
                    </div>
                    <span class="cat-name"><?php echo $cat['tcat_name']; ?></span>
                </a>
            <?php endforeach; ?>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $category_order, 'html' => ob_get_clean()];
}

// =========================================================================
//  HELPER: REUSABLE PRODUCT CARD GENERATOR
// =========================================================================


    function renderProductCard($row, $currencySymbol) {
        global $pdo;
        // Get product rating
        $rating = 0;
        $review_count = 0;
        $stmt = $pdo->prepare("SELECT AVG(rating) as avg_rating, COUNT(*) as total FROM tbl_review WHERE product_id = ? AND status = 'Approved'");
        $stmt->execute([$row['p_id']]);
        $rating_data = $stmt->fetch();
        if ($rating_data) {
            $rating = $rating_data['avg_rating'] !== null ? round($rating_data['avg_rating']) : 0;
            $review_count = $rating_data['total'];
        }
        
        // Generate stars HTML
        $stars_html = '';
        for ($i = 1; $i <= 5; $i++) {
            if ($i <= $rating) {
                $stars_html .= '<i class="fa fa-star"></i>';
            } else {
                $stars_html .= '<i class="fa fa-star-o"></i>';
            }
        }

        // Calculate discount
        $discount = 0;
        // Discount badge
        $discount_html = '';
        if ($row['p_old_price'] > 0 && $row['p_old_price'] > $row['p_current_price']) {
            $discount = round((($row['p_old_price'] - $row['p_current_price']) / $row['p_old_price']) * 100);
            $discount_html = '<div class="discount-badge">-' . $discount . '%</div>';
        }
        
        // Product tags
        $tags_html = '<div class="product-tags">';
        if (isset($row['p_is_top_sale']) && $row['p_is_top_sale']) {
            $tags_html .= '<span class="tag tag-hot">HOT</span>';
        }
        if (isset($row['p_is_featured']) && $row['p_is_featured']) {
            $tags_html .= '<span class="tag tag-new">NEW</span>';
        }
        if (isset($row['p_is_official']) && $row['p_is_official']) {
            $tags_html .= '<span class="tag tag-official">OFFICIAL</span>';
        }
        if ($discount > 30) {
            $tags_html .= '<span class="tag tag-sale">SALE</span>';
        }
        if (isset($row['p_qty']) && $row['p_qty'] < 10 && $row['p_qty'] > 0) {
            $tags_html .= '<span class="tag tag-limited">LIMITED</span>';
        }
        $tags_html .= '</div>';

        // Stock status
        $stock_status = '';
        $stock_progress = '';
        if ($row['p_qty'] > 0) {
            $stock_status = '<span class="stock-status stock-in"><i class="fa fa-check-circle"></i> In Stock</span>';
            if ($row['p_qty'] < 20) {
                $percentage = ($row['p_qty'] / 20) * 100;
                $stock_progress = '
                <div class="stock-progress">
                    <div class="stock-progress-bar" style="width: ' . $percentage . '%"></div>
                </div>
                <div class="stock-progress-text">Only ' . $row['p_qty'] . ' left</div>';
            }
        } else {
            $stock_status = '<span class="stock-status stock-out"><i class="fa fa-times-circle"></i> Out of Stock</span>';
        }
        
        // Sold count (simulated based on views or random)
        $sold_count = rand(50, 500);
        $sold_html = $sold_count > 100 ? 
            '<div class="sold-count"><i class="fa fa-fire"></i> ' . number_format($sold_count) . ' sold</div>' : '';
        
        // Old price
        $old_price_html = ($row['p_old_price'] > 0) ? 
            '<span class="p-old">' . $currencySymbol . number_format($row['p_old_price']) . '</span>' : '';
        
        // Wishlist status
        $wishlist_icon = isset($_SESSION['customer']) ? 
            '<a href="#" class="btn-action" onclick="addToWishlist(' . $row['p_id'] . '); return false;" title="Add to Wishlist"><i class="far fa-heart"></i></a>' : 
            '<a href="login.php" class="btn-action" title="Login for Wishlist"><i class="far fa-heart"></i></a>';
        
        return '
        <div class="product-card" data-product-id="' . $row['p_id'] . '">
            ' . $discount_html . '
            ' . $tags_html . '
            
            <div class="p-img-box">
                <a href="product.php?id=' . $row['p_id'] . '">
                    <img src="assets/uploads/' . htmlspecialchars($row['p_featured_photo']) . '" alt="' . htmlspecialchars($row['p_name']) . '">
                </a>
                <div class="hover-btns-container">
                    <a href="product.php?id=' . $row['p_id'] . '" class="btn-action" title="View Product"><i class="fa fa-eye"></i></a>
                    <a href="javascript:void(0);" onclick="addToCart(' . $row['p_id'] . ')" class="btn-action" title="Add to Cart"><i class="fa fa-shopping-cart"></i></a>
                    ' . $wishlist_icon . '
                </div>
            </div>
            
            <div class="p-details">
                <div class="p-title">
                    <a href="product.php?id=' . $row['p_id'] . '">' . htmlspecialchars($row['p_name']) . '</a>
                </div>
                
                <div class="meta-row">
                    <div class="review-stars">
                        ' . $stars_html . '
                        <span class="review-count">(' . $review_count . ')</span>
                    </div>
                    ' . $sold_html . '
                </div>
                
                <div class="meta-row">
                    ' . $stock_status . '
                </div>
                
                ' . $stock_progress . '
                
                <div class="price-row">
                    <span class="p-price">' . $currencySymbol . number_format($row['p_current_price']) . '</span>
                    ' . $old_price_html . '
                </div>
            </div>
        </div>';
    }

// =========================================================================
//  SECTION: FLASH SALE
// =========================================================================
if ($flash_order) { 
    ob_start();
    $stmt = $pdo->prepare("SELECT * FROM tbl_product WHERE p_old_price > p_current_price AND p_is_active=1 LIMIT 10");
    $stmt->execute();
    $flash_products = $stmt->fetchAll(PDO::FETCH_ASSOC);

    if(count($flash_products) > 0):
    ?>
    <div class="content-flow">
        <div style="padding: 20px 30px; border-bottom: 1px solid #f0f0f0; display:flex; justify-content:space-between; align-items:center;">
            <h3 class="text-shine" style="margin:0; font-size:20px; font-weight:700;">
                <i class="fa fa-bolt" style="color:#ff4757;"></i> Flash Sale
            </h3>
            <div style="font-weight:bold; color:#ff4757; background:#fff0f1; padding:5px 12px; border-radius:20px; font-size:13px; border:1px solid #ffdbde;">
                <span id="flash-timer-display">00:00:00</span>
            </div>
        </div>
        <div class="grid-5-col">
            <?php foreach ($flash_products as $row) { echo renderProductCard($row, LANG_VALUE_1); } ?>
        </div>
    </div>
    <?php endif; 
    // Inject flash sale countdown JS if an end time is set
    ob_start();
    ?>
    <script>
    (function(){
        var endTs = <?php echo $flash_sale_end_ts ? $flash_sale_end_ts : 'null'; ?>;
        if(!endTs) return;
        var display = document.getElementById('flash-timer-display');
        if(!display) return;
        function updateTimer(){
            var now = Date.now();
            var diff = endTs - now;
            if(diff <= 0){ display.textContent = '00:00:00'; clearInterval(timer); return; }
            var s = Math.floor(diff/1000);
            var h = Math.floor(s/3600);
            var m = Math.floor((s%3600)/60);
            var sec = s%60;
            display.textContent = (h<10?'0':'')+h+':' + (m<10?'0':'')+m+':' + (sec<10?'0':'')+sec;
        }
        updateTimer();
        var timer = setInterval(updateTimer, 1000);
    })();
    </script>
    <?php
    $script_html = ob_get_clean();
    $homepage_layout[] = ['order' => $flash_order, 'html' => ob_get_clean() . $script_html];
}

// =========================================================================
//  SECTION: FEATURED PRODUCTS
// =========================================================================
if ($featured_on == 1) {
    ob_start();
    ?>
    <div class="content-flow" style="background: <?php echo $settings_data['bg_color_featured_products'] ?? ($settings_data['bg_color_latest_products'] ?? '#ffffff'); ?>;">
        <div class="section-header" style="padding: 20px 30px; border-bottom: 1px solid #eee;">
            <h3 class="section-title text-shine" style="margin:0;"><?php echo get_safe_setting($settings_data, 'featured_product_title', 'Featured Products'); ?></h3>
        </div>
        <div class="grid-5-col">
            <?php
            $limit = get_safe_setting($settings_data, 'total_featured_product_home', 8);
            $stmt = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_featured=? AND p_is_active=? LIMIT ".$limit);
            $stmt->execute(array(1, 1));
            foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row) { echo renderProductCard($row, LANG_VALUE_1); } 
            ?>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $featured_order, 'html' => ob_get_clean()];
}

// =========================================================================
//  SECTION: LATEST PRODUCTS (Original Horizontal Scroll Preserved)
// =========================================================================
if ($latest_on == 1) {
    ob_start();
    ?>
    <div class="content-flow" style="background: <?php echo $settings_data['bg_color_latest_products'] ?? '#ffffff'; ?>;">
        <div class="section-header" style="padding: 20px; display:flex; justify-content:space-between;">
            <h3 class="section-title"><?php echo get_safe_setting($settings_data, 'latest_product_title', 'Latest Products'); ?></h3>
            <a href="search-result.php?type=latest" class="btn btn-default btn-xs">View All</a>
        </div>
        <div class="horizontal-scroll-wrapper" style="padding: 0 20px 20px 20px; display: flex; overflow-x: auto; gap: 15px;">
            <?php
            $limit = get_safe_setting($settings_data, 'total_latest_product_home', 8);
            $stmt = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_active=? ORDER BY p_id DESC LIMIT 20");
            $stmt->execute(array(1));
            foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row): ?>
                 <div class="product-card" style="min-width: 200px;">
                    <div class="p-img-box">
                        <a href="product.php?id=<?php echo $row['p_id']; ?>">
                            <img src="assets/uploads/<?php echo htmlspecialchars($row['p_featured_photo']); ?>" alt="">
                        </a>
                        <div class="hover-btns-container">
                             <a href="product.php?id=<?php echo $row['p_id']; ?>" class="btn-action"><i class="fa fa-shopping-cart"></i></a>
                             <a href="#" class="btn-action" onclick="addToWishlist(<?php echo $row['p_id']; ?>); return false;"><i class="fa fa-heart"></i></a>
                        </div>
                    </div>
                    <div class="p-details">
                        <div class="p-title"><a href="product.php?id=<?php echo $row['p_id']; ?>"><?php echo htmlspecialchars($row['p_name']); ?></a></div>
                        <div class="price-row">
                            <span class="p-price"><?php echo LANG_VALUE_1; ?><?php echo number_format($row['p_current_price']); ?></span>
                        </div>
                    </div>
                </div>
            <?php endforeach; ?>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $latest_order, 'html' => ob_get_clean()];
}

// =========================================================================
//  SECTION: STICKY NAV & DYNAMIC FEED
// =========================================================================
if ($sticky_nav_on == 1) {
    ob_start();
    ?>
    <div style="position: sticky; top: 60px; z-index: 100; background: rgba(255,255,255,0.95); backdrop-filter: blur(5px); padding: 15px 0; margin-top:30px; border-top:1px solid #eee; border-bottom:1px solid #eee;">
        <div style="display:flex; gap:10px; overflow-x:auto; padding:0 15px;">
            <button class="btn btn-sm btn-primary">For You</button>
            <button class="btn btn-sm btn-default">Top Sale</button>
            <button class="btn btn-sm btn-default">New Arrivals</button>
        </div>
    </div>

    <div class="content-flow" style="background:transparent; border:none; box-shadow:none;">
        <div class="grid-5-col" id="dynamic-products-grid">
            <?php
            $sql_rand = defined('SQL_RAND') ? SQL_RAND : 'RAND()';
            $stmt = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_active=1 ORDER BY {$sql_rand} LIMIT 10");
            $stmt->execute();
            foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row) { echo renderProductCard($row, LANG_VALUE_1); } 
            ?>
        </div>
    </div>
    <?php
    $homepage_layout[] = ['order' => $sticky_nav_order, 'html' => ob_get_clean()];
}

// --- RENDER LAYOUT ---
usort($homepage_layout, function($a, $b) { return $a['order'] <=> $b['order']; });
foreach ($homepage_layout as $block) { echo $block['html']; }
?>

</div> <?php if(($settings_data['extra_footer_section_enable']??0) == 1): ?>
<div class="desktop-footer">
    <div class="container">
        <div class="row">
            <div class="col-md-3">
                <h4>Information</h4>
                <a href="about.php">About Us</a>
                <a href="contact.php">Contact Us</a>
                <a href="privacy.php">Privacy Policy</a>
                <a href="terms.php">Terms & Conditions</a>
            </div>
            <div class="col-md-3">
                <h4>My Account</h4>
                <a href="dashboard.php">Dashboard</a>
                <a href="customer-order.php">My Orders</a>
                <a href="cart.php">Shopping Cart</a>
                <a href="wishlist.php">Wishlist</a>
            </div>
            <div class="col-md-3">
                <h4>Customer Service</h4>
                <a href="#">Help Center</a>
                <a href="#">Returns</a>
                <a href="#">Shipping Info</a>
                <a href="faq.php">FAQs</a>
            </div>
            <div class="col-md-3">
                <h4>Contact Info</h4>
                <p><i class="fa fa-phone"></i> <?php echo $settings_data['contact_phone']; ?></p>
                <p><i class="fa fa-envelope"></i> <?php echo $settings_data['contact_email']; ?></p>
                <p><i class="fa fa-map-marker"></i> <?php echo $settings_data['contact_address']; ?></p>
            </div>
        </div>
    </div>
</div>
<?php endif; ?>

<script>
// --- Live Feed Animation ---
document.addEventListener("DOMContentLoaded", function() {
    startLiveFeed();
});

function startLiveFeed() {
    setInterval(() => {
        if(window.isScrolling) return;

        const grid = document.getElementById('dynamic-products-grid');
        if(grid && grid.children.length > 4) {
            
            const cards = grid.getElementsByClassName('product-card');
            const randomIndex = Math.floor(Math.random() * cards.length);
            const targetCard = cards[randomIndex];

            fetch('fetch_live_feed.php')
                .then(res => res.json())
                .then data => {
                    if(data.success) {
                        targetCard.style.transform = "rotateY(90deg)";
                        targetCard.style.opacity = "0.5";
                        targetCard.style.transition = "transform 0.4s ease-in, opacity 0.4s";

                        setTimeout(() => {
                            const img = targetCard.querySelector('.p-img-box img');
                            const titleLink = targetCard.querySelector('.p-title a');
                            const priceEl = targetCard.querySelector('.p-price');
                            const stockEl = targetCard.querySelector('.stock-status');

                            if(img) img.src = data.product.image;
                            if(titleLink) { titleLink.innerText = data.product.name; titleLink.href = data.product.link; }
                            if(priceEl) priceEl.innerText = data.product.price;
                            
                            // Visual reset for stock (optional if data includes it)
                            // if(stockEl) ...

                            targetCard.style.transform = "rotateY(0deg)";
                            targetCard.style.opacity = "1";
                        }, 400);
                    }
                })
                .catch(err => console.log('Live feed error'));
        }
    }, 4000);
}

// Scroll Tracker
window.isScrolling = false;
window.addEventListener('scroll', () => {
    window.isScrolling = true;
    clearTimeout(window.scrollTimeout);
    window.scrollTimeout = setTimeout(() => { window.isScrolling = false; }, 200);
});

// Timer
window.onload = function () {
    const display = document.querySelector('#flash-timer-display');
    if(display) {
        let timer = 18000;
        setInterval(function () {
            let hours = parseInt(timer / 3600, 10);
            let minutes = parseInt((timer % 3600) / 60, 10);
            let seconds = parseInt(timer % 60, 10);
            display.textContent = (hours < 10 ? "0" + hours : hours) + ":" + (minutes < 10 ? "0" + minutes : minutes) + ":" + (seconds < 10 ? "0" + seconds : seconds);
            if (--timer < 0) timer = 0;
        }, 1000);
    }
};

function addToWishlist(p_id) {
    <?php if(!isset($_SESSION['customer'])): ?>
        alert("Please login first.");
        window.location.href = "login.php";
    <?php else: ?>
        alert("Product " + p_id + " added to wishlist!");
    <?php endif; ?>
}

<!-- Replace your existing renderProductCard function with this: -->


<!-- Add auto-scroll to latest products section: -->
<div class="horizontal-scroll-wrapper product-auto-scroll">
    <?php foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $row): ?>
        <?php echo renderProductCard($row, LANG_VALUE_1, $pdo); ?>
    <?php endforeach; ?>
</div>

<!-- Add JavaScript for auto-scroll pause on hover: -->
<script>
document.querySelectorAll('.product-auto-scroll').forEach(scroll => {
    scroll.addEventListener('mouseenter', () => {
        scroll.style.animationPlayState = 'paused';
    });
    scroll.addEventListener('mouseleave', () => {
        scroll.style.animationPlayState = 'running';
    });
});

// Add to Cart function
function addToCart(productId) {
    fetch('add_to_cart.php', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: 'product_id=' + productId + '&quantity=1'
    })
    .then(response => response.json())
    .then(data => {
        if(data.success) {
            // Show cart animation
            const cartBtn = event.target.closest('.btn-action');
            cartBtn.innerHTML = '<i class="fa fa-check"></i>';
            cartBtn.style.background = '#27ae60';
            
            setTimeout(() => {
                cartBtn.innerHTML = '<i class="fa fa-shopping-cart"></i>';
                cartBtn.style.background = '';
            }, 1000);
            
            // Update cart count
            updateCartCount();
        }
    });
}

function updateCartCount() {
    // Update cart count in header
    fetch('get_cart_count.php')
    .then(response => response.json())
    .then(data => {
        document.querySelectorAll('.cart-count').forEach(el => {
            el.textContent = data.count;
        });
    });
}
</script>
</script

<!-- Limit featured products client-side (falls back until admin UI updates) -->
<script>
document.addEventListener('DOMContentLoaded', function(){
    try {
        var maxFeatured = <?php echo (int) $featured_product_count; ?>;
        // Find the featured section by header text
        document.querySelectorAll('.content-flow').forEach(function(block){
            var titleEl = block.querySelector('.section-title');
            if(titleEl && /featured/i.test(titleEl.textContent)){
                var cards = block.querySelectorAll('.product-card');
                if(cards.length > maxFeatured){
                    for(var i=maxFeatured;i<cards.length;i++){
                        cards[i].style.display = 'none';
                    }
                }
            }
        });
    } catch(e){ console.warn('Featured limiter:', e); }
});
</script>

<?php require_once('footer.php'); ?>