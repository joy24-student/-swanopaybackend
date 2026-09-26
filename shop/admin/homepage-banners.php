<?php
require_once __DIR__ . '/inc/guard.php';
require_once __DIR__ . '/inc/supabase_storage.php';
require_once('header.php');

$success_message = '';
$error_message = '';

// Handle Slide Deletion
if (isset($_GET['action']) && $_GET['action'] === 'delete_slide' && !empty($_GET['slide_id'])) {
    $deleteId = (int)$_GET['slide_id'];
    $stmt = $pdo->prepare("DELETE FROM tbl_slider WHERE id = ?");
    $stmt->execute([$deleteId]);
    header("Location: homepage-banners.php?deleted=1");
    exit;
}

if (isset($_GET['deleted'])) {
    $success_message = 'Slide removed successfully!';
}

// Handle Form Submission
if (isset($_POST['form_banners'])) {
    if (!$csrf->checkToken()) {
        $error_message = 'Security token expired. Please try again.';
    } else {
        // 1. General & Hero Settings
        $hero_slider_autoplay = isset($_POST['hero_slider_autoplay']) ? 1 : 0;
        $hero_slider_interval = !empty($_POST['hero_slider_interval']) ? (int)$_POST['hero_slider_interval'] : 4500;
        $home_slider_on_off   = isset($_POST['home_slider_on_off']) ? (int)$_POST['home_slider_on_off'] : 1;
        $hero_tag             = trim($_POST['hero_tag'] ?? '');
        $hero_title           = trim($_POST['hero_title'] ?? '');
        $hero_subtitle        = trim($_POST['hero_subtitle'] ?? '');
        $hero_btn_text        = trim($_POST['hero_btn_text'] ?? '');
        $hero_btn_url         = trim($_POST['hero_btn_url'] ?? '');
        $hero_btn2_text       = trim($_POST['hero_btn2_text'] ?? '');
        $hero_btn2_url        = trim($_POST['hero_btn2_url'] ?? '');
        $hero_badge1_text     = trim($_POST['hero_badge1_text'] ?? '');
        $hero_badge2_text     = trim($_POST['hero_badge2_text'] ?? '');

        // 2. Categories Section
        $home_category_on_off = isset($_POST['home_category_on_off']) ? (int)$_POST['home_category_on_off'] : 1;
        $categories_title     = trim($_POST['categories_title'] ?? 'Shop by Category');
        $categories_subtitle  = trim($_POST['categories_subtitle'] ?? 'Explore our wide range of popular collections');

        // 3. Dual Promotional Banners
        $home_welcome_on_off  = isset($_POST['home_welcome_on_off']) ? (int)$_POST['home_welcome_on_off'] : 1;
        $promo1_tag           = trim($_POST['promo_banner1_tag'] ?? '');
        $promo1_title         = trim($_POST['promo_banner1_title'] ?? '');
        $promo1_subtitle      = trim($_POST['promo_banner1_subtitle'] ?? '');
        $promo1_btn_text      = trim($_POST['promo_banner1_btn_text'] ?? '');
        $promo1_btn_url       = trim($_POST['promo_banner1_btn_url'] ?? '');

        $promo2_tag           = trim($_POST['promo_banner2_tag'] ?? '');
        $promo2_title         = trim($_POST['promo_banner2_title'] ?? '');
        $promo2_subtitle      = trim($_POST['promo_banner2_subtitle'] ?? '');
        $promo2_btn_text      = trim($_POST['promo_banner2_btn_text'] ?? '');
        $promo2_btn_url       = trim($_POST['promo_banner2_btn_url'] ?? '');

        // 4. Featured Products Section
        $home_featured_product_on_off = isset($_POST['home_featured_product_on_off']) ? (int)$_POST['home_featured_product_on_off'] : 1;
        $featured_products_title      = trim($_POST['featured_products_title'] ?? 'Featured Products');
        $featured_products_subtitle   = trim($_POST['featured_products_subtitle'] ?? 'Handpicked best sellers and top rated products');
        $total_featured_product_home  = !empty($_POST['total_featured_product_home']) ? (int)$_POST['total_featured_product_home'] : 8;

        // 5. Trust Bar
        $home_service_on_off  = isset($_POST['home_service_on_off']) ? (int)$_POST['home_service_on_off'] : 1;
        $trust1_title         = trim($_POST['trust_item1_title'] ?? '');
        $trust1_desc          = trim($_POST['trust_item1_desc'] ?? '');
        $trust2_title         = trim($_POST['trust_item2_title'] ?? '');
        $trust2_desc          = trim($_POST['trust_item2_desc'] ?? '');
        $trust3_title         = trim($_POST['trust_item3_title'] ?? '');
        $trust3_desc          = trim($_POST['trust_item3_desc'] ?? '');
        $trust4_title         = trim($_POST['trust_item4_title'] ?? '');
        $trust4_desc          = trim($_POST['trust_item4_desc'] ?? '');

        // Fetch current image URLs from DB
        $currSettings = $pdo->query("SELECT promo_banner1_image, promo_banner2_image FROM tbl_settings WHERE id=1")->fetch(PDO::FETCH_ASSOC);
        $promo1_image = $currSettings['promo_banner1_image'] ?? '';
        $promo2_image = $currSettings['promo_banner2_image'] ?? '';

        // Supabase Upload for Promo Banner 1
        if (!empty($_FILES['promo1_image_file']['tmp_name']) && is_uploaded_file($_FILES['promo1_image_file']['tmp_name'])) {
            $ext = strtolower(pathinfo($_FILES['promo1_image_file']['name'], PATHINFO_EXTENSION));
            $newPromo1Url = uploadFileToSupabase($_FILES['promo1_image_file']['tmp_name'], 'promo1_' . time() . '.' . $ext);
            if ($newPromo1Url) {
                $promo1_image = $newPromo1Url;
            }
        } elseif (!empty($_POST['promo_banner1_image_url'])) {
            $promo1_image = trim($_POST['promo_banner1_image_url']);
        }

        // Supabase Upload for Promo Banner 2
        if (!empty($_FILES['promo2_image_file']['tmp_name']) && is_uploaded_file($_FILES['promo2_image_file']['tmp_name'])) {
            $ext = strtolower(pathinfo($_FILES['promo2_image_file']['name'], PATHINFO_EXTENSION));
            $newPromo2Url = uploadFileToSupabase($_FILES['promo2_image_file']['tmp_name'], 'promo2_' . time() . '.' . $ext);
            if ($newPromo2Url) {
                $promo2_image = $newPromo2Url;
            }
        } elseif (!empty($_POST['promo_banner2_image_url'])) {
            $promo2_image = trim($_POST['promo_banner2_image_url']);
        }

        // Update tbl_settings in Supabase
        $updateStmt = $pdo->prepare("UPDATE tbl_settings SET 
            home_slider_on_off = ?, hero_slider_autoplay = ?, hero_slider_interval = ?,
            hero_tag = ?, hero_title = ?, hero_subtitle = ?, hero_btn_text = ?, hero_btn_url = ?, 
            hero_btn2_text = ?, hero_btn2_url = ?, hero_badge1_text = ?, hero_badge2_text = ?,
            home_category_on_off = ?, categories_title = ?, categories_subtitle = ?,
            home_welcome_on_off = ?,
            promo_banner1_tag = ?, promo_banner1_title = ?, promo_banner1_subtitle = ?, promo_banner1_btn_text = ?, promo_banner1_btn_url = ?, promo_banner1_image = ?,
            promo_banner2_tag = ?, promo_banner2_title = ?, promo_banner2_subtitle = ?, promo_banner2_btn_text = ?, promo_banner2_btn_url = ?, promo_banner2_image = ?,
            home_featured_product_on_off = ?, featured_products_title = ?, featured_products_subtitle = ?, total_featured_product_home = ?,
            home_service_on_off = ?,
            trust_item1_title = ?, trust_item1_desc = ?,
            trust_item2_title = ?, trust_item2_desc = ?,
            trust_item3_title = ?, trust_item3_desc = ?,
            trust_item4_title = ?, trust_item4_desc = ?
            WHERE id = 1");

        $updateStmt->execute([
            $home_slider_on_off, $hero_slider_autoplay, $hero_slider_interval,
            $hero_tag, $hero_title, $hero_subtitle, $hero_btn_text, $hero_btn_url,
            $hero_btn2_text, $hero_btn2_url, $hero_badge1_text, $hero_badge2_text,
            $home_category_on_off, $categories_title, $categories_subtitle,
            $home_welcome_on_off,
            $promo1_tag, $promo1_title, $promo1_subtitle, $promo1_btn_text, $promo1_btn_url, $promo1_image,
            $promo2_tag, $promo2_title, $promo2_subtitle, $promo2_btn_text, $promo2_btn_url, $promo2_image,
            $home_featured_product_on_off, $featured_products_title, $featured_products_subtitle, $total_featured_product_home,
            $home_service_on_off,
            $trust1_title, $trust1_desc,
            $trust2_title, $trust2_desc,
            $trust3_title, $trust3_desc,
            $trust4_title, $trust4_desc
        ]);

        // Process Existing Slide Orders & Active Status
        if (!empty($_POST['slide_order']) && is_array($_POST['slide_order'])) {
            foreach ($_POST['slide_order'] as $slideId => $orderVal) {
                $slideId  = (int)$slideId;
                $orderVal = (int)$orderVal;
                $isActive = isset($_POST['slide_active'][$slideId]) ? 1 : 0;
                $pdo->prepare("UPDATE tbl_slider SET slide_order = ?, is_active = ? WHERE id = ?")->execute([$orderVal, $isActive, $slideId]);
            }
        }

        // Process MULTIPLE Image Uploads for Hero Slider to Supabase Bucket
        $uploadedCount = 0;
        if (!empty($_FILES['hero_slider_photos']['name']) && is_array($_FILES['hero_slider_photos']['name'])) {
            $maxOrder = (int)$pdo->query("SELECT COALESCE(MAX(slide_order), 0) FROM tbl_slider")->fetchColumn();
            foreach ($_FILES['hero_slider_photos']['name'] as $idx => $fileName) {
                if (!empty($fileName) && !empty($_FILES['hero_slider_photos']['tmp_name'][$idx])) {
                    $tmpName = $_FILES['hero_slider_photos']['tmp_name'][$idx];
                    $error   = $_FILES['hero_slider_photos']['error'][$idx];
                    if ($error === UPLOAD_ERR_OK && is_uploaded_file($tmpName)) {
                        $ext = strtolower(pathinfo($fileName, PATHINFO_EXTENSION));
                        if (in_array($ext, ['jpg', 'jpeg', 'png', 'webp', 'gif'])) {
                            $uniqueRemoteName = 'hero_slide_' . time() . '_' . ($idx + 1) . '_' . bin2hex(random_bytes(3)) . '.' . $ext;
                            $cloudUrl = uploadFileToSupabase($tmpName, $uniqueRemoteName, 'assets');
                            if ($cloudUrl) {
                                $maxOrder++;
                                $insertStmt = $pdo->prepare("INSERT INTO tbl_slider (photo, heading, content, button_text, button_url, position, slide_order, is_active) VALUES (?, '', '', '', '', 'Center', ?, 1)");
                                $insertStmt->execute([$cloudUrl, $maxOrder]);
                                $uploadedCount++;
                            }
                        }
                    }
                }
            }
        }

        // Invalidate settings cache
        @unlink(__DIR__ . '/inc/cache_settings.json');
        
        $msgParts = ['Homepage customization saved successfully!'];
        if ($uploadedCount > 0) {
            $msgParts[] = "{$uploadedCount} new hero slide(s) uploaded directly to Supabase Storage.";
        }
        $success_message = implode(' ', $msgParts);
    }
}

// Fetch Current Settings & Slides
$s = $pdo->query("SELECT * FROM tbl_settings WHERE id=1")->fetch(PDO::FETCH_ASSOC) ?: [];
$slides = $pdo->query("SELECT * FROM tbl_slider ORDER BY slide_order ASC, id ASC")->fetchAll(PDO::FETCH_ASSOC) ?: [];
?>

<style>
.customizer-card {
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.06);
    margin-bottom: 25px;
    border: 1px solid #e9ecef;
    overflow: hidden;
}
.customizer-header {
    padding: 16px 22px;
    background: #f8fafc;
    border-bottom: 1px solid #e2e8f0;
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.customizer-header h3 {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: #1e293b;
    display: flex;
    align-items: center;
    gap: 10px;
}
.customizer-body {
    padding: 24px;
}
.slide-item-card {
    display: flex;
    align-items: center;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 12px 16px;
    margin-bottom: 12px;
    transition: all 0.2s ease;
}
.slide-item-card:hover {
    background: #ffffff;
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    border-color: #cbd5e1;
}
.slide-thumb {
    width: 90px;
    height: 60px;
    object-fit: cover;
    border-radius: 6px;
    border: 1px solid #cbd5e1;
    margin-right: 18px;
    background: #fff;
}
.slide-meta {
    flex: 1;
}
.badge-supabase {
    background: #10b981;
    color: #fff;
    font-size: 11px;
    font-weight: 600;
    padding: 3px 8px;
    border-radius: 20px;
    letter-spacing: 0.3px;
    display: inline-block;
}
.drag-order-input {
    width: 70px;
    text-align: center;
    font-weight: 600;
}
.upload-dropzone {
    border: 2px dashed #93c5fd;
    background: #eff6ff;
    border-radius: 12px;
    padding: 24px;
    text-align: center;
    transition: all 0.2s ease;
    cursor: pointer;
}
.upload-dropzone:hover {
    background: #dbeafe;
    border-color: #3b82f6;
}
</style>

<section class="content-header">
    <h1>
        <i class="fa fa-sliders text-primary"></i> Homepage Customizer & Hero Slider
        <small>Comprehensive controllability for every section of your homepage</small>
    </h1>
    <ol class="breadcrumb">
        <li><a href="index.php"><i class="fa fa-dashboard"></i> Home</a></li>
        <li><a href="settings.php">Settings</a></li>
        <li class="active">Homepage Customizer</li>
    </ol>
</section>

<section class="content">
    <?php if ($error_message): ?>
        <div class="alert alert-danger alert-dismissible">
            <button type="button" class="close" data-dismiss="alert">&times;</button>
            <i class="fa fa-exclamation-triangle"></i> <?php echo htmlspecialchars($error_message); ?>
        </div>
    <?php endif; ?>
    <?php if ($success_message): ?>
        <div class="alert alert-success alert-dismissible">
            <button type="button" class="close" data-dismiss="alert">&times;</button>
            <i class="fa fa-check-circle"></i> <?php echo htmlspecialchars($success_message); ?>
        </div>
    <?php endif; ?>

    <form method="post" enctype="multipart/form-data">
        <?php $csrf->echoInputField(); ?>

        <!-- ============================================================
             1. HERO SLIDER & AUTO-SLIDING MULTIPLE IMAGE UPLOAD
             ============================================================ -->
        <div class="customizer-card">
            <div class="customizer-header" style="border-left: 4px solid #3b82f6;">
                <h3><i class="fa fa-picture-o text-primary"></i> 1. Hero Auto-Sliding Gallery & Multi-Upload</h3>
                <div>
                    <span class="badge-supabase"><i class="fa fa-cloud-upload"></i> Supabase Storage CDN</span>
                </div>
            </div>
            <div class="customizer-body">
                <div class="row">
                    <div class="col-md-4">
                        <div class="form-group">
                            <label>Hero Section Display</label>
                            <select name="home_slider_on_off" class="form-control">
                                <option value="1" <?php if(($s['home_slider_on_off'] ?? 1) == 1) echo 'selected'; ?>>Show Hero Section</option>
                                <option value="0" <?php if(($s['home_slider_on_off'] ?? 1) == 0) echo 'selected'; ?>>Hide Hero Section</option>
                            </select>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="form-group">
                            <label>Automatic Sliding (Autoplay)</label>
                            <select name="hero_slider_autoplay" class="form-control">
                                <option value="1" <?php if(($s['hero_slider_autoplay'] ?? 1) == 1) echo 'selected'; ?>>Enabled (Auto-slides automatically)</option>
                                <option value="0" <?php if(($s['hero_slider_autoplay'] ?? 1) == 0) echo 'selected'; ?>>Disabled (Manual slide only)</option>
                            </select>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="form-group">
                            <label>Slide Duration / Interval (Milliseconds)</label>
                            <div class="input-group">
                                <input type="number" name="hero_slider_interval" class="form-control" value="<?php echo htmlspecialchars($s['hero_slider_interval'] ?? 4500); ?>" min="1000" step="500">
                                <span class="input-group-addon">ms (e.g. 4500)</span>
                            </div>
                        </div>
                    </div>
                </div>

                <hr style="margin: 15px 0 20px 0;">

                <!-- Multiple Image Upload Box -->
                <div class="form-group">
                    <label style="font-size: 15px; color: #1e293b;">
                        <i class="fa fa-plus-circle text-success"></i> Add Multiple Slide Images for Home Hero:
                    </label>
                    <div class="upload-dropzone">
                        <i class="fa fa-cloud-upload" style="font-size: 38px; color: #3b82f6; margin-bottom: 8px;"></i>
                        <p style="font-weight: 600; margin-bottom: 4px; color: #1e293b;">Select one or multiple images to upload directly to Supabase</p>
                        <p style="font-size: 12px; color: #64748b; margin-bottom: 12px;">Supported formats: JPG, PNG, WEBP, GIF. Images will automatically slide on the storefront.</p>
                        <input type="file" name="hero_slider_photos[]" multiple accept="image/*" class="form-control" style="max-width: 400px; margin: 0 auto; background: #fff;">
                    </div>
                </div>

                <!-- Current Active Slides List -->
                <h4 style="font-size: 15px; font-weight: 700; color: #1e293b; margin-top: 25px; margin-bottom: 15px;">
                    <i class="fa fa-list"></i> Current Slides in Hero Carousel (<?php echo count($slides); ?> active):
                </h4>

                <?php if (empty($slides)): ?>
                    <div class="alert alert-info">No slides uploaded yet. Upload images above to enable sliding!</div>
                <?php else: ?>
                    <div class="table-responsive">
                        <table class="table table-bordered table-striped" style="background:#fff;">
                            <thead>
                                <tr style="background:#f1f5f9;">
                                    <th style="width: 50px; text-align:center;">Order</th>
                                    <th style="width: 130px; text-align:center;">Preview</th>
                                    <th>Image Cloud Storage CDN URL</th>
                                    <th style="width: 100px; text-align:center;">Active</th>
                                    <th style="width: 100px; text-align:center;">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($slides as $idx => $slide): 
                                    $photoUrl = $slide['photo'];
                                    if (!str_starts_with($photoUrl, 'http')) {
                                        $photoUrl = '../assets/uploads/' . $photoUrl;
                                    }
                                ?>
                                    <tr>
                                        <td style="vertical-align: middle; text-align:center;">
                                            <input type="number" name="slide_order[<?php echo $slide['id']; ?>]" value="<?php echo (int)($slide['slide_order'] ?? ($idx+1)); ?>" class="form-control drag-order-input">
                                        </td>
                                        <td style="vertical-align: middle; text-align:center;">
                                            <a href="<?php echo htmlspecialchars($photoUrl); ?>" target="_blank">
                                                <img src="<?php echo htmlspecialchars($photoUrl); ?>" class="slide-thumb">
                                            </a>
                                        </td>
                                        <td style="vertical-align: middle;">
                                            <div style="font-size:12px; font-family:monospace; color:#3b82f6; word-break:break-all;">
                                                <?php echo htmlspecialchars($photoUrl); ?>
                                            </div>
                                            <small class="text-muted">Slide ID: #<?php echo $slide['id']; ?></small>
                                        </td>
                                        <td style="vertical-align: middle; text-align:center;">
                                            <label style="margin:0; cursor:pointer;">
                                                <input type="checkbox" name="slide_active[<?php echo $slide['id']; ?>]" value="1" <?php if(($slide['is_active'] ?? 1) == 1) echo 'checked'; ?>>
                                                <span class="text-success" style="font-size:12px;">Active</span>
                                            </label>
                                        </td>
                                        <td style="vertical-align: middle; text-align:center;">
                                            <a href="homepage-banners.php?action=delete_slide&slide_id=<?php echo $slide['id']; ?>" class="btn btn-danger btn-xs" onclick="return confirm('Are you sure you want to delete this slide from the hero?');" title="Delete Slide">
                                                <i class="fa fa-trash"></i> Delete
                                            </a>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php endif; ?>
            </div>
        </div>

        <!-- ============================================================
             2. HERO CONTENT & TEXTS & FLOATING BADGES
             ============================================================ -->
        <div class="customizer-card">
            <div class="customizer-header" style="border-left: 4px solid #f59e0b;">
                <h3><i class="fa fa-font text-yellow"></i> 2. Hero Headings, Buttons & Floating Badges</h3>
            </div>
            <div class="customizer-body">
                <div class="row">
                    <div class="col-md-6">
                        <div class="form-group">
                            <label>Eyebrow Tagline / Badge</label>
                            <input type="text" name="hero_tag" class="form-control" value="<?php echo htmlspecialchars($s['hero_tag'] ?? 'BETTER PRODUCTS • BETTER LIFE'); ?>" placeholder="e.g. 🔥 2026 NEW ARRIVALS">
                        </div>
                        <div class="form-group">
                            <label>Main Headline / Hero Title</label>
                            <input type="text" name="hero_title" class="form-control input-lg" value="<?php echo htmlspecialchars($s['hero_title'] ?? 'Upgrade Your Everyday Life'); ?>" required placeholder="e.g. Upgrade Your Everyday Life">
                        </div>
                        <div class="form-group">
                            <label>Hero Subtitle / Description</label>
                            <textarea name="hero_subtitle" class="form-control" rows="3" placeholder="Describe your store value proposition..."><?php echo htmlspecialchars($s['hero_subtitle'] ?? 'Discover top-quality products, unbeatable prices, and a seamless shopping experience.'); ?></textarea>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="panel panel-default" style="background:#f8fafc; border-radius:8px; padding:15px; margin-bottom:15px;">
                            <h4 style="margin-top:0; font-size:14px; font-weight:700;"><i class="fa fa-mouse-pointer text-primary"></i> Primary CTA Button</h4>
                            <div class="row">
                                <div class="col-xs-6">
                                    <label>Button Label</label>
                                    <input type="text" name="hero_btn_text" class="form-control" value="<?php echo htmlspecialchars($s['hero_btn_text'] ?? 'Shop Now'); ?>">
                                </div>
                                <div class="col-xs-6">
                                    <label>Button Target URL</label>
                                    <input type="text" name="hero_btn_url" class="form-control" value="<?php echo htmlspecialchars($s['hero_btn_url'] ?? 'product-category.php?id=1&type=top-category'); ?>">
                                </div>
                            </div>
                        </div>

                        <div class="panel panel-default" style="background:#f8fafc; border-radius:8px; padding:15px; margin-bottom:15px;">
                            <h4 style="margin-top:0; font-size:14px; font-weight:700;"><i class="fa fa-external-link text-info"></i> Secondary CTA Button</h4>
                            <div class="row">
                                <div class="col-xs-6">
                                    <label>Button Label</label>
                                    <input type="text" name="hero_btn2_text" class="form-control" value="<?php echo htmlspecialchars($s['hero_btn2_text'] ?? 'Explore All'); ?>">
                                </div>
                                <div class="col-xs-6">
                                    <label>Button Target URL</label>
                                    <input type="text" name="hero_btn2_url" class="form-control" value="<?php echo htmlspecialchars($s['hero_btn2_url'] ?? 'product-category.php?id=1&type=top-category'); ?>">
                                </div>
                            </div>
                        </div>

                        <div class="panel panel-default" style="background:#f8fafc; border-radius:8px; padding:15px;">
                            <h4 style="margin-top:0; font-size:14px; font-weight:700;"><i class="fa fa-certificate text-warning"></i> Floating Annotation Badges</h4>
                            <div class="row">
                                <div class="col-xs-6">
                                    <label>Badge 1 (Top Left)</label>
                                    <input type="text" name="hero_badge1_text" class="form-control" value="<?php echo htmlspecialchars($s['hero_badge1_text'] ?? 'Top Brands • Best Deals'); ?>">
                                </div>
                                <div class="col-xs-6">
                                    <label>Badge 2 (Floating Trust)</label>
                                    <input type="text" name="hero_badge2_text" class="form-control" value="<?php echo htmlspecialchars($s['hero_badge2_text'] ?? '⭐ 4.9/5 Rating (12k+ Reviews)'); ?>">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- ============================================================
             3. CATEGORIES & FEATURED PRODUCTS SECTION HEADINGS
             ============================================================ -->
        <div class="customizer-card">
            <div class="customizer-header" style="border-left: 4px solid #10b981;">
                <h3><i class="fa fa-th text-green"></i> 3. Category & Product Sections Controllability</h3>
            </div>
            <div class="customizer-body">
                <div class="row">
                    <!-- Categories Config -->
                    <div class="col-md-6">
                        <div class="panel panel-default" style="border-radius:8px; padding:16px;">
                            <h4 style="margin-top:0; font-weight:700; color:#0f766e;"><i class="fa fa-folder-open"></i> Shop by Category Section</h4>
                            <div class="form-group">
                                <label>Section Visibility</label>
                                <select name="home_category_on_off" class="form-control">
                                    <option value="1" <?php if(($s['home_category_on_off'] ?? 1) == 1) echo 'selected'; ?>>Show Category Section</option>
                                    <option value="0" <?php if(($s['home_category_on_off'] ?? 1) == 0) echo 'selected'; ?>>Hide Category Section</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label>Section Title</label>
                                <input type="text" name="categories_title" class="form-control" value="<?php echo htmlspecialchars($s['categories_title'] ?? 'Shop by Category'); ?>">
                            </div>
                            <div class="form-group">
                                <label>Section Subtitle</label>
                                <input type="text" name="categories_subtitle" class="form-control" value="<?php echo htmlspecialchars($s['categories_subtitle'] ?? 'Explore our wide range of popular collections'); ?>">
                            </div>
                        </div>
                    </div>

                    <!-- Featured Products Config -->
                    <div class="col-md-6">
                        <div class="panel panel-default" style="border-radius:8px; padding:16px;">
                            <h4 style="margin-top:0; font-weight:700; color:#b45309;"><i class="fa fa-star"></i> Featured Products Section</h4>
                            <div class="row">
                                <div class="col-xs-6">
                                    <div class="form-group">
                                        <label>Section Visibility</label>
                                        <select name="home_featured_product_on_off" class="form-control">
                                            <option value="1" <?php if(($s['home_featured_product_on_off'] ?? 1) == 1) echo 'selected'; ?>>Show Featured Products</option>
                                            <option value="0" <?php if(($s['home_featured_product_on_off'] ?? 1) == 0) echo 'selected'; ?>>Hide Featured Products</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="col-xs-6">
                                    <div class="form-group">
                                        <label>Products To Show</label>
                                        <input type="number" name="total_featured_product_home" class="form-control" value="<?php echo htmlspecialchars($s['total_featured_product_home'] ?? 8); ?>" min="4" max="24" step="2">
                                    </div>
                                </div>
                            </div>
                            <div class="form-group">
                                <label>Section Title</label>
                                <input type="text" name="featured_products_title" class="form-control" value="<?php echo htmlspecialchars($s['featured_products_title'] ?? 'Featured Products'); ?>">
                            </div>
                            <div class="form-group">
                                <label>Section Subtitle</label>
                                <input type="text" name="featured_products_subtitle" class="form-control" value="<?php echo htmlspecialchars($s['featured_products_subtitle'] ?? 'Handpicked best sellers and top rated products'); ?>">
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- ============================================================
             4. PROMOTIONAL DUAL POSTERS (BANNERS 1 & 2)
             ============================================================ -->
        <div class="customizer-card">
            <div class="customizer-header" style="border-left: 4px solid #8b5cf6;">
                <h3><i class="fa fa-th-large text-purple"></i> 4. Promotional Banners (Dual Poster Cards)</h3>
                <div>
                    <label style="margin:0; font-weight:normal;">
                        <input type="checkbox" name="home_welcome_on_off" value="1" <?php if(($s['home_welcome_on_off'] ?? 1) == 1) echo 'checked'; ?>> Show Dual Banners
                    </label>
                </div>
            </div>
            <div class="customizer-body">
                <div class="row">
                    <!-- Poster 1: Electronics -->
                    <div class="col-md-6">
                        <div class="panel panel-default" style="border-radius:10px; border-top: 3px solid #3b82f6;">
                            <div class="panel-heading" style="background:#eff6ff;"><strong>Left Poster: Top Electronics</strong></div>
                            <div class="panel-body">
                                <div class="form-group">
                                    <label>Eyebrow / Discount Tag</label>
                                    <input type="text" name="promo_banner1_tag" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_tag'] ?? 'Up to 50% Off'); ?>">
                                </div>
                                <div class="form-group">
                                    <label>Poster Title</label>
                                    <input type="text" name="promo_banner1_title" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_title'] ?? 'Top Electronics'); ?>">
                                </div>
                                <div class="form-group">
                                    <label>Subtitle / Description</label>
                                    <input type="text" name="promo_banner1_subtitle" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_subtitle'] ?? 'Laptops, Phones, Accessories & More'); ?>">
                                </div>
                                <div class="row">
                                    <div class="col-xs-6">
                                        <div class="form-group">
                                            <label>Button Text</label>
                                            <input type="text" name="promo_banner1_btn_text" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_btn_text'] ?? 'Shop Now'); ?>">
                                        </div>
                                    </div>
                                    <div class="col-xs-6">
                                        <div class="form-group">
                                            <label>Button Link</label>
                                            <input type="text" name="promo_banner1_btn_url" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_btn_url'] ?? 'product-category.php?id=4&type=top-category'); ?>">
                                        </div>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Upload New Poster (Direct to Supabase)</label>
                                    <input type="file" name="promo1_image_file" class="form-control" accept="image/*">
                                </div>
                                <div class="form-group">
                                    <label>Or Supabase Cloud URL</label>
                                    <input type="text" name="promo_banner1_image_url" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner1_image'] ?? ''); ?>">
                                </div>
                                <?php if (!empty($s['promo_banner1_image'])): ?>
                                    <div style="background:#f8fafc; padding:10px; border-radius:8px; text-align:center; border:1px solid #e2e8f0;">
                                        <img src="<?php echo htmlspecialchars($s['promo_banner1_image']); ?>" style="max-height:110px; max-width:100%; border-radius:6px; object-fit:contain;">
                                    </div>
                                <?php endif; ?>
                            </div>
                        </div>
                    </div>

                    <!-- Poster 2: Fashion -->
                    <div class="col-md-6">
                        <div class="panel panel-default" style="border-radius:10px; border-top: 3px solid #f59e0b;">
                            <div class="panel-heading" style="background:#fffbeb;"><strong>Right Poster: Fresh Styles</strong></div>
                            <div class="panel-body">
                                <div class="form-group">
                                    <label>Eyebrow / Discount Tag</label>
                                    <input type="text" name="promo_banner2_tag" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_tag'] ?? 'Trending Deals'); ?>">
                                </div>
                                <div class="form-group">
                                    <label>Poster Title</label>
                                    <input type="text" name="promo_banner2_title" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_title'] ?? 'Fresh Styles For You'); ?>">
                                </div>
                                <div class="form-group">
                                    <label>Subtitle / Description</label>
                                    <input type="text" name="promo_banner2_subtitle" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_subtitle'] ?? 'Fashion, Footwear & Accessories'); ?>">
                                </div>
                                <div class="row">
                                    <div class="col-xs-6">
                                        <div class="form-group">
                                            <label>Button Text</label>
                                            <input type="text" name="promo_banner2_btn_text" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_btn_text'] ?? 'Shop Now'); ?>">
                                        </div>
                                    </div>
                                    <div class="col-xs-6">
                                        <div class="form-group">
                                            <label>Button Link</label>
                                            <input type="text" name="promo_banner2_btn_url" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_btn_url'] ?? 'product-category.php?id=1&type=top-category'); ?>">
                                        </div>
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label>Upload New Poster (Direct to Supabase)</label>
                                    <input type="file" name="promo2_image_file" class="form-control" accept="image/*">
                                </div>
                                <div class="form-group">
                                    <label>Or Supabase Cloud URL</label>
                                    <input type="text" name="promo_banner2_image_url" class="form-control" value="<?php echo htmlspecialchars($s['promo_banner2_image'] ?? ''); ?>">
                                </div>
                                <?php if (!empty($s['promo_banner2_image'])): ?>
                                    <div style="background:#f8fafc; padding:10px; border-radius:8px; text-align:center; border:1px solid #e2e8f0;">
                                        <img src="<?php echo htmlspecialchars($s['promo_banner2_image']); ?>" style="max-height:110px; max-width:100%; border-radius:6px; object-fit:contain;">
                                    </div>
                                <?php endif; ?>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- ============================================================
             5. TRUST & GUARANTEES BAR (4 VALUE PROPS)
             ============================================================ -->
        <div class="customizer-card">
            <div class="customizer-header" style="border-left: 4px solid #10b981;">
                <h3><i class="fa fa-shield text-green"></i> 5. Trust & Guarantees Value Bar</h3>
                <div>
                    <label style="margin:0; font-weight:normal;">
                        <input type="checkbox" name="home_service_on_off" value="1" <?php if(($s['home_service_on_off'] ?? 1) == 1) echo 'checked'; ?>> Show Trust Bar
                    </label>
                </div>
            </div>
            <div class="customizer-body">
                <div class="row">
                    <div class="col-md-3">
                        <div class="form-group">
                            <label><i class="fa fa-truck text-primary"></i> Item 1 (Free Delivery)</label>
                            <input type="text" name="trust_item1_title" class="form-control" value="<?php echo htmlspecialchars($s['trust_item1_title'] ?? 'Free Shipping'); ?>" placeholder="Title">
                            <input type="text" name="trust_item1_desc" class="form-control" style="margin-top:6px;" value="<?php echo htmlspecialchars($s['trust_item1_desc'] ?? 'On orders over ৳ 2,000'); ?>" placeholder="Subtitle">
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label><i class="fa fa-lock text-success"></i> Item 2 (Payment Security)</label>
                            <input type="text" name="trust_item2_title" class="form-control" value="<?php echo htmlspecialchars($s['trust_item2_title'] ?? 'Secure Payment'); ?>" placeholder="Title">
                            <input type="text" name="trust_item2_desc" class="form-control" style="margin-top:6px;" value="<?php echo htmlspecialchars($s['trust_item2_desc'] ?? '100% secure payment'); ?>" placeholder="Subtitle">
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label><i class="fa fa-refresh text-warning"></i> Item 3 (Returns Policy)</label>
                            <input type="text" name="trust_item3_title" class="form-control" value="<?php echo htmlspecialchars($s['trust_item3_title'] ?? 'Easy Returns'); ?>" placeholder="Title">
                            <input type="text" name="trust_item3_desc" class="form-control" style="margin-top:6px;" value="<?php echo htmlspecialchars($s['trust_item3_desc'] ?? '30-day return policy'); ?>" placeholder="Subtitle">
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label><i class="fa fa-headphones text-info"></i> Item 4 (Customer Support)</label>
                            <input type="text" name="trust_item4_title" class="form-control" value="<?php echo htmlspecialchars($s['trust_item4_title'] ?? '24/7 Support'); ?>" placeholder="Title">
                            <input type="text" name="trust_item4_desc" class="form-control" style="margin-top:6px;" value="<?php echo htmlspecialchars($s['trust_item4_desc'] ?? "We're here to help"); ?>" placeholder="Subtitle">
                        </div>
                    </div>
                </div>
            </div>
            <div class="customizer-footer" style="padding:18px 24px; background:#f8fafc; border-top:1px solid #e2e8f0; display:flex; justify-content:space-between; align-items:center;">
                <span class="text-muted"><i class="fa fa-info-circle"></i> Changes reflect live instantly on the storefront.</span>
                <button type="submit" name="form_banners" class="btn btn-primary btn-lg" style="border-radius:30px; padding:10px 36px; font-weight:700; box-shadow: 0 4px 14px rgba(59,130,246,0.35);">
                    <i class="fa fa-save"></i> Save All Customizations
                </button>
            </div>
        </div>
    </form>
</section>

<?php require_once('footer.php'); ?>
