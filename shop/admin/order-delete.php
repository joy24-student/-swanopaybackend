<?php
require_once __DIR__ . '/inc/guard.php';
require_once __DIR__ . '/inc/orders.php';
try {
    updateStoreOrder($pdo,(string)($_POST['id'] ?? $_GET['id'] ?? ''),'cancel');
    $_SESSION['order_notice']='Order updated successfully.';
} catch(Throwable $error) {
    $_SESSION['order_error']=$error instanceof PDOException ? 'The order could not be updated. Please try again.' : $error->getMessage();
}
header('Location: order.php');exit;
