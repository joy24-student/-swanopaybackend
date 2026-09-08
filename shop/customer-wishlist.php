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
                        <i class="fas fa-heart"></i> My Wishlist
                    </h3>

                    <?php
                    try {
                        $statement = $pdo->prepare("
                            SELECT
                                t1.wishlist_id,
                                t1.product_id,
                                t2.p_name,
                                t2.p_current_price,
                                t2.p_featured_photo,
                                t2.p_old_price,
                                t2.p_id
                            FROM tbl_wishlist t1
                            JOIN tbl_product t2 ON t1.product_id = t2.p_id
                            WHERE t1.cust_id = ?
                            ORDER BY t1.added_date DESC
                        ");
                        $statement->execute(array($customer_id));
                        $wishlist_items = $statement->fetchAll(PDO::FETCH_ASSOC);
                    } catch (PDOException $e) {
                        $wishlist_items = [];
                        echo '<div class="alert alert-danger">Error fetching wishlist: ' . htmlspecialchars($e->getMessage()) . '</div>';
                    }
                    ?>

                    <?php if (empty($wishlist_items)): ?>
                        <div class="alert alert-info text-center">
                            <p>Your wishlist is empty. <a href="<?php echo BASE_URL; ?>index.php">Start Shopping</a></p>
                        </div>
                    <?php else: ?>
                        <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                            <?php foreach ($wishlist_items as $item): ?>
                                <div style="border-bottom: 1px solid #eee; padding: 15px 0; display: flex; justify-content: space-between; align-items: center;">
                                    <div style="display: flex; gap: 15px; align-items: center; flex: 1;">
                                        <img 
                                            src="assets/uploads/<?php echo htmlspecialchars($item['p_featured_photo']); ?>" 
                                            alt="<?php echo htmlspecialchars($item['p_name']); ?>"
                                            style="width: 80px; height: 80px; object-fit: contain; border-radius: 4px; background: #f5f5f5;"
                                            onerror="this.src='https://placehold.co/80x80/f0f0f0/333?text=No+Image';"
                                        >
                                        <div style="flex: 1;">
                                            <strong><?php echo htmlspecialchars($item['p_name']); ?></strong><br>
                                            <span style="color: #ff6a00; font-weight: bold; font-size: 16px;">
                                                ৳<?php echo htmlspecialchars(number_format($item['p_current_price'], 2)); ?>
                                            </span>
                                            <?php if($item['p_old_price'] > 0): ?>
                                                <span style="text-decoration: line-through; color: #999; margin-left: 10px;">
                                                    ৳<?php echo htmlspecialchars(number_format($item['p_old_price'], 2)); ?>
                                                </span>
                                            <?php endif; ?>
                                        </div>
                                    </div>
                                    <div style="display: flex; gap: 10px;">
                                        <a href="product.php?id=<?php echo htmlspecialchars($item['p_id']); ?>" class="btn btn-sm btn-primary">View</a>
                                        <button 
                                            class="btn btn-sm btn-danger remove-from-wishlist" 
                                            data-wishlist-id="<?php echo $item['wishlist_id']; ?>"
                                            style="cursor: pointer;">
                                            Remove
                                        </button>
                                    </div>
                                </div>
                            <?php endforeach; ?>
                        </div>

                        <script>
                        document.querySelectorAll('.remove-from-wishlist').forEach(button => {
                            button.addEventListener('click', async function() {
                                if (!confirm('Remove this item from wishlist?')) return;

                                const wishlistId = this.dataset.wishlistId;
                                const formData = new FormData();
                                formData.append('wishlist_id', wishlistId);
                                formData.append('action', 'remove');

                                try {
                                    const response = await fetch('wishlist_action.php', {
                                        method: 'POST',
                                        body: formData
                                    });
                                    const data = await response.json();
                                    if (data.status === 'success') {
                                        alert(data.message);
                                        location.reload();
                                    } else {
                                        alert(data.message || 'Error removing item');
                                    }
                                } catch (error) {
                                    alert('Error: ' + error.message);
                                }
                            });
                        });
                        </script>
                     <?php endif; ?>
                 </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>
