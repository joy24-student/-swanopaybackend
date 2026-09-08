<?php require_once('header.php'); ?>

<?php
// Check customer login status
if(!isset($_SESSION['customer'])) {
    header('location: '.BASE_URL.'logout.php');
    exit;
} else {
    // Force logout if customer is inactive
    $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=? AND cust_status=?");
    $statement->execute(array($_SESSION['customer']['cust_id'], 0));
    if($statement->rowCount()) {
        header('location: '.BASE_URL.'logout.php');
        exit;
    }
}

$cust_id = $_SESSION['customer']['cust_id'];
$customer_name = htmlspecialchars($_SESSION['customer']['cust_name'] ?? 'Customer');
$customer_email = htmlspecialchars($_SESSION['customer']['cust_email'] ?? 'N/A');
$customer_photo = !empty($_SESSION['customer']['cust_photo']) ? 'assets/uploads/' . $_SESSION['customer']['cust_photo'] : 'assets/uploads/default.png';

$error_message_orders = '';
$error_message_wishlist = '';
$recent_orders = [];
$wishlist_items = [];


// --- 1. Fetch Wishlist Count and Items ---
$total_wishlist_items = 0;
try {
    $statement_wishlist = $pdo->prepare("
        SELECT 
            t1.wishlist_id, 
            t1.product_id,
            t2.p_name, 
            t2.p_current_price, 
            t2.p_featured_photo,
            t2.p_old_price 
        FROM tbl_wishlist t1
        JOIN tbl_product t2 ON t1.product_id = t2.p_id
        WHERE t1.cust_id = ?
        ORDER BY t1.added_date DESC
    ");
    $statement_wishlist->execute(array($cust_id));
    $wishlist_items = $statement_wishlist->fetchAll(PDO::FETCH_ASSOC);
    $total_wishlist_items = count($wishlist_items);

} catch (PDOException $e) {
    $error_message_wishlist = "Wishlist Error: Please ensure the **tbl_wishlist** table exists. DB message: " . htmlspecialchars($e->getMessage());
}

// --- 2. Fetch Recent Orders ---
try {
    // Fetches the most recent 3 orders for display
    $statement_orders = $pdo->prepare("
        SELECT * FROM tbl_payment 
        WHERE customer_id=? 
        ORDER BY id DESC 
        LIMIT 3
    ");
    $statement_orders->execute(array($cust_id));
    $recent_orders = $statement_orders->fetchAll(PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    $error_message_orders = "Orders Error: " . htmlspecialchars($e->getMessage());
}

// --- 3. Fetch Recently Visited Products (Simulated - using popular/random products) ---
$visited_products = [];
try {
    $sql_rand = defined('SQL_RAND') ? SQL_RAND : 'RAND()';
    $statement_visited = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_active = 1 ORDER BY {$sql_rand} LIMIT 4");
    $statement_visited->execute();
    $visited_products = $statement_visited->fetchAll(PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    // Non-critical error
}

// --- 4. Check if Customer has Vendor/Business Account ---
$has_vendor_account = false;
$vendor_account = null;
try {
    $statement_vendor = $pdo->prepare("SELECT * FROM tbl_businesses WHERE owner_user_id = ? AND verification_status = 'approved' LIMIT 1");
    $statement_vendor->execute(array($cust_id));
    $vendor_account = $statement_vendor->fetch(PDO::FETCH_ASSOC);
    $has_vendor_account = !empty($vendor_account) && $vendor_account['verification_status'] === 'approved';
} catch (PDOException $e) {
    // Vendor check failed, assume no vendor account
}


?>

<!-- Custom CSS from User's Template -->
<style>
    /* ------------------------------
        Color palette (changeable)
        ------------------------------
        --orange: primary action (Daraz-like)
        --dark: main text
        --muted: secondary text
        --card: card background
        --accent: purple accent for freebies
        --bg: page background
    */
    :root{
        --orange: #ff6a00;
        --orange-600: #ff5a00;
        --dark: #111827;
        --muted: #6b7280;
        --card: #ffffff;
        --accent: #7b3ff2;
        --bg: #f6f7fb;
        --glass: rgba(255,255,255,0.6);
        --radius: 12px;
        --shadow-1: 0 6px 18px rgba(15,23,42,0.06);
        --shadow-2: 0 8px 30px rgba(15,23,42,0.08);
        font-family: "Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial;
    }

    /* ------------------------------
        Global reset & utils
        ------------------------------ */
    *{box-sizing:border-box}
    html,body{height:100%}
    body{
        margin:0;
        background: linear-gradient(180deg,var(--bg),#ffffff 60%);
        color:var(--dark);
        -webkit-font-smoothing:antialiased;
        -moz-osx-font-smoothing:grayscale;
        padding:20px 0; /* Adjusted padding to better fit existing header/footer */
    }
    .container{
        max-width:920px;
        margin:0 auto;
        padding: 0 15px; /* Added internal padding for responsiveness */
    }

    /* ------------------------------
        Header / Profile card
        ------------------------------ */
    .profile-card{
        display:flex;
        align-items:center;
        gap:18px;
        background:linear-gradient(180deg, rgba(255,255,255,0.9), rgba(255,255,255,0.85));
        border-radius:18px;
        padding:18px;
        box-shadow:var(--shadow-1);
        margin-bottom:18px;
    }
    .avatar{
        width:68px;
        height:68px;
        border-radius:50%;
        overflow:hidden;
        flex:0 0 68px;
        display:grid;
        place-items:center;
        background:linear-gradient(135deg,#ffe8d0, #ffd6b3);
        border:4px solid rgba(255,255,255,0.6);
    }
    .avatar img{width:100%;height:100%;object-fit:cover;display:block}
    .profile-info{flex:1;min-width:0}
    .profile-info h1{
        margin:0;
        font-size:20px;
        letter-spacing:-0.2px;
    }
    .profile-meta{
        margin-top:6px;
        color:var(--muted);
        font-size:13px;
    }
    .settings-btn{
        flex:0 0 auto;
        background:transparent;
        border:none;
        cursor:pointer;
        width:36px;
        height:36px;
        display:grid;
        place-items:center;
        border-radius:10px;
        transition:all .18s ease;
        text-decoration: none; /* Ensure no underline on anchor */
    }
    .settings-btn:hover{background:rgba(0,0,0,0.04);transform:translateY(-2px)}

    /* ------------------------------
        Orders row
        ------------------------------ */
    .orders{
        margin-top:10px;
        display:flex;
        align-items:center;
        justify-content:space-between;
    }
    .orders .title{
        font-weight:600;
        color:var(--dark);
    }
    .orders .viewall{
        color:var(--muted);
        font-size:14px;
        text-decoration:none;
    }
    .order-cards{
        margin-top:12px;
        display:flex;
        gap:12px;
        background:transparent;
        padding:12px 4px;
        overflow-x: auto; /* Enable horizontal scroll on smaller screens */
    }
    .order-item{
        background:var(--card);
        border-radius:14px;
        padding:12px;
        flex:0 0 auto; /* Ensure items don't shrink */
        min-width: 100px;
        display:flex;
        flex-direction:column;
        align-items:center;
        gap:8px;
        box-shadow:var(--shadow-1);
        text-align:center;
        text-decoration: none;
        color: inherit;
        transition: transform 0.1s;
    }
    .order-item:hover {
        transform: scale(0.98);
        box-shadow: 0 4px 12px rgba(15,23,42,0.1);
    }
    .order-item svg{width:26px;height:26px;display:block}
    .order-item span{font-size:13px;color:var(--muted)}

    /* ------------------------------
        Promo widgets
        ------------------------------ */
    .promo-row{
        display:grid;
        grid-template-columns:1fr 1fr;
        gap:12px;
        margin-top:18px;
    }
    .promo{
        border-radius:14px;
        padding:14px;
        display:flex;
        align-items:center;
        gap:12px;
        box-shadow:var(--shadow-1);
        background:linear-gradient(180deg,#fff,#fff);
        text-decoration: none;
    }
    .promo.coins{border:1px solid rgba(255,106,0,0.08)}
    .promo.freebie{border:1px solid rgba(123,63,242,0.06)}
    .promo .info{flex:1}
    .promo .title{font-weight:700;margin:0; color:var(--dark);}
    .promo .desc{color:var(--muted);font-size:13px;margin-top:6px}
    .btn{
        display:inline-block;
        padding:8px 12px;
        border-radius:10px;
        font-weight:700;
        font-size:13px;
        border:none;
        cursor:pointer;
        text-decoration: none;
        transition: opacity 0.2s;
    }
    .btn:hover {
        opacity: 0.9;
    }
    .btn.orange{background:var(--orange);color:#fff}
    .btn.accent{background:var(--accent);color:#fff}

    /* ------------------------------
        Recently viewed list / Wishlist (Re-used structure)
        ------------------------------ */
    .section{
        margin-top:20px;
    }
    .section .head{
        display:flex;
        justify-content:space-between;
        align-items:center;
        gap:12px;
        margin-bottom:10px;
    }
    .section h2{margin:0;font-size:16px; color:var(--dark);}
    .cards{
        display:flex;
        gap:12px;
        overflow-x:auto;
        padding-bottom:6px;
    }
    .product-link {
        text-decoration: none;
    }
    .product{
        background:var(--card);
        border-radius:12px;
        padding:10px;
        min-width:180px;
        box-shadow:var(--shadow-1);
        flex:0 0 180px;
        transition: transform 0.15s;
    }
    .product:hover {
        transform: translateY(-2px);
        box-shadow: var(--shadow-2);
    }
    .product .thumb{
        width:100%;
        height:110px;
        border-radius:8px;
        background:linear-gradient(90deg,#eef2ff,#fff);
        display:grid;
        place-items:center;
        margin-bottom:8px;
        overflow:hidden;
    }
    .product .thumb img {
        width: 90%;
        height: 90%;
        object-fit: contain;
    }
    .product .title{
        font-size:13px;
        margin:0;
        color:var(--dark);
        min-height:36px;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
    }
    .product .price{margin-top:8px;font-weight:700;color:var(--orange)}
    .product .old{font-size:12px;color:var(--muted);text-decoration:line-through;margin-left:6px}
    .btn-remove {
        background-color: #fcebeb;
        color: #ff3b30;
        border: 1px solid #f9dcdc;
        padding: 5px 10px;
        font-size: 12px;
        margin-top: 5px;
        width: 100%;
        font-weight: 500;
        border-radius: 8px;
        cursor: pointer;
        transition: background-color 0.2s;
    }
    .btn-remove:hover {
        background-color: #ff3b30;
        color: white;
    }

    /* ------------------------------
        Shortcut grid
        ------------------------------ */
    .shortcuts{
        margin-top:18px;
        display:grid;
        grid-template-columns:repeat(4,1fr);
        gap:12px;
    }
    .shortcut-link {
        text-decoration: none;
        color: inherit;
    }
    .shortcut{
        background:var(--card);
        padding:10px;
        border-radius:12px;
        display:flex;
        flex-direction:column;
        align-items:center;
        gap:8px;
        justify-content:center;
        box-shadow:var(--shadow-1);
        text-align:center;
        height: 100px; /* Uniform height for grid items */
        transition: transform 0.1s;
    }
    .shortcut:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(15,23,42,0.1);
    }
    .shortcut svg{width:34px;height:34px}
    .shortcut span{font-size:13px;color:var(--muted); text-transform: capitalize;}

    /* Custom Modal Styles (For error/confirm handling) */
    .custom-modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.7);
        display: none;
        justify-content: center;
        align-items: center;
        z-index: 9999;
    }
    .custom-modal-content {
        background: var(--card);
        padding: 30px;
        border-radius: var(--radius);
        box-shadow: var(--shadow-2);
        max-width: 90%;
        width: 350px;
        text-align: center;
    }
    .custom-modal-content h4 {
        margin-top: 0;
        color: var(--dark);
        font-size: 1.2rem;
    }
    .custom-modal-footer button {
        margin: 5px;
        min-width: 80px;
    }

    /* ------------------------------
        Responsive
        ------------------------------ */
    @media (max-width:720px){
        body { padding: 10px 0; }
        .promo-row{grid-template-columns:1fr;}
        .profile-card{padding:12px}
        .avatar{width:62px;height:62px}
        .order-cards{gap:8px}
        .shortcuts{grid-template-columns:repeat(3, 1fr)} /* Use 3 columns on small screens */
        .product{min-width:160px; flex:0 0 160px;}
    }
</style>

<!-- Custom Modal Structure (To replace alert/confirm) -->
<div id="customAlertModal" class="custom-modal-overlay">
    <div class="custom-modal-content">
        <h4 id="customAlertTitle">Notification</h4>
        <p id="customAlertMessage" style="color:var(--muted); margin-bottom: 20px;"></p>
        <div class="custom-modal-footer">
            <button class="btn orange" onclick="hideCustomAlert()">OK</button>
        </div>
    </div>
</div>

<div id="customConfirmModal" class="custom-modal-overlay">
    <div class="custom-modal-content">
        <h4 id="customConfirmTitle">Confirm Action</h4>
        <p id="customConfirmMessage" style="color:var(--muted); margin-bottom: 20px;"></p>
        <div class="custom-modal-footer">
            <button id="confirmYes" class="btn orange">Yes</button>
            <button id="confirmNo" class="btn" style="background-color: #e5e7eb; color: #333;" onclick="hideCustomConfirm(false)">No</button>
        </div>
    </div>
</div>

<main class="container">

    <!-- PROFILE HEADER -->
    <section class="profile-card" aria-label="Profile header">
        <div class="avatar" aria-hidden="true">
            <!-- Ensure image path is correct, use fallback image if main image not found -->
            <img 
                src="<?php echo htmlspecialchars($customer_photo); ?>" 
                alt="<?php echo $customer_name; ?>'s avatar"
                onerror="this.onerror=null; this.src='assets/uploads/default.png';" 
            >
        </div>

        <div class="profile-info">
            <h1><?php echo $customer_name; ?></h1>
            <div class="profile-meta">
                <?php echo $total_wishlist_items; ?> Wishlist 
                <!-- You would fetch these counts from DB for accuracy -->
                · 0 Followed Stores 
                · 0 Vouchers
            </div>

            <!-- Orders quick row -->
            <div class="orders" style="margin-top:12px">
                <div class="title">My Orders</div>
                <a href="customer-orders.php" class="viewall">View All Orders &gt;</a>
            </div>

            <div class="order-cards" role="list" aria-label="Order statuses">
                <!-- Links to respective pages if they exist -->
                <a href="customer-orders.php?status=to_cancel" class="order-item" role="listitem" title="Cancellable Orders (24h)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="var(--orange)" stroke-width="1.2"><rect x="2" y="6" width="20" height="12" rx="2"/></svg>
                    <span>Cancellable</span>
                </a>
                <a href="customer-orders.php?status=to_ship" class="order-item" role="listitem" title="To Ship">
                    <svg viewBox="0 0 24 24" fill="none" stroke="var(--orange)" stroke-width="1.2"><path d="M3 7h13v8H3z"/></svg>
                    <span>To Ship</span>
                </a>
                <a href="customer-orders.php?status=to_receive" class="order-item" role="listitem" title="To Receive">
                    <svg viewBox="0 0 24 24" fill="none" stroke="var(--orange)" stroke-width="1.4"><path d="M3 12h13"/></svg>
                    <span>To Receive</span>
                </a>
                <a href="customer-reviews.php" class="order-item" role="listitem" title="To Review">
                    <svg viewBox="0 0 24 24" fill="none" stroke="var(--orange)" stroke-width="1.2"><circle cx="12" cy="12" r="4"/></svg>
                    <span>To Review</span>
                </a>
                <a href="customer-returns.php" class="order-item" role="listitem" title="Returns & Cancellations">
                    <svg viewBox="0 0 24 24" fill="none" stroke="var(--orange)" stroke-width="1.2"><path d="M6 7h12v9H6z"/></svg>
                    <span>Returns &amp; Cancellations</span>
                </a>
            </div>
        </div>

        <a href="customer-profile.php" class="settings-btn" aria-label="Settings/Edit Profile">
            <!-- gear icon -->
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M12 15.5A3.5 3.5 0 1 0 12 8.5a3.5 3.5 0 0 0 0 7z" stroke="#444" stroke-width="1.2"/><path d="M19.4 15a1.8 1.8 0 0 0 .33 1.95l.04.04a1 1 0 0 1-1.2 1.6l-.06-.04a1.8 1.8 0 0 0-1-.25 1.8 1.8 0 0 0-1.32.63l-.04.04a1 1 0 0 1-1.45 0l-.03-.03a1.8 1.8 0 0 0-1.33-.64 1.8 1.8 0 0 0-1 .25l-.06.04a1 1 0 0 1-1.2-1.6l.04-.04A1.8 1.8 0 0 0 9 16.05" stroke="#444" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </a>
    </section>
    
    <!-- Display Errors -->
    <?php if (!empty($error_message_orders)): ?>
        <div style="background:#fef2f2; border:1px solid #fecaca; color:#ef4444; padding:10px; border-radius:8px; margin-bottom: 15px;">
            <?php echo $error_message_orders; ?>
        </div>
    <?php endif; ?>
    <?php if (!empty($error_message_wishlist)): ?>
        <div style="background:#fffbeb; border:1px solid #fde68a; color:#f59e0b; padding:10px; border-radius:8px; margin-bottom: 15px;">
            <?php echo $error_message_wishlist; ?>
        </div>
    <?php endif; ?>

    <!-- PROMO WIDGETS -->
    <section class="promo-row" aria-label="Promotions and coins">
         <?php if($has_vendor_account): ?>
        
        <a href="vendor_dashboard.php" class="promo coins" role="region" aria-label="Daraz coins">
            <div style="flex:0 0 72px;">
                <div style="width:72px;height:72px;border-radius:12px;background:linear-gradient(135deg,#fff7f1,#fff0e6);display:grid;place-items:center;">
                    <strong style="color:var(--orange);font-size:18px">₵</strong>
                </div>
            </div>
            <div class="info">
                <p class="title">Daraz Coins</p>
                <p class="desc">Save ৳100 with 100 coins! Up to 80% OFF during 11.11</p>
                <div style="margin-top:8px">
                    <span class="btn orange">Shop Now</span>
                </div>
            </div>
        </a>
<?php else: ?>
        
        <a href="merchant-register.php" class="promo freebie" role="region" aria-label="Daraz freebie">
            <div style="flex:0 0 72px;">
                <div style="width:72px;height:72px;border-radius:12px;background:linear-gradient(135deg,#f6f0ff,#f0e8ff);display:grid;place-items:center;">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none"><rect x="3" y="6" width="18" height="12" rx="2" stroke="var(--accent)" stroke-width="1.4"/></svg>
                </div>
            </div>

            <div class="info">
                <p class="title" style="color:var(--accent)">Daraz Freebie!</p>
                <p class="desc">Share, Invite &amp; Win Free Prizes!</p>
                <div style="margin-top:8px">
                    <span class="btn accent">Play</span>
                </div>
            </div>
        </a>
    </section>
<?php endif; ?>
    
    <!-- RECENTLY VIEWED -->
    <section class="section" aria-label="Recently viewed items">
        <div class="head">
            <h2>Recently Viewed</h2>
            <a href="shop.php" class="viewall">View More &gt;</a>
        </div>

        <?php if (!empty($visited_products)): ?>
        <div class="cards" role="list">
            <?php foreach ($visited_products as $product): ?>
            <article class="product" role="listitem" aria-label="Product: <?php echo htmlspecialchars($product['p_name']); ?>">
                <a href="product.php?id=<?php echo htmlspecialchars($product['p_id']); ?>" class="product-link">
                    <div class="thumb">
                         <img 
                            src="assets/uploads/<?php echo htmlspecialchars($product['p_featured_photo']); ?>" 
                            alt="<?php echo htmlspecialchars($product['p_name']); ?>"
                            onerror="this.onerror=null; this.src='https://placehold.co/150x150/f0f0f0/333?text=No+Image';" 
                        />
                    </div>
                    <h3 class="title"><?php echo htmlspecialchars($product['p_name']); ?></h3>
                    <div style="display:flex;align-items:center;gap:6px;">
                        <div class="price">৳<?php echo htmlspecialchars(number_format($product['p_current_price'], 2)); ?></div>
                        <?php if($product['p_old_price'] > 0): ?>
                        <div class="old">৳<?php echo htmlspecialchars(number_format($product['p_old_price'], 2)); ?></div>
                        <?php endif; ?>
                    </div>
                </a>
            </article>
            <?php endforeach; ?>
        </div>
        <?php else: ?>
            <div style="padding: 15px; background: #fff; border-radius: 12px; text-align: center; color: var(--muted);">No recent view history. Browse the shop to see products here!</div>
        <?php endif; ?>
    </section>

    <!-- SHORTCUT GRID -->
    <section class="section" aria-label="Quick shortcuts">
        <div class="head">
            <h2>Shortcuts</h2>
            <span style="color:var(--muted);font-size:13px">Quick access</span>
        </div>

        <div class="shortcuts" role="list">
            <a href="customer-orders.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#ff7a3a" stroke-width="1.6"><circle cx="12" cy="9" r="3"/></svg>
                    <span> My orders </span>
                </div>
            </a>

            <a href="customer-profile.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="1.6"><rect x="3" y="6" width="18" height="12" rx="2"/></svg>
                    <span>Profile Info</span>
                </div>
            </a>

            <a href="customer-reviews.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="1.6"><path d="M3 7h18"/></svg>
                    <span>My Reviews</span>
                </div>
            </a>

            <a href="customer-password.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#6366f1" stroke-width="1.6"><path d="M6 7h12v10H6z"/></svg>
                    <span>Change Password</span>
                </div>
            </a>

            <a href="customer-wishlist.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#fb7185" stroke-width="1.6"><path d="M4 4h16v12H4z"/></svg>
                    <span>Wishlist</span>
                </div>
            </a>
            
            <a href="customer-profile.php#address" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#60a5fa" stroke-width="1.6"><circle cx="12" cy="12" r="9"/></svg>
                    <span> Manage Address </span>
                </div>
            </a>

            <a href="customer-reviews.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#f97316" stroke-width="1.6"><path d="M3 12h18"/></svg>
                    <span>My Reviews</span>
                </div>
            </a>

           
            

            <a href="customer-returns.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="1.6"><path d="M3 9h18M7 9l1-6h8l1 6M7 9l-1 9c0 .55.45 1 1 1h8c.55 0 1-.45 1-1l-1-9"/></svg>
                    <span>Returns</span>
                </div>
            </a>

            <a href="logout.php" class="shortcut-link">
                <div class="shortcut" role="listitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#14b8a6" stroke-width="1.6"><rect x="3" y="5" width="18" height="4" rx="1"/></svg>
                    <span>Logout</span>
                </div>
            </a>
        </div>
    </section>

</main>

<script>
// --- Custom Modal Functions (Re-included for no-alert policy) ---
let resolveConfirm;

function showCustomAlert(message, title = 'Notification') {
    document.getElementById('customAlertTitle').textContent = title;
    document.getElementById('customAlertMessage').textContent = message;
    document.getElementById('customAlertModal').style.display = 'flex';
}

function hideCustomAlert() {
    document.getElementById('customAlertModal').style.display = 'none';
}

function showCustomConfirm(message, title = 'Confirm Action') {
    document.getElementById('customConfirmTitle').textContent = title;
    document.getElementById('customConfirmMessage').textContent = message;
    document.getElementById('customConfirmModal').style.display = 'flex';

    return new Promise((resolve) => {
        resolveConfirm = resolve;
    });
}

function hideCustomConfirm(result) {
    document.getElementById('customConfirmModal').style.display = 'none';
    if (resolveConfirm) {
        resolveConfirm(result);
    }
}

// --- End Custom Modal Functions ---


document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('confirmYes').onclick = () => hideCustomConfirm(true);
    document.getElementById('confirmNo').onclick = () => hideCustomConfirm(false);

    // Function to handle removing from wishlist (AJAX)
    async function removeWishlist(wishlistId) {
        const url = 'wishlist-action.php'; 
        
        let shouldProceed = await showCustomConfirm('Are you sure you want to remove this item from your wishlist?', 'Remove Item');

        if (shouldProceed) {
            const formData = new FormData();
            formData.append('wishlist_id', wishlistId);
            // Use 'remove' which is the expected action name in wishlist_action.php
            // keep compatibility with older clients by also accepting 'remove_by_id' server-side
            formData.append('action', 'remove');
            
            // Exponential backoff retry logic
            let attempt = 0;
            const maxAttempts = 3;
            const delay = (ms) => new Promise(res => setTimeout(res, ms));

            while (attempt < maxAttempts) {
                try {
                    const response = await fetch(url, { method: 'POST', body: formData });
                    const data = await response.json();
                    
                    if (data.status === 'success') {
                        showCustomAlert(data.message, 'Success');
                        location.reload(); 
                        return; 
                    } else {
                        throw new Error(data.message || 'Unknown error');
                    }
                } catch (error) {
                    attempt++;
                    if (attempt >= maxAttempts) {
                        console.error('Final error removing from wishlist:', error);
                        showCustomAlert('Failed to remove item after multiple attempts. Error: ' + error.message, 'Error');
                        return; 
                    }
                    // Retrying
                    await delay(Math.pow(2, attempt) * 1000); 
                }
            }
        }
    }

    // Event listener for "Remove from Wishlist" buttons
    document.querySelectorAll('.remove-from-wishlist').forEach(button => {
        button.addEventListener('click', function() {
            const wishlistId = this.dataset.wishlistId;
            removeWishlist(wishlistId);
        });
    });
});
</script>

<?php require_once('footer.php'); ?>
