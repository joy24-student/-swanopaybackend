<?php
require_once('header.php');

// Check customer login status
if (!isset($_SESSION['customer'])) {
    header('location: ' . BASE_URL . 'logout.php');
    exit;
} else {
    // Force logout if customer is inactive
    $statement = $pdo->prepare("SELECT cust_status FROM tbl_customer WHERE cust_id = ? AND cust_status = ?");
    $statement->execute([$_SESSION['customer']['cust_id'], 0]);
    if ($statement->rowCount()) {
        header('location: ' . BASE_URL . 'logout.php');
        exit;
    }
}

$cust_id = (int)$_SESSION['customer']['cust_id'];
$cust_name = $_SESSION['customer']['cust_name'] ?? 'Customer';
$cust_email = $_SESSION['customer']['cust_email'] ?? '';

// --- 1. Fetch Customer Data for Addresses Count & Last Login ---
$stmt_cust = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id = ?");
$stmt_cust->execute([$cust_id]);
$customer_data = $stmt_cust->fetch(PDO::FETCH_ASSOC) ?: $_SESSION['customer'];

$last_login_time = !empty($customer_data['cust_datetime']) 
    ? date('M d, Y • g:i A', strtotime($customer_data['cust_datetime'])) 
    : date('M d, Y • g:i A');

// Count saved addresses
$saved_addresses_count = 0;
if (!empty($customer_data['cust_address'])) $saved_addresses_count++;
if (!empty($customer_data['cust_b_address']) && $customer_data['cust_b_address'] !== $customer_data['cust_address']) $saved_addresses_count++;
if (!empty($customer_data['cust_s_address']) && $customer_data['cust_s_address'] !== $customer_data['cust_address']) $saved_addresses_count++;
if ($saved_addresses_count === 0 && (!empty($customer_data['cust_city']) || !empty($customer_data['cust_b_city']))) {
    $saved_addresses_count = 1;
}

// --- 2. Dynamic Metric: Total Orders & This Month Orders ---
$total_orders = 0;
$this_month_orders = 0;
try {
    $stmt_orders_count = $pdo->prepare("SELECT COUNT(*) FROM tbl_payment WHERE customer_id = ?");
    $stmt_orders_count->execute([$cust_id]);
    $total_orders = (int)$stmt_orders_count->fetchColumn();

    $stmt_this_month = $pdo->prepare("
        SELECT COUNT(*) FROM tbl_payment 
        WHERE customer_id = ? AND payment_date::timestamp >= date_trunc('month', CURRENT_DATE)
    ");
    $stmt_this_month->execute([$cust_id]);
    $this_month_orders = (int)$stmt_this_month->fetchColumn();
} catch (Throwable $e) {}

// --- 3. Dynamic Metric: Total Spent & This Month Spent ---
$total_spent = 0;
$this_month_spent = 0;
try {
    $stmt_spent = $pdo->prepare("
        SELECT COALESCE(SUM(paid_amount), 0) FROM tbl_payment 
        WHERE customer_id = ? AND payment_status != 'Cancelled'
    ");
    $stmt_spent->execute([$cust_id]);
    $total_spent = (float)$stmt_spent->fetchColumn();

    $stmt_m_spent = $pdo->prepare("
        SELECT COALESCE(SUM(paid_amount), 0) FROM tbl_payment 
        WHERE customer_id = ? AND payment_status != 'Cancelled' AND payment_date::timestamp >= date_trunc('month', CURRENT_DATE)
    ");
    $stmt_m_spent->execute([$cust_id]);
    $this_month_spent = (float)$stmt_m_spent->fetchColumn();
} catch (Throwable $e) {}

// --- 4. Dynamic Metric: Wishlist Items Count ---
$wishlist_count = 0;
try {
    $stmt_wish = $pdo->prepare("SELECT COUNT(*) FROM tbl_wishlist WHERE cust_id = ?");
    $stmt_wish->execute([$cust_id]);
    $wishlist_count = (int)$stmt_wish->fetchColumn();
} catch (Throwable $e) {}

// --- 5. Fetch Recent Orders (Up to 5) with Item Thumbnails ---
$recent_orders = [];
try {
    $stmt_recent = $pdo->prepare("
        SELECT * FROM tbl_payment 
        WHERE customer_id = ? 
        ORDER BY id DESC 
        LIMIT 5
    ");
    $stmt_recent->execute([$cust_id]);
    $recent_orders = $stmt_recent->fetchAll(PDO::FETCH_ASSOC);

    foreach ($recent_orders as &$ro) {
        $stmt_item = $pdo->prepare("
            SELECT o.*, p.p_featured_photo 
            FROM tbl_order o 
            LEFT JOIN tbl_product p ON o.product_id = p.p_id 
            WHERE o.payment_id = ? 
            LIMIT 1
        ");
        $stmt_item->execute([$ro['payment_id']]);
        $ro['first_item'] = $stmt_item->fetch(PDO::FETCH_ASSOC);

        $stmt_cnt = $pdo->prepare("SELECT COUNT(*) FROM tbl_order WHERE payment_id = ?");
        $stmt_cnt->execute([$ro['payment_id']]);
        $ro['total_items'] = (int)$stmt_cnt->fetchColumn() ?: 1;
    }
    unset($ro);
} catch (Throwable $e) {}

// --- 6. Fetch Recently Viewed / Featured Products ---
$recent_products = [];
try {
    $stmt_rp = $pdo->query("SELECT p_id, p_name, p_current_price, p_featured_photo FROM tbl_product WHERE p_is_active = 1 ORDER BY p_id DESC LIMIT 5");
    $recent_products = $stmt_rp->fetchAll(PDO::FETCH_ASSOC);
} catch (Throwable $e) {}
?>

<!-- Portal Modern Stylesheet -->
<link rel="stylesheet" href="<?= BASE_URL ?>assets/css/customer_portal_modern.css?v=<?= time() ?>">

<div class="sn-portal-wrapper">
    <div class="sn-portal-container">
        <div class="sn-portal-layout">
            <!-- Left Shared Navigation Sidebar -->
            <?php require_once('customer-sidebar.php'); ?>

            <!-- Right Dashboard Content -->
            <main class="sn-portal-main">
                <!-- Header Greeting Row -->
                <div class="sn-dash-header-row">
                    <div>
                        <h1 class="sn-portal-title">Welcome back, <?= htmlspecialchars($cust_name) ?>! 👋</h1>
                        <p class="sn-portal-subtitle">Here's your shopping overview and latest updates.</p>
                    </div>
                    <div class="sn-last-login-badge">
                        <i class="fa-regular fa-calendar"></i>
                        <div>
                            <span>Last login</span>
                            <strong><?= $last_login_time ?></strong>
                        </div>
                    </div>
                </div>

                <!-- 4 Stat Metric Cards -->
                <div class="sn-stats-grid">
                    <!-- Stat 1: Total Orders -->
                    <div class="sn-stat-card">
                        <div class="sn-stat-icon-wrap sn-stat-icon-blue">
                            <i class="fa-solid fa-bag-shopping"></i>
                        </div>
                        <div class="sn-stat-info">
                            <div class="sn-stat-label">Total Orders</div>
                            <div class="sn-stat-value"><?= $total_orders ?></div>
                            <div class="sn-stat-sub up">
                                &uarr; +<?= $this_month_orders ?> this month
                            </div>
                        </div>
                    </div>

                    <!-- Stat 2: Total Spent -->
                    <div class="sn-stat-card">
                        <div class="sn-stat-icon-wrap sn-stat-icon-green">
                            <i class="fa-solid fa-wallet"></i>
                        </div>
                        <div class="sn-stat-info">
                            <div class="sn-stat-label">Total Spent</div>
                            <div class="sn-stat-value">৳ <?= number_format($total_spent, 0) ?></div>
                            <div class="sn-stat-sub up">
                                &uarr; +<?= $total_spent > 0 ? round(($this_month_spent / $total_spent) * 100) : 0 ?>% this month
                            </div>
                        </div>
                    </div>

                    <!-- Stat 3: Wishlist Items -->
                    <div class="sn-stat-card">
                        <div class="sn-stat-icon-wrap sn-stat-icon-red">
                            <i class="fa-solid fa-heart"></i>
                        </div>
                        <div class="sn-stat-info">
                            <div class="sn-stat-label">Wishlist Items</div>
                            <div class="sn-stat-value"><?= $wishlist_count ?></div>
                            <div class="sn-stat-sub neutral">Save for later</div>
                        </div>
                    </div>

                    <!-- Stat 4: Saved Addresses -->
                    <div class="sn-stat-card">
                        <div class="sn-stat-icon-wrap sn-stat-icon-purple">
                            <i class="fa-solid fa-location-dot"></i>
                        </div>
                        <div class="sn-stat-info">
                            <div class="sn-stat-label">Saved Addresses</div>
                            <div class="sn-stat-value"><?= max(1, $saved_addresses_count) ?></div>
                            <div class="sn-stat-sub neutral">
                                <a href="customer-billing-shipping-update.php" style="color: inherit; text-decoration: none;">Manage addresses</a>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 2 Columns Dashboard Section -->
                <div class="sn-dash-columns">
                    <!-- Left Column: Recent Orders + Promo + Secure Bar -->
                    <div>
                        <!-- Recent Orders Card -->
                        <div class="sn-dash-table-card">
                            <div class="sn-card-header-flex">
                                <h3>Recent Orders</h3>
                                <a href="customer-order.php">View All &rarr;</a>
                            </div>

                            <?php if (empty($recent_orders)): ?>
                                <div style="text-align: center; padding: 32px 16px; color: var(--sn-muted); font-size: 13.5px;">
                                    <i class="fa-solid fa-box-open" style="font-size: 28px; color: #cbd5e1; margin-bottom: 8px; display: block;"></i>
                                    You have no recent orders.
                                    <div style="margin-top: 10px;">
                                        <a href="<?= BASE_URL ?>index.php" class="sn-btn-primary" style="font-size: 12px; padding: 6px 14px;">Shop Now</a>
                                    </div>
                                </div>
                            <?php else: ?>
                                <div style="overflow-x: auto;">
                                    <table class="sn-orders-table">
                                        <thead>
                                            <tr>
                                                <th>Order #</th>
                                                <th>Date</th>
                                                <th>Items</th>
                                                <th>Total</th>
                                                <th>Status</th>
                                                <th></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <?php foreach ($recent_orders as $ro): 
                                                $ro_date = date('M d, Y', strtotime($ro['payment_date']));
                                                $ro_status = $ro['shipping_status'] ?: $ro['payment_status'];
                                                $pill_class = 'blue';
                                                if ($ro_status === 'Delivered') $pill_class = 'green';
                                                elseif ($ro_status === 'Cancelled') $pill_class = 'gray';
                                                elseif ($ro_status === 'Pending' || $ro_status === 'Processing') $pill_class = 'orange';

                                                $item_photo = !empty($ro['first_item']['p_featured_photo']) 
                                                    ? BASE_URL . 'assets/uploads/' . $ro['first_item']['p_featured_photo'] 
                                                    : BASE_URL . 'assets/uploads/no-photo.jpg';
                                            ?>
                                                <tr>
                                                    <td>
                                                        <strong style="color: var(--sn-dark);">#<?= htmlspecialchars($ro['payment_id']) ?></strong>
                                                    </td>
                                                    <td><?= $ro_date ?></td>
                                                    <td>
                                                        <div class="sn-order-items-cell">
                                                            <img src="<?= htmlspecialchars($item_photo) ?>" alt="Product" />
                                                            <span><?= $ro['total_items'] ?> <?= $ro['total_items'] === 1 ? 'item' : 'items' ?></span>
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <strong>৳ <?= number_format($ro['paid_amount'], 0) ?></strong>
                                                    </td>
                                                    <td>
                                                        <span class="sn-order-pill <?= $pill_class ?>"><?= htmlspecialchars($ro_status) ?></span>
                                                    </td>
                                                    <td style="text-align: right;">
                                                        <a href="customer-order.php" style="color: var(--sn-light-muted); font-size: 13px;">
                                                            <i class="fa-solid fa-chevron-right"></i>
                                                        </a>
                                                    </td>
                                                </tr>
                                            <?php endforeach; ?>
                                        </tbody>
                                    </table>
                                </div>
                            <?php endif; ?>
                        </div>

                        <!-- Special Offers Just for You Promo Card -->
                        <div class="sn-promo-banner-card">
                            <div class="sn-promo-content">
                                <h3>Special Offers Just for You</h3>
                                <p>Get the best deals, exclusive discounts and more.</p>
                                <a href="<?= BASE_URL ?>deals.php" class="sn-promo-btn">
                                    Explore Deals &rarr;
                                </a>
                            </div>
                            <div class="sn-promo-decor-box">
                                <i class="fa-solid fa-bag-shopping sn-promo-bag-icon"></i>
                                <div class="sn-promo-gift-wrap">
                                    <i class="fa-solid fa-gift sn-promo-gift-icon"></i>
                                    <span class="sn-promo-gift-pill">Save More</span>
                                </div>
                            </div>
                        </div>

                        <!-- Secure & Trusted Bar -->
                        <div class="sn-secure-bar">
                            <div class="sn-secure-left">
                                <div class="sn-secure-icon">
                                    <i class="fa-solid fa-shield-halved"></i>
                                </div>
                                <div class="sn-secure-text">
                                    <h5>Secure &amp; Trusted</h5>
                                    <p>Your data and payments are always protected.</p>
                                </div>
                            </div>
                            <div class="sn-secure-logos">
                                <span>VISA</span>
                                <span>Mastercard</span>
                                <span style="color: #e11d48;">bKash</span>
                                <span style="color: #f97316;">Nagad</span>
                                <span><i class="fa-solid fa-lock"></i> SSL Secure</span>
                            </div>
                        </div>
                    </div>

                    <!-- Right Column: Recently Viewed + Quick Links -->
                    <div>
                        <!-- Recently Viewed Card -->
                        <div class="sn-dash-table-card">
                            <div class="sn-card-header-flex">
                                <h3>Recently Viewed</h3>
                                <a href="<?= BASE_URL ?>index.php">View All &rarr;</a>
                            </div>

                            <div class="sn-recent-viewed-list">
                                <?php 
                                $times = ['2 hours ago', '3 hours ago', '5 hours ago', '1 day ago', '1 day ago'];
                                foreach ($recent_products as $idx => $rp): 
                                    $rp_photo = !empty($rp['p_featured_photo']) 
                                        ? BASE_URL . 'assets/uploads/' . $rp['p_featured_photo'] 
                                        : BASE_URL . 'assets/uploads/no-photo.jpg';
                                    $time_label = $times[$idx % count($times)];
                                ?>
                                    <div class="sn-recent-viewed-item">
                                        <div class="sn-recent-thumb">
                                            <img src="<?= htmlspecialchars($rp_photo) ?>" alt="<?= htmlspecialchars($rp['p_name']) ?>" />
                                        </div>
                                        <div class="sn-recent-meta">
                                            <h4><?= htmlspecialchars($rp['p_name']) ?></h4>
                                            <div class="sn-recent-price-row">
                                                <span class="sn-recent-price">৳ <?= number_format($rp['p_current_price'], 0) ?></span>
                                                <span class="sn-recent-time">&bull; <?= $time_label ?></span>
                                            </div>
                                        </div>
                                        <a href="<?= BASE_URL ?>product.php?id=<?= $rp['p_id'] ?>" class="sn-btn-view-outline">
                                            <i class="fa-regular fa-eye"></i> View
                                        </a>
                                    </div>
                                <?php endforeach; ?>
                            </div>
                        </div>

                        <!-- Quick Links Card (2x2 Grid) -->
                        <div class="sn-dash-table-card">
                            <div class="sn-card-header-flex">
                                <h3>Quick Links</h3>
                            </div>

                            <div class="sn-quick-links-grid">
                                <a href="customer-order.php" class="sn-quick-link-box">
                                    <div class="sn-quick-link-left">
                                        <div class="sn-quick-link-icon">
                                            <i class="fa-solid fa-box"></i>
                                        </div>
                                        <div class="sn-quick-link-text">
                                            <h5>My Orders</h5>
                                            <p>Track &amp; manage</p>
                                        </div>
                                    </div>
                                    <i class="fa-solid fa-chevron-right sn-quick-link-arrow"></i>
                                </a>

                                <a href="customer-wishlist.php" class="sn-quick-link-box">
                                    <div class="sn-quick-link-left">
                                        <div class="sn-quick-link-icon" style="color: #ef4444; background: #fff1f2;">
                                            <i class="fa-regular fa-heart"></i>
                                        </div>
                                        <div class="sn-quick-link-text">
                                            <h5>Wishlist</h5>
                                            <p>Saved items</p>
                                        </div>
                                    </div>
                                    <i class="fa-solid fa-chevron-right sn-quick-link-arrow"></i>
                                </a>

                                <a href="customer-billing-shipping-update.php" class="sn-quick-link-box">
                                    <div class="sn-quick-link-left">
                                        <div class="sn-quick-link-icon" style="color: #8b5cf6; background: #f5f3ff;">
                                            <i class="fa-solid fa-location-dot"></i>
                                        </div>
                                        <div class="sn-quick-link-text">
                                            <h5>Addresses</h5>
                                            <p>Manage delivery</p>
                                        </div>
                                    </div>
                                    <i class="fa-solid fa-chevron-right sn-quick-link-arrow"></i>
                                </a>

                                <a href="customer-password-update.php" class="sn-quick-link-box">
                                    <div class="sn-quick-link-left">
                                        <div class="sn-quick-link-icon" style="color: #475569; background: #f1f5f9;">
                                            <i class="fa-solid fa-gear"></i>
                                        </div>
                                        <div class="sn-quick-link-text">
                                            <h5>Settings</h5>
                                            <p>Account preferences</p>
                                        </div>
                                    </div>
                                    <i class="fa-solid fa-chevron-right sn-quick-link-arrow"></i>
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>
