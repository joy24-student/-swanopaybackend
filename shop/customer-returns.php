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
                        <i class="fas fa-undo"></i> Returns & Cancellations
                    </h3>

                    <?php
                    try {
                        $statement = $pdo->prepare("
                            SELECT
                                o.*,
                                p.payment_status,
                                p.shipping_status
                            FROM tbl_order o
                            LEFT JOIN tbl_payment p ON o.payment_id = p.payment_id
                            WHERE o.cust_id = ? AND (p.payment_status IN ('Cancelled', 'Returned', 'Return Requested') OR p.shipping_status IN ('Cancelled', 'Returned', 'Return Requested'))
                            ORDER BY o.id DESC
                        ");
                        $statement->execute(array($customer_id));
                        $returns = $statement->fetchAll(PDO::FETCH_ASSOC);
                    } catch (PDOException $e) {
                        echo '<div class="alert alert-danger">Error: ' . htmlspecialchars($e->getMessage()) . '</div>';
                        $returns = [];
                    }

                    if (empty($returns)):
                    ?>
                        <div class="alert alert-info text-center">
                            <p>No returns or cancellations. <a href="<?php echo BASE_URL; ?>index.php">Continue Shopping</a></p>
                        </div>
                    <?php else: ?>
                        <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                            <?php foreach ($returns as $return): ?>
                                <div style="border-bottom: 1px solid #eee; padding: 15px 0; display: flex; justify-content: space-between; align-items: center;">
                                    <div>
                                        <strong>Order ID:</strong> <?php echo htmlspecialchars($return['id']); ?><br>
                                        <strong>Product:</strong> <?php echo htmlspecialchars($return['product_name']); ?><br>
                                        <strong>Status:</strong>
                                        <span style="background: #ffc107; color: white; padding: 3px 8px; border-radius: 4px; font-size: 12px;">
                                            <?php echo htmlspecialchars($return['payment_status'] ?? $return['shipping_status'] ?? 'Pending'); ?>
                                        </span>
                                    </div>
                                    <div style="text-align: right;">
                                       <strong>Amount:</strong> ৳<?php echo htmlspecialchars(number_format($return['unit_price'] ?? 0, 2)); ?><br>
                                       <strong>Quantity:</strong> <?php echo htmlspecialchars($return['quantity']); ?><br>
                                       <a href="customer-order.php?id=<?php echo htmlspecialchars($return['id']); ?>" class="btn btn-sm btn-primary" style="margin-top: 5px;">View Order</a>
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

<?php require_once('footer.php'); ?>
