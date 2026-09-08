<?php
// search_suggestions_improved.php
// This script provides product name suggestions for the search bar.
// IMPROVED VERSION with enhanced security, validation, and error handling

// Include the database connection and configuration
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

// Set response headers
header('Content-Type: application/json; charset=UTF-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');
header('X-Content-Type-Options: nosniff');

// Initialize response array
$response = [
    'success' => false,
    'suggestions' => [],
    'debug' => []
];

// DIAGNOSTIC LOGGING - Remove in production
$logFile = 'search_suggestions_debug.log';
$debugInfo = [
    'timestamp' => date('Y-m-d H:i:s'),
    'request_method' => $_SERVER['REQUEST_METHOD'] ?? 'UNKNOWN',
    'query_raw' => $_GET['query'] ?? 'NOT_SET',
    'query_type' => gettype($_GET['query'] ?? null),
    'query_length' => isset($_GET['query']) ? strlen($_GET['query']) : 0
];

// ============================================================================
// IMPROVEMENT 1: Request Method Validation
// ============================================================================
// Only accept GET requests for security and RESTful best practices
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405); // Method Not Allowed
    $response['error'] = 'Only GET requests are allowed';
    $debugInfo['error'] = 'Invalid request method';
    file_put_contents($logFile, json_encode($debugInfo) . PHP_EOL, FILE_APPEND);
    echo json_encode($response);
    exit;
}

// ============================================================================
// IMPROVEMENT 2: Enhanced Input Validation and Sanitization
// ============================================================================
// Original code:
// $query = isset($_GET['query']) ? trim($_GET['query']) : '';

// Improved version with multiple validation layers:
$query = '';
$validationErrors = [];

// Step 1: Check if parameter exists and is a string
if (!isset($_GET['query'])) {
    $validationErrors[] = 'Query parameter is missing';
} elseif (!is_string($_GET['query'])) {
    $validationErrors[] = 'Query parameter must be a string';
    $debugInfo['validation_error'] = 'Non-string query parameter';
} else {
    // Step 2: Sanitize and validate the input
    $rawQuery = $_GET['query'];
    
    // Remove whitespace from beginning and end
    $query = trim($rawQuery);
    
    // Step 3: Validate length constraints
    $minLength = 2;
    $maxLength = 100; // Prevent DoS attacks with extremely long queries
    
    if (strlen($query) > $maxLength) {
        $validationErrors[] = "Query too long (max {$maxLength} characters)";
        $debugInfo['validation_error'] = 'Query exceeds maximum length';
        $query = substr($query, 0, $maxLength); // Truncate instead of rejecting
    }
    
    // Step 4: Remove potentially dangerous characters
    // Allow alphanumeric, spaces, hyphens, and common punctuation
    $query = preg_replace('/[^\p{L}\p{N}\s\-_.,&()]/u', '', $query);
    
    // Step 5: Additional sanitization for SQL safety (defense in depth)
    // Note: We use prepared statements, but this adds an extra layer
    $query = htmlspecialchars($query, ENT_QUOTES, 'UTF-8');
    
    // Step 6: Normalize whitespace
    $query = preg_replace('/\s+/', ' ', $query);
    
    $debugInfo['query_sanitized'] = $query;
    $debugInfo['query_final_length'] = strlen($query);
}

// ============================================================================
// IMPROVEMENT 3: Better Array Initialization with Type Safety
// ============================================================================
// Original code:
// $suggestions = [];

// Improved version with explicit typing and structure
$suggestions = [];
$metadata = [
    'query' => $query,
    'result_count' => 0,
    'execution_time' => 0,
    'cached' => false
];

// Log validation errors
if (!empty($validationErrors)) {
    $debugInfo['validation_errors'] = $validationErrors;
}

// Write diagnostic log
file_put_contents($logFile, json_encode($debugInfo) . PHP_EOL, FILE_APPEND);

// ============================================================================
// IMPROVEMENT 4: Early Return for Invalid Input
// ============================================================================
if (!empty($validationErrors) && strlen($query) === 0) {
    http_response_code(400); // Bad Request
    $response['error'] = 'Invalid query parameter';
    $response['details'] = $validationErrors;
    echo json_encode($response);
    exit;
}

// ============================================================================
// IMPROVEMENT 5: Enhanced Query Execution with Better Error Handling
// ============================================================================
$startTime = microtime(true);

if (strlen($query) >= 2) {
    try {
        // Search in product names and descriptions
        $statement = $pdo->prepare(
            "SELECT DISTINCT
                p_id, 
                p_name,
                p_featured_photo,
                p_current_price,
                ecat_id
             FROM tbl_product 
             WHERE (p_name LIKE ? OR p_short_description LIKE ?) 
             AND p_is_active = 1
             ORDER BY p_name ASC
             LIMIT 15"
        );
        
        $searchTerm = '%' . $query . '%';
        $statement->execute([$searchTerm, $searchTerm]);
        $results = $statement->fetchAll(PDO::FETCH_ASSOC);
        
        // Format suggestions with additional info
        foreach ($results as $product) {
            $suggestions[] = [
                'id' => (int)$product['p_id'],
                'name' => htmlspecialchars($product['p_name'], ENT_QUOTES, 'UTF-8'),
                'price' => number_format((float)$product['p_current_price'], 2),
                'image' => !empty($product['p_featured_photo']) ? htmlspecialchars($product['p_featured_photo'], ENT_QUOTES, 'UTF-8') : '',
                'url' => BASE_URL . 'product.php?id=' . (int)$product['p_id']
            ];
        }
        
        $metadata['result_count'] = count($suggestions);
        $response['success'] = true;
        
    } catch (PDOException $e) {
        // Log the actual error for debugging (don't expose to client)
        error_log("Search suggestions DB error: " . $e->getMessage());
        file_put_contents($logFile, json_encode([
            'timestamp' => date('Y-m-d H:i:s'),
            'error_type' => 'PDOException',
            'error_message' => $e->getMessage(),
            'query' => $query
        ]) . PHP_EOL, FILE_APPEND);
        
        // Return generic error response
        http_response_code(500);
        $response['error'] = 'An error occurred while searching. Please try again.';
        echo json_encode($response);
        exit;
    } catch (Exception $e) {
        // Catch any other unexpected errors
        error_log("Search suggestions unexpected error: " . $e->getMessage());
        http_response_code(500);
        $response['error'] = 'An unexpected error occurred';
        echo json_encode($response);
        exit;
    }
} else {
    // Query too short - return empty results with success status
    $response['success'] = true;
    $response['message'] = 'Query must be at least 2 characters';
}

// Calculate execution time
$metadata['execution_time'] = round((microtime(true) - $startTime) * 1000, 2) . 'ms';

// Build final response
$response['suggestions'] = $suggestions;
$response['metadata'] = $metadata;

// Return JSON response
echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
?>
