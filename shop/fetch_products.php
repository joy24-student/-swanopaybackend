<?php
require_once('admin/inc/config.php'); // Your database config

header('Content-Type: application/json');

$offset = isset($_GET['offset']) ? (int)$_GET['offset'] : 0;
$limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 12; // Default to 12 products per load

$products_data = [];
try {
    // Fetch products (Standard SQL LIMIT ? OFFSET ? compatible with PostgreSQL & MySQL)
    $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE p_is_active=? ORDER BY p_total_view DESC LIMIT ? OFFSET ?");
    $active_status = 1; // Assuming 1 means active
    $statement->bindParam(1, $active_status, PDO::PARAM_INT);
    $statement->bindParam(2, $limit, PDO::PARAM_INT);
    $statement->bindParam(3, $offset, PDO::PARAM_INT);
    $statement->execute();
    $products = $statement->fetchAll(PDO::FETCH_ASSOC);

    // Fetch total count for pagination logic
    $statement_count = $pdo->prepare("SELECT COUNT(*) FROM tbl_product WHERE p_is_active=?");
    $statement_count->execute(array(1));
    $total_products_count = $statement_count->fetchColumn();

    // Fetch ratings for each product (if review feature is ON)
    $review_feature_on_off = $settings['review_feature_on_off'] ?? 1; // Assuming $settings is available or fetched
    foreach ($products as &$product) {
        $product['avg_rating'] = 0;
        $product['total_reviews_count'] = 0;
        if ($review_feature_on_off == 1) {
            $statement_rating = $pdo->prepare("SELECT AVG(rating) AS avg_rating, COUNT(review_id) AS total_count FROM tbl_review WHERE product_id=? AND status='Approved'");
            $statement_rating->execute(array($product['p_id']));
            $rating_result = $statement_rating->fetch(PDO::FETCH_ASSOC);
            if ($rating_result['total_count'] > 0) {
                $product['avg_rating'] = round($rating_result['avg_rating']);
                $product['total_reviews_count'] = $rating_result['total_count'];
            }
        }
    }

    echo json_encode([
        'success' => true,
        'products' => $products,
        'has_more' => ($offset + $limit) < $total_products_count,
        'next_offset' => $offset + $limit
    ]);

} catch (PDOException $e) {
    error_log("Error in fetch_products.php: " . $e->getMessage());
    echo json_encode(['success' => false, 'message' => 'Database error: ' . $e->getMessage()]);
}
?>