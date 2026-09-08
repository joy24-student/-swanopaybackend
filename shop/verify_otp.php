<?php
ob_start();
session_start();
require_once('admin/inc/config.php');
require_once('admin/inc/CSRF_Protect.php');
$csrf = new CSRF_Protect();

header('Content-Type: application/json');

// Check CSRF token
if (!$csrf->checkToken()) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token.']);
    exit;
}

$mobile_number = $_POST['mobile_number'] ?? '';
$otp_code = $_POST['otp_code'] ?? '';

if (empty($mobile_number) || empty($otp_code)) {
    echo json_encode(['status' => 'error', 'message' => 'Mobile number and OTP are required.']);
    exit;
}

// Verify OTP from session (or database if preferred for persistence)
if (isset($_SESSION['otp_mobile_number']) && $_SESSION['otp_mobile_number'] === $mobile_number &&
    isset($_SESSION['otp_code']) && $_SESSION['otp_code'] == $otp_code) {

    // Check OTP expiry (e.g., 5 minutes)
    if (isset($_SESSION['otp_timestamp']) && (time() - $_SESSION['otp_timestamp']) > 300) { // 300 seconds = 5 minutes
        echo json_encode(['status' => 'error', 'message' => 'OTP has expired. Please resend.']);
        unset($_SESSION['otp_code']); // Clear expired OTP
        exit;
    }

    // OTP is valid, log in or register the user
    try {
        // Check if user exists by mobile number
        $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_phone=? LIMIT 1");
        $statement->execute([$mobile_number]);
        $customer_exists = $statement->fetch(PDO::FETCH_ASSOC);

        if ($customer_exists) {
            // User exists, log them in
            $_SESSION['customer'] = $customer_exists;
            // Mark mobile as verified if not already
            if ($customer_exists['mobile_verified'] == 0) {
                $update_stmt = $pdo->prepare("UPDATE tbl_customer SET mobile_verified = 1 WHERE cust_id = ?");
                $update_stmt->execute([$customer_exists['cust_id']]);
            }
            
            // Load cart from database after successful login
            require_once('admin/inc/functions.php');
            if(function_exists('loadCartFromDatabase')) {
                loadCartFromDatabase($pdo, $customer_exists['cust_id']);
            } else {
                error_log("Error: loadCartFromDatabase function not found in admin/inc/functions.php during OTP login.");
            }
            
            unset($_SESSION['otp_code']); // Clear OTP after successful verification
            unset($_SESSION['otp_timestamp']);
            unset($_SESSION['otp_mobile_number']);

            echo json_encode(['status' => 'success', 'message' => 'Login successful.', 'redirect' => BASE_URL . 'dashboard.php']);
            exit;
        } else {
            // New user, register them with mobile number
            $token = md5(time() . $mobile_number); // Generate a token
            $cust_datetime = date('Y-m-d H:i:s');
            $cust_timestamp = time();

            $insert_statement = $pdo->prepare("INSERT INTO tbl_customer (
                cust_name, cust_email, cust_phone, cust_country, cust_address, cust_city, cust_state, cust_zip,
                cust_password, cust_token, cust_datetime, cust_timestamp, cust_status, mobile_verified,
                cust_cname, cust_b_name, cust_b_cname, cust_b_phone, cust_b_country, cust_b_address, cust_b_city, cust_b_state, cust_b_zip, cust_s_name, cust_s_cname, cust_s_phone, cust_s_country, cust_s_address, cust_s_city, cust_s_state, cust_s_zip
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            
            $insert_statement->execute(array(
                'New User', '', $mobile_number, 0, '', '', '', '', // Name, email can be empty initially
                '', $token, $cust_datetime, $cust_timestamp, 1, 1, // Status 1, mobile_verified 1
                '', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '' // Default empty/zero for other fields
            ));

            $new_customer_id = $pdo->lastInsertId();
            $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=?");
            $statement->execute([$new_customer_id]);
            $_SESSION['customer'] = $statement->fetch(PDO::FETCH_ASSOC);

            unset($_SESSION['otp_code']); // Clear OTP after successful verification
            unset($_SESSION['otp_timestamp']);
            unset($_SESSION['otp_mobile_number']);

            echo json_encode(['status' => 'success', 'message' => 'Registration successful.', 'redirect' => BASE_URL . 'dashboard.php']);
            exit;
        }
    } catch (PDOException $e) {
        error_log("OTP verification/login error: " . $e->getMessage());
        echo json_encode(['status' => 'error', 'message' => 'Database error during login/registration.']);
        exit;
    }

} else {
    echo json_encode(['status' => 'error', 'message' => 'Invalid OTP or mobile number.']);
    exit;
}
?>