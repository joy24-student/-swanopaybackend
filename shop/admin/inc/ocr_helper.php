<?php
/**
 * OCR.space API Helper for NID Extraction
 */

function extractNidData($imagePath) {
    // Get your FREE API Key at https://ocr.space/ocrapi
    $apiKey = 'K84438157688957'; // Replace with your actual API key

    // Check if file exists
    if (!file_exists($imagePath)) {
        return ['error' => 'File not found'];
    }

    $postData = [
        'apikey' => $apiKey,
        'language' => 'eng', // Set to 'ben' if primarily using Bengali NIDs
        'isOverlayRequired' => 'false',
        'base64Image' => 'data:image/jpeg;base64,' . base64_encode(file_get_contents($imagePath)),
        'OCREngine' => '2' // Engine 2 is specifically optimized for ID cards/labels
    ];

    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, 'https://api.ocr.space/parse/image');
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
    
    // Set timeout for slower uploads
    curl_setopt($ch, CURLOPT_TIMEOUT, 30);

    $response = curl_exec($ch);
    $err = curl_error($ch);
    curl_close($ch);

    if ($err) {
        return ['error' => 'CURL Error: ' . $err];
    } else {
        return json_decode($response, true);
    }
}
?>