<?php
/**
 * Security Middleware for Request Processing
 * 
 * Validate and sanitize all requests before processing
 * Include in header.php: require_once('admin/inc/SecurityMiddleware.php');
 */

class SecurityMiddleware {
    private $waf;
    private $errors = [];
    
    public function __construct() {
        require_once('WAFSecuritySystem.php');
        $this->waf = getWAF();
    }
    
    /**
     * Process request through security checks
     */
    public function processRequest() {
        // Initialize security
        $this->waf->setSecurityHeaders();
        
        // Check blocked IP
        if ($this->waf->isIPBlocked()) {
            $this->denialOfAccess('Your IP has been blocked due to suspicious activity');
        }
        
        // Check rate limiting
        if (!$this->waf->checkRateLimit()) {
            $this->denialOfAccess('Rate limit exceeded. Please try again later');
        }
        
        // Check for malicious bots
        if ($this->waf->detectBot()) {
            $this->denialOfAccess('Access denied: Suspicious bot detected');
        }
        
        // Validate all inputs
        $this->validateAllInputs();
        
        // Set security session
        $this->setSecuritySession();
    }
    
    /**
     * Validate GET, POST, COOKIE parameters
     */
    private function validateAllInputs() {
        // Check GET parameters
        foreach ($_GET as $key => $value) {
            if ($this->waf->detectSQLInjection($value)) {
                $this->logAttack('SQL Injection', $key, $value);
                $this->denyRequest('Invalid input detected in GET parameter');
            }
            
            if ($this->waf->detectXSS($value)) {
                $this->logAttack('XSS Attack', $key, $value);
                $this->denyRequest('Invalid input detected in GET parameter');
            }
        }
        
        // Check POST parameters
        foreach ($_POST as $key => $value) {
            if (is_array($value)) {
                $this->validateArray($value, $key, 'POST');
            } else {
                if ($this->waf->detectSQLInjection($value)) {
                    $this->logAttack('SQL Injection', $key, $value);
                    $this->denyRequest('Invalid input detected in POST parameter');
                }
                
                if ($this->waf->detectXSS($value)) {
                    $this->logAttack('XSS Attack', $key, $value);
                    $this->denyRequest('Invalid input detected in POST parameter');
                }
            }
        }
        
        // Check COOKIE values
        foreach ($_COOKIE as $key => $value) {
            if ($this->waf->detectSQLInjection($value)) {
                $this->logAttack('SQL Injection', $key, $value);
                $this->denyRequest('Invalid cookie detected');
            }
        }
    }
    
    /**
     * Validate array values recursively
     */
    private function validateArray($array, $parentKey, $source) {
        foreach ($array as $key => $value) {
            if (is_array($value)) {
                $this->validateArray($value, $parentKey . '[' . $key . ']', $source);
            } else {
                if ($this->waf->detectSQLInjection($value)) {
                    $this->logAttack('SQL Injection', $parentKey . '[' . $key . ']', $value);
                    $this->denyRequest('Invalid input detected in ' . $source . ' parameter');
                }
                
                if ($this->waf->detectXSS($value)) {
                    $this->logAttack('XSS Attack', $parentKey . '[' . $key . ']', $value);
                    $this->denyRequest('Invalid input detected in ' . $source . ' parameter');
                }
            }
        }
    }
    
    /**
     * Set security-related session values
     */
    private function setSecuritySession() {
        // Set session ID
        if (!isset($_SESSION['security_session_id'])) {
            $_SESSION['security_session_id'] = session_id();
        }
        
        // Set client fingerprint
        if (!isset($_SESSION['client_fingerprint'])) {
            $_SESSION['client_fingerprint'] = $this->waf->getClientFingerprint();
        }
        
        // Check for session hijacking
        if (isset($_SESSION['client_fingerprint'])) {
            if ($_SESSION['client_fingerprint'] !== $this->waf->getClientFingerprint()) {
                $this->logAttack('Session Hijacking', 'fingerprint', 'Mismatch detected');
                session_destroy();
                $this->denyRequest('Security violation detected');
            }
        }
        
        // Set CSRF token if not exists
        if (!isset($_SESSION['csrf_token'])) {
            $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
        }
    }
    
    /**
     * Get CSRF token
     */
    public function getCSRFToken() {
        return $_SESSION['csrf_token'] ?? '';
    }
    
    /**
     * Verify CSRF token
     */
    public function verifyCSRFToken($token) {
        if (empty($token) || !isset($_SESSION['csrf_token'])) {
            return false;
        }
        
        return hash_equals($_SESSION['csrf_token'], $token);
    }
    
    /**
     * Generate CSRF form field
     */
    public function getCSRFField() {
        $token = $this->getCSRFToken();
        return '<input type="hidden" name="csrf_token" value="' . htmlspecialchars($token) . '">';
    }
    
    /**
     * Log attack for review
     */
    private function logAttack($type, $parameter, $value) {
        $this->waf->logSecurityEvent('security_violation', [
            'type' => $type,
            'parameter' => $parameter,
            'value' => substr($value, 0, 100),
            'ip' => $_SERVER['REMOTE_ADDR'] ?? 'Unknown',
            'timestamp' => date('Y-m-d H:i:s')
        ]);
    }
    
    /**
     * Deny access to request
     */
    private function denyRequest($message) {
        // Block the IP for repeated offenses
        $this->waf->blockIP($_SERVER['REMOTE_ADDR'], 3600);
        
        $this->denialOfAccess($message);
    }
    
    /**
     * Display denial of access page
     */
    private function denialOfAccess($message) {
        http_response_code(403);
        ?>
        <!DOCTYPE html>
        <html>
        <head>
            <title>Access Denied</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    margin: 0;
                    padding: 20px;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                }
                .container {
                    background: white;
                    padding: 40px;
                    border-radius: 12px;
                    box-shadow: 0 8px 16px rgba(0,0,0,0.2);
                    max-width: 500px;
                    text-align: center;
                }
                h1 {
                    color: #721c24;
                    margin: 0 0 20px 0;
                }
                p {
                    color: #666;
                    line-height: 1.6;
                    margin: 15px 0;
                }
                .error-code {
                    color: #999;
                    font-size: 0.9em;
                    margin-top: 30px;
                    padding-top: 20px;
                    border-top: 1px solid #eee;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🔒 Access Denied</h1>
                <p><?php echo htmlspecialchars($message); ?></p>
                <p>If you believe this is an error, please contact support.</p>
                <div class="error-code">
                    Error ID: <?php echo md5($_SERVER['REMOTE_ADDR'] . time()); ?>
                </div>
            </div>
        </body>
        </html>
        <?php
        exit;
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Initialize security middleware
 * Call this at the top of header.php
 */
function initializeSecurityMiddleware() {
    $middleware = new SecurityMiddleware();
    $middleware->processRequest();
    return $middleware;
}

/**
 * Get CSRF token for forms
 */
function csrf_token() {
    global $security_middleware;
    if (!isset($security_middleware)) {
        $security_middleware = new SecurityMiddleware();
    }
    return $security_middleware->getCSRFToken();
}

/**
 * Get CSRF form field
 */
function csrf_field() {
    global $security_middleware;
    if (!isset($security_middleware)) {
        $security_middleware = new SecurityMiddleware();
    }
    return $security_middleware->getCSRFField();
}

/**
 * Verify CSRF token
 */
function verify_csrf_token($token) {
    global $security_middleware;
    if (!isset($security_middleware)) {
        $security_middleware = new SecurityMiddleware();
    }
    return $security_middleware->verifyCSRFToken($token);
}

?>
