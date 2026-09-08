<?php
include("../inc/config.php");
include("../inc/ocr_helper.php");

// Set header to JSON for AJAX communication
header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. Validation: Ensure a file was actually uploaded
    if (!isset($_FILES['nid_image']) || $_FILES['nid_image']['error'] !== UPLOAD_ERR_OK) {
        echo json_encode([
            'success' => false, 
            'message' => 'No image file received or upload error.'
        ]);
        exit;
    }

    $tmp_file = $_FILES['nid_image']['tmp_name'];
    $file_type = $_FILES['nid_image']['type'];

    // 2. Security: Basic Image Validation
    $allowed_types = ['image/jpeg', 'image/png', 'image/jpg'];
    if (!in_array($file_type, $allowed_types)) {
        echo json_encode([
            'success' => false, 
            'message' => 'Invalid file format. Please upload a JPG or PNG.'
        ]);
        exit;
    }

    // 3. Process with OCR Helper
    // This function is defined in inc/ocr_helper.php
    $ocr_response = extractNidData($tmp_file);

    if (isset($ocr_response['ParsedResults'][0]['ParsedText'])) {
        $raw_text = $ocr_response['ParsedResults'][0]['ParsedText'];

        // 4. Pattern Matching: Extract NID Number using Regex
        // Matches 10 digits (New Smart NID) or 13/17 digits (Old NID)
        $nid_pattern = '/\b\d{10}\b|\b\d{13}\b|\b\d{17}\b/';
        
        if (preg_match($nid_pattern, $raw_text, $matches)) {
            echo json_encode([
                'success' => true,
                'nid_number' => $matches[0],
                'raw_text' => $raw_text // Optional: for debugging
            ]);
        } else {
            echo json_encode([
                'success' => false,
                'message' => 'Could not detect a valid NID number. Please ensure the photo is clear.'
            ]);
        }
    } else {
        echo json_encode([
            'success' => false,
            'message' => 'OCR Service Error. Please try again.'
        ]);
    }
} else {
    echo json_encode(['success' => false, 'message' => 'Invalid Request Method']);
}
?>