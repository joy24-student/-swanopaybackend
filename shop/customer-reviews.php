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
                        <i class="fas fa-star"></i> My Reviews
                    </h3>

                    <?php
                    try {
                        $statement = $pdo->prepare("
                            SELECT 
                                r.*, 
                                p.p_name,
                                p.p_featured_photo,
                                p.p_id
                            FROM tbl_review r
                            JOIN tbl_product p ON r.product_id = p.p_id
                            WHERE r.cust_id = ?
                            ORDER BY r.created_at DESC
                        ");
                        $statement->execute(array($customer_id));
                        $reviews = $statement->fetchAll(PDO::FETCH_ASSOC);

                        if (empty($reviews)):
                    ?>
                        <div class="alert alert-info text-center">
                            <p>You haven't written any reviews yet. <a href="<?php echo BASE_URL; ?>index.php">Shop Now</a></p>
                        </div>
                    <?php else: ?>
                        <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                            <?php foreach ($reviews as $review): ?>
                                <div style="border-bottom: 1px solid #eee; padding: 15px 0;">
                                    <div style="display: flex; justify-content: space-between; align-items: start;">
                                        <div style="flex: 1;">
                                            <h5><?php echo htmlspecialchars($review['p_name']); ?></h5>
                                            <div style="color: #ff6a00; margin: 5px 0;">
                                                <strong>Rating:</strong> 
                                                <?php 
                                                    $rating = intval($review['rating'] ?? 0);
                                                    for($i = 0; $i < 5; $i++) {
                                                        echo $i < $rating ? '★' : '☆';
                                                    }
                                                ?>
                                            </div>
                                            <p style="margin: 10px 0; color: #333;">
                                                <?php echo htmlspecialchars($review['comment'] ?? ''); ?>
                                            </p>
                                            <small style="color: #999;">
                                                Reviewed on <?php echo date('F j, Y', strtotime($review['created_at'] ?? date('Y-m-d'))); ?>
                                                - Status: <span style="background: #e3f2fd; padding: 2px 6px; border-radius: 3px;">
                                                    <?php echo htmlspecialchars($review['status'] ?? 'Pending'); ?>
                                                </span>
                                            </small>
                                        </div>
                                        <img 
                                            src="assets/uploads/<?php echo htmlspecialchars($review['p_featured_photo']); ?>" 
                                            alt="<?php echo htmlspecialchars($review['p_name']); ?>"
                                            style="width: 80px; height: 80px; object-fit: contain; border-radius: 4px; background: #f5f5f5; margin-left: 15px;"
                                            onerror="this.src='https://placehold.co/80x80/f0f0f0/333?text=No+Image';"
                                        >
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>
                    <?php endif;
                    } catch (PDOException $e) {
                        echo '<div class="alert alert-danger">Error fetching reviews: ' . htmlspecialchars($e->getMessage()) . '</div>';
                    }
                    ?>
                </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>
