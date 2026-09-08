<?php
// This is a test file to diagnose API connection issues.
// Replace 'YOUR_GEMINI_API_KEY_HERE' with your actual key.

$api_key = 'AIzaSyCWUzRXavT03qdmxL45WcbY56kXIUP7R-4';
$url = "https://generativelanguage.googleapis.com/v1beta/models?key=" . $api_key;

// Initialize cURL session
$ch = curl_init();

// Set the URL
curl_setopt($ch, CURLOPT_URL, $url);

// Return the response instead of printing
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);

// Set a timeout
curl_setopt($ch, CURLOPT_TIMEOUT, 30);

// For local development on Android, disable SSL verification to bypass certificate errors
curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);

$response = curl_exec($ch);

if (curl_errno($ch)) {
    echo "cURL Error: " . curl_error($ch) . "\n";
} else {
    $http_status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    echo "HTTP Status Code: " . $http_status . "\n";
    if ($http_status == 200) {
        echo "Connection Successful! Response:\n";
        echo htmlspecialchars($response) . "\n";
    } else {
        echo "API Connection Failed with HTTP Status Code: " . $http_status . "\n";
        echo "Response:\n";
        echo htmlspecialchars($response) . "\n";
    }
}

curl_close($ch);
?>