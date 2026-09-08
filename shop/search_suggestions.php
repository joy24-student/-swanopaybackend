<?php
// search_suggestions.php
// This script provides product name suggestions for the search bar.

// Include the database connection and configuration
require_once('admin/inc/config.php');
require_once('admin/inc/functions.php');

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$query = isset($_GET['query']) ? trim($_GET['query']) : '';
$suggestions = [];

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
                'id' => $product['p_id'],
                'name' => $product['p_name'],
                'price' => $product['p_current_price'],
                'image' => isset($product['p_featured_photo']) ? $product['p_featured_photo'] : '',
                'url' => BASE_URL . 'product.php?id=' . $product['p_id']
            ];
        }
    } catch (PDOException $e) {
        // Return error response
        http_response_code(500);
        $suggestions = ['error' => 'Database error occurred'];
    }
} else {
    // Return empty suggestions if query is too short
    $suggestions = [];
}

echo json_encode($suggestions);
?>
