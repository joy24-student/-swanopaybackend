<?php
ob_start(); // Start output buffering to catch any unexpected output

// Ensure session is started if needed for CSRF token
if (session_status() == PHP_SESSION_NONE) {
    session_start();
}

// Set header for JSON response immediately
header('Content-Type: application/json');

// It's good practice to try and include necessary files as early as possible.
// Adjust paths as necessary based on your project structure.
try {
    require_once('admin/inc/config.php');
    require_once('admin/inc/CSRF_Protect.php');
    $csrf = new CSRF_Protect();
} catch (Throwable $e) {
    // Catch any errors during includes or CSRF_Protect instantiation
    ob_clean(); // Clear any buffered output before sending JSON
    echo json_encode(['exists' => false, 'message' => 'Server configuration error: ' . $e->getMessage()]);
    exit;
}

$email = $_POST['email'] ?? '';
$csrf_token = $_POST['_csrf'] ?? ''; // Get CSRF token from POST

// Check CSRF token for security
if (!$csrf->isTokenValid($csrf_token)) {
    ob_clean(); // Clear any buffered output
    echo json_encode(['exists' => false, 'message' => 'Invalid CSRF token.']);
    exit;
}

// Basic validation for email input
if (empty($email)) {
    ob_clean(); // Clear any buffered output
    echo json_encode(['exists' => false, 'message' => 'Email cannot be empty.']);
    exit;
}

// Prepare and execute the SQL query to check email existence
try {
    if (!isset($pdo) || !$pdo instanceof PDO) {
        throw new Exception("PDO object is not properly initialized.");
    }
    $statement = $pdo->prepare("SELECT COUNT(*) FROM tbl_customer WHERE cust_email=?");
    $statement->execute(array($email));
    $total = $statement->fetchColumn(); // Fetch just the count

    ob_clean(); // Clear any buffered output before sending final JSON
    echo json_encode(['exists' => ($total > 0)]);

} catch (PDOException $e) {
    // Log the database error and return a generic message
    error_log("Database error in check_email_exists.php: " . $e->getMessage());
    ob_clean(); // Clear any buffered output
    echo json_encode(['exists' => false, 'message' => 'Database error during email check. Please try again later.']);
} catch (Exception $e) {
    // Catch other general exceptions
    error_log("General error in check_email_exists.php: " . $e->getMessage());
    ob_clean(); // Clear any buffered output
    echo json_encode(['exists' => false, 'message' => 'An unexpected server error occurred.']);
}

exit; // Ensure nothing else is outputted
?>
