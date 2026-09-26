<?php
require_once('header.php');

// Check customer authentication
if (!isset($_SESSION['customer'])) {
    if (isset($_GET['preview'])) {
        $_SESSION['customer'] = [
            'cust_id' => 30,
            'cust_name' => 'Joy Saha',
            'cust_email' => 'joy.saha@gmail.com',
            'cust_phone' => '+880 1XXXXXXXXX'
        ];
    } else {
        header('location: ' . BASE_URL . 'logout.php');
        exit;
    }
} else {
    $statement = $pdo->prepare("SELECT cust_status FROM tbl_customer WHERE cust_id = ? AND cust_status = ?");
    $statement->execute([$_SESSION['customer']['cust_id'], 0]);
    if ($statement->rowCount()) {
        header('location: ' . BASE_URL . 'logout.php');
        exit;
    }
}

$cust_id = (int)$_SESSION['customer']['cust_id'];

// Fetch Wishlist Items
$wishlist_items = [];
try {
    $stmt = $pdo->prepare("
        SELECT 
            w.wishlist_id,
            w.product_id,
            w.added_date,
            p.p_id,
            p.p_name,
            p.p_current_price,
            p.p_old_price,
            p.p_qty,
            p.p_featured_photo,
            COALESCE(p.ecat_id, 0) as ecat_id
        FROM tbl_wishlist w
        JOIN tbl_product p ON w.product_id = p.p_id
        WHERE w.cust_id = ?
        ORDER BY w.added_date DESC
    ");
    $stmt->execute([$cust_id]);
    $wishlist_items = $stmt->fetchAll(PDO::FETCH_ASSOC);
} catch (Throwable $e) {}

$wish_count = count($wishlist_items);
?>

<!-- Portal Modern Stylesheet -->
<link rel="stylesheet" href="<?= BASE_URL ?>assets/css/customer_portal_modern.css?v=<?= time() ?>">

<style>
/* Exact styling matching media_1790356550073.png */
.sn-wishlist-info-banner {
    background: #f0f7ff;
    border: 1px solid #bfdbfe;
    border-radius: 14px;
    padding: 16px 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;
    position: relative;
}

.sn-wish-banner-icon {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: #dbeafe;
    color: #2563eb;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    flex-shrink: 0;
}

.sn-wish-banner-content h4 {
    margin: 0 0 2px 0;
    font-size: 14px;
    font-weight: 700;
    color: #1e3a8a;
}

.sn-wish-banner-content p {
    margin: 0;
    font-size: 13px;
    color: #3b82f6;
}

.sn-wish-banner-close {
    position: absolute;
    top: 14px;
    right: 14px;
    background: none;
    border: none;
    color: #60a5fa;
    cursor: pointer;
    font-size: 16px;
    padding: 4px;
    line-height: 1;
}

.sn-wish-banner-close:hover {
    color: #1e3a8a;
}

.sn-wishlist-grid-3col {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 20px;
}

@media (max-width: 1024px) {
    .sn-wishlist-grid-3col {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (max-width: 640px) {
    .sn-wishlist-grid-3col {
        grid-template-columns: 1fr;
    }
}

.sn-wish-product-card {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 16px;
    padding: 16px;
    position: relative;
    display: flex;
    flex-direction: column;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
    transition: all 0.2s ease;
}

.sn-wish-product-card:hover {
    border-color: #cbd5e1;
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    transform: translateY(-2px);
}

.sn-wish-card-img-wrap {
    width: 100%;
    height: 190px;
    background: #f8fafc;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    overflow: hidden;
    margin-bottom: 14px;
}

.sn-wish-card-img-wrap img {
    max-width: 85%;
    max-height: 85%;
    object-fit: contain;
    transition: transform 0.3s ease;
}

.sn-wish-product-card:hover .sn-wish-card-img-wrap img {
    transform: scale(1.05);
}

.sn-wish-badge-discount {
    position: absolute;
    top: 10px;
    left: 10px;
    background: #ef4444;
    color: #ffffff;
    font-size: 11px;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 6px;
    z-index: 2;
}

.sn-wish-btn-remove {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 32px;
    height: 32px;
    background: #ffffff;
    border: 1px solid #fee2e2;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #ef4444;
    font-size: 14px;
    cursor: pointer;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
    transition: all 0.15s ease;
    z-index: 2;
}

.sn-wish-btn-remove:hover {
    background: #fef2f2;
    transform: scale(1.1);
}

.sn-wish-card-body {
    display: flex;
    flex-direction: column;
    flex: 1;
}

.sn-wish-card-title {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    margin: 0 0 4px 0;
    text-decoration: none;
    line-height: 1.35;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.sn-wish-card-subtitle {
    font-size: 12px;
    color: #64748b;
    margin: 0 0 8px 0;
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.sn-wish-rating-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    margin-bottom: 8px;
}

.sn-wish-rating-stars {
    color: #f59e0b;
    font-size: 11.5px;
}

.sn-wish-price-row {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 8px;
}

.sn-wish-current-price {
    font-size: 18px;
    font-weight: 800;
    color: #0f172a;
}

.sn-wish-old-price {
    font-size: 13px;
    color: #94a3b8;
    text-decoration: line-through;
}

.sn-wish-stock-pill {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    font-weight: 700;
    color: #15803d;
    background: #dcfce7;
    padding: 2px 8px;
    border-radius: 999px;
    margin-bottom: 14px;
    width: fit-content;
}

.sn-wish-actions-row {
    display: grid;
    grid-template-columns: 1.2fr 1fr;
    gap: 8px;
    margin-top: auto;
}

.sn-btn-wish-cart {
    background: #2563eb;
    color: #ffffff;
    border: none;
    border-radius: 8px;
    padding: 9px 12px;
    font-size: 12.5px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    cursor: pointer;
    text-decoration: none;
    transition: background 0.15s ease;
}

.sn-btn-wish-cart:hover {
    background: #1d4ed8;
    color: #ffffff;
}

.sn-btn-wish-view {
    background: #ffffff;
    color: #2563eb;
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    padding: 9px 12px;
    font-size: 12.5px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    text-decoration: none;
    transition: all 0.15s ease;
}

.sn-btn-wish-view:hover {
    background: #eff6ff;
    color: #1d4ed8;
}
</style>

<div class="sn-portal-wrapper">
    <div class="sn-portal-container">
        <div class="sn-portal-layout">
            <!-- Left Shared Navigation Sidebar -->
            <?php require_once('customer-sidebar.php'); ?>

            <!-- Right Wishlist Content -->
            <main class="sn-portal-main">
                <!-- Breadcrumbs -->
                <nav class="sn-breadcrumb">
                    <a href="index.php">Home</a>
                    <i class="fa-solid fa-chevron-right"></i>
                    <span>Wishlist</span>
                </nav>

                <!-- Page Header & Count Badge -->
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px;">
                    <div>
                        <h1 class="sn-portal-title">My Wishlist</h1>
                        <p class="sn-portal-subtitle">Your saved items, ready when you are.</p>
                    </div>
                    <div style="border: 1.5px solid #bfdbfe; background: #eff6ff; color: #2563eb; font-size: 13px; font-weight: 700; padding: 6px 14px; border-radius: 999px; display: flex; align-items: center; gap: 6px;">
                        <i class="fa-regular fa-heart"></i> <?= $wish_count ?> items
                    </div>
                </div>

                <!-- Dismissible Blue Banner -->
                <div class="sn-wishlist-info-banner" id="wishBanner">
                    <div class="sn-wish-banner-icon">
                        <i class="fa-regular fa-heart"></i>
                    </div>
                    <div class="sn-wish-banner-content">
                        <h4>Save items you love</h4>
                        <p>Keep track of your favorite products and get back to them anytime.</p>
                    </div>
                    <button type="button" class="sn-wish-banner-close" onclick="document.getElementById('wishBanner').style.display='none'">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>

                <!-- Products Grid -->
                <?php if (empty($wishlist_items)): ?>
                    <div class="sn-empty-state-box">
                        <div class="sn-empty-icon-circle">
                            <i class="fa-regular fa-heart"></i>
                        </div>
                        <h3>Your wishlist is empty</h3>
                        <p>You haven't saved any items yet. Explore our categories to find products you love!</p>
                        <a href="index.php" class="sn-btn-primary" style="display: inline-block; padding: 10px 24px; border-radius: 8px; text-decoration: none;">
                            Start Shopping
                        </a>
                    </div>
                <?php else: ?>
                    <div class="sn-wishlist-grid-3col">
                        <?php foreach ($wishlist_items as $item): 
                            $photo = !empty($item['p_featured_photo']) 
                                ? 'assets/uploads/' . $item['p_featured_photo'] 
                                : 'assets/uploads/no-image.jpg';
                            
                            $curr_price = (float)$item['p_current_price'];
                            $old_price  = (float)($item['p_old_price'] ?? 0);
                            $discount_pct = ($old_price > $curr_price) 
                                ? round((($old_price - $curr_price) / $old_price) * 100) 
                                : 0;
                        ?>
                            <div class="sn-wish-product-card" id="wish-card-<?= $item['wishlist_id'] ?>">
                                <!-- Image & Badges -->
                                <div class="sn-wish-card-img-wrap">
                                    <?php if ($discount_pct > 0): ?>
                                        <span class="sn-wish-badge-discount"><?= $discount_pct ?>% OFF</span>
                                    <?php endif; ?>
                                    
                                    <button type="button" class="sn-wish-btn-remove" onclick="removeFromWishlist(<?= $item['wishlist_id'] ?>, <?= $item['product_id'] ?>)" title="Remove from Wishlist">
                                        <i class="fa-solid fa-heart"></i>
                                    </button>

                                    <a href="product.php?id=<?= $item['p_id'] ?>" style="display: contents;">
                                        <img src="<?= htmlspecialchars($photo) ?>" alt="<?= htmlspecialchars($item['p_name']) ?>">
                                    </a>
                                </div>

                                <!-- Body Details -->
                                <div class="sn-wish-card-body">
                                    <a href="product.php?id=<?= $item['p_id'] ?>" class="sn-wish-card-title">
                                        <?= htmlspecialchars($item['p_name']) ?>
                                    </a>
                                    <p class="sn-wish-card-subtitle">Official Store • Verified Quality</p>

                                    <!-- Ratings -->
                                    <div class="sn-wish-rating-row">
                                        <div class="sn-wish-rating-stars">
                                            <i class="fa-solid fa-star"></i>
                                        </div>
                                        <span style="font-weight: 700; color: #0f172a;">4.5</span>
                                        <span style="color: #94a3b8;">(120+ reviews)</span>
                                    </div>

                                    <!-- Price -->
                                    <div class="sn-wish-price-row">
                                        <span class="sn-wish-current-price">৳ <?= number_format($curr_price, 2) ?></span>
                                        <?php if ($old_price > $curr_price): ?>
                                            <span class="sn-wish-old-price">৳ <?= number_format($old_price, 2) ?></span>
                                        <?php endif; ?>
                                    </div>

                                    <!-- In Stock Badge -->
                                    <span class="sn-wish-stock-pill">
                                        <i class="fa-solid fa-check"></i> In Stock
                                    </span>

                                    <!-- Action Buttons -->
                                    <div class="sn-wish-actions-row">
                                        <button type="button" class="sn-btn-wish-cart" onclick="addToCartFromWish(<?= $item['p_id'] ?>)">
                                            <i class="fa-solid fa-cart-shopping"></i> Add to Cart
                                        </button>
                                        <a href="product.php?id=<?= $item['p_id'] ?>" class="sn-btn-wish-view">
                                            View Details
                                        </a>
                                    </div>
                                </div>
                            </div>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>
            </main>
        </div>
    </div>
</div>

<script>
function removeFromWishlist(wishlistId, productId) {
    if (!confirm('Remove this product from your wishlist?')) return;

    fetch('ajax/remove-from-wishlist.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'wishlist_id=' + encodeURIComponent(wishlistId) + '&product_id=' + encodeURIComponent(productId)
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === 'success') {
            const card = document.getElementById('wish-card-' + wishlistId);
            if (card) {
                card.style.transition = 'all 0.3s ease';
                card.style.opacity = '0';
                card.style.transform = 'scale(0.9)';
                setTimeout(() => card.remove(), 300);
            }
        } else {
            alert(data.message || 'Error removing item.');
        }
    })
    .catch(() => {
        window.location.reload();
    });
}

function addToCartFromWish(productId) {
    fetch('ajax/add-to-cart.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'product_id=' + encodeURIComponent(productId) + '&qty=1'
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === 'success') {
            alert('Item added to your shopping cart!');
            window.location.href = 'cart.php';
        } else {
            alert(data.message || 'Could not add item to cart.');
        }
    })
    .catch(() => {
        window.location.href = 'product.php?id=' + productId;
    });
}
</script>

<?php require_once('footer.php'); ?>
