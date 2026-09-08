<?php
require_once('Parsedown.php');
header('Content-Type: application/json');
error_reporting(E_ALL);
ini_set('display_errors', 1);

// More detailed error logging
ini_set('log_errors', 1);
ini_set('error_log', dirname(__FILE__) . '/php_errors.log');

// Debug function
function log_debug($message) {
    $log_file = dirname(__FILE__) . '/debug.log';
    $timestamp = date('Y-m-d H:i:s');
    file_put_contents($log_file, "[{$timestamp}] {$message}\n", FILE_APPEND);
}

log_debug("=== Script Started ===");

// 1. CONFIGURATION & DATABASE CONNECTION
$config_path = dirname(__FILE__) . '/admin/inc/config.php';
if (!file_exists($config_path)) {
    log_debug("ERROR: Config file not found at: {$config_path}");
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Configuration file missing']);
    exit();
}

require_once($config_path);

// 2. INPUT VALIDATION
if (!isset($_POST['prompt']) || empty($_POST['prompt']) || !isset($_POST['product_id']) || empty($_POST['product_id'])) {
    http_response_code(400);
    $error_message = "Error: Missing or empty POST data. Required: prompt and product_id";
    log_debug($error_message);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    exit();
}

$prompt = trim($_POST['prompt']);
$product_id = intval($_POST['product_id']);

log_debug("Processing - Prompt: '{$prompt}', Product ID: {$product_id}");

// 3. FETCH API KEY FROM DATABASE
try {
    if (!isset($pdo) || !($pdo instanceof PDO)) {
        throw new Exception("Database connection not established");
    }
    
    $statement = $pdo->prepare("SELECT gemini_api_key FROM tbl_settings WHERE id = 1");
    $statement->execute();
    $settings = $statement->fetch(PDO::FETCH_ASSOC);
    
    if (!$settings) {
        throw new Exception("No settings found in database");
    }
    
    $gemini_api_key = trim($settings['gemini_api_key'] ?? '');
    
    // Safety check for empty key
    if (empty($gemini_api_key)) {
        throw new Exception("Gemini API key is not configured in the database.");
    }

} catch (Exception $e) {
    http_response_code(500);
    $error_message = "Database error: " . $e->getMessage();
    log_debug($error_message);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    exit();
}

// 4. FETCH PRODUCT DETAILS
try {
    $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE p_id = ? AND p_is_active = 1");
    $statement->execute([$product_id]);
    $product_details = $statement->fetch(PDO::FETCH_ASSOC);

    if (!$product_details) {
        http_response_code(404);
        $error_message = "Error: Product with ID '{$product_id}' not found or not active.";
        log_debug($error_message);
        echo json_encode(['status' => 'error', 'message' => $error_message]);
        exit();
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    $error_message = "Database error fetching product: " . $e->getMessage();
    log_debug($error_message);
    echo json_encode(['status' => 'error', 'message' => 'Database error.']);
    exit();
}

// 5. PREPARE THE PROMPT
// NEW STRUCTURED & CONVINCING PROMPT
$product_prompt_template = "You are an expert sales consultant for our online store. Your goal is to provide a smart, well-structured, and highly convincing response to a customer query.\n\n" .
    "### INSTRUCTIONS:\n" .
    "1. **Direct Answer First:** Start by answering the User's Query directly,shortly and use two line break for each paragraph. Make sure to answer the user's query in a friendly and clearly and humanlike, don't cross 25 word  of response   .\n" .
    "2. **Smart Structure:** Use bold headings and bullet points to make the information easy to read. you can use emojis to make it more engaging. you can ask relevant questions to the user to make the conversation more engaging.\n" .
    "3. **Persuasive Tone:** Highlight the benefits of the product and create a sense of value. Mention that we have stock available if the count is high.\n" .
    "4. **Factual:** Use only the product details provided below. Do not invent features.\n\n" .
    "### PRODUCT DATA:\n" .
    "- **Name:** {$product_details['p_name']}\n" .
    "- **Current Price:** {$product_details['p_current_price']}\n" .
    "- **Stock Status:** {$product_details['p_qty']} units available\n" .
    "- **Product Details:** " . strip_tags($product_details['p_description']) . "\n\n" .
    "### CUSTOMER INQUIRY:\n" .
    "\"{$prompt}\"\n\n" .
    "### YOUR PROFESSIONAL RESPONSE:";
// 6. API REQUEST CONFIGURATION
// UPDATED: Using 'gemini-2.5-flash' because 1.5 is deprecated/shut down.
// This model is Free Tier eligible and highly efficient.
$model_id = 'gemini-2.5-flash'; 

$url = "https://generativelanguage.googleapis.com/v1beta/models/" . $model_id . ":generateContent?key=" . $gemini_api_key;

$data = [
    'contents' => [
        [
            'parts' => [
                ['text' => $product_prompt_template]
            ]
        ]
    ],
    // Optional: Safety settings to prevent blocking legitimate product text
    'safetySettings' => [
        [
            'category' => 'HARM_CATEGORY_HARASSMENT',
            'threshold' => 'BLOCK_ONLY_HIGH'
        ]
    ]
];
$payload = json_encode($data);

log_debug("Using Model: " . $model_id);

// 7. EXECUTE API CALL (cURL)
$ch = curl_init();
curl_setopt_array($ch, [
    CURLOPT_URL => $url,
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $payload,
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT => 30,
    CURLOPT_SSL_VERIFYPEER => false,
    CURLOPT_SSL_VERIFYHOST => false
]);

$response_from_api = curl_exec($ch);
$http_status = curl_getinfo($ch, CURLINFO_HTTP_CODE);

if (curl_errno($ch)) {
    $error_message = "cURL Error: " . curl_error($ch);
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'API connection failed. ' . $error_message]);
    curl_close($ch);
    exit;
}

curl_close($ch);

// 8. HANDLE API RESPONSE
$result = json_decode($response_from_api, true);

if ($http_status >= 400) {
    // Detailed error parsing for debugging
    $api_msg = $result['error']['message'] ?? 'Unknown API error';
    $error_message = 'API Error (' . $http_status . '): ' . $api_msg;
    
    // Log the full response to help you debug future model issues
    log_debug("API Failed Response: " . $response_from_api);
    
    http_response_code($http_status);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    exit;
}

if (isset($result['candidates'][0]['content']['parts'][0]['text'])) {
    $ai_markdown = $result['candidates'][0]['content']['parts'][0]['text'];
    
    // 2. Create the Parsedown object
    $Parsedown = new Parsedown();
    
    // 3. Convert Markdown to clean HTML
    $ai_html = $Parsedown->text($ai_markdown);
    
    // 4. Send the HTML response back
    echo json_encode(['status' => 'success', 'response' => $ai_html]);
    log_debug("Success: HTML response generated via Parsedown.");
} else {
    // Fallback if structure is unexpected
    $error_message = 'AI could not generate a valid response.';
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    log_debug("Unexpected Response Structure: " . $response_from_api);
    exit;
}

log_debug("=== Script Completed ===");
?>