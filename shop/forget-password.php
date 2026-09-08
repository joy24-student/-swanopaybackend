<?php require_once('header.php'); ?>

<?php
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC); // Fetch as associative array
$banner_forget_password = $settings_data['banner_forget_password'] ?? 'default_banner_forget_password.jpg';
$contact_email_for_from = $settings_data['contact_email'] ?? 'noreply@yourwebsite.com'; // Use a configured email for 'From' header

$error_message = '';
$success_message = '';

if(isset($_POST['form1'])) {

    $valid = 1;
        
    if(empty($_POST['cust_email'])) {
        $valid = 0;
        $error_message .= LANG_VALUE_131."<br>"; // Email cannot be empty
    } else {
        if (filter_var($_POST['cust_email'], FILTER_VALIDATE_EMAIL) === false) {
            $valid = 0;
            $error_message .= LANG_VALUE_134."<br>"; // Invalid email format
        } else {
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=?");
            $statement->execute(array($_POST['cust_email']));
            $total = $statement->rowCount();                        
            if(!$total) {
                $valid = 0;
                $error_message .= LANG_VALUE_135."<br>"; // Email address not found
            }
        }
    }

    // CSRF Token Validation - Corrected method call to isTokenValid() with direct $_POST['_csrf']
    if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
        $valid = 0;
        $error_message .= 'Invalid CSRF token. Please refresh the page and try again.<br>';
    }

    if($valid == 1) {

        $statement = $pdo->prepare("SELECT forget_password_message FROM tbl_settings WHERE id=1");
        $statement->execute();
        $result_settings = $statement->fetch(PDO::FETCH_ASSOC);                           
        $forget_password_message = $result_settings['forget_password_message'] ?? 'Please check your email for password reset instructions.';

        $token = md5(time() . rand(1000, 9999)); // More unique token
        $now = time();

        // Update customer with new token and timestamp
        $statement = $pdo->prepare("UPDATE tbl_customer SET cust_token=?, cust_timestamp=? WHERE cust_email=?");
        $statement->execute(array($token, $now, strip_tags($_POST['cust_email'])));
        
        // Construct the reset link
        $reset_link = BASE_URL.'reset-password.php?email='.urlencode($_POST['cust_email']).'&token='.urlencode($token);
        
        // Email content
        $message_body = '
            Dear Customer,<br><br>
            You have requested to reset your password. Please click on the link below to reset your password:<br><br>
            <a href="'.$reset_link.'">'.$reset_link.'</a><br><br>
            If you did not request this, please ignore this email.<br><br>
            Thank you,<br>
            Your Website Name
        ';
        
        $to_email = $_POST['cust_email'];
        $to_name = 'Customer'; // Or fetch customer name if available
        $subject = LANG_VALUE_143; // "Password Reset Request"

        // Use the send_email function from functions.php
        try {
            // Ensure send_email function is properly defined and configured in admin/inc/functions.php
            send_email($to_email, $to_name, $subject, $message_body);
            $success_message = $forget_password_message;
        } catch (Exception $e) {
            $error_message .= "Email could not be sent. Mailer Error: {$e->getMessage()}<br>";
        }
    }
}
?>

<div class="page-banner" style="background-color:#444;background-image: url(<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($banner_forget_password); ?>);">
    <div class="overlay"></div>
    <div class="inner">
        <h1><?php echo LANG_VALUE_97; ?></h1> <!-- Forgot Password -->
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
                    if($success_message != '') {
                        echo "<div class='alert alert-success' role='alert' style='margin-bottom:20px;'>".$success_message."</div>";
                    }
                    ?>
                    <form action="" method="post">
                        <?php $csrf->echoInputField(); ?>
                        <div class="row">
                            <div class="col-md-4"></div>
                            <div class="col-md-4">
                                <div class="form-group">
                                    <label for="cust_email_input"><?php echo LANG_VALUE_94; ?> *</label>
                                    <input type="email" class="form-control" id="cust_email_input" name="cust_email" required>
                                </div>
                                <div class="form-group">
                                    <label for=""></label>
                                    <input type="submit" class="btn btn-primary" value="<?php echo LANG_VALUE_4; ?>" name="form1">
                                </div>
                                <a href="login.php" style="color:#e4144d;"><?php echo LANG_VALUE_12; ?></a>
                            </div>
                        </div>                        
                    </form>
                </div>                
            </div>
        </div>
    </div>
</div>

<!-- Custom Alert Modal (replaces alert()) -->
<div id="custom-alert-modal" class="custom-modal">
    <div class="custom-modal-content">
        <span class="close-modal">&times;</span>
        <p id="custom-alert-message"></p>
    </div>
</div>

<style>
    /* Custom Modal Styles (copied from registration.php for consistency) */
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
