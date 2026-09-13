<?php
ob_start();
session_start();
require_once('admin/inc/config.php');
require_once('admin/inc/CSRF_Protect.php');
$csrf = new CSRF_Protect();

header('Content-Type: application/json');

// Check CSRF token
if (!$csrf->checkToken()) {
    echo json_encode(['status' => 'error', 'message' => 'Invalid CSRF token.']);
    exit;
}

if (!isset($_SESSION['customer'])) {
    echo json_encode(['status' => 'error', 'message' => 'You must be logged in to submit a review.']);
    exit;
}

$customer_id = $_SESSION['customer']['cust_id'];
$product_id = isset($_POST['product_id']) ? (int)$_POST['product_id'] : 0;
$rating = isset($_POST['rating']) ? (int)$_POST['rating'] : 0;
$review_title = $_POST['review_title'] ?? '';
$comment = $_POST['comment'] ?? '';

if ($product_id === 0 || $rating < 1 || $rating > 5 || empty($review_title) || empty($comment)) {
    echo json_encode(['status' => 'error', 'message' => 'Missing or invalid review data.']);
    exit;
}

try {
    $purchased=$pdo->prepare("SELECT 1 FROM tbl_order o JOIN tbl_payment p ON p.payment_id=o.payment_id JOIN tbl_customer c ON c.cust_id=p.customer_id WHERE o.product_id=? AND p.customer_id=? AND p.shipping_status='Delivered' AND p.payment_status<>'Cancelled' AND c.cust_status=1 LIMIT 1");
    $purchased->execute([$product_id,$customer_id]);
    if (!$purchased->fetchColumn()) {http_response_code(403);echo json_encode(['status'=>'error','message'=>'You can review products after your order is delivered.']);exit;}
    if (strlen($review_title)>200 || strlen($comment)>5000) {http_response_code(400);echo json_encode(['status'=>'error','message'=>'Keep the title under 200 characters and review under 5,000 characters.']);exit;}
    // Check if the customer has already reviewed this product
    $stmt_check = $pdo->prepare("SELECT COUNT(*) FROM tbl_review WHERE cust_id = ? AND product_id = ?"); // CHANGED FROM tbl_rating
    $stmt_check->execute([$customer_id, $product_id]);
    if ($stmt_check->fetchColumn() > 0) {
        echo json_encode(['status' => 'error', 'message' => 'You have already submitted a review for this product.']);
        exit;
    }

    // Insert the new review
    $statement = $pdo->prepare("INSERT INTO tbl_review (product_id, cust_id, rating, review_title, comment, created_at, status) VALUES (?,?,?,?,?,?,?)"); // CHANGED FROM tbl_rating
    $statement->execute(array(
        $product_id,
        $customer_id,
        $rating,
        strip_tags($review_title), // Sanitize input
        strip_tags($comment),     // Sanitize input
        date('Y-m-d H:i:s'),
        'Pending' // Reviews are pending by default, admin needs to approve
    ));

    echo json_encode(['status' => 'success', 'message' => 'Your review has been submitted and is awaiting approval.']);

} catch (PDOException $e) {
    error_log("Review submission error: " . $e->getMessage());
    http_response_code(409);
    echo json_encode(['status' => 'error', 'message' => 'The review could not be saved. Refresh the page and check whether you already reviewed this product.']);
}
?>
