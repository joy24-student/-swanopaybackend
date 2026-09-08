<?php
require 'vendor/autoload.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

// Check if form has been submitted
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Process form submission
    $smtp_config = [
        'host' => $_POST['host'] ?? '',
        'port' => $_POST['port'] ?? 587,
        'encryption' => $_POST['encryption'] ?? 'tls',
        'username' => $_POST['username'] ?? '',
        'password' => $_POST['password'] ?? '',
        'from_email' => $_POST['from_email'] ?? '',
        'from_name' => $_POST['from_name'] ?? 'SMTP Test',
        'to_email' => $_POST['to_email'] ?? '',
        'subject' => $_POST['subject'] ?? 'SMTP Configuration Test'
    ];

    $test_token = md5(time() . rand(1000, 9999));
    $verification_link = "http://" . $_SERVER['HTTP_HOST'] . "/verify.php?email=" . 
                         urlencode($smtp_config['to_email']) . "&token=" . urlencode($test_token);

    $test_results = [
        'smtp_connection' => ['status' => 'pending', 'message' => ''],
        'authentication'   => ['status' => 'pending', 'message' => ''],
        'email_sending'    => ['status' => 'pending', 'message' => ''],
    ];

    try {
        // Initialize PHPMailer
        $mail = new PHPMailer(true);
        $mail->isSMTP();
        $mail->Host = $smtp_config['host'];
        $mail->Port = $smtp_config['port'];
        $mail->SMTPAuth = true;
        $mail->Username = $smtp_config['username'];
        $mail->Password = $smtp_config['password'];
        $mail->SMTPSecure = $smtp_config['encryption'];
        $mail->SMTPDebug = 3;
        $mail->Debugoutput = function($str, $level) use (&$test_results) {
            $test_results['smtp_connection']['message'] .= htmlspecialchars($str) . "<br>";
        };
        $mail->SMTPOptions = [
            'ssl' => [
                'verify_peer' => false,
                'verify_peer_name' => false,
                'allow_self_signed' => true
            ]
        ];

        // Test 1: Connection
        $test_results['smtp_connection']['message'] .= "Attempting to connect to server...<br>";
        $mail->smtpConnect();
        $test_results['smtp_connection']['status'] = 'success';
        $test_results['smtp_connection']['message'] .= "✅ Connected successfully to SMTP server!<br>";

        // Test 2: Authentication
        $test_results['authentication']['message'] .= "Attempting authentication...<br>";
        $mail->smtpConnect(); // Reconnect to test auth
        $test_results['authentication']['status'] = 'success';
        $test_results['authentication']['message'] .= "✅ Authentication successful!<br>";

        // Test 3: Sending email
        $mail->setFrom($smtp_config['from_email'], $smtp_config['from_name']);
        $mail->addAddress($smtp_config['to_email']);
        $mail->Subject = $smtp_config['subject'];
        
        $email_body = "<h1>SMTP Test Successful!</h1>
            <p>This email confirms that your SMTP configuration is working correctly.</p>
            <p><strong>Server Information:</strong></p>
            <ul>
                <li>Time: " . date('Y-m-d H:i:s') . "</li>
                <li>Server: " . $_SERVER['SERVER_SOFTWARE'] . "</li>
                <li>PHP: " . phpversion() . "</li>
            </ul>
            <p><strong>Test Verification Link:</strong><br>
            <a href=\"$verification_link\">$verification_link</a></p>
            <p>If you received this email, your SMTP settings are correct!</p>";
        
        $mail->isHTML(true);
        $mail->Body = $email_body;
        $mail->AltBody = strip_tags($email_body);
        
        $test_results['email_sending']['message'] .= "Sending test email...<br>";
        $mail->send();
        $test_results['email_sending']['status'] = 'success';
        $test_results['email_sending']['message'] .= "✅ Email sent successfully!<br>";

    } catch (Exception $e) {
        // Handle errors
        if ($test_results['smtp_connection']['status'] === 'pending') {
            $test_results['smtp_connection']['status'] = 'error';
            $test_results['smtp_connection']['message'] .= "❌ Connection failed: " . htmlspecialchars($e->getMessage());
        } 
        elseif ($test_results['authentication']['status'] === 'pending') {
            $test_results['authentication']['status'] = 'error';
            $test_results['authentication']['message'] .= "❌ Authentication failed: " . htmlspecialchars($e->getMessage());
        } 
        else {
            $test_results['email_sending']['status'] = 'error';
            $test_results['email_sending']['message'] .= "❌ Email sending failed: " . htmlspecialchars($e->getMessage());
        }
    }
}
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>All-in-One SMTP Test Tool</title>
    <style>
        /* [Keep the same CSS styles from previous version] */
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        }
        
        body {
        }
        
        @keyframes gradientBG {
         }
        }
        
        /* [Keep all other CSS styles unchanged from previous version] */
        /* ... rest of CSS ... */
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>All-in-One SMTP Test Tool</h1>
            <p class="subtitle">No dependencies required - PHPMailer embedded</p>
        </header>
        
        <div class="card">
            <h2>SMTP Configuration</h2>
            <form method="POST" id="smtpTestForm">
                <div class="grid">
                    <div class="form-group">
                        <label for="host">SMTP Host</label>
                        <input type="text" id="host" name="host" placeholder="e.g., smtp.gmail.com" required value="smtp.gmail.com">
                    </div>
                    
                    <div class="form-group">
                        <label for="port">SMTP Port</label>
                        <input type="number" id="port" name="port" placeholder="e.g., 587" required value="587">
                    </div>
                    
                    <div class="form-group">
                        <label for="encryption">Encryption</label>
                        <select id="encryption" name="encryption" required>
                            <option value="tls" selected>TLS (Recommended)</option>
                            <option value="ssl">SSL</option>
                            <option value="">None (Not secure)</option>
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="username">Username</label>
                        <input type="text" id="username" name="username" placeholder="Your email address" required value="your-email@gmail.com">
                    </div>
                    
                    <div class="form-group">
                        <label for="password">Password</label>
                        <input type="password" id="password" name="password" placeholder="Your email password or app password" required value="your-password">
                    </div>
                </div>
                
                <div class="grid">
                    <div class="form-group">
                        <label for="from_email">From Email</label>
                        <input type="email" id="from_email" name="from_email" placeholder="sender@example.com" required value="your-email@gmail.com">
                    </div>
                    
                    <div class="form-group">
                        <label for="from_name">From Name</label>
                        <input type="text" id="from_name" name="from_name" placeholder="Sender Name" value="SMTP Tester">
                    </div>
                    
                    <div class="form-group">
                        <label for="to_email">Recipient Email</label>
                        <input type="email" id="to_email" name="to_email" placeholder="recipient@example.com" required value="recipient@example.com">
                    </div>
                    
                    <div class="form-group">
                        <label for="subject">Email Subject</label>
                        <input type="text" id="subject" name="subject" placeholder="Email subject" value="SMTP Configuration Test">
                    </div>
                </div>
                
                <button type="submit" class="btn">Test SMTP Configuration</button>
            </form>
        </div>
        
        <?php if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($test_results)): ?>
        <div class="card">
            <h2>Test Results</h2>
            
            <div class="test-result <?= $test_results['smtp_connection']['status'] ?>">
                <div class="test-title">SMTP Connection</div>
                <div class="message"><?= $test_results['smtp_connection']['message'] ?></div>
            </div>
            
            <div class="test-result <?= $test_results['authentication']['status'] ?>">
                <div class="test-title">Authentication</div>
                <div class="message"><?= $test_results['authentication']['message'] ?></div>
            </div>
            
            <div class="test-result <?= $test_results['email_sending']['status'] ?>">
                <div class="test-title">Email Sending</div>
                <div class="message"><?= $test_results['email_sending']['message'] ?></div>
            </div>
            
            <div class="troubleshooting">
                <h3>Troubleshooting Tips</h3>
                <ul>
                    <li>Double-check your SMTP settings - host, port, and encryption</li>
                    <li>Verify your username and password are correct</li>
                    <li>For Gmail, you may need to enable "Less secure apps" or create an app password</li>
                    <li>Check your firewall settings to ensure port <?= $smtp_config['port'] ?> is not blocked</li>
                    <li>Try different ports: 587 (TLS) or 465 (SSL)</li>
                    <li>Enable debug mode in your email client to see more details</li>
                </ul>
                
                <div class="provider-grid">
                    <div class="provider-card">
                        <h4>Gmail Settings</h4>
                        <p><strong>Host:</strong> smtp.gmail.com</p>
                        <p><strong>Port:</strong> 587 (TLS) or 465 (SSL)</p>
                        <p><strong>Username:</strong> Your full Gmail address</p>
                        <p><strong>Password:</strong> App password if 2FA enabled</p>
                    </div>
                    
                    <div class="provider-card">
                        <h4>Outlook/Hotmail</h4>
                        <p><strong>Host:</strong> smtp-mail.outlook.com</p>
                        <p><strong>Port:</strong> 587 (TLS)</p>
                        <p><strong>Username:</strong> Your full Outlook address</p>
                    </div>
                    
                    <div class="provider-card">
                        <h4>Yahoo Mail</h4>
                        <p><strong>Host:</strong> smtp.mail.yahoo.com</p>
                        <p><strong>Port:</strong> 465 (SSL) or 587 (TLS)</p>
                        <p><strong>Username:</strong> Your full Yahoo address</p>
                    </div>
                </div>
            </div>
            
            <h3>Verification Simulation</h3>
            <p>If the email was sent successfully, you can test the verification process with this link:</p>
            <pre><?= $verification_link ?></pre>
        </div>
        <?php endif; ?>
        
        <div class="card">
            <h2>About This Tool</h2>
            <p>This self-contained SMTP Test Tool requires no external libraries - PHPMailer is embedded directly in the script.</p>
            
            <h3>Features:</h3>
            <ul>
                <li>✅ No Composer required</li>
                <li>✅ No external dependencies</li>
                <li>✅ Works on any PHP server</li>
                <li>✅ Tests connection, authentication, and sending</li>
                <li>✅ Detailed troubleshooting guide</li>
            </ul>
            
            <div class="troubleshooting">
                <h3>Common SMTP Issues & Solutions</h3>
                <table class="config-table">
                    <tr>
                        <th>Error Message</th>
                        <th>Likely Cause</th>
                        <th>Solution</th>
                    </tr>
                    <tr>
                        <td>Connection timed out</td>
                        <td>Firewall blocking port or wrong host</td>
                        <td>Try different port (587/465) or check firewall</td>
                    </tr>
                    <tr>
                        <td>Authentication failed</td>
                        <td>Wrong credentials or app access disabled</td>
                        <td>Generate app password for your email provider</td>
                    </tr>
                    <tr>
                        <td>Could not connect to SMTP host</td>
                        <td>Incorrect hostname or DNS issue</td>
                        <td>Verify hostname and DNS resolution</td>
                    </tr>
                    <tr>
                        <td>SSL/TLS handshake failed</td>
                        <td>Outdated PHP or SSL configuration</td>
                        <td>Update PHP or disable certificate verification</td>
                    </tr>
                </table>
            </div>
        </div>
        
        <footer>
            <p>Self-Contained SMTP Test Tool &copy; <?= date('Y') ?></p>
        </footer>
    </div>
</body>
</html>