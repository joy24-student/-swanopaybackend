<?php
function updateStoreOrder(PDO $pdo,string $reference,string $action,?int $customerId=null): void {
    if(!$reference || strlen($reference)>255 || !in_array($action,['paid','shipped','delivered','cancel'],true)) throw new RuntimeException('Choose a valid order action.');
    try {
        $pdo->beginTransaction();
        $stmt=$pdo->prepare('SELECT * FROM tbl_payment WHERE payment_id=? FOR UPDATE');$stmt->execute([$reference]);$order=$stmt->fetch();
        if(!$order) throw new RuntimeException('Order not found.');
        if($customerId!==null && (string)$order['customer_id']!==(string)$customerId) throw new RuntimeException('Order not found.');
        if($order['payment_status']==='Cancelled') {
            if($action==='cancel') {$pdo->commit();return;}
            throw new RuntimeException('A cancelled order cannot be updated.');
        }
        $cash=in_array($order['payment_method'],['COD','Cash on Delivery','Cash'],true);
        if($action==='paid') {
            if(!$cash) throw new RuntimeException('Online payments must be verified by the payment provider.');
            $pdo->prepare("UPDATE tbl_payment SET payment_status='Completed' WHERE payment_id=?")->execute([$reference]);
        } elseif($action==='cancel') {
            if($customerId!==null && time()-strtotime($order['payment_date'])>=86400) throw new RuntimeException('The 24-hour cancellation window has expired. Please contact the store.');
            if($order['payment_status']==='Completed' || in_array($order['shipping_status'],['Shipped','Delivered','Completed'],true)) throw new RuntimeException('Paid or dispatched orders require a return or refund review before cancellation.');
            $stmt=$pdo->prepare('SELECT product_id,SUM(quantity) AS quantity FROM tbl_order WHERE payment_id=? GROUP BY product_id ORDER BY product_id');$stmt->execute([$reference]);
            foreach($stmt->fetchAll() as $item) $pdo->prepare('UPDATE tbl_product SET p_qty=p_qty+? WHERE p_id=?')->execute([(int)$item['quantity'],(int)$item['product_id']]);
            $pdo->prepare("UPDATE tbl_payment SET payment_status='Cancelled',shipping_status='Cancelled' WHERE payment_id=?")->execute([$reference]);
        } else {
            if(!$cash && $order['payment_status']!=='Completed') throw new RuntimeException('Verify payment before shipping this order.');
            if(in_array($order['shipping_status'],['Delivered','Completed'],true) && $action==='shipped') throw new RuntimeException('A delivered order cannot return to shipped.');
            $pdo->prepare('UPDATE tbl_payment SET shipping_status=? WHERE payment_id=?')->execute([$action==='shipped' ? 'Shipped' : 'Delivered',$reference]);
        }
        $pdo->commit();
    } catch(Throwable $error) {if($pdo->inTransaction()) $pdo->rollBack();throw $error;}
}
