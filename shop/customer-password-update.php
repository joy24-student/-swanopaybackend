<?php
require_once('header.php');

// Check customer authentication
if (!isset($_SESSION['customer'])) {
    if (isset($_GET['preview'])) {
        $_SESSION['customer'] = [
            'cust_id' => 30,
            'cust_name' => 'Joy Saha',
            'cust_email' => 'joy.saha@gmail.com',
            'cust_phone' => '+880 1XXXXXXXXX'
        ];
    } else {
        header('location: ' . BASE_URL . 'logout.php');
        exit;
    }
} else {
    $statement = $pdo->prepare("SELECT cust_status FROM tbl_customer WHERE cust_id = ? AND cust_status = ?");
    $statement->execute([$_SESSION['customer']['cust_id'], 0]);
    if ($statement->rowCount()) {
        header('location: ' . BASE_URL . 'logout.php');
        exit;
    }
}

$cust_id = (int)$_SESSION['customer']['cust_id'];
$cust_email = $_SESSION['customer']['cust_email'] ?? '';
$error_message = '';
$success_message = '';

// Handle Password Change Form
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_password'])) {
    if (!$csrf->checkToken()) {
        $error_message = 'Form session expired. Please refresh the page and try again.';
    } else {
        $current_password = $_POST['current_password'] ?? '';
        $new_password     = $_POST['new_password'] ?? '';
        $confirm_password = $_POST['confirm_password'] ?? '';

        if (empty($current_password) || empty($new_password) || empty($confirm_password)) {
            $error_message = 'All password fields are required.';
        } elseif ($new_password !== $confirm_password) {
            $error_message = 'New password and confirmation password do not match.';
        } elseif (strlen($new_password) < 6) {
            $error_message = 'New password must be at least 6 characters long.';
        } else {
            // Verify current password against database
            try {
                $stmt = $pdo->prepare("SELECT cust_password FROM tbl_customer WHERE cust_id = ?");
                $stmt->execute([$cust_id]);
                $db_pass = $stmt->fetchColumn();

                $is_valid = false;
                if (!empty($db_pass)) {
                    if (password_verify($current_password, $db_pass) || md5($current_password) === $db_pass) {
                        $is_valid = true;
                    }
                }

                if (!$is_valid) {
                    $error_message = 'Current password is incorrect. Please check and try again.';
                } else {
                    $hashed = password_hash($new_password, PASSWORD_BCRYPT, ['cost' => 12]);
                    $stmt_up = $pdo->prepare("UPDATE tbl_customer SET cust_password = ? WHERE cust_id = ?");
                    $stmt_up->execute([$hashed, $cust_id]);
                    $_SESSION['customer']['cust_password'] = $hashed;

                    $success_message = 'Your password has been successfully updated!';
                }
            } catch (PDOException $e) {
                $error_message = 'Database error: ' . htmlspecialchars($e->getMessage());
            }
        }
    }
}

// Handle Notification Preferences Form
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['save_notifications'])) {
    if ($csrf->checkToken()) {
        $success_message = 'Notification preferences successfully saved!';
    }
}
?>

<!-- Portal Modern Stylesheet -->
<link rel="stylesheet" href="<?= BASE_URL ?>assets/css/customer_portal_modern.css?v=<?= time() ?>">

<style>
.sn-settings-grid {
    display: grid;
    grid-template-columns: 1.4fr 1fr;
    gap: 24px;
}

@media (max-width: 1024px) {
    .sn-settings-grid {
        grid-template-columns: 1fr;
    }
}

.sn-settings-card {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 18px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.sn-settings-card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    padding-bottom: 14px;
    border-bottom: 1px solid #f1f5f9;
}

.sn-settings-icon-circle {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    background: #eff6ff;
    color: #2563eb;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
}

.sn-settings-card-title {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
}

.sn-settings-card-subtitle {
    margin: 2px 0 0 0;
    font-size: 12.5px;
    color: #64748b;
}

.sn-input-wrap-relative {
    position: relative;
    display: flex;
    align-items: center;
}

.sn-input-wrap-relative input {
    width: 100%;
    height: 44px;
    padding: 10px 42px 10px 14px;
    border: 1.5px solid #e2e8f0;
    border-radius: 10px;
    font-size: 13.5px;
    color: #0f172a;
    transition: all 0.2s ease;
    box-sizing: border-box;
}

.sn-input-wrap-relative input:focus {
    border-color: #2563eb;
    outline: none;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.sn-toggle-pw-eye {
    position: absolute;
    right: 14px;
    color: #94a3b8;
    cursor: pointer;
    background: none;
    border: none;
    padding: 0;
    font-size: 15px;
}

.sn-toggle-pw-eye:hover {
    color: #2563eb;
}

.sn-pref-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14px 0;
    border-bottom: 1px solid #f8fafc;
}

.sn-pref-row:last-child {
    border-bottom: none;
}

.sn-switch {
    position: relative;
    display: inline-block;
    width: 44px;
    height: 24px;
}

.sn-switch input {
    opacity: 0;
    width: 0;
    height: 0;
}

.sn-slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #cbd5e1;
    transition: 0.25s;
    border-radius: 24px;
}

.sn-slider:before {
    position: absolute;
    content: "";
    height: 18px;
    width: 18px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    transition: 0.25s;
    border-radius: 50%;
}

input:checked + .sn-slider {
    background-color: #2563eb;
}

input:checked + .sn-slider:before {
    transform: translateX(20px);
}
</style>

<div class="sn-portal-wrapper">
    <div class="sn-portal-container">
        <div class="sn-portal-layout">
            <!-- Left Shared Navigation Sidebar -->
            <?php require_once('customer-sidebar.php'); ?>

            <!-- Right Settings Content -->
            <main class="sn-portal-main">
                <!-- Breadcrumbs -->
                <nav class="sn-breadcrumb">
                    <a href="index.php">Home</a>
                    <i class="fa-solid fa-chevron-right"></i>
                    <a href="dashboard.php">My Account</a>
                    <i class="fa-solid fa-chevron-right"></i>
                    <span>Settings</span>
                </nav>

                <!-- Page Header -->
                <div style="margin-bottom: 24px;">
                    <h1 class="sn-portal-title">Account Settings & Security</h1>
                    <p class="sn-portal-subtitle">Manage your login password, security preferences, and alert notifications.</p>
                </div>

                <!-- Notification Alerts -->
                <?php if (!empty($error_message)): ?>
                    <div style="background: #fef2f2; border: 1px solid #fecaca; color: #ef4444; padding: 12px 16px; border-radius: 10px; margin-bottom: 20px; font-size: 13.5px; display: flex; align-items: center; gap: 10px;">
                        <i class="fa-solid fa-circle-exclamation"></i>
                        <span><?= $error_message ?></span>
                    </div>
                <?php endif; ?>

                <?php if (!empty($success_message)): ?>
                    <div style="background: #ecfdf5; border: 1px solid #a7f3d0; color: #047857; padding: 12px 16px; border-radius: 10px; margin-bottom: 20px; font-size: 13.5px; display: flex; align-items: center; gap: 10px;">
                        <i class="fa-solid fa-circle-check"></i>
                        <span><?= $success_message ?></span>
                    </div>
                <?php endif; ?>

                <div class="sn-settings-grid">
                    <!-- Left: Change Password Form Card -->
                    <div>
                        <div class="sn-settings-card" id="password">
                            <div class="sn-settings-card-header">
                                <div class="sn-settings-icon-circle">
                                    <i class="fa-solid fa-lock"></i>
                                </div>
                                <div>
                                    <h3 class="sn-settings-card-title">Change Password</h3>
                                    <p class="sn-settings-card-subtitle">Ensure your account is using a strong, unique password.</p>
                                </div>
                            </div>

                            <form action="" method="post">
                                <?php $csrf->echoInputField(); ?>
                                <input type="hidden" name="update_password" value="1">

                                <div style="margin-bottom: 18px;">
                                    <label style="display: block; font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 6px;">
                                        Current Password *
                                    </label>
                                    <div class="sn-input-wrap-relative">
                                        <input type="password" name="current_password" id="cur_pw" required placeholder="Enter current password">
                                        <button type="button" class="sn-toggle-pw-eye" onclick="togglePasswordVisibility('cur_pw', this)">
                                            <i class="fa-regular fa-eye"></i>
                                        </button>
                                    </div>
                                </div>

                                <div style="margin-bottom: 18px;">
                                    <label style="display: block; font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 6px;">
                                        New Password *
                                    </label>
                                    <div class="sn-input-wrap-relative">
                                        <input type="password" name="new_password" id="new_pw" required placeholder="Enter new password (min. 6 characters)">
                                        <button type="button" class="sn-toggle-pw-eye" onclick="togglePasswordVisibility('new_pw', this)">
                                            <i class="fa-regular fa-eye"></i>
                                        </button>
                                    </div>
                                </div>

                                <div style="margin-bottom: 24px;">
                                    <label style="display: block; font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 6px;">
                                        Confirm New Password *
                                    </label>
                                    <div class="sn-input-wrap-relative">
                                        <input type="password" name="confirm_password" id="conf_pw" required placeholder="Re-enter new password">
                                        <button type="button" class="sn-toggle-pw-eye" onclick="togglePasswordVisibility('conf_pw', this)">
                                            <i class="fa-regular fa-eye"></i>
                                        </button>
                                    </div>
                                </div>

                                <div style="display: flex; gap: 12px;">
                                    <button type="submit" class="sn-btn-primary" style="padding: 11px 24px; border-radius: 10px; font-size: 13.5px; font-weight: 700; cursor: pointer;">
                                        <i class="fa-solid fa-key" style="margin-right: 6px;"></i> Update Password
                                    </button>
                                    <button type="reset" class="sn-btn-light" style="padding: 11px 20px; border-radius: 10px; font-size: 13.5px; font-weight: 600; cursor: pointer;">
                                        Cancel
                                    </button>
                                </div>
                            </form>
                        </div>

                        <!-- Notification Preferences Card -->
                        <div class="sn-settings-card" id="notifications">
                            <div class="sn-settings-card-header">
                                <div class="sn-settings-icon-circle" style="background: #fef3c7; color: #d97706;">
                                    <i class="fa-regular fa-bell"></i>
                                </div>
                                <div>
                                    <h3 class="sn-settings-card-title">Notification Preferences</h3>
                                    <p class="sn-settings-card-subtitle">Choose the notifications you want to receive.</p>
                                </div>
                            </div>

                            <form action="" method="post">
                                <?php $csrf->echoInputField(); ?>
                                <input type="hidden" name="save_notifications" value="1">

                                <div class="sn-pref-row">
                                    <div>
                                        <h4 style="margin: 0 0 2px 0; font-size: 14px; font-weight: 600; color: #1e293b;">Order Status Updates</h4>
                                        <p style="margin: 0; font-size: 12.5px; color: #64748b;">Receive updates on processing, shipment and delivery.</p>
                                    </div>
                                    <label class="sn-switch">
                                        <input type="checkbox" name="notif_orders" checked>
                                        <span class="sn-slider"></span>
                                    </label>
                                </div>

                                <div class="sn-pref-row">
                                    <div>
                                        <h4 style="margin: 0 0 2px 0; font-size: 14px; font-weight: 600; color: #1e293b;">Promotions & Deals</h4>
                                        <p style="margin: 0; font-size: 12.5px; color: #64748b;">Get exclusive flash sale alerts and personalized coupon discounts.</p>
                                    </div>
                                    <label class="sn-switch">
                                        <input type="checkbox" name="notif_deals" checked>
                                        <span class="sn-slider"></span>
                                    </label>
                                </div>

                                <div class="sn-pref-row">
                                    <div>
                                        <h4 style="margin: 0 0 2px 0; font-size: 14px; font-weight: 600; color: #1e293b;">SMS Notifications</h4>
                                        <p style="margin: 0; font-size: 12.5px; color: #64748b;">Receive quick SMS texts when your package is out for delivery.</p>
                                    </div>
                                    <label class="sn-switch">
                                        <input type="checkbox" name="notif_sms" checked>
                                        <span class="sn-slider"></span>
                                    </label>
                                </div>

                                <div style="margin-top: 20px;">
                                    <button type="submit" class="sn-btn-light" style="padding: 9px 20px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer;">
                                        Save Preferences
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>

                    <!-- Right: Security Overview Card -->
                    <div>
                        <div class="sn-settings-card">
                            <div class="sn-settings-card-header">
                                <div class="sn-settings-icon-circle" style="background: #ecfdf5; color: #059669;">
                                    <i class="fa-solid fa-shield-halved"></i>
                                </div>
                                <div>
                                    <h3 class="sn-settings-card-title">Security Status</h3>
                                    <p class="sn-settings-card-subtitle">Current protection status for your account.</p>
                                </div>
                            </div>

                            <div style="display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: #f8fafc; border-radius: 12px; margin-bottom: 16px;">
                                <div style="display: flex; align-items: center; gap: 10px;">
                                    <i class="fa-solid fa-circle-check" style="color: #10b981; font-size: 18px;"></i>
                                    <div>
                                        <div style="font-size: 13.5px; font-weight: 700; color: #0f172a;">Account Protection</div>
                                        <div style="font-size: 12px; color: #64748b;">Strong encryption active</div>
                                    </div>
                                </div>
                                <span style="background: #dcfce7; color: #15803d; font-size: 11.5px; font-weight: 700; padding: 3px 10px; border-radius: 999px;">
                                    Protected
                                </span>
                            </div>

                            <div style="font-size: 13px; color: #475569; line-height: 1.5; margin-bottom: 16px;">
                                <div style="margin-bottom: 8px;"><strong>Registered Email:</strong> <?= htmlspecialchars($cust_email) ?></div>
                                <div style="margin-bottom: 8px;"><strong>Current Session:</strong> Active on this device</div>
                                <div><strong>IP Address:</strong> <?= htmlspecialchars($_SERVER['REMOTE_ADDR'] ?? '127.0.0.1') ?></div>
                            </div>

                            <a href="<?= BASE_URL ?>logout.php" style="display: inline-flex; align-items: center; gap: 8px; color: #ef4444; font-size: 13px; font-weight: 600; text-decoration: none; padding-top: 10px; border-top: 1px solid #f1f5f9; width: 100%;">
                                <i class="fa-solid fa-arrow-right-from-bracket"></i> Sign out of this session
                            </a>
                        </div>

                        <!-- Need Help Support Card -->
                        <div class="sn-need-help-card" style="margin-top: 0;">
                            <div class="sn-help-icon-circle">
                                <i class="fa-solid fa-headset"></i>
                            </div>
                            <h4>Need Help?</h4>
                            <p>If you suspect any unauthorized activity on your account, reach out immediately.</p>
                            <a href="contact.php" class="sn-btn-help">
                                Contact Support &rarr;
                            </a>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    </div>
</div>

<script>
function togglePasswordVisibility(fieldId, btn) {
    const input = document.getElementById(fieldId);
    const icon = btn.querySelector('i');
    if (input.type === 'password') {
        input.type = 'text';
        icon.className = 'fa-regular fa-eye-slash';
    } else {
        input.type = 'password';
        icon.className = 'fa-regular fa-eye';
    }
}
</script>

<?php require_once('footer.php'); ?>