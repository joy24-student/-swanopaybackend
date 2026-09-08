<div class="user-sidebar">
    <ul>
    <a href="dashboard.php"><button class="btn btn-danger"><?php echo LANG_VALUE_89; ?></button></a>
    <a href="customer-profile-update.php"><button class="btn btn-danger"><?php echo LANG_VALUE_117; ?></button></a>
        <a href="customer-billing-shipping-update.php"><button class="btn btn-danger"><?php echo LANG_VALUE_88; ?></button></a>
        <a href="customer-password-update.php"><button class="btn btn-danger"><?php echo LANG_VALUE_99; ?></button></a>
        <a href="customer-order.php"><button class="btn btn-danger"><?php echo LANG_VALUE_24; ?></button></a>
        <a href="customer-returns.php"><button class="btn btn-danger">Returns & Cancellations</button></a>
        <?php
        // Check if customer has vendor account and it's approved
        if(isset($_SESSION['customer'])) {
            try {
                $stmt_vendor = $pdo->prepare("SELECT * FROM tbl_businesses WHERE owner_user_id = ? AND verification_status = 'approved' LIMIT 1");
                $stmt_vendor->execute(array($_SESSION['customer']['cust_id']));
                $vendor_row = $stmt_vendor->fetch(PDO::FETCH_ASSOC);
                $has_approved_vendor = !empty($vendor_row) && $vendor_row['verification_status'] === 'approved';
                
                if($has_approved_vendor) {
                    echo '<a href="admin/vendor_dashboard.php"><button class="btn btn-danger">Vendor Panel</button></a>';
                } else {
                    echo '<a href="merchant-register.php"><button class="btn btn-danger">Become a Vendor</button></a>';
                }
            } catch(PDOException $e) {
                // Silently fail if vendor check fails
            }
        }
        ?>

        <a href="logout.php"><button class="btn btn-danger"><?php echo LANG_VALUE_14; ?></button></a>
    </ul>
</div>