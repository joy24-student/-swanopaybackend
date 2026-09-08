<?php require_once('header.php'); 
if(isset($_POST['form1'])) {
    $statement = $pdo->prepare("INSERT INTO tbl_home_sections (title, category_id, category_type, product_limit, order_no) VALUES (?,?,?,?,?)");
    $statement->execute(array($_POST['title'],$_POST['category_id'],$_POST['category_type'],8,$_POST['order_no']));
    $success_message = "Section Added.";
}
?>
<section class="content">
    <form action="" method="post">
        <div class="box box-info">
            <div class="box-body">
                <div class="form-group">
                    <label>Section Title</label>
                    <input type="text" class="form-control" name="title" required>
                </div>
                <div class="form-group">
                    <label>Category Type</label>
                    <select name="category_type" class="form-control" required>
                        <option value="top">Top Level Category</option>
                        <option value="mid">Mid Level Category</option>
                        <option value="end">End Level Category</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Category ID (Enter the ID number from Category Page)</label>
                    <input type="number" class="form-control" name="category_id" required>
                    </div>
                <button type="submit" class="btn btn-success" name="form1">Submit</button>
            </div>
        </div>
    </form>
</section>
<?php require_once('footer.php'); ?>