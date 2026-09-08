<?php
require_once('admin/inc/config.php'); // Your database config

header('Content-Type: application/json');

$search_terms_str = isset($_GET['search_terms']) ? $_GET['search_terms'] : '';
$search_terms = explode(',', $search_terms_str);
$search_terms = array_map('trim', $search_terms);
$search_terms = array_filter($search_terms); // Remove empty values

$products = [];
if (empty($search_terms)) {
    echo json_encode(['success' => true, 'products' => []]);
    exit;
}

try {
    // Build WHERE clause for relevant products
    $like_conditions = [];
    $params = [];
    foreach ($search_terms as $term) {
        $like_conditions[] = "p_name LIKE ?";
        $like_conditions[] = "p_short_description LIKE ?";
        $params[] = '%' . $term . '%';
        $params[] = '%' . $term . '%';
    }
    $where_clause = implode(' OR ', $like_conditions);

    // Fetch products based on search terms, limited to 8 for recommendations
    $sql = "SELECT * FROM tbl_product WHERE p_is_active = 1 AND ({$where_clause}) ORDER BY p_total_view DESC LIMIT 8";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $products = $stmt->fetchAll(PDO::FETCH_ASSOC);

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

    echo json_encode(['success' => true, 'products' => $products]);

} catch (PDOException $e) {
    error_log("Error in fetch_recommendations.php: " . $e->getMessage());
    echo json_encode(['success' => false, 'message' => 'Database error: ' . $e->getMessage()]);
}
?>