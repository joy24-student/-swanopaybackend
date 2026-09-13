<?php
require_once __DIR__ . '/inc/guard.php';
$reference=(string)($_GET['payment_id'] ?? $_GET['id'] ?? '');
if(!$reference && isset($_GET['order_id'])) {$stmt=$pdo->prepare('SELECT payment_id FROM tbl_order WHERE id=?');$stmt->execute([(int)$_GET['order_id']]);$reference=(string)$stmt->fetchColumn();}
$stmt=$pdo->prepare('SELECT * FROM tbl_payment WHERE payment_id=?');$stmt->execute([$reference]);$order=$stmt->fetch();
if(!$order) {http_response_code(404);exit('Order not found.');}
$stmt=$pdo->prepare('SELECT * FROM tbl_order WHERE payment_id=? ORDER BY id');$stmt->execute([$reference]);$items=$stmt->fetchAll();
require_once __DIR__ . '/header.php';
function receiptText($value) {return htmlspecialchars((string)$value,ENT_QUOTES,'UTF-8');}
?>
<section class="content-header"><h1>Order details</h1><p><?= receiptText($reference) ?></p></section>
<section class="content"><div class="box box-info"><div class="box-body">
  <div class="row"><div class="col-sm-6"><h2 style="font-size:20px">Customer</h2><p><strong><?= receiptText($order['customer_name']) ?></strong><br><?= receiptText($order['customer_email']) ?><br><?= receiptText($order['shipping_phone'] ?: $order['billing_phone']) ?></p></div>
  <div class="col-sm-6"><h2 style="font-size:20px">Delivery address</h2><p><?= nl2br(receiptText($order['shipping_street'] ?: $order['shipping_address'])) ?><br><?= receiptText($order['shipping_city']) ?>, <?= receiptText($order['shipping_state']) ?> <?= receiptText($order['shipping_zip']) ?></p></div></div>
  <p>Placed <?= receiptText($order['payment_date']) ?> · <?= receiptText($order['payment_method']) ?></p>
  <p><strong>Payment:</strong> <?= receiptText($order['payment_status']) ?> · <strong>Delivery:</strong> <?= receiptText($order['shipping_status']) ?></p>
  <div class="table-responsive"><table class="table table-striped"><thead><tr><th>Product</th><th>Options</th><th>Quantity</th><th>Unit price</th><th>Line total</th></tr></thead><tbody><?php foreach($items as $item): ?><tr><td><?= receiptText($item['product_name']) ?></td><td><?= receiptText(trim($item['size'].' '.$item['color'])) ?></td><td><?= (int)$item['quantity'] ?></td><td><?= number_format((float)$item['unit_price'],2) ?></td><td><?= number_format((float)$item['unit_price']*(int)$item['quantity'],2) ?></td></tr><?php endforeach; ?></tbody></table></div>
  <p>Delivery: BDT <?= number_format((float)$order['shipping_cost'],2) ?> · Discount: BDT <?= number_format((float)$order['coupon_discount'],2) ?></p>
  <h2 style="font-size:24px">Total: BDT <?= number_format((float)$order['paid_amount'],2) ?></h2>
  <?php if($order['payment_note']): ?><p><strong>Customer note:</strong> <?= receiptText($order['payment_note']) ?></p><?php endif; ?>
  <a class="btn btn-default" href="order.php">Back to orders</a> <button class="btn btn-primary" type="button" onclick="window.print()">Print order</button>
</div></div></section>
<?php require_once __DIR__ . '/footer.php'; ?>
