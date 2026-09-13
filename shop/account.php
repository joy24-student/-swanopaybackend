<?php
// Hosted-store email/password authentication. Client-supplied social identities
// are never treated as proof of ownership.
if (!getenv('SHOP_RUNTIME_DIR')) { http_response_code(404); exit; }
session_start();
require_once __DIR__ . '/admin/inc/config.php';
require_once __DIR__ . '/admin/inc/CSRF_Protect.php';
$csrf = new CSRF_Protect();
$register = ($accountMode ?? '') === 'register';
$accountError = '';
$email = strtolower(trim((string)($_POST['cust_email'] ?? '')));
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    try {
        if (!$csrf->checkToken()) throw new RuntimeException('Your session expired. Refresh and try again.');
        $password=(string)($_POST['cust_password'] ?? '');
        if (!filter_var($email,FILTER_VALIDATE_EMAIL) || strlen($email)>100 || !$password || strlen($password)>72) throw new RuntimeException('Enter a valid email and password.');
        $key=hash('sha256','customer|' . ($register ? 'signup' : $email) . '|' . ($_SERVER['REMOTE_ADDR'] ?? ''));
        $stmt=$pdo->prepare("SELECT failures FROM shop_login_attempts WHERE attempt_key=? AND last_attempt > NOW() - INTERVAL '15 minutes'"); $stmt->execute([$key]);
        if((int)$stmt->fetchColumn()>=10) throw new RuntimeException('Too many attempts. Please try again in 15 minutes.');
        $pdo->prepare("INSERT INTO shop_login_attempts(attempt_key,failures) VALUES(?,1) ON CONFLICT(attempt_key) DO UPDATE SET failures=CASE WHEN shop_login_attempts.last_attempt < NOW() - INTERVAL '15 minutes' THEN 1 ELSE shop_login_attempts.failures+1 END,last_attempt=NOW()")->execute([$key]);
        if ($register) {
            if(strlen($password)<12 || $password !== ($_POST['cust_re_password'] ?? '')) throw new RuntimeException('Use matching passwords of at least 12 characters.');
            $fields=[];
            foreach(['cust_name'=>100,'cust_phone'=>50,'cust_address'=>500,'cust_city'=>100,'cust_state'=>100,'cust_zip'=>30] as $field=>$max) {
                $fields[$field]=trim(strip_tags((string)($_POST[$field] ?? '')));
                if(!$fields[$field] || strlen($fields[$field])>$max) throw new RuntimeException('Complete your name, phone and delivery address.');
            }
            $country=$pdo->query("SELECT country_id FROM tbl_country WHERE country_name='Bangladesh'")->fetchColumn();
            if(!$country) throw new RuntimeException('Delivery is temporarily unavailable.');
            $stmt=$pdo->prepare('INSERT INTO tbl_customer(cust_name,cust_email,cust_phone,cust_address,cust_city,cust_state,cust_zip,cust_country,cust_password,cust_datetime,cust_timestamp,cust_status,cust_b_name,cust_b_phone,cust_b_address,cust_b_city,cust_b_state,cust_b_zip,cust_b_country,cust_s_name,cust_s_phone,cust_s_address,cust_s_city,cust_s_state,cust_s_zip,cust_s_country) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING cust_id');
            $address=[$fields['cust_name'],$fields['cust_phone'],$fields['cust_address'],$fields['cust_city'],$fields['cust_state'],$fields['cust_zip'],$country];
            $stmt->execute(array_merge([$fields['cust_name'],$email,$fields['cust_phone'],$fields['cust_address'],$fields['cust_city'],$fields['cust_state'],$fields['cust_zip'],$country,password_hash($password,PASSWORD_BCRYPT,['cost'=>12]),date('Y-m-d H:i:s'),(string)time(),1],$address,$address));
        }
        $stmt=$pdo->prepare('SELECT * FROM tbl_customer WHERE lower(cust_email)=? AND cust_status=1 LIMIT 1'); $stmt->execute([$email]); $customer=$stmt->fetch();
        if(!$customer || !password_verify($password,$customer['cust_password'])) throw new RuntimeException('Email or password is incorrect.');
        if(!$register) $pdo->prepare('DELETE FROM shop_login_attempts WHERE attempt_key=?')->execute([$key]);
        session_regenerate_id(true);
        unset($customer['cust_password']);
        $_SESSION['customer']=$customer;
        $_SESSION['shop_merchant_id']=MERCHANT_ID;
        header('Location: ' . BASE_URL . (empty($_SESSION['cart_p_id']) ? 'dashboard.php' : 'checkout.php')); exit;
    } catch(Throwable $error) {
        $accountError=$error instanceof PDOException ? 'The account could not be created. If you already have an account, sign in.' : $error->getMessage();
    }
}
require_once __DIR__ . '/header.php';
?>
<main class="store-account">
  <div class="store-account-card">
    <p class="store-account-eyebrow">WELCOME TO OUR STORE</p>
    <h1><?= $register ? 'Create your account' : 'Welcome back' ?></h1>
    <p><?= $register ? 'Save your delivery details and follow your orders.' : 'Sign in to continue shopping and track your orders.' ?></p>
    <?php if($accountError): ?><div role="alert" class="alert alert-danger"><?= htmlspecialchars($accountError,ENT_QUOTES,'UTF-8') ?></div><?php endif; ?>
    <form method="post">
      <?php $csrf->echoInputField(); ?>
      <?php if($register): ?>
        <label>Full name<input name="cust_name" autocomplete="name" maxlength="100" required></label>
        <label>Phone number<input name="cust_phone" type="tel" autocomplete="tel" maxlength="50" required></label>
      <?php endif; ?>
      <label>Email address<input name="cust_email" type="email" autocomplete="username" maxlength="100" value="<?= htmlspecialchars($email,ENT_QUOTES,'UTF-8') ?>" required></label>
      <label>Password<input name="cust_password" type="password" autocomplete="<?= $register ? 'new-password' : 'current-password' ?>" <?= $register ? 'minlength="12"' : '' ?> maxlength="72" required></label>
      <?php if($register): ?>
        <label>Confirm password<input name="cust_re_password" type="password" autocomplete="new-password" minlength="12" maxlength="72" required></label>
        <h2>Delivery address</h2>
        <label>Street address<input name="cust_address" autocomplete="street-address" maxlength="500" required></label>
        <div class="store-account-row">
          <label>City<input name="cust_city" autocomplete="address-level2" maxlength="100" required></label>
          <label>District<input name="cust_state" autocomplete="address-level1" maxlength="100" required></label>
        </div>
        <label>Postcode<input name="cust_zip" autocomplete="postal-code" maxlength="30" required></label>
        <p>Delivery country: Bangladesh</p>
      <?php endif; ?>
      <button type="submit" class="btn btn-primary"><?= $register ? 'Create account' : 'Sign in' ?></button>
    </form>
    <p class="store-account-switch"><?= $register ? 'Already have an account?' : 'New here?' ?> <a href="<?= $register ? 'login.php' : 'registration.php' ?>"><?= $register ? 'Sign in' : 'Create an account' ?></a></p>
  </div>
</main>
<style>
.store-account{padding:48px 18px;background:#f4f6fb;min-height:65vh}.store-account-card{max-width:520px;margin:auto;padding:32px;background:#fff;border:1px solid #e2e8f0;border-radius:20px;box-shadow:0 12px 40px #0f172a08;color:#172554}.store-account-eyebrow{font-size:11px;font-weight:700;letter-spacing:2px;color:#4f46e5}.store-account h1{font-size:30px;line-height:1.2;font-weight:700;margin:12px 0}.store-account h2{font-size:20px}.store-account p{color:#64748b;line-height:1.6}.store-account label{display:block;margin:16px 0;font-weight:600;width:100%}.store-account input{display:block;width:100%;min-height:46px;font-size:16px;border:1px solid #cbd5e1;border-radius:8px;padding:10px;margin-top:6px}.store-account input:focus{outline:2px solid #818cf8;outline-offset:2px}.store-account .btn{min-height:48px;width:100%;border-radius:10px;background:#4f46e5;border:0;font-weight:700;margin-top:12px}.store-account-switch{text-align:center;margin-top:24px}.store-account-row{display:flex;gap:12px}.store-account-row>*{min-width:0}@media(max-width:400px){.store-account-card{padding:22px}.store-account{padding:28px 14px}}
</style>
<?php require_once __DIR__ . '/footer.php'; ?>
