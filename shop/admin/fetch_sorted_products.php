<?php require_once __DIR__ . '/inc/guard.php'; ?>
<?php
include("admin/inc/config.php");
$sort_type = $_POST['sort_type'] ?? '';

$sql = "SELECT * FROM tbl_product WHERE p_is_active=1";
if($sort_type == 'low-high') { $sql .= " ORDER BY p_current_price ASC"; }
elseif($sort_type == 'high-low') { $sql .= " ORDER BY p_current_price DESC"; }
else { $sql .= " ORDER BY p_id DESC"; }

$statement = $pdo->prepare($sql);
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);

foreach($result as $row) {
    echo '
    <div class="p-card visible">
        <div class="p-img-box">
            <a href="product.php?id='.$row['p_id'].'">
                <img src="assets/uploads/'.$row['p_featured_photo'].'">
            </a>
            <div class="p-actions">
                <button class="circle-btn" onclick="wishlist('.$row['p_id'].')"><i class="fa fa-heart"></i></button>
            </div>
        </div>
        <div style="padding:15px; text-align:center;">
            <h5 style="font-weight:700;">'.$row['p_name'].'</h5>
            <div style="color:#e44d26; font-weight:800;">$'.number_format($row['p_current_price'], 2).'</div>
        </div>
    </div>';
}
?>