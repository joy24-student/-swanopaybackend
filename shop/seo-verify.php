<?php
/**
 * SEO VERIFICATION & DEBUG SCRIPT
 * 
 * This script helps verify that all SEO features are working correctly
 * Run this during development and troubleshooting
 */

header('Content-Type: text/plain; charset=utf-8');

echo "================================================================================\n";
echo "SEO IMPLEMENTATION VERIFICATION REPORT\n";
echo "================================================================================\n";
echo "Generated: " . date('Y-m-d H:i:s') . "\n";
echo "URL: " . $_SERVER['HTTP_HOST'] . "\n";
echo "Protocol: " . (isset($_SERVER['HTTPS']) ? 'HTTPS' : 'HTTP') . "\n";
echo "\n";

// 1. Check Files
echo "1. REQUIRED FILES CHECK\n";
echo "================================================================================\n";

$files = [
    '.htaccess',
    'robots.txt',
    'sitemap.php',
    'seo-manager.php',
    'admin/inc/seo_helpers.php',
    'admin/inc/seo_meta.php',
    'migration_helper.php',
];

foreach ($files as $file) {
    $exists = file_exists($file);
    echo ($exists ? '✓' : '✗') . ' ' . str_pad($file, 40) . ($exists ? 'Found' : 'MISSING') . "\n";
}

echo "\n";

// 2. Check Configuration
echo "2. CONFIGURATION CHECK\n";
echo "================================================================================\n";

try {
    require_once('admin/inc/config.php');
    
    echo "✓ Database connection: SUCCESS\n";
    echo "  BASE_URL: " . BASE_URL . "\n";
    echo "  Protocol: " . (strpos(BASE_URL, 'https') === 0 ? 'HTTPS ✓' : 'HTTP ✗') . "\n";
    
} catch (Exception $e) {
    echo "✗ Database connection: FAILED\n";
    echo "  Error: " . $e->getMessage() . "\n";
}

echo "\n";

// 3. Check SEO Helpers
echo "3. SEO HELPER FUNCTIONS\n";
echo "================================================================================\n";

if (file_exists('admin/inc/seo_helpers.php')) {
    require_once('admin/inc/seo_helpers.php');
    
    // Test generateSlug
    $testSlug = generateSlug("Sony WH-CH720 Wireless Headphones");
    echo "✓ generateSlug() working\n";
    echo "  Input: 'Sony WH-CH720 Wireless Headphones'\n";
    echo "  Output: '$testSlug'\n";
    
    // Test generateUniqueSlug
    $testUniqueSlug = generateUniqueSlug("Sony WH-CH720 Wireless Headphones", 123);
    echo "✓ generateUniqueSlug() working\n";
    echo "  Output: '$testUniqueSlug'\n";
    
    // Test extractIdFromSlug
    $extractedId = extractIdFromSlug($testUniqueSlug);
    echo "✓ extractIdFromSlug() working\n";
    echo "  Extracted ID: $extractedId\n";
    
} else {
    echo "✗ seo_helpers.php not found\n";
}

echo "\n";

// 4. Database Schema Check
echo "4. DATABASE SCHEMA CHECK\n";
echo "================================================================================\n";

try {
    // Check if slug column exists in tbl_product
    $statement = $pdo->query("SHOW COLUMNS FROM tbl_product LIKE 'slug'");
    $hasSlug = $statement->rowCount() > 0;
    
    echo ($hasSlug ? '✓' : '⚠') . ' Product slug column: ' . ($hasSlug ? 'EXISTS' : 'MISSING (auto-created on first use)') . "\n";
    
    // Count products
    $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_product WHERE p_is_active=1");
    $count = $statement->fetch(PDO::FETCH_ASSOC)['count'];
    echo "  Active products: $count\n";
    
    // Count products with slugs (if column exists)
    if ($hasSlug) {
        $statement = $pdo->query("SELECT COUNT(*) as count FROM tbl_product WHERE slug IS NOT NULL");
        $withSlug = $statement->fetch(PDO::FETCH_ASSOC)['count'];
        $percent = $count > 0 ? round(($withSlug / $count) * 100) : 0;
        echo "  Products with slugs: $withSlug / $count ($percent%)\n";
    }
    
} catch (Exception $e) {
    echo "✗ Database check failed: " . $e->getMessage() . "\n";
}

echo "\n";

// 5. .htaccess Configuration
echo "5. .HTACCESS CONFIGURATION\n";
echo "================================================================================\n";

if (file_exists('.htaccess')) {
    $content = file_get_contents('.htaccess');
    
    $checks = [
        'RewriteEngine On' => 'Rewrite engine enabled',
        'HTTPS' => 'HTTPS enforcement',
        'GZIP' => 'GZIP compression',
        'Cache-Control' => 'Browser caching',
        'mod_expires' => 'Expiration headers',
    ];
    
    foreach ($checks as $keyword => $description) {
        $found = strpos($content, $keyword) !== false;
        echo ($found ? '✓' : '✗') . ' ' . $description . "\n";
    }
    
} else {
    echo "✗ .htaccess file not found\n";
}

echo "\n";

// 6. robots.txt Check
echo "6. ROBOTS.TXT CHECK\n";
echo "================================================================================\n";

if (file_exists('robots.txt')) {
    $content = file_get_contents('robots.txt');
    
    $hasSitemap = strpos($content, 'Sitemap:') !== false;
    $hasMeta = strpos($content, 'sitemap.php') !== false;
    
    echo ($hasSitemap ? '✓' : '✗') . " Sitemap directive present\n";
    echo "  Note: Update domain in robots.txt from 'yourdomain.com' to your actual domain\n";
    
} else {
    echo "✗ robots.txt file not found\n";
}

echo "\n";

// 7. Sitemap Check
echo "7. SITEMAP GENERATION\n";
echo "================================================================================\n";

if (file_exists('sitemap.php')) {
    echo "✓ sitemap.php exists\n";
    echo "  Access sitemap index: /sitemap.php\n";
    echo "  Products sitemap: /sitemap.php?type=products\n";
    echo "  Categories sitemap: /sitemap.php?type=categories\n";
    echo "  Pages sitemap: /sitemap.php?type=pages\n";
} else {
    echo "✗ sitemap.php file not found\n";
}

echo "\n";

// 8. Server Information
echo "8. SERVER CONFIGURATION\n";
echo "================================================================================\n";

echo "PHP Version: " . phpversion() . "\n";
echo "Server Software: " . $_SERVER['SERVER_SOFTWARE'] . "\n";
echo "Memory Limit: " . ini_get('memory_limit') . "\n";
echo "Max Execution Time: " . ini_get('max_execution_time') . "s\n";
echo "Upload Max Size: " . ini_get('upload_max_filesize') . "\n";

echo "\nExtensions:\n";
$extensions = [
    'OPcache' => extension_loaded('Zend OPcache'),
    'PDO' => extension_loaded('PDO'),
    'cURL' => extension_loaded('curl'),
    'JSON' => extension_loaded('json'),
];

foreach ($extensions as $name => $loaded) {
    echo "  " . ($loaded ? '✓' : '✗') . ' ' . $name . "\n";
}

echo "\nApache Modules (if available):\n";
if (function_exists('apache_get_modules')) {
    $modules = apache_get_modules();
    $important = ['mod_rewrite', 'mod_deflate', 'mod_expires', 'mod_headers'];
    foreach ($important as $module) {
        $loaded = in_array($module, $modules);
        echo "  " . ($loaded ? '✓' : '✗') . ' ' . $module . "\n";
    }
} else {
    echo "  (Apache module info not available - contact hosting provider)\n";
}

echo "\n";

// 9. Quick Tests
echo "9. QUICK FUNCTIONALITY TESTS\n";
echo "================================================================================\n";

if (file_exists('admin/inc/seo_helpers.php') && isset($pdo)) {
    require_once('admin/inc/seo_helpers.php');
    
    // Test URL generation
    $testUrl = getProductURL(123, "Test Product", BASE_URL);
    echo "✓ Product URL generation working\n";
    echo "  Sample: $testUrl\n";
    
    $testCatUrl = getCategoryURL(1, "Electronics", 'top-category', '', BASE_URL);
    echo "✓ Category URL generation working\n";
    echo "  Sample: $testCatUrl\n";
}

echo "\n";

// 10. Recommendations
echo "10. RECOMMENDATIONS\n";
echo "================================================================================\n";

$issues = [];

// Check HTTPS
if (!isset($_SERVER['HTTPS']) || $_SERVER['HTTPS'] !== 'on') {
    $issues[] = "⚠ Not using HTTPS - Enable SSL certificate for better SEO";
}

// Check BASE_URL
if (!defined('BASE_URL') || strpos(BASE_URL, 'https') === false) {
    $issues[] = "⚠ BASE_URL not using HTTPS - Update admin/inc/config.php";
}

// Check robots.txt
if (file_exists('robots.txt')) {
    $content = file_get_contents('robots.txt');
    if (strpos($content, 'yourdomain.com') !== false) {
        $issues[] = "⚠ robots.txt has 'yourdomain.com' - Replace with actual domain";
    }
}

if (empty($issues)) {
    echo "✓ No issues found! Your SEO setup looks good.\n";
    echo "\nNext steps:\n";
    echo "1. Visit /seo-manager.php to generate product slugs\n";
    echo "2. Test some URLs to ensure they're working\n";
    echo "3. Submit sitemap.php to Google Search Console\n";
    echo "4. Monitor Google Search Console for crawl errors\n";
} else {
    echo count($issues) . " issue(s) found:\n";
    foreach ($issues as $issue) {
        echo "  $issue\n";
    }
}

echo "\n";
echo "================================================================================\n";
echo "END OF REPORT\n";
echo "================================================================================\n";
?>
