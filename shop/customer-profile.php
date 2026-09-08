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
$customer = $_SESSION['customer'];
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
                        <i class="fas fa-user"></i> My Profile
                    </h3>

                    <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <form action="customer-profile-update.php" method="post" enctype="multipart/form-data">
                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="cust_name"><strong>Full Name</strong></label>
                                <input 
                                    type="text" 
                                    id="cust_name"
                                    name="cust_name" 
                                    class="form-control" 
                                    value="<?php echo htmlspecialchars($customer['cust_name'] ?? ''); ?>" 
                                    required
                                >
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="cust_email"><strong>Email</strong></label>
                                <input 
                                    type="email" 
                                    id="cust_email"
                                    name="cust_email" 
                                    class="form-control" 
                                    value="<?php echo htmlspecialchars($customer['cust_email'] ?? ''); ?>" 
                                    disabled
                                >
                                <small style="color: #999;">Email cannot be changed</small>
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="cust_phone"><strong>Phone Number</strong></label>
                                <input 
                                    type="tel" 
                                    id="cust_phone"
                                    name="cust_phone" 
                                    class="form-control" 
                                    value="<?php echo htmlspecialchars($customer['cust_phone'] ?? ''); ?>"
                                >
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="cust_photo"><strong>Profile Photo</strong></label>
                                <input 
                                    type="file" 
                                    id="cust_photo"
                                    name="cust_photo" 
                                    class="form-control"
                                    accept="image/*"
                                >
                                <small style="color: #999;">Leave empty to keep current photo</small>
                            </div>

                            <div class="form-group" style="margin-bottom: 15px;">
                                <label for="cust_address"><strong>Address</strong></label>
                                <textarea 
                                    id="cust_address"
                                    name="cust_address" 
                                    class="form-control" 
                                    rows="3"
                                ><?php echo htmlspecialchars($customer['cust_address'] ?? ''); ?></textarea>
                            </div>

                            <button type="submit" class="btn btn-success" style="background-color: #ff6a00; border: none; padding: 10px 20px; color: white; cursor: pointer; border-radius: 4px;">
                                <i class="fas fa-save"></i> Save Changes
                            </button>
                        </form>
                    </div>

                    <h4 style="margin-top: 30px; margin-bottom: 15px;" id="address">
                        <i class="fas fa-map-marker-alt"></i> Manage Addresses
                    </h4>

                    <div style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                        <p style="color: #666;">Manage your delivery and billing addresses here.</p>
                        <a href="customer-billing-shipping-update.php" class="btn btn-primary" style="background-color: #ff6a00; border: none; padding: 10px 20px; color: white; cursor: pointer; border-radius: 4px;">
                            <i class="fas fa-edit"></i> Manage Addresses
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>
