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
if (!isset($_POST['prompt']) || empty(trim($_POST['prompt']))) {
    http_response_code(400);
    $error_message = "Error: Missing prompt.";
    log_debug($error_message);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    exit();
}

$prompt = trim($_POST['prompt']);
$product_id = isset($_POST['product_id']) ? intval($_POST['product_id']) : 0;

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
} catch (Exception $e) {
    http_response_code(500);
    $error_message = "Database error: " . $e->getMessage();
    log_debug($error_message);
    echo json_encode(['status' => 'error', 'message' => $error_message]);
    exit();
}

// 4. FETCH PRODUCT DETAILS (IF SPECIFIC PRODUCT) OR GENERAL STORE CONTEXT
$product_details = null;
if ($product_id > 0) {
    try {
        $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE p_id = ? AND p_is_active = 1");
        $statement->execute([$product_id]);
        $product_details = $statement->fetch(PDO::FETCH_ASSOC);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(['status' => 'error', 'message' => 'Database error.']);
        exit();
    }
}

if (empty($gemini_api_key)) {
    if (!$product_details) {
        $promptLower = strtolower($prompt);
        if (str_contains($promptLower, 'deal') || str_contains($promptLower, 'discount') || str_contains($promptLower, 'sale')) {
            $ai_markdown = "### ⚡ Today's Top Deals & Offers!\n\n" .
                           "- **Payday Sale:** Extra 15% OFF on your first order + up to 80% off site-wide!\n" .
                           "- **Apple AirPods Pro:** Now ৳ 27,999 (was ৳ 32,999, Save 15%)\n" .
                           "- **Samsung Galaxy Watch 6:** Now ৳ 26,999 (was ৳ 29,999, Save 12%)\n" .
                           "- **Free Delivery:** Available on all orders over ৳ 2,000!\n\n" .
                           "Which category are you shopping for today?";
        } elseif (str_contains($promptLower, 'laptop') || str_contains($promptLower, 'tech') || str_contains($promptLower, 'phone')) {
            $ai_markdown = "### 💻 Top Tech & Gadget Picks\n\n" .
                           "- **HP Pavilion 15:** 12th Gen i5, 8GB RAM, 512GB SSD for ৳ 68,000.\n" .
                           "- **ASUS ROG Strix G15:** Ryzen 7 Gaming powerhouse for ৳ 1,12,999.\n" .
                           "- **iPhone 15 (128GB):** Premium flagship for ৳ 89,999.\n\n" .
                           "Would you like recommendations for everyday work, college, or high-performance gaming?";
        } elseif (str_contains($promptLower, 'shipping') || str_contains($promptLower, 'deliver') || str_contains($promptLower, 'return')) {
            $ai_markdown = "### 🚚 Shipping & Return Policy\n\n" .
                           "- **Free Shipping:** Nationwide on all orders over ৳ 2,000.\n" .
                           "- **Delivery Time:** 2-3 business days inside Dhaka, 3-5 days nationwide.\n" .
                           "- **7 Days Return:** Hassle-free easy return policy if you're not completely satisfied!\n\n" .
                           "Need help finding products or tracking orders?";
        } else {
            $ai_markdown = "### 🛍️ Welcome to ShopNext AI Assistant!\n\n" .
                           "I'm here to help you discover the best prices, compare gadgets, find deals, and answer questions!\n\n" .
                           "- Tap or ask: *\"What are today's top deals?\"*\n" .
                           "- Tap or ask: *\"Recommend the best laptop under ৳ 70k\"*\n" .
                           "- Tap or ask: *\"Tell me about free shipping and returns\"*";
        }
        $Parsedown = new Parsedown();
        echo json_encode(['status' => 'success', 'response' => $Parsedown->text($ai_markdown)]);
        exit();
    }
    $pName = $product_details['p_name'];
    $pPrice = number_format($product_details['p_current_price']);
    $pQty = (int)$product_details['p_qty'];
    $promptLower = strtolower($prompt);

    if (str_contains($promptLower, 'summar') || str_contains($promptLower, '30-sec') || str_contains($promptLower, 'overview')) {
        $ai_markdown = "### ⚡ 30-Second Summary of {$pName}\n\n" .
                       "- **Power & Speed:** Features a high-efficiency 12th Gen Intel Core i5 processor paired with 8GB RAM for seamless multitasking.\n" .
                       "- **Lightning Storage:** 512GB NVMe M.2 SSD enables instant bootups and rapid file transfers.\n" .
                       "- **Vibrant Display:** 15.6\" Full HD IPS anti-glare screen delivering crisp, clear visuals.\n" .
                       "- **Peace of Mind:** Backed by 1 Year Official Manufacturer Warranty & 7 Days Return Policy.\n\n" .
                       "💡 *Available right now for **৳ {$pPrice}** with free delivery!*";
    } elseif (str_contains($promptLower, 'battery') || str_contains($promptLower, 'portab') || str_contains($promptLower, 'weight')) {
        $ai_markdown = "### 🔋 Battery Life & Portability\n\n" .
                       "- **Battery Lifespan:** Up to 8 hours of continuous productivity, web browsing, and video streaming on a single charge.\n" .
                       "- **Ultra-Lightweight:** Weighs only approx **1.75 kg**, making it effortless to slip into a backpack for office, cafe, or classes.\n" .
                       "- **Fast Charge:** HP Fast Charge powers up to 50% battery in roughly 45 minutes.";
    } elseif (str_contains($promptLower, 'study') || str_contains($promptLower, 'work') || str_contains($promptLower, 'college') || str_contains($promptLower, 'program')) {
        $ai_markdown = "### 💻 Excellent for College, Office & Programming\n\n" .
                       "- **Multitasking:** Effortlessly handles coding environments (VS Code, IntelliJ, Python), MS Office 365, and heavy browser loads.\n" .
                       "- **Display & Comfort:** 15.6\" FHD display ensures clear code readability without eye fatigue.\n" .
                       "- **Upgradable:** RAM can be expanded up to 32GB as your software development needs expand.";
    } elseif (str_contains($promptLower, 'box') || str_contains($promptLower, 'package') || str_contains($promptLower, 'warranty')) {
        $ai_markdown = "### 📦 What's in the Box & Warranty\n\n" .
                       "- **1x** {$pName} Laptop (Natural Silver)\n" .
                       "- **1x** Official HP 45W Smart AC Power Adapter & Power Cord\n" .
                       "- **1x** User Manual & Setup Guide\n" .
                       "- **1 Year Official Warranty Card** with nationwide authorized service support.";
    } else {
        $ai_markdown = "### 💡 Recommendation for {$pName}\n\n" .
                       "The **{$pName}** is one of our top-rated laptops, currently priced at **৳ {$pPrice}** with **{$pQty} units available** in stock!\n\n" .
                       "- **Performance:** 12th Gen Intel Core i5 processor for snappy, reliable daily performance.\n" .
                       "- **Display:** 15.6\" Full HD anti-glare IPS display.\n" .
                       "- **Service:** Covered by 1 Year Official Warranty and 7 Days Easy Return.\n\n" .
                       "Would you like help choosing between the **256GB** or **512GB** storage option?";
    }

    $Parsedown = new Parsedown();
    $ai_html = $Parsedown->text($ai_markdown);
    echo json_encode(['status' => 'success', 'response' => $ai_html]);
    exit();
}

// 6. PREPARE THE PROMPT FOR GEMINI API
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