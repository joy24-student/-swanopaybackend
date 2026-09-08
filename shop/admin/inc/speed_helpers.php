<?php
/**
 * Speed Optimization Helper Functions
 * 
 * Quick access functions for optimization classes
 * Include this file in your header or use individual includes
 * 
 * Usage:
 * require_once('admin/inc/speed_helpers.php');
 * $img = optimizeImage('path/to/image.jpg');
 * $css = minifyCSS('path/to/style.css');
 */

// ============================================================================
// IMAGE OPTIMIZATION HELPERS
// ============================================================================

/**
 * Get ImageOptimizer instance
 */
function getImageOptimizer() {
    static $optimizer = null;
    if ($optimizer === null) {
        require_once(__DIR__ . '/ImageOptimizer.php');
        $optimizer = new ImageOptimizer();
    }
    return $optimizer;
}

/**
 * Optimize and display image with responsive markup
 * 
 * @param string $imagePath Path to image file
 * @param string $alt Alt text
 * @param int $width Image width
 * @param int $height Image height
 * @return string HTML picture element
 */
function optimizeImage($imagePath, $alt = '', $width = 400, $height = 400) {
    return getImageOptimizer()->generateResponsiveImage($imagePath, $alt, $width, $height);
}

/**
 * Generate thumbnail
 * 
 * @param string $imagePath Path to original image
 * @param int $size Thumbnail size (width)
 * @return string Path to thumbnail
 */
function getThumbnail($imagePath, $size = 200) {
    return getImageOptimizer()->generateThumbnail($imagePath, $size);
}

/**
 * Compress image
 * 
 * @param string $imagePath Path to image
 * @param int $quality Compression quality (1-100)
 * @return bool Success
 */
function compressImage($imagePath, $quality = 85) {
    return getImageOptimizer()->compressImage($imagePath, $quality);
}

/**
 * Convert image to WebP
 * 
 * @param string $sourcePath Path to source image
 * @param string $destPath Path to save WebP
 * @param int $quality Quality (1-100)
 * @return bool Success
 */
function convertToWebP($sourcePath, $destPath, $quality = 85) {
    return getImageOptimizer()->convertToWebP($sourcePath, $destPath, $quality);
}

// ============================================================================
// ASSET MINIFICATION HELPERS
// ============================================================================

/**
 * Get AssetMinifier instance
 */
function getAssetMinifier() {
    static $minifier = null;
    if ($minifier === null) {
        require_once(__DIR__ . '/AssetMinifier.php');
        $minifier = new AssetMinifier();
    }
    return $minifier;
}

/**
 * Get minified CSS link tag
 * 
 * @param string $cssPath Path to CSS file
 * @param bool $minify Whether to minify
 * @return string HTML link tag
 */
function getCSSLink($cssPath, $minify = true) {
    return getAssetMinifier()->getCSSLink($cssPath, $minify);
}

/**
 * Get minified JS script tag
 * 
 * @param string $jsPath Path to JS file
 * @param bool $minify Whether to minify
 * @param bool $defer Whether to defer execution
 * @param bool $async Whether to load async
 * @return string HTML script tag
 */
function getJSScript($jsPath, $minify = true, $defer = true, $async = false) {
    return getAssetMinifier()->getJSScript($jsPath, $minify, $defer, $async);
}

/**
 * Bundle multiple CSS files
 * 
 * @param array $cssFiles Array of CSS file paths
 * @param string $bundleName Bundle name
 * @return string Path to bundled CSS
 */
function bundleCSS($cssFiles, $bundleName = 'bundle') {
    return getAssetMinifier()->bundleCSS($cssFiles, $bundleName);
}

/**
 * Bundle multiple JS files
 * 
 * @param array $jsFiles Array of JS file paths
 * @param string $bundleName Bundle name
 * @return string Path to bundled JS
 */
function bundleJS($jsFiles, $bundleName = 'bundle') {
    return getAssetMinifier()->bundleJS($jsFiles, $bundleName);
}

/**
 * Minify CSS content
 * 
 * @param string $css CSS content
 * @return string Minified CSS
 */
function minifyCSS($css) {
    return getAssetMinifier()->minifyCSS($css);
}

/**
 * Minify JS content
 * 
 * @param string $js JavaScript content
 * @return string Minified JS
 */
function minifyJS($js) {
    return getAssetMinifier()->minifyJS($js);
}

// ============================================================================
// DATABASE OPTIMIZATION HELPERS
// ============================================================================

/**
 * Get DatabaseOptimizer instance (requires PDO)
 * 
 * @param PDO $pdo Database connection
 * @return DatabaseOptimizer Instance
 */
function getDatabaseOptimizer($pdo = null) {
    if ($pdo === null) {
        // Try to get from config
        if (file_exists(__DIR__ . '/config.php')) {
            require_once(__DIR__ . '/config.php');
            if (isset($pdo)) {
                $pdo = $pdo; // Use global $pdo
            }
        }
    }
    
    require_once(__DIR__ . '/DatabaseOptimizer.php');
    return new DatabaseOptimizer($pdo);
}

/**
 * Get cached query result
 * 
 * @param PDO $pdo Database connection
 * @param string $query SQL query
 * @param array $params Query parameters
 * @param int $cacheDuration Cache duration in seconds
 * @return array Query results
 */
function getCachedQuery($pdo, $query, $params = [], $cacheDuration = 3600) {
    $db = getDatabaseOptimizer($pdo);
    return $db->getCachedQuery($query, $params, $cacheDuration);
}

/**
 * Invalidate cached query
 * 
 * @param PDO $pdo Database connection
 * @param string $query SQL query
 * @param array $params Query parameters (optional)
 * @return bool Success
 */
function invalidateCachedQuery($pdo, $query, $params = []) {
    $db = getDatabaseOptimizer($pdo);
    return $db->invalidateCache($query, $params);
}

/**
 * Create database index
 * 
 * @param PDO $pdo Database connection
 * @param string $table Table name
 * @param string $column Column name
 * @return bool Success
 */
function createDatabaseIndex($pdo, $table, $column) {
    $db = getDatabaseOptimizer($pdo);
    return $db->createIndex($table, $column);
}

/**
 * Get EXPLAIN analysis for query
 * 
 * @param PDO $pdo Database connection
 * @param string $query SQL query
 * @param array $params Query parameters
 * @return array EXPLAIN results
 */
function explainQuery($pdo, $query, $params = []) {
    $db = getDatabaseOptimizer($pdo);
    return $db->explainQuery($query, $params);
}

/**
 * Optimize database table
 * 
 * @param PDO $pdo Database connection
 * @param string $table Table name
 * @return bool Success
 */
function optimizeTable($pdo, $table) {
    $db = getDatabaseOptimizer($pdo);
    return $db->optimizeTable($table);
}

/**
 * Clear database cache
 * 
 * @return bool Success
 */
function clearDatabaseCache() {
    $cacheDir = __DIR__ . '/../../assets/cache/database';
    if (!is_dir($cacheDir)) {
        return true;
    }
    
    $files = glob($cacheDir . '/*');
    foreach ($files as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    return true;
}

// ============================================================================
// PERFORMANCE MONITORING HELPERS
// ============================================================================

/**
 * Get PerformanceMonitor instance
 */
function getPerformanceMonitor() {
    static $monitor = null;
    if ($monitor === null) {
        require_once(__DIR__ . '/PerformanceMonitor.php');
        $monitor = new PerformanceMonitor();
    }
    return $monitor;
}

/**
 * Mark performance checkpoint
 * 
 * @param string $label Checkpoint label
 * @return void
 */
function markPerformance($label) {
    getPerformanceMonitor()->mark($label);
}

/**
 * Get page load time
 * 
 * @return int Load time in milliseconds
 */
function getPageLoadTime() {
    return getPerformanceMonitor()->getPageLoadTime();
}

/**
 * Get performance grade (A-F)
 * 
 * @return string Grade
 */
function getPerformanceGrade() {
    return getPerformanceMonitor()->getPerformanceGrade();
}

/**
 * Get performance recommendations
 * 
 * @return array Recommendations
 */
function getPerformanceRecommendations() {
    return getPerformanceMonitor()->getRecommendations();
}

/**
 * Log performance data
 * 
 * @return bool Success
 */
function logPerformanceData() {
    return getPerformanceMonitor()->logPerformance();
}

/**
 * Get performance metrics
 * 
 * @return array Metrics
 */
function getPerformanceMetrics() {
    return getPerformanceMonitor()->getMetrics();
}

/**
 * Display performance metrics (for debugging)
 * 
 * @param bool $echo Whether to echo or return
 * @return string Performance info
 */
function displayPerformanceInfo($echo = true) {
    $monitor = getPerformanceMonitor();
    
    $info = [
        'Load Time' => $monitor->getPageLoadTime() . 'ms',
        'Peak Memory' => $monitor->getPeakMemory() . 'MB',
        'Grade' => $monitor->getPerformanceGrade(),
        'LCP' => $monitor->estimateLCP() . 'ms',
        'FID' => $monitor->estimateFID() . 'ms',
        'CLS' => $monitor->estimateCLS()
    ];
    
    $html = '<div style="background: #f5f5f5; padding: 10px; border-radius: 4px; font-family: monospace; margin: 10px 0;">';
    $html .= '<strong>⚡ Performance Metrics:</strong><br>';
    
    foreach ($info as $key => $value) {
        $html .= '<strong>' . htmlspecialchars($key) . ':</strong> ' . htmlspecialchars($value) . '<br>';
    }
    
    $html .= '</div>';
    
    if ($echo) {
        echo $html;
    }
    return $html;
}

// ============================================================================
// CACHE HELPERS
// ============================================================================

/**
 * Clear all optimization caches
 * 
 * @return array Results of clearing each cache
 */
function clearAllCaches() {
    $results = [
        'images' => clearImageCache(),
        'assets' => clearAssetCache(),
        'database' => clearDatabaseCache(),
        'performance' => clearPerformanceCache()
    ];
    
    return $results;
}

/**
 * Clear image cache
 * 
 * @return bool Success
 */
function clearImageCache() {
    $dirs = [
        'assets/cache/images',
        'assets/cache/thumbnails'
    ];
    
    foreach ($dirs as $dir) {
        if (is_dir($dir)) {
            $files = glob($dir . '/*');
            foreach ($files as $file) {
                if (is_file($file)) {
                    @unlink($file);
                }
            }
        }
    }
    
    return true;
}

/**
 * Clear asset minification cache
 * 
 * @return bool Success
 */
function clearAssetCache() {
    $dir = 'assets/cache/minified';
    if (is_dir($dir)) {
        $files = glob($dir . '/*');
        foreach ($files as $file) {
            if (is_file($file)) {
                @unlink($file);
            }
        }
    }
    return true;
}

/**
 * Clear performance cache
 * 
 * @return bool Success
 */
function clearPerformanceCache() {
    $dir = 'assets/cache/performance';
    if (is_dir($dir)) {
        $files = glob($dir . '/*');
        foreach ($files as $file) {
            if (is_file($file)) {
                @unlink($file);
            }
        }
    }
    return true;
}

/**
 * Get cache directory statistics
 * 
 * @return array Cache statistics
 */
function getCacheStats() {
    $dirs = [
        'images' => 'assets/cache/images',
        'thumbnails' => 'assets/cache/thumbnails',
        'minified' => 'assets/cache/minified',
        'database' => 'assets/cache/database',
        'performance' => 'assets/cache/performance'
    ];
    
    $stats = [];
    
    foreach ($dirs as $name => $dir) {
        if (!is_dir($dir)) {
            $stats[$name] = ['files' => 0, 'size' => 0, 'size_mb' => 0];
            continue;
        }
        
        $files = glob($dir . '/*');
        $totalSize = 0;
        
        foreach ($files as $file) {
            if (is_file($file)) {
                $totalSize += filesize($file);
            }
        }
        
        $stats[$name] = [
            'files' => count($files),
            'size' => $totalSize,
            'size_mb' => round($totalSize / 1024 / 1024, 2)
        ];
    }
    
    return $stats;
}

// ============================================================================
// UTILITY HELPERS
// ============================================================================

/**
 * Initialize all optimization systems
 * Call this in header.php once to set up everything
 */
function initializeOptimizations() {
    // Load all classes
    require_once(__DIR__ . '/ImageOptimizer.php');
    require_once(__DIR__ . '/AssetMinifier.php');
    require_once(__DIR__ . '/DatabaseOptimizer.php');
    require_once(__DIR__ . '/PerformanceMonitor.php');
    
    // Initialize performance monitoring
    $monitor = getPerformanceMonitor();
    $monitor->mark('optimizations_initialized');
}

/**
 * Get optimization system status
 * 
 * @return array Status information
 */
function getOptimizationStatus() {
    $status = [
        'image_optimizer' => function_exists('getImageOptimizer') ? 'Loaded' : 'Not loaded',
        'asset_minifier' => function_exists('getAssetMinifier') ? 'Loaded' : 'Not loaded',
        'database_optimizer' => function_exists('getDatabaseOptimizer') ? 'Loaded' : 'Not loaded',
        'performance_monitor' => function_exists('getPerformanceMonitor') ? 'Loaded' : 'Not loaded',
        'lazy_loading_script' => file_exists('assets/js/lazy-load.js') ? 'Available' : 'Missing',
        'cache_dirs' => getCacheStats()
    ];
    
    return $status;
}

?>
