<?php
require_once __DIR__ . '/admin/inc/config.php';
header('Content-Type: application/json');
header('Cache-Control: no-store');
try {
    $settings = $pdo->query('SELECT id FROM tbl_settings WHERE id=1')->fetchColumn();
    $admin = $pdo->query("SELECT id FROM tbl_user WHERE id=1 AND status='Active'")->fetchColumn();
    $languageCount = (int)$pdo->query('SELECT count(*) FROM tbl_language')->fetchColumn();
    $ready = (bool)($settings && $admin && $languageCount >= 100 && MERCHANT_ID);
    http_response_code($ready ? 200 : 503);
    echo json_encode(['ready'=>$ready,'merchant_id'=>MERCHANT_ID]);
} catch (Throwable $error) {
    http_response_code(503); echo json_encode(['ready'=>false]);
}
