<?php require_once __DIR__ . '/inc/guard.php'; ?>
<?php // coupon-add.php
require_once('header.php');

// Process form submission
if(isset($_POST['form1'])) {
    $valid = 1;
    
    if(empty($_POST['coupon_code'])) {
        $valid = 0;
        $error_message .= "Coupon code can't be empty<br>";
    } else {
        // Check if coupon code exists
        $statement = $pdo->prepare("SELECT * FROM tbl_coupon WHERE coupon_code=?");
        $statement->execute([$_POST['coupon_code']]);
        if($statement->rowCount() > 0) {
            $valid = 0;
            $error_message .= "Coupon code already exists<br>";
        }
    }
    
    if(empty($_POST['discount_value']) || $_POST['discount_value'] <= 0) {
        $valid = 0;
        $error_message .= "Discount value must be greater than 0<br>";
    }
    
    if($_POST['discount_type'] == 'percentage' && $_POST['discount_value'] > 100) {
        $valid = 0;
        $error_message .= "Percentage discount can't be more than 100%<br>";
    }
    
    if(empty($_POST['start_date']) || empty($_POST['end_date'])) {
        $valid = 0;
        $error_message .= "Start and end dates are required<br>";
    } elseif(strtotime($_POST['end_date']) < strtotime($_POST['start_date'])) {
        $valid = 0;
        $error_message .= "End date must be after start date<br>";
    }
    
    if($valid == 1) {
        $statement = $pdo->prepare("INSERT INTO tbl_coupon (coupon_code, discount_type, discount_value, minimum_order, usage_limit, start_date, end_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        $statement->execute([
            $_POST['coupon_code'],
            $_POST['discount_type'],
            $_POST['discount_value'],
            $_POST['minimum_order'] ?? 0,
            $_POST['usage_limit'] ?? 0,
            $_POST['start_date'],
            $_POST['end_date'],
            $_POST['status']
        ]);
        
        $success_message = "Coupon added successfully!";
    }
}
?>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <div class="user-content">
                    <h3>Add New Coupon</h3>
                    
                    <?php if($error_message): ?>
                        <div class="alert alert-danger">
                            <?= $error_message ?>
                        </div>
                    <?php endif; ?>
                    
                    <?php if($success_message): ?>
                        <div class="alert alert-success">
                            <?= $success_message ?>
                        </div>
                    <?php endif; ?>
                    
                    <form action="" method="post">
                        <div class="form-group">
                            <label for="coupon_code">Coupon Code *</label>
                            <input type="text" class="form-control" name="coupon_code" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="discount_type">Discount Type *</label>
                            <select name="discount_type" class="form-control" required>
                                <option value="percentage">Percentage</option>
                                <option value="fixed">Fixed Amount</option>
                            </select>
                        </div>
                        
                        <div class="form-group">
                            <label for="discount_value">Discount Value *</label>
                            <input type="number" step="0.01" min="0.01" class="form-control" name="discount_value" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="minimum_order">Minimum Order Amount </label>
                            <input type="number" step="0.01" min="0" class="form-control" name="minimum_order" value="0">
                        </div>
                        
                        <div class="form-group">
                            <label for="usage_limit">Usage Limit (0 = unlimited)</label>
                            <input type="number" min="0" class="form-control" name="usage_limit" value="0">
                        </div>
                        
                        <div class="form-group">
                            <label for="start_date">Start Date *</label>
                            <input type="date" class="form-control" name="start_date" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="end_date">End Date *</label>
                            <input type="date" class="form-control" name="end_date" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="status">Status *</label>
                            <select name="status" class="form-control" required>
                                <option value="active">Active</option>
                                <option value="inactive">Inactive</option>
                            </select>
                        </div>
                        
                        <input type="submit" class="btn btn-primary" value="Add Coupon" name="form1">
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>