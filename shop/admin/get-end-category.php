<?php require_once __DIR__ . '/inc/guard.php'; ?>
<?php
include 'inc/config.php';
if(isset($_POST['id']) && !empty($_POST['id']))
{
	$id = $_POST['id'];
	
	$statement = $pdo->prepare("SELECT * FROM tbl_end_category WHERE mcat_id=?");
	$statement->execute(array($id));
	$result = $statement->fetchAll(PDO::FETCH_ASSOC);
	?><option value="">Select End Level Category</option><?php						
	foreach ($result as $row) {
		?>
        <option value="<?php echo $row['ecat_id']; ?>"><?php echo $row['ecat_name']; ?></option>
        <?php
	}
}