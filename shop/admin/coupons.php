<?php require_once('header.php'); ?>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <div class="user-content">
                    <h3 class="text-center">Manage Coupons</h3>
                    <a href="coupon-add.php" class="btn btn-primary">Add New Coupon</a>
                    
                    <table class="table table-bordered">
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th>Coupon Code</th>
                                <th>Discount Type</th>
                                <th>Discount Value</th>
                                <th>Min. Order</th>
                                <th>Usage Limit</th>
                                <th>Used</th>
                                <th>Start Date</th>
                                <th>End Date</th>
                                <th>Status</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php
                            $statement = $pdo->prepare("SELECT * FROM tbl_coupon ORDER BY coupon_id DESC");
                            $statement->execute();
                            $coupons = $statement->fetchAll(PDO::FETCH_ASSOC);
                            
                            foreach ($coupons as $coupon) {
                                ?>
                                <tr>
                                    <td><?= $coupon['coupon_id'] ?></td>
                                    <td><?= $coupon['coupon_code'] ?></td>
                                    <td><?= ucfirst($coupon['discount_type']) ?></td>
                                    <td>
                                        <?= ($coupon['discount_type'] == 'percentage') 
                                            ? $coupon['discount_value'] . '%' 
                                            : LANG_VALUE_1 . $coupon['discount_value'] ?>
                                    </td>
                                    <td><?= LANG_VALUE_1 . $coupon['minimum_order'] ?></td>
                                    <td><?= $coupon['usage_limit'] ?: 'Unlimited' ?></td>
                                    <td><?= $coupon['used_count'] ?></td>
                                    <td><?= $coupon['start_date'] ?></td>
                                    <td><?= $coupon['end_date'] ?></td>
                                    <td><?= ucfirst($coupon['status']) ?></td>
                                    <td>
                                        <a href="coupon-edit.php?id=<?= $coupon['coupon_id'] ?>" class="btn btn-primary btn-xs">Edit</a>
                                        <a href="coupon-delete.php?id=<?= $coupon['coupon_id'] ?>" class="btn btn-danger btn-xs" onclick="return confirm('Are you sure?');">Delete</a>
                                    </td>
                                </tr>
                                <?php
                            }
                            ?>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>