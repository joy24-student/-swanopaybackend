<?php
require_once('header.php');

$ids_str = $_GET['ids'] ?? '';
$product_ids = [];
if (!empty($ids_str)) {
    $product_ids = array_map('intval', explode(',', $ids_str));
}

$products = [];
if (!empty($product_ids)) {
    $id_placeholders = implode(',', array_fill(0, count($product_ids), '?'));
    $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE p_id IN ($id_placeholders)");
    $statement->execute($product_ids);
    $products = $statement->fetchAll(PDO::FETCH_ASSOC);
}
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Compare Products - <?php echo $settings['site_name'] ?? 'eCommerce Site'; ?></title>
    <link rel="stylesheet" href="assets/css/redesign.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <style>
        .compare-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 30px;
        }
        .compare-table th, .compare-table td {
            border: 1px solid #ddd;
            padding: 15px;
            text-align: center;
            vertical-align: top;
        }
        .compare-table th {
            background-color: #f8f9fa;
        }
        .compare-table .feature-name {
            text-align: left;
            font-weight: 700;
        }
        .compare-table img {
            max-width: 150px;
            margin-bottom: 15px;
        }
    </style>
</head>
<body>

<header class="header-redesign">
    <div class="container">
        <div class="header-logo">
            <a href="<?php echo BASE_URL; ?>">JoyStore</a>
        </div>
    </div>
</header>

<main class="main-content container section-spacing">
    <h1 class="section-title">Product Comparison</h1>

    <?php if (empty($products)): ?>
        <p>No products selected for comparison. Please add some products to compare.</p>
    <?php else: ?>
        <div class="table-responsive">
            <table class="compare-table">
                <thead>
                    <tr>
                        <th class="feature-name">Feature</th>
                        <?php foreach ($products as $product): ?>
                            <th>
                                <a href="product.php?id=<?php echo $product['p_id']; ?>">
                                    <img src="<?php echo BASE_URL; ?>assets/uploads/<?php echo $product['p_featured_photo']; ?>" alt="<?php echo $product['p_name']; ?>">
                                    <br>
                                    <?php echo $product['p_name']; ?>
                                </a>
                            </th>
                        <?php endforeach; ?>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td class="feature-name">Price</td>
                        <?php foreach ($products as $product): ?>
                            <td>$<?php echo $product['p_current_price']; ?></td>
                        <?php endforeach; ?>
                    </tr>
                    <tr>
                        <td class="feature-name">Old Price</td>
                        <?php foreach ($products as $product): ?>
                            <td><?php echo $product['p_old_price'] ? '$' . $product['p_old_price'] : '-'; ?></td>
                        <?php endforeach; ?>
                    </tr>
                    <tr>
                        <td class="feature-name">Quantity</td>
                        <?php foreach ($products as $product): ?>
                            <td><?php echo $product['p_qty'] > 0 ? 'In Stock' : 'Out of Stock'; ?></td>
                        <?php endforeach; ?>
                    </tr>
                    <tr>
                        <td class="feature-name">Short Description</td>
                        <?php foreach ($products as $product): ?>
                            <td><?php echo $product['p_short_description']; ?></td>
                        <?php endforeach; ?>
                    </tr>
                    <!-- Add more features to compare as needed -->
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</main>

<?php require_once('footer.php'); ?>

</body>
</html>
