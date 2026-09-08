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
$message = '';
$error = '';

// Handle form submission
if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $current_password = $_POST['current_password'] ?? '';
    $new_password = $_POST['new_password'] ?? '';
    $confirm_password = $_POST['confirm_password'] ?? '';

    if (empty($current_password) || empty($new_password) || empty($confirm_password)) {
        $error = 'All fields are required';
    } elseif ($new_password !== $confirm_password) {
        $error = 'New passwords do not match';
    } elseif (strlen($new_password) < 6) {
        $error = 'New password must be at least 6 characters';
    } else {
        // Verify current password
        try {
            $statement = $pdo->prepare("SELECT cust_password FROM tbl_customer WHERE cust_id = ?");
            $statement->execute(array($customer_id));
            $customer = $statement->fetch(PDO::FETCH_ASSOC);

            if (password_verify($current_password, $customer['cust_password'])) {
                // Update password
                $hashed_password = password_hash($new_password, PASSWORD_DEFAULT);
                $update_stmt = $pdo->prepare("UPDATE tbl_customer SET cust_password = ? WHERE cust_id = ?");
                $update_stmt->execute(array($hashed_password, $customer_id));

                $message = 'Password changed successfully!';
            } else {
                $error = 'Current password is incorrect';
            }
        } catch (PDOException $e) {
            $error = 'Database error: ' . htmlspecialchars($e->getMessage());
        }
    }
}
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
                        <i class="fas fa-lock"></i> Change Password
                    </h3>

                    <?php if (!empty($message)): ?>
                        <div class="alert alert-success" style="background: #d4edda; border: 1px solid #c3e6cb; color: #155724; padding: 12px; border-radius: 4px; margin-bottom: 20px;">
                            <i class="fas fa-check"></i> <?php echo htmlspecialchars($message); ?>
                        </div>
                    <?php endif; ?>

                    <?php if (!empty($error)): ?>
                        <div class="alert alert-danger" style="background: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; padding: 12px; border-radius: 4px; margin-bottom: 20px;">
                            <i class="fas fa-exclamation"></i> <?php echo htmlspecialchars($error); ?>
                        </div>
                    <?php endif; ?>

                    <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <form method="POST" action="">
                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="current_password"><strong>Current Password</strong></label>
                                <input 
                                    type="password" 
                                    id="current_password"
                                    name="current_password" 
                                    class="form-control" 
                                    required
                                    style="padding: 10px; border: 1px solid #ddd; border-radius: 4px; width: 100%;"
                                >
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="new_password"><strong>New Password</strong></label>
                                <input 
                                    type="password" 
                                    id="new_password"
                                    name="new_password" 
                                    class="form-control" 
                                    required
                                    style="padding: 10px; border: 1px solid #ddd; border-radius: 4px; width: 100%;"
                                >
                                <small style="color: #999;">Minimum 6 characters</small>
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="confirm_password"><strong>Confirm New Password</strong></label>
                                <input 
                                    type="password" 
                                    id="confirm_password"
                                    name="confirm_password" 
                                    class="form-control" 
                                    required
                                    style="padding: 10px; border: 1px solid #ddd; border-radius: 4px; width: 100%;"
                                >
                            </div>

                            <button 
                                type="submit" 
                                class="btn btn-success" 
                                style="background-color: #ff6a00; border: none; padding: 10px 20px; color: white; cursor: pointer; border-radius: 4px;">
                                <i class="fas fa-save"></i> Update Password
                            </button>
                        </form>
                    </div>

                    <div style="background: #f0f8ff; padding: 15px; border-radius: 8px; margin-top: 20px;">
                        <h5 style="margin-top: 0;">Password Requirements:</h5>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>Minimum 6 characters</li>
                            <li>Use a strong combination of letters, numbers, and symbols</li>
                            <li>Do not share your password with anyone</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>
