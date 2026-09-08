<?php
ob_start();
session_start();
require_once('admin/inc/config.php');
require_once('admin/inc/CSRF_Protect.php');
$csrf = new CSRF_Protect();

// Include Twilio PHP Library (install via Composer: composer require twilio/sdk)
// Or download from https://github.com/twilio/twilio-php/releases
require __DIR__ . '/vendor/autoload.php'; // Adjust path if not using Composer
use Twilio\Rest\Client;

header('Content-Type: application/json');

// Check CSRF token
if (!$csrf->checkToken()) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token.']);
    exit;
}

// Fetch Twilio settings from database
$statement = $pdo->prepare("SELECT twilio_account_sid, twilio_auth_token, twilio_phone_number FROM tbl_settings WHERE id=1");
$statement->execute();
$twilio_settings = $statement->fetch(PDO::FETCH_ASSOC);

$account_sid = $twilio_settings['twilio_account_sid'] ?? '';
$auth_token = $twilio_settings['twilio_auth_token'] ?? '';
$twilio_number = $twilio_settings['twilio_phone_number'] ?? '';

if (empty($account_sid) || empty($auth_token) || empty($twilio_number)) {
    echo json_encode(['status' => 'error', 'message' => 'Twilio API keys are not configured in admin settings.']);
    exit;
}

$mobile_number = $_POST['mobile_number'] ?? '';

if (empty($mobile_number)) {
    echo json_encode(['status' => 'error', 'message' => 'Mobile number is required.']);
    exit;
}

// Generate OTP
$otp = rand(100000, 999999); // 6-digit OTP

try {
    $client = new Client($account_sid, $auth_token);

    // Send the SMS
    $client->messages->create(
        $mobile_number, // To
        [
            'from' => $twilio_number, // From a valid Twilio number
            'body' => "Your OTP for login is: {$otp}. Do not share this code."
        ]
    );

    // Store OTP in session or database for verification
    $_SESSION['otp_mobile_number'] = $mobile_number;
    $_SESSION['otp_code'] = $otp;
    $_SESSION['otp_timestamp'] = time(); // Store timestamp for expiry

    echo json_encode(['status' => 'success', 'message' => 'OTP sent successfully.']);

} catch (Exception $e) {
    error_log("Twilio SMS Error: " . $e->getMessage());
    echo json_encode(['status' => 'error', 'message' => 'Failed to send OTP. Please try again.']);
}
?>