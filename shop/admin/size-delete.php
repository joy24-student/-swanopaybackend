<?php
require_once __DIR__ . '/inc/guard.php';
require_once __DIR__ . '/inc/catalog-delete.php';
try {
    deleteStoreCatalogEntry($pdo, 'size', (int)($_GET['id'] ?? 0));
    header('Location: size.php');exit;
} catch (Throwable $error) {
    http_response_code(409);
    $message=$error instanceof PDOException ? 'This record is still in use. Refresh the list and try again.' : $error->getMessage();
    echo '<p>' . htmlspecialchars($message,ENT_QUOTES,'UTF-8') . '</p><a href="size.php">Return to list</a>';
}
