<?php
use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

require 'vendor/autoload.php';

$message = '';
$message_type = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $smtp_host = $_POST['smtp_host'] ?? '';
    $smtp_username = $_POST['smtp_username'] ?? '';
    $smtp_password = $_POST['smtp_password'] ?? '';
    $smtp_port = $_POST['smtp_port'] ?? 587;
    $smtp_encryption = $_POST['smtp_encryption'] ?? 'tls';
    $from_email = $_POST['from_email'] ?? '';
    $from_name = $_POST['from_name'] ?? '';
    $to_email = $_POST['to_email'] ?? '';
    $subject = $_POST['subject'] ?? 'Test Email from PHPMailer';
    $body = $_POST['body'] ?? 'This is a test email sent from the manual tester.';

    if (empty($smtp_host) || empty($smtp_username) || empty($smtp_password) || empty($from_email) || empty($to_email)) {
        $message = 'Please fill in all required fields (Host, Username, Password, From Email, To Email).';
        $message_type = 'error';
    } else {
        $mail = new PHPMailer(true);

        try {
            $mail->isSMTP();
            $mail->Host = $smtp_host;
            $mail->SMTPAuth = true;
            $mail->Username = $smtp_username;
            $mail->Password = $smtp_password;
            
            if (strtolower($smtp_encryption) === 'ssl') {
                $mail->SMTPSecure = PHPMailer::ENCRYPTION_SMTPS;
            } elseif (strtolower($smtp_encryption) === 'tls') {
                $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
            } else {
                $mail->SMTPSecure = false;
            }
            $mail->Port = $smtp_port;
            
            // ... after setting $mail->SMTPSecure and $mail->Port ...

// It's crucial for the local server to ignore SSL certificate validation.
$mail->SMTPOptions = array(
    'ssl' => array(
        'verify_peer' => false,
        'verify_peer_name' => false,
        'allow_self_signed' => true
    )
);
            $mail->SMTPDebug = 2; // Enable verbose debug output
            $mail->Debugoutput = 'html';

            $mail->setFrom($from_email, $from_name);
            $mail->addAddress($to_email);

            $mail->isHTML(true);
            $mail->Subject = $subject;
            $mail->Body    = $body;
            $mail->AltBody = strip_tags($body);

            if ($mail->send()) {
                $message = 'Message has been sent successfully.';
                $message_type = 'success';
            }
        } catch (Exception $e) {
            $message = "Message could not be sent. Mailer Error: " . $mail->ErrorInfo;
            $message_type = 'error';
        }
    }
}
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>PHPMailer Manual Tester</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; font-weight: bold; }
        input[type="text"], input[type="email"], input[type="password"], textarea, select { width: 100%; padding: 8px; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px; }
        .btn { display: inline-block; background-color: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }
        .message { padding: 10px; margin-bottom: 15px; border-radius: 4px; }
        .success { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
    </style>
</head>
<body>
    <div class="container">
        <h2>PHPMailer Manual Tester</h2>
        <?php if ($message): ?>
            <div class="message <?php echo $message_type; ?>">
                <?php echo htmlspecialchars($message); ?>
            </div>
        <?php endif; ?>
        <form action="" method="post">
            <h4>SMTP Server Settings</h4>
            <div class="form-group">
                <label for="smtp_host">SMTP Host *</label>
                <input type="text" name="smtp_host" id="smtp_host" value="<?php echo htmlspecialchars($_POST['smtp_host'] ?? ''); ?>" required>
            </div>
            <div class="form-group">
                <label for="smtp_username">SMTP Username *</label>
                <input type="email" name="smtp_username" id="smtp_username" value="<?php echo htmlspecialchars($_POST['smtp_username'] ?? ''); ?>" required>
            </div>
            <div class="form-group">
                <label for="smtp_password">SMTP Password *</label>
                <input type="password" name="smtp_password" id="smtp_password" required>
            </div>
            <div class="form-group">
                <label for="smtp_port">Port *</label>
                <input type="text" name="smtp_port" id="smtp_port" value="<?php echo htmlspecialchars($_POST['smtp_port'] ?? '587'); ?>" required>
            </div>
            <div class="form-group">
                <label for="smtp_encryption">Encryption</label>
                <select name="smtp_encryption" id="smtp_encryption">
                    <option value="tls" <?php echo (($_POST['smtp_encryption'] ?? 'tls') === 'tls') ? 'selected' : ''; ?>>TLS</option>
                    <option value="ssl" <?php echo (($_POST['smtp_encryption'] ?? '') === 'ssl') ? 'selected' : ''; ?>>SSL</option>
                    <option value="none" <?php echo (($_POST['smtp_encryption'] ?? '') === 'none') ? 'selected' : ''; ?>>None</option>
                </select>
            </div>
            <hr>
            <h4>Email Details</h4>
            <div class="form-group">
                <label for="from_email">From Email *</label>
                <input type="email" name="from_email" id="from_email" value="<?php echo htmlspecialchars($_POST['from_email'] ?? ''); ?>" required>
            </div>
            <div class="form-group">
                <label for="from_name">From Name</label>
                <input type="text" name="from_name" id="from_name" value="<?php echo htmlspecialchars($_POST['from_name'] ?? ''); ?>">
            </div>
            <div class="form-group">
                <label for="to_email">To Email *</label>
                <input type="email" name="to_email" id="to_email" value="<?php echo htmlspecialchars($_POST['to_email'] ?? ''); ?>" required>
            </div>
            <div class="form-group">
                <label for="subject">Subject</label>
                <input type="text" name="subject" id="subject" value="<?php echo htmlspecialchars($_POST['subject'] ?? 'Test Email from PHPMailer'); ?>">
            </div>
            <div class="form-group">
                <label for="body">Body</label>
                <textarea name="body" id="body" rows="5"><?php echo htmlspecialchars($_POST['body'] ?? 'This is a test email sent from the manual tester.'); ?></textarea>
            </div>
            <button type="submit" class="btn">Send Test Email</button>
        </form>
        <hr>
        <h4>Debug Output</h4>
        <div id="debug-output">
            </div>
    </div>
</body>
</html>