<?php
/**
 * WAF Security System - Setup Verification Script
 * 
 * This script verifies that all security components are properly installed
 * and configured. Run this after installing the WAF system.
 * 
 * Access: /setup_security_system.php
 */

session_start();

// Allow access without authentication for setup
$setup_mode = true;

// Color codes for terminal output
$colors = [
    'green'  => "\033[92m",
    'red'    => "\033[91m",
    'yellow' => "\033[93m",
    'blue'   => "\033[94m",
    'reset'  => "\033[0m",
];

// HTML colors for web display
$is_cli = php_sapi_name() === 'cli';

$results = [];
$all_passed = true;

// ============================================================================
// VERIFICATION CHECKS
// ============================================================================

echo ($is_cli ? $colors['blue'] : '') . "🔒 WAF Security System - Setup Verification\n" . 
     ($is_cli ? $colors['reset'] : '') . 
     str_repeat("=", 50) . "\n\n";

// 1. Check WAFSecuritySystem.php
echo "1. Checking WAFSecuritySystem.php... ";
if (file_exists(__DIR__ . '/admin/inc/WAFSecuritySystem.php')) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['waf_class'] = true;
    
    // Try to load it
    require_once(__DIR__ . '/admin/inc/WAFSecuritySystem.php');
    
    // Check methods
    $methods = ['checkRateLimit', 'detectSQLInjection', 'detectXSS', 'blockIP', 'getClientIP', 'logSecurityEvent'];
    $missing = [];
    
    foreach ($methods as $method) {
        if (!method_exists('WAFSecuritySystem', $method)) {
            $missing[] = $method;
        }
    }
    
    if (!empty($missing)) {
        echo "   Warning: Missing methods: " . implode(', ', $missing) . "\n";
        $all_passed = false;
    }
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['waf_class'] = false;
    $all_passed = false;
}

// 2. Check SecurityMiddleware.php
echo "2. Checking SecurityMiddleware.php... ";
if (file_exists(__DIR__ . '/admin/inc/SecurityMiddleware.php')) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['middleware_class'] = true;
    
    require_once(__DIR__ . '/admin/inc/SecurityMiddleware.php');
    
    $methods = ['processRequest', 'getCSRFToken', 'verifyCSRFToken'];
    $missing = [];
    
    foreach ($methods as $method) {
        if (!method_exists('SecurityMiddleware', $method)) {
            $missing[] = $method;
        }
    }
    
    if (!empty($missing)) {
        echo "   Warning: Missing methods: " . implode(', ', $missing) . "\n";
        $all_passed = false;
    }
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['middleware_class'] = false;
    $all_passed = false;
}

// 3. Check security_helpers.php
echo "3. Checking security_helpers.php... ";
if (file_exists(__DIR__ . '/admin/inc/security_helpers.php')) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['helper_functions'] = true;
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['helper_functions'] = false;
    $all_passed = false;
}

// 4. Check cache directories
echo "4. Checking cache directories... ";
$cache_dir = __DIR__ . '/assets/cache/security';
if (!is_dir($cache_dir)) {
    mkdir($cache_dir, 0755, true);
}

if (is_writable($cache_dir)) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['cache_dir'] = true;
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL (not writable)" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['cache_dir'] = false;
    $all_passed = false;
}

// 5. Check session support
echo "5. Checking session support... ";
if (session_id() || session_start()) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['session'] = true;
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['session'] = false;
    $all_passed = false;
}

// 6. Check PHP version
echo "6. Checking PHP version (5.6+ required)... ";
if (version_compare(PHP_VERSION, '5.6.0', '>=')) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS (PHP " . PHP_VERSION . ")" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['php_version'] = true;
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL (PHP " . PHP_VERSION . ")" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['php_version'] = false;
    $all_passed = false;
}

// 7. Check required functions
echo "7. Checking required PHP functions... ";
$required_functions = ['json_encode', 'json_decode', 'hash', 'random_bytes', 'filter_var'];
$missing_functions = [];

foreach ($required_functions as $func) {
    if (!function_exists($func)) {
        $missing_functions[] = $func;
    }
}

if (empty($missing_functions)) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['php_functions'] = true;
} else {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . 
         " (Missing: " . implode(', ', $missing_functions) . ")\n";
    $results['php_functions'] = false;
    $all_passed = false;
}

// 8. Check documentation
echo "8. Checking documentation files... ";
$doc_files = [
    'WAF_SECURITY_GUIDE.md',
    'CLOUDFLARE_WAF_SETUP.md',
    'AWS_WAF_SETUP.md',
    'SECURITY_IMPLEMENTATION_COMPLETE.md',
];

$missing_docs = [];
foreach ($doc_files as $doc) {
    if (!file_exists(__DIR__ . '/' . $doc)) {
        $missing_docs[] = $doc;
    }
}

if (empty($missing_docs)) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS (all " . count($doc_files) . " docs)" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['docs'] = true;
} else {
    echo ($is_cli ? $colors['yellow'] : '<span style="color:orange">') . "⚠ WARNING" . 
         ($is_cli ? $colors['reset'] : '</span>') . 
         " (Missing: " . implode(', ', $missing_docs) . ")\n";
    $results['docs'] = false;
}

// 9. Test WAF instantiation
echo "9. Testing WAF instantiation... ";
try {
    require_once(__DIR__ . '/admin/inc/WAFSecuritySystem.php');
    $waf = WAFSecuritySystem::getInstance();
    
    if ($waf !== null) {
        echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
             ($is_cli ? $colors['reset'] : '</span>') . "\n";
        $results['waf_instantiation'] = true;
    } else {
        throw new Exception('WAF instance is null');
    }
} catch (Exception $e) {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . 
         " (" . $e->getMessage() . ")\n";
    $results['waf_instantiation'] = false;
    $all_passed = false;
}

// 10. Test rate limiting
echo "10. Testing rate limiting... ";
try {
    $result = $waf->checkRateLimit('test', 100, 60);
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['rate_limiting'] = true;
} catch (Exception $e) {
    echo ($is_cli ? $colors['red'] : '<span style="color:red">') . "✗ FAIL" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
    $results['rate_limiting'] = false;
    $all_passed = false;
}

// 11. Test security logging
echo "11. Testing security logging... ";
try {
    $waf->logSecurityEvent('test', ['test' => 'data']);
    
    if (file_exists($cache_dir . '/' . date('Y-m-d') . '.json')) {
        echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ PASS" . 
             ($is_cli ? $colors['reset'] : '</span>') . "\n";
        $results['logging'] = true;
    } else {
        throw new Exception('Log file not created');
    }
} catch (Exception $e) {
    echo ($is_cli ? $colors['yellow'] : '<span style="color:orange">') . "⚠ WARNING" . 
         ($is_cli ? $colors['reset'] : '</span>') . 
         " (" . $e->getMessage() . ")\n";
    $results['logging'] = false;
}

// ============================================================================
// SUMMARY
// ============================================================================

echo "\n" . str_repeat("=", 50) . "\n";

$passed = count(array_filter($results));
$total = count($results);

if ($all_passed) {
    echo ($is_cli ? $colors['green'] : '<span style="color:green">') . 
         "✓ ALL TESTS PASSED ($passed/$total)" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
} else {
    echo ($is_cli ? $colors['yellow'] : '<span style="color:orange">') . 
         "⚠ SOME TESTS FAILED ($passed/$total)" . 
         ($is_cli ? $colors['reset'] : '</span>') . "\n";
}

echo "\n📋 Summary:\n";
foreach ($results as $check => $passed) {
    $status = $passed ? 
        ($is_cli ? $colors['green'] . '✓' : '<span style="color:green">✓</span>') :
        ($is_cli ? $colors['red'] . '✗' : '<span style="color:red">✗</span>');
    
    $label = str_replace('_', ' ', ucfirst($check));
    
    echo "   " . $status . ($is_cli ? $colors['reset'] : '') . " " . $label . "\n";
}

// ============================================================================
// NEXT STEPS
// ============================================================================

echo "\n" . ($is_cli ? $colors['blue'] : '<strong>') . "📚 Next Steps:" . 
     ($is_cli ? $colors['reset'] : '</strong>') . "\n";

if ($all_passed) {
    echo "1. Add to header.php:\n";
    echo "   require_once('admin/inc/SecurityMiddleware.php');\n";
    echo "   initializeSecurityMiddleware();\n\n";
    
    echo "2. Add csrf_field() to all forms\n\n";
    
    echo "3. Use esc() for output escaping\n\n";
    
    echo "4. Access security dashboard:\n";
    echo "   /security-dashboard.php\n\n";
    
    echo "5. Read documentation:\n";
    echo "   - WAF_SECURITY_GUIDE.md\n";
    echo "   - CLOUDFLARE_WAF_SETUP.md or AWS_WAF_SETUP.md\n";
} else {
    echo "Please fix the failed tests above before deploying.\n\n";
    echo "Contact support if you need help.\n";
}

echo "\n" . str_repeat("=", 50) . "\n";

echo ($is_cli ? $colors['green'] : '<span style="color:green">') . "✓ Setup Verification Complete" . 
     ($is_cli ? $colors['reset'] : '</span>') . "\n";

?>
