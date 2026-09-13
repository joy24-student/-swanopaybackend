<?php if (getenv('SHOP_RUNTIME_DIR')) { $accountMode='register'; require __DIR__ . '/account.php'; exit; } require_once('header.php'); if (isset($_POST['social_login_email'])) { http_response_code(403); exit('Use email and password to sign in.'); } ?>
<?php
use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;
use Twilio\Rest\Client;
// Assuming vendor/autoload.php is in the main directory, adjust path if necessary
require_once ('vendor/autoload.php');


// Initialize PHPMailer (enable exceptions for error handling)
// This is now handled by the send_email function in functions.php, so direct PHPMailer setup here is redundant.
// $mail = new PHPMailer(true);

// Fetch all necessary settings from the database
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC);

// Assign settings to variables, with default fallbacks
$banner_registration = $settings_data['banner_registration'] ?? 'default_banner_registration.jpg';
// SMTP settings are now fetched inside send_email function from functions.php
// $smtp_host = $settings_data['smtp_host'] ?? '';
// $smtp_username = $settings_data['smtp_username'] ?? '';
// $smtp_password = $settings_data['smtp_password'] ?? '';
// $smtp_encryption = $settings_data['smtp_encryption'] ?? 'NONE';
// $smtp_port = $settings_data['smtp_port'] ?? 587;
// $smtp_from_email = $settings_data['smtp_from_email'] ?? 'no-reply@yourdomain.com';
// $smtp_from_name = $settings_data['smtp_from_name'] ?? 'Your Website Name';
$email_verify_subj = $settings_data['email_verify_subj'] ?? 'Account Verification';
$email_verify_body = $settings_data['email_verify_body'] ?? 'Dear [[customer_name]], please click the following link to verify your account: [[verify_link]]';
$google_client_id = $settings_data['google_client_id'] ?? '';
$facebook_app_id = $settings_data['facebook_app_id'] ?? '';
$twilio_account_sid = $settings_data['twilio_account_sid'] ?? '';
$twilio_auth_token = $settings_data['twilio_auth_token'] ?? '';
$twilio_phone_number = $settings_data['twilio_phone_number'] ?? '';

$error_message = '';
$success_message = '';

// --- Handle Standard Email Registration Form Submission ---
if (isset($_POST['form1'])) {
    $valid = 1;

    // Validate all required fields from the multi-step form
    if(empty($_POST['cust_name'])) { $valid = 0; $error_message .= LANG_VALUE_123."<br>"; }
    if(empty($_POST['cust_email'])) {
        $valid = 0; $error_message .= LANG_VALUE_131."<br>";
    } else {
        // Validate email format and check if email already exists
        if (filter_var($_POST['cust_email'], FILTER_VALIDATE_EMAIL) === false) {
            $valid = 0; $error_message .= LANG_VALUE_134."<br>";
        } else {
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=?");
            $statement->execute(array($_POST['cust_email']));
            $total = $statement->rowCount();                            
            if($total) { $valid = 0; $error_message .= LANG_VALUE_147."<br>"; }
        }
    }
    if(empty($_POST['cust_phone'])) { $valid = 0; $error_message .= LANG_VALUE_124."<br>"; }
    if( empty($_POST['cust_password']) || empty($_POST['cust_re_password']) ) {
        $valid = 0; $error_message .= LANG_VALUE_138."<br>";
    }
    if( !empty($_POST['cust_password']) && !empty($_POST['cust_re_password']) ) {
        if($_POST['cust_password'] != $_POST['cust_re_password']) {
            $valid = 0; $error_message .= LANG_VALUE_139."<br>";
        }
    }
    if(empty($_POST['cust_address'])) { $valid = 0; $error_message .= LANG_VALUE_125."<br>"; }
    if(empty($_POST['cust_country'])) { $valid = 0; $error_message .= LANG_VALUE_126."<br>"; }
    if(empty($_POST['cust_city'])) { $valid = 0; $error_message .= LANG_VALUE_127."<br>"; }
    if(empty($_POST['cust_state'])) { $valid = 0; $error_message .= LANG_VALUE_128."<br>"; }
    if(empty($_POST['cust_zip'])) { $valid = 0; $error_message .= LANG_VALUE_129."<br>"; }

    // CSRF Token Validation
    if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
        $valid = 0;
        $error_message .= 'Invalid CSRF token. Please refresh the page and try again.<br>';
    }

    // If all validations pass, proceed with registration and email sending
    if($valid == 1) {
        $token = md5(time()); // Generate a unique token for email verification
        $cust_datetime = date('Y-m-d H:i:s');
        $cust_timestamp = time();

        // HASH PASSWORD SECURELY using password_hash()
        $hashed_password = password_hash($_POST['cust_password'], PASSWORD_DEFAULT);

        // Insert new customer into the database with status 0 (pending verification)
        $insert_statement = $pdo->prepare("INSERT INTO tbl_customer (
            cust_name, cust_email, cust_phone, cust_country, cust_address, cust_city, cust_state, cust_zip, cust_password, cust_token, cust_datetime, cust_timestamp, cust_status, cust_cname, cust_b_name, cust_b_cname, cust_b_phone, cust_b_country, cust_b_address, cust_b_city, cust_b_state, cust_b_zip, cust_s_name, cust_s_cname, cust_s_phone, cust_s_country, cust_s_address, cust_s_city, cust_s_state, cust_s_zip
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        
        $insert_statement->execute(array(
            strip_tags($_POST['cust_name']), strip_tags($_POST['cust_email']), strip_tags($_POST['cust_phone']), strip_tags($_POST['cust_country']), strip_tags($_POST['cust_address']), strip_tags($_POST['cust_city']), strip_tags($_POST['cust_state']), strip_tags($_POST['cust_zip']), $hashed_password, $token, $cust_datetime, $cust_timestamp, 0, // cust_status: 0 for pending verification
            '', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '' // Default empty/zero for other fields
        ));

        // Prepare the email verification link and body content
        $verify_link = BASE_URL . 'verify.php?email=' . urlencode(strip_tags($_POST['cust_email'])) . '&token=' . $token;
        $email_body = str_replace(['[[customer_name]]', '[[verify_link]]'], [strip_tags($_POST['cust_name']), $verify_link], $email_verify_body);
        
        try {
            // Use the send_email function from functions.php
            // This function now uses PHPMailer internally, fetching SMTP settings from DB.
            $email_sent_successfully = send_email(strip_tags($_POST['cust_email']), strip_tags($_POST['cust_name']), $email_verify_subj, $email_body);

            if ($email_sent_successfully) {
                $success_message = LANG_VALUE_136; // Success message for email sent
            } else {
                $error_message .= "Email could not be sent. Please check server logs for details.<br>";
            }
        } catch (Exception $e) {
            error_log("General Email Error: {$e->getMessage()}");
            $error_message .= "An unexpected error occurred while sending email.<br>";
        }
    }
}

// --- Handle Social Login (Google/Facebook) Callback ---
if(isset($_POST['social_login_email'])) {
    header('Content-Type: application/json'); // Respond with JSON for AJAX calls
    $social_login_email = $_POST['social_login_email'];
    $social_login_id = $_POST['social_login_id'];
    $social_login_provider = $_POST['social_login_provider'];
    $social_login_name = $_POST['social_login_name'];

    // Ensure CSRF token is checked for AJAX social logins as well
    if (!isset($_POST['csrf_token']) || !$csrf->isTokenValid($_POST['csrf_token'])) {
        echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token for social login.']);
        exit;
    }

    try {
        // Check if user exists by email
        $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=?");
        $statement->execute([$social_login_email]);
        $customer = $statement->fetch(PDO::FETCH_ASSOC);

        if ($customer) {
            // User exists, update social ID if not already set and log them in
            $update_field = ($social_login_provider === 'Google') ? 'google_id' : 'facebook_id';
            if (empty($customer[$update_field])) {
                $update_stmt = $pdo->prepare("UPDATE tbl_customer SET {$update_field}=? WHERE cust_id=?");
                $update_stmt->execute([$social_login_id, $customer['cust_id']]);
            }
            $_SESSION['customer'] = $customer;
            echo json_encode(['status' => 'success', 'redirect' => BASE_URL . 'dashboard.php']);
        } else {
            // New user, register them with social details
            $cust_datetime = date('Y-m-d H:i:s');
            $cust_timestamp = time();
            
            // Password for social login can be empty or a placeholder hash since it's not used
            $placeholder_password_hash = password_hash(uniqid(rand(), true), PASSWORD_DEFAULT);

            $insert_statement = $pdo->prepare("INSERT INTO tbl_customer (
                cust_name, cust_email, cust_social_id, cust_social_provider, cust_datetime, cust_timestamp, cust_status, cust_password
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            $insert_statement->execute([$social_login_name, $social_login_email, $social_login_id, $social_login_provider, $cust_datetime, $cust_timestamp, 1, $placeholder_password_hash]); // Status 1 for social logins (already verified)
            
            // Fetch the newly registered user's data to set in session
            $new_customer_id = $pdo->lastInsertId();
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=?");
            $statement->execute([$new_customer_id]);
            $_SESSION['customer'] = $statement->fetch(PDO::FETCH_ASSOC);

            echo json_encode(['status' => 'success', 'redirect' => BASE_URL . 'dashboard.php']);
        }
    } catch (PDOException $e) {
        error_log("Social login error: " . $e->getMessage());
        echo json_encode(['status' => 'error', 'message' => 'Database error during social login.']);
    }
    exit; // Important to exit after sending JSON response
}

// --- Handle Mobile Login (Send OTP) ---
if (isset($_POST['form_mobile_login'])) {
    header('Content-Type: application/json');
    $mobile_number = $_POST['mobile_number'] ?? '';
    
    // Ensure CSRF token is checked for AJAX mobile login as well
    if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
        echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token. Please refresh the page.']);
        exit;
    }

    if (empty($mobile_number)) {
        echo json_encode(['status' => 'error', 'message' => 'Mobile number is required.']);
        exit;
    }

    try {
        // Initialize Twilio client
        $client = new Client($twilio_account_sid, $twilio_auth_token);
        $otp_code = rand(100000, 999999); // Generate a 6-digit OTP
        $message = "Your verification code is: " . $otp_code;

        // Send SMS via Twilio
        $client->messages->create(
            $mobile_number,
            array(
                'from' => $twilio_phone_number, // Your Twilio phone number
                'body' => $message
            )
        );

        // Store OTP in session for verification
        $_SESSION['otp_code'] = $otp_code;
        $_SESSION['otp_timestamp'] = time(); // Store timestamp for OTP expiry
        $_SESSION['otp_mobile_number'] = $mobile_number;

        echo json_encode(['status' => 'success', 'message' => 'OTP sent successfully.']);
    } catch (Exception $e) {
        error_log("Twilio Error: " . $e->getMessage());
        echo json_encode(['status' => 'error', 'message' => 'Failed to send OTP. Please check Twilio credentials or mobile number format.']);
    }
    exit; // Important to exit after sending JSON response
}

// --- Handle Mobile OTP Verification ---
if (isset($_POST['form_verify_otp'])) {
    header('Content-Type: application/json');
    $mobile_number = $_POST['mobile_number'] ?? '';
    $otp_code = $_POST['otp_code'] ?? '';

    // Ensure CSRF token is checked for AJAX OTP verification as well
    if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
        echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token. Please refresh the page.']);
        exit;
    }

    if (empty($mobile_number) || empty($otp_code)) {
        echo json_encode(['status' => 'error', 'message' => 'Mobile number and OTP are required.']);
        exit;
    }

    // Verify OTP against session data
    if (isset($_SESSION['otp_mobile_number']) && $_SESSION['otp_mobile_number'] === $mobile_number && isset($_SESSION['otp_code']) && $_SESSION['otp_code'] == $otp_code) {
        // Check OTP expiry (e.g., 5 minutes = 300 seconds)
        if (isset($_SESSION['otp_timestamp']) && (time() - $_SESSION['otp_timestamp']) > 300) {
            echo json_encode(['status' => 'error', 'message' => 'OTP has expired. Please resend.']);
            unset($_SESSION['otp_code']); // Clear expired OTP
            exit;
        }

        try {
            // Check if user exists by mobile number
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_phone=?");
            $statement->execute([$mobile_number]);
            $customer = $statement->fetch(PDO::FETCH_ASSOC);

            if ($customer) {
                // User exists, log them in
                $_SESSION['customer'] = $customer;
            } else {
                // New user, register them with mobile number
                $cust_datetime = date('Y-m-d H:i:s');
                $cust_timestamp = time();
                // Password for mobile login can be empty or a placeholder hash since it's not used directly
                $placeholder_password_hash = password_hash(uniqid(rand(), true), PASSWORD_DEFAULT);

                $insert_statement = $pdo->prepare("INSERT INTO tbl_customer (
                    cust_name, cust_phone, cust_datetime, cust_timestamp, cust_status, cust_password
                ) VALUES (?, ?, ?, ?, ?, ?)");
                $insert_statement->execute(['New User (Mobile)', $mobile_number, $cust_datetime, $cust_timestamp, 1, $placeholder_password_hash]); // Status 1 for mobile users (verified by OTP)

                // Fetch the newly registered user's data to set in session
                $new_customer_id = $pdo->lastInsertId();
                $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=?");
                $statement->execute([$new_customer_id]);
                $_SESSION['customer'] = $statement->fetch(PDO::FETCH_ASSOC);
            }

            // Clear OTP session data after successful verification
            unset($_SESSION['otp_code']);
            unset($_SESSION['otp_timestamp']);
            unset($_SESSION['otp_mobile_number']);

            echo json_encode(['status' => 'success', 'message' => 'Login/Registration successful.', 'redirect' => BASE_URL . 'dashboard.php']);
        } catch (PDOException $e) {
            error_log("OTP verification/login error: " . $e->getMessage());
            echo json_encode(['status' => 'error', 'message' => 'Database error during login/registration.']);
        }
    } else {
        echo json_encode(['status' => 'error', 'message' => 'Invalid OTP. Please try again.']);
    }
    exit; // Important to exit after sending JSON response
}

?>
<head></head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
    <!-- Google Sign-In Platform Library (for Google login button) -->
    <script src="https://accounts.google.com/gsi/client" async defer></script>
    <!-- Facebook SDK (for Facebook login button) -->
    <script async defer crossorigin="anonymous" src="https://connect.facebook.net/en_US/sdk.js#xfbml=1&version=v12.0&appId=<?php echo htmlspecialchars($facebook_app_id); ?>&autoLogAppEvents=1" nonce="YOUR_NONCE_VALUE"></script>
    <!-- Your existing CSS files (e.g., style.css) -->
    <!-- <link rel="stylesheet" href="<?php echo BASE_URL; ?>assets/css/style.css"> -->

</head>
<div class="page-banner" style="background-color:#444;background-image: url(<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($banner_registration); ?>);">
    <div class="inner">
        <h1><?php echo LANG_VALUE_16; ?></h1>
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <div class="user-content registration-form-container">

                    <?php
                    if($error_message != '') {
                        echo "<div class='error-message' style='padding: 10px;background:#ffebeb;border:1px solid #ff0000;color:#ff0000;margin-bottom:20px;'>".$error_message."</div>";
                    }
                    if($success_message != '') {
                        echo "<div class='success-message' style='padding: 10px;background:#ebffeb;border:1px solid #008000;color:#008000;margin-bottom:20px;'>".$success_message."</div>";
                    }
                    ?>
                    <!-- Dedicated div for JavaScript error messages -->
                    <div id="js-error-message" class="error-message" style="display:none; padding: 10px;background:#ffebeb;border:1px solid #ff0000;color:#ff0000;margin-bottom:20px;"></div>

                    <div class="registration-tracker-bar">
                        <div class="step active" id="step-1-tracker" data-step-number="1">1. Basic Info</div>
                        <div class="step" id="step-2-tracker" data-step-number="2">2. Address</div>
                        <div class="step" id="step-3-tracker" data-step-number="3">3. Confirm</div>
                    </div>

                    <form action="" method="post" id="registrationForm">
                        <?php $csrf->echoInputField(); ?>

                        <!-- Step 1: Basic Information -->
                        <div class="form-section active" id="form-step-1">
                            <h3>Basic Information</h3>
                            <div class="row">
                                <div class="col-md-6 form-group">
                                    <label for="cust_name"><?php echo LANG_VALUE_102; ?> *</label>
                                    <input type="text" class="form-control" name="cust_name" id="cust_name" value="<?php echo htmlspecialchars($_POST['cust_name'] ?? ''); ?>" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_email"><?php echo LANG_VALUE_94; ?> *</label>
                                    <input type="email" class="form-control" name="cust_email" id="cust_email" value="<?php echo htmlspecialchars($_POST['cust_email'] ?? ''); ?>" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_phone"><?php echo LANG_VALUE_104; ?> *</label>
                                    <input type="text" class="form-control" name="cust_phone" id="cust_phone" value="<?php echo htmlspecialchars($_POST['cust_phone'] ?? ''); ?>" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_password"><?php echo LANG_VALUE_96; ?> *</label>
                                    <input type="password" class="form-control" name="cust_password" id="cust_password" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_re_password"><?php echo LANG_VALUE_98; ?> *</label>
                                    <input type="password" class="form-control" name="cust_re_password" id="cust_re_password" required>
                                </div>
                            </div>
                            <div class="form-navigation">
                                <button type="button" class="btn btn-primary next-step">Next</button>
                            </div>
                        </div>

                        <!-- Step 2: Address Information -->
                        <div class="form-section" id="form-step-2">
                            <h3>Address Information</h3>
                            <div class="row">
                                <div class="col-md-12 form-group">
                                    <label for="cust_address"><?php echo LANG_VALUE_105; ?> *</label>
                                    <textarea name="cust_address" id="cust_address" class="form-control" cols="30" rows="5" required><?php echo htmlspecialchars($_POST['cust_address'] ?? ''); ?></textarea>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_country"><?php echo LANG_VALUE_106; ?> *</label>
                                    <select name="cust_country" id="cust_country" class="form-control select2" required>
                                        <option value="">Select country</option>
                                    <?php
                                    $statement_country = $pdo->prepare("SELECT * FROM tbl_country ORDER BY country_name ASC");
                                    $statement_country->execute();
                                    $result_country = $statement_country->fetchAll(PDO::FETCH_ASSOC);                            
                                    foreach ($result_country as $row_country) {
                                        ?>
                                        <option value="<?php echo $row_country['country_id']; ?>" <?php echo (isset($_POST['cust_country']) && $_POST['cust_country'] == $row_country['country_id']) ? 'selected' : ''; ?>><?php echo htmlspecialchars($row_country['country_name']); ?></option>
                                        <?php
                                    }
                                    ?>    
                                    </select>                                    
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_city"><?php echo LANG_VALUE_107; ?> *</label>
                                    <input type="text" class="form-control" name="cust_city" id="cust_city" value="<?php echo htmlspecialchars($_POST['cust_city'] ?? ''); ?>" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_state"><?php echo LANG_VALUE_108; ?> *</label>
                                    <input type="text" class="form-control" name="cust_state" id="cust_state" value="<?php echo htmlspecialchars($_POST['cust_state'] ?? ''); ?>" required>
                                </div>
                                <div class="col-md-6 form-group">
                                    <label for="cust_zip"><?php echo LANG_VALUE_109; ?> *</label>
                                    <input type="text" class="form-control" name="cust_zip" id="cust_zip" value="<?php echo htmlspecialchars($_POST['cust_zip'] ?? ''); ?>" required>
                                </div>
                            </div>
                            <div class="form-navigation">
                                <button type="button" class="btn btn-secondary prev-step">Previous</button>
                                <button type="button" class="btn btn-primary next-step">Next</button>
                            </div>
                        </div>

                        <!-- Step 3: Confirmation and Submit -->
                        <div class="form-section" id="form-step-3">
                            <h3>Confirm Your Details</h3>
                            <div class="confirmation-summary">
                                <p><strong>Full Name:</strong> <span id="confirm_cust_name"></span></p>
                                <p><strong>Email:</strong> <span id="confirm_cust_email"></span></p>
                                <p><strong>Phone:</strong> <span id="confirm_cust_phone"></span></p>
                                <p><strong>Address:</strong> <span id="confirm_cust_address"></span></p>
                                <p><strong>Country:</strong> <span id="confirm_cust_country"></span></p>
                                <p><strong>City:</strong> <span id="confirm_cust_city"></span></p>
                                <p><strong>State:</strong> <span id="confirm_cust_state"></span></p>
                                <p><strong>Zip Code:</strong> <span id="confirm_cust_zip"></span></p>
                            </div>
                            <div class="form-navigation">
                                <button type="button" class="btn btn-secondary prev-step">Previous</button>
                                <button type="submit" class="btn btn-success" name="form1">Register</button>
                            </div>
                        </div>

                        <!-- Hidden fields for optional data (if needed, e.g., for billing/shipping company) -->
                        <input type="hidden" name="cust_cname" value="">
                        <input type="hidden" name="cust_b_name" value="">
                        <input type="hidden" name="cust_b_cname" value="">
                        <input type="hidden" name="cust_b_phone" value="">
                        <input type="hidden" name="cust_b_country" value="0">
                        <input type="hidden" name="cust_b_address" value="">
                        <input type="hidden" name="cust_b_city" value="">
                        <input type="hidden" name="cust_b_state" value="">
                        <input type="hidden" name="cust_b_zip" value="">
                        <input type="hidden" name="cust_s_name" value="">
                        <input type="hidden" name="cust_s_cname" value="">
                        <input type="hidden" name="cust_s_phone" value="">
                        <input type="hidden" name="cust_s_country" value="0">
                        <input type="hidden" name="cust_s_address" value="">
                        <input type="hidden" name="cust_s_city" value="">
                        <input type="hidden" name="cust_s_state" value="">
                        <input type="hidden" name="cust_s_zip" value="">
                    </form>
                    
                    <div class="social-login-buttons">
                        <!-- Google Sign-In Button (rendered by GSI client) -->
                        <div id="g_id_onload"
                             data-client_id="<?php echo htmlspecialchars($google_client_id); ?>"
                             data-callback="onGoogleSignIn"
                             data-auto_prompt="false">
                        </div>
                        <div class="g_id_signin"
                             data-type="standard"
                             data-size="large"
                             data-theme="outline"
                             data-text="signup_with"
                             data-shape="rectangular"
                             data-logo_alignment="left">
                        </div>
                        
                        <!-- Facebook Login Button -->
                        <br><button type="button" class="btn btn-facebook" id="facebook-login"><i class="fab fa-facebook-f"></i> &nbsp; Sign up with Facebook</button>
                    </div>

                    <!-- Mobile Login Section -->
<div class="d-md-none text-center"> <button type="button" class="btn btn-primary" id="open-mobile-login-modal">
        <i class="fas fa-mobile-alt"></i> Login with Mobile
    </button>
</div>

<div id="mobileLoginModal" class="modal">
    <div class="modal-content">
        <span class="close-btn">&times;</span>
        <div class="mobile-login-container">
            <h4 class="text-center">Login or Register with Mobile Number</h4>
            <div id="mobile-login-section">
                <form id="mobile-login-form">
                    <?php $csrf->echoInputField(); ?>
                    <div class="form-group">
                        <label for="mobile_number">Mobile Number (e.g., +11234567890)</label>
                        <input type="text" class="form-control" id="mobile_number" name="mobile_number" placeholder="Enter your mobile number" required>
                    </div>
                    <button type="submit" class="btn btn-primary" name="form_mobile_login">Send OTP</button>
                </form>
            </div>
            <div id="otp-verification-section" style="display: none;">
                <form id="otp-verification-form">
                    <?php $csrf->echoInputField(); ?>
                    <input type="hidden" name="mobile_number" id="otp_mobile_number_field">
                    <div class="form-group">
                        <label for="otp_code">Enter OTP</label>
                        <input type="text" class="form-control" id="otp_code" name="otp_code" placeholder="Enter 6-digit OTP" required>
                    </div>
                    <button type="submit" class="btn btn-success" name="form_verify_otp">Verify OTP</button>
                </form>
            </div>
        </div>
    </div>
</div>
                    </div>
                    
                    <hr>
            

                </div>                
            </div>
        </div>
    </div>
</div>

<style>
/* Add this CSS to your assets/css/style.css or directly in a <style> tag in registration.php */


        /* Social and Mobile Login Buttons */
     /* Update the existing social-login-buttons class */
     
/* New CSS for the Modal */
.modal {
    display: none; /* Hidden by default */
    position: fixed; /* Stay in place */
    z-index: 1050; /* Sit on top of other content */
    left: 0;
    top: 0;
    width: 100%; /* Full width */
    height: 100%; /* Full height */
    overflow: auto; /* Enable scroll if needed */
    background-color: rgba(0,0,0,0.4); /* Black w/ opacity */
    padding-top: 60px;
}

.modal-content {
    background-color: #fefefe;
    margin: 5% auto; /* 5% from the top and centered */
    padding: 20px;
    border: 1px solid #888;
    width: 90%; /* Default width for mobile */
    max-width: 500px; /* Max width for larger screens */
    border-radius: 8px;
    position: relative;
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.close-btn {
    color: #aaa;
    float: right;
    font-size: 28px;
    font-weight: bold;
}

.close-btn:hover,
.close-btn:focus {
    color: black;
    text-decoration: none;
    cursor: pointer;
}

/* Original mobile login container CSS, now for inside the modal */
.mobile-login-container {
    margin-top: 0; /* Resetting margin as it's now in a modal */
    padding: 0; /* Resetting padding */
    border: none; /* No border inside the modal */
    background-color: transparent; /* Transparent background */
}

/* Hide the old mobile login container on medium and larger screens */
@media (min-width: 768px) {
    .mobile-login-container {
        display: block !important; /* Keep it visible for desktop */
    }
    #open-mobile-login-modal {
        display: none; /* Hide the modal trigger button on desktop */
    }
}

/* Specific styling for the trigger button */
#open-mobile-login-modal {
    width: 100%;
    margin-top: 20px;
    padding: 15px;
    font-size: 18px;
}
     
     
     
.social-login-buttons {
    display: flex;
    justify-content: center; /* Center buttons horizontally */
    align-items: center; /* Align items vertically */
    flex-wrap: wrap; /* Allow buttons to wrap to the next line on small screens */
    gap: 15px; /* Add space between buttons */
    margin: 25px 0; /* Add margin above and below */
}

/* Style the Facebook button to match Google's appearance */
.btn-facebook {
    background-color: #3b5998;
    color: #fff;
    border: none;
    padding: 12px 25px; /* Standardize padding */
    border-radius: 8px;
    font-weight: bold;
    cursor: pointer;
    transition: background-color 0.3s ease;
    display: inline-flex;
    align-items: center;
    text-decoration: none;
}

.btn-facebook:hover {
    background-color: #2d4373;
}

/* Ensure the Google button is also styled for consistency, if needed */
.g_id_signin {
    /* The Google button is handled by their JS, but you can target the container */
    margin: 0; /* Remove any default margins that might interfere */
    height: 48px; /* Standardize height */
}

/* Media query to handle mobile view */
@media (max-width: 767px) {
    .social-login-buttons {
        flex-direction: column; /* Stack buttons vertically on small screens */
        gap: 10px;
    }
    .social-login-buttons > div, /* Target the Google button container */
    .social-login-buttons .btn-facebook {
        width: 100%; /* Make buttons full width on mobile */
    }
}
        .mobile-login-container {
            margin-top: 20px;
            padding: 20px;
            border: 1px solid #eee;
            border-radius: 8px;
            background-color: #fcfcfc;
        }

        .mobile-login-container h4 {
            text-align: center;
            margin-bottom: 20px;
            color: #555;
        }

        .mobile-login-container .form-group {
            margin-bottom: 15px;
        }

        .mobile-login-container .btn-primary, .mobile-login-container .btn-success {
            width: 100%;
            margin-top: 10px;
        }

        hr {
            border-top: 1px solid #eee;
            margin: 30px 0;
        }



.registration-form-container {
    background-color: #fff;
    padding: 30px;
    border-radius: 8px;
    box-shadow: 0 0 15px rgba(0,0,0,0.05);
    max-width: 800px;
    margin: 30px auto;
}

.registration-tracker-bar {
    display: flex;
    justify-content: space-between;
    margin-bottom: 30px;
    position: relative;
    padding: 0 20px;
}

.registration-tracker-bar::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 10%;
    right: 10%;
    height: 4px;
    background-color: #e0e0e0;
    transform: translateY(-50%);
    z-index: 0;
}

.registration-tracker-bar .step {
    flex: 1;
    text-align: center;
    position: relative;
    padding-top: 30px;
    color: #888;
    font-weight: bold;
    font-size: 0.9em;
    z-index: 1;
}

.registration-tracker-bar .step::before {
    content: attr(data-step-number); /* Use custom attribute for number */
    position: absolute;
    top: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 30px;
    height: 30px;
    background-color: #e0e0e0;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 1.1em;
    border: 2px solid #e0e0e0;
    transition: background-color 0.3s ease, border-color 0.3s ease;
}

.registration-tracker-bar .step.active {
    color: #f14040;
}

.registration-tracker-bar .step.active::before {
    background-color: #f14040;
    border-color: #f14040;
}

.registration-tracker-bar .step.completed {
    color: #008000; /* Green for completed steps */
}

.registration-tracker-bar .step.completed::before {
    background-color: #008000;
    border-color: #008000;
    content: '✔'; /* Checkmark for completed steps */
    font-size: 1.2em;
}


.form-section {
    display: none;
    opacity: 0;
    transition: opacity 0.5s ease-in-out;
}

.form-section.active {
    display: block;
    opacity: 1;
}

.form-section h3 {
    margin-bottom: 25px;
    color: #333;
    border-bottom: 1px solid #eee;
    padding-bottom: 10px;
}

.form-navigation {
    margin-top: 30px;
    display: flex;
    justify-content: space-between;
}

.form-navigation button {
    padding: 10px 25px;
    border-radius: 5px;
    font-size: 16px;
    cursor: pointer;
}



}

@media (max-width: 767px) {
    .registration-form-container {
        padding: 15px;
        margin: 15px auto;
    }
    .registration-tracker-bar .step {
        font-size: 0.8em;
    }
    .form-navigation button {
        width: 100%;
        margin-bottom: 10px;
    }
    .form-navigation {
        flex-direction: column;
    }
    
            .user-content {
                padding: 20px;
                margin: 15px auto;
            }
            .page-banner h1 {
                font-size: 2.2em;
            }
            .social-login-buttons {
                flex-direction: column;
                gap: 10px;
            }
            .social-login-buttons .btn {
                width: 100%;
            }
           
}
</style>

<script>
document.addEventListener('DOMContentLoaded', function() {
    const formSections = document.querySelectorAll('.form-section');
    const trackerSteps = document.querySelectorAll('.registration-tracker-bar .step');
    let currentStep = 0;

    function showStep(stepIndex) {
        formSections.forEach((section, index) => {
            if (index === stepIndex) {
                section.classList.add('active');
            } else {
                section.classList.remove('active');
            }
        });

        trackerSteps.forEach((step, index) => {
            step.classList.remove('active', 'completed');
            if (index === stepIndex) {
                step.classList.add('active');
            } else if (index < stepIndex) {
                step.classList.add('completed');
            }
        });
        // Ensure data-step-number is set correctly for initial load and subsequent steps
        trackerSteps[0].setAttribute('data-step-number', '1');
        trackerSteps[1].setAttribute('data-step-number', '2');
        trackerSteps[2].setAttribute('data-step-number', '3');
    }

    function validateStep(stepIndex) {
        let isValid = true;
        const currentSection = formSections[stepIndex];
        const inputs = currentSection.querySelectorAll('[required]');
        const jsErrorMessageDiv = document.getElementById('js-error-message');
        let errorMessages = []; // Collect error messages

        inputs.forEach(input => {
            if (!input.value.trim()) {
                isValid = false;
                input.classList.add('is-invalid');
                errorMessages.push(`Please fill in "${input.previousElementSibling ? input.previousElementSibling.textContent.replace('*', '').trim() : input.name}".`);
            } else {
                input.classList.remove('is-invalid');
            }
        });

        if (stepIndex === 0) {
            const email = document.getElementById('cust_email');
            const password = document.getElementById('cust_password');
            const rePassword = document.getElementById('cust_re_password');

            if (!/\S+@\S+\.\S+/.test(email.value)) {
                isValid = false;
                email.classList.add('is-invalid');
                errorMessages.push("Please enter a valid email address.");
            } else {
                email.classList.remove('is-invalid');
            }

            if (password.value !== rePassword.value) {
                isValid = false;
                password.classList.add('is-invalid');
                rePassword.classList.add('is-invalid');
                errorMessages.push("Passwords do not match.");
            } else {
                if (password.value.trim()) password.classList.remove('is-invalid');
                if (rePassword.value.trim()) rePassword.classList.remove('is-invalid');
            }
        }
        if (stepIndex === 1) {
            const country = document.getElementById('cust_country');
            if (!country.value.trim()) {
                isValid = false;
                country.classList.add('is-invalid');
                errorMessages.push("Please select a country.");
            } else {
                country.classList.remove('is-invalid');
            }
        }
        
        if (!isValid) {
            jsErrorMessageDiv.innerHTML = errorMessages.join('<br>');
            jsErrorMessageDiv.style.display = 'block';
        } else {
            jsErrorMessageDiv.style.display = 'none';
        }

        return isValid;
    }

    document.querySelectorAll('.next-step').forEach(button => {
        button.addEventListener('click', () => {
            if (validateStep(currentStep)) {
                if (currentStep === 0) { // For Step 1, perform AJAX email check
                    const email = document.getElementById('cust_email').value;
                    $.ajax({
                        url: '<?php echo BASE_URL; ?>check_email_exists.php',
                        type: 'POST',
                        data: { email: email, _csrf: $('input[name="_csrf"]').val() }, // Assuming CSRF token name is _csrf
                        success: function(response) {
                            if (response.exists) {
                                customAlert('This email is already registered.');
                                document.getElementById('cust_email').classList.add('is-invalid');
                            } else {
                                document.getElementById('cust_email').classList.remove('is-invalid');
                                if (currentStep < formSections.length - 1) {
                                    currentStep++;
                                    showStep(currentStep);
                                    if (currentStep === 2) {
                                        updateConfirmationSummary();
                                    }
                                }
                            }
                        },
                        error: function(xhr, status, error) {
                            console.error("AJAX Error:", status, error);
                            customAlert('Error checking email. Please try again. (Details: ' + error + ')');
                        }
                    });
                } else { // For other steps, just advance
                    if (currentStep < formSections.length - 1) {
                        currentStep++;
                        showStep(currentStep);
                        if (currentStep === 2) {
                            updateConfirmationSummary();
                        }
                    }
                }
            }
        });
    });

    document.querySelectorAll('.prev-step').forEach(button => {
        button.addEventListener('click', () => {
            const jsErrorMessageDiv = document.getElementById('js-error-message');
            if (jsErrorMessageDiv) {
                jsErrorMessageDiv.style.display = 'none';
            }

            if (currentStep > 0) {
                currentStep--;
                showStep(currentStep);
            }
        });
    });

    function updateConfirmationSummary() {
        if (currentStep === 2) {
            document.getElementById('confirm_cust_name').textContent = document.getElementById('cust_name').value;
            document.getElementById('confirm_cust_email').textContent = document.getElementById('cust_email').value;
            document.getElementById('confirm_cust_phone').textContent = document.getElementById('cust_phone').value;
            document.getElementById('confirm_cust_address').textContent = document.getElementById('cust_address').value;
            document.getElementById('confirm_cust_city').textContent = document.getElementById('cust_city').value;
            document.getElementById('confirm_cust_state').textContent = document.getElementById('cust_state').value;
            document.getElementById('confirm_cust_zip').textContent = document.getElementById('cust_zip').value;
            
            const countrySelect = document.getElementById('cust_country');
            if (countrySelect.selectedIndex !== -1) {
                 document.getElementById('confirm_cust_country').textContent = countrySelect.options[countrySelect.selectedIndex].text;
            } else {
                 document.getElementById('confirm_cust_country').textContent = 'N/A';
            }
        }
    }

    showStep(currentStep);

    // --- NEW: Modal Logic for Mobile Login ---
    const mobileLoginModal = document.getElementById("mobileLoginModal");
    const openModalBtn = document.getElementById("open-mobile-login-modal");
    const closeModalSpan = document.querySelector("#mobileLoginModal .close-btn");

    if (openModalBtn) {
        openModalBtn.onclick = function() {
            mobileLoginModal.style.display = "block";
        }
    }

    if (closeModalSpan) {
        closeModalSpan.onclick = function() {
            mobileLoginModal.style.display = "none";
        }
    }

    window.onclick = function(event) {
        if (event.target === mobileLoginModal) {
            mobileLoginModal.style.display = "none";
        }
    }

    // --- Existing AJAX for Mobile Login/OTP ---
    const mobileLoginForm = document.getElementById('mobile-login-form');
    const otpVerificationForm = document.getElementById('otp-verification-form');
    const mobileNumberField = document.getElementById('mobile_number');
    const otpMobileNumberField = document.getElementById('otp_mobile_number_field');
    const mobileLoginSection = document.getElementById('mobile-login-section');
    const otpVerificationSection = document.getElementById('otp-verification-section');
    // jsErrorMessageDiv is already defined globally at the top of the script
    const jsErrorMessageDiv = document.getElementById('js-error-message');


    // Handle "Send OTP" form submission
    if (mobileLoginForm) {
        mobileLoginForm.addEventListener('submit', function(e) {
            e.preventDefault();
            const formData = new FormData(mobileLoginForm);
            fetch('registration.php', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (jsErrorMessageDiv) jsErrorMessageDiv.style.display = 'none';
                if (data.status === 'success') {
                    otpMobileNumberField.value = mobileNumberField.value;
                    mobileLoginSection.style.display = 'none';
                    otpVerificationSection.style.display = 'block';
                } else {
                    if (jsErrorMessageDiv) {
                        jsErrorMessageDiv.innerHTML = data.message;
                        jsErrorMessageDiv.style.display = 'block';
                    }
                }
            })
            .catch(error => {
                console.error('Error:', error);
                if (jsErrorMessageDiv) {
                    jsErrorMessageDiv.innerHTML = 'An unexpected error occurred.';
                    jsErrorMessageDiv.style.display = 'block';
                }
            });
        });
    }

    // Handle "Verify OTP" form submission
    if (otpVerificationForm) {
        otpVerificationForm.addEventListener('submit', function(e) {
            e.preventDefault();
            const formData = new FormData(otpVerificationForm);
            fetch('registration.php', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (jsErrorMessageDiv) jsErrorMessageDiv.style.display = 'none';
                if (data.status === 'success') {
                    window.location.href = data.redirect;
                } else {
                    if (jsErrorMessageDiv) {
                        jsErrorMessageDiv.innerHTML = data.message;
                        jsErrorMessageDiv.style.display = 'block';
                    }
                }
            })
            .catch(error => {
                console.error('Error:', error);
                if (jsErrorMessageDiv) {
                    jsErrorMessageDiv.innerHTML = 'An unexpected error occurred.';
                    jsErrorMessageDiv.style.display = 'block';
                }
            });
        });
    }

    // --- Custom Alert Modal (replaces alert()) ---
    const customAlertModalHtml = `
        <div id="custom-alert-modal" class="custom-modal">
            <div class="custom-modal-content">
                <span class="close-modal">&times;</span>
                <p id="custom-alert-message"></p>
            </div>
        </div>
        <style>
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
        </style>
    `;
    // Only append if it doesn't already exist to prevent duplicates on multiple script runs
    if ($('#custom-alert-modal').length === 0) {
        $('body').append(customAlertModalHtml);
    }


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
    // Intercept alert calls to use customAlert
    window.alert = customAlert;


});
</script>

<?php require_once('footer.php'); ?>
