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
?>
<?php
if (isset($_POST['form1'])) {
    if (!$csrf->checkToken()) {http_response_code(403);exit('Your form session expired. Refresh the page.');}
    // Update billing and shipping data
    $statement = $pdo->prepare("UPDATE tbl_customer SET 
                            cust_b_name=?, 
                            cust_b_cname=?, 
                            cust_b_phone=?, 
                            cust_b_country=?, 
                            cust_b_address=?, 
                            cust_b_city=?, 
                            cust_b_state=?, 
                            cust_b_zip=?,
                            cust_s_name=?, 
                            cust_s_cname=?, 
                            cust_s_phone=?, 
                            cust_s_country=?, 
                            cust_s_address=?, 
                            cust_s_city=?, 
                            cust_s_state=?, 
                            cust_s_zip=? 

                            WHERE cust_id=?");
    $statement->execute(array(
                            strip_tags($_POST['cust_b_name']),
                            strip_tags($_POST['cust_b_cname']),
                            strip_tags($_POST['cust_b_phone']),
                            strip_tags($_POST['cust_b_country']),
                            strip_tags($_POST['cust_b_address']),
                            strip_tags($_POST['cust_b_city']),
                            strip_tags($_POST['cust_b_state']),
                            strip_tags($_POST['cust_b_zip']),
                            strip_tags($_POST['cust_s_name']),
                            strip_tags($_POST['cust_s_cname']),
                            strip_tags($_POST['cust_s_phone']),
                            strip_tags($_POST['cust_s_country']),
                            strip_tags($_POST['cust_s_address']),
                            strip_tags($_POST['cust_s_city']),
                            strip_tags($_POST['cust_s_state']),
                            strip_tags($_POST['cust_s_zip']),
                            $_SESSION['customer']['cust_id']
                        ));  
   
    $success_message = LANG_VALUE_122;

    // Update session data
    $_SESSION['customer']['cust_b_name'] = strip_tags($_POST['cust_b_name']);
    $_SESSION['customer']['cust_b_cname'] = strip_tags($_POST['cust_b_cname']);
    $_SESSION['customer']['cust_b_phone'] = strip_tags($_POST['cust_b_phone']);
    $_SESSION['customer']['cust_b_country'] = strip_tags($_POST['cust_b_country']);
    $_SESSION['customer']['cust_b_address'] = strip_tags($_POST['cust_b_address']);
    $_SESSION['customer']['cust_b_city'] = strip_tags($_POST['cust_b_city']);
    $_SESSION['customer']['cust_b_state'] = strip_tags($_POST['cust_b_state']);
    $_SESSION['customer']['cust_b_zip'] = strip_tags($_POST['cust_b_zip']);
    $_SESSION['customer']['cust_s_name'] = strip_tags($_POST['cust_s_name']);
    $_SESSION['customer']['cust_s_cname'] = strip_tags($_POST['cust_s_cname']);
    $_SESSION['customer']['cust_s_phone'] = strip_tags($_POST['cust_s_phone']);
    $_SESSION['customer']['cust_s_country'] = strip_tags($_POST['cust_s_country']);
    $_SESSION['customer']['cust_s_address'] = strip_tags($_POST['cust_s_address']);
    $_SESSION['customer']['cust_s_city'] = strip_tags($_POST['cust_s_city']);
    $_SESSION['customer']['cust_s_state'] = strip_tags($_POST['cust_s_state']);
    $_SESSION['customer']['cust_s_zip'] = strip_tags($_POST['cust_s_zip']);
}
?>

<div class="page">
    <div class="container">
        <div class="row">            
            <div class="col-md-12"> 
                <?php require_once('customer-sidebar.php'); ?>
            </div>
            <div class="col-md-12">
                <div class="user-content">
                    <?php
                    if($error_message != '') {
                        echo "<div class='error' style='padding: 10px;background:#f1f1f1;margin-bottom:20px;'>".$error_message."</div>";
                    }
                    if($success_message != '') {
                        echo "<div class='success' style='padding: 10px;background:#f1f1f1;margin-bottom:20px;'>".$success_message."</div>";
                    }
                    ?>
                    <form action="" method="post" id="billing-shipping-form">
                        <?php $csrf->echoInputField(); ?>
                        <div class="row">
                            <div class="col-md-6">
                                <div class="section-header">
                                    <h3><?php echo LANG_VALUE_86; ?></h3>
                                    <div class="form-check mb-3">
                                        <input type="checkbox" class="form-check-input" id="use-profile-address">
                                        <label class="form-check-label" for="use-profile-address">
                                            <?php echo LANG_VALUE_131; ?>
                                        </label>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_102; ?></label>
                                    <input type="text" class="form-control" name="cust_b_name" value="<?php echo $_SESSION['customer']['cust_b_name']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_103; ?></label>
                                    <input type="text" class="form-control" name="cust_b_cname" value="<?php echo $_SESSION['customer']['cust_b_cname']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_104; ?></label>
                                    <input type="text" class="form-control" name="cust_b_phone" value="<?php echo $_SESSION['customer']['cust_b_phone']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_106; ?></label>
                                    <select name="cust_b_country" class="form-control">
                                        <?php
                                        $statement = $pdo->prepare("SELECT * FROM tbl_country ORDER BY country_name ASC");
                                        $statement->execute();
                                        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
                                        foreach ($result as $row) {
                                            ?>
                                            <option value="<?php echo $row['country_id']; ?>" <?php if($row['country_id'] == $_SESSION['customer']['cust_b_country']) {echo 'selected';} ?>><?php echo $row['country_name']; ?></option>
                                            <?php
                                        }
                                        ?>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_105; ?></label>
                                    <textarea name="cust_b_address" class="form-control" cols="30" rows="3"><?php echo $_SESSION['customer']['cust_b_address']; ?></textarea>
                                </div>
                                <div class="row">
                                    <div class="col-md-6 form-group">
                                        <label for=""><?php echo LANG_VALUE_107; ?></label>
                                        <input type="text" class="form-control" name="cust_b_city" value="<?php echo $_SESSION['customer']['cust_b_city']; ?>">
                                    </div>
                                    <div class="col-md-6 form-group">
                                        <label for=""><?php echo LANG_VALUE_108; ?></label>
                                        <input type="text" class="form-control" name="cust_b_state" value="<?php echo $_SESSION['customer']['cust_b_state']; ?>">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_109; ?></label>
                                    <input type="text" class="form-control" name="cust_b_zip" value="<?php echo $_SESSION['customer']['cust_b_zip']; ?>">
                                </div>
                            </div>
                            
                            <div class="col-md-6">
                                <div class="section-header">
                                    <h3><?php echo LANG_VALUE_87; ?></h3>
                                    <div class="form-check mb-3">
                                        <input type="checkbox" class="form-check-input" id="same-as-billing">
                                        <label class="form-check-label" for="same-as-billing">
                                            <?php echo LANG_VALUE_132; ?>
                                        </label>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_102; ?></label>
                                    <input type="text" class="form-control" name="cust_s_name" value="<?php echo $_SESSION['customer']['cust_s_name']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_103; ?></label>
                                    <input type="text" class="form-control" name="cust_s_cname" value="<?php echo $_SESSION['customer']['cust_s_cname']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_104; ?></label>
                                    <input type="text" class="form-control" name="cust_s_phone" value="<?php echo $_SESSION['customer']['cust_s_phone']; ?>">
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_106; ?></label>
                                    <select name="cust_s_country" class="form-control">
                                        <?php
                                        $statement = $pdo->prepare("SELECT * FROM tbl_country ORDER BY country_name ASC");
                                        $statement->execute();
                                        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
                                        foreach ($result as $row) {
                                            ?>
                                            <option value="<?php echo $row['country_id']; ?>" <?php if($row['country_id'] == $_SESSION['customer']['cust_s_country']) {echo 'selected';} ?>><?php echo $row['country_name']; ?></option>
                                            <?php
                                        }
                                        ?>
                                    </select>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_105; ?></label>
                                    <textarea name="cust_s_address" class="form-control" cols="30" rows="3"><?php echo $_SESSION['customer']['cust_s_address']; ?></textarea>
                                </div>
                                <div class="row">
                                    <div class="col-md-6 form-group">
                                        <label for=""><?php echo LANG_VALUE_107; ?></label>
                                        <input type="text" class="form-control" name="cust_s_city" value="<?php echo $_SESSION['customer']['cust_s_city']; ?>">
                                    </div>
                                    <div class="col-md-6 form-group">
                                        <label for=""><?php echo LANG_VALUE_108; ?></label>
                                        <input type="text" class="form-control" name="cust_s_state" value="<?php echo $_SESSION['customer']['cust_s_state']; ?>">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label for=""><?php echo LANG_VALUE_109; ?></label>
                                    <input type="text" class="form-control" name="cust_s_zip" value="<?php echo $_SESSION['customer']['cust_s_zip']; ?>">
                                </div>
                            </div>
                        </div>
                        <div class="text-center mt-4">
                            <input type="submit" class="btn btn-primary btn-lg" value="<?php echo LANG_VALUE_5; ?>" name="form1">
                        </div>
                    </form>
                </div>                
            </div>
        </div>
    </div>
</div>

<script>
document.addEventListener('DOMContentLoaded', function() {
    // Get profile data from PHP session
    const profileData = {
        name: '<?php echo addslashes($_SESSION['customer']['cust_name']); ?>',
        cname: '<?php echo addslashes($_SESSION['customer']['cust_cname']); ?>',
        phone: '<?php echo addslashes($_SESSION['customer']['cust_phone']); ?>',
        country: '<?php echo addslashes($_SESSION['customer']['cust_country']); ?>',
        address: '<?php echo addslashes($_SESSION['customer']['cust_address']); ?>',
        city: '<?php echo addslashes($_SESSION['customer']['cust_city']); ?>',
        state: '<?php echo addslashes($_SESSION['customer']['cust_state']); ?>',
        zip: '<?php echo addslashes($_SESSION['customer']['cust_zip']); ?>'
    };

    // Elements
    const useProfileCheckbox = document.getElementById('use-profile-address');
    const sameAsBillingCheckbox = document.getElementById('same-as-billing');
    const billingFields = {
        name: document.getElementsByName('cust_b_name')[0],
        cname: document.getElementsByName('cust_b_cname')[0],
        phone: document.getElementsByName('cust_b_phone')[0],
        country: document.getElementsByName('cust_b_country')[0],
        address: document.getElementsByName('cust_b_address')[0],
        city: document.getElementsByName('cust_b_city')[0],
        state: document.getElementsByName('cust_b_state')[0],
        zip: document.getElementsByName('cust_b_zip')[0]
    };
    
    const shippingFields = {
        name: document.getElementsByName('cust_s_name')[0],
        cname: document.getElementsByName('cust_s_cname')[0],
        phone: document.getElementsByName('cust_s_phone')[0],
        country: document.getElementsByName('cust_s_country')[0],
        address: document.getElementsByName('cust_s_address')[0],
        city: document.getElementsByName('cust_s_city')[0],
        state: document.getElementsByName('cust_s_state')[0],
        zip: document.getElementsByName('cust_s_zip')[0]
    };

    // Copy profile data to billing
    function fillBillingFromProfile() {
        billingFields.name.value = profileData.name;
        billingFields.cname.value = profileData.cname;
        billingFields.phone.value = profileData.phone;
        billingFields.country.value = profileData.country;
        billingFields.address.value = profileData.address;
        billingFields.city.value = profileData.city;
        billingFields.state.value = profileData.state;
        billingFields.zip.value = profileData.zip;
    }

    // Copy billing data to shipping
    function copyBillingToShipping() {
        shippingFields.name.value = billingFields.name.value;
        shippingFields.cname.value = billingFields.cname.value;
        shippingFields.phone.value = billingFields.phone.value;
        shippingFields.country.value = billingFields.country.value;
        shippingFields.address.value = billingFields.address.value;
        shippingFields.city.value = billingFields.city.value;
        shippingFields.state.value = billingFields.state.value;
        shippingFields.zip.value = billingFields.zip.value;
    }

    // Event listeners
    useProfileCheckbox.addEventListener('change', function() {
        if(this.checked) {
            fillBillingFromProfile();
            // If same as billing is checked, update shipping too
            if(sameAsBillingCheckbox.checked) {
                copyBillingToShipping();
            }
        }
    });

    sameAsBillingCheckbox.addEventListener('change', function() {
        if(this.checked) {
            copyBillingToShipping();
        }
    });

    // Update shipping when billing changes if "same as billing" is checked
    Object.keys(billingFields).forEach(field => {
        billingFields[field].addEventListener('input', function() {
            if(sameAsBillingCheckbox.checked) {
                shippingFields[field].value = this.value;
            }
        });
    });
});
</script>
<?php require_once('footer.php'); ?>