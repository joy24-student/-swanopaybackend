<?php require_once('header.php'); ?>

<?php
require_once('admin/inc/seo_helpers.php');

$statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
$statement->execute();
$result = $statement->fetchAll(PDO::FETCH_ASSOC);                            
foreach ($result as $row) {
    $banner_product_category = $row['banner_product_category'];
}
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
                        // Checking if any product is available or not
                        $prod_count = 0;
                        $statement = $pdo->prepare("SELECT * FROM tbl_product");
                        $statement->execute();
                        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
                        foreach ($result as $row) {
                            $prod_table_ecat_ids[] = $row['ecat_id'];
                        }

                        for($ii=0;$ii<count($final_ecat_ids);$ii++):
                            if(in_array($final_ecat_ids[$ii],$prod_table_ecat_ids)) {
                                $prod_count++;
                            }
                        endfor;

                        if($prod_count==0) {
                            echo '<div class="pl_15">'.LANG_VALUE_153.'</div>';
                        } else {
                            for($ii=0;$ii<count($final_ecat_ids);$ii++) {
                                $statement = $pdo->prepare("SELECT * FROM tbl_product WHERE ecat_id=? AND p_is_active=?");
                                $statement->execute(array($final_ecat_ids[$ii],1));
                                $result = $statement->fetchAll(PDO::FETCH_ASSOC);
                                foreach ($result as $row) {
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
                                                    $t_rating = 0;
                                                    $statement1 = $pdo->prepare("SELECT * FROM tbl_rating WHERE p_id=?");
                                                    $statement1->execute(array($row['p_id']));
                                                    $tot_rating = $statement1->rowCount();
                                                    if($tot_rating == 0) {
                                                        $avg_rating = 0;
                                                    } else {
                                                        $result1 = $statement1->fetchAll(PDO::FETCH_ASSOC);
                                                        foreach ($result1 as $row1) {
                                                            $t_rating = $t_rating + $row1['rating'];
                                                        }
                                                        $avg_rating = $t_rating / $tot_rating;
                                                    }
                                                    ?>
                                                    <?php
                                                    if($avg_rating == 0) {
                                                        echo '';
                                                    }
                                                    elseif($avg_rating == 1.5) {
                                                        echo '
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star-half-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                        ';
                                                    } 
                                                    elseif($avg_rating == 2.5) {
                                                        echo '
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star-half-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                        ';
                                                    }
                                                    elseif($avg_rating == 3.5) {
                                                        echo '
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star-half-o"></i>
                                                            <i class="fa fa-star-o"></i>
                                                        ';
                                                    }
                                                    elseif($avg_rating == 4.5) {
                                                        echo '
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star"></i>
                                                            <i class="fa fa-star-half-o"></i>
                                                        ';
                                                    }
                                                    else {
                                                        for($i=1;$i<=5;$i++) {
                                                            ?>
                                                            <?php if($i>$avg_rating): ?>
                                                                <i class="fa fa-star-o"></i>
                                                            <?php else: ?>
                                                                <i class="fa fa-star"></i>
                                                            <?php endif; ?>
                                                            <?php
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
                        }
                        ?>
                    </div>

                </div>

            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>