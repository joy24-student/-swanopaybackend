<?php
/**
 * Web Application Firewall (WAF) Security System
 * 
 * Comprehensive security layer including:
 * - SQL Injection prevention
 * - XSS (Cross-Site Scripting) filtering
 * - Bot protection
 * - Rate limiting
 * - Security headers
 * - Input validation & sanitization
 * 
 * Include in header.php: require_once('admin/inc/WAFSecuritySystem.php');
 */

class WAFSecuritySystem {
    private static $instance = null;
    private $config = [];
    private $blockedIPs = [];
    private $suspiciousPatterns = [];
    
    public function __construct() {
        $this->initializeConfig();
        $this->loadBlockedIPs();
        $this->initializeSuspiciousPatterns();
    }
    
    /**
     * Get singleton instance
     */
    public static function getInstance() {
        if (self::$instance === null) {
            self::$instance = new self();
        }
        return self::$instance;
    }
    
    /**
     * Initialize WAF configuration
     */
    private function initializeConfig() {
        $this->config = [
            'enabled' => true,
            'log_suspicious' => true,
            'block_suspicious' => true,
            'rate_limit_enabled' => true,
            'rate_limit_requests' => 100,
            'rate_limit_window' => 60, // seconds
            'bot_protection' => true,
            'xss_filtering' => true,
            'sql_injection_protection' => true,
            'security_headers' => true,
            'cloudflare_protection' => true,
        ];
    }
    
    /**
     * Load blocked IP addresses
     */
    private function loadBlockedIPs() {
        $blockFile = __DIR__ . '/../../assets/cache/security/blocked_ips.json';
        if (file_exists($blockFile)) {
            $data = json_decode(file_get_contents($blockFile), true);
            $this->blockedIPs = $data ?? [];
        }
    }
    
    /**
     * Initialize suspicious patterns (SQL injection, XSS, etc.)
     */
    private function initializeSuspiciousPatterns() {
        $this->suspiciousPatterns = [
            // SQL Injection patterns
            'sql_injection' => [
                '/(\bunion\b.*\bselect\b)/i',
                '/(\bselect\b.*\bfrom\b)/i',
                '/(\bdrop\b.*\b(table|database)\b)/i',
                '/(\binsert\b.*\binto\b)/i',
                '/(\bupdate\b.*\bset\b)/i',
                '/(\bdelete\b.*\bfrom\b)/i',
                '/(\bexec\b|\bexecute\b)/i',
                '/(-{2}|\/\*|\*\/|xp_)/i',
                '/(;|\||&&)/i',
                '/(char\s*\(|ascii\s*\(|substring\s*\()/i',
            ],
            // XSS patterns
            'xss' => [
                '/<script[^>]*>.*?<\/script>/is',
                '/javascript:/i',
                '/on\w+\s*=/i',
                '/<iframe[^>]*>/i',
                '/<embed[^>]*>/i',
                '/<object[^>]*>/i',
                '/eval\s*\(/i',
                '/expression\s*\(/i',
                '/<img[^>]*on/i',
                '/style\s*=.*expression/i',
            ],
            // Path Traversal
            'path_traversal' => [
                '/\.\.\//i',
                '/\.\.%2f/i',
                '/\/etc\/passwd/i',
                '/\/etc\/shadow/i',
                '/\/windows\/system32/i',
                '/%2e%2e/i',
                '/\.\.\\\/i',
            ],
            // Command Injection
            'command_injection' => [
                '/[;&|`$()]/i',
                '/bash\s*-[a-z]/i',
                '/sh\s*-[a-z]/i',
                '/exec\s*\(/i',
                '/system\s*\(/i',
                '/passthru\s*\(/i',
                '/shell_exec\s*\(/i',
                '/proc_open\s*\(/i',
            ]
        ];
    }
    
    // ========================================================================
    // RATE LIMITING
    // ========================================================================
    
    /**
     * Check and enforce rate limiting
     * 
     * @param string $identifier User IP or session ID
     * @param int $limit Maximum requests
     * @param int $window Time window in seconds
     * @return bool True if allowed, false if rate limited
     */
    public function checkRateLimit($identifier = null, $limit = null, $window = null) {
        if (!$this->config['rate_limit_enabled']) {
            return true;
        }
        
        $identifier = $identifier ?? $this->getClientIP();
        $limit = $limit ?? $this->config['rate_limit_requests'];
        $window = $window ?? $this->config['rate_limit_window'];
        
        $cacheDir = __DIR__ . '/../../assets/cache/security';
        @mkdir($cacheDir, 0755, true);
        
        $rateFile = $cacheDir . '/rate_' . md5($identifier) . '.json';
        $now = time();
        
        $data = [];
        if (file_exists($rateFile)) {
            $content = file_get_contents($rateFile);
            $data = json_decode($content, true) ?? [];
            
            // Remove old entries
            $data['requests'] = array_filter(
                $data['requests'] ?? [],
                function($timestamp) use ($now, $window) {
                    return $timestamp > ($now - $window);
                }
            );
        }
        
        $requestCount = count($data['requests'] ?? []);
        
        if ($requestCount >= $limit) {
            $this->logSecurityEvent('rate_limit_exceeded', [
                'ip' => $identifier,
                'requests' => $requestCount,
                'limit' => $limit
            ]);
            
            // Block IP temporarily
            $this->blockIP($identifier, 3600); // 1 hour
            
            return false;
        }
        
        // Add current request
        $data['requests'][] = $now;
        $data['last_check'] = $now;
        
        file_put_contents($rateFile, json_encode($data));
        
        return true;
    }
    
    /**
     * Check if IP is rate limited
     */
    public function isRateLimited($ip = null) {
        $ip = $ip ?? $this->getClientIP();
        return isset($this->blockedIPs[$ip]) && $this->blockedIPs[$ip]['reason'] === 'rate_limit';
    }
    
    // ========================================================================
    // SQL INJECTION PROTECTION
    // ========================================================================
    
    /**
     * Detect SQL injection attempts
     * 
     * @param string $input User input
     * @return bool True if SQL injection detected
     */
    public function detectSQLInjection($input) {
        if (empty($input)) {
            return false;
        }
        
        foreach ($this->suspiciousPatterns['sql_injection'] as $pattern) {
            if (preg_match($pattern, $input)) {
                $this->logSecurityEvent('sql_injection_attempt', [
                    'ip' => $this->getClientIP(),
                    'input' => substr($input, 0, 100),
                    'pattern' => $pattern
                ]);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Prevent SQL injection by prepared statements
     * PDO prepared statements are the best defense
     */
    public function prepareStatement($pdo, $query, $params = []) {
        try {
            $stmt = $pdo->prepare($query);
            $stmt->execute($params);
            return $stmt;
        } catch (PDOException $e) {
            $this->logSecurityEvent('database_error', [
                'ip' => $this->getClientIP(),
                'error' => $e->getMessage()
            ]);
            return false;
        }
    }
    
    /**
     * Sanitize input to prevent SQL injection
     * NOTE: Use prepared statements instead when possible
     */
    public function sanitizeInput($input) {
        // Remove null bytes
        $input = str_replace(chr(0), '', $input);
        
        // Remove control characters
        $input = preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F]/', '', $input);
        
        // Escape dangerous characters
        $dangerous = ['--', '/*', '*/', 'xp_', 'sp_'];
        foreach ($dangerous as $char) {
            $input = str_ireplace($char, '', $input);
        }
        
        return $input;
    }
    
    // ========================================================================
    // XSS PROTECTION
    // ========================================================================
    
    /**
     * Detect XSS attempts
     * 
     * @param string $input User input
     * @return bool True if XSS detected
     */
    public function detectXSS($input) {
        if (empty($input)) {
            return false;
        }
        
        foreach ($this->suspiciousPatterns['xss'] as $pattern) {
            if (preg_match($pattern, $input)) {
                $this->logSecurityEvent('xss_attempt', [
                    'ip' => $this->getClientIP(),
                    'input' => substr($input, 0, 100)
                ]);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Filter XSS from input
     * 
     * @param string $input User input
     * @return string Filtered input
     */
    public function filterXSS($input) {
        if (empty($input)) {
            return $input;
        }
        
        // Remove script tags
        $input = preg_replace('/<script[^>]*>.*?<\/script>/is', '', $input);
        
        // Remove event handlers
        $input = preg_replace('/on\w+\s*=\s*["\'][^"\']*["\']/i', '', $input);
        
        // Remove dangerous HTML tags
        $dangerous_tags = ['script', 'iframe', 'embed', 'object', 'applet', 'form'];
        foreach ($dangerous_tags as $tag) {
            $input = preg_replace('/<' . $tag . '[^>]*>.*?<\/' . $tag . '>/is', '', $input);
        }
        
        return $input;
    }
    
    /**
     * Escape output for safe display
     * Use in HTML context
     */
    public function escapeOutput($output) {
        return htmlspecialchars($output, ENT_QUOTES, 'UTF-8');
    }
    
    /**
     * Escape for JavaScript context
     */
    public function escapeJS($output) {
        return json_encode($output);
    }
    
    /**
     * Escape for HTML attribute context
     */
    public function escapeAttr($output) {
        return htmlspecialchars($output, ENT_QUOTES, 'UTF-8');
    }
    
    // ========================================================================
    // BOT PROTECTION
    // ========================================================================
    
    /**
     * Detect bot/crawler
     * 
     * @return bool True if bot detected
     */
    public function detectBot() {
        $userAgent = $_SERVER['HTTP_USER_AGENT'] ?? '';
        
        // List of legitimate bots
        $legit_bots = [
            'googlebot',
            'bingbot',
            'yandexbot',
            'slurp',
            'duckduckbot',
            'baiduspider',
            'yajuicebot',
            'ahrefsbot',
            'mj12bot',
        ];
        
        foreach ($legit_bots as $bot) {
            if (stripos($userAgent, $bot) !== false) {
                return false; // Legitimate bot
            }
        }
        
        // List of malicious bots
        $malicious_bots = [
            'sqlmap',
            'nmap',
            'nessus',
            'nikto',
            'masscan',
            'acunetix',
            'burp',
            'metasploit',
            'havij',
            'sqlninja',
            'commix',
        ];
        
        foreach ($malicious_bots as $bot) {
            if (stripos($userAgent, $bot) !== false) {
                $this->logSecurityEvent('malicious_bot_detected', [
                    'ip' => $this->getClientIP(),
                    'user_agent' => $userAgent
                ]);
                return true;
            }
        }
        
        // Check for missing or empty user agent
        if (empty($userAgent)) {
            $this->logSecurityEvent('suspicious_no_user_agent', [
                'ip' => $this->getClientIP()
            ]);
        }
        
        return false;
    }
    
    /**
     * Implement CAPTCHA challenge for suspicious activity
     * 
     * @return bool True if CAPTCHA passed
     */
    public function requireCAPTCHA() {
        if (isset($_SESSION['captcha_verified']) && $_SESSION['captcha_verified'] === true) {
            return true;
        }
        
        // Store requirement in session
        $_SESSION['require_captcha'] = true;
        
        return false;
    }
    
    /**
     * Verify CAPTCHA (basic implementation)
     */
    public function verifyCAPTCHA($response) {
        // TODO: Integrate with reCAPTCHA v3 or hCaptcha
        // For now, just mark as verified
        $_SESSION['captcha_verified'] = true;
        return true;
    }
    
    // ========================================================================
    // IP BLOCKING
    // ========================================================================
    
    /**
     * Block an IP address
     * 
     * @param string $ip IP address to block
     * @param int $duration Block duration in seconds
     * @param string $reason Reason for blocking
     */
    public function blockIP($ip, $duration = 3600, $reason = 'suspicious_activity') {
        $cacheDir = __DIR__ . '/../../assets/cache/security';
        @mkdir($cacheDir, 0755, true);
        
        $blockFile = $cacheDir . '/blocked_ips.json';
        
        $blocked = [];
        if (file_exists($blockFile)) {
            $blocked = json_decode(file_get_contents($blockFile), true) ?? [];
        }
        
        $blocked[$ip] = [
            'blocked_at' => time(),
            'expires_at' => time() + $duration,
            'reason' => $reason
        ];
        
        file_put_contents($blockFile, json_encode($blocked));
        $this->blockedIPs[$ip] = $blocked[$ip];
    }
    
    /**
     * Check if IP is blocked
     * 
     * @param string $ip IP address to check
     * @return bool True if IP is blocked
     */
    public function isIPBlocked($ip = null) {
        $ip = $ip ?? $this->getClientIP();
        
        if (!isset($this->blockedIPs[$ip])) {
            return false;
        }
        
        $block = $this->blockedIPs[$ip];
        
        // Check if block has expired
        if (time() > $block['expires_at']) {
            unset($this->blockedIPs[$ip]);
            return false;
        }
        
        return true;
    }
    
    /**
     * Unblock an IP address
     */
    public function unblockIP($ip) {
        unset($this->blockedIPs[$ip]);
        
        $cacheDir = __DIR__ . '/../../assets/cache/security';
        $blockFile = $cacheDir . '/blocked_ips.json';
        
        if (file_exists($blockFile)) {
            $blocked = json_decode(file_get_contents($blockFile), true) ?? [];
            unset($blocked[$ip]);
            file_put_contents($blockFile, json_encode($blocked));
        }
    }
    
    // ========================================================================
    // SECURITY HEADERS
    // ========================================================================
    
    /**
     * Set security headers
     */
    public function setSecurityHeaders() {
        if (!$this->config['security_headers']) {
            return;
        }
        
        // Prevent clickjacking
        header('X-Frame-Options: SAMEORIGIN');
        
        // Prevent MIME type sniffing
        header('X-Content-Type-Options: nosniff');
        
        // Enable XSS protection in older browsers
        header('X-XSS-Protection: 1; mode=block');
        
        // Content Security Policy (strict)
        header("Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'");
        
        // Referrer Policy
        header('Referrer-Policy: strict-origin-when-cross-origin');
        
        // Permissions Policy (Feature-Policy)
        header('Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=()');
        
        // HSTS (HTTP Strict Transport Security)
        if (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on') {
            header('Strict-Transport-Security: max-age=31536000; includeSubDomains; preload');
        }
        
        // Remove server info
        header_remove('Server');
        header('Server: Secure Server');
        
        // Prevent caching of sensitive content
        header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
        header('Pragma: no-cache');
        header('Expires: 0');
    }
    
    // ========================================================================
    // INPUT VALIDATION
    // ========================================================================
    
    /**
     * Validate and sanitize all GET/POST parameters
     * 
     * @param string $source GET, POST, or COOKIE
     * @return array Cleaned parameters
     */
    public function validateAllInputs($source = 'all') {
        $inputs = [];
        
        if ($source === 'all' || $source === 'GET') {
            $inputs = array_merge($inputs, $this->validateInput($_GET));
        }
        
        if ($source === 'all' || $source === 'POST') {
            $inputs = array_merge($inputs, $this->validateInput($_POST));
        }
        
        if ($source === 'all' || $source === 'COOKIE') {
            $inputs = array_merge($inputs, $this->validateInput($_COOKIE));
        }
        
        return $inputs;
    }
    
    /**
     * Validate input array
     */
    private function validateInput($input) {
        $validated = [];
        
        foreach ($input as $key => $value) {
            if (is_array($value)) {
                $validated[$key] = $this->validateInput($value);
            } else {
                // Check for attacks
                if ($this->detectSQLInjection($value) || $this->detectXSS($value)) {
                    $this->logSecurityEvent('attack_in_input', [
                        'ip' => $this->getClientIP(),
                        'key' => $key,
                        'value' => substr($value, 0, 50)
                    ]);
                    
                    if ($this->config['block_suspicious']) {
                        $this->blockIP($this->getClientIP(), 3600);
                        http_response_code(403);
                        die('Access Denied: Suspicious Activity Detected');
                    }
                }
                
                // Sanitize
                $validated[$key] = $this->sanitizeInput($value);
                
                // Filter XSS
                if ($this->config['xss_filtering']) {
                    $validated[$key] = $this->filterXSS($validated[$key]);
                }
            }
        }
        
        return $validated;
    }
    
    /**
     * Validate email
     */
    public function validateEmail($email) {
        return filter_var($email, FILTER_VALIDATE_EMAIL) !== false;
    }
    
    /**
     * Validate URL
     */
    public function validateURL($url) {
        return filter_var($url, FILTER_VALIDATE_URL) !== false;
    }
    
    /**
     * Validate IP address
     */
    public function validateIP($ip) {
        return filter_var($ip, FILTER_VALIDATE_IP) !== false;
    }
    
    /**
     * Validate phone number
     */
    public function validatePhoneNumber($phone) {
        $phone = preg_replace('/[^0-9+\-()]/i', '', $phone);
        return strlen($phone) >= 10 && strlen($phone) <= 15;
    }
    
    // ========================================================================
    // LOGGING & MONITORING
    // ========================================================================
    
    /**
     * Log security events
     * 
     * @param string $event_type Type of security event
     * @param array $details Event details
     */
    public function logSecurityEvent($event_type, $details = []) {
        if (!$this->config['log_suspicious']) {
            return;
        }
        
        $cacheDir = __DIR__ . '/../../assets/cache/security';
        @mkdir($cacheDir, 0755, true);
        
        $logFile = $cacheDir . '/security_' . date('Y-m-d') . '.json';
        
        $logs = [];
        if (file_exists($logFile)) {
            $logs = json_decode(file_get_contents($logFile), true) ?? [];
        }
        
        $logs[] = [
            'timestamp' => date('Y-m-d H:i:s'),
            'event_type' => $event_type,
            'details' => $details
        ];
        
        file_put_contents($logFile, json_encode($logs, JSON_PRETTY_PRINT));
    }
    
    /**
     * Get security events
     */
    public function getSecurityEvents($days = 7) {
        $events = [];
        
        for ($i = 0; $i < $days; $i++) {
            $date = date('Y-m-d', strtotime("-$i days"));
            $logFile = __DIR__ . '/../../assets/cache/security/security_' . $date . '.json';
            
            if (file_exists($logFile)) {
                $logs = json_decode(file_get_contents($logFile), true) ?? [];
                $events = array_merge($events, $logs);
            }
        }
        
        return $events;
    }
    
    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================
    
    /**
     * Get client IP address
     * Handles proxies and cloud services
     */
    private function getClientIP() {
        $ip = '127.0.0.1';
        
        if (!empty($_SERVER['HTTP_CLIENT_IP'])) {
            $ip = $_SERVER['HTTP_CLIENT_IP'];
        } elseif (!empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
            // Handle multiple IPs in X-Forwarded-For
            $ips = explode(',', $_SERVER['HTTP_X_FORWARDED_FOR']);
            $ip = trim($ips[0]);
        } elseif (!empty($_SERVER['REMOTE_ADDR'])) {
            $ip = $_SERVER['REMOTE_ADDR'];
        }
        
        // Validate IP
        if (!$this->validateIP($ip)) {
            $ip = '127.0.0.1';
        }
        
        return $ip;
    }
    
    /**
     * Get client's browser fingerprint
     */
    public function getClientFingerprint() {
        return hash('sha256', 
            ($_SERVER['HTTP_USER_AGENT'] ?? '') .
            ($_SERVER['HTTP_ACCEPT_LANGUAGE'] ?? '') .
            ($_SERVER['HTTP_ACCEPT_ENCODING'] ?? '')
        );
    }
    
    /**
     * Detect if using Cloudflare
     */
    public function isUsingCloudflare() {
        return !empty($_SERVER['HTTP_CF_RAY']);
    }
    
    /**
     * Get Cloudflare client IP
     */
    public function getCloudflareIP() {
        if ($this->isUsingCloudflare()) {
            return $_SERVER['HTTP_CF_CONNECTING_IP'] ?? $this->getClientIP();
        }
        return $this->getClientIP();
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get WAF instance
 */
function getWAF() {
    return WAFSecuritySystem::getInstance();
}

/**
 * Initialize WAF protection
 * Call this in header.php
 */
function initializeWAF() {
    $waf = getWAF();
    
    // Set security headers
    $waf->setSecurityHeaders();
    
    // Check if IP is blocked
    if ($waf->isIPBlocked()) {
        http_response_code(403);
        die('Access Denied: Your IP has been blocked');
    }
    
    // Check rate limiting
    if (!$waf->checkRateLimit()) {
        http_response_code(429);
        die('Too Many Requests: Rate limit exceeded');
    }
    
    // Check for malicious bots
    if ($waf->detectBot()) {
        http_response_code(403);
        die('Access Denied: Malicious bot detected');
    }
    
    return $waf;
}

/**
 * Escape output safely
 */
function esc($output) {
    return getWAF()->escapeOutput($output);
}

/**
 * Validate input
 */
function validateInput($input, $type = 'text') {
    $waf = getWAF();
    
    if ($type === 'email') {
        return $waf->validateEmail($input);
    } elseif ($type === 'url') {
        return $waf->validateURL($input);
    } elseif ($type === 'ip') {
        return $waf->validateIP($input);
    } elseif ($type === 'phone') {
        return $waf->validatePhoneNumber($input);
    }
    
    return true;
}

?>
