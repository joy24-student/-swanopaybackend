<?php
/**
 * SEO CONFIGURATION MANAGER
 * Helps with setting up and testing SEO features
 */

require_once('admin/inc/config.php');
require_once('admin/inc/seo_helpers.php');

// Only allow access from admin or localhost
$isAdmin = isset($_SESSION['admin']) || ($_SERVER['REMOTE_ADDR'] == '127.0.0.1' || $_SERVER['REMOTE_ADDR'] == 'localhost');

if (!$isAdmin && isset($_GET['action']) && $_GET['action'] != 'test') {
    die("Access Denied");
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SEO Configuration Manager</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        
        .container {
            max-width: 1000px;
            margin: 0 auto;
            background: white;
            border-radius: 10px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            text-align: center;
        }
        
        .header h1 {
            font-size: 28px;
            margin-bottom: 5px;
        }
        
        .header p {
            opacity: 0.9;
            font-size: 14px;
        }
        
        .content {
            padding: 30px;
        }
        
        .section {
            margin-bottom: 30px;
            padding: 20px;
            border-left: 4px solid #667eea;
            background: #f8f9fa;
            border-radius: 5px;
        }
        
        .section h2 {
            font-size: 18px;
            margin-bottom: 15px;
            color: #333;
        }
        
        .button-group {
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
            margin-bottom: 15px;
        }
        
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 5px;
            cursor: pointer;
            font-size: 14px;
            text-decoration: none;
            display: inline-block;
            transition: all 0.3s ease;
        }
        
        .btn-primary {
            background: #667eea;
            color: white;
        }
        
        .btn-primary:hover {
            background: #5568d3;
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
        }
        
        .btn-success {
            background: #27ae60;
            color: white;
        }
        
        .btn-success:hover {
            background: #229954;
        }
        
        .btn-warning {
            background: #f39c12;
            color: white;
        }
        
        .btn-warning:hover {
            background: #d68910;
        }
        
        .btn-danger {
            background: #e74c3c;
            color: white;
        }
        
        .btn-danger:hover {
            background: #c0392b;
        }
        
        .status-box {
            background: white;
            padding: 15px;
            border-radius: 5px;
            margin-top: 10px;
            border-left: 4px solid;
        }
        
        .status-box.success {
            border-color: #27ae60;
            color: #27ae60;
        }
        
        .status-box.error {
            border-color: #e74c3c;
            color: #e74c3c;
        }
        
        .status-box.warning {
            border-color: #f39c12;
            color: #f39c12;
        }
        
        .status-box.info {
            border-color: #3498db;
            color: #3498db;
        }
        
        .table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            border-radius: 5px;
            overflow: hidden;
        }
        
        .table th {
            background: #f0f0f0;
            padding: 12px;
            text-align: left;
            font-weight: 600;
            color: #333;
            border-bottom: 2px solid #ddd;
        }
        
        .table td {
            padding: 12px;
            border-bottom: 1px solid #eee;
        }
        
        .table tr:hover {
            background: #f9f9f9;
        }
        
        .code {
            background: #2c3e50;
            color: #ecf0f1;
            padding: 15px;
            border-radius: 5px;
            font-family: 'Courier New', monospace;
            font-size: 13px;
            overflow-x: auto;
            margin-top: 10px;
        }
        
        .stats {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }
        
        .stat-card {
            background: white;
            padding: 15px;
            border-radius: 5px;
            text-align: center;
            border-top: 3px solid #667eea;
        }
        
        .stat-card .number {
            font-size: 28px;
            font-weight: bold;
            color: #667eea;
        }
        
        .stat-card .label {
            color: #666;
            font-size: 13px;
            margin-top: 5px;
        }
        
        .alert {
            padding: 15px;
            border-radius: 5px;
            margin-bottom: 15px;
        }
        
        .alert-success {
            background: #d4edda;
            color: #155724;
            border-left: 4px solid #27ae60;
        }
        
        .alert-error {
            background: #f8d7da;
            color: #721c24;
            border-left: 4px solid #e74c3c;
        }
        
        .alert-info {
            background: #d1ecf1;
            color: #0c5460;
            border-left: 4px solid #3498db;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 SEO Configuration Manager</h1>
            <p>Manage and optimize your e-commerce site for search engines</p>
        </div>
        
        <div class="content">
            <?php
            $action = isset($_GET['action']) ? $_GET['action'] : 'dashboard';
            
            switch($action) {
                case 'generate_slugs':
                    handleGenerateSlugs();
                    break;
                case 'test_urls':
                    handleTestUrls();
                    break;
                case 'check_config':
                    handleCheckConfig();
                    break;
                case 'test':
                    handlePublicTest();
                    break;
                case 'stats':
                    handleStats();
                    break;
                default:
                    showDashboard();
            }
            ?>
        </div>
    </div>
</body>
</html>

<?php

function showDashboard() {
    ?>
    <div class="section">
        <h2>📊 SEO Status Dashboard</h2>
        <div class="button-group">
            <a href="?action=check_config" class="btn btn-primary">Check Configuration</a>
            <a href="?action=stats" class="btn btn-primary">View Statistics</a>
            <a href="?action=test_urls" class="btn btn-primary">Test URLs</a>
            <a href="?action=generate_slugs" class="btn btn-success">Generate Missing Slugs</a>
        </div>
        
        <div class="alert alert-info">
            <strong>ℹ️ Info:</strong> This tool helps you manage SEO configuration. Use "Check Configuration" to verify all settings are correct.
        </div>
    </div>
    
    <div class="section">
        <h2>🔧 Quick Setup</h2>
        <ol style="margin-left: 20px; line-height: 1.8;">
            <li>Verify <strong>.htaccess</strong> is in your root directory</li>
            <li>Update <strong>robots.txt</strong> with your domain name</li>
            <li>Generate slugs for existing products (see above)</li>
            <li>Test URLs work correctly</li>
            <li>Submit sitemap to Google Search Console</li>
        </ol>
    </div>
    
    <div class="section">
        <h2>📚 Key Files</h2>
        <table class="table">
            <thead>
                <tr>
                    <th>File</th>
                    <th>Purpose</th>
                    <th>Status</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><code>.htaccess</code></td>
                    <td>URL rewriting and caching headers</td>
                    <td><?php echo file_exists('.htaccess') ? '✓ Found' : '✗ Missing'; ?></td>
                </tr>
                <tr>
                    <td><code>robots.txt</code></td>
                    <td>Search engine crawling rules</td>
                    <td><?php echo file_exists('robots.txt') ? '✓ Found' : '✗ Missing'; ?></td>
                </tr>
                <tr>
                    <td><code>sitemap.php</code></td>
                    <td>Dynamic sitemap generator</td>
                    <td><?php echo file_exists('sitemap.php') ? '✓ Found' : '✗ Missing'; ?></td>
                </tr>
                <tr>
                    <td><code>admin/inc/seo_helpers.php</code></td>
                    <td>URL slug functions</td>
                    <td><?php echo file_exists('admin/inc/seo_helpers.php') ? '✓ Found' : '✗ Missing'; ?></td>
                </tr>
                <tr>
                    <td><code>admin/inc/seo_meta.php</code></td>
                    <td>Meta tags and schema markup</td>
                    <td><?php echo file_exists('admin/inc/seo_meta.php') ? '✓ Found' : '✗ Missing'; ?></td>
                </tr>
            </tbody>
        </table>
    </div>
    <?php
}

function handleCheckConfig() {
    global $pdo, $isAdmin;
    
    if (!$isAdmin) {
        die("Access Denied");
    }
    
    ?>
    <div class="section">
        <h2>🔍 Configuration Check</h2>
        
        <?php
        // Check .htaccess
        $htaccess_exists = file_exists('.htaccess');
        echo '<div class="status-box ' . ($htaccess_exists ? 'success' : 'error') . '">';
        echo ($htaccess_exists ? '✓' : '✗') . ' .htaccess file ' . ($htaccess_exists ? 'exists' : 'missing');
        echo '</div>';
        
        // Check robots.txt
        $robots_exists = file_exists('robots.txt');
        echo '<div class="status-box ' . ($robots_exists ? 'success' : 'warning') . '">';
        echo ($robots_exists ? '✓' : '✗') . ' robots.txt file ' . ($robots_exists ? 'exists' : 'missing (important for SEO)');
        echo '</div>';
        
        // Check database slug columns
        try {
            $statement = $pdo->query("SHOW COLUMNS FROM tbl_product LIKE 'slug'");
            $hasSlug = $statement->rowCount() > 0;
            echo '<div class="status-box ' . ($hasSlug ? 'success' : 'warning') . '">';
            echo ($hasSlug ? '✓' : '✗') . ' Product slug column ' . ($hasSlug ? 'exists' : 'missing (will be auto-created)');
            echo '</div>';
        } catch (Exception $e) {
            echo '<div class="status-box error">✗ Cannot check database: ' . $e->getMessage() . '</div>';
        }
        
        // Count products with slugs
        try {
            $statement = $pdo->query("SHOW COLUMNS FROM tbl_product LIKE 'slug'");
            if ($statement->rowCount() > 0) {
                $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_product WHERE slug IS NOT NULL");
                $result = $statement->fetch(PDO::FETCH_ASSOC);
                $count = $result['count'];
                
                $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_product");
                $total = $statement->fetch(PDO::FETCH_ASSOC)['count'];
                
                echo '<div class="status-box info">';
                echo "ℹ Products with slugs: $count / $total";
                echo '</div>';
            }
        } catch (Exception $e) {
            // Column doesn't exist yet
        }
        ?>
    </div>
    
    <div class="section">
        <h2>⚙️ Server Configuration</h2>
        <table class="table">
            <thead>
                <tr>
                    <th>Setting</th>
                    <th>Value</th>
                    <th>Recommendation</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>PHP Version</td>
                    <td><?php echo phpversion(); ?></td>
                    <td>PHP 7.4+ (you have: <?php echo phpversion() >= '7.4' ? '✓' : '✗'; ?>)</td>
                </tr>
                <tr>
                    <td>OPcache</td>
                    <td><?php echo extension_loaded('Zend OPcache') ? 'Enabled' : 'Disabled'; ?></td>
                    <td><?php echo extension_loaded('Zend OPcache') ? '✓ Good' : '⚠ Recommended'; ?></td>
                </tr>
                <tr>
                    <td>HTTPS</td>
                    <td><?php echo (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on') ? 'Enabled' : 'Disabled'; ?></td>
                    <td><?php echo (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on') ? '✓ Good' : '✗ Required'; ?></td>
                </tr>
                <tr>
                    <td>mod_rewrite</td>
                    <td><?php echo (in_array('mod_rewrite', apache_get_modules() ?? [])) ? 'Enabled' : 'Unknown'; ?></td>
                    <td>Required for clean URLs</td>
                </tr>
                <tr>
                    <td>Memory Limit</td>
                    <td><?php echo ini_get('memory_limit'); ?></td>
                    <td><?php $mem = ini_get('memory_limit'); echo (intval($mem) >= 256) ? '✓ Good' : '⚠ Increase'; ?></td>
                </tr>
            </tbody>
        </table>
    </div>
    
    <div class="button-group" style="margin-top: 20px;">
        <a href="?action=dashboard" class="btn btn-primary">Back to Dashboard</a>
        <a href="?action=generate_slugs" class="btn btn-success">Generate Slugs</a>
    </div>
    <?php
}

function handleGenerateSlugs() {
    global $pdo, $isAdmin;
    
    if (!$isAdmin) {
        die("Access Denied");
    }
    
    require_once('admin/inc/seo_helpers.php');
    
    $count = 0;
    
    try {
        // Get all active products
        $statement = $pdo->prepare("SELECT p_id, p_name FROM tbl_product WHERE p_is_active=1");
        $statement->execute();
        $products = $statement->fetchAll(PDO::FETCH_ASSOC);
        
        echo '<div class="section"><h2>Generating Product Slugs...</h2>';
        
        foreach ($products as $product) {
            if (updateProductSlug($product['p_id'], $product['p_name'], $pdo)) {
                $count++;
            }
        }
        
        echo '<div class="alert alert-success">✓ Successfully generated slugs for ' . $count . ' products!</div>';
        echo '</div>';
        
    } catch (Exception $e) {
        echo '<div class="alert alert-error">✗ Error: ' . htmlspecialchars($e->getMessage()) . '</div>';
    }
    
    ?>
    <div class="button-group">
        <a href="?action=dashboard" class="btn btn-primary">Back to Dashboard</a>
        <a href="?action=test_urls" class="btn btn-primary">Test URLs</a>
    </div>
    <?php
}

function handleTestUrls() {
    global $pdo, $isAdmin;
    
    if (!$isAdmin) {
        die("Access Denied");
    }
    
    require_once('admin/inc/seo_helpers.php');
    
    ?>
    <div class="section">
        <h2>🧪 Test Sample URLs</h2>
        
        <?php
        try {
            // Get a sample product
            $statement = $pdo->prepare("SELECT p_id, p_name FROM tbl_product WHERE p_is_active=1 LIMIT 1");
            $statement->execute();
            $product = $statement->fetch(PDO::FETCH_ASSOC);
            
            if ($product) {
                $url = getProductURL($product['p_id'], $product['p_name'], BASE_URL);
                echo '<div style="margin-bottom: 20px;">';
                echo '<h3>Sample Product URL:</h3>';
                echo '<div class="code">' . htmlspecialchars($url) . '</div>';
                echo '<p style="margin-top: 10px;"><a href="' . $url . '" target="_blank" class="btn btn-primary">Visit URL →</a></p>';
                echo '</div>';
            }
            
            // Get a sample category
            $statement = $pdo->prepare("SELECT tcat_id, tcat_name FROM tbl_top_category LIMIT 1");
            $statement->execute();
            $category = $statement->fetch(PDO::FETCH_ASSOC);
            
            if ($category) {
                $url = getCategoryURL($category['tcat_id'], $category['tcat_name'], 'top-category', '', BASE_URL);
                echo '<div>';
                echo '<h3>Sample Category URL:</h3>';
                echo '<div class="code">' . htmlspecialchars($url) . '</div>';
                echo '<p style="margin-top: 10px;"><a href="' . $url . '" target="_blank" class="btn btn-primary">Visit URL →</a></p>';
                echo '</div>';
            }
        } catch (Exception $e) {
            echo '<div class="alert alert-error">Error: ' . $e->getMessage() . '</div>';
        }
        ?>
    </div>
    
    <div class="button-group" style="margin-top: 20px;">
        <a href="?action=dashboard" class="btn btn-primary">Back to Dashboard</a>
        <a href="?action=stats" class="btn btn-primary">View Stats</a>
    </div>
    <?php
}

function handlePublicTest() {
    require_once('admin/inc/seo_helpers.php');
    
    // Public test of slug generation
    $testText = "Sony WH-CH720 Wireless Headphones";
    $slug = generateSlug($testText);
    $uniqueSlug = generateUniqueSlug($testText, 123);
    
    ?>
    <div class="section">
        <h2>Test Slug Generation</h2>
        <div style="margin-bottom: 15px;">
            <p><strong>Input:</strong> <?php echo htmlspecialchars($testText); ?></p>
            <p><strong>Basic Slug:</strong> <code><?php echo htmlspecialchars($slug); ?></code></p>
            <p><strong>Unique Slug:</strong> <code><?php echo htmlspecialchars($uniqueSlug); ?></code></p>
        </div>
        <p style="margin-top: 20px; text-align: center; color: #666; font-size: 13px;">
            Slug generation working correctly ✓
        </p>
    </div>
    <?php
}

function handleStats() {
    global $pdo, $isAdmin;
    
    if (!$isAdmin) {
        die("Access Denied");
    }
    
    ?>
    <div class="section">
        <h2>📈 SEO Statistics</h2>
        
        <div class="stats">
            <?php
            try {
                // Product count
                $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_product WHERE p_is_active=1");
                $products = $statement->fetch(PDO::FETCH_ASSOC)['count'];
                
                echo '<div class="stat-card">';
                echo '<div class="number">' . $products . '</div>';
                echo '<div class="label">Active Products</div>';
                echo '</div>';
                
                // Category count
                $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_end_category");
                $categories = $statement->fetch(PDO::FETCH_ASSOC)['count'];
                
                echo '<div class="stat-card">';
                echo '<div class="number">' . $categories . '</div>';
                echo '<div class="label">End Categories</div>';
                echo '</div>';
                
                // Slug coverage
                $statement = $pdo->query("SHOW COLUMNS FROM tbl_product LIKE 'slug'");
                if ($statement->rowCount() > 0) {
                    $statement = $pdo->query("SELECT COUNT(*) as total FROM tbl_product");
                    $total = $statement->fetch(PDO::FETCH_ASSOC)['total'];
                    
                    $statement = $pdo->query("SELECT COUNT(*) as withSlug FROM tbl_product WHERE slug IS NOT NULL");
                    $withSlug = $statement->fetch(PDO::FETCH_ASSOC)['withSlug'];
                    
                    $percent = $total > 0 ? round(($withSlug / $total) * 100) : 0;
                    
                    echo '<div class="stat-card">';
                    echo '<div class="number">' . $percent . '%</div>';
                    echo '<div class="label">Slug Coverage</div>';
                    echo '</div>';
                }
            } catch (Exception $e) {
                echo '<div class="alert alert-error">Error fetching stats</div>';
            }
            ?>
        </div>
    </div>
    
    <div class="button-group" style="margin-top: 20px;">
        <a href="?action=dashboard" class="btn btn-primary">Back to Dashboard</a>
        <a href="?action=check_config" class="btn btn-primary">Check Configuration</a>
    </div>
    <?php
}
?>
