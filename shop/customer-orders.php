<?php require_once('header.php'); ?>

<?php
// Check if the customer is logged in
if(!isset($_SESSION['customer'])) {
    header('location: '.BASE_URL.'logout.php');
    exit;
} else {
    // If customer is logged in, but admin made them inactive, force logout
    $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=? AND cust_status=?");
    $statement->execute(array($_SESSION['customer']['cust_id'], 0));
    if($statement->rowCount()) {
        header('location: '.BASE_URL.'logout.php');
        exit;
    }
}

$customer_id = $_SESSION['customer']['cust_id'];
$status_filter = isset($_GET['status']) ? htmlspecialchars($_GET['status']) : null;
?>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-3">
                <?php require_once('customer-sidebar.php'); ?>
            </div>
            <div class="col-md-9">
                <div class="user-content">
                    <h3 style="margin-bottom: 20px;">
                        <i class="fas fa-shopping-bag"></i> My Orders
                    </h3>

                    <?php
                    // Fetch orders based on status filter
                    try {
                        // Get unique payments (orders) for the customer with order details
                        $query = "
                            SELECT DISTINCT
                                p.id,
                                p.payment_id,
                                p.payment_date,
                                p.payment_status,
                                p.shipping_status,
                                p.paid_amount,
                                COUNT(o.id) as item_count
                            FROM tbl_payment p
                            LEFT JOIN tbl_order o ON p.payment_id = o.payment_id
                            WHERE p.customer_id = ?
                        ";
                        
                        $params = array($customer_id);

                        // Filter by shipping/payment status
                        if ($status_filter) {
                            switch($status_filter) {
                                case 'to_cancel':
                                    // Orders within 24 hours that haven't shipped yet
                                    $query .= " AND p.shipping_status = 'Pending' AND p.payment_status = 'Completed' AND p.payment_date >= NOW() - INTERVAL '24 HOUR'";
                                    break;
                                case 'to_ship':
                                    // Orders ready to ship
                                    $query .= " AND p.shipping_status = 'Pending' AND p.payment_status = 'Completed'";
                                    break;
                                case 'to_receive':
                                    // Shipped orders not yet delivered
                                    $query .= " AND p.shipping_status = 'Shipped'";
                                    break;
                                case 'to_review':
                                    // Delivered orders
                                    $query .= " AND p.shipping_status = 'Delivered'";
                                    break;
                            }
                        }

                        $query .= " GROUP BY p.id, p.payment_id, p.payment_date, p.payment_status, p.shipping_status, p.paid_amount ORDER BY p.id DESC";

                        $statement = $pdo->prepare($query);
                        $statement->execute($params);
                        $orders = $statement->fetchAll(PDO::FETCH_ASSOC);

                        if (empty($orders)):
                    ?>
                        <div class="alert alert-info text-center">
                            <p>No orders found. <a href="<?php echo BASE_URL; ?>index.php">Continue Shopping</a></p>
                        </div>
                    <?php else: ?>
                        <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                            <div style="margin-bottom: 15px; padding-bottom: 15px; border-bottom: 2px solid #f0f0f0;">
                                <strong style="font-size: 14px;">Filter:</strong><br>
                                <a href="customer-orders.php" style="text-decoration: none; margin-right: 15px; color: <?php echo !$status_filter ? '#ff6a00' : '#666'; ?>; font-weight: <?php echo !$status_filter ? 'bold' : 'normal'; ?>;">All Orders</a>
                                <a href="customer-orders.php?status=to_cancel" style="text-decoration: none; margin-right: 15px; color: <?php echo $status_filter === 'to_cancel' ? '#ff6a00' : '#666'; ?>; font-weight: <?php echo $status_filter === 'to_cancel' ? 'bold' : 'normal'; ?>;">Cancellable (24h)</a>
                                <a href="customer-orders.php?status=to_ship" style="text-decoration: none; margin-right: 15px; color: <?php echo $status_filter === 'to_ship' ? '#ff6a00' : '#666'; ?>; font-weight: <?php echo $status_filter === 'to_ship' ? 'bold' : 'normal'; ?>;">To Ship</a>
                                <a href="customer-orders.php?status=to_receive" style="text-decoration: none; margin-right: 15px; color: <?php echo $status_filter === 'to_receive' ? '#ff6a00' : '#666'; ?>; font-weight: <?php echo $status_filter === 'to_receive' ? 'bold' : 'normal'; ?>;">To Receive</a>
                                <a href="customer-orders.php?status=to_review" style="text-decoration: none; color: <?php echo $status_filter === 'to_review' ? '#ff6a00' : '#666'; ?>; font-weight: <?php echo $status_filter === 'to_review' ? 'bold' : 'normal'; ?>;">To Review</a>
                            </div>

                            <?php foreach ($orders as $order): 
                                $order_date = strtotime($order['payment_date']);
                                $can_cancel = ($order['shipping_status'] === 'Pending' && (time() - $order_date) < 86400); // 24 hours
                                $status_color = 'warning';
                                $status_text = 'Processing';
                                
                                if ($order['payment_status'] === 'Completed') {
                                    if ($order['shipping_status'] === 'Shipped') {
                                        $status_color = 'primary';
                                        $status_text = 'Shipped';
                                    } else if ($order['shipping_status'] === 'Delivered') {
                                        $status_color = 'success';
                                        $status_text = 'Delivered';
                                    } else if ($order['shipping_status'] === 'Pending') {
                                        $status_color = 'info';
                                        $status_text = 'Processing';
                                    }
                                } else if ($order['payment_status'] === 'Cancelled') {
                                    $status_color = 'danger';
                                    $status_text = 'Cancelled';
                                }
                            ?>
                                <div style="border-bottom: 1px solid #eee; padding: 15px 0; display: flex; justify-content: space-between; align-items: flex-start;">
                                    <div>
                                        <strong>Order ID:</strong> <?php echo htmlspecialchars($order['payment_id']); ?><br>
                                        <strong>Date:</strong> <?php echo date('F j, Y, g:i A', $order_date); ?><br>
                                        <strong>Items:</strong> <?php echo htmlspecialchars($order['item_count']); ?> product(s)<br>
                                        <strong>Status:</strong> 
                                        <span style="background: #ff6a00; color: white; padding: 3px 8px; border-radius: 4px; font-size: 12px;">
                                            <?php echo htmlspecialchars($status_text); ?>
                                        </span>
                                        <?php if($can_cancel): ?>
                                            <br><span style="color: #ff6a00; font-size: 11px; margin-top: 5px; display: inline-block;">⏱ Can cancel within 24 hours</span>
                                        <?php endif; ?>
                                    </div>
                                    <div style="text-align: right;">
                                        <strong>Total:</strong> ৳<?php echo htmlspecialchars(number_format($order['paid_amount'] ?? 0, 2)); ?><br>
                                        <a href="customer-order.php?payment_id=<?php echo htmlspecialchars($order['payment_id']); ?>" class="btn btn-sm btn-primary" style="margin-top: 5px;">View Details & Track</a>
                                        <?php if($can_cancel): ?>
                                            <br><a href="#" onclick="cancelOrder('<?php echo htmlspecialchars($order['payment_id']); ?>')" class="btn btn-sm" style="margin-top: 5px; background-color: #f87171; color: white; border: none; text-decoration: none; padding: 5px 10px; border-radius: 4px; display: inline-block;">Cancel Order</a>
                                        <?php endif; ?>
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>
                    <?php endif; ?>
                    <?php
                    }
                    catch (PDOException $e) {
                        echo '<div class="alert alert-danger">Error fetching orders: ' . htmlspecialchars($e->getMessage()) . '</div>';
                    }
                    ?>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
function cancelOrder(paymentId) {
    if (confirm('Are you sure you want to cancel this order? You have 24 hours from the order date to cancel.')) {
        // AJAX call to cancel order
        fetch('ajax/cancel-order.php', {
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
</script>

<?php require_once('footer.php'); ?>
