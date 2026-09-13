<?php require_once __DIR__ . '/header.php';
$page=max(1,min(100000,(int)($_GET['page'] ?? 1)));
$filter=(string)($_GET['status'] ?? '');
$statuses=['Pending','Completed','Cancelled'];
if(!in_array($filter,$statuses,true)) $filter='';
$where=$filter ? ' WHERE payment_status=?' : '';
$count=$pdo->prepare('SELECT count(*) FROM tbl_payment' . $where);$count->execute($filter ? [$filter] : []);$total=(int)$count->fetchColumn();
$query=$pdo->prepare('SELECT * FROM tbl_payment' . $where . ' ORDER BY id DESC LIMIT 25 OFFSET ' . (($page-1)*25));$query->execute($filter ? [$filter] : []);$orders=$query->fetchAll();
function orderText($value) {return htmlspecialchars((string)$value,ENT_QUOTES,'UTF-8');}
?>
<section class="content-header"><h1>Website orders <small><?= $total ?> orders</small></h1></section>
<section class="content">
  <?php foreach(['order_notice'=>'success','order_error'=>'danger'] as $key=>$kind): if(!empty($_SESSION[$key])): ?><div role="status" class="alert alert-<?= $kind ?>"><?= orderText($_SESSION[$key]) ?></div><?php unset($_SESSION[$key]);endif;endforeach; ?>
  <div class="box box-info"><div class="box-body">
    <form method="get" class="form-inline" style="margin-bottom:20px"><label for="order-status">Payment status</label> <select id="order-status" name="status" class="form-control"><option value="">All orders</option><?php foreach($statuses as $status): ?><option <?= $filter===$status ? 'selected' : '' ?>><?= $status ?></option><?php endforeach; ?></select> <button class="btn btn-primary" type="submit">Filter orders</button></form>
    <div class="table-responsive"><table class="table table-striped"><thead><tr><th>Order</th><th>Customer</th><th>Total</th><th>Payment</th><th>Delivery</th><th>Actions</th></tr></thead><tbody>
      <?php if(!$orders): ?><tr><td colspan="6" style="padding:48px;text-align:center">No website orders match this view.</td></tr><?php endif; ?>
      <?php foreach($orders as $order): $reference=$order['payment_id'];$cash=in_array($order['payment_method'],['COD','Cash on Delivery','Cash'],true);$cancelled=$order['payment_status']==='Cancelled';$paid=$order['payment_status']==='Completed'; ?>
      <tr>
        <td><a href="order-summary.php?payment_id=<?= rawurlencode($reference) ?>"><strong><?= orderText($reference) ?></strong></a><br><small><?= orderText($order['payment_date']) ?></small></td>
        <td><?= orderText($order['customer_name']) ?><br><small><?= orderText($order['customer_email']) ?></small></td>
        <td style="white-space:nowrap">BDT <?= number_format((float)$order['paid_amount'],2) ?></td>
        <td><strong><?= orderText($order['payment_status']) ?></strong><br><small><?= orderText($order['payment_method']) ?></small></td>
        <td><?= orderText($order['shipping_status']) ?></td>
        <td style="min-width:170px">
          <a class="btn btn-default btn-sm" href="order-summary.php?payment_id=<?= rawurlencode($reference) ?>">View order</a>
          <?php if(!$cancelled && $cash && !$paid): ?><form method="post" action="order-change-status.php"><input type="hidden" name="id" value="<?= orderText($reference) ?>"><button class="btn btn-success btn-sm" type="submit">Confirm cash received</button></form><?php endif; ?>
          <?php if(!$cancelled && ($cash || $paid) && !in_array($order['shipping_status'],['Delivered','Completed'],true)): ?><form method="post" action="shipping-change-status.php"><input type="hidden" name="id" value="<?= orderText($reference) ?>"><input type="hidden" name="task" value="<?= $order['shipping_status']==='Shipped' ? 'Delivered' : 'Shipped' ?>"><button class="btn btn-primary btn-sm" type="submit"><?= $order['shipping_status']==='Shipped' ? 'Mark delivered' : 'Mark shipped' ?></button></form><?php endif; ?>
          <?php if(!$cancelled && !$paid && !in_array($order['shipping_status'],['Shipped','Delivered','Completed'],true)): ?><a class="btn btn-default btn-sm" href="order-delete.php?id=<?= rawurlencode($reference) ?>">Cancel order</a><?php endif; ?>
        </td>
      </tr><?php endforeach; ?>
    </tbody></table></div>
    <nav aria-label="Order pages" style="display:flex;gap:16px;align-items:center"><?php if($page>1): ?><a href="?page=<?= $page-1 ?>&amp;status=<?= rawurlencode($filter) ?>">Previous</a><?php endif; ?><span>Page <?= $page ?> of <?= max(1,(int)ceil($total/25)) ?></span><?php if($page*25<$total): ?><a href="?page=<?= $page+1 ?>&amp;status=<?= rawurlencode($filter) ?>">Next</a><?php endif; ?></nav>
  </div></div>
</section>
<?php require_once __DIR__ . '/footer.php'; ?>
