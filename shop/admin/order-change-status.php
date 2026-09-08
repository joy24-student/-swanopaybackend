<?php require_once('header.php');

if (!isset($_REQUEST['id']) || !isset($_REQUEST['task'])) {
    header('location: logout.php');
    exit;
}

// Check if the payment_id is valid
$statement = $pdo->prepare("SELECT * FROM tbl_payment WHERE payment_id=?");
$statement->execute(array($_REQUEST['id']));
$total = $statement->rowCount();
if ($total == 0) {
    header('location: logout.php');
    exit;
}

// Update the payment status using payment_id
$statement = $pdo->prepare("UPDATE tbl_payment SET payment_status=? WHERE payment_id=?");
$statement->execute(array($_REQUEST['task'], $_REQUEST['id']));

header('location: order.php');
?>