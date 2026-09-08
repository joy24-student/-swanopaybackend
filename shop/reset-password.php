<?php require_once('header.php'); ?>

<?php
// Include CSRF_Protect class
require_once('admin/inc/CSRF_Protect.php');
$csrf = new CSRF_Protect();

$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);                            
foreach ($result as $row) {
    $banner_reset_password = $row['banner_reset_password'];
}
?>

<?php
if( !isset($_GET['email']) || !isset($_GET['token']) )
{
    header('location: '.BASE_URL.'login.php');
    exit;
}

$statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=? AND cust_token=?");
$statement->execute(array($_GET['email'],$_GET['token']));
$result = $statement->fetchAll(PDO::FETCH_ASSOC);
$tot = $statement->rowCount();
if($tot == 0)
{
    header('location: '.BASE_URL.'login.php');
    exit;
}
foreach ($result as $row) {
    $saved_time = $row['cust_timestamp'];
}

$error_message = ''; // Initialize error message for form submission
$error_message2 = ''; // For token expiration message

// Check if the token has expired (24 hours)
if(time() - $saved_time > 86400)
{
    $error_message2 = LANG_VALUE_144; // "The token has expired. Please try again."
}

if(isset($_POST['form1'])) {

    $valid = 1;
    
    if( empty($_POST['cust_new_password']) || empty($_POST['cust_re_password']) )
    {
        $valid = 0;
        $error_message .= LANG_VALUE_140.'<br>'; // "New password and re-enter password cannot be empty."
    }
    else
    {
        if($_POST['cust_new_password'] != $_POST['cust_re_password'])
        {
            $valid = 0;
            $error_message .= LANG_VALUE_139.'<br>'; // "Passwords do not match."
        }
    }   

    // CSRF Token Validation - Corrected method call to isTokenValid() with direct $_POST['_csrf']
    if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
        $valid = 0;
        $error_message .= 'Invalid CSRF token. Please refresh the page and try again.<br>';
    }

    if($valid == 1) {
        // Hash the new password securely
        $hashed_new_password = password_hash($_POST['cust_new_password'], PASSWORD_DEFAULT);

        $statement = $pdo->prepare("UPDATE tbl_customer SET cust_password=?, cust_token=?, cust_timestamp=? WHERE cust_email=?");
        // Clear token and timestamp after successful reset
        $statement->execute(array($hashed_new_password, '', '', $_GET['email']));
        
        header('location: '.BASE_URL.'reset-password-success.php');
        exit; // Always exit after a header redirect
    }

    
}
?>

<div class="page-banner" style="background-color:#444;background-image: url(assets/uploads/<?php echo htmlspecialchars($banner_reset_password); ?>);">
    <div class="inner">
        <h1><?php echo LANG_VALUE_149; ?></h1> <!-- Reset Password -->
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <div class="user-content">
                    <?php
                    // Display error/success messages using standard HTML div
                    if($error_message != '') {
                        echo "<div class='alert alert-danger' role='alert' style='margin-bottom:20px;'>".$error_message."</div>";
                    }
                    ?>
                    <?php if($error_message2 != ''): ?>
                        <div class="alert alert-danger" role="alert" style="margin-bottom:20px;"><?php echo $error_message2; ?></div>
                    <?php else: ?>
                        <form action="" method="post">
                            <?php $csrf->echoInputField(); ?>
                            <div class="row">
                                <div class="col-md-4"></div>
                                <div class="col-md-4">
                                    <div class="form-group">
                                        <label for="cust_new_password"><?php echo LANG_VALUE_100; ?> *</label>
                                        <input type="password" class="form-control" id="cust_new_password" name="cust_new_password" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="cust_re_password"><?php echo LANG_VALUE_101; ?> *</label>
                                        <input type="password" class="form-control" id="cust_re_password" name="cust_re_password" required>
                                    </div>
                                    <div class="form-group">
                                        <label for=""></label>
                                        <input type="submit" class="btn btn-primary" value="<?php echo LANG_VALUE_149; ?>" name="form1">
                                    </div>
                                </div>
                            </div>                        
                        </form>
                    <?php endif; ?>
                    
                </div>                
            </div>
        </div>
    </div>
</div>

<style>
    /* Custom Alert Modal (copied from registration.php for consistency) */
    .custom-modal {
        display: none;
        position: fixed;
        z-index: 1001; /* Higher than other modals */
        left: 0;
        top: 0;
        width: 100%;
        height: 100%;
        overflow: auto;
        background-color: rgba(0,0,0,0.4);
        justify-content: center;
        align-items: center;
    }
    .custom-modal-content {
        background-color: #fefefe;
        margin: auto;
        padding: 20px;
        border: 1px solid #888;
        width: 80%;
        max-width: 400px;
        border-radius: 8px;
        position: relative;
        text-align: center;
        box-shadow: 0 5px 15px rgba(0,0,0,0.3);
    }
    .close-modal {
        color: #aaa;
        position: absolute;
        top: 10px;
        right: 15px;
        font-size: 28px;
        font-weight: bold;
        cursor: pointer;
    }
    .close-modal:hover,
    .close-modal:focus {
        color: #000;
        text-decoration: none;
        cursor: pointer;
    }
    #custom-alert-message {
        margin: 20px 0;
        font-size: 1.1em;
        color: #333;
    }

    /* Existing form styles from the file, ensure they are present in your style.css */
    .form-group {
        margin-bottom: 15px;
    }
    .form-control {
        width: 100%;
        padding: 10px 12px;
        border: 1px solid #ccc;
        border-radius: 6px;
    }
    .btn-primary {
        background-color: #007bff;
        color: #fff;
        border: none;
        padding: 10px 20px;
        border-radius: 8px;
        cursor: pointer;
    }
    .btn-primary:hover {
        background-color: #0056b3;
    }
    .alert-danger {
        color: #721c24;
        background-color: #f8d7da;
        border-color: #f5c6cb;
        padding: .75rem 1.25rem;
        margin-bottom: 1rem;
        border: 1px solid transparent;
        border-radius: .25rem;
    }
    .alert-success {
        color: #155724;
        background-color: #d4edda;
        border-color: #c3e6cb;
        padding: .75rem 1.25rem;
        margin-bottom: 1rem;
        border: 1px solid transparent;
        border-radius: .25rem;
    }
</style>

<script src="js/jquery-2.2.3.min.js"></script>
<script>
$(document).ready(function() {
    // Custom Alert Function (replaces alert())
    function customAlert(message) {
        const modal = $('#custom-alert-modal');
        $('#custom-alert-message').text(message);
        modal.css('display', 'flex'); // Use flex to center

        $('.close-modal').on('click', function() {
            modal.css('display', 'none');
        });

        $(window).on('click', function(event) {
            if ($(event.target).is(modal)) {
                modal.css('display', 'none');
            }
        });
    }

    // Intercept form submission to use customAlert if PHP messages are echoed
    window.alert = function(message) {
        customAlert(message);
    };

    // Append the custom alert modal HTML to the body if it doesn't exist
    if ($('#custom-alert-modal').length === 0) {
        const customAlertModalHtml = `
            <div id="custom-alert-modal" class="custom-modal">
                <div class="custom-modal-content">
                    <span class="close-modal">&times;</span>
                    <p id="custom-alert-message"></p>
                </div>
            </div>
        `;
        $('body').append(customAlertModalHtml);
    }
});
</script>

<?php require_once('footer.php'); ?>
