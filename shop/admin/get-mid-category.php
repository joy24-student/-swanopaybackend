<?php require_once __DIR__ . '/inc/guard.php'; ?>
<?php
include 'inc/config.php';
if(isset($_POST['id']) && !empty($_POST['id']))
{
	$id = $_POST['id'];
	
	$statement = $pdo->prepare("SELECT * FROM tbl_mid_category WHERE tcat_id=?");
	$statement->execute(array($id));
	$result = $statement->fetchAll(PDO::FETCH_ASSOC);
	?><option value="">Select Mid Level Category</option><?php						
	foreach ($result as $row) {
		?>
        <option value="<?php echo $row['mcat_id']; ?>"><?php echo $row['mcat_name']; ?></option>
        <?php
	}
}