<?php
/**
 * Speed Optimization Manager Dashboard
 * Monitor and manage performance optimizations
 * 
 * Access: /speed-manager.php (admin only)
 */

// Security check
session_start();
if (!isset($_SESSION['admin_id']) && !isset($_SESSION['vendor_id'])) {
    header('Location: login.php');
    exit;
}

require_once('admin/inc/config.php');
require_once('admin/inc/ImageOptimizer.php');
require_once('admin/inc/AssetMinifier.php');
require_once('admin/inc/DatabaseOptimizer.php');
require_once('admin/inc/PerformanceMonitor.php');

// Get instances
$imageOpt = getImageOptimizer();
$assetMin = getAssetMinifier();
$dbOpt = new DatabaseOptimizer($pdo);
$perfMonitor = getPerformanceMonitor();

// Handle actions
$action = $_GET['action'] ?? '';
$message = '';
$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    switch ($action) {
        case 'compress_images':
            // Compress all product images
            $dir = 'assets/products';
            if (is_dir($dir)) {
                $files = glob($dir . '/*.{jpg,jpeg,png}', GLOB_BRACE);
                $compressed = 0;
                $failed = 0;

                foreach ($files as $file) {
                    if ($imageOpt->compressImage($file, 85)) {
                        $compressed++;
                    } else {
                        $failed++;
                    }
                }

                $message = "Compressed $compressed images. Failed: $failed.";
            }
            break;

        case 'convert_webp':
            // Convert images to WebP
            $dir = 'assets/products';
            if (is_dir($dir)) {
                @mkdir('assets/products/webp', 0755, true);
                $files = glob($dir . '/*.{jpg,jpeg,png}', GLOB_BRACE);
                $converted = 0;

                foreach ($files as $file) {
                    $filename = basename($file, '.' . pathinfo($file, PATHINFO_EXTENSION));
                    $webpPath = 'assets/products/webp/' . $filename . '.webp';
                    
                    if ($imageOpt->convertToWebP($file, $webpPath, 85)) {
                        $converted++;
                    }
                }

                $message = "Converted $converted images to WebP.";
            }
            break;

        case 'create_indexes':
            // Create database indexes
            try {
                $dbOpt->createIndex('tbl_product', 'p_is_active');
                $dbOpt->createIndex('tbl_product', 'ecat_id');
                $dbOpt->createIndex('tbl_product', 'p_current_price');
                $dbOpt->createIndex('tbl_product', 'created_at');
                $dbOpt->createIndex('tbl_customer', 'cust_email');
                $dbOpt->createIndex('tbl_order', 'cust_id');
                $dbOpt->createIndex('tbl_review', 'product_id');

                $message = "Database indexes created successfully!";
            } catch (Exception $e) {
                $error = "Error creating indexes: " . $e->getMessage();
            }
            break;

        case 'clear_image_cache':
            // Clear image cache
            $dirs = [
                'assets/cache/images',
                'assets/cache/thumbnails'
            ];
            $cleared = 0;

            foreach ($dirs as $dir) {
                if (is_dir($dir)) {
                    $files = glob($dir . '/*');
                    foreach ($files as $file) {
                        if (is_file($file)) {
                            unlink($file);
                            $cleared++;
                        }
                    }
                }
            }

            $message = "Cleared $cleared cached image files.";
            break;

        case 'clear_asset_cache':
            // Clear asset cache
            $dir = 'assets/cache/minified';
            if (is_dir($dir)) {
                $files = glob($dir . '/*');
                $cleared = 0;

                foreach ($files as $file) {
                    if (is_file($file)) {
                        unlink($file);
                        $cleared++;
                    }
                }

                $message = "Cleared $cleared minified asset files.";
            }
            break;

        case 'clear_db_cache':
            // Clear database cache
            $dir = 'assets/cache/database';
            if (is_dir($dir)) {
                $files = glob($dir . '/*');
                $cleared = 0;

                foreach ($files as $file) {
                    if (is_file($file)) {
                        unlink($file);
                        $cleared++;
                    }
                }

                $message = "Cleared $cleared database cache files.";
            }
            break;

        case 'optimize_tables':
            // Optimize database tables
            try {
                $tables = ['tbl_product', 'tbl_customer', 'tbl_order', 'tbl_review'];
                $optimized = 0;

                foreach ($tables as $table) {
                    if ($dbOpt->optimizeTable($table)) {
                        $optimized++;
                    }
                }

                $message = "Optimized $optimized tables.";
            } catch (Exception $e) {
                $error = "Error optimizing tables: " . $e->getMessage();
            }
            break;
    }
}

// Get statistics
$imageStats = [];
$assetStats = [];
$dbStats = [];
$perfStats = [];

// Image statistics
$productDir = 'assets/products';
if (is_dir($productDir)) {
    $images = glob($productDir . '/*.{jpg,jpeg,png}', GLOB_BRACE);
    $totalImageSize = 0;
    foreach ($images as $img) {
        $totalImageSize += filesize($img);
    }
    $imageStats = [
        'count' => count($images),
        'total_size' => $totalImageSize,
        'total_size_mb' => round($totalImageSize / 1024 / 1024, 2)
    ];
}

// Asset statistics
$cacheDir = 'assets/cache/minified';
if (is_dir($cacheDir)) {
    $files = glob($cacheDir . '/*');
    $cacheSize = 0;
    foreach ($files as $file) {
        if (is_file($file)) {
            $cacheSize += filesize($file);
        }
    }
    $assetStats = [
        'count' => count($files),
        'total_size' => $cacheSize,
        'total_size_mb' => round($cacheSize / 1024 / 1024, 2)
    ];
}

// Database statistics
try {
    $tables = ['tbl_product', 'tbl_customer', 'tbl_order', 'tbl_review'];
    $totalRows = 0;
    $totalSize = 0;

    foreach ($tables as $table) {
        // Get row count
        $stmt = $pdo->query("SELECT COUNT(*) FROM $table");
        $count = $stmt->fetchColumn();
        $totalRows += $count;

        // Get table size
        $stmt = $pdo->query("SELECT ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size 
                           FROM information_schema.TABLES 
                           WHERE table_schema = DATABASE() AND table_name = '$table'");
        $result = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($result) {
            $totalSize += $result['size'];
        }
    }

    $dbStats = [
        'tables' => count($tables),
        'total_rows' => $totalRows,
        'total_size_mb' => round($totalSize, 2)
    ];
} catch (Exception $e) {
    $dbStats['error'] = $e->getMessage();
}

// Performance statistics
$perfLogs = [];
$perfDir = 'assets/cache/performance';
if (is_dir($perfDir)) {
    $today = date('Y-m-d');
    $logFile = $perfDir . '/perf_' . $today . '.json';
    
    if (file_exists($logFile)) {
        $logs = json_decode(file_get_contents($logFile), true);
        
        if (is_array($logs) && count($logs) > 0) {
            $totalLoadTime = 0;
            $avgMemory = 0;
            
            foreach ($logs as $log) {
                $totalLoadTime += $log['page_load_time'] ?? 0;
                $avgMemory += $log['peak_memory_mb'] ?? 0;
            }
            
            $perfStats = [
                'samples' => count($logs),
                'avg_load_time_ms' => round($totalLoadTime / count($logs), 0),
                'avg_memory_mb' => round($avgMemory / count($logs), 2),
                'latest_grade' => $logs[0]['grade'] ?? 'N/A'
            ];
        }
    }
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>⚡ Speed Optimization Manager</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
        }

        header {
            color: white;
            margin-bottom: 30px;
            text-align: center;
        }

        header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
        }

        header p {
            font-size: 1.1em;
            opacity: 0.9;
        }

        .alerts {
            margin-bottom: 20px;
        }

        .alert {
            padding: 15px 20px;
            border-radius: 8px;
            margin-bottom: 10px;
            animation: slideIn 0.3s ease-out;
        }

        .alert-success {
            background-color: #d4edda;
            border: 1px solid #c3e6cb;
            color: #155724;
        }

        .alert-error {
            background-color: #f8d7da;
            border: 1px solid #f5c6cb;
            color: #721c24;
        }

        @keyframes slideIn {
            from {
                opacity: 0;
                transform: translateY(-10px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }

        .card {
            background: white;
            border-radius: 12px;
            padding: 25px;
            box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
            transition: transform 0.3s, box-shadow 0.3s;
        }

        .card:hover {
            transform: translateY(-5px);
            box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
        }

        .card h2 {
            font-size: 1.3em;
            margin-bottom: 15px;
            color: #333;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .card-icon {
            font-size: 1.5em;
        }

        .stat {
            margin: 10px 0;
            padding: 8px 0;
            border-bottom: 1px solid #eee;
        }

        .stat:last-child {
            border-bottom: none;
        }

        .stat-label {
            color: #666;
            font-size: 0.9em;
            text-transform: uppercase;
            letter-spacing: 1px;
        }

        .stat-value {
            font-size: 1.8em;
            font-weight: bold;
            color: #667eea;
            margin-top: 5px;
        }

        .actions {
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid #eee;
        }

        .btn {
            display: inline-block;
            padding: 10px 15px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 0.9em;
            transition: all 0.3s;
            margin: 5px 5px 5px 0;
            text-decoration: none;
        }

        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
        }

        .btn-sm {
            padding: 8px 12px;
            font-size: 0.85em;
        }

        .btn-danger {
            background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
        }

        .btn-success {
            background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
        }

        form {
            display: inline;
        }

        .full-width {
            grid-column: 1 / -1;
        }

        .section-title {
            background: white;
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 20px;
            color: #333;
        }

        .section-title h2 {
            font-size: 1.5em;
            margin-bottom: 10px;
        }

        .section-title p {
            color: #666;
            font-size: 0.95em;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 15px;
        }

        table th, table td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }

        table th {
            background: #f8f9fa;
            font-weight: 600;
            color: #333;
        }

        table tr:hover {
            background: #f8f9fa;
        }

        .empty {
            text-align: center;
            padding: 40px;
            color: #666;
        }

        .badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.8em;
            font-weight: bold;
        }

        .badge-success {
            background: #d4edda;
            color: #155724;
        }

        .badge-warning {
            background: #fff3cd;
            color: #856404;
        }

        .badge-danger {
            background: #f8d7da;
            color: #721c24;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>⚡ Speed Optimization Manager</h1>
            <p>Monitor and manage your performance optimizations</p>
        </header>

        <?php if ($message): ?>
            <div class="alerts">
                <div class="alert alert-success">✓ <?php echo htmlspecialchars($message); ?></div>
            </div>
        <?php endif; ?>

        <?php if ($error): ?>
            <div class="alerts">
                <div class="alert alert-error">✗ <?php echo htmlspecialchars($error); ?></div>
            </div>
        <?php endif; ?>

        <!-- Performance Overview -->
        <div class="section-title">
            <h2>📊 Performance Summary</h2>
            <p>Real-time statistics and metrics for your optimization efforts</p>
        </div>

        <div class="grid">
            <!-- Images Card -->
            <div class="card">
                <h2><span class="card-icon">🖼️</span> Image Optimization</h2>
                
                <div class="stat">
                    <div class="stat-label">Total Images</div>
                    <div class="stat-value"><?php echo $imageStats['count'] ?? 0; ?></div>
                </div>

                <div class="stat">
                    <div class="stat-label">Total Image Size</div>
                    <div class="stat-value"><?php echo $imageStats['total_size_mb'] ?? 0; ?> MB</div>
                </div>

                <div class="actions">
                    <form method="POST">
                        <input type="hidden" name="action" value="compress_images">
                        <button type="submit" class="btn btn-sm btn-success" 
                                onclick="return confirm('This will compress all images. Continue?')">
                            🗜️ Compress All
                        </button>
                    </form>
                    <form method="POST">
                        <input type="hidden" name="action" value="convert_webp">
                        <button type="submit" class="btn btn-sm btn-success"
                                onclick="return confirm('Convert all images to WebP? Continue?')">
                            📦 Convert to WebP
                        </button>
                    </form>
                    <form method="POST">
                        <input type="hidden" name="action" value="clear_image_cache">
                        <button type="submit" class="btn btn-sm btn-danger">
                            🗑️ Clear Cache
                        </button>
                    </form>
                </div>
            </div>

            <!-- Assets Card -->
            <div class="card">
                <h2><span class="card-icon">📦</span> Asset Minification</h2>
                
                <div class="stat">
                    <div class="stat-label">Cached Files</div>
                    <div class="stat-value"><?php echo $assetStats['count'] ?? 0; ?></div>
                </div>

                <div class="stat">
                    <div class="stat-label">Cache Size</div>
                    <div class="stat-value"><?php echo $assetStats['total_size_mb'] ?? 0; ?> MB</div>
                </div>

                <div class="actions">
                    <form method="POST">
                        <input type="hidden" name="action" value="clear_asset_cache">
                        <button type="submit" class="btn btn-sm btn-danger">
                            🗑️ Clear Cache
                        </button>
                    </form>
                </div>
            </div>

            <!-- Database Card -->
            <div class="card">
                <h2><span class="card-icon">💾</span> Database Optimization</h2>
                
                <div class="stat">
                    <div class="stat-label">Total Rows</div>
                    <div class="stat-value"><?php echo $dbStats['total_rows'] ?? 0; ?></div>
                </div>

                <div class="stat">
                    <div class="stat-label">Database Size</div>
                    <div class="stat-value"><?php echo $dbStats['total_size_mb'] ?? 0; ?> MB</div>
                </div>

                <div class="actions">
                    <form method="POST">
                        <input type="hidden" name="action" value="create_indexes">
                        <button type="submit" class="btn btn-sm btn-success"
                                onclick="return confirm('Create recommended indexes? Continue?')">
                            ⚡ Create Indexes
                        </button>
                    </form>
                    <form method="POST">
                        <input type="hidden" name="action" value="optimize_tables">
                        <button type="submit" class="btn btn-sm btn-success"
                                onclick="return confirm('Optimize database tables? Continue?')">
                            🔧 Optimize Tables
                        </button>
                    </form>
                    <form method="POST">
                        <input type="hidden" name="action" value="clear_db_cache">
                        <button type="submit" class="btn btn-sm btn-danger">
                            🗑️ Clear Cache
                        </button>
                    </form>
                </div>
            </div>

            <!-- Performance Card -->
            <div class="card">
                <h2><span class="card-icon">📈</span> Performance Metrics</h2>
                
                <div class="stat">
                    <div class="stat-label">Average Load Time</div>
                    <div class="stat-value"><?php echo $perfStats['avg_load_time_ms'] ?? 'N/A'; ?> ms</div>
                </div>

                <div class="stat">
                    <div class="stat-label">Average Memory Usage</div>
                    <div class="stat-value"><?php echo $perfStats['avg_memory_mb'] ?? 'N/A'; ?> MB</div>
                </div>

                <div class="stat">
                    <div class="stat-label">Performance Grade</div>
                    <div class="stat-value">
                        <?php 
                        $grade = $perfStats['latest_grade'] ?? 'N/A';
                        if ($grade === 'A') {
                            echo '<span style="color: #28a745;">A</span>';
                        } elseif ($grade === 'B') {
                            echo '<span style="color: #17a2b8;">B</span>';
                        } elseif ($grade === 'C') {
                            echo '<span style="color: #ffc107;">C</span>';
                        } elseif ($grade === 'D') {
                            echo '<span style="color: #fd7e14;">D</span>';
                        } else {
                            echo $grade;
                        }
                        ?>
                    </div>
                </div>
            </div>
        </div>

        <!-- Information Cards -->
        <div class="grid">
            <div class="card full-width">
                <h2>💡 Optimization Tips</h2>
                <ul style="margin-left: 20px; line-height: 1.8;">
                    <li><strong>Compress Images:</strong> Reduces file size by 30-40% with minimal quality loss</li>
                    <li><strong>Convert to WebP:</strong> Modern format that's 25-35% smaller than JPEG/PNG</li>
                    <li><strong>Create Indexes:</strong> Database queries become 50-100x faster with proper indexing</li>
                    <li><strong>Clear Caches:</strong> After making changes, clear caches to force regeneration</li>
                    <li><strong>Monitor Performance:</strong> Check metrics regularly to ensure optimizations are working</li>
                </ul>
            </div>
        </div>

        <footer style="text-align: center; color: white; margin-top: 40px; opacity: 0.8;">
            <p>⚡ Speed Optimization Manager | Real-time Performance Monitoring</p>
            <p style="font-size: 0.9em; margin-top: 10px;">For best results, run all optimizations and monitor performance metrics regularly.</p>
        </footer>
    </div>
</body>
</html>
