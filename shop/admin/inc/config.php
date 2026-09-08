<?php
// Error Reporting Turn On
// Error Reporting
ini_set('error_reporting', E_ALL);

// Setting up the time zone
date_default_timezone_set('America/Los_Angeles'); // Or your desired timezone
// Timezone setup
date_default_timezone_set('Asia/Dhaka');

// Host Name
$dbhost = 'localhost'; // Make sure this is correct for your setup
// Database Name
$dbname = 'ecommerceweb'; // Make sure this is correct
// Database Username
$dbuser = 'root';        // Make sure this is correct
// Database Password
$dbpass = '';        // Make sure this is correct
// ----------------------------------------------------------------------------
// 1. Environment & Configuration Loader
// Checks system getenv, $_ENV, and optional .env file in shop root
// ----------------------------------------------------------------------------
$env_file = dirname(dirname(__DIR__)) . DIRECTORY_SEPARATOR . '.env';
if (file_exists($env_file)) {
    $lines = file($env_file, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    foreach ($lines as $line) {
        if (strpos(trim($line), '#') === 0) continue;
        if (strpos($line, '=') !== false) {
            list($name, $value) = explode('=', $line, 2);
            $name = trim($name);
            $value = trim($value, " \t\n\r\0\x0B\"'");
            if (!getenv($name)) {
                putenv("{$name}={$value}");
                $_ENV[$name] = $value;
            }
        }
    }
}

// Establish PDO connection
// ----------------------------------------------------------------------------
// 2. Database Driver Selection (Supabase PostgreSQL / MySQL Fallback)
// ----------------------------------------------------------------------------
$db_url = getenv('DATABASE_URL');
$db_driver = getenv('DB_DRIVER'); // 'pgsql' or 'mysql'

// Auto-detect Supabase if PostgreSQL host or credentials are provided
if ($db_url && strpos($db_url, 'postgres') === 0) {
    $db_driver = 'pgsql';
    $db_parts = parse_url($db_url);
    $dbhost = $db_parts['host'] ?? 'localhost';
    $dbport = $db_parts['port'] ?? 6543;
    $dbuser = $db_parts['user'] ?? 'postgres';
    $dbpass = $db_parts['pass'] ?? '';
    $dbname = ltrim($db_parts['path'] ?? 'postgres', '/');
} elseif (getenv('SUPABASE_DB_HOST') || $db_driver === 'pgsql') {
    $db_driver = 'pgsql';
    $dbhost = getenv('SUPABASE_DB_HOST') ?: 'aws-0-ap-southeast-1.pooler.supabase.com';
    $dbport = getenv('SUPABASE_DB_PORT') ?: '6543';
    $dbname = getenv('SUPABASE_DB_NAME') ?: 'postgres';
    $dbuser = getenv('SUPABASE_DB_USER') ?: 'postgres';
    $dbpass = getenv('SUPABASE_DB_PASSWORD') ?: '';
} else {
    // Default local development fallback
    $db_driver = 'mysql';
    $dbhost = getenv('DB_HOST') ?: 'localhost';
    $dbport = getenv('DB_PORT') ?: '3306';
    $dbname = getenv('DB_NAME') ?: 'ecommerceweb';
    $dbuser = getenv('DB_USER') ?: 'root';
    $dbpass = getenv('DB_PASS') ?: '';
}

// ----------------------------------------------------------------------------
// 3. Establish PDO Connection
// ----------------------------------------------------------------------------
$pdo = null;
try {
    if ($db_driver === 'pgsql') {
        $sslmode = getenv('DB_SSLMODE') ?: 'require';
        $dsn = "pgsql:host={$dbhost};port={$dbport};dbname={$dbname};sslmode={$sslmode}";
        $pdo = new PDO($dsn, $dbuser, $dbpass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        // Register MySQL rand() alias in PostgreSQL for seamless query compatibility
        try {
            $pdo->exec("CREATE OR REPLACE FUNCTION rand() RETURNS double precision AS \$\$ BEGIN RETURN random(); END; \$\$ LANGUAGE plpgsql;");
        } catch (Exception $e) {}
    } else {
        $dsn = "mysql:host={$dbhost};port={$dbport};dbname={$dbname};charset=utf8mb4";
        $pdo = new PDO($dsn, $dbuser, $dbpass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
    }
} catch (PDOException $exception) {
    error_log("Database connection error: " . $exception->getMessage());
    die("Database Connection Error: Could not connect to {$db_driver} database. " . htmlspecialchars($exception->getMessage()));
}

define('DB_DRIVER_NAME', $db_driver);
define('SQL_RAND', ($db_driver === 'pgsql') ? 'RANDOM()' : 'RAND()');

// ----------------------------------------------------------------------------
// 4. Supabase REST API & Realtime Credentials (For Edge Functions / REST calls)
// ----------------------------------------------------------------------------
define('SUPABASE_URL', getenv('SUPABASE_URL') ?: '');
define('SUPABASE_ANON_KEY', getenv('SUPABASE_ANON_KEY') ?: '');
define('SUPABASE_SERVICE_KEY', getenv('SUPABASE_SERVICE_ROLE_KEY') ?: '');
define('MERCHANT_ID', getenv('MERCHANT_ID') ?: '');

// ----------------------------------------------------------------------------
// 5. Dynamic BASE_URL Resolution
// ----------------------------------------------------------------------------
$BASE_URL = '';
$protocol = (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on') || (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && $_SERVER['HTTP_X_FORWARDED_PROTO'] === 'https') ? "https" : "http";
$host = $_SERVER['HTTP_HOST'] ?? 'localhost';

try {
    $statement = $pdo->prepare("SELECT BASE_URL FROM tbl_settings WHERE id=1 LIMIT 1");
    $statement->execute();
    $result = $statement->fetch(PDO::FETCH_ASSOC);
    if ($result && !empty($result['BASE_URL'])) {
        $BASE_URL = rtrim($result['BASE_URL'], '/') . '/';
    } else {
        // Check store_settings if tbl_settings has no BASE_URL
        try {
            $stmtStore = $pdo->prepare("SELECT store_slug FROM store_settings LIMIT 1");
            $stmtStore->execute();
            $store = $stmtStore->fetch(PDO::FETCH_ASSOC);
            if ($store && !empty($store['store_slug'])) {
                $BASE_URL = "{$protocol}://{$host}/";
            }
        } catch (Exception $ignored) {}
    }
} catch (Exception $e) {
    error_log("Notice: Unable to query BASE_URL from settings: " . $e->getMessage());
}

// Defining base url
if (empty($BASE_URL)) {
    $script_dir = str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME']));
    // Strip /admin or /payment from base path if present
    $base_path = preg_replace('#/(admin|payment/.*|ajax)$#', '', $script_dir);
    $base_path = rtrim($base_path, '/') . '/';
    $BASE_URL = "{$protocol}://{$host}{$base_path}";
}

define('BASE_URL', $BASE_URL);

?>