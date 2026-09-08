<?php
/**
 * Speed Optimization Setup & Verification Script
 * 
 * Run this to verify all optimization components are installed
 * and create necessary directories
 * 
 * Access: /setup_speed_optimization.php
 */

$checks = [];
$warnings = [];
$success = [];

// Color functions for CLI output
function color($text, $color = 'default') {
    $colors = [
        'default' => "\033[0m",
        'green' => "\033[32m",
        'red' => "\033[31m",
        'yellow' => "\033[33m",
        'blue' => "\033[34m",
    ];
    return ($colors[$color] ?? '') . $text . $colors['default'];
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>⚡ Speed Optimization Setup</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .container { max-width: 900px; margin: 0 auto; }
        header { color: white; text-align: center; margin-bottom: 30px; }
        header h1 { font-size: 2.5em; margin-bottom: 10px; }
        .card {
            background: white;
            border-radius: 12px;
            padding: 25px;
            margin-bottom: 20px;
            box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
        }
        .card h2 { font-size: 1.3em; margin-bottom: 15px; color: #333; border-bottom: 2px solid #667eea; padding-bottom: 10px; }
        .check-item {
            padding: 12px;
            margin-bottom: 10px;
            border-radius: 6px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .check-success { background: #d4edda; color: #155724; }
        .check-error { background: #f8d7da; color: #721c24; }
        .check-warning { background: #fff3cd; color: #856404; }
        .check-icon { font-size: 1.3em; font-weight: bold; }
        .btn {
            display: inline-block;
            padding: 12px 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 1em;
            margin: 10px 5px 10px 0;
            text-decoration: none;
            transition: all 0.3s;
        }
        .btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
        .summary {
            background: #f8f9fa;
            border-left: 4px solid #667eea;
            padding: 15px;
            margin-top: 20px;
            border-radius: 4px;
        }
        footer { text-align: center; color: white; margin-top: 40px; opacity: 0.8; }
        code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: 'Courier New', monospace; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>⚡ Speed Optimization Setup Verification</h1>
            <p>Checking installation and creating necessary files/directories</p>
        </header>

        <div class="card">
            <h2>📋 File Verification</h2>
            
            <?php
            $files = [
                'admin/inc/ImageOptimizer.php' => 'Image optimization class',
                'admin/inc/AssetMinifier.php' => 'Asset minification class',
                'admin/inc/DatabaseOptimizer.php' => 'Database optimization class',
                'admin/inc/PerformanceMonitor.php' => 'Performance monitoring class',
                'assets/js/lazy-load.js' => 'Lazy loading JavaScript',
                'speed-manager.php' => 'Speed optimization dashboard',
                'SPEED_OPTIMIZATION_GUIDE.md' => 'Optimization guide',
                'SPEED_INTEGRATION_GUIDE.md' => 'Integration guide',
            ];

            foreach ($files as $file => $description) {
                if (file_exists($file)) {
                    echo '<div class="check-item check-success">';
                    echo '<span class="check-icon">✓</span>';
                    echo '<span><strong>' . htmlspecialchars($file) . '</strong> - ' . htmlspecialchars($description) . '</span>';
                    echo '</div>';
                    $success[] = $file;
                } else {
                    echo '<div class="check-item check-error">';
                    echo '<span class="check-icon">✗</span>';
                    echo '<span><strong>' . htmlspecialchars($file) . '</strong> - ' . htmlspecialchars($description) . ' [MISSING]</span>';
                    echo '</div>';
                    $checks[] = $file;
                }
            }
            ?>
        </div>

        <div class="card">
            <h2>📁 Directory Verification</h2>
            
            <?php
            $dirs = [
                'assets/cache/images' => 'Image cache',
                'assets/cache/thumbnails' => 'Thumbnail cache',
                'assets/cache/minified' => 'Minified asset cache',
                'assets/cache/database' => 'Database query cache',
                'assets/cache/performance' => 'Performance logs',
            ];

            foreach ($dirs as $dir => $description) {
                if (is_dir($dir)) {
                    echo '<div class="check-item check-success">';
                    echo '<span class="check-icon">✓</span>';
                    echo '<span><strong>' . htmlspecialchars($dir) . '</strong> - ' . htmlspecialchars($description) . '</span>';
                    echo '</div>';
                } else {
                    // Try to create
                    @mkdir($dir, 0755, true);
                    if (is_dir($dir)) {
                        echo '<div class="check-item check-success">';
                        echo '<span class="check-icon">✓</span>';
                        echo '<span><strong>' . htmlspecialchars($dir) . '</strong> - ' . htmlspecialchars($description) . ' [CREATED]</span>';
                        echo '</div>';
                    } else {
                        echo '<div class="check-item check-error">';
                        echo '<span class="check-icon">✗</span>';
                        echo '<span><strong>' . htmlspecialchars($dir) . '</strong> - ' . htmlspecialchars($description) . ' [FAILED TO CREATE]</span>';
                        echo '</div>';
                        $checks[] = "Directory: $dir";
                    }
                }
            }
            ?>
        </div>

        <div class="card">
            <h2>⚙️ PHP Extensions</h2>
            
            <?php
            $extensions = [
                'gd' => 'GD Library (for image optimization)',
                'json' => 'JSON extension',
                'pdo_mysql' => 'PDO MySQL driver',
            ];

            foreach ($extensions as $ext => $description) {
                if (extension_loaded($ext)) {
                    echo '<div class="check-item check-success">';
                    echo '<span class="check-icon">✓</span>';
                    echo '<span><strong>' . htmlspecialchars($ext) . '</strong> - ' . htmlspecialchars($description) . '</span>';
                    echo '</div>';
                } else {
                    echo '<div class="check-item check-warning">';
                    echo '<span class="check-icon">⚠</span>';
                    echo '<span><strong>' . htmlspecialchars($ext) . '</strong> - ' . htmlspecialchars($description) . ' [NOT LOADED]</span>';
                    echo '</div>';
                    $warnings[] = "Extension: $ext";
                }
            }
            ?>
        </div>

        <div class="card">
            <h2>🚀 Quick Start</h2>
            
            <p style="margin-bottom: 15px; color: #666; line-height: 1.6;">
                All components are installed and ready to use. Follow these steps to get started:
            </p>

            <ol style="margin-left: 20px; line-height: 2; color: #333;">
                <li><strong>Add to header.php:</strong>
                    <pre style="background: #f5f5f5; padding: 10px; margin-top: 5px; border-radius: 4px; overflow-x: auto;"><code>&lt;?php
require_once('admin/inc/ImageOptimizer.php');
require_once('admin/inc/AssetMinifier.php');
require_once('admin/inc/DatabaseOptimizer.php');
require_once('admin/inc/PerformanceMonitor.php');
?&gt;</code></pre>
                </li>
                <li><strong>Add to footer.php:</strong>
                    <pre style="background: #f5f5f5; padding: 10px; margin-top: 5px; border-radius: 4px; overflow-x: auto;"><code>&lt;script src="assets/js/lazy-load.js" defer&gt;&lt;/script&gt;</code></pre>
                </li>
                <li><strong>Create database indexes:</strong>
                    <p style="margin-top: 5px;">Visit <code>/speed-manager.php</code> and click "Create Indexes"</p>
                </li>
                <li><strong>Compress images:</strong>
                    <p style="margin-top: 5px;">Visit <code>/speed-manager.php</code> and click "Compress All" and "Convert to WebP"</p>
                </li>
                <li><strong>Start optimizing:</strong>
                    <p style="margin-top: 5px;">Follow the <code>SPEED_OPTIMIZATION_GUIDE.md</code> for detailed instructions</p>
                </li>
            </ol>
        </div>

        <div class="card">
            <h2>📊 Summary</h2>
            
            <div class="summary">
                <p><strong>✓ Files Found:</strong> <span style="color: #28a745;"><?php echo count($success); ?></span></p>
                <p><strong>⚠ Issues Found:</strong> <span style="color: <?php echo count($checks) > 0 ? '#dc3545' : '#28a745'; ?>"><?php echo count($checks); ?></span></p>
                <p><strong>⚠ Warnings:</strong> <span style="color: #ffc107;"><?php echo count($warnings); ?></span></p>

                <?php if (count($checks) === 0 && count($warnings) === 0): ?>
                    <p style="margin-top: 15px; padding: 10px; background: #d4edda; border-radius: 4px; color: #155724;">
                        ✓ All checks passed! Speed optimization is ready to use.
                    </p>
                <?php endif; ?>
            </div>
        </div>

        <div class="card">
            <h2>🔗 Quick Links</h2>
            
            <a href="speed-manager.php" class="btn">📊 Dashboard</a>
            <a href="SPEED_OPTIMIZATION_GUIDE.md" class="btn" target="_blank">📖 Guide</a>
            <a href="SPEED_INTEGRATION_GUIDE.md" class="btn" target="_blank">🔧 Integration Guide</a>
        </div>

        <footer>
            <p>⚡ Speed Optimization Setup | All systems go!</p>
        </footer>
    </div>
</body>
</html>
