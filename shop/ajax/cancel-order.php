<?php
if (session_status() !== PHP_SESSION_ACTIVE) session_start();
require_once __DIR__ . '/../admin/inc/config.php';
require_once __DIR__ . '/../admin/inc/CSRF_Protect.php';
require_once __DIR__ . '/../admin/inc/orders.php';
header('Content-Type: application/json');
header('Cache-Control: no-store');
function cancelError(int $status,string $message): void {http_response_code($status);echo json_encode(['status'=>'error','message'=>$message]);exit;}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET')!=='POST') cancelError(405,'Use the order cancellation form.');
$csrf=new CSRF_Protect();
if (!$csrf->checkToken()) cancelError(403,'Your form session expired. Refresh the page.');
$customerId=(int)($_SESSION['customer']['cust_id'] ?? 0);
$query=$pdo->prepare('SELECT cust_id FROM tbl_customer WHERE cust_id=? AND cust_status=1');$query->execute([$customerId]);
if (!$query->fetchColumn()) cancelError(401,'Sign in to cancel your order.');
try {
    updateStoreOrder($pdo,(string)($_POST['payment_id'] ?? ''),'cancel',$customerId);
    echo json_encode(['status'=>'success','message'=>'Your order was cancelled.']);
} catch (Throwable $error) {
    cancelError(409,$error instanceof PDOException ? 'We could not update the order. Please try again.' : $error->getMessage());
}
