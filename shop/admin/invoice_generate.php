<?php
require_once __DIR__ . '/inc/guard.php';
$reference=(string)($_GET['payment_id'] ?? $_GET['tran_id'] ?? '');
if (!$reference) {http_response_code(400);exit('Choose an order to print.');}
header('Location: order-summary.php?payment_id=' . rawurlencode($reference));
exit;
