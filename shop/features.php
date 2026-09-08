<?php require_once('header.php'); ?>

<section class="content-header">
	<div class="content-header-left">
		<h1>Manage Feature Icons</h1>
	</div>
	<div class="content-header-right">
		<a href="feature-add.php" class="btn btn-primary btn-sm">Add New Feature</a>
	</div>
</section>

<section class="content">
	<div class="row">
		<div class="col-md-12">
			<div class="box box-info">
				<div class="box-body table-responsive">
					<table id="example1" class="table table-bordered table-striped">
						<thead>
							<tr>
								<th>SL</th>
								<th>Icon (FontAwesome)</th>
								<th>Title</th>
								<th>Link</th>
								<th>Action</th>
							</tr>
						</thead>
						<tbody>
							<?php
							$i=0;
							$statement = $pdo->prepare("SELECT * FROM tbl_features ORDER BY order_no ASC");
							$statement->execute();
							$result = $statement->fetchAll(PDO::FETCH_ASSOC);
							foreach ($result as $row) { $i++; ?>
								<tr>
									<td><?php echo $i; ?></td>
									<td><i class="fa <?php echo $row['icon']; ?>"></i> (<?php echo $row['icon']; ?>)</td>
									<td><?php echo $row['title']; ?></td>
									<td><?php echo $row['link']; ?></td>
									<td>
										<a href="feature-edit.php?id=<?php echo $row['id']; ?>" class="btn btn-primary btn-xs">Edit</a>
										<a href="feature-delete.php?id=<?php echo $row['id']; ?>" class="btn btn-danger btn-xs" onclick="return confirm('Delete item?');">Delete</a>
									</td>
								</tr>
							<?php } ?>
						</tbody>
					</table>
				</div>
			</div>
		</div>
	</div>
</section>
<?php require_once('footer.php'); ?>