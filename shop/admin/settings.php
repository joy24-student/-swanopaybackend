<?php require_once __DIR__ . '/inc/guard.php'; ?>
<?php require_once('header.php'); ?>

<?php
// Fetch all settings data from the database
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC);
// Assign variables for current values, using null coalescing operator for safety
// General Settings
$logo = $settings_data['logo'] ?? '';
$favicon = $settings_data['favicon'] ?? '';
$contact_email = $settings_data['contact_email'] ?? '';
$contact_phone = $settings_data['contact_phone'] ?? '';
$meta_title_home = $settings_data['meta_title_home'] ?? '';
$meta_keyword_home = $settings_data['meta_keyword_home'] ?? '';
$meta_description_home = $settings_data['meta_description_home'] ?? '';
$before_head = $settings_data['before_head'] ?? '';
$after_body = $settings_data['after_body'] ?? '';
$before_body = $settings_data['before_body'] ?? ''; // From user's provided settings.php
$estimated_delivery_time_local = $settings_data['estimated_delivery_time_local'] ?? '3-5 business days';
$estimated_delivery_time_international = $settings_data['estimated_delivery_time_international'] ?? '10-20 business days';

// Home Page Features
$cta_title = $settings_data['cta_title'] ?? '';
$cta_content = $settings_data['cta_content'] ?? '';
$cta_read_more_text = $settings_data['cta_read_more_text'] ?? '';
$cta_read_more_url = $settings_data['cta_read_more_url'] ?? '';
$cta_photo = $settings_data['cta_photo'] ?? '';
$featured_product_title = $settings_data['featured_product_title'] ?? '';
$featured_product_subtitle = $settings_data['featured_product_subtitle'] ?? '';
$latest_product_title = $settings_data['latest_product_title'] ?? '';
$latest_product_subtitle = $settings_data['latest_product_subtitle'] ?? '';
$popular_product_title = $settings_data['popular_product_title'] ?? '';
$popular_product_subtitle = $settings_data['popular_product_subtitle'] ?? '';
$testimonial_title = $settings_data['testimonial_title'] ?? '';
$testimonial_subtitle = $settings_data['testimonial_subtitle'] ?? '';
$testimonial_photo = $settings_data['testimonial_photo'] ?? '';
$blog_title = $settings_data['blog_title'] ?? '';
$blog_subtitle = $settings_data['blog_subtitle'] ?? '';
$newsletter_text = $settings_data['newsletter_text'] ?? '';

$total_featured_product_home = $settings_data['total_featured_product_home'] ?? 0;
$total_latest_product_home = $settings_data['total_latest_product_home'] ?? 0;
$total_popular_product_home = $settings_data['total_popular_product_home'] ?? 0;

// Flash sale end time (admin-configurable)
$flash_sale_end_time = $settings_data['flash_sale_end_time'] ?? '';

$home_service_on_off = $settings_data['home_service_on_off'] ?? 0;
$home_welcome_on_off = $settings_data['home_welcome_on_off'] ?? 0;
$home_featured_product_on_off = $settings_data['home_featured_product_on_off'] ?? 0;
$home_latest_product_on_off = $settings_data['home_latest_product_on_off'] ?? 0;
$home_popular_product_on_off = $settings_data['home_popular_product_on_off'] ?? 0;
$home_testimonial_on_off = $settings_data['home_testimonial_on_off'] ?? 0;
$home_blog_on_off = $settings_data['home_blog_on_off'] ?? 0;
$home_map_on_off = $settings_data['home_map_on_off'] ?? 0;
$home_newsletter_on_off = $settings_data['home_newsletter_on_off'] ?? 0;
$home_brand_on_off = $settings_data['home_brand_on_off'] ?? 0; // Assuming this exists or will be added
// --- Variable Declarations for Popup ---
$popup_on_off = $settings_data['popup_on_off'] ?? 0;
$popup_text   = $settings_data['popup_text'] ?? '';
$popup_link   = $settings_data['popup_link'] ?? '';
$popup_photo  = $settings_data['popup_photo'] ?? '';
// Email Settings - Reverted to original names based on user feedback
$smtp_from_name = $settings_data['smtp_from_name'] ?? ''; // Reverted to original
$smtp_from_email = $settings_data['smtp_from_email'] ?? ''; // Reverted to original
$email_method = $settings_data['email_method'] ?? 'PHP Mail';
$smtp_host = $settings_data['smtp_host'] ?? '';
$smtp_port = $settings_data['smtp_port'] ?? '';
$smtp_username = $settings_data['smtp_username'] ?? '';
$smtp_password = $settings_data['smtp_password'] ?? '';
$smtp_encryption = $settings_data['smtp_encryption'] ?? 'none'; // Confirmed consistency
$receive_email = $settings_data['receive_email'] ?? ''; // From user's provided settings.php
$receive_email_subject = $settings_data['receive_email_subject'] ?? ''; // From user's provided settings.php
$receive_email_thank_you_message = $settings_data['receive_email_thank_you_message'] ?? ''; // From user's provided settings.php
$forget_password_message = $settings_data['forget_password_message'] ?? ''; // From user's provided settings.php
    $multi_vendor_on_off = $settings_data['multi_vendor_on_off'] ?? 0;

$coin_payment_on_off = $settings_data['coin_payment_on_off'] ?? 0;

        $desktop_advanced_layout_on_off = $settings_data['desktop_advanced_layout_on_off'] ?? 0;



// Payment Gateways
$stripe_public_key = $settings_data['stripe_public_key'] ?? '';
$stripe_secret_key = $settings_data['stripe_secret_key'] ?? '';
$paypal_client_id = $settings_data['paypal_client_id'] ?? '';
$paypal_secret = $settings_data['paypal_secret'] ?? '';
$paypal_sandbox_mode = $settings_data['paypal_sandbox_mode'] ?? 0;
$paypal_email = $settings_data['paypal_email'] ?? ''; // From user's provided settings.php
$bank_detail = $settings_data['bank_detail'] ?? ''; // From user's provided settings.php
// Corrected variable names for SSLCommerz data retrieval
$sslcz_store_id = $settings_data['sslcz_store_id'] ?? '';
$sslcz_store_pass = $settings_data['sslcz_store_pass'] ?? '';
$sslcz_mode = $settings_data['sslcz_mode'] ?? 'sandbox'; // Corrected to sandbox_mode
$cod_enabled = $settings_data['cod_enabled'] ?? 1;
$payment_methods = $settings_data['payment_methods'] ?? '';
$enabled_payment_methods_array = explode(',', $payment_methods);







// API Integrations
$gemini_api_key = $settings_data['gemini_api_key'] ?? '';
$facebook_app_id = $settings_data['facebook_app_id'] ?? '';
$facebook_app_secret = $settings_data['facebook_app_secret'] ?? '';
$google_client_id = $settings_data['google_client_id'] ?? '';
$google_client_secret = $settings_data['google_client_secret'] ?? '';
// Assuming Twilio API key and secret will also be added if needed
$twilio_account_sid = $settings_data['twilio_account_sid'] ?? '';
$twilio_auth_token = $settings_data['twilio_auth_token'] ?? '';
$twilio_phone_number = $settings_data['twilio_phone_number'] ?? '';






// Review Settings
$review_feature_on_off = $settings_data['review_feature_on_off'] ?? 1;

// SMS Settings
$sms_api_key = $settings_data['sms_api_key'] ?? '';
$sms_sender_id = $settings_data['sms_sender_id'] ?? '';
$sms_feature_on_off = $settings_data['sms_feature_on_off'] ?? 0;





// Banner Settings
$banner_cart = $settings_data['banner_cart'] ?? '';
$banner_search = $settings_data['banner_search'] ?? '';
$banner_registration = $settings_data['banner_registration'] ?? '';
$banner_login = $settings_data['banner_login'] ?? '';
$banner_forget_password = $settings_data['banner_forget_password'] ?? '';
$banner_reset_password = $settings_data['banner_reset_password'] ?? '';
$banner_product_category = $settings_data['banner_product_category'] ?? '';
$banner_blog = $settings_data['banner_blog'] ?? '';
$banner_faq = $settings_data['banner_faq'] ?? '';
$banner_contact = $settings_data['banner_contact'] ?? '';
$banner_checkout = $settings_data['banner_checkout'] ?? '';
$banner_payment = $settings_data['banner_payment'] ?? '';
$banner_customer_panel = $settings_data['banner_customer_panel'] ?? '';
$banner_about = $settings_data['banner_about'] ?? '';
$banner_terms = $settings_data['banner_terms'] ?? '';
$banner_privacy = $settings_data['banner_privacy'] ?? '';
$banner_shipping = $settings_data['banner_shipping'] ?? '';
$banner_return_policy = $settings_data['banner_return_policy'] ?? '';
$banner_photo_gallery = $settings_data['banner_photo_gallery'] ?? '';
$banner_team = $settings_data['banner_team'] ?? '';







// Social Media Settings
$facebook_url = $settings_data['facebook_url'] ?? '';
$twitter_url = $settings_data['twitter_url'] ?? '';
$linkedin_url = $settings_data['linkedin_url'] ?? '';
$instagram_url = $settings_data['instagram_url'] ?? '';
$youtube_url = $settings_data['youtube_url'] ?? '';







// Footer Settings
$copyright_text = $settings_data['copyright_text'] ?? '';
$footer_about_us = $settings_data['footer_about_us'] ?? '';
$contact_address = $settings_data['contact_address'] ?? ''; // From user's provided settings.php
$contact_map_iframe = $settings_data['contact_map_iframe'] ?? ''; // From user's provided settings.php
$payment_verified_image = $settings_data['payment_verified_image'] ?? ''; // New field






// Ads Settings
$ads_above_welcome_on_off = $settings_data['ads_above_welcome_on_off'] ?? 0;
$ads_above_featured_product_on_off = $settings_data['ads_above_featured_product_on_off'] ?? 0;
$ads_above_latest_product_on_off = $settings_data['ads_above_latest_product_on_off'] ?? 0;
$ads_above_popular_product_on_off = $settings_data['ads_above_popular_product_on_off'] ?? 0;
$ads_above_testimonial_on_off = $settings_data['ads_above_testimonial_on_off'] ?? 0;
$ads_category_sidebar_on_off = $settings_data['ads_category_sidebar_on_off'] ?? 0;






// Blog/Post Counts (from user's provided settings.php)
$total_recent_post_footer = $settings_data['total_recent_post_footer'] ?? 3;
$total_popular_post_footer = $settings_data['total_popular_post_footer'] ?? 3;
$total_recent_post_sidebar = $settings_data['total_recent_post_sidebar'] ?? 3;
$total_popular_post_sidebar = $settings_data['total_popular_post_sidebar'] ?? 3;


// Initialize messages
$error_message = '';
$success_message = '';

// --- Form Submission Handling ---

// Helper function for file uploads
function handle_file_upload($file_input_name, $current_file_name, $upload_dir, $prefix = '') {
    global $error_message;
    $new_file_name = $current_file_name;
    if (!empty($_FILES[$file_input_name]['name'])) {
        $file = $_FILES[$file_input_name];
        $path = $file['name'];
        $path_tmp = $file['tmp_name'] ?? '';
        $error = $file['error'] ?? UPLOAD_ERR_OK;
        $ext = strtolower(pathinfo($path, PATHINFO_EXTENSION));
        $allowed = ['jpg', 'png', 'jpeg', 'gif'];

        $phpFileUploadErrors = array(
            UPLOAD_ERR_OK => 'There is no error, the file uploaded with success.',
            UPLOAD_ERR_INI_SIZE => 'The uploaded file exceeds the upload_max_filesize directive in php.ini.',
            UPLOAD_ERR_FORM_SIZE => 'The uploaded file exceeds the MAX_FILE_SIZE directive that was specified in the HTML form.',
            UPLOAD_ERR_PARTIAL => 'The uploaded file was only partially uploaded.',
            UPLOAD_ERR_NO_FILE => 'No file was uploaded.',
            UPLOAD_ERR_NO_TMP_DIR => 'Missing a temporary folder.',
            UPLOAD_ERR_CANT_WRITE => 'Failed to write file to disk.',
            UPLOAD_ERR_EXTENSION => 'A PHP extension stopped the file upload.',
        );

        if ($error !== UPLOAD_ERR_OK) {
            error_log("UPLOAD DIAG: Upload error for {$file_input_name}: " . ($phpFileUploadErrors[$error] ?? 'Unknown error code ' . $error));
            $error_message .= 'Failed to upload file for ' . str_replace('_', ' ', $file_input_name) . '.<br>';
            return false;
        }

        if (!in_array($ext, $allowed)) {
            $error_message .= 'You must upload a jpg, jpeg, gif or png file for ' . str_replace('_', ' ', $file_input_name) . '.<br>';
            return false;
        }

        if (!is_dir($upload_dir)) {
            if (!@mkdir($upload_dir, 0755, true)) {
                error_log("UPLOAD DIAG: Failed to create upload directory: {$upload_dir}");
                $error_message .= 'Upload directory does not exist and could not be created.<br>';
                return false;
            }
        }

        if (!is_writable($upload_dir)) {
            error_log("UPLOAD DIAG: Upload directory not writable: {$upload_dir}");
            $error_message .= 'Upload directory is not writable. Check permissions.<br>';
            return false;
        }

        if (empty($path_tmp) || !is_uploaded_file($path_tmp)) {
            error_log("UPLOAD DIAG: is_uploaded_file returned false for {$file_input_name}. tmp_name=" . var_export($path_tmp, true) . " _FILES=" . var_export($file, true));
            $error_message .= 'Possible file upload attack detected for ' . str_replace('_', ' ', $file_input_name) . '.<br>';
            return false;
        }

        // Keep the previous image until settings have been saved successfully.

        try {
            $uniq = bin2hex(random_bytes(5));
        } catch (Exception $e) {
            $uniq = uniqid();
        }
        $new_file_name = $prefix . time() . '-' . $uniq . '.' . $ext;

        if (!move_uploaded_file($path_tmp, $upload_dir . $new_file_name)) {
            error_log("UPLOAD DIAG: move_uploaded_file failed for {$file_input_name}. tmp={$path_tmp} dest={$upload_dir}{$new_file_name}");
            $error_message .= 'Failed to move uploaded file for ' . str_replace('_', ' ', $file_input_name) . '.<br>';
            return false;
        }
    }
    return $new_file_name;
}







try {
if ($_SERVER['REQUEST_METHOD']==='POST') $pdo->beginTransaction();
// General Settings Form
if(isset($_POST['form_general_settings'])) {
    $valid = 1;

    $logo = handle_file_upload('photo_logo', $logo, '../assets/uploads/', 'logo-');
    if ($logo === false) $valid = 0;

    $favicon = handle_file_upload('photo_favicon', $favicon, '../assets/uploads/', 'favicon-');
    if ($favicon === false) $valid = 0;

    if($valid == 1) {
        $statement = $pdo->prepare("UPDATE tbl_settings SET
                                    logo=?, favicon=?, contact_email=?, contact_phone=?,
                                    meta_title_home=?, meta_keyword_home=?, meta_description_home=?,
                                    before_head=?, after_body=?, before_body=?,
                                    hide_banner_desktop=?, hide_banner_mobile=?, hide_free_delivery_desktop=?, hide_free_delivery_mobile=?
                                    WHERE id=1");
        $statement->execute(array(
            $logo,
            $favicon,
            $_POST['contact_email'] ?? '',
            $_POST['contact_phone'] ?? '',
            $_POST['meta_title_home'] ?? '',
            $_POST['meta_keyword_home'] ?? '',
            $_POST['meta_description_home'] ?? '',
            $_POST['before_head'] ?? '',
            $_POST['after_body'] ?? '',
            $_POST['before_body'] ?? '',
            isset($_POST['hide_banner_desktop']) ? 1 : 0,
            isset($_POST['hide_banner_mobile']) ? 1 : 0,
            isset($_POST['hide_free_delivery_desktop']) ? 1 : 0,
            isset($_POST['hide_free_delivery_mobile']) ? 1 : 0
        ));
        $success_message = 'General Settings are updated successfully.';
    }
}

if(isset($_POST['form_popup_settings'])) {
    $popup_on_off = $_POST['popup_on_off'] ?? 0;
    $popup_text   = $_POST['popup_text'] ?? '';
    $popup_link   = $_POST['popup_link'] ?? '';
    $path = $_FILES['popup_photo']['name'] ?? '';
    $path_tmp = $_FILES['popup_photo']['tmp_name'] ?? '';

    if($path != '') {
        $ext = pathinfo($path, PATHINFO_EXTENSION);
        $file_name = 'popup-'.bin2hex(random_bytes(16)).'.'.$ext;
        move_uploaded_file($path_tmp, '../assets/uploads/'.$file_name);
        $statement = $pdo->prepare("UPDATE tbl_settings SET popup_on_off=?, popup_text=?, popup_link=?, popup_photo=? WHERE id=1");
        $statement->execute(array($popup_on_off, $popup_text, $popup_link, $file_name));
    } else {
        $statement = $pdo->prepare("UPDATE tbl_settings SET popup_on_off=?, popup_text=?, popup_link=? WHERE id=1");
        $statement->execute(array($popup_on_off, $popup_text, $popup_link));
    }
    $success_message = 'Popup settings updated successfully.';
}



// Home Page Features Form
if(isset($_POST['form_home_features'])) {
    $valid = 1;

    $cta_photo = handle_file_upload('cta_photo', $cta_photo, '../assets/uploads/', 'cta-');
    if ($cta_photo === false) $valid = 0;

    $testimonial_photo = handle_file_upload('testimonial_photo', $testimonial_photo, '../assets/uploads/', 'testimonial-');
    if ($testimonial_photo === false) $valid = 0;

    $slider_side_banner_img = handle_file_upload('slider_side_banner_img', $settings_data['slider_side_banner_img'] ?? '', '../assets/uploads/', 'side-banner-');
    if ($slider_side_banner_img === false) $valid = 0;

    if ($valid == 1) {
        $statement = $pdo->prepare("UPDATE tbl_settings SET 
            cta_title=?, cta_content=?, cta_read_more_text=?, cta_read_more_url=?, cta_photo=?, 
            featured_product_title=?, featured_product_subtitle=?, 
            latest_product_title=?, latest_product_subtitle=?, 
            popular_product_title=?, popular_product_subtitle=?, 
            testimonial_title=?, testimonial_subtitle=?, testimonial_photo=?, 
            blog_title=?, blog_subtitle=?, newsletter_text=?, 
            total_featured_product_home=?, total_latest_product_home=?, total_popular_product_home=?, 
            home_welcome_on_off=?, home_featured_product_on_off=?, 
            home_latest_product_on_off=?, home_popular_product_on_off=?, 
            home_service_on_off=?, home_blog_on_off=?, 
            home_map_on_off=?, home_newsletter_on_off=?, home_brand_on_off=?, 
            home_testimonial_on_off=?, 
            home_slider_on_off=?, home_features_on_off=?, home_category_on_off=?,
            multi_vendor_on_off=?, 
        coin_payment_on_off=?, 
        desktop_advanced_layout_on_off=?,
            /* New Columns */
            bg_color_categories=?, bg_color_latest_products=?, bg_color_featured_products=?, show_scroll_top_btn=?,
            slider_side_banner_img=?, slider_side_banner_text=?, extra_footer_section_enable=?, flash_sale_end_time=?,

            /* Ordering */
            home_slider_order=?, home_features_order=?, home_category_order=?,
            home_flash_order=?, home_featured_product_order=?, 
            home_latest_product_order=?, home_popular_product_order=?

            WHERE id=1");

        $statement->execute(array(
            $_POST['cta_title'] ?? '',
            $_POST['cta_content'] ?? '',
            $_POST['cta_read_more_text'] ?? '',
            $_POST['cta_read_more_url'] ?? '',
            $cta_photo,
            $_POST['featured_product_title'] ?? '',
            $_POST['featured_product_subtitle'] ?? '',
            $_POST['latest_product_title'] ?? '',
            $_POST['latest_product_subtitle'] ?? '',
            $_POST['popular_product_title'] ?? '',
            $_POST['popular_product_subtitle'] ?? '',
            $_POST['testimonial_title'] ?? '',
            $_POST['testimonial_subtitle'] ?? '',
            $testimonial_photo,
            $_POST['blog_title'] ?? '',
            $_POST['blog_subtitle'] ?? '',
            $_POST['newsletter_text'] ?? '',
            $_POST['total_featured_product_home'] ?? 0,
            $_POST['total_latest_product_home'] ?? 0,
            $_POST['total_popular_product_home'] ?? 0,
            $_POST['home_welcome_on_off'] ?? 0,
            $_POST['home_featured_product_on_off'] ?? 0,
            $_POST['home_latest_product_on_off'] ?? 0,
            $_POST['home_popular_product_on_off'] ?? 0,
            $_POST['home_service_on_off'] ?? 0,
            $_POST['home_blog_on_off'] ?? 0,
            $_POST['home_map_on_off'] ?? 0,
            $_POST['home_newsletter_on_off'] ?? 0,
            $_POST['home_brand_on_off'] ?? 0,
            $_POST['home_testimonial_on_off'] ?? 0,
            $_POST['home_slider_on_off'] ?? 1,
            $_POST['home_features_on_off'] ?? 1,
            $_POST['home_category_on_off'] ?? 1,
              $_POST['multi_vendor_on_off'] ?? 0,
        $_POST['coin_payment_on_off'] ?? 0,
        $_POST['desktop_advanced_layout_on_off'] ?? 0,
            // New Values
            $_POST['bg_color_categories'] ?? '#ffffff',
            $_POST['bg_color_latest_products'] ?? '#ffffff',
            $_POST['bg_color_featured_products'] ?? '#ffffff',
            $_POST['show_scroll_top_btn'] ?? 0,
            $slider_side_banner_img,
            $_POST['slider_side_banner_text'] ?? '',
            $_POST['extra_footer_section_enable'] ?? 0,
            ($_POST['flash_sale_end_time'] ?? '') ?: null,

            // Ordering
            $_POST['home_slider_order'] ?? 1,
            $_POST['home_features_order'] ?? 2,
            $_POST['home_category_order'] ?? 3,
            $_POST['home_flash_order'] ?? 4,
            $_POST['home_featured_product_order'] ?? 5,
            $_POST['home_latest_product_order'] ?? 6,
            $_POST['home_popular_product_order'] ?? 7
        ));
        $success_message = 'Home Page Features updated successfully.';
    }
}
// Payment Gateways Form
if(isset($_POST['form_payment_gateways'])) {
    $selected_methods = array_intersect((array)($_POST['payment_methods'] ?? []), ['Cash on Delivery','PayPal','Bank','SSLCommerz','SwapnoPay']);
    if (($_POST['cod_enabled'] ?? 0)==1) $selected_methods[]='Cash on Delivery';
    $payment_methods_selected = implode(',', array_unique($selected_methods));

    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                stripe_public_key=?, stripe_secret_key=?,
                                paypal_client_id=?, paypal_secret=?, paypal_sandbox_mode=?, paypal_email=?,
                                sslcz_store_id=?, sslcz_store_pass=?, sslcz_mode=?,
                                cod_enabled=?, payment_methods=?, bank_detail=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['stripe_public_key'] ?? '',
        $_POST['stripe_secret_key'] ?? '',
        $_POST['paypal_client_id'] ?? '',
        $_POST['paypal_secret'] ?? '',
        $_POST['paypal_sandbox_mode'] ?? 0,
        $_POST['paypal_email'] ?? '',
        $_POST['sslcz_store_id'] ?? '',
        $_POST['sslcz_store_pass'] ?? '',
        in_array($_POST['sslcz_mode'] ?? '', ['live','0'], true) ? 'live' : 'sandbox',
        $_POST['cod_enabled'] ?? 0,
        $payment_methods_selected,
        $_POST['bank_detail'] ?? ''
    ));
    $success_message = 'Payment Gateway Settings are updated successfully.';
}





// API Integrations Form
if(isset($_POST['form_api_integrations'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                gemini_api_key=?,
                                facebook_app_id=?, facebook_app_secret=?,
                                google_client_id=?, google_client_secret=?,
                                twilio_account_sid=?, twilio_auth_token=?, twilio_phone_number=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['gemini_api_key'] ?? '',
        $_POST['facebook_app_id'] ?? '',
        $_POST['facebook_app_secret'] ?? '',
        $_POST['google_client_id'] ?? '',
        $_POST['google_client_secret'] ?? '',
        $_POST['twilio_account_sid'] ?? '',
        $_POST['twilio_auth_token'] ?? '',
        $_POST['twilio_phone_number'] ?? ''
    ));
    $success_message = 'API Integration Settings are updated successfully.';
}






// Review & Delivery Settings Form
if(isset($_POST['form_review_delivery_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                review_feature_on_off=?,
                                estimated_delivery_time_local=?,
                                estimated_delivery_time_international=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['review_feature_on_off'] ?? 0,
        $_POST['estimated_delivery_time_local'] ?? '',
        $_POST['estimated_delivery_time_international'] ?? ''
    ));
    $success_message = 'Review & Delivery Settings are updated successfully.';
}

// SMS Settings Form
if(isset($_POST['form_sms_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                sms_feature_on_off=?,
                                sms_api_key=?,
                                sms_sender_id=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['sms_feature_on_off'] ?? 0,
        $_POST['sms_api_key'] ?? '',
        $_POST['sms_sender_id'] ?? ''
    ));
    $success_message = 'SMS Settings are updated successfully.';
}





// Banner Settings Form
if(isset($_POST['form_banner_settings'])) {
    $valid = 1;
    $banner_fields = [
        'banner_cart', 'banner_search', 'banner_registration', 'banner_login',
        'banner_forget_password', 'banner_reset_password', 'banner_product_category',
        'banner_blog', 'banner_faq', 'banner_contact', 'banner_checkout',
        'banner_payment', 'banner_customer_panel', 'banner_about', 'banner_terms',
        'banner_privacy', 'banner_shipping', 'banner_return_policy',
        'banner_photo_gallery', 'banner_team'
    ];

    $update_query_parts = [];
    $update_query_values = [];

    foreach ($banner_fields as $field) {
        $new_banner_name = handle_file_upload($field, $settings_data[$field] ?? '', '../assets/uploads/', $field . '-');
        if ($new_banner_name === false) {
            $valid = 0;
            break; // Stop if any upload fails
        }
        if ($new_banner_name !== ($settings_data[$field] ?? '')) { // Only update if file was changed/uploaded
            $update_query_parts[] = '"' . $field . '" = ?';
            $update_query_values[] = $new_banner_name;
        }
    }

    if ($valid == 1) {
        if (!empty($update_query_parts)) {
            $statement = $pdo->prepare("UPDATE tbl_settings SET " . implode(', ', $update_query_parts) . " WHERE id=1");
            $statement->execute($update_query_values);
            $success_message = 'Banner Settings are updated successfully.';
        } else {
            $error_message = 'No new banner files selected for upload.';
        }
    }
}





// Social Media Form
if(isset($_POST['form_social_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                facebook_url=?, twitter_url=?, linkedin_url=?,
                                instagram_url=?, youtube_url=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['facebook_url'] ?? '',
        $_POST['twitter_url'] ?? '',
        $_POST['linkedin_url'] ?? '',
        $_POST['instagram_url'] ?? '',
        $_POST['youtube_url'] ?? ''
    ));
    $success_message = 'Social Media Settings are updated successfully.';
}




// Footer Settings Form
if(isset($_POST['form_footer_settings'])) {
    $valid = 1;
    $payment_verified_image = handle_file_upload('payment_verified_image', $payment_verified_image, '../assets/uploads/', 'payment_verified-');
    if ($payment_verified_image === false) $valid = 0;

    if ($valid == 1) {
        $statement = $pdo->prepare("UPDATE tbl_settings SET
                                    copyright_text=?, footer_about_us=?, contact_address=?, contact_map_iframe=?, payment_verified_image=?
                                    WHERE id=1");
        $statement->execute(array(
            $_POST['copyright_text'] ?? '',
            $_POST['footer_about_us'] ?? '',
            $_POST['contact_address'] ?? '',
            $_POST['contact_map_iframe'] ?? '',
            $payment_verified_image
        ));
        $success_message = 'Footer Settings are updated successfully.';
    }
}



// Email Form Settings Form (renamed from form4)

if(isset($_POST['form_email_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                smtp_from_name=?, smtp_from_email=?, email_method=?, smtp_host=?,smtp_port=?,smtp_username=?,smtp_password=?,smtp_encryption=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['smtp_from_name'] ?? '',
        $_POST['smtp_from_email'] ?? '',
        $_POST['email_method'] ?? '',
        $_POST['smtp_host'] ?? '',
        $_POST['smtp_port'] ?? '',
        $_POST['smtp_username'] ?? '',
        $_POST['smtp_password'] ?? '',
        $_POST['smtp_encryption'] ?? ''
    ));
    $success_message = 'Email outgoing Settings are updated successfully.';
}








// Email Content Settings Form (renamed from form4)
if(isset($_POST['form_email_content_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                receive_email=?, receive_email_subject=?, receive_email_thank_you_message=?, forget_password_message=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['receive_email'] ?? '',
        $_POST['receive_email_subject'] ?? '',
        $_POST['receive_email_thank_you_message'] ?? '',
        $_POST['forget_password_message'] ?? ''
    ));
    $success_message = 'Email Content Settings are updated successfully.';
}


// Blog/Post Counts Settings Form (renamed from form5)
if(isset($_POST['form_blog_post_counts'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                total_featured_product_home=?, total_latest_product_home=?, total_popular_product_home=?,
                                total_recent_post_footer=?, total_popular_post_footer=?,
                                total_recent_post_sidebar=?, total_popular_post_sidebar=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['total_featured_product_home'] ?? 0,
        $_POST['total_latest_product_home'] ?? 0,
        $_POST['total_popular_product_home'] ?? 0,
        $_POST['total_recent_post_footer'] ?? 0,
        $_POST['total_popular_post_footer'] ?? 0,
        $_POST['total_recent_post_sidebar'] ?? 0,
        $_POST['total_popular_post_sidebar'] ?? 0
    ));
    $success_message = 'Blog/Post Counts Settings are updated successfully.';
}

// Ads On/Off Settings Form (renamed from form11)
if(isset($_POST['form_ads_settings'])) {
    $statement = $pdo->prepare("UPDATE tbl_settings SET
                                ads_above_welcome_on_off=?,
                                ads_above_featured_product_on_off=?,
                                ads_above_latest_product_on_off=?,
                                ads_above_popular_product_on_off=?,
                                ads_above_testimonial_on_off=?,
                                ads_category_sidebar_on_off=?
                                WHERE id=1");
    $statement->execute(array(
        $_POST['ads_above_welcome_on_off'] ?? 0,
        $_POST['ads_above_featured_product_on_off'] ?? 0,
        $_POST['ads_above_latest_product_on_off'] ?? 0,
        $_POST['ads_above_popular_product_on_off'] ?? 0,
        $_POST['ads_above_testimonial_on_off'] ?? 0,
        $_POST['ads_category_sidebar_on_off'] ?? 0
    ));
    $success_message = 'Advertisement On-Off Settings are updated successfully.';
}



if ($_SERVER['REQUEST_METHOD']==='POST') {
    if ($error_message !== '') { $pdo->rollBack(); $success_message=''; }
    else {
        if (isset($_POST['form_footer_settings'])) $pdo->prepare('UPDATE tbl_settings SET footer_copyright=copyright_text,footer_about=footer_about_us WHERE id=1')->execute();
        $pdo->commit();
    }
}
} catch(Throwable $error) {
    if($pdo->inTransaction()) $pdo->rollBack();
    $success_message='';$error_message='Settings could not be saved. Check the entered values and try again.';
    error_log('Store settings update failed: ' . $error->getCode());
}
// Re-fetch settings after any update to ensure displayed values are current
$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$settings_data = $statement->fetch(PDO::FETCH_ASSOC);

// Re-assign all variables with potentially updated values (using original names)
$logo = $settings_data['logo'] ?? '';
$favicon = $settings_data['favicon'] ?? '';
$contact_email = $settings_data['contact_email'] ?? '';
$contact_phone = $settings_data['contact_phone'] ?? '';
$meta_title_home = $settings_data['meta_title_home'] ?? '';
$meta_keyword_home = $settings_data['meta_keyword_home'] ?? '';
$meta_description_home = $settings_data['meta_description_home'] ?? '';
$before_head = $settings_data['before_head'] ?? '';
$after_body = $settings_data['after_body'] ?? '';
$before_body = $settings_data['before_body'] ?? '';

$cta_title = $settings_data['cta_title'] ?? '';
$cta_content = $settings_data['cta_content'] ?? '';
$cta_read_more_text = $settings_data['cta_read_more_text'] ?? '';
$cta_read_more_url = $settings_data['cta_read_more_url'] ?? '';
$cta_photo = $settings_data['cta_photo'] ?? '';
$featured_product_title = $settings_data['featured_product_title'] ?? '';
$featured_product_subtitle = $settings_data['featured_product_subtitle'] ?? '';
$latest_product_title = $settings_data['latest_product_title'] ?? '';
$latest_product_subtitle = $settings_data['latest_product_subtitle'] ?? '';
$popular_product_title = $settings_data['popular_product_title'] ?? '';
$popular_product_subtitle = $settings_data['popular_product_subtitle'] ?? '';
$testimonial_title = $settings_data['testimonial_title'] ?? '';
$testimonial_subtitle = $settings_data['testimonial_subtitle'] ?? '';
$testimonial_photo = $settings_data['testimonial_photo'] ?? '';
$blog_title = $settings_data['blog_title'] ?? '';
$blog_subtitle = $settings_data['blog_subtitle'] ?? '';
$newsletter_text = $settings_data['newsletter_text'] ?? '';

$total_featured_product_home = $settings_data['total_featured_product_home'] ?? 0;
$total_latest_product_home = $settings_data['total_latest_product_home'] ?? 0;
$total_popular_product_home = $settings_data['total_popular_product_home'] ?? 0;

$home_service_on_off = $settings_data['home_service_on_off'] ?? 0;
$home_welcome_on_off = $settings_data['home_welcome_on_off'] ?? 0;
$home_featured_product_on_off = $settings_data['home_featured_product_on_off'] ?? 0;
$home_latest_product_on_off = $settings_data['home_latest_product_on_off'] ?? 0;
$home_popular_product_on_off = $settings_data['home_popular_product_on_off'] ?? 0;
$home_testimonial_on_off = $settings_data['home_testimonial_on_off'] ?? 0;
$home_blog_on_off = $settings_data['home_blog_on_off'] ?? 0;
$home_map_on_off = $settings_data['home_map_on_off'] ?? 0;
$home_newsletter_on_off = $settings_data['home_newsletter_on_off'] ?? 0;
$home_brand_on_off = $settings_data['home_brand_on_off'] ?? 0;






// Email Settings - Using original names for consistency with likely DB schema
$smtp_from_name = $settings_data['smtp_from_name'] ?? '';
$smtp_from_email = $settings_data['smtp_from_email'] ?? '';
$email_method = $settings_data['email_method'] ?? 'PHP Mail';
$smtp_host = $settings_data['smtp_host'] ?? '';
$smtp_port = $settings_data['smtp_port'] ?? '';
$smtp_username = $settings_data['smtp_username'] ?? '';
$smtp_password = $settings_data['smtp_password'] ?? '';
$smtp_encryption = $settings_data['smtp_encryption'] ?? 'none';
$receive_email = $settings_data['receive_email'] ?? '';
$receive_email_subject = $settings_data['receive_email_subject'] ?? '';
$receive_email_thank_you_message = $settings_data['receive_email_thank_you_message'] ?? '';
$forget_password_message = $settings_data['forget_password_message'] ?? '';

$stripe_public_key = $settings_data['stripe_public_key'] ?? '';
$stripe_secret_key = $settings_data['stripe_secret_key'] ?? '';
$paypal_client_id = $settings_data['paypal_client_id'] ?? '';
$paypal_secret = $settings_data['paypal_secret'] ?? '';
$paypal_sandbox_mode = $settings_data['paypal_sandbox_mode'] ?? 0;
$paypal_email = $settings_data['paypal_email'] ?? '';
$bank_detail = $settings_data['bank_detail'] ?? '';
$sslcz_store_id = $settings_data['sslcz_store_id'] ?? '';
$sslcz_store_pass = $settings_data['sslcz_store_pass'] ?? '';
$sslcz_mode = $settings_data['sslcz_mode'] ?? 'sandbox';
$cod_enabled = $settings_data['cod_enabled'] ?? 1;
$payment_methods = $settings_data['payment_methods'] ?? '';
$enabled_payment_methods_array = explode(',', $payment_methods);

$gemini_api_key = $settings_data['gemini_api_key'] ?? '';
$facebook_app_id = $settings_data['facebook_app_id'] ?? '';
$facebook_app_secret = $settings_data['facebook_app_secret'] ?? '';
$google_client_id = $settings_data['google_client_id'] ?? '';
$google_client_secret = $settings_data['google_client_secret'] ?? '';
$twilio_account_sid = $settings_data['twilio_account_sid'] ?? '';
$twilio_auth_token = $settings_data['twilio_auth_token'] ?? '';
$twilio_phone_number = $settings_data['twilio_phone_number'] ?? '';

$review_feature_on_off = $settings_data['review_feature_on_off'] ?? 1;
$estimated_delivery_time_local = $settings_data['estimated_delivery_time_local'] ?? '3-5 business days';
$estimated_delivery_time_international = $settings_data['estimated_delivery_time_international'] ?? '10-20 business days';

$sms_api_key = $settings_data['sms_api_key'] ?? '';
$sms_sender_id = $settings_data['sms_sender_id'] ?? '';
$sms_feature_on_off = $settings_data['sms_feature_on_off'] ?? 0;

$banner_cart = $settings_data['banner_cart'] ?? '';
$banner_search = $settings_data['banner_search'] ?? '';
$banner_registration = $settings_data['banner_registration'] ?? '';
$banner_login = $settings_data['banner_login'] ?? '';
$banner_forget_password = $settings_data['banner_forget_password'] ?? '';
$banner_reset_password = $settings_data['banner_reset_password'] ?? '';
$banner_product_category = $settings_data['banner_product_category'] ?? '';
$banner_blog = $settings_data['banner_blog'] ?? '';
$banner_faq = $settings_data['banner_faq'] ?? '';
$banner_contact = $settings_data['banner_contact'] ?? '';
$banner_checkout = $settings_data['banner_checkout'] ?? '';
$banner_payment = $settings_data['banner_payment'] ?? '';
$banner_customer_panel = $settings_data['banner_customer_panel'] ?? '';
$banner_about = $settings_data['banner_about'] ?? '';
$banner_terms = $settings_data['banner_terms'] ?? '';
$banner_privacy = $settings_data['banner_privacy'] ?? '';
$banner_shipping = $settings_data['banner_shipping'] ?? '';
$banner_return_policy = $settings_data['banner_return_policy'] ?? '';
$banner_photo_gallery = $settings_data['banner_photo_gallery'] ?? '';
$banner_team = $settings_data['banner_team'] ?? '';

$facebook_url = $settings_data['facebook_url'] ?? '';
$twitter_url = $settings_data['twitter_url'] ?? '';
$linkedin_url = $settings_data['linkedin_url'] ?? '';
$instagram_url = $settings_data['instagram_url'] ?? '';
$youtube_url = $settings_data['youtube_url'] ?? '';

$copyright_text = $settings_data['copyright_text'] ?? '';
$footer_about_us = $settings_data['footer_about_us'] ?? '';
$contact_address = $settings_data['contact_address'] ?? '';
$contact_map_iframe = $settings_data['contact_map_iframe'] ?? '';
$payment_verified_image = $settings_data['payment_verified_image'] ?? '';

$ads_above_welcome_on_off = $settings_data['ads_above_welcome_on_off'] ?? 0;
$ads_above_featured_product_on_off = $settings_data['ads_above_featured_product_on_off'] ?? 0;
$ads_above_latest_product_on_off = $settings_data['ads_above_latest_product_on_off'] ?? 0;
$ads_above_popular_product_on_off = $settings_data['ads_above_popular_product_on_off'] ?? 0;
$ads_above_testimonial_on_off = $settings_data['ads_above_testimonial_on_off'] ?? 0;
$ads_category_sidebar_on_off = $settings_data['ads_category_sidebar_on_off'] ?? 0;

$total_recent_post_footer = $settings_data['total_recent_post_footer'] ?? 3;
$total_popular_post_footer = $settings_data['total_popular_post_footer'] ?? 3;
$total_recent_post_sidebar = $settings_data['total_recent_post_sidebar'] ?? 3;
$total_popular_post_sidebar = $settings_data['total_popular_post_sidebar'] ?? 3;


// New variables for banner and free delivery visibility
$hide_banner_desktop = $settings_data['hide_banner_desktop'] ?? 0;
$hide_banner_mobile = $settings_data['hide_banner_mobile'] ?? 0;
$hide_free_delivery_desktop = $settings_data['hide_free_delivery_desktop'] ?? 0;
$hide_free_delivery_mobile = $settings_data['hide_free_delivery_mobile'] ?? 0;








?>

<style>
    /* Basic styling for the admin panel to enhance Tailwind's utility classes */
    .tab-content {
        background-color: #ffffff;
        padding: 2rem;
        border-radius: 0.5rem;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }
    .nav-tabs {
        border-bottom: none;
        margin-bottom: 1.5rem;
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem; /* Space between tabs */
    }
    .nav-tabs li {
        margin-bottom: 0;
    }
    .nav-tabs li a {
        display: block;
        padding: 0.75rem 1.25rem;
        border-radius: 0.375rem; /* rounded-md */
        font-weight: 600; /* font-semibold */
        color: #4b5563; /* text-gray-700 */
        transition: all 0.2s ease-in-out;
        background-color: #e5e7eb; /* bg-gray-200 */
        border: 1px solid transparent;
        text-decoration: none;
    }
    .nav-tabs li a:hover {
        background-color: #d1d5db; /* bg-gray-300 */
        color: #1f2937; /* text-gray-900 */
    }
    .nav-tabs li.active a,
    .nav-tabs li.active a:hover {
        background-color: #4f46e5; /* bg-indigo-600 */
        color: #ffffff; /* text-white */
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }

    .form-group {
        margin-bottom: 1.5rem;
    }
    .form-group label {
        font-weight: 500;
        color: #374151; /* text-gray-700 */
        margin-bottom: 0.5rem;
        display: block;
    }
    .form-control {
        display: block;
        width: 100%;
        padding: 0.625rem 1rem; /* py-2.5 px-4 */
        font-size: 1rem;
        line-height: 1.5;
        color: #495057;
        background-color: #fff;
        background-clip: padding-box;
        border: 1px solid #d2d6da; /* border-gray-300 */
        border-radius: 0.375rem; /* rounded-md */
        transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
    }
    .form-control:focus {
        border-color: #818cf8; /* indigo-300 */
        outline: 0;
        box-shadow: 0 0 0 0.2rem rgba(99, 102, 241, 0.25); /* ring-indigo-200 */
    }
    textarea.form-control {
        min-height: 80px;
    }
    select.form-control {
        padding-right: 2.5rem; /* For dropdown arrow */
    }

    .btn-success {
        background-color: #10b981; /* bg-green-500 */
        color: #ffffff;
        border: none;
        padding: 0.75rem 1.5rem;
        border-radius: 0.375rem;
        font-weight: 600;
        transition: background-color 0.2s ease-in-out, transform 0.1s ease-in-out;
    }
    .btn-success:hover {
        background-color: #059669; /* bg-green-600 */
        transform: translateY(-1px);
    }
    .btn-primary {
        background-color: #3b82f6; /* bg-blue-500 */
        color: #ffffff;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 0.375rem;
        font-weight: 500;
        transition: background-color 0.2s ease-in-out;
    }
    .btn-primary:hover {
        background-color: #2563eb; /* bg-blue-600 */
    }

    .error {
        background-color: #fee2e2; /* bg-red-100 */
        color: #dc2626; /* text-red-700 */
        padding: 1rem;
        border-radius: 0.5rem;
        margin-bottom: 1.5rem;
        border: 1px solid #ef4444; /* border-red-500 */
    }
    .success {
        background-color: #d1fae5; /* bg-green-100 */
        color: #065f46; /* text-green-700 */
        padding: 1rem;
        border-radius: 0.5rem;
        margin-bottom: 1.5rem;
        border: 1px solid #10b981; /* border-green-500 */
 }
    .seo-info {
        font-size: 1.25rem; /* text-xl */
        font-weight: 700; /* font-bold */
        color: #1f2937; /* text-gray-900 */
        margin-bottom: 1rem;
        border-bottom: 2px solid #e5e7eb; /* border-gray-200 */
        padding-bottom: 0.5rem;
    }
    .existing-photo {
        max-width: 150px;
        height: auto;
        border-radius: 0.25rem;
        margin-bottom: 0.5rem;
        border: 1px solid #e5e7eb;
    }
    .help-block {
        font-size: 0.875rem;
        color: #6b7280;
        margin-top: 0.25rem;
    }
    .checkbox label {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        cursor: pointer;
        font-weight: normal;
        color: #374151;
    }
    .checkbox input[type="checkbox"] {
        width: 1.25rem;
        height: 1.25rem;
        border-radius: 0.25rem;
        border: 1px solid #d2d6da;
        accent-color: #4f46e5; /* For checked state */
    }
</style>

<section class="content-header p-6 bg-white shadow-sm rounded-lg mb-6">
    <div class="flex justify-between items-center">
        <h1 class="text-2xl font-bold text-gray-800">Website Settings</h1>
    </div>
</section>

<section class="content p-6">
    <div class="row">
        <div class="col-md-12">
            <?php if($error_message): ?>
            <div class="error">
                <p><?php echo $error_message; ?></p>
            </div>
            <?php endif; ?>

            <?php if($success_message): ?>
            <div class="success">
                <p><?php echo $success_message; ?></p>
            </div>
            <?php endif; ?>

            <form class="form-horizontal" action="" method="post" enctype="multipart/form-data">
                <div class="nav-tabs-custom bg-white shadow-lg rounded-lg">
                    <ul class="nav nav-tabs px-4 pt-4">
                        <li class="active"><a href="#tab_general" data-toggle="tab">General</a></li>
                        <li><a href="#tab_home_features" data-toggle="tab">Home Features</a></li>
                        <li><a href="#tab_payment_gateways" data-toggle="tab">Payment Gateways</a></li>
                        <li><a href="#tab_api_integrations" data-toggle="tab">API Integrations</a></li>
                        <li><a href="#tab_review_delivery" data-toggle="tab">Review & Delivery</a></li>
                        <li><a href="#tab_sms" data-toggle="tab">SMS</a></li>
                        <li><a href="#tab_banners" data-toggle="tab">Banners</a></li>
                        <li><a href="#tab_social_media" data-toggle="tab">Social Media</a></li>
                        <li><a href="#tab_email" data-toggle="tab">Email</a></li>
                        <li><a href="#tab_footer" data-toggle="tab">Footer</a></li>
                        <li><a href="#tab_ads" data-toggle="tab">Ads</a></li>
                        <li><a href="#tab_blog_posts" data-toggle="tab">Blog/Post Counts</a></li>
                    </ul>

                    <div class="tab-content">
                        <!-- Tab 1: General Settings -->
                        <div class="tab-pane active" id="tab_general">
                            <div class="box box-info">
                                <div class="box-body">
                                    <div class="form-group">
                                        <label for="photo_logo" class="col-sm-3 control-label">Website Logo</label>
                                        <div class="col-sm-9">
                                            <?php if (!empty($logo) && file_exists('../assets/uploads/'.$logo)): ?>
                                                <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($logo); ?>" alt="Logo" class="existing-photo"><br>
                                            <?php else: ?>
                                                <p class="text-gray-500">No logo uploaded.</p>
                                            <?php endif; ?>
                                            <input type="file" name="photo_logo" id="photo_logo" class="form-control-file">
                                            <p class="help-block">Upload a new logo (JPG, PNG, JPEG, GIF)</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="photo_favicon" class="col-sm-3 control-label">Website Favicon</label>
                                        <div class="col-sm-9">
                                            <?php if (!empty($favicon) && file_exists('../assets/uploads/'.$favicon)): ?>
                                                <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($favicon); ?>" alt="Favicon" class="existing-photo" style="width:50px; height:50px;"><br>
                                            <?php else: ?>
                                                <p class="text-gray-500">No favicon uploaded.</p>
                                            <?php endif; ?>
                                            <input type="file" name="photo_favicon" id="photo_favicon" class="form-control-file">
                                            <p class="help-block">Upload a new favicon (JPG, PNG, JPEG, GIF)</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="contact_email" class="col-sm-3 control-label">Contact Email</label>
                                        <div class="col-sm-9">
                                            <input type="email" name="contact_email" id="contact_email" class="form-control" value="<?php echo htmlspecialchars($contact_email); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="contact_phone" class="col-sm-3 control-label">Contact Phone</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="contact_phone" id="contact_phone" class="form-control" value="<?php echo htmlspecialchars($contact_phone); ?>">
                                        </div>
                                    </div>
                                    <h3 class="seo-info mt-8">SEO & Script Settings</h3>
                                    <div class="form-group">
                                        <label for="meta_title_home" class="col-sm-3 control-label">Meta Title (Home)</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="meta_title_home" id="meta_title_home" class="form-control" value="<?php echo htmlspecialchars($meta_title_home); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="meta_keyword_home" class="col-sm-3 control-label">Meta Keyword (Home)</label>
                                        <div class="col-sm-9">
                                            <textarea name="meta_keyword_home" id="meta_keyword_home" class="form-control" rows="5"><?php echo htmlspecialchars($meta_keyword_home); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="meta_description_home" class="col-sm-3 control-label">Meta Description (Home)</label>
                                        <div class="col-sm-9">
                                            <textarea name="meta_description_home" id="meta_description_home" class="form-control" rows="5"><?php echo htmlspecialchars($meta_description_home); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="before_head" class="col-sm-3 control-label">Code before &lt;/head&gt; tag</label>
                                        <div class="col-sm-9">
                                            <textarea name="before_head" id="before_head" class="form-control" rows="5"><?php echo htmlspecialchars($before_head); ?></textarea>
                                            <p class="help-block">e.g., Google Analytics, custom CSS</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="after_body" class="col-sm-3 control-label">Code after &lt;body&gt; tag</label>
                                        <div class="col-sm-9">
                                            <textarea name="after_body" id="after_body" class="form-control" rows="5"><?php echo htmlspecialchars($after_body); ?></textarea>
                                            <p class="help-block">e.g., Google Tag Manager, custom JS</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="before_body" class="col-sm-3 control-label">Code before &lt;/body&gt; tag</label>
                                        <div class="col-sm-9">
                                            <textarea name="before_body" id="before_body" class="form-control" rows="5"><?php echo htmlspecialchars($before_body); ?></textarea>
                                            <p class="help-block">e.g., Live chat scripts</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="" class="col-sm-3 control-label">Base URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="base_url" class="form-control" value="<?php echo BASE_URL; ?>">
                     
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="hide_banner_desktop" class="col-sm-3 control-label">Hide Page Banner on Desktop</label>
                                        <div class="col-sm-9">
                                            <label class="checkbox">
                                                <input type="checkbox" name="hide_banner_desktop" id="hide_banner_desktop" value="1" <?php if($hide_banner_desktop) echo 'checked'; ?>>
                                                Hide banner on desktop (visible on mobile only)
                                            </label>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="hide_banner_mobile" class="col-sm-3 control-label">Hide Page Banner on Mobile</label>
                                        <div class="col-sm-9">
                                            <label class="checkbox">
                                                <input type="checkbox" name="hide_banner_mobile" id="hide_banner_mobile" value="1" <?php if($hide_banner_mobile) echo 'checked'; ?>>
                                                Hide banner on mobile (visible on desktop only)
                                            </label>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="hide_free_delivery_desktop" class="col-sm-3 control-label">Hide Free Delivery on Desktop</label>
                                        <div class="col-sm-9">
                                            <label class="checkbox">
                                                <input type="checkbox" name="hide_free_delivery_desktop" id="hide_free_delivery_desktop" value="1" <?php if($hide_free_delivery_desktop) echo 'checked'; ?>>
                                                Hide 'Free Delivery' section on desktop (show on mobile only)
                                            </label>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="hide_free_delivery_mobile" class="col-sm-3 control-label">Hide Free Delivery on Mobile</label>
                                        <div class="col-sm-9">
                                            <label class="checkbox">
                                                <input type="checkbox" name="hide_free_delivery_mobile" id="hide_free_delivery_mobile" value="1" <?php if($hide_free_delivery_mobile) echo 'checked'; ?>>
                                                Hide 'Free Delivery' section on mobile (show on desktop only)
                                            </label>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_general_settings">Update General Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                      <div class="tab-pane" id="tab_home_features">
                            <form class="form-horizontal" action="" method="post" enctype="multipart/form-data">
                            <div class="box box-info">
                                <div class="box-body">

                                    <h3 class="seo-info" style="color:#3c8dbc; margin-bottom: 20px;">1. Homepage Layout Manager</h3>
                                    <p style="margin-bottom: 25px; color:#666;">Control which sections appear and rearrange their order (1 = Top, 10 = Bottom).</p>

                                    <div class="form-group" style="background:#f9f9f9; padding:15px 0; border:1px solid #eee;">
                                        <label class="col-sm-3 control-label">Home Slider</label>
                                        <div class="col-sm-3">
                                            <select name="home_slider_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_slider_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_slider_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_slider_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_slider_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 1)</p></div>
                                    </div>

                                    <div class="form-group" style="padding:15px 0;">
                                        <label class="col-sm-3 control-label">Feature Icons</label>
                                        <div class="col-sm-3">
                                            <select name="home_features_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_features_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_features_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_features_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_features_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 2)</p></div>
                                    </div>

                                    <div class="form-group" style="background:#f9f9f9; padding:15px 0; border:1px solid #eee;">
                                        <label class="col-sm-3 control-label">Browse Categories</label>
                                        <div class="col-sm-3">
                                            <select name="home_category_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_category_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_category_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_category_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_category_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 3)</p></div>
                                    </div>

                                    <div class="form-group" style="padding:15px 0;">
                                        <label class="col-sm-3 control-label">Flash Sale Section</label>
                                        <div class="col-sm-3">
                                             <input type="text" class="form-control" value="Auto-Hides if Empty" disabled style="background:#fff;">
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_flash_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_flash_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 4)</p></div>
                                    </div>

                                    <div class="form-group" style="padding:15px 0;">
                                        <label class="col-sm-3 control-label">Flash Sale End Time</label>
                                        <div class="col-sm-6">
                                            <input type="datetime-local" name="flash_sale_end_time" class="form-control" value="<?php echo htmlspecialchars($flash_sale_end_time); ?>">
                                            <p style="padding-top:7px; color:#888;">Set the end time for the current flash sale (server time).</p>
                                        </div>
                                    </div>

                                    <div class="form-group" style="background:#f9f9f9; padding:15px 0; border:1px solid #eee;">
                                        <label class="col-sm-3 control-label">Featured Products</label>
                                        <div class="col-sm-3">
                                            <select name="home_featured_product_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_featured_product_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_featured_product_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_featured_product_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_featured_product_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 5)</p></div>
                                    </div>

                                    <div class="form-group" style="padding:15px 0;">
                                        <label class="col-sm-3 control-label">Latest Products</label>
                                        <div class="col-sm-3">
                                            <select name="home_latest_product_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_latest_product_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_latest_product_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_latest_product_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_latest_product_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 6)</p></div>
                                    </div>

                                    <div class="form-group" style="background:#f9f9f9; padding:15px 0; border:1px solid #eee;">
                                        <label class="col-sm-3 control-label">Popular Products</label>
                                        <div class="col-sm-3">
                                            <select name="home_popular_product_on_off" class="form-control">
                                                <option value="1" <?php if($settings_data['home_popular_product_on_off'] == 1) {echo 'selected';} ?>>Show</option>
                                                <option value="0" <?php if($settings_data['home_popular_product_on_off'] == 0) {echo 'selected';} ?>>Hide</option>
                                            </select>
                                        </div>
                                        <div class="col-sm-2">
                                            <input type="number" name="home_popular_product_order" class="form-control" placeholder="Order #" value="<?php echo $settings_data['home_popular_product_order']; ?>">
                                        </div>
                                        <div class="col-sm-4"><p style="padding-top:7px; color:#888;">(Default Order: 7)</p></div>
                                    </div>


                                    <hr style="border-top: 2px solid #ccc; margin-top:40px; margin-bottom:40px;">
                                    <h3 class="seo-info" style="color:#3c8dbc; margin-bottom: 20px;">2. Section Content & Titles</h3>

                                    <h4 style="margin-top:30px; border-bottom:1px solid #ddd; padding-bottom:10px;">Call To Action (Welcome) Area</h4>
                                    <div class="form-group">
                                        <label for="cta_title" class="col-sm-3 control-label">Title</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="cta_title" class="form-control" value="<?php echo htmlspecialchars($cta_title); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="cta_content" class="col-sm-3 control-label">Content</label>
                                        <div class="col-sm-9">
                                            <textarea name="cta_content" class="form-control" rows="5"><?php echo htmlspecialchars($cta_content); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="cta_read_more_text" class="col-sm-3 control-label">Button Text</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="cta_read_more_text" class="form-control" value="<?php echo htmlspecialchars($cta_read_more_text); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="cta_read_more_url" class="col-sm-3 control-label">Button URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="cta_read_more_url" class="form-control" value="<?php echo htmlspecialchars($cta_read_more_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="cta_photo" class="col-sm-3 control-label">Background Photo</label>
                                        <div class="col-sm-9">
                                            <?php if (!empty($cta_photo)): ?>
                                                <img src="../assets/uploads/<?php echo $cta_photo; ?>" style="width:150px; margin-bottom:10px;"><br>
                                            <?php endif; ?>
                                            <input type="file" name="cta_photo">
                                        </div>
                                    </div>
                                    
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Show Welcome Section?</label>
                                        <div class="col-sm-9">
                                            <select name="home_welcome_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($home_welcome_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($home_welcome_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>


                                    <h4 style="margin-top:30px; border-bottom:1px solid #ddd; padding-bottom:10px;">Product Section Headers</h4>
                                    
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Featured Title</label>
                                        <div class="col-sm-9"><input type="text" name="featured_product_title" class="form-control" value="<?php echo htmlspecialchars($featured_product_title); ?>"></div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Featured Subtitle</label>
                                        <div class="col-sm-9"><input type="text" name="featured_product_subtitle" class="form-control" value="<?php echo htmlspecialchars($featured_product_subtitle); ?>"></div>
                                    </div>

                                    <div class="form-group" style="margin-top:15px;">
                                        <label class="col-sm-3 control-label">Latest Title</label>
                                        <div class="col-sm-9"><input type="text" name="latest_product_title" class="form-control" value="<?php echo htmlspecialchars($latest_product_title); ?>"></div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Latest Subtitle</label>
                                        <div class="col-sm-9"><input type="text" name="latest_product_subtitle" class="form-control" value="<?php echo htmlspecialchars($latest_product_subtitle); ?>"></div>
                                    </div>

                                    <div class="form-group" style="margin-top:15px;">
                                        <label class="col-sm-3 control-label">Popular Title</label>
                                        <div class="col-sm-9"><input type="text" name="popular_product_title" class="form-control" value="<?php echo htmlspecialchars($popular_product_title); ?>"></div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Popular Subtitle</label>
                                        <div class="col-sm-9"><input type="text" name="popular_product_subtitle" class="form-control" value="<?php echo htmlspecialchars($popular_product_subtitle); ?>"></div>
                                    </div>


                                    <h4 style="margin-top:30px; border-bottom:1px solid #ddd; padding-bottom:10px;">Other Sections Visibility</h4>
                                    
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Testimonial Section</label>
                                        <div class="col-sm-9">
                                            <select name="home_testimonial_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($home_testimonial_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($home_testimonial_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Blog Section</label>
                                        <div class="col-sm-9">
                                            <select name="home_blog_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($home_blog_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($home_blog_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Newsletter Section</label>
                                        <div class="col-sm-9">
                                            <select name="home_newsletter_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($home_newsletter_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($home_newsletter_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Brands Section</label>
                                        <div class="col-sm-9">
                                            <select name="home_brand_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($home_brand_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($home_brand_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
<hr>
<h3 class="seo-info" style="color:#3c8dbc;">3. New UI & Effect Settings</h3>

<div class="form-group">
    <label class="col-sm-3 control-label">Category Section Background</label>
    <div class="col-sm-3">
        <input type="color" name="bg_color_categories" class="form-control" value="<?php echo $settings_data['bg_color_categories'] ?? '#ffffff'; ?>">
    </div>
</div>

<div class="form-group">
    <label class="col-sm-3 control-label">Latest Products Background</label>
    <div class="col-sm-3">
        <input type="color" name="bg_color_latest_products" class="form-control" value="<?php echo $settings_data['bg_color_latest_products'] ?? '#ffffff'; ?>">
    </div>
</div>

<div class="form-group">
    <label class="col-sm-3 control-label">Featured Products Background</label>
    <div class="col-sm-3">
        <input type="color" name="bg_color_featured_products" class="form-control" value="<?php echo htmlspecialchars($settings_data['bg_color_featured_products'] ?? '#ffffff'); ?>">
    </div>
</div>

<div class="form-group">
    <label class="col-sm-3 control-label">Scroll To Top Button</label>
    <div class="col-sm-3">
        <select name="show_scroll_top_btn" class="form-control">
            <option value="1" <?php if(($settings_data['show_scroll_top_btn']??0) == 1) echo 'selected'; ?>>Enable</option>
            <option value="0" <?php if(($settings_data['show_scroll_top_btn']??0) == 0) echo 'selected'; ?>>Disable</option>
        </select>
    </div>
</div>

<div class="form-group">
    <label class="col-sm-3 control-label">Extra Footer Links (Desktop)</label>
    <div class="col-sm-3">
        <select name="extra_footer_section_enable" class="form-control">
            <option value="1" <?php if(($settings_data['extra_footer_section_enable']??0) == 1) echo 'selected'; ?>>Enable</option>
            <option value="0" <?php if(($settings_data['extra_footer_section_enable']??0) == 0) echo 'selected'; ?>>Disable</option>
        </select>
    </div>
</div>

<hr>
<h3 class="seo-info" style="color:#3c8dbc;">4. Slider Side Banner</h3>
<div class="form-group">
    <label class="col-sm-3 control-label">Side Banner Image</label>
    <div class="col-sm-9">
        <?php if(!empty($settings_data['slider_side_banner_img'])): ?>
            <img src="../assets/uploads/<?php echo $settings_data['slider_side_banner_img']; ?>" style="width:150px;"><br>
        <?php endif; ?>
        <input type="file" name="slider_side_banner_img">
    </div>
</div>
<div class="form-group">
    <label class="col-sm-3 control-label">Side Banner Text (HTML)</label>
    <div class="col-sm-9">
        <textarea name="slider_side_banner_text" class="form-control" rows="3"><?php echo htmlspecialchars($settings_data['slider_side_banner_text'] ?? ''); ?></textarea>
    </div>
</div>

                         <div class="form-group">
                                        <label class="col-sm-3 control-label">Multi-Vendor System</label>
                                        <div class="col-sm-4">
                                            <select name="multi_vendor_on_off" class="form-control">
                                                <option value="1" <?php if($multi_vendor_on_off == 1) echo 'selected'; ?>>On (Enable Shop & Merchant Buttons)</option>
                                                <option value="0" <?php if($multi_vendor_on_off == 0) echo 'selected'; ?>>Off (Disable Merchant Features)</option>
                                            </select>
                                        </div>
                                    </div>
                         <input type="hidden" name="multi_vendor_on_off" value="0">
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Coin Payment System</label>
                                        <div class="col-sm-4">
                                            <select name="coin_payment_on_off" class="form-control">
                                                <option value="1" <?php if($coin_payment_on_off == 1) echo 'selected'; ?>>On (Show Coin Payment Button)</option>
                                                <option value="0" <?php if($coin_payment_on_off == 0) echo 'selected'; ?>>Off (Hide Coin Payment)</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Desktop Advanced Layout</label>
                                        <div class="col-sm-4">
                                            <select name="desktop_advanced_layout_on_off" class="form-control">
                                                <option value="1" <?php if($desktop_advanced_layout_on_off == 1) echo 'selected'; ?>>On (Decorative Sticky Grid)</option>
                                                <option value="0" <?php if($desktop_advanced_layout_on_off == 0) echo 'selected'; ?>>Off (Standard Layout)</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label"></label>
                                        <div class="col-sm-6">
                                            <button type="submit" class="btn btn-success" name="form_feature_settings">Update Features</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </div>

                    <!-- TAB: GENERAL SETTINGS -->
                    <div class="tab-pane" id="tab_general">
                        <form class="form-horizontal" action="" method="post">
                            <div class="box box-info">
                                <div class="box-body">
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Contact Email</label>
                                        <div class="col-sm-6">
                                            <input type="text" class="form-control" name="contact_email" value="<?php echo $contact_email; ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label class="col-sm-3 control-label">Contact Phone</label>
                                        <div class="col-sm-6">
                                            <input type="text" class="form-control" name="contact_phone" value="<?php echo $contact_phone; ?>">
                                        </div>
                                    </div>
                        <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9" style="margin-top:20px;">
                                            <button type="submit" class="btn btn-success btn-lg" name="form_home_features">Update Settings</button>
                                        </div>
                                    </div>

                                </div>
                            </div>
                            </form>
                        </div>
                        <!-- Tab 3: Payment Gateways -->
                        <div class="tab-pane" id="tab_payment_gateways">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Enabled Payment Methods</h3>
                                    <div class="form-group">
                                        <label for="" class="col-sm-3 control-label">Select Methods</label>
                                        <div class="col-sm-9">
                                            <?php
                                            $all_methods = ['Stripe', 'PayPal', 'Bank Deposit', 'Cash on Delivery', 'SSLCommerz'];
                                            foreach ($all_methods as $method) {
                                                $checked = in_array($method, $enabled_payment_methods_array) ? 'checked' : '';
                                                echo '<div class="checkbox">';
                                                echo '<label>';
                                                echo '<input type="checkbox" name="payment_methods[]" value="'.$method.'" '.$checked.'> '.$method;
                                                echo '</label>';
                                                echo '</div>';
                                            }
                                            ?>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Stripe Settings</h3>
                                    <div class="form-group">
                                        <label for="stripe_public_key" class="col-sm-3 control-label">Stripe Public Key</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="stripe_public_key" id="stripe_public_key" class="form-control" value="<?php echo htmlspecialchars($stripe_public_key); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="stripe_secret_key" class="col-sm-3 control-label">Stripe Secret Key</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="stripe_secret_key" id="stripe_secret_key" class="form-control" value="<?php echo htmlspecialchars($stripe_secret_key); ?>">
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">PayPal Settings</h3>
                                    <div class="form-group">
                                        <label for="paypal_email" class="col-sm-3 control-label">PayPal Business Email</label>
                                        <div class="col-sm-9">
                                            <input type="email" name="paypal_email" id="paypal_email" class="form-control" value="<?php echo htmlspecialchars($paypal_email); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="paypal_client_id" class="col-sm-3 control-label">PayPal Client ID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="paypal_client_id" id="paypal_client_id" class="form-control" value="<?php echo htmlspecialchars($paypal_client_id); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="paypal_secret" class="col-sm-3 control-label">PayPal Secret</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="paypal_secret" id="paypal_secret" class="form-control" value="<?php echo htmlspecialchars($paypal_secret); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="paypal_sandbox_mode" class="col-sm-3 control-label">PayPal Sandbox Mode</label>
                                        <div class="col-sm-9">
                                            <select name="paypal_sandbox_mode" id="paypal_sandbox_mode" class="form-control w-auto">
                                                <option value="1" <?php if($paypal_sandbox_mode == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($paypal_sandbox_mode == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">SSLCommerz Settings</h3>
                                    <div class="form-group">
                                        <label for="sslcz_store_id" class="col-sm-3 control-label">SSLCommerz Store ID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="sslcz_store_id" id="sslcz_store_id" class="form-control" value="<?php echo htmlspecialchars($sslcz_store_id); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="sslcz_store_pass" class="col-sm-3 control-label">SSLCommerz Store Password</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="sslcz_store_pass" id="sslcz_store_pass" class="form-control" value="<?php echo htmlspecialchars($sslcz_store_pass); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="sslcz_mode" class="col-sm-3 control-label">SSLCommerz Mode</label>
                                        <div class="col-sm-9">
                                            <select name="sslcz_mode" id="sslcz_mode" class="form-control w-auto">
                                                <option value="sandbox" <?php if($sslcz_mode === 'sandbox') {echo 'selected';} ?>>Sandbox</option>
                                                <option value="live" <?php if($sslcz_mode === 'live') {echo 'selected';} ?>>Live</option>
                                            </select>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Cash on Delivery (COD)</h3>
                                    <div class="form-group">
                                        <label for="cod_enabled" class="col-sm-3 control-label">Enable COD?</label>
                                        <div class="col-sm-9">
                                            <select name="cod_enabled" id="cod_enabled" class="form-control w-auto">
                                                <option value="1" <?php if($cod_enabled == 1) {echo 'selected';} ?>>Yes</option>
                                                <option value="0" <?php if($cod_enabled == 0) {echo 'selected';} ?>>No</option>
                                            </select>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Bank Deposit Information</h3>
                                    <div class="form-group">
                                        <label for="bank_detail" class="col-sm-3 control-label">Bank Information</label>
                                        <div class="col-sm-9">
                                            <textarea name="bank_detail" id="bank_detail" class="form-control" rows="5"><?php echo htmlspecialchars($bank_detail); ?></textarea>
                                            <p class="help-block">Provide bank account details for direct transfers.</p>
                                        </div>
                                    </div>

                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_payment_gateways">Update Payment Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 4: API Integrations -->
                        <div class="tab-pane" id="tab_api_integrations">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Google Gemini API (for AI Chat)</h3>
                                    <div class="form-group">
                                        <label for="gemini_api_key" class="col-sm-3 control-label">Gemini API Key</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="gemini_api_key" id="gemini_api_key" class="form-control" value="<?php echo htmlspecialchars($gemini_api_key); ?>">
                                            <p class="help-block">Get your API key from Google AI Studio for product inquiries.</p>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Facebook Login API</h3>
                                    <div class="form-group">
                                        <label for="facebook_app_id" class="col-sm-3 control-label">Facebook App ID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="facebook_app_id" id="facebook_app_id" class="form-control" value="<?php echo htmlspecialchars($facebook_app_id); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="facebook_app_secret" class="col-sm-3 control-label">Facebook App Secret</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="facebook_app_secret" id="facebook_app_secret" class="form-control" value="<?php echo htmlspecialchars($facebook_app_secret); ?>">
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Google Sign-in API</h3>
                                    <div class="form-group">
                                        <label for="google_client_id" class="col-sm-3 control-label">Google Client ID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="google_client_id" id="google_client_id" class="form-control" value="<?php echo htmlspecialchars($google_client_id); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="google_client_secret" class="col-sm-3 control-label">Google Client Secret</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="google_client_secret" id="google_client_secret" class="form-control" value="<?php echo htmlspecialchars($google_client_secret); ?>">
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Twilio API (for SMS Notifications)</h3>
                                    <div class="form-group">
                                        <label for="twilio_account_sid" class="col-sm-3 control-label">Twilio Account SID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="twilio_account_sid" id="twilio_account_sid" class="form-control" value="<?php echo htmlspecialchars($twilio_account_sid); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="twilio_auth_token" class="col-sm-3 control-label">Twilio Auth Token</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="twilio_auth_token" id="twilio_auth_token" class="form-control" value="<?php echo htmlspecialchars($twilio_auth_token); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="twilio_phone_number" class="col-sm-3 control-label">Twilio Phone Number</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="twilio_phone_number" id="twilio_phone_number" class="form-control" value="<?php echo htmlspecialchars($twilio_phone_number); ?>">
                                            <p class="help-block">e.g., +1234567890 (Your Twilio number)</p>
                                        </div>
                                    </div>

                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_api_integrations">Update API Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 5: Review & Delivery Settings -->
                        <div class="tab-pane" id="tab_review_delivery">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Review Settings</h3>
                                    <div class="form-group">
                                        <label for="review_feature_on_off" class="col-sm-3 control-label">Enable Review Feature?</label>
                                        <div class="col-sm-9">
                                            <select name="review_feature_on_off" id="review_feature_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($review_feature_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($review_feature_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Estimated Delivery Times</h3>
                                    <div class="form-group">
                                        <label for="estimated_delivery_time_local" class="col-sm-3 control-label">Local Delivery Time</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="estimated_delivery_time_local" id="estimated_delivery_time_local" class="form-control" value="<?php echo htmlspecialchars($estimated_delivery_time_local); ?>">
                                            <p class="help-block">e.g., 3-5 business days, 24-48 hours</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="estimated_delivery_time_international" class="col-sm-3 control-label">International Delivery Time</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="estimated_delivery_time_international" id="estimated_delivery_time_international" class="form-control" value="<?php echo htmlspecialchars($estimated_delivery_time_international); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_review_delivery_settings">Update Review & Delivery Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 6: SMS Settings -->
                        <div class="tab-pane" id="tab_sms">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">SMS Settings (e.g., BulkSMSBD.net)</h3>
                                    <div class="form-group">
                                        <label for="sms_feature_on_off" class="col-sm-3 control-label">Enable SMS Feature?</label>
                                        <div class="col-sm-9">
                                            <select name="sms_feature_on_off" id="sms_feature_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($sms_feature_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($sms_feature_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="sms_api_key" class="col-sm-3 control-label">SMS API Key</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="sms_api_key" id="sms_api_key" class="form-control" value="<?php echo htmlspecialchars($sms_api_key); ?>">
                                            <p class="help-block">Your API key from your SMS gateway provider.</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="sms_sender_id" class="col-sm-3 control-label">SMS Sender ID</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="sms_sender_id" id="sms_sender_id" class="form-control" value="<?php echo htmlspecialchars($sms_sender_id); ?>">
                                            <p class="help-block">Your Sender ID (e.g., your brand name, usually 11 characters).</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_sms_settings">Update SMS Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 7: Banner Settings -->
                        <div class="tab-pane" id="tab_banners">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Page Banners</h3>
                                    <?php
                                    $banner_fields_map = [
                                        'banner_login' => 'Login Page Banner',
                                        'banner_registration' => 'Registration Page Banner',
                                        'banner_forget_password' => 'Forget Password Page Banner',
                                        'banner_reset_password' => 'Reset Password Page Banner',
                                        'banner_search' => 'Search Result Page Banner',
                                        'banner_cart' => 'Cart Page Banner',
                                        'banner_checkout' => 'Checkout Page Banner',
                                        'banner_product_category' => 'Product Category Page Banner',
                                        'banner_blog' => 'Blog Page Banner',
                                        'banner_faq' => 'FAQ Page Banner',
                                        'banner_contact' => 'Contact Page Banner',
                                        'banner_payment' => 'Payment Page Banner',
                                        'banner_customer_panel' => 'Customer Panel Banner',
                                        'banner_about' => 'About Us Page Banner',
                                        'banner_terms' => 'Terms & Conditions Page Banner',
                                        'banner_privacy' => 'Privacy Policy Page Banner',
                                        'banner_shipping' => 'Shipping Policy Page Banner',
                                        'banner_return_policy' => 'Return Policy Page Banner',
                                        'banner_photo_gallery' => 'Photo Gallery Page Banner',
                                        'banner_team' => 'Team Page Banner',
                                    ];

                                    foreach ($banner_fields_map as $field_name => $label): ?>
                                        <div class="form-group">
                                            <label for="<?php echo $field_name; ?>" class="col-sm-3 control-label">Existing <?php echo $label; ?></label>
                                            <div class="col-sm-9">
                                                <?php if (!empty($settings_data[$field_name]) && file_exists('../assets/uploads/'.$settings_data[$field_name])): ?>
                                                    <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($settings_data[$field_name]); ?>" alt="<?php echo $label; ?>" class="existing-photo"><br>
                                                <?php else: ?>
                                                    <p class="text-gray-500">No <?php echo strtolower($label); ?> uploaded.</p>
                                                <?php endif; ?>
                                                <input type="file" name="<?php echo $field_name; ?>" id="<?php echo $field_name; ?>" class="form-control-file">
                                                <p class="help-block">Upload a new banner (JPG, PNG, JPEG, GIF)</p>
                                            </div>
                                        </div>
                                    <?php endforeach; ?>

                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_banner_settings">Update Banner Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 8: Social Media -->
                        <div class="tab-pane" id="tab_social_media">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Social Media Links</h3>
                                    <div class="form-group">
                                        <label for="facebook_url" class="col-sm-3 control-label">Facebook URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="facebook_url" id="facebook_url" class="form-control" value="<?php echo htmlspecialchars($facebook_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="twitter_url" class="col-sm-3 control-label">Twitter URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="twitter_url" id="twitter_url" class="form-control" value="<?php echo htmlspecialchars($twitter_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="linkedin_url" class="col-sm-3 control-label">LinkedIn URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="linkedin_url" id="linkedin_url" class="form-control" value="<?php echo htmlspecialchars($linkedin_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="instagram_url" class="col-sm-3 control-label">Instagram URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="instagram_url" id="instagram_url" class="form-control" value="<?php echo htmlspecialchars($instagram_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="youtube_url" class="col-sm-3 control-label">YouTube URL</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="youtube_url" id="youtube_url" class="form-control" value="<?php echo htmlspecialchars($youtube_url); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_social_settings">Update Social Media Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 9: Email Settings -->
                        <div class="tab-pane" id="tab_email">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Outgoing Email Configuration</h3>
                                    <div class="form-group">
                                        <label for="smtp_from_name" class="col-sm-3 control-label">Email From Name</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="smtp_from_name" id="smtp_from_name" class="form-control" value="<?php echo htmlspecialchars($smtp_from_name); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_from_email" class="col-sm-3 control-label">Email From Email</label>
                                        <div class="col-sm-9">
                                            <input type="email" name="smtp_from_email" id="smtp_from_email" class="form-control" value="<?php echo htmlspecialchars($smtp_from_email); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="email_method" class="col-sm-3 control-label">Email Method</label>
                                        <div class="col-sm-9">
                                            <select name="email_method" id="email_method" class="form-control w-auto">
                                                <option value="PHP Mail" <?php if($email_method == 'PHP Mail') {echo 'selected';} ?>>PHP Mail</option>
                                                <option value="SMTP" <?php if($email_method == 'SMTP') {echo 'selected';} ?>>SMTP</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_host" class="col-sm-3 control-label">SMTP Host</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="smtp_host" id="smtp_host" class="form-control" value="<?php echo htmlspecialchars($smtp_host); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_port" class="col-sm-3 control-label">SMTP Port</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="smtp_port" id="smtp_port" class="form-control" value="<?php echo htmlspecialchars($smtp_port); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_username" class="col-sm-3 control-label">SMTP Username</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="smtp_username" id="smtp_username" class="form-control" value="<?php echo htmlspecialchars($smtp_username); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_password" class="col-sm-3 control-label">SMTP Password</label>
                                        <div class="col-sm-9">
                                            <input type="password" name="smtp_password" id="smtp_password" class="form-control" value="<?php echo htmlspecialchars($smtp_password); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="smtp_encryption" class="col-sm-3 control-label">SMTP Encryption</label>
                                        <div class="col-sm-9">
                                            <select name="smtp_encryption" id="smtp_encryption" class="form-control w-auto">
<option value="NONE" <?php if(strtoupper($smtp_encryption) == 'NONE') echo 'selected'; ?>>None</option>
<option value="TLS" <?php if(strtoupper($smtp_encryption) == 'TLS') echo 'selected'; ?>>TLS</option>
<option value="SSL" <?php if(strtoupper($smtp_encryption) == 'SSL') echo 'selected'; ?>>SSL</option>        </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_email_settings">Update Outgoing Email Settings</button>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Email Content Settings</h3>
                                    <div class="form-group">
                                        <label for="receive_email" class="col-sm-3 control-label">Contact Form Recipient Email</label>
                                        <div class="col-sm-9">
                                            <input type="email" class="form-control" name="receive_email" id="receive_email" value="<?php echo htmlspecialchars($receive_email); ?>">
                                            <p class="help-block">Email address where contact form submissions will be sent.</p>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="receive_email_subject" class="col-sm-3 control-label">Contact Email Subject</label>
                                        <div class="col-sm-9">
                                            <input type="text" class="form-control" name="receive_email_subject" id="receive_email_subject" value="<?php echo htmlspecialchars($receive_email_subject); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="receive_email_thank_you_message" class="col-sm-3 control-label">Contact Email Thank You Message</label>
                                        <div class="col-sm-9">
                                            <textarea class="form-control" name="receive_email_thank_you_message" id="receive_email_thank_you_message" rows="5"><?php echo htmlspecialchars($receive_email_thank_you_message); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="forget_password_message" class="col-sm-3 control-label">Forget Password Email Message</label>
                                        <div class="col-sm-9">
                                            <textarea class="form-control" name="forget_password_message" id="forget_password_message" rows="5"><?php echo htmlspecialchars($forget_password_message); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_email_content_settings">Update Email Content Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 10: Footer Settings -->
                        <div class="tab-pane" id="tab_footer">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Footer Content</h3>
                                    <div class="form-group">
                                        <label for="copyright_text" class="col-sm-3 control-label">Copyright Text</label>
                                        <div class="col-sm-9">
                                            <input type="text" name="copyright_text" id="copyright_text" class="form-control" value="<?php echo htmlspecialchars($copyright_text); ?>">
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="footer_about_us" class="col-sm-3 control-label">Footer About Us (Short)</label>
                                        <div class="col-sm-9">
                                            <textarea name="footer_about_us" id="footer_about_us" class="form-control" rows="5"><?php echo htmlspecialchars($footer_about_us); ?></textarea>
                                            <p class="help-block">A short description about your company for the footer.</p>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Contact Information in Footer</h3>
                                    <div class="form-group">
                                        <label for="contact_address" class="col-sm-3 control-label">Contact Address</label>
                                        <div class="col-sm-9">
                                            <textarea class="form-control" name="contact_address" id="contact_address" rows="5"><?php echo htmlspecialchars($contact_address); ?></textarea>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="contact_map_iframe" class="col-sm-3 control-label">Contact Map iFrame</label>
                                        <div class="col-sm-9">
                                            <textarea class="form-control" name="contact_map_iframe" id="contact_map_iframe" rows="5"><?php echo htmlspecialchars($contact_map_iframe); ?></textarea>
                                            <p class="help-block">Embed code for Google Map or similar.</p>
                                        </div>
                                    </div>

                                    <h3 class="seo-info mt-8">Payment Verified Image</h3>
                                    <div class="form-group">
                                        <label for="payment_verified_image" class="col-sm-3 control-label">Payment Methods Image</label>
                                        <div class="col-sm-9">
                                            <?php if (!empty($payment_verified_image) && file_exists('../assets/uploads/'.$payment_verified_image)): ?>
                                                <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo htmlspecialchars($payment_verified_image); ?>" alt="Payment Verified" class="existing-photo" style="max-width:200px;"><br>
                                            <?php else: ?>
                                                <p class="text-gray-500">No image uploaded.</p>
                                            <?php endif; ?>
                                            <input type="file" name="payment_verified_image" id="payment_verified_image" class="form-control-file">
                                            <p class="help-block">Upload an image showing accepted payment methods (JPG, PNG, JPEG, GIF)</p>
                                        </div>
                                    </div>

                                    <div class="form-group">
                                        <div class="col-sm-offset-3 col-sm-9">
                                            <button type="submit" class="btn btn-success" name="form_footer_settings">Update Footer Settings</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Tab 11: Ads Settings -->
                        <div class="tab-pane" id="tab_ads">
                            <div class="box box-info">
                                <div class="box-body">
                                    <h3 class="seo-info">Advertisement Section Visibility</h3>
                                    <div class="form-group">
                                        <label for="ads_above_welcome_on_off" class="col-sm-3 control-label">Above Welcome Section</label>
                                        <div class="col-sm-9">
                                            <select name="ads_above_welcome_on_off" id="ads_above_welcome_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_above_welcome_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_above_welcome_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="ads_above_featured_product_on_off" class="col-sm-3 control-label">Above Featured Product Section</label>
                                        <div class="col-sm-9">
                                            <select name="ads_above_featured_product_on_off" id="ads_above_featured_product_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_above_featured_product_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_above_featured_product_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="ads_above_latest_product_on_off" class="col-sm-3 control-label">Above Latest Product Section</label>
                                        <div class="col-sm-9">
                                            <select name="ads_above_latest_product_on_off" id="ads_above_latest_product_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_above_latest_product_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_above_latest_product_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="ads_above_popular_product_on_off" class="col-sm-3 control-label">Above Popular Product Section</label>
                                        <div class="col-sm-9">
                                            <select name="ads_above_popular_product_on_off" id="ads_above_popular_product_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_above_popular_product_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_above_popular_product_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="ads_above_testimonial_on_off" class="col-sm-3 control-label">Above Testimonial Section</label>
                                        <div class="col-sm-9">
                                            <select name="ads_above_testimonial_on_off" id="ads_above_testimonial_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_above_testimonial_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_above_testimonial_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="form-group">
                                        <label for="ads_category_sidebar_on_off" class="col-sm-3 control-label">Category Page Sidebar Ads</label>
                                        <div class="col-sm-9">
                                            <select name="ads_category_sidebar_on_off" id="ads_category_sidebar_on_off" class="form-control w-auto">
                                                <option value="1" <?php if($ads_category_sidebar_on_off == 1) {echo 'selected';} ?>>On</option>
                                                <option value="0" <?php if($ads_category_sidebar_on_off == 0) {echo 'selected';} ?>>Off</option>
                                            </select>
                                        </div>
                                    </div>                                    
                                    <div class="form-group">
                                        <label for="" class="col-sm-3 control-label"></label>
                                        <div class="col-sm-6">
                                            <button type="submit" class="btn btn-success pull-left" name="form_ads_settings">Update</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            </form>
                        </div>



                    </div>
                </div>

                

            </form>
        </div>
    </div>

</section>

<?php require_once('footer.php'); ?>
