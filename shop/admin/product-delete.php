<?php
require_once __DIR__ . '/inc/guard.php';
$id=filter_var($_REQUEST['id'] ?? null,FILTER_VALIDATE_INT);
if(!$id) {http_response_code(400);exit('Invalid product.');}
$pdo->prepare('UPDATE tbl_product SET p_is_active=0 WHERE p_id=?')->execute([$id]);
header('Location: product.php');exit;
