<?php
/**
 * Security Helper Functions
 * Convenient functions for security operations throughout the site
 * 
 * These functions provide quick access to WAF and security features
 * without requiring direct class instantiation
 * 
 * Usage Examples:
 * - echo esc($user_input);
 * - validateInput($email, 'email');
 * - echo csrf_field();
 * - verify_csrf_token($_POST['csrf_token']);
 * - block_suspicious_ip($_SERVER['REMOTE_ADDR'], 'Malicious activity');
 */

// ============================================================================
// INPUT VALIDATION HELPERS
// ============================================================================

/**
 * Validate input by type
 * 
 * @param mixed $input The input to validate
 * @param string $type The type: email, url, ip, phone, alphanumeric, numeric
 * @return bool True if valid, false otherwise
 */
function validate_by_type($input, $type) {
    $waf = getWAF();
    
    switch ($type) {
        case 'email':
            return $waf->validateEmail($input);
        case 'url':
            return $waf->validateURL($input);
        case 'ip':
            return $waf->validateIP($input);
        case 'phone':
            return $waf->validatePhoneNumber($input);
        case 'alphanumeric':
            return preg_match('/^[a-zA-Z0-9]+$/', $input);
        case 'numeric':
            return is_numeric($input);
        default:
            return false;
    }
}

/**
 * Validate multiple inputs at once
 * 
 * Example:
 * $valid = validate_multiple([
 *     'email'    => ['value' => $_POST['email'], 'type' => 'email'],
 *     'phone'    => ['value' => $_POST['phone'], 'type' => 'phone'],
 *     'username' => ['value' => $_POST['username'], 'type' => 'alphanumeric'],
 * ]);
 * 
 * @param array $inputs Array of input validations
 * @return bool True if all valid, false if any invalid
 */
function validate_multiple($inputs) {
    foreach ($inputs as $name => $config) {
        if (!validate_by_type($config['value'], $config['type'])) {
            return false;
        }
    }
    return true;
}

/**
 * Validate password strength
 * 
 * Requirements:
 * - At least 8 characters
 * - At least 1 uppercase letter
 * - At least 1 lowercase letter
 * - At least 1 number
 * - At least 1 special character
 * 
 * @param string $password The password to validate
 * @return bool True if password meets requirements
 */
function validate_password_strength($password) {
    $has_upper   = preg_match('/[A-Z]/', $password);
    $has_lower   = preg_match('/[a-z]/', $password);
    $has_number  = preg_match('/[0-9]/', $password);
    $has_special = preg_match('/[!@#$%^&*()_+\-=\[\]{};:\'",.<>?\/\\|`~]/', $password);
    $has_length  = strlen($password) >= 8;
    
    return $has_upper && $has_lower && $has_number && $has_special && $has_length;
}

/**
 * Get password strength message
 * 
 * @param string $password The password to check
 * @return string Strength message (weak, fair, good, strong, very strong)
 */
function get_password_strength($password) {
    $score = 0;
    
    if (strlen($password) >= 8) $score++;
    if (strlen($password) >= 12) $score++;
    if (strlen($password) >= 16) $score++;
    if (preg_match('/[a-z]/', $password)) $score++;
    if (preg_match('/[A-Z]/', $password)) $score++;
    if (preg_match('/[0-9]/', $password)) $score++;
    if (preg_match('/[!@#$%^&*()_+\-=\[\]{};:\'",.<>?\/\\|`~]/', $password)) $score++;
    
    switch ($score) {
        case 0:
        case 1:
            return 'Weak';
        case 2:
        case 3:
            return 'Fair';
        case 4:
        case 5:
            return 'Good';
        case 6:
            return 'Strong';
        default:
            return 'Very Strong';
    }
}

// ============================================================================
// OUTPUT ESCAPING HELPERS
// ============================================================================

/**
 * Escape output for HTML context
 * Safe for displaying user content in HTML
 * 
 * @param mixed $output The output to escape
 * @return string Escaped output
 */
function esc_html($output) {
    return htmlspecialchars((string)$output, ENT_QUOTES, 'UTF-8');
}

/**
 * Alias for esc_html
 */
function esc($output) {
    return esc_html($output);
}

/**
 * Escape output for JavaScript context
 * Safe for embedding data in JavaScript code
 * 
 * @param mixed $output The output to escape
 * @return string Escaped output
 */
function esc_js($output) {
    $waf = getWAF();
    return $waf->escapeJS($output);
}

/**
 * Escape output for HTML attribute context
 * Safe for use in HTML attributes
 * 
 * @param mixed $output The output to escape
 * @return string Escaped output
 */
function esc_attr($output) {
    $waf = getWAF();
    return $waf->escapeAttr($output);
}

/**
 * Escape output for URL context
 * Safe for use in href and src attributes
 * 
 * @param string $url The URL to escape
 * @return string Escaped URL
 */
function esc_url($url) {
    if (empty($url)) {
        return '';
    }
    
    $url = trim($url);
    
    // Only allow safe protocols
    if (preg_match('%^(https?|ftp)://%i', $url)) {
        return htmlspecialchars($url, ENT_QUOTES, 'UTF-8');
    }
    
    // Allow relative URLs
    if (preg_match('%^/%', $url)) {
        return htmlspecialchars($url, ENT_QUOTES, 'UTF-8');
    }
    
    // Reject other URLs
    return '';
}

/**
 * Escape CSS values
 * Safe for use in style attributes
 * 
 * @param string $css The CSS to escape
 * @return string Escaped CSS
 */
function esc_css($css) {
    return htmlspecialchars($css, ENT_QUOTES, 'UTF-8');
}

// ============================================================================
// IP & RATE LIMITING HELPERS
// ============================================================================

/**
 * Check if IP is rate limited
 * 
 * @param string $endpoint The endpoint name (for custom limits)
 * @param int $limit Maximum requests allowed
 * @param int $window Time window in seconds
 * @return bool True if within limit, false if rate limited
 */
function is_rate_limited($endpoint = 'default', $limit = 100, $window = 60) {
    $waf = getWAF();
    return !$waf->checkRateLimit($endpoint, $limit, $window);
}

/**
 * Get current client IP
 * 
 * @return string Client IP address
 */
function get_client_ip() {
    $waf = getWAF();
    return $waf->getClientIP();
}

/**
 * Check if IP is blocked
 * 
 * @param string $ip The IP to check (optional, uses current if empty)
 * @return bool True if blocked, false if allowed
 */
function is_ip_blocked($ip = '') {
    if (empty($ip)) {
        $ip = get_client_ip();
    }
    
    $waf = getWAF();
    return $waf->isIPBlocked($ip);
}

/**
 * Block a suspicious IP
 * 
 * @param string $ip The IP to block
 * @param string $reason Reason for blocking
 * @param int $duration Duration in seconds (default: 24 hours)
 */
function block_suspicious_ip($ip, $reason = 'Suspicious activity', $duration = 86400) {
    $waf = getWAF();
    $waf->blockIP($ip, $reason, $duration);
}

/**
 * Unblock an IP
 * 
 * @param string $ip The IP to unblock
 */
function unblock_ip($ip) {
    $waf = getWAF();
    $waf->unblockIP($ip);
}

/**
 * Get all blocked IPs
 * 
 * @return array Array of blocked IPs with details
 */
function get_blocked_ips() {
    $blocked_file = __DIR__ . '/assets/cache/security/blocked_ips.json';
    if (!file_exists($blocked_file)) {
        return [];
    }
    
    return json_decode(file_get_contents($blocked_file), true) ?? [];
}

// ============================================================================
// CSRF PROTECTION HELPERS
// ============================================================================

/**
 * Generate CSRF token
 * 
 * @return string CSRF token
 */
function get_csrf_token() {
    $middleware = $_SESSION['_security_middleware'] ?? null;
    if (!$middleware) {
        return '';
    }
    return $middleware->getCSRFToken();
}

/**
 * Get CSRF form field (HTML)
 * 
 * @return string HTML input field
 */
function csrf_field() {
    $middleware = $_SESSION['_security_middleware'] ?? null;
    if (!$middleware) {
        return '';
    }
    return $middleware->getCSRFField();
}

/**
 * Verify CSRF token
 * 
 * @param string $token The token to verify
 * @return bool True if valid, false otherwise
 */
function verify_csrf_token($token) {
    $middleware = $_SESSION['_security_middleware'] ?? null;
    if (!$middleware) {
        return false;
    }
    return $middleware->verifyCSRFToken($token);
}

// ============================================================================
// SECURITY MONITORING HELPERS
// ============================================================================

/**
 * Log a security event
 * 
 * @param string $event_type The type of event
 * @param array $details Details about the event
 */
function log_security_event($event_type, $details = []) {
    $waf = getWAF();
    $waf->logSecurityEvent($event_type, $details);
}

/**
 * Get recent security events
 * 
 * @param int $days Number of days to retrieve (default: 7)
 * @return array Array of security events
 */
function get_security_events($days = 7) {
    $waf = getWAF();
    return $waf->getSecurityEvents($days);
}

/**
 * Get security events by type
 * 
 * @param string $event_type The type of event to filter
 * @param int $days Number of days to retrieve
 * @return array Filtered security events
 */
function get_security_events_by_type($event_type, $days = 7) {
    $events = get_security_events($days);
    return array_filter($events, fn($e) => $e['event_type'] === $event_type);
}

/**
 * Get security events from specific IP
 * 
 * @param string $ip The IP address to filter
 * @param int $days Number of days to retrieve
 * @return array Filtered security events
 */
function get_security_events_by_ip($ip, $days = 7) {
    $events = get_security_events($days);
    return array_filter($events, fn($e) => 
        ($e['details']['ip'] ?? '') === $ip
    );
}

/**
 * Get attack summary
 * Count of each attack type in the last N days
 * 
 * @param int $days Number of days to analyze
 * @return array Summary array with counts by type
 */
function get_attack_summary($days = 7) {
    $events = get_security_events($days);
    $summary = [];
    
    foreach ($events as $event) {
        $type = $event['event_type'];
        $summary[$type] = ($summary[$type] ?? 0) + 1;
    }
    
    return $summary;
}

/**
 * Get security score (0-100)
 * 
 * Based on attack attempts and successful blocks
 * 
 * @param int $days Days to analyze (default: 30)
 * @return int Score from 0 (bad) to 100 (good)
 */
function get_security_score($days = 30) {
    $events = get_security_events($days);
    
    // Base score
    $score = 100;
    
    // Deduct points for attacks
    $sql_injections  = count(array_filter($events, fn($e) => $e['event_type'] === 'sql_injection'));
    $xss_attacks     = count(array_filter($events, fn($e) => $e['event_type'] === 'xss_attack'));
    $bot_attacks     = count(array_filter($events, fn($e) => $e['event_type'] === 'bot_detected'));
    $rate_limits     = count(array_filter($events, fn($e) => $e['event_type'] === 'rate_limit_exceeded'));
    
    $score -= min(30, $sql_injections * 2);
    $score -= min(20, $xss_attacks * 1.5);
    $score -= min(15, $bot_attacks * 1);
    $score -= min(10, $rate_limits * 0.5);
    
    return max(0, $score);
}

/**
 * Send security alert email
 * 
 * @param string $admin_email Admin email address
 * @param string $subject Email subject
 * @param string $event_type Type of event (sql_injection, xss_attack, etc)
 * @param int $threshold Alert if attacks exceed this count
 */
function send_security_alert($admin_email, $event_type, $threshold = 1) {
    $events = get_security_events_by_type($event_type, 1); // Last day
    
    if (count($events) >= $threshold) {
        $subject = "🚨 Security Alert: " . ucfirst(str_replace('_', ' ', $event_type));
        $message = "Total attacks: " . count($events) . "\n\n";
        
        foreach ($events as $event) {
            $message .= "- " . $event['timestamp'] . " from " . 
                       $event['details']['ip'] . "\n";
        }
        
        mail($admin_email, $subject, $message);
    }
}

// ============================================================================
// DETECTION HELPERS
// ============================================================================

/**
 * Check if string contains SQL injection
 * 
 * @param string $string The string to check
 * @return bool True if SQL injection detected
 */
function has_sql_injection($string) {
    $waf = getWAF();
    return $waf->detectSQLInjection($string);
}

/**
 * Check if string contains XSS
 * 
 * @param string $string The string to check
 * @return bool True if XSS detected
 */
function has_xss($string) {
    $waf = getWAF();
    return $waf->detectXSS($string);
}

/**
 * Filter XSS from string
 * 
 * @param string $string The string to filter
 * @return string Filtered string
 */
function filter_xss($string) {
    $waf = getWAF();
    return $waf->filterXSS($string);
}

/**
 * Check if user agent is bot
 * 
 * @param string $user_agent The user agent string (optional, uses current if empty)
 * @return bool True if bot detected
 */
function is_bot($user_agent = '') {
    if (empty($user_agent)) {
        $user_agent = $_SERVER['HTTP_USER_AGENT'] ?? '';
    }
    
    $waf = getWAF();
    return $waf->detectBot($user_agent);
}

// ============================================================================
// CONVENIENCE FUNCTIONS
// ============================================================================

/**
 * Require CAPTCHA for suspicious activity
 * Used in conjunction with rate limiting
 */
function require_captcha_for_suspicious() {
    if (is_rate_limited('default', 50, 60)) {
        $waf = getWAF();
        $waf->requireCAPTCHA();
    }
}

/**
 * Log and block current request
 * Use when custom security rule is violated
 * 
 * @param string $reason Reason for blocking
 */
function block_current_request($reason = 'Security violation') {
    block_suspicious_ip(get_client_ip(), $reason);
    log_security_event('custom_block', [
        'ip' => get_client_ip(),
        'reason' => $reason,
        'timestamp' => date('Y-m-d H:i:s'),
    ]);
    die(json_encode(['error' => 'Request blocked: ' . $reason], JSON_PRETTY_PRINT));
}

/**
 * Check security on critical operation
 * Performs comprehensive security check before executing critical code
 * 
 * @param string $operation_name Name of operation (for logging)
 * @return bool True if secure, false otherwise
 */
function check_critical_operation_security($operation_name) {
    // Check rate limit
    if (is_rate_limited('critical', 5, 60)) {
        log_security_event('critical_rate_limit_exceeded', [
            'operation' => $operation_name,
            'ip' => get_client_ip(),
        ]);
        return false;
    }
    
    // Check CSRF token
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        if (!verify_csrf_token($_POST['csrf_token'] ?? '')) {
            log_security_event('csrf_token_mismatch', [
                'operation' => $operation_name,
                'ip' => get_client_ip(),
            ]);
            return false;
        }
    }
    
    // Check if IP is blocked
    if (is_ip_blocked()) {
        return false;
    }
    
    // All checks passed
    log_security_event('critical_operation_allowed', [
        'operation' => $operation_name,
        'ip' => get_client_ip(),
    ]);
    
    return true;
}

/**
 * Get security status page (for API/monitoring)
 * 
 * @return array Security status information
 */
function get_security_status() {
    $waf = getWAF();
    
    return [
        'waf_active' => true,
        'security_score' => get_security_score(),
        'attacks_today' => count(get_security_events(1)),
        'attacks_week' => count(get_security_events(7)),
        'blocked_ips_count' => count(get_blocked_ips()),
        'attack_summary' => get_attack_summary(7),
        'timestamp' => date('Y-m-d H:i:s'),
    ];
}

// ============================================================================
// INITIALIZATION
// ============================================================================

/**
 * Get WAF instance
 * 
 * @return WAFSecuritySystem
 */
function getWAF() {
    static $waf = null;
    
    if ($waf === null) {
        require_once(__DIR__ . '/admin/inc/WAFSecuritySystem.php');
        $waf = WAFSecuritySystem::getInstance();
    }
    
    return $waf;
}

?>
