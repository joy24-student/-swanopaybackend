<?php require_once('header.php'); ?>

<?php
// Check if customer is logged in, redirect if not
if (!isset($_SESSION['customer'])) {
    header('location: login.php');
    exit;
}

// Ensure cart is not empty, redirect if empty
if (!isset($_SESSION['cart_p_id']) || empty($_SESSION['cart_p_id'])) {
    header('location: cart.php');
    exit;
}

// Fetch banner image and enabled payment methods from settings
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);
$banner_checkout = '';
$payment_methods_string = 'PayPal,Bank Deposit,Cash on Delivery,SSLCommerz'; // Default
$payment_methods_string = 'SwapnoPay,Cash on Delivery,Bank Deposit,PayPal,SSLCommerz'; // Default
foreach ($result as $row) {
    $banner_checkout = $row['banner_checkout'];
    $payment_methods_string = $row['payment_methods'] ?? $payment_methods_string;
}
$enabled_methods = explode(',', $payment_methods_string);
$enabled_methods = array_map('trim', explode(',', $payment_methods_string));
// Hosted stores offer only configured payment methods.
if ($runtimeRoot) $enabled_methods = (int)($settings['cod_enabled'] ?? 0) ? ['Cash on Delivery'] : [];
if (empty($_SESSION['checkout_token'])) $_SESSION['checkout_token'] = bin2hex(random_bytes(32));


// --- Calculate Cart Total Price ---
$table_total_price = 0;
// Re-index cart session arrays for easier iteration
$arr_cart_p_id = array_values($_SESSION['cart_p_id']);
$arr_cart_size_id = array_values($_SESSION['cart_size_id']);
$arr_cart_size_name = array_values($_SESSION['cart_size_name']);
$arr_cart_color_id = array_values($_SESSION['cart_color_id']);
$arr_cart_color_name = array_values($_SESSION['cart_color_name']);
$arr_cart_p_qty = array_values($_SESSION['cart_p_qty']);
$arr_cart_p_current_price = array_values($_SESSION['cart_p_current_price']);
$arr_cart_p_name = array_values($_SESSION['cart_p_name']);
$arr_cart_p_featured_photo = array_values($_SESSION['cart_p_featured_photo']);

for ($i = 0; $i < count($arr_cart_p_id); $i++) {
    $row_total_price = $arr_cart_p_current_price[$i] * $arr_cart_p_qty[$i];
    $table_total_price += $row_total_price;
}

// --- Calculate Shipping Cost ---
$shipping_cost = 0;
$statement = $pdo->prepare("SELECT amount FROM tbl_shipping_cost WHERE country_id=?");
$statement->execute(array($_SESSION['customer']['cust_country'])); // Assuming cust_country is the shipping country ID
$total_shipping_rows = $statement->rowCount();

if ($total_shipping_rows) {
    $result = $statement->fetch(PDO::FETCH_ASSOC);
    $shipping_cost = $result['amount'];
} else {
    // Fallback to general shipping cost if country-specific not found
    $statement = $pdo->prepare("SELECT amount FROM tbl_shipping_cost_all WHERE sca_id=1");
    $statement->execute();
    $result = $statement->fetch(PDO::FETCH_ASSOC);
    $shipping_cost = $result['amount'];
}


// --- Coupon Processing ---
$coupon_discount = 0;
$coupon_error = '';
$coupon_code = $_SESSION['coupon']['code'] ?? ''; // Initialize with existing session coupon

if (isset($_POST['apply_coupon'])) {
    $new_coupon_code = strip_tags($_POST['coupon_code']);

    if (!empty($new_coupon_code)) {
        $today = date('Y-m-d');

        $statement = $pdo->prepare("SELECT * FROM tbl_coupon
                                       WHERE coupon_code = ?
                                       AND status = 'active'
                                       AND start_date <= ?
                                       AND end_date >= ?");
        $statement->execute([$new_coupon_code, $today, $today]);
        $coupon = $statement->fetch(PDO::FETCH_ASSOC);

        if ($coupon) {
            // Check usage limit
            if ($coupon['usage_limit'] > 0 && $coupon['used_count'] >= $coupon['usage_limit']) {
                $coupon_error = "This coupon has reached its usage limit.";
                unset($_SESSION['coupon']); // Clear invalid coupon from session
            }
            // Check minimum order amount
            elseif ($coupon['minimum_order'] > 0 && $table_total_price < $coupon['minimum_order']) {
                $coupon_error = "Minimum order amount of " . CURRENCY_SYMBOL . number_format($coupon['minimum_order'], 2) . " required for this coupon.";
                unset($_SESSION['coupon']); // Clear invalid coupon from session
            } else {
                // Calculate discount
                if ($coupon['discount_type'] == 'percentage') {
                    $coupon_discount = ($table_total_price * $coupon['discount_value']) / 100;
                } else {
                    $coupon_discount = $coupon['discount_value'];
                }

                // Ensure discount doesn't exceed product total price
                if ($coupon_discount > $table_total_price) {
                    $coupon_discount = $table_total_price;
                }

                // Store in session
                $_SESSION['coupon'] = [
                    'code' => $new_coupon_code,
                    'discount' => $coupon_discount,
                    'coupon_id' => $coupon['coupon_id'] // Store coupon ID for updating used_count later
                ];
                $coupon_code = $new_coupon_code; // Update coupon_code for display
            }
        } else {
            $coupon_error = "Invalid or expired coupon code.";
            unset($_SESSION['coupon']); // Clear invalid coupon from session
        }
    } else {
        $coupon_error = "Please enter a coupon code.";
        unset($_SESSION['coupon']); // Clear coupon if input is empty
    }
} elseif (isset($_SESSION['coupon'])) {
    // If no new coupon applied, but one exists in session, re-calculate based on current cart
    // This is crucial if cart items change after coupon was initially applied
    $stored_coupon_code = $_SESSION['coupon']['code'];
    $today = date('Y-m-d');

    $statement = $pdo->prepare("SELECT * FROM tbl_coupon
                                   WHERE coupon_code = ?
                                   AND status = 'active'
                                   AND start_date <= ?
                                   AND end_date >= ?");
    $statement->execute([$stored_coupon_code, $today, $today]);
    $coupon = $statement->fetch(PDO::FETCH_ASSOC);

    if ($coupon) {
        if ($coupon['usage_limit'] > 0 && $coupon['used_count'] >= $coupon['usage_limit']) {
            $coupon_error = "The previously applied coupon has reached its usage limit.";
            unset($_SESSION['coupon']);
            $coupon_discount = 0;
        } elseif ($coupon['minimum_order'] > 0 && $table_total_price < $coupon['minimum_order']) {
            $coupon_error = "The previously applied coupon requires a minimum order of " . CURRENCY_SYMBOL . number_format($coupon['minimum_order'], 2) . ".";
            unset($_SESSION['coupon']);
            $coupon_discount = 0;
        } else {
            if ($coupon['discount_type'] == 'percentage') {
                $coupon_discount = ($table_total_price * $coupon['discount_value']) / 100;
            } else {
                $coupon_discount = $coupon['discount_value'];
            }
            if ($coupon_discount > $table_total_price) {
                $coupon_discount = $table_total_price;
            }
            $_SESSION['coupon']['discount'] = $coupon_discount; // Update discount in session if total changed
        }
    } else {
        $coupon_error = "The previously applied coupon is no longer valid.";
        unset($_SESSION['coupon']);
        $coupon_discount = 0;
    }
}


// --- Final Total Calculation ---
$final_total = ($table_total_price + $shipping_cost) - $coupon_discount;
if ($final_total < 0) $final_total = 0; // Ensure total doesn't go negative


// --- Prepare Customer Address Data for Payment Processing ---
// These variables will be passed to payment gateway scripts via hidden fields
// or session and then inserted into tbl_payment
$cust_b_name = $_SESSION['customer']['cust_b_name'];
$cust_b_cname = $_SESSION['customer']['cust_b_cname'];
$cust_b_phone = $_SESSION['customer']['cust_b_phone'];
$cust_b_country_id = $_SESSION['customer']['cust_b_country'];
$cust_b_address = $_SESSION['customer']['cust_b_address'];
$cust_b_city = $_SESSION['customer']['cust_b_city'];
$cust_b_state = $_SESSION['customer']['cust_b_state'];
$cust_b_zip = $_SESSION['customer']['cust_b_zip'];

$cust_s_name = $_SESSION['customer']['cust_s_name'];
$cust_s_cname = $_SESSION['customer']['cust_s_cname'];
$cust_s_phone = $_SESSION['customer']['cust_s_phone'];
$cust_s_country_id = $_SESSION['customer']['cust_s_country'];
$cust_s_address = $_SESSION['customer']['cust_s_address'];
$cust_s_city = $_SESSION['customer']['cust_s_city'];
$cust_s_state = $_SESSION['customer']['cust_s_state'];
$cust_s_zip = $_SESSION['customer']['cust_s_zip'];

// Store these in session so payment scripts can access them directly
$_SESSION['billing_address_details'] = [
    'name' => $cust_b_name,
    'cname' => $cust_b_cname,
    'phone' => $cust_b_phone,
    'country_id' => $cust_b_country_id, // Store ID, fetch name in payment script
    'address' => $cust_b_address,
    'city' => $cust_b_city,
    'state' => $cust_b_state,
    'zip' => $cust_b_zip,
];

$_SESSION['shipping_address_details'] = [
    'name' => $cust_s_name,
    'cname' => $cust_s_cname,
    'phone' => $cust_s_phone,
    'country_id' => $cust_s_country_id, // Store ID, fetch name in payment script
    'address' => $cust_s_address,
    'city' => $cust_s_city,
    'state' => $cust_s_state,
    'zip' => $cust_s_zip,
];

// Also store final total and other necessary payment details in session
// This is critical because payment gateways might redirect, and session data is persistent
$_SESSION['payment_data'] = [
    'customer_id' => $_SESSION['customer']['cust_id'],
    'customer_name' => $_SESSION['customer']['cust_name'],
    'customer_email' => $_SESSION['customer']['cust_email'],
    'paid_amount' => $table_total_price,
    'shipping_cost' => $shipping_cost,
    'coupon_code' => $_SESSION['coupon']['code'] ?? '',
    'coupon_discount' => $_SESSION['coupon']['discount'] ?? 0,
    'overall_total' => $final_total,
    'cart_p_id' => $_SESSION['cart_p_id'],
    'cart_size_id' => $_SESSION['cart_size_id'],
    'cart_size_name' => $_SESSION['cart_size_name'],
    'cart_color_id' => $_SESSION['cart_color_id'],
    'cart_color_name' => $_SESSION['cart_color_name'],
    'cart_p_qty' => $_SESSION['cart_p_qty'],
    'cart_p_current_price' => $_SESSION['cart_p_current_price'],
    'cart_p_name' => $_SESSION['cart_p_name'],
    'cart_p_featured_photo' => $_SESSION['cart_p_featured_photo'],
    'coupon_id' => $_SESSION['coupon']['coupon_id'] ?? null // Pass coupon ID for usage count update
];

?>

<div class="page-banner" style="background-image: url(assets/uploads/<?php echo htmlspecialchars($banner_checkout); ?>)">
    <div class="overlay"></div>
    <div class="page-banner-inner">
        <h1><?php echo LANG_VALUE_22; ?></h1>
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
            <div class="col-md-12">

                <h3 class="special"><?php echo LANG_VALUE_26; ?></h3>
                <div class="cart">
                    <table class="table table-responsive table-hover table-bordered">
                        <tr>
                            <th><?php echo '#' ?></th>
                            <th><?php echo LANG_VALUE_8; ?></th>
                            <th><?php echo LANG_VALUE_47; ?></th>
                            <th><?php echo LANG_VALUE_157; ?></th>
                            <th><?php echo LANG_VALUE_158; ?></th>
                            <th><?php echo LANG_VALUE_159; ?></th>
                            <th><?php echo LANG_VALUE_55; ?></th>
                            <th class="text-right"><?php echo LANG_VALUE_82; ?></th>
                        </tr>
                        <?php for ($i = 0; $i < count($arr_cart_p_id); $i++): ?>
                            <tr>
                                <td><?php echo ($i + 1); ?></td>
                                <td>
                                    <img src="assets/uploads/<?php echo htmlspecialchars($arr_cart_p_featured_photo[$i]); ?>" alt="<?php echo htmlspecialchars($arr_cart_p_name[$i]); ?>">
                                </td>
                                <td><?php echo htmlspecialchars($arr_cart_p_name[$i]); ?></td>
                                <td><?php echo htmlspecialchars($arr_cart_size_name[$i]); ?></td>
                                <td><?php echo htmlspecialchars($arr_cart_color_name[$i]); ?></td>
                                <td><?php echo LANG_VALUE_1 . number_format($arr_cart_p_current_price[$i], 2); ?></td>
                                <td><?php echo $arr_cart_p_qty[$i]; ?></td>
                                <td class="text-right">
                                    <?php echo LANG_VALUE_1 . number_format($arr_cart_p_current_price[$i] * $arr_cart_p_qty[$i], 2); ?>
                                </td>
                            </tr>
                        <?php endfor; ?>
                        <tr>
                            <th colspan="7" class="total-text"><?php echo LANG_VALUE_81; ?></th>
                            <th class="total-amount"><?php echo LANG_VALUE_1 . number_format($table_total_price, 2); ?></th>
                        </tr>
                        <tr>
                            <td colspan="7" class="total-text"><?php echo LANG_VALUE_84; ?></td>
                            <td class="total-amount"><?php echo LANG_VALUE_1 . number_format($shipping_cost, 2); ?></td>
                        </tr>

                        <?php if ($coupon_discount > 0): ?>
                            <tr>
                                <td colspan="7" class="total-text">Coupon Discount (<?php echo htmlspecialchars($coupon_code); ?>)</td>
                                <td class="total-amount">-<?php echo LANG_VALUE_1 . number_format($coupon_discount, 2); ?></td>
                            </tr>
                        <?php endif; ?>
                        <tr>
                            <th colspan="7" class="total-text"><?php echo LANG_VALUE_82; ?></th>
                            <th class="total-amount">
                                <?php echo LANG_VALUE_1 . number_format($final_total, 2); ?>
                            </th>
                        </tr>
                    </table>
                </div>


                <div class="billing-address">
                    <div class="row">
                        <div class="col-md-6">
                            <h3 class="special"><?php echo LANG_VALUE_161; ?></h3>
                            <table class="table table-responsive table-bordered table-hover table-striped bill-address">
                                <tr>
                                    <td><?php echo LANG_VALUE_102; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_name); ?></p></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_103; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_cname); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_104; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_phone); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_106; ?></td>
                                    <td>
                                        <?php
                                        // Fetch billing country name
                                        $statement = $pdo->prepare("SELECT country_name FROM tbl_country WHERE country_id=?");
                                        $statement->execute(array($cust_b_country_id));
                                        $result = $statement->fetch(PDO::FETCH_ASSOC);
                                        echo htmlspecialchars($result['country_name'] ?? 'N/A');
                                        ?>
                                    </td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_105; ?></td>
                                    <td><?php echo nl2br(htmlspecialchars($cust_b_address)); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_107; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_city); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_108; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_state); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_109; ?></td>
                                    <td><?php echo htmlspecialchars($cust_b_zip); ?></td>
                                </tr>
                            </table>
                        </div>
                        <div class="col-md-6">
                            <h3 class="special"><?php echo LANG_VALUE_162; ?></h3>
                            <table class="table table-responsive table-bordered table-hover table-striped bill-address">
                                <tr>
                                    <td><?php echo LANG_VALUE_102; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_name); ?></p></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_103; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_cname); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_104; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_phone); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_106; ?></td>
                                    <td>
                                        <?php
                                        // Fetch shipping country name
                                        $statement = $pdo->prepare("SELECT country_name FROM tbl_country WHERE country_id=?");
                                        $statement->execute(array($cust_s_country_id));
                                        $result = $statement->fetch(PDO::FETCH_ASSOC);
                                        echo htmlspecialchars($result['country_name'] ?? 'N/A');
                                        ?>
                                    </td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_105; ?></td>
                                    <td><?php echo nl2br(htmlspecialchars($cust_s_address)); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_107; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_city); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_108; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_state); ?></td>
                                </tr>
                                <tr>
                                    <td><?php echo LANG_VALUE_109; ?></td>
                                    <td><?php echo htmlspecialchars($cust_s_zip); ?></td>
                                </tr>
                            </table>
                        </div>
                    </div>
                </div>


                <div class="cart-buttons">
                    <ul>
                        <li><a href="cart.php" class="btn btn-primary"><?php echo LANG_VALUE_21; ?></a></li>
                    </ul>
                </div>

                <div class="clear"></div>
                <h3 class="special"><?php echo LANG_VALUE_33; ?></h3>
                <div class="row">

                    <?php
                    $checkout_access = 1;
                    if (
                        ($_SESSION['customer']['cust_b_name'] == '') ||
                        ($_SESSION['customer']['cust_b_cname'] == '') ||
                        ($_SESSION['customer']['cust_b_phone'] == '') ||
                        ($_SESSION['customer']['cust_b_country'] == '') ||
                        ($_SESSION['customer']['cust_b_address'] == '') ||
                        ($_SESSION['customer']['cust_b_city'] == '') ||
                        ($_SESSION['customer']['cust_b_state'] == '') ||
                        ($_SESSION['customer']['cust_b_zip'] == '') ||
                        ($_SESSION['customer']['cust_s_name'] == '') ||
                        ($_SESSION['customer']['cust_s_cname'] == '') ||
                        ($_SESSION['customer']['cust_s_phone'] == '') ||
                        ($_SESSION['customer']['cust_s_country'] == '') ||
                        ($_SESSION['customer']['cust_s_address'] == '') ||
                        ($_SESSION['customer']['cust_s_city'] == '') ||
                        ($_SESSION['customer']['cust_s_state'] == '') ||
                        ($_SESSION['customer']['cust_s_zip'] == '')
                    ) {
                        $checkout_access = 0;
                    }
                    ?>
                    <?php if ($checkout_access == 0): ?>
                        <div class="col-md-12">
                            <div style="color:red;font-size:22px;margin-bottom:50px;">
                                You must have to fill up all the billing and shipping information from your dashboard panel in order to checkout the order. Please fill up the information going to <a href="customer-billing-shipping-update.php" style="color:red;text-decoration:underline;">this link</a>.
                            </div>
                        </div>
                    <?php else: ?>

                        <div class="coupon-section">
                            <h3 class="special">Apply Coupon</h3>
                            <form method="post">
                                <div class="form-group">
                                    <div class="input-group">
                                        <input type="text" class="form-control" name="coupon_code"
                                               placeholder="Enter coupon code"
                                               value="<?php echo htmlspecialchars($coupon_code); ?>">
                                        <span class="input-group-btn">
                                            <button type="submit" name="apply_coupon" class="btn btn-primary">Apply</button>
                                        </span>
                                    </div>
                                    <?php if (!empty($coupon_error)): ?>
                                        <div class="text-danger"><?php echo htmlspecialchars($coupon_error); ?></div>
                                    <?php elseif ($coupon_discount > 0): ?>
                                        <div class="text-success">
                                            Coupon applied! Discount: <?php echo  LANG_VALUE_1 . number_format($coupon_discount, 2); ?>
                                        </div>
                                    <?php endif; ?>
                                </div>
                            </form>
                        </div>

                        <div class="col-md-12">
                            <div class="form-group">
                                <label for="paymentMethod"><?php echo LANG_VALUE_34; ?> *</label>
                                <select name="payment_method" class="form-control select2" id="paymentMethod" onchange="changeColor()">
                                    <option value=""><?php echo LANG_VALUE_35; ?></option>
                                    <?php if (in_array('bKash/Nagad/Rocket', $enabled_methods) || in_array('SwapnoPay', $enabled_methods)): ?>
                                        <option value="SwapnoPay" selected>bKash / Nagad / Rocket (Instant Auto-Verification)</option>
                                    <?php endif; ?>
                                    <?php if (in_array('Cash on Delivery', $enabled_methods)): ?>
                                        <option value="Cash on Delivery">Cash on Delivery</option>
                                    <?php endif; ?>
                                    <?php if (in_array('PayPal', $enabled_methods)): ?>
                                        <option value="PayPal"><?php echo LANG_VALUE_36; ?></option>
                                    <?php endif; ?>
                                    <?php if (in_array('Bank Deposit', $enabled_methods)): ?>
                                        <option value="Bank Deposit"><?php echo LANG_VALUE_38; ?></option>
                                    <?php endif; ?>
                                    <?php if (in_array('Cash on Delivery', $enabled_methods)): ?>
                                        <option value="Cash on Delivery">Cash on Delivery</option>
                                    <?php endif; ?>

                                    <?php if (in_array('SSLCommerz', $enabled_methods)): ?>
                                        <option value="SSLCommerz">SSLCommerz</option>
                                    <?php endif; ?>
                                </select>
                            </div>

                            <?php if (in_array('bKash/Nagad/Rocket', $enabled_methods) || in_array('SwapnoPay', $enabled_methods)): ?>
                                <div id="swapnopay_form" class="payment-form" style="display:block;">
                                    <form action="payment/swapnopay/process.php" method="post">
                                        <input type="hidden" name="final_total" value="<?php echo number_format($final_total, 2, '.', ''); ?>">
                                        
                                        <div class="form-group">
                                            <div class="alert alert-success" style="background:#f0fdf4; border:1px solid #bbf7d0; color:#166534; padding:15px; border-radius:8px;">
                                                <h4 style="margin-top:0; font-weight:700;"><i class="fa fa-bolt"></i> Instant Mobile Payment (bKash / Nagad / Rocket / Upay)</h4>
                                                <p style="margin-bottom:0;">Pay directly to the merchant's verified mobile account. Zero transaction fee and instant automated verification via the merchant's mobile terminal.</p>
                                            </div>
                                        </div>

                                        <div class="form-group">
                                            <label><strong>Select Payment Provider:</strong></label>
                                            <div class="row" style="margin-top:8px;">
                                                <div class="col-xs-6 col-sm-3">
                                                    <label style="display:block; padding:10px; border:2px solid #e2e8f0; border-radius:8px; text-align:center; cursor:pointer; background:#fff;">
                                                        <input type="radio" name="mfs_provider" value="bKash" checked style="margin-right:5px;">
                                                        <span style="font-weight:700; color:#e2136e;">bKash</span>
                                                    </label>
                                                </div>
                                                <div class="col-xs-6 col-sm-3">
                                                    <label style="display:block; padding:10px; border:2px solid #e2e8f0; border-radius:8px; text-align:center; cursor:pointer; background:#fff;">
                                                        <input type="radio" name="mfs_provider" value="Nagad" style="margin-right:5px;">
                                                        <span style="font-weight:700; color:#f7941d;">Nagad</span>
                                                    </label>
                                                </div>
                                                <div class="col-xs-6 col-sm-3">
                                                    <label style="display:block; padding:10px; border:2px solid #e2e8f0; border-radius:8px; text-align:center; cursor:pointer; background:#fff;">
                                                        <input type="radio" name="mfs_provider" value="Rocket" style="margin-right:5px;">
                                                        <span style="font-weight:700; color:#8c3494;">Rocket</span>
                                                    </label>
                                                </div>
                                                <div class="col-xs-6 col-sm-3">
                                                    <label style="display:block; padding:10px; border:2px solid #e2e8f0; border-radius:8px; text-align:center; cursor:pointer; background:#fff;">
                                                        <input type="radio" name="mfs_provider" value="Upay" style="margin-right:5px;">
                                                        <span style="font-weight:700; color:#005696;">Upay</span>
                                                    </label>
                                                </div>
                                            </div>
                                        </div>

                                        <div class="form-group">
                                            <label>Order / Delivery Note (Optional):</label>
                                            <textarea name="customer_note" class="form-control" rows="2" placeholder="Special delivery instructions or notes for the merchant..."></textarea>
                                        </div>

                                        <div class="form-group" style="margin-top:20px;">
                                            <button type="submit" class="btn btn-success btn-lg" style="background:#16a34a; border-color:#15803d; font-weight:bold; padding:12px 30px;">
                                                Proceed to Pay (<?php echo LANG_VALUE_1 . number_format($final_total, 2); ?>) <i class="fa fa-arrow-right"></i>
                                            </button>
                                        </div>
                                    </form>
                                </div>
                            <?php endif; ?>

                            <?php if (in_array('PayPal', $enabled_methods)): ?>
                                <div id="paypal_form" class="payment-form" style="display:none;">
                                    <form class="paypal" action="<?php echo BASE_URL; ?>payment/paypal/payment_process.php" method="post" target="_blank">
                                        <input type="hidden" name="cmd" value="_xclick" />
                                        <input type="hidden" name="no_note" value="1" />
                                        <input type="hidden" name="lc" value="UK" />
                                        <input type="hidden" name="currency_code" value="USD" />
                                        <input type="hidden" name="bn" value="PP-BuyNowBF:btn_buynow_LG.gif:NonHostedGuest" />
                                        <input type="hidden" name="final_total" value="<?php echo number_format($final_total, 2, '.', ''); ?>">
                                        <div class="form-group">
                                            <input type="submit" class="btn btn-primary" value="<?php echo LANG_VALUE_46; ?>" name="form1">
                                        </div>
                                    </form>
                                </div>
                            <?php endif; ?>

                            <?php if (in_array('Bank Deposit', $enabled_methods)): ?>
                                <div id="bank_form" class="payment-form" style="display:none;">
                                    <form action="payment/bank/init.php" method="post">
                                        <input type="hidden" name="amount" value="<?php echo number_format($final_total, 2, '.', ''); ?>">
                                        <div class="form-group">
                                            <label for=""><?php echo LANG_VALUE_43; ?></span></label><br>
                                            <?php
                                            $statement = $pdo->prepare("SELECT bank_detail FROM tbl_settings WHERE id=1");
                                             $statement = $pdo->prepare("SELECT bank_detail FROM tbl_settings WHERE id=1");
                                            $statement->execute();
                                            $result = $statement->fetch(PDO::FETCH_ASSOC);
                                            echo nl2br(htmlspecialchars($result['bank_detail'] ?? ''));
                                            ?>
                                        </div>
                                        <div class="form-group">
                                            <label for=""><?php echo LANG_VALUE_44; ?> <br><span style="font-size:12px;font-weight:normal;">(<?php echo LANG_VALUE_45; ?>)</span></label>
                                            <textarea name="transaction_info" class="form-control" cols="30" rows="10"></textarea>
                                        </div>
                                        <div class="form-group">
                                            <input type="submit" class="btn btn-primary" value="<?php echo LANG_VALUE_46; ?>" name="form3">
                                        </div>
                                    </form>
                                </div>
                            <?php endif; ?>

                            <?php if (in_array('Cash on Delivery', $enabled_methods)): ?>
                                <div id="cod_form" class="payment-form" style="display:none;">
                                    <form action="payment/cod/process.php" method="post">
                                        <?php $csrf->echoInputField(); ?>
                                        <input type="hidden" name="checkout_token" value="<?php echo htmlspecialchars($_SESSION['checkout_token'],ENT_QUOTES,'UTF-8'); ?>">
                                        <input type="hidden" name="final_total" value="<?php echo number_format($final_total, 2, '.', ''); ?>">
                                        <div class="form-group">
                                            <div class="alert alert-info">
                                                <h4><i class="fa fa-truck"></i> Cash on Delivery</h4>
                                                <p>Pay with cash when your order is delivered. No online payment required.</p>
                                            </div>
                                        </div>
                                        <div class="form-group">
                                            <label>Additional Notes (Optional)</label>
                                            <textarea name="customer_note" class="form-control" placeholder="Special delivery instructions"></textarea>
                                        </div>
                                        <div class="form-group">
                                            <input type="submit" class="btn btn-success btn-lg" value="Place Order (Cash on Delivery)" name="form_cod">
                                        </div>
                                    </form>
                                </div>
                            <?php endif; ?>

                            <?php if (in_array('SSLCommerz', $enabled_methods)): ?>
                                <div id="sslcommerz_form" class="payment-form" style="display:block;">
                                <div id="sslcommerz_form" class="payment-form" style="display:none;">
                                    <form action="payment/sslcommerz/process.php" method="post">
                                        <input type="hidden" name="final_total" value="<?php echo number_format($final_total, 2, '.', ''); ?>">
                                        <div class="form-group">
                                            <input type="submit" class="btn btn-primary" value="Pay with SSLCommerz" name="form_ssl">
                                        </div>
                                    </form>
                                </div>
                            <?php endif; ?>

                        </div>

                    <?php endif; ?>
                </div>

            </div>
        </div>
    </div>
</div>

<script>
    function changeColor() {
        // Hide all payment forms
        document.querySelectorAll('.payment-form').forEach(form => {
            form.style.display = 'none';
        });

        // Show selected form
        const method = document.getElementById("paymentMethod").value;
        const formIds = {
            'SwapnoPay': 'swapnopay_form',
            'bKash/Nagad/Rocket': 'swapnopay_form',
            'PayPal': 'paypal_form',
            'Bank Deposit': 'bank_form',
            'Cash on Delivery': 'cod_form',
            'SSLCommerz': 'sslcommerz_form'
        };
        
        if (method && formIds[method]) {
            document.getElementById(formIds[method]).style.display = 'block';
            const targetEl = document.getElementById(formIds[method]);
            if (targetEl) {
                targetEl.style.display = 'block';
            }
        }
    }

    // Initialize on page load
    document.addEventListener('DOMContentLoaded', changeColor);
</script>

<?php require_once('footer.php'); ?>