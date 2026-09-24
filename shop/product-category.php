<?php require_once('header.php'); ?>

<?php
require_once('admin/inc/seo_helpers.php');

$banner_product_category = $settings['banner_product_category'] ?? '';
?>

<?php
$category_type=(string)($_REQUEST['type'] ?? '');
$category_id=isset($_REQUEST['slug']) ? extractIdFromSlug((string)$_REQUEST['slug']) : (int)($_REQUEST['id'] ?? 0);
$types=['top-category'=>['tbl_top_category','tcat_id','tcat_name'],'mid-category'=>['tbl_mid_category','mcat_id','mcat_name'],'end-category'=>['tbl_end_category','ecat_id','ecat_name']];
if(!isset($types[$category_type]) || !$category_id) {http_response_code(404);exit('Category not found.');}
[$table,$idColumn,$nameColumn]=$types[$category_type];
$stmt=$pdo->prepare("SELECT $nameColumn FROM $table WHERE $idColumn=?");$stmt->execute([$category_id]);$title=$stmt->fetchColumn();
if($title===false) {http_response_code(404);exit('Category not found.');}
$_REQUEST['id']=$category_id;
if($category_type==='top-category') {$stmt=$pdo->prepare('SELECT e.ecat_id FROM tbl_end_category e JOIN tbl_mid_category m ON e.mcat_id=m.mcat_id WHERE m.tcat_id=?');$stmt->execute([$category_id]);$final_ecat_ids=$stmt->fetchAll(PDO::FETCH_COLUMN);}
elseif($category_type==='mid-category') {$stmt=$pdo->prepare('SELECT ecat_id FROM tbl_end_category WHERE mcat_id=?');$stmt->execute([$category_id]);$final_ecat_ids=$stmt->fetchAll(PDO::FETCH_COLUMN);}
else {$final_ecat_ids=[$category_id];}
$final_ecat_ids=array_map('intval',$final_ecat_ids);
?>

<div class="page-banner" style="background-image: url(assets/uploads/<?php echo $banner_product_category; ?>)">
    <div class="inner">
        <h1><?php echo LANG_VALUE_50; ?> <?php echo $title; ?></h1>
    </div>
</div>

<div class="page">
    <div class="container">
        <div class="row">
          <div class="col-md-3">
                <?php require_once('sidebar-category.php'); ?>
            </div>
            <div class="col-md-9">
                
                <h3><?php echo LANG_VALUE_51; ?> "<?php echo $title; ?>"</h3>
                <div class="product product-cat">

                    <div class="row">
                        <?php
                        $products = [];
                        if (!empty($final_ecat_ids)) {
                            $placeholders = implode(',', array_fill(0, count($final_ecat_ids), '?'));
                            $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE ecat_id IN ($placeholders) AND p_is_active = 1 ORDER BY p_id DESC");
                            $statement->execute($final_ecat_ids);
                            $products = $statement->fetchAll(PDO::FETCH_ASSOC);
                        }

                        if (empty($products)) {
                            echo '<div class="pl_15">'.LANG_VALUE_153.'</div>';
                        } else {
                            $ratingMap = [];
                            try {
                                $stmtR = $pdo->query("SELECT p_id, AVG(rating) as avg_rating FROM tbl_rating GROUP BY p_id");
                                while ($rRow = $stmtR->fetch(PDO::FETCH_ASSOC)) {
                                    $ratingMap[$rRow['p_id']] = (float)$rRow['avg_rating'];
                                }
                            } catch (Throwable $e) {}

                            foreach ($products as $row) {
                                ?>
                                <div class="col-md-4 item item-product-cat">
                                    <div class="inner">
                                        <div class="thumb">
                                            <div class="photo" style="background-image:url(assets/uploads/<?php echo $row['p_featured_photo']; ?>);"></div>
                                            <div class="overlay"></div>
                                        </div>
                                        <div class="text">
                                            <h3><a href="product.php?id=<?php echo $row['p_id']; ?>"><?php echo $row['p_name']; ?></a></h3>
                                            <h4>
                                                <?php echo LANG_VALUE_1; ?><?php echo $row['p_current_price']; ?> 
                                                <?php if($row['p_old_price'] != ''): ?>
                                                <del>
                                                    <?php echo LANG_VALUE_1; ?><?php echo $row['p_old_price']; ?>
                                                </del>
                                                <?php endif; ?>
                                            </h4>
                                            <div class="rating">
                                                <?php
                                                $avg_rating = $ratingMap[$row['p_id']] ?? 0;
                                                for($i=1;$i<=5;$i++) {
                                                    if($i>$avg_rating) {
                                                        echo '<i class="fa fa-star-o"></i>';
                                                    } else {
                                                        echo '<i class="fa fa-star"></i>';
                                                    }
                                                }
                                                ?>
                                            </div>
                                            <?php if($row['p_qty'] == 0): ?>
                                                <div class="out-of-stock">
                                                    <div class="inner">
                                                        Out Of Stock
                                                    </div>
                                                </div>
                                            <?php else: ?>
                                                <p><a href="product.php?id=<?php echo $row['p_id']; ?>"><i class="fa fa-shopping-cart"></i> <?php echo LANG_VALUE_154; ?></a></p>
                                            <?php endif; ?>
                                        </div>
                                    </div>
                                </div>
                                <?php
                            }
                        }
                        ?>
                    </div>

                </div>

            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>