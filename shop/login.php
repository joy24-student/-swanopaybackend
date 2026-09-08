<?php require_once('header.php'); ?>

<?php
// Fetch banner login from settings
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC); // Fetch all settings
$banner_login = $settings_data['banner_login'] ?? 'default_banner_login.jpg';

// Get API keys from settings
$google_client_id = $settings_data['google_client_id'] ?? '';
$facebook_app_id = $settings_data['facebook_app_id'] ?? '';
$twilio_account_sid = $settings_data['twilio_account_sid'] ?? '';
$twilio_auth_token = $settings_data['twilio_auth_token'] ?? '';
$twilio_phone_number = $settings_data['twilio_phone_number'] ?? '';


$error_message = '';
$success_message = '';

// Handle standard form login
if(isset($_POST['form1'])) {
    // CSRF Token Validation (ensure this is present and correct as in other forms)
    // Assuming $csrf->isTokenValid($_POST['_csrf']) is checked here by header.php or parent includes.
    // If not, explicitly add:
    // if (!isset($_POST['_csrf']) || !$csrf->isTokenValid($_POST['_csrf'])) {
    //     $error_message .= 'Invalid CSRF token. Please refresh the page and try again.<br>';
    // }

    if(empty($_POST['cust_email']) || empty($_POST['cust_password'])) {
        $error_message .= LANG_VALUE_132.'<br>';
    } else {
        $cust_email = strip_tags($_POST['cust_email']);
        $cust_password = $_POST['cust_password']; // Keep as plain text for password_verify

        $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=?");
        $statement->execute(array($cust_email));
        $total = $statement->rowCount();
        $row = $statement->fetch(PDO::FETCH_ASSOC);

        if($total==0) {
            $error_message .= LANG_VALUE_133.'<br>';
        } else {
            // VERIFY PASSWORD USING password_verify()
            // $row['cust_password'] now contains the securely hashed password (e.g., bcrypt)
            if( !password_verify($cust_password, $row['cust_password']) ) {
                $error_message .= LANG_VALUE_139.'<br>';
            } else {
                if($row['cust_status'] == 0) {
                    $error_message .= LANG_VALUE_148.'<br>';
                } else {
                    $_SESSION['customer'] = $row;
                    
                    $customer_id = $_SESSION['customer']['cust_id'];
                    // Load cart from database into session after successful login
                    if(function_exists('loadCartFromDatabase')) {
                        loadCartFromDatabase($pdo, $customer_id);
                    } else {
                        error_log("Error: loadCartFromDatabase function not found in inc/functions.php");
                    }
                    header("location: ".BASE_URL."dashboard.php");
                    exit;
                }
            }
        }
    }
}

// Handle Social Login Callback (e.g., from Google/Facebook JS SDK)
if (isset($_POST['social_login_email']) && isset($_POST['social_login_id']) && isset($_POST['social_login_provider'])) {
    // Ensure CSRF token is checked for AJAX social logins as well
    // Assuming $csrf->isTokenValid($_POST['csrf_token']) is checked
    // if (!isset($_POST['csrf_token']) || !$csrf->isTokenValid($_POST['csrf_token'])) {
    //     echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token for social login.']);
    //     exit;
    // }

    header('Content-Type: application/json'); // Respond with JSON for AJAX calls
    $social_email = $_POST['social_login_email'];
    $social_id = $_POST['social_login_id'];
    $social_provider = $_POST['social_login_provider'];
    $social_name = $_POST['social_login_name'] ?? '';

    $column_name = '';
    if ($social_provider == 'google') {
        $column_name = 'google_id';
    } elseif ($social_provider == 'facebook') {
        $column_name = 'facebook_id';
    }

    if ($column_name) {
        // Check if user exists by social ID
        $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE {$column_name}=? LIMIT 1");
        $statement->execute([$social_id]);
        $customer_exists = $statement->fetch(PDO::FETCH_ASSOC);

        if ($customer_exists) {
            // User exists, log them in
            $_SESSION['customer'] = $customer_exists;
            // Load cart from database into session for social login
            if(function_exists('loadCartFromDatabase')) {
                loadCartFromDatabase($pdo, $customer_exists['cust_id']);
            }
            echo json_encode(['status' => 'success', 'redirect' => BASE_URL . 'dashboard.php']);
            exit;
        } else {
            // User does not exist by social ID, check by email
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_email=? LIMIT 1");
            $statement->execute([$social_email]);
            $customer_by_email = $statement->fetch(PDO::FETCH_ASSOC);

            if ($customer_by_email) {
                // Email exists, link social ID to existing account
                $update_sql = "UPDATE tbl_customer SET {$column_name}=? WHERE cust_id=?";
                $update_stmt = $pdo->prepare($update_sql);
                $update_stmt->execute([$social_id, $customer_by_email['cust_id']]);
                
                $_SESSION['customer'] = $customer_by_email;
                // Load cart from database into session for social login
                if(function_exists('loadCartFromDatabase')) {
                    loadCartFromDatabase($pdo, $customer_by_email['cust_id']);
                }
                echo json_encode(['status' => 'success', 'redirect' => BASE_URL . 'dashboard.php']);
                exit;
            } else {
                // New user, register them
                $token = md5(time() . $social_email); // Generate a token (though for social login, token might not be used for email verification)
                $cust_datetime = date('Y-m-d H:i:s');
                $cust_timestamp = time();

                // For social logins, password can be null/empty, status is 1 (verified)
                $insert_statement = $pdo->prepare("INSERT INTO tbl_customer (
                    cust_name, cust_email, cust_phone, cust_country, cust_address, cust_city, cust_state, cust_zip,
                    cust_password, cust_token, cust_datetime, cust_timestamp, cust_status, {$column_name},
                    cust_cname, cust_b_name, cust_b_cname, cust_b_phone, cust_b_country, cust_b_address, cust_b_city, cust_b_state, cust_b_zip, cust_s_name, cust_s_cname, cust_s_phone, cust_s_country, cust_s_address, cust_s_city, cust_s_state, cust_s_zip
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                
                $insert_statement->execute(array(
                    $social_name, $social_email, '', 0, '', '', '', '',
                    '', $token, $cust_datetime, $cust_timestamp, 1, $social_id, // Status 1 for social login (already verified)
                    '', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '' // Default empty/zero for other fields
                ));

                $new_customer_id = $pdo->lastInsertId();
                $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=?");
                $statement->execute([$new_customer_id]);
                $_SESSION['customer'] = $statement->fetch(PDO::FETCH_ASSOC);
                
                // Load cart for newly registered social user
                if(function_exists('loadCartFromDatabase')) {
                    loadCartFromDatabase($pdo, $new_customer_id);
                }

                echo json_encode(['status' => 'success', 'redirect' => BASE_URL . 'dashboard.php']);
                exit;
            }
        }
    }
}
?>

<div class="page-banner" style="background-color:#444;background-image: url(assets/uploads/<?php echo htmlspecialchars($banner_login); ?>);">
    <div class="inner">
        <h1><?php echo LANG_VALUE_10; ?></h1>
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <div class="user-content login-form-container">
                    <?php
                    if($error_message != '') {
                        echo "<div class='error-message' style='padding: 10px;background:#ffebeb;border:1px solid #ff0000;color:#ff0000;margin-bottom:20px;'>".$error_message."</div>";
                    }
                    if($success_message != '') {
                        echo "<div class='success-message' style='padding: 10px;background:#ebffeb;border:1px solid #008000;color:#008000;margin-bottom:20px;'>".$success_message."</div>";
                    }
                    ?>

                    <div class="login-options">
                        <h2>Login to Your Account</h2>
                        <div class="social-login-buttons">
                            <?php if (!empty($google_client_id)): ?>
                                <button type="button" class="btn btn-block btn-google" id="google-login-btn"><i class="fab fa-google"></i> Sign in with Google</button>
                            <?php endif; ?>
                            <?php if (!empty($facebook_app_id)): ?>
                                <button type="button" class="btn btn-block btn-facebook" id="facebook-login-btn"><i class="fab fa-facebook-f"></i> Sign in with Facebook</button>
                            <?php endif; ?>
                            <button type="button" class="btn btn-block btn-mobile" id="mobile-login-btn"><i class="fas fa-mobile-alt"></i> Login with Mobile Number</button>
                        </div>

                        <div class="or-separator"><span>OR</span></div>

                        <form action="" method="post" id="standardLoginForm">
                            <?php $csrf->echoInputField(); ?>                  
                            <div class="form-group">
                                <label for="cust_email"><?php echo LANG_VALUE_94; ?> *</label>
                                <input type="email" class="form-control" name="cust_email" id="cust_email">
                            </div>
                            <div class="form-group">
                                <label for="cust_password"><?php echo LANG_VALUE_96; ?> *</label>
                                <input type="password" class="form-control" name="cust_password" id="cust_password">
                            </div>
                            <div class="form-group">
                                <button type="submit" class="btn btn-success btn-block" name="form1"><?php echo LANG_VALUE_4; ?></button>
                            </div>
                            <div class="text-center">
                                <a href="forget-password.php" class="forgot-password-link"><?php echo LANG_VALUE_97; ?>?</a>
                            </div>
                            <div class="text-center mt-3">
                                <p>Don't have an account? <a href="<?php echo BASE_URL; ?>registration.php">Register Now</a></p>
                            </div>
                        </form>

                        <!-- Mobile Login Section -->
                        <div class="mobile-login-section" style="display:none;">
                            <h3>Login with Mobile Number</h3>
                            <div class="form-group">
                                <label for="mobile_number">Mobile Number *</label>
                                <input type="text" class="form-control" id="mobile_number" placeholder="+8801XXXXXXXXX">
                            </div>
                            <button type="button" class="btn btn-primary btn-block" id="send-otp-btn">Send OTP</button>
                            
                            <div class="otp-verification-section mt-3" style="display:none;">
                                <div class="form-group">
                                    <label for="otp_code">Enter OTP *</label>
                                    <input type="text" class="form-control" id="otp_code" placeholder="XXXXXX">
                                </div>
                                <button type="button" class="btn btn-success btn-block" id="verify-otp-btn">Verify OTP</button>
                                <div class="text-center mt-2">
                                    <a href="#" id="resend-otp-link">Resend OTP</a>
                                </div>
                            </div>
                            <div class="text-center mt-3">
                                <button type="button" class="btn btn-secondary" id="back-to-email-login">Back to Email Login</button>
                            </div>
                        </div>
                    </div>
                </div>                
            </div>
        </div>
    </div>
</div>

<!-- Google Platform Library -->
<?php if (!empty($google_client_id)): ?>
<script src="https://accounts.google.com/gsi/client" async defer></script>
<?php endif; ?>

<!-- Facebook SDK -->
<?php if (!empty($facebook_app_id)): ?>
<script async defer crossorigin="anonymous" src="https://connect.facebook.net/en_US/sdk.js"></script>
<?php endif; ?>

<style>
/* Add this CSS to your assets/css/style.css */

.login-form-container {
    background-color: #fff;
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 0 20px rgba(0,0,0,0.08);
    max-width: 500px;
    margin: 50px auto;
    text-align: center;
    animation: fadeIn 0.8s ease-out;
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(-20px); }
    to { opacity: 1; transform: translateY(0); }
}

.login-form-container h2 {
    color: #333;
    margin-bottom: 30px;
    font-weight: 600;
}

.social-login-buttons button {
    margin-bottom: 15px;
    padding: 12px 20px;
    font-size: 16px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    border: none;
    cursor: pointer;
    transition: all 0.3s ease;
}

.social-login-buttons button i {
    font-size: 20px;
}

.btn-google {
    background-color: #dd4b39;
    color: #fff;
}
.btn-google:hover {
    background-color: #c23321;
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.btn-facebook {
    background-color: #3b5998;
    color: #fff;
}
.btn-facebook:hover {
    background-color: #2d4373;
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.btn-mobile {
    background-color: #28a745; /* Green for mobile */
    color: #fff;
}
.btn-mobile:hover {
    background-color: #218838;
    transform: translateY(-2px);
    box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}

.or-separator {
    display: flex;
    align-items: center;
    text-align: center;
    margin: 30px 0;
    color: #aaa;
}
.or-separator::before,
.or-separator::after {
    content: '';
    flex: 1;
    border-bottom: 1px solid #eee;
}
.or-separator:not(:empty)::before {
    margin-right: .25em;
}
.or-separator:not(:empty)::after {
    margin-left: .25em;
}

/* Standard form styles */
#standardLoginForm .form-group label {
    text-align: left;
    display: block;
    margin-bottom: 8px;
    color: #555;
    font-weight: 500;
}
#standardLoginForm .form-control {
    border-radius: 5px;
    padding: 10px 15px;
    height: auto;
    font-size: 16px;
}
#standardLoginForm .btn-success {
    background-color: #f14040;
    border-color: #f14040;
    transition: background-color 0.3s ease, transform 0.3s ease;
}
#standardLoginForm .btn-success:hover {
    background-color: #d63434;
    border-color: #d63434;
    transform: translateY(-2px);
}

.forgot-password-link {
    color: #f14040;
    text-decoration: none;
    font-weight: 500;
    transition: color 0.3s ease;
}
.forgot-password-link:hover {
    color: #d63434;
    text-decoration: underline;
}

/* Mobile Login Section */
.mobile-login-section h3 {
    margin-bottom: 20px;
    color: #333;
}
.otp-verification-section {
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid #eee;
}
#resend-otp-link {
    color: #007bff;
    text-decoration: none;
    transition: color 0.3s ease;
}
#resend-otp-link:hover {
    color: #0056b3;
    text-decoration: underline;
}
#back-to-email-login {
    background-color: #6c757d;
    border-color: #6c757d;
    color: #fff;
    transition: background-color 0.3s ease, transform 0.3s ease;
}
#back-to-email-login:hover {
    background-color: #5a6268;
    border-color: #5a6268;
    transform: translateY(-2px);
}

/* Error/Success Messages */
.error-message {
    background:#ffebeb;
    border:1px solid #ff0000;
    color:#ff0000;
    padding: 10px;
    margin-bottom: 20px;
    border-radius: 5px;
    animation: shake 0.5s;
}
.success-message {
    background:#ebffeb;
    border:1px solid #008000;
    color:#008000;
    padding: 10px;
    margin-bottom: 20px;
    border-radius: 5px;
    animation: fadeIn 0.8s;
}

@keyframes shake {
    0%, 100% { transform: translateX(0); }
    20%, 60% { transform: translateX(-5px); }
    40%, 80% { transform: translateX(5px); }
}

</style>

<script>
// Add this JavaScript to your assets/js/main.js

document.addEventListener('DOMContentLoaded', function() {
    // --- Login Form UI Toggles ---
    const mobileLoginBtn = document.getElementById('mobile-login-btn');
    const backToEmailLoginBtn = document.getElementById('back-to-email-login');
    const standardLoginForm = document.getElementById('standardLoginForm');
    const mobileLoginSection = document.querySelector('.mobile-login-section');
    const socialLoginButtons = document.querySelector('.social-login-buttons');
    const orSeparator = document.querySelector('.or-separator');

    if (mobileLoginBtn) {
        mobileLoginBtn.addEventListener('click', function() {
            standardLoginForm.style.display = 'none';
            socialLoginButtons.style.display = 'none';
            orSeparator.style.display = 'none';
            mobileLoginSection.style.display = 'block';
            mobileLoginSection.classList.add('fade-in'); // Add animation class
        });
    }

    if (backToEmailLoginBtn) {
        backToEmailLoginBtn.addEventListener('click', function() {
            standardLoginForm.style.display = 'block';
            socialLoginButtons.style.display = 'block';
            orSeparator.style.display = 'flex';
            mobileLoginSection.style.display = 'none';
            mobileLoginSection.classList.remove('fade-in');
        });
    }

    // --- Mobile OTP Logic (Twilio Integration) ---
    const sendOtpBtn = document.getElementById('send-otp-btn');
    const verifyOtpBtn = document.getElementById('verify-otp-btn');
    const resendOtpLink = document.getElementById('resend-otp-link');
    const mobileNumberInput = document.getElementById('mobile_number');
    const otpCodeInput = document.getElementById('otp_code');
    const otpVerificationSection = document.querySelector('.otp-verification-section');

    let currentOtp = ''; // Store OTP generated by server

    if (sendOtpBtn) {
        sendOtpBtn.addEventListener('click', function() {
            const mobileNumber = mobileNumberInput.value.trim();
            if (!mobileNumber) {
                alert('Please enter your mobile number.');
                return;
            }

            // Show loading spinner/disable button
            sendOtpBtn.textContent = 'Sending...';
            sendOtpBtn.disabled = true;

            // AJAX call to send OTP
            fetch('<?php echo BASE_URL; ?>send_otp.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `mobile_number=${encodeURIComponent(mobileNumber)}&csrf_token=<?php echo $csrf->getTokenValue(); ?>` // Pass CSRF token
            })
            .then(response => response.json())
            .then(data => {
                sendOtpBtn.textContent = 'Send OTP';
                sendOtpBtn.disabled = false;
                if (data.status === 'success') {
                    alert('OTP sent to your mobile number!');
                    otpVerificationSection.style.display = 'block';
                    // For security, currentOtp should ideally not be client-side.
                    // Instead, verification should happen server-side against a stored OTP.
                    // For this example, we'll simulate.
                    // currentOtp = data.otp; // In a real app, you wouldn't send OTP to client
                } else {
                    alert('Failed to send OTP: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error sending OTP:', error);
                sendOtpBtn.textContent = 'Send OTP';
                sendOtpBtn.disabled = false;
                alert('An error occurred while sending OTP.');
            });
        });
    }

    if (verifyOtpBtn) {
        verifyOtpBtn.addEventListener('click', function() {
            const mobileNumber = mobileNumberInput.value.trim();
            const otpCode = otpCodeInput.value.trim();

            if (!otpCode) {
                alert('Please enter the OTP.');
                return;
            }

            // Show loading spinner/disable button
            verifyOtpBtn.textContent = 'Verifying...';
            verifyOtpBtn.disabled = true;

            // AJAX call to verify OTP
            fetch('<?php echo BASE_URL; ?>verify_otp.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `mobile_number=${encodeURIComponent(mobileNumber)}&otp_code=${encodeURIComponent(otpCode)}&csrf_token=<?php echo $csrf->getTokenValue(); ?>`
            })
            .then(response => response.json())
            .then(data => {
                verifyOtpBtn.textContent = 'Verify OTP';
                verifyOtpBtn.disabled = false;
                if (data.status === 'success') {
                    alert('Mobile number verified successfully!');
                    // Redirect or log in the user
                    window.location.href = data.redirect;
                } else {
                    alert('OTP verification failed: ' + data.message);
                }
            })
            .catch(error => {
                console.error('Error verifying OTP:', error);
                verifyOtpBtn.textContent = 'Verify OTP';
                verifyOtpBtn.disabled = false;
                alert('An error occurred during OTP verification.');
            });
        });
    }

    if (resendOtpLink) {
        resendOtpLink.addEventListener('click', function(e) {
            e.preventDefault();
            // Trigger send OTP logic again
            sendOtpBtn.click();
        });
    }


    // --- Google Login Integration ---
    const googleLoginBtn = document.getElementById('google-login-btn');
    if (googleLoginBtn && typeof google !== 'undefined' && typeof google.accounts !== 'undefined') {
        google.accounts.id.initialize({
            client_id: '<?php echo $google_client_id; ?>', // Your Google Client ID from admin settings
            callback: handleGoogleCredentialResponse
        });
        google.accounts.id.renderButton(
            googleLoginBtn, // The button element
            { theme: "outline", size: "large", text: "signin_with", width: "100%" } // customization attributes
        );
        // If you want to use a custom button, you'd use google.accounts.id.prompt();
        // and trigger it on your custom button click.
    }

    function handleGoogleCredentialResponse(response) {
        // Decode the JWT token
        const idToken = response.credential;
        const decodedToken = parseJwt(idToken); // Helper function to parse JWT

        if (decodedToken) {
            const email = decodedToken.email;
            const googleId = decodedToken.sub; // Google User ID
            const name = decodedToken.name;

            // Send this data to your server for login/registration
            sendSocialLoginDataToServer(email, googleId, 'google', name);
        } else {
            alert('Failed to decode Google credential.');
        }
    }

    // Helper function to parse JWT
    function parseJwt (token) {
        try {
            return JSON.parse(atob(token.split('.')[1]));
        } catch (e) {
            return null;
        }
    }


    // --- Facebook Login Integration ---
    const facebookLoginBtn = document.getElementById('facebook-login-btn');
    if (facebookLoginBtn && typeof FB !== 'undefined') {
        window.fbAsyncInit = function() {
            FB.init({
                appId      : '<?php echo $facebook_app_id; ?>', // Your Facebook App ID from admin settings
                cookie     : true,
                xfbml      : true,
                version    : 'v18.0' // Use a recent API version
            });
            // Optional: Check login status on page load
            // FB.getLoginStatus(function(response) {
            //     statusChangeCallback(response);
            // });
        };

        facebookLoginBtn.addEventListener('click', function() {
            FB.login(function(response) {
                if (response.authResponse) {
                    FB.api('/me?fields=id,name,email', function(userResponse) {
                        if (userResponse.email) {
                            sendSocialLoginDataToServer(userResponse.email, userResponse.id, 'facebook', userResponse.name);
                        } else {
                            alert('Facebook login requires email permission.');
                        }
                    });
                } else {
                    console.log('User cancelled login or did not fully authorize.');
                }
            }, {scope: 'public_profile,email'}); // Request necessary permissions
        });
    }

    // --- Function to send social login data to server ---
    function sendSocialLoginDataToServer(email, socialId, provider, name) {
        fetch('<?php echo BASE_URL; ?>login.php', { // Send to login.php itself for processing
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `social_login_email=${encodeURIComponent(email)}&social_login_id=${encodeURIComponent(socialId)}&social_login_provider=${encodeURIComponent(provider)}&social_login_name=${encodeURIComponent(name)}&csrf_token=<?php echo $csrf->getTokenValue(); ?>`
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                window.location.href = data.redirect;
            } else {
                alert('Social login failed: ' + (data.message || 'Unknown error.'));
            }
        })
        .catch(error => {
            console.error('Error during social login server communication:', error);
            alert('An error occurred during social login.');
        });
    }
});
</script>
<?php require_once('footer.php'); ?>
