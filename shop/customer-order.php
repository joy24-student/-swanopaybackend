<?php require_once('header.php'); ?>

<?php
// Check if the customer is logged in or not
if(!isset($_SESSION['customer'])) {
    header('location: '.BASE_URL.'logout.php');
    exit;
} else {
    // If customer is logged in, but admin make him inactive, then force logout this user.
    $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=? AND cust_status=?");
    $statement->execute(array($_SESSION['customer']['cust_id'],0));
    $total = $statement->rowCount();
    if($total) {
        header('location: '.BASE_URL.'logout.php');
        exit;
    }
}

// Fetch settings for review feature on/off
$statement_settings = $pdo->prepare("SELECT review_feature_on_off FROM tbl_settings WHERE id=1");
$statement_settings->execute();
$settings_data = $statement_settings->fetch(PDO::FETCH_ASSOC);
$review_feature_on_off = $settings_data['review_feature_on_off'] ?? 1;

// Function to check if a product has been reviewed by the current customer
function hasCustomerReviewedProduct($pdo, $customer_id, $product_id) {
    $stmt = $pdo->prepare("SELECT COUNT(*) FROM tbl_review WHERE cust_id = ? AND product_id = ?"); // CHANGED FROM tbl_rating
    $stmt->execute([$customer_id, $product_id]);
    return $stmt->fetchColumn() > 0;
}

?>

<div class="page">
    <div class="container customer-order-container">
        <div class="row">
            <div class="col-md-3">
                <?php require_once('customer-sidebar.php'); ?>
            </div>
            <div class="col-md-9">
                <div class="user-content order-history-content">
                    <h3 class="order-history-title">
                        <i class="fas fa-history"></i> <?php echo LANG_VALUE_25; ?>
                    </h3>

                    <?php
                    $customer_id = $_SESSION['customer']['cust_id'];
                    
                    // Get payment_id from URL if provided (for viewing single order)
                    $specific_payment_id = isset($_GET['payment_id']) ? htmlspecialchars($_GET['payment_id']) : null;
                    
                    if ($specific_payment_id) {
                        // Show single order detail
                        $statement = $pdo->prepare("SELECT * FROM tbl_payment WHERE customer_id=? AND payment_id=?");
                        $statement->execute(array($customer_id, $specific_payment_id));
                        $payments = $statement->fetchAll(PDO::FETCH_ASSOC);
                    } else {
                        // Show all orders
                        $statement = $pdo->prepare("SELECT * FROM tbl_payment WHERE customer_id=? ORDER BY id DESC");
                        $statement->execute(array($customer_id));
                        $payments = $statement->fetchAll(PDO::FETCH_ASSOC);
                    }

                    if (empty($payments)):
                    ?>
                        <div class="alert alert-info text-center" role="alert">
                            You have no orders yet. Start shopping now!
                            <br><a href="<?php echo BASE_URL; ?>index.php" class="btn btn-primary mt-3">Go to Shop</a>
                        </div>
                    <?php else: ?>
                        <div class="order-list">
                            <?php foreach ($payments as $payment): 
                                $order_date = strtotime($payment['payment_date']);
                                $can_cancel = ($payment['shipping_status'] === 'Pending' && (time() - $order_date) < 86400);
                            ?>
                                <div class="order-card animate__animated animate__fadeInUp">
                                    <div class="order-header">
                                        <div class="order-id">
                                            Order ID: <strong><?php echo htmlspecialchars($payment['payment_id']); ?></strong>
                                        </div>
                                        <div class="order-date">
                                            Placed On: <?php echo date('F j, Y g:i A', $order_date); ?>
                                        </div>
                                        <div class="order-status">
                                            Status: <span class="badge <?php
                                                if ($payment['payment_status'] == 'Pending') echo 'badge-warning';
                                                else if ($payment['payment_status'] == 'Completed') echo 'badge-success';
                                                else if ($payment['payment_status'] == 'Cancelled') echo 'badge-danger';
                                                else echo 'badge-info';
                                            ?>"><?php echo htmlspecialchars($payment['payment_status']); ?></span>
                                            <span class="badge <?php
                                                if ($payment['shipping_status'] == 'Pending') echo 'badge-warning';
                                                else if ($payment['shipping_status'] == 'Shipped') echo 'badge-primary';
                                                else if ($payment['shipping_status'] == 'Delivered') echo 'badge-success';
                                                else if ($payment['shipping_status'] == 'Cancelled') echo 'badge-danger';
                                                else echo 'badge-secondary';
                                            ?>"><?php echo htmlspecialchars($payment['shipping_status']); ?></span>
                                        </div>
                                    </div>

                                    <!-- ORDER TRACKER -->
                                    <div style="background: #f9f9f9; padding: 20px; border-radius: 8px; margin: 15px 0;">
                                        <h5 style="margin-top: 0; margin-bottom: 15px;">Order Status Tracker</h5>
                                        <div style="display: flex; align-items: center; gap: 0; position: relative;">
                                            <!-- Step 1: Order Placed -->
                                            <div style="flex: 1; text-align: center;">
                                                <div style="width: 40px; height: 40px; background: #ff6a00; border-radius: 50%; margin: 0 auto 10px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">
                                                    ✓
                                                </div>
                                                <div style="font-size: 12px; color: #666;">Order Placed</div>
                                                <div style="font-size: 11px; color: #999;"><?php echo date('M d, Y', $order_date); ?></div>
                                            </div>
                                            
                                            <!-- Connector 1 -->
                                            <div style="flex: 1; height: 2px; background: <?php echo ($payment['shipping_status'] !== 'Pending') ? '#ff6a00' : '#ddd'; ?>; position: relative; top: -20px;"></div>
                                            
                                            <!-- Step 2: Processing -->
                                            <div style="flex: 1; text-align: center;">
                                                <div style="width: 40px; height: 40px; background: <?php echo ($payment['shipping_status'] !== 'Pending') ? '#ff6a00' : '#ddd'; ?>; border-radius: 50%; margin: 0 auto 10px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">
                                                    <?php echo ($payment['shipping_status'] !== 'Pending') ? '✓' : '2'; ?>
                                                </div>
                                                <div style="font-size: 12px; color: #666;">Being Prepared</div>
                                                <div style="font-size: 11px; color: #999;">By Seller</div>
                                            </div>
                                            
                                            <!-- Connector 2 -->
                                            <div style="flex: 1; height: 2px; background: <?php echo ($payment['shipping_status'] === 'Shipped' || $payment['shipping_status'] === 'Delivered') ? '#ff6a00' : '#ddd'; ?>; position: relative; top: -20px;"></div>
                                            
                                            <!-- Step 3: Shipped -->
                                            <div style="flex: 1; text-align: center;">
                                                <div style="width: 40px; height: 40px; background: <?php echo ($payment['shipping_status'] === 'Shipped' || $payment['shipping_status'] === 'Delivered') ? '#ff6a00' : '#ddd'; ?>; border-radius: 50%; margin: 0 auto 10px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">
                                                    <?php echo ($payment['shipping_status'] === 'Shipped' || $payment['shipping_status'] === 'Delivered') ? '✓' : '3'; ?>
                                                </div>
                                                <div style="font-size: 12px; color: #666;">Shipped</div>
                                                <div style="font-size: 11px; color: #999;">In Transit</div>
                                            </div>
                                            
                                            <!-- Connector 3 -->
                                            <div style="flex: 1; height: 2px; background: <?php echo ($payment['shipping_status'] === 'Delivered') ? '#ff6a00' : '#ddd'; ?>; position: relative; top: -20px;"></div>
                                            
                                            <!-- Step 4: Delivered -->
                                            <div style="flex: 1; text-align: center;">
                                                <div style="width: 40px; height: 40px; background: <?php echo ($payment['shipping_status'] === 'Delivered') ? '#ff6a00' : '#ddd'; ?>; border-radius: 50%; margin: 0 auto 10px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold;">
                                                    <?php echo ($payment['shipping_status'] === 'Delivered') ? '✓' : '4'; ?>
                                                </div>
                                                <div style="font-size: 12px; color: #666;">Delivered</div>
                                                <div style="font-size: 11px; color: #999;">To You</div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- CANCELLATION OPTION -->
                                    <?php if ($can_cancel): ?>
                                    <div style="background: #fff3cd; border: 1px solid #ffc107; padding: 12px; border-radius: 6px; margin-bottom: 15px;">
                                        <strong style="color: #856404;">⏱ Cancel Window Open</strong><br>
                                        <small style="color: #856404;">You can cancel this order within 24 hours. Expires in: <?php echo round((86400 - (time() - $order_date)) / 3600); ?> hour(s)</small><br>
                                        <button type="button" class="btn btn-sm" style="background-color: #ff6a00; color: white; margin-top: 8px; border: none; padding: 5px 10px; border-radius: 4px; cursor: pointer;" onclick="cancelOrder('<?php echo htmlspecialchars($payment['payment_id']); ?>')">Cancel Order</button>
                                    </div>
                                    <?php endif; ?>

                                    <!-- OTP SECTION FOR DELIVERY -->
                                    <?php if ($payment['shipping_status'] === 'Shipped'): ?>
                                    <div style="background: #e7f3ff; border: 1px solid #90caf9; padding: 15px; border-radius: 6px; margin-bottom: 15px;">
                                        <h5 style="margin-top: 0; color: #1976d2;">📦 Delivery OTP</h5>
                                        <p style="margin: 10px 0; color: #555;">
                                            Your delivery OTP will be provided when the package arrives at your location. The delivery partner will ask for this OTP to confirm delivery.
                                        </p>
                                        <div style="background: white; padding: 10px; border-radius: 4px; border: 2px dashed #90caf9;">
                                            <strong style="font-size: 14px; color: #666;">OTP:</strong>
                                            <div style="font-size: 28px; font-weight: bold; color: #1976d2; letter-spacing: 5px; margin-top: 5px; font-family: 'Courier New', monospace;">
                                                <?php 
                                                // Generate consistent OTP based on payment_id
                                                $otp = substr(str_pad(hexdec(substr(md5($payment['payment_id']), 0, 8)) % 1000000, 6, '0', STR_PAD_LEFT), 0, 6);
                                                echo htmlspecialchars($otp);
                                                ?>
                                            </div>
                                        </div>
                                        <small style="color: #1976d2; display: block; margin-top: 10px;">
                                            💡 Keep this OTP confidential and share only with the delivery partner
                                        </small>
                                    </div>
                                    <?php endif; ?>

                                    <div class="order-body">
                                        <?php
                                        $statement1 = $pdo->prepare("SELECT T1.*, T2.p_featured_photo FROM tbl_order T1 JOIN tbl_product T2 ON T1.product_id = T2.p_id WHERE T1.payment_id=?");
                                        $statement1->execute(array($payment['payment_id']));
                                        $order_products = $statement1->fetchAll(PDO::FETCH_ASSOC);

                                        foreach ($order_products as $product):
                                            $has_reviewed = hasCustomerReviewedProduct($pdo, $customer_id, $product['product_id']);
                                        ?>
                                            <div class="order-item">
                                                <div class="item-image">
                                                    <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($product['p_featured_photo']); ?>" alt="<?php echo htmlspecialchars($product['product_name']); ?>">
                                                </div>
                                                <div class="item-details">
                                                    <h5 class="item-name"><?php echo htmlspecialchars($product['product_name']); ?></h5>
                                                    <p class="item-meta">
                                                        Size: <?php echo htmlspecialchars($product['size']); ?>,
                                                        Color: <?php echo htmlspecialchars($product['color']); ?>
                                                    </p>
                                                    <p class="item-qty-price">
                                                        Qty: <?php echo htmlspecialchars($product['quantity']); ?> x <?php echo formatCurrency($product['unit_price']); ?>
                                                    </p>
                                                </div>
                                                <div class="item-actions">
                                                    <?php if ($payment['shipping_status'] == 'Shipped' || $payment['shipping_status'] == 'Delivered'): ?>
                                                        <?php if ($review_feature_on_off == 1 && !$has_reviewed): ?>
                                                            <button class="btn btn-sm btn-review" data-product-id="<?php echo htmlspecialchars($product['product_id']); ?>" data-product-name="<?php echo htmlspecialchars($product['product_name']); ?>">
                                                                <i class="fas fa-star"></i> Write Review
                                                            </button>
                                                        <?php elseif ($review_feature_on_off == 1 && $has_reviewed): ?>
                                                            <button class="btn btn-sm btn-secondary" disabled>
                                                                <i class="fas fa-check-circle"></i> Reviewed
                                                            </button>
                                                        <?php endif; ?>
                                                    <?php endif; ?>
                                                </div>
                                            </div>
                                        <?php endforeach; ?>
                                    </div>

                                    <div class="order-footer">
                                        <div class="total-amount">
                                            Total: <strong><?php echo formatCurrency($payment['paid_amount']); ?></strong>
                                        </div>
                                        <div class="payment-method">
                                            Method: <?php echo htmlspecialchars($payment['payment_method']); ?>
                                        </div>
                                        <div class="invoice-link">
                                            <a href="<?php echo BASE_URL; ?>payment_success.php?method=<?php echo ($payment['payment_method'] == 'Cash on Delivery' ? 'cod' : 'sslcommerz'); ?>&tran_id=<?php echo htmlspecialchars($payment['payment_id']); ?>&session_id=<?php echo session_id(); ?>" target="_blank" class="btn btn-sm btn-info">
                                                <i class="fas fa-file-invoice"></i> View Invoice
                                            </a>
                                        </div>
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>
                    <?php endif; ?>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- Review Modal -->
<div class="modal fade" id="reviewModal" tabindex="-1" role="dialog" aria-labelledby="reviewModalLabel" aria-hidden="true">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="reviewModalLabel">Write a Review for <span id="reviewProductName"></span></h5>
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="modal-body">
                <form id="reviewForm">
                    <?php $csrf->echoInputField(); ?>
                    <input type="hidden" id="reviewProductId" name="product_id">
                    <div class="form-group rating-input">
                        <label>Your Rating *</label>
                        <div class="stars">
                            <input type="radio" id="modal_star5" name="rating" value="5" /><label for="modal_star5" title="5 stars"></label>
                            <input type="radio" id="modal_star4" name="rating" value="4" /><label for="modal_star4" title="4 stars"></label>
                            <input type="radio" id="modal_star3" name="rating" value="3" /><label for="modal_star3" title="3 stars"></label>
                            <input type="radio" id="modal_star2" name="rating" value="2" /><label for="modal_star2" title="2 stars"></label>
                            <input type="radio" id="modal_star1" name="rating" value="1" /><label for="modal_star1" title="1 star"></label>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="modal_review_title">Review Title *</label>
                        <input type="text" name="review_title" id="modal_review_title" class="form-control">
                    </div>
                    <div class="form-group">
                        <label for="modal_comment">Your Comment *</label>
                        <textarea name="comment" id="modal_comment" class="form-control" rows="5"></textarea>
                    </div>
                    <div id="reviewFormMessage" class="mt-3" style="display:none;"></div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
                <button type="button" class="btn btn-primary" id="submitReviewBtn">Submit Review</button>
            </div>
        </div>
    </div>
</div>

<style>
/* Add this CSS to your assets/css/style.css */

.customer-order-container {
    padding-top: 30px;
    padding-bottom: 30px;
}

.order-history-content {
    background-color: #fff;
    padding: 30px;
    border-radius: 8px;
    box-shadow: 0 0 15px rgba(0,0,0,0.05);
}

.order-history-title {
    text-align: center;
    color: #333;
    margin-bottom: 40px;
    font-size: 2em;
    border-bottom: 2px solid #f14040;
    padding-bottom: 15px;
    animation: slideInDown 0.8s ease-out;
}

.order-history-title i {
    margin-right: 10px;
    color: #f14040;
}

/* Order Card Layout */
.order-list {
    display: flex;
    flex-direction: column;
    gap: 25px;
}

.order-card {
    background-color: #fdfdfd;
    border: 1px solid #eee;
    border-radius: 10px;
    box-shadow: 0 4px 15px rgba(0,0,0,0.05);
    overflow: hidden;
    transition: all 0.3s ease;
}

.order-card:hover {
    box-shadow: 0 8px 25px rgba(0,0,0,0.1);
    transform: translateY(-3px);
}

.order-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #f8f8f8;
    padding: 15px 20px;
    border-bottom: 1px solid #eee;
    flex-wrap: wrap;
    gap: 10px;
}

.order-header .order-id,
.order-header .order-date,
.order-header .order-status {
    font-size: 0.95em;
    color: #555;
    font-weight: 500;
}

.order-header .order-id strong {
    color: #f14040;
}

.order-header .badge {
    padding: 5px 10px;
    border-radius: 5px;
    font-size: 0.85em;
    font-weight: bold;
    margin-left: 5px;
}

.badge-warning { background-color: #ffc107; color: #343a40; }
.badge-success { background-color: #28a745; color: #fff; }
.badge-info { background-color: #17a2b8; color: #fff; }
.badge-primary { background-color: #007bff; color: #fff; }
.badge-secondary { background-color: #6c757d; color: #fff; }

.order-body {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 15px;
}

.order-item {
    display: flex;
    align-items: center;
    gap: 15px;
    padding: 10px;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    background-color: #fff;
    box-shadow: 0 2px 5px rgba(0,0,0,0.02);
}

.order-item .item-image {
    flex-shrink: 0;
    width: 80px;
    height: 80px;
    border-radius: 5px;
    overflow: hidden;
    background-color: #f8f8f8;
    display: flex;
    align-items: center;
    justify-content: center;
}

.order-item .item-image img {
    width: 100%;
    height: 100%;
    object-fit: contain;
}

.order-item .item-details {
    flex-grow: 1;
}

.order-item .item-name {
    font-size: 1.1em;
    font-weight: 600;
    color: #333;
    margin-bottom: 5px;
}

.order-item .item-meta {
    font-size: 0.85em;
    color: #777;
    margin-bottom: 5px;
}

.order-item .item-qty-price {
    font-size: 0.95em;
    font-weight: bold;
    color: #f14040;
}

.order-item .item-actions {
    flex-shrink: 0;
    text-align: right;
}

.order-item .btn-review {
    background-color: #007bff;
    border-color: #007bff;
    color: #fff;
    padding: 8px 15px;
    border-radius: 5px;
    font-size: 0.9em;
    transition: all 0.3s ease;
    display: inline-flex;
    align-items: center;
    gap: 5px;
}
.order-item .btn-review:hover {
    background-color: #0056b3;
    border-color: #0056b3;
    transform: translateY(-1px);
}
.order-item .btn-review i {
    margin-right: 5px;
}

.order-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background-color: #f8f8f8;
    padding: 15px 20px;
    border-top: 1px solid #eee;
    flex-wrap: wrap;
    gap: 10px;
}

.order-footer .total-amount,
.order-footer .payment-method,
.order-footer .invoice-link {
    font-size: 0.95em;
    color: #555;
}

.order-footer .total-amount strong {
    color: #f14040;
    font-size: 1.1em;
}

.order-footer .btn-info {
    background-color: #17a2b8;
    border-color: #17a2b8;
    color: #fff;
    padding: 8px 15px;
    border-radius: 5px;
    font-size: 0.9em;
    transition: all 0.3s ease;
    display: inline-flex;
    align-items: center;
    gap: 5px;
}
.order-footer .btn-info:hover {
    background-color: #138496;
    border-color: #117a8b;
    transform: translateY(-1px);
}

/* Review Modal Styling (similar to product.php) */
.modal-content {
    border-radius: 10px;
    box-shadow: 0 5px 15px rgba(0,0,0,0.3);
}
.modal-header {
    background-color: #f14040;
    color: #fff;
    border-top-left-radius: 10px;
    border-top-right-radius: 10px;
}
.modal-header .close {
    color: #fff;
    opacity: 0.8;
}
.modal-header .close:hover {
    opacity: 1;
}
.modal-title {
    font-weight: 600;
}
.modal-body .rating-input .stars {
    display: inline-block;
    direction: rtl; /* For right-to-left star display */
}
.modal-body .rating-input .stars input[type="radio"] {
    display: none;
}
.modal-body .rating-input .stars label {
    color: #aaa;
    font-size: 2em;
    padding: 0 5px;
    cursor: pointer;
    transition: color 0.2s ease;
}
.modal-body .rating-input .stars label:hover,
.modal-body .rating-input .stars label:hover ~ label,
.modal-body .rating-input .stars input[type="radio"]:checked ~ label {
    color: #f1c40f;
}
.modal-footer .btn-primary {
    background-color: #f14040;
    border-color: #f14040;
    transition: background-color 0.3s ease;
}
.modal-footer .btn-primary:hover {
    background-color: #d63434;
    border-color: #d63434;
}

/* Responsive adjustments */
@media (max-width: 767px) {
    .order-history-content {
        padding: 15px;
    }
    .order-history-title {
        font-size: 1.5em;
        margin-bottom: 20px;
    }
    .order-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 5px;
    }
    .order-item {
        flex-direction: column;
        align-items: flex-start;
    }
    .order-item .item-details {
        width: 100%;
        text-align: left;
    }
    .order-item .item-actions {
        width: 100%;
        text-align: left;
        margin-top: 10px;
    }
    .order-footer {
        flex-direction: column;
        align-items: flex-start;
        gap: 5px;
    }
    .order-footer .total-amount,
    .order-footer .payment-method,
    .order-footer .invoice-link {
        width: 100%;
        text-align: left;
    }
}

/* Animation for cards */
@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translate3d(0, -20px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

.animate__animated.animate__fadeInUp {
    animation-name: fadeInUp;
    animation-duration: 1s; /* Adjust duration as needed */
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translate3d(0, 20px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}
</style>

<script>
// Cancel order function
function cancelOrder(paymentId) {
    if (confirm('Are you sure you want to cancel this order? This action cannot be undone.')) {
        // AJAX call to cancel order
        fetch('<?php echo BASE_URL; ?>ajax/cancel-order.php', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: 'payment_id=' + encodeURIComponent(paymentId)
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                alert(data.message);
                location.reload();
            } else {
                alert('Error: ' + (data.message || 'Failed to cancel order'));
            }
        })
        .catch(error => {
            console.error('Error:', error);
            alert('Error canceling order');
        });
    }
}

// Add this JavaScript to your assets/js/main.js

document.addEventListener('DOMContentLoaded', function() {
    // --- Review Modal Logic ---
    const reviewModal = document.getElementById('reviewModal');
    const reviewProductNameSpan = document.getElementById('reviewProductName');
    const reviewProductIdInput = document.getElementById('reviewProductId');
    const submitReviewBtn = document.getElementById('submitReviewBtn');
    const reviewFormMessage = document.getElementById('reviewFormMessage');
    const reviewForm = document.getElementById('reviewForm');

    // Open review modal when "Write Review" button is clicked
    document.querySelectorAll('.btn-review').forEach(button => {
        button.addEventListener('click', function() {
            const productId = this.dataset.productId;
            const productName = this.dataset.productName;

            reviewProductNameSpan.textContent = productName;
            reviewProductIdInput.value = productId;

            // Clear previous form data and messages
            reviewForm.reset();
            reviewFormMessage.style.display = 'none';
            reviewFormMessage.className = 'mt-3'; // Reset classes

            // Show the modal
            $('#reviewModal').modal('show');
        });
    });

    // Handle review form submission
    if (submitReviewBtn) {
        submitReviewBtn.addEventListener('click', function() {
            const productId = reviewProductIdInput.value;
            const rating = reviewForm.querySelector('input[name="rating"]:checked');
            const reviewTitle = document.getElementById('modal_review_title').value.trim();
            const comment = document.getElementById('modal_comment').value.trim();

            if (!rating) {
                displayReviewMessage('Please select a rating.', 'alert-danger');
                return;
            }
            if (!reviewTitle) {
                displayReviewMessage('Review title is required.', 'alert-danger');
                return;
            }
            if (!comment) {
                displayReviewMessage('Comment is required.', 'alert-danger');
                return;
            }

            // Disable button and show loading
            submitReviewBtn.disabled = true;
            submitReviewBtn.textContent = 'Submitting...';

            // AJAX call to submit review
            fetch('<?php echo BASE_URL; ?>submit_review.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `product_id=${encodeURIComponent(productId)}&rating=${encodeURIComponent(rating.value)}&review_title=${encodeURIComponent(reviewTitle)}&comment=${encodeURIComponent(comment)}&csrf_token=<?php echo $csrf->getTokenValue() ?? ''; ?>` // Added ?? '' for safety
            })
            .then(response => response.json())
            .then(data => {
                submitReviewBtn.disabled = false;
                submitReviewBtn.textContent = 'Submit Review';
                if (data.status === 'success') {
                    displayReviewMessage(data.message, 'alert-success');
                    // Optionally, close modal after a short delay or reload page
                    setTimeout(() => {
                        $('#reviewModal').modal('hide');
                        location.reload(); // Reload to update "Reviewed" button
                    }, 1500);
                } else {
                    displayReviewMessage('Error: ' + data.message, 'alert-danger');
                }
            })
            .catch(error => {
                console.error('Error submitting review:', error);
                submitReviewBtn.disabled = false;
                submitReviewBtn.textContent = 'Submit Review';
                displayReviewMessage('An error occurred while submitting your review.', 'alert-danger');
            });
        });
    }

    function displayReviewMessage(message, typeClass) {
        reviewFormMessage.textContent = message;
        reviewFormMessage.className = `mt-3 alert ${typeClass}`;
        reviewFormMessage.style.display = 'block';
    }
});
</script>

<?php require_once('footer.php'); ?>