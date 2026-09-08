<?php
/**
 * MIGRATION HELPER - Update Product Links Throughout Application
 * 
 * This helper provides functions to generate proper product and category links
 * Use these functions throughout your application instead of hardcoding URLs
 * 
 * Examples:
 * - Old: <a href="product.php?id=123">Product</a>
 * - New: <a href="<?php echo product_link($id, $name); ?>">Product</a>
 */

require_once('admin/inc/config.php');
require_once('admin/inc/seo_helpers.php');

/**
 * Generate SEO-friendly product link
 * Usage: <a href="<?php echo product_link($p_id, $p_name); ?>">
 */
function product_link($product_id, $product_name) {
    return getProductURL($product_id, $product_name, BASE_URL);
}

/**
 * Generate SEO-friendly category link
 * Usage: <a href="<?php echo category_link($cat_id, $cat_name, 'end-category'); ?>">
 */
function category_link($category_id, $category_name, $type = 'top-category') {
    return getCategoryURL($category_id, $category_name, $type, '', BASE_URL);
}

/**
 * Generate search link
 * Usage: <a href="<?php echo search_link('headphones'); ?>">
 */
function search_link($query) {
    return getSearchURL($query, BASE_URL);
}

/**
 * Get pagination link
 * Usage: <a href="<?php echo paginate_link($page); ?>">
 */
function paginate_link($page_number) {
    return getPaginationURL($page_number, BASE_URL);
}

/**
 * Get category pagination link
 * Usage: <a href="<?php echo category_paginate_link($slug, $page); ?>">
 */
function category_paginate_link($category_slug, $page_number) {
    return getCategoryPaginationURL($category_slug, $page_number, BASE_URL);
}

/**
 * RECOMMENDED PLACES TO UPDATE IN YOUR APPLICATION:
 * 
 * 1. Category Sidebar (sidebar-category.php)
 *    OLD: <a href="product-category.php?id=<?php echo $row['ecat_id']; ?>&type=end-category">
 *    NEW: <a href="<?php echo category_link($row['ecat_id'], $row['ecat_name'], 'end-category'); ?>">
 * 
 * 2. Product Grid/List (fetch_products.php)
 *    OLD: <a href="product.php?id=<?php echo $row['p_id']; ?>">
 *    NEW: <a href="<?php echo product_link($row['p_id'], $row['p_name']); ?>">
 * 
 * 3. Search Results (search-result.php)
 *    OLD: <a href="product.php?id=<?php echo $row['p_id']; ?>">
 *    NEW: <a href="<?php echo product_link($row['p_id'], $row['p_name']); ?>">
 * 
 * 4. Navigation Links
 *    OLD: <a href="product-category.php?id=1&type=top-category">
 *    NEW: <a href="<?php echo category_link(1, 'Electronics', 'top-category'); ?>">
 * 
 * 5. Pagination Links
 *    OLD: <a href="index.php?page=2">
 *    NEW: <a href="<?php echo paginate_link(2); ?>">
 * 
 * 6. Related Products
 *    Similar to Product Grid - use product_link()
 * 
 * 7. Breadcrumb Navigation
 *    Use category_link() for categories and product_link() for products
 */

/**
 * SQL Query to Find All Hardcoded Product Links (for reference)
 * Run this in your database to find places to update:
 * 
 * SELECT * FROM tbl_product WHERE p_description LIKE '%product.php?id%'
 * OR p_short_description LIKE '%product.php?id%';
 */

/**
 * TESTING HELPER - Verify links work
 * 
 * Example usage in a test file:
 * 
 * <?php
 * require_once('migration_helper.php');
 * 
 * // Test product link
 * $product_id = 1;
 * $product_name = "Sony Headphones";
 * echo "Product URL: " . product_link($product_id, $product_name) . "\n";
 * 
 * // Test category link
 * $category_id = 5;
 * $category_name = "Electronics";
 * echo "Category URL: " . category_link($category_id, $category_name, 'end-category') . "\n";
 * ?>
 */

/**
 * BULK UPDATE: Update all product references in HTML
 * 
 * If you have hardcoded URLs in templates, find and replace:
 * 
 * Find:    <a href="product.php?id=
 * Replace: <a href="<?php echo product_link(
 * 
 * Then manually adjust the closing part:
 * From:    <a href="<?php echo product_link(123); ?>&other=param">
 * To:      <a href="<?php echo product_link($p_id, $p_name); ?>">
 */

?>
