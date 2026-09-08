// admin/review-delete.php
<?php
ob_start();
session_start();
include("inc/config.php");
include("inc/functions.php");

if(!isset($_SESSION['user'])) {
    header('location: login.php');
    exit;
}

if(!isset($_REQUEST['id'])) {
    header('location: reviews.php');
    exit;
} else {
    $statement = $pdo->prepare("SELECT * FROM tbl_rating WHERE id=?");
    $statement->execute(array($_REQUEST['id']));
    $total = $statement->rowCount();
    if( $total == 0 ) {
        header('location: reviews.php');
        exit;
    }
}

$statement = $pdo->prepare("DELETE FROM tbl_rating WHERE id=?");
$statement->execute(array($_REQUEST['id']));

$_SESSION['success_message'] = 'Review deleted successfully!';
header('location: reviews.php');
exit;
?>