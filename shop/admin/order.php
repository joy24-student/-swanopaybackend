<?php require_once('header.php'); ?>

<?php
// PHP code for form submission at the top remains unchanged...
$error_message = '';
if(isset($_POST['form1'])) {
    $valid = 1;
    if(empty($_POST['subject_text'])) {
        $valid = 0;
        $error_message .= 'Subject can not be empty\n';
    }
    if(empty($_POST['message_text'])) {
        $valid = 0;
        $error_message .= 'Message can not be empty\n';
    }
    if($valid == 1) {

        $subject_text = strip_tags($_POST['subject_text']);
        $message_text = strip_tags($_POST['message_text']);

        // Getting Customer Email Address
        $statement = $pdo->prepare("SELECT * FROM tbl_customer WHERE cust_id=?");
        $statement->execute(array($_POST['cust_id']));
        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
        foreach ($result as $row) {
            $cust_email = $row['cust_email'];
        }

        // Getting Admin Email Address
        $statement = $pdo->prepare("SELECT * FROM tbl_settings WHERE id=1");
        $statement->execute();
        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
        foreach ($result as $row) {
            $admin_email = $row['contact_email'];
        }

        $order_detail = '';
        $statement = $pdo->prepare("SELECT * FROM tbl_payment WHERE payment_id=?");
        $statement->execute(array($_POST['payment_id']));
        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
        foreach ($result as $row) {
            
            if($row['payment_method'] == 'PayPal'):
                $payment_details = '
Transaction Id: '.$row['txnid'].'<br>
                ';
            elseif($row['payment_method'] == 'Stripe'):
                $payment_details = '
Transaction Id: '.$row['txnid'].'<br>
Card number: '.$row['card_number'].'<br>
Card CVV: '.$row['card_cvv'].'<br>
Card Month: '.$row['card_month'].'<br>
Card Year: '.$row['card_year'].'<br>
                ';
            elseif($row['payment_method'] == 'Bank Deposit'):
                $payment_details = '
Transaction Details: <br>'.$row['bank_transaction_info'];
            endif;

            $order_detail .= '
Customer Name: '.$row['customer_name'].'<br>
Customer Email: '.$row['customer_email'].'<br>
Payment Method: '.$row['payment_method'].'<br>
Payment Date: '.$row['payment_date'].'<br>
Payment Details: <br>'.$payment_details.'<br>
Paid Amount: '.$row['paid_amount'].'<br>
Payment Status: '.$row['payment_status'].'<br>
Shipping Status: '.$row['shipping_status'].'<br>
Payment Id: '.$row['payment_id'].'<br>
            ';
        }

        $i=0;
        $statement = $pdo->prepare("SELECT * FROM tbl_order WHERE payment_id=?");
        $statement->execute(array($_POST['payment_id']));
        $result = $statement->fetchAll(PDO::FETCH_ASSOC);
        foreach ($result as $row) {
            $i++;
            $order_detail .= '
<br><b><u>Product Item '.$i.'</u></b><br>
Product Name: '.$row['product_name'].'<br>
Size: '.$row['size'].'<br>
Color: '.$row['color'].'<br>
Quantity: '.$row['quantity'].'<br>
Unit Price: '.$row['unit_price'].'<br>
            ';
        }

        $statement = $pdo->prepare("INSERT INTO tbl_customer_message (subject,message,order_detail,cust_id) VALUES (?,?,?,?)");
        $statement->execute(array($subject_text,$message_text,$order_detail,$_POST['cust_id']));

        // sending email
        $to_customer = $cust_email;
        $message = '
<html><body>
<h3>Message: </h3>
'.$message_text.'
<h3>Order Details: </h3>
'.$order_detail.'
</body></html>
';
        $headers = 'From: ' . $admin_email . "\r\n" .
                     'Reply-To: ' . $admin_email . "\r\n" .
                     'X-Mailer: PHP/' . phpversion() . "\r\n" . 
                     "MIME-Version: 1.0\r\n" . 
                     "Content-Type: text/html; charset=ISO-8859-1\r\n";

        // Sending email to admin                   
        mail($to_customer, $subject_text, $message, $headers);
        
        $success_message = 'Your email to customer is sent successfully.';
    }
}
?>

<?php
if($error_message != '') {
    echo "<script>alert('".$error_message."')</script>";
}
if($success_message != '') {
    echo "<script>alert('".$success_message."')</script>";
}
?>

<section class="content-header">
    <div class="content-header-left">
        <h1>View Orders</h1>
    </div>
</section>

<section class="content">
    <div class="row">
        <div class="col-md-12">
            <div class="box box-info">
                <div class="box-body table-responsive">
                    <table id="example1" class="table table-bordered table-striped">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Customer Details</th>
                                <th>Product Details</th>
                                <th>Payment Information</th>
                                <th>Billing & Shipping Address</th>
                                <th>Amount</th>
                                <th>Payment Status</th>
                                <th>Shipping Status & Time</th>
                                <th>Invoice</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php
                            $i=0;
                            $statement = $pdo->prepare("SELECT * FROM tbl_payment ORDER by id DESC");
                            $statement->execute();
                            $result = $statement->fetchAll(PDO::FETCH_ASSOC);                                 
                            foreach ($result as $row) {
                                $i++;
                                ?>
                                <tr class="<?php if($row['payment_status']=='Pending'){echo 'bg-r';}else{echo 'bg-g';} ?>">
                                    <td><?php echo $i; ?></td>
                                    <td>
                                        <b>ID:</b> <?php echo $row['customer_id']; ?><br>
                                        <b>Name:</b> <?php echo $row['customer_name']; ?><br>
                                        <b>Email:</b> <?php echo $row['customer_email']; ?><br>
                                        <b>Phone:<?php echo $row['billing_phone']; ?><br
                                        <a href="#" data-toggle="modal" data-target="#model-<?php echo $i; ?>" class="btn btn-primary btn-xs" style="margin-top:5px;">
                                            <i class="fa fa-envelope"></i> Send Message
                                        </a>
                                        
                                        <div id="model-<?php echo $i; ?>" class="modal fade" role="dialog">
                                            <div class="modal-dialog">
                                                <div class="modal-content">
                                                    <div class="modal-header">
                                                        <button type="button" class="close" data-dismiss="modal">&times;</button>
                                                        <h4 class="modal-title" style="font-weight: bold;">Send Message</h4>
                                                    </div>
                                                    <div class="modal-body">
                                                        <form action="" method="post">
                                                            <input type="hidden" name="cust_id" value="<?php echo $row['customer_id']; ?>">
                                                            <input type="hidden" name="payment_id" value="<?php echo $row['payment_id']; ?>">
                                                            <div class="form-group">
                                                                <label>Subject</label>
                                                                <input type="text" name="subject_text" class="form-control" required>
                                                            </div>
                                                            <div class="form-group">
                                                                <label>Message</label>
                                                                <textarea name="message_text" class="form-control" rows="5" required></textarea>
                                                            </div>
                                                            <div class="form-group">
                                                                <input type="submit" class="btn btn-primary" value="Send Message" name="form1">
                                                            </div>
                                                        </form>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </td>
                                    <td>
                                        <?php
                                        $statement1 = $pdo->prepare("SELECT * FROM tbl_order WHERE payment_id=?");
                                        $statement1->execute(array($row['payment_id']));
                                        $result1 = $statement1->fetchAll(PDO::FETCH_ASSOC);
                                        foreach ($result1 as $row1) {
                                            echo '<b>Product:</b> '.$row1['product_name'];
                                            echo '<br><b>Size:</b> '.$row1['size'];
                                            echo '<br><b>Color:</b> '.$row1['color'];
                                            echo '<br><b>Qty:</b> '.$row1['quantity'];
                                            echo '<br><b>Price:</b> '.$row1['unit_price'];
                                            echo '<br><br>';
                                        }
                                        ?>
                                    </td>
                                    <td>
                                        <?php if($row['payment_method'] == 'PayPal'): ?>
                                            <b>Method:</b> <span class="label label-danger">PayPal</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                            <b>Txn ID:</b> <?php echo $row['txnid']; ?><br>
                                        <?php elseif($row['payment_method'] == 'Cash on Delivery'): ?>
                                            <b>Method:</b> <span class="label label-success">Cash on Delivery</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                        <?php elseif($row['payment_method'] == 'SSLCommerz') :?>
                                            <b>Method:</b> <span class="label label-primary">SSLCommerz</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                            <b>Txn ID:</b> <?php echo $row['txnid']; ?><br>
                                            <b>Card:</b> <?php echo $row['ssl_payment_method']; ?><br>
                                        <?php elseif($row['payment_method'] == 'Stripe'): ?>
                                            <b>Method:</b> <span class="label label-info">Stripe</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                            <b>Txn ID:</b> <?php echo $row['txnid']; ?><br>
                                            <b>Card:</b> <?php echo $row['card_number']; ?><br>
                                        <?php elseif(in_array($row['payment_method'], ['SwapnoPay', 'bKash', 'Nagad', 'Rocket', 'Upay'])): ?>
                                            <b>Method:</b> <span class="label" style="background:#059669; color:#fff; font-weight:bold;"><?php echo htmlspecialchars($row['payment_method']); ?> (SwapnoPay)</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                            <b>Txn ID:</b> <?php echo htmlspecialchars($row['txnid'] ?: $row['payment_id']); ?><br>
                                            <?php if(!empty($row['payment_note'])): ?>
                                                <b>Note:</b> <?php echo htmlspecialchars($row['payment_note']); ?><br>
                                            <?php endif; ?>
                                        <?php elseif($row['payment_method'] == 'Bank Deposit'): ?>
                                            <b>Method:</b> <span class="label label-warning">Bank Deposit</span><br>
                                            <b>Date:</b> <?php echo $row['payment_date']; ?><br>
                                            <b>Info:</b><br> <?php echo nl2br($row['bank_transaction_info']); ?><br>
                                        <?php endif; ?>
                                    </td>
                                    <td>
                                        <div class="panel panel-default">
                                            <div class="panel-heading" style="background-color:#337ab7;color:white;padding:5px 10px;">
                                                <b>Billing Address</b>
                                            </div>
                                            <div class="panel-body" style="padding:5px 10px;">
                                                <?php echo $row['billing_name']; ?><br>
                                                <?php echo $row['billing_phone']; ?><br>
                                                <?php echo $row['billing_street']; ?><br>
                                                <?php echo $row['billing_city']; ?>, <?php echo $row['billing_state']; ?><br>
                                                <?php echo $row['billing_country']; ?>, <?php echo $row['billing_zip']; ?>
                                            </div>
                                        </div>
                                        <div class="panel panel-default">
                                            <div class="panel-heading" style="background-color:#337ab7;color:white;padding:5px 10px;">
                                                <b>Shipping Address</b>
                                            </div>
                                            <div class="panel-body" style="padding:5px 10px;">
                                                <?php echo $row['shipping_name']; ?><br>
                                                <?php echo $row['shipping_phone']; ?><br>
                                                <?php echo $row['shipping_street']; ?><br>
                                                <?php echo $row['shipping_city']; ?>, <?php echo $row['shipping_state']; ?><br>
                                                <?php echo $row['shipping_country']; ?>, <?php echo $row['shipping_zip']; ?>
                                            </div>
                                        </div>
                                    </td>
                                    <td><?php echo $row['paid_amount']; ?></td>
                                    <td>
                                        <?php echo $row['payment_status']; ?>
                                        <br><br>
                                        <?php if($row['payment_status']=='Pending'): ?>
                                            <a href="order-change-status.php?id=<?php echo $row['payment_id']; ?>&task=Completed" class="btn btn-success btn-xs" onclick="return confirm('Mark this order as paid?');">
                                                <i class="fa fa-check"></i> Mark Paid
                                            </a>
                                        <?php endif; ?>
                                    </td>
                                    <td>
                                        <?php echo $row['shipping_status']; ?>
                                        <?php if(isset($row['delivery_time']) && $row['delivery_time'] != '0000-00-00 00:00:00'): ?>
                                            <br><small><?php echo $row['delivery_time']; ?></small>
                                        <?php endif; ?>
                                        <br><br>
                                        <?php if($row['payment_status']=='Completed' && $row['shipping_status']=='Pending'): ?>
                                            <a href="shipping-change-status.php?id=<?php echo $row['payment_id']; ?>&task=Completed" class="btn btn-warning btn-xs" onclick="return confirm('Mark this order as shipped?');">
                                                <i class="fa fa-truck"></i> Mark Shipped
                                            </a>
                                        <?php endif; ?>
                                    </td>
                                    <td>
                                        <?php
                                        $invoice_method = ($row['payment_method'] == 'Cash on Delivery') ? 'cod' : (($row['payment_method'] == 'SSLCommerz') ? 'sslcommerz' : 'swapnopay');
                                        ?>
                                        <a href="invoice_generate.php?method=<?php echo $invoice_method; ?>&tran_id=<?php echo $row['payment_id']; ?>" class="btn btn-info btn-xs" target="_blank">
                                            <i class="fa fa-file-text"></i> Generate
                                        </a>
                                    </td>
                                    <td>
                                        <a href="#" class="btn btn-danger btn-xs" 
                                           data-href="order-delete.php?id=<?php echo $row['payment_id']; ?>" 
                                           data-toggle="modal" 
                                           data-target="#confirm-delete">
                                           <i class="fa fa-trash"></i> Delete
                                        </a>
                                    </td>
                                </tr>
                                <?php
                            }
                            ?>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</section>

<div class="modal fade" id="confirm-delete" tabindex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                <h4 class="modal-title" id="myModalLabel">Delete Confirmation</h4>
            </div>
            <div class="modal-body">
                Are you sure you want to delete this order?
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
                <a class="btn btn-danger btn-ok">Delete</a>
            </div>
        </div>
    </div>
</div>

<?php require_once('footer.php'); ?>