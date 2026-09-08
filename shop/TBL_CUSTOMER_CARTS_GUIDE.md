# tbl_customer_carts Table Guide

## Table Overview
The `tbl_customer_carts` table stores shopping cart data for customers, allowing carts to persist across sessions.

---

## Table Structure

### Database Schema
```sql
CREATE TABLE `tbl_customer_carts` (
  `cart_id` int(11) NOT NULL AUTO_INCREMENT,
  `customer_id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL,
  `size_id` int(11) DEFAULT NULL,
  `size_name` varchar(255) DEFAULT '',
  `color_id` int(11) DEFAULT NULL,
  `color_name` varchar(255) DEFAULT '',
  `quantity` int(11) NOT NULL DEFAULT 1,
  `price_at_add` decimal(10,2) NOT NULL DEFAULT 0.00,
  `product_name` varchar(255) DEFAULT '',
  `product_photo` varchar(255) DEFAULT NULL,
  `added_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`cart_id`),
  KEY `customer_id` (`customer_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `tbl_customer_carts_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

---

## Column Descriptions

| Column | Type | NULL | Default | Description |
|--------|------|------|---------|-------------|
| `cart_id` | int(11) | NO | AUTO_INCREMENT | Unique identifier for each cart item |
| `customer_id` | int(11) | NO | - | Foreign key to tbl_customer (cust_id) |
| `product_id` | int(11) | NO | - | Product ID being added to cart |
| `size_id` | int(11) | YES | NULL | Optional size variant ID |
| `size_name` | varchar(255) | YES | '' | Size name (e.g., "Large", "XL") |
| `color_id` | int(11) | YES | NULL | Optional color variant ID |
| `color_name` | varchar(255) | YES | '' | Color name (e.g., "Red", "Blue") |
| `quantity` | int(11) | NO | 1 | Number of items in cart |
| `price_at_add` | decimal(10,2) | NO | 0.00 | Price when item was added |
| `product_name` | varchar(255) | YES | '' | Product name snapshot |
| `product_photo` | varchar(255) | YES | NULL | Product photo filename |
| `added_at` | timestamp | NO | CURRENT_TIMESTAMP | When item was added to cart |
| `updated_at` | timestamp | NO | CURRENT_TIMESTAMP | Last time item was updated |

---

## Key Constraints

### Primary Key
- `cart_id` - Unique identifier for each cart entry

### Foreign Keys
- `customer_id` → `tbl_customer.cust_id` (ON DELETE CASCADE)
  - When a customer is deleted, all their cart items are automatically deleted

### Indexes
- `customer_id` - For fast lookups of customer's carts
- `product_id` - For product-related queries

---

## Example Data

### Sample Rows
```
cart_id | customer_id | product_id | size_name | color_name | quantity | price_at_add | product_name | added_at
--------|-------------|------------|-----------|-----------|----------|--------------|--------------|-------------------
1       | 1           | 42         | Large     | Red       | 2        | 15.99        | T-Shirt      | 2026-01-10 10:30:00
2       | 1           | 58         | 32        | Blue      | 1        | 49.99        | Jeans        | 2026-01-10 10:35:00
3       | 2           | 15         | M         | Black     | 3        | 29.99        | Jacket       | 2026-01-10 11:00:00
```

---

## Common Queries

### View All Carts
```sql
SELECT * FROM tbl_customer_carts;
```

### View Specific Customer's Cart
```sql
SELECT * FROM tbl_customer_carts 
WHERE customer_id = 1 
ORDER BY added_at ASC;
```

### Count Items in Cart
```sql
SELECT COUNT(*) as total_items, SUM(quantity) as total_quantity
FROM tbl_customer_carts 
WHERE customer_id = 1;
```

### Calculate Cart Total Price
```sql
SELECT SUM(price_at_add * quantity) as cart_total
FROM tbl_customer_carts 
WHERE customer_id = 1;
```

### Find Product in Cart
```sql
SELECT * FROM tbl_customer_carts 
WHERE customer_id = 1 AND product_id = 42;
```

### Find Specific Variant in Cart
```sql
SELECT * FROM tbl_customer_carts 
WHERE customer_id = 1 
AND product_id = 42 
AND size_id = 5 
AND color_id = 3;
```

### View Carts by Product
```sql
SELECT customer_id, product_name, SUM(quantity) as total_qty
FROM tbl_customer_carts 
WHERE product_id = 42
GROUP BY customer_id;
```

### Get Abandoned Carts (Older than 7 days)
```sql
SELECT customer_id, COUNT(*) as items, 
       SUM(price_at_add * quantity) as potential_revenue
FROM tbl_customer_carts 
WHERE added_at < DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY customer_id;
```

### Clear Old Carts (Older than 30 days)
```sql
DELETE FROM tbl_customer_carts 
WHERE added_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

### Get Cart Details with Customer Info
```sql
SELECT 
    cc.cart_id,
    c.cust_name,
    c.cust_email,
    cc.product_name,
    cc.size_name,
    cc.color_name,
    cc.quantity,
    cc.price_at_add,
    (cc.price_at_add * cc.quantity) as line_total,
    cc.added_at
FROM tbl_customer_carts cc
JOIN tbl_customer c ON cc.customer_id = c.cust_id
WHERE c.cust_id = 1
ORDER BY cc.added_at DESC;
```

---

## PHP Functions

### Save Cart to Database
```php
function saveCartToDatabase($pdo, $customer_id, $session_data) {
    try {
        // Delete existing cart items
        $statement = $pdo->prepare("DELETE FROM tbl_customer_carts WHERE customer_id = ?");
        $statement->execute([$customer_id]);

        // Insert new cart items from session
        if (isset($session_data['cart_p_id']) && is_array($session_data['cart_p_id'])) {
            $total_items = count($session_data['cart_p_id']);
            for ($i = 0; $i < $total_items; $i++) {
                $product_id = $session_data['cart_p_id'][$i] ?? null;
                $size_id = $session_data['cart_size_id'][$i] ?? null;
                $size_name = $session_data['cart_size_name'][$i] ?? '';
                $color_id = $session_data['cart_color_id'][$i] ?? null;
                $color_name = $session_data['cart_color_name'][$i] ?? '';
                $quantity = $session_data['cart_p_qty'][$i] ?? 0;
                $price_at_add = $session_data['cart_p_current_price'][$i] ?? 0.00;
                $product_name = $session_data['cart_p_name'][$i] ?? '';
                $product_photo = $session_data['cart_p_featured_photo'][$i] ?? null;

                if ($product_id !== null && $quantity > 0) {
                    $insert_statement = $pdo->prepare("INSERT INTO tbl_customer_carts (
                        customer_id, product_id, size_id, size_name, color_id, color_name,
                        quantity, price_at_add, product_name, product_photo
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

                    $insert_statement->execute([
                        $customer_id, $product_id, $size_id, $size_name, $color_id, $color_name,
                        $quantity, $price_at_add, $product_name, $product_photo
                    ]);
                }
            }
        }
        return true;
    } catch (PDOException $e) {
        error_log("Error saving cart: " . $e->getMessage());
        return false;
    }
}
```

### Load Cart from Database
```php
function loadCartFromDatabase($pdo, $customer_id) {
    try {
        // Clear current session cart data
        $_SESSION['cart_p_id'] = [];
        $_SESSION['cart_size_id'] = [];
        $_SESSION['cart_size_name'] = [];
        $_SESSION['cart_color_id'] = [];
        $_SESSION['cart_color_name'] = [];
        $_SESSION['cart_p_qty'] = [];
        $_SESSION['cart_p_current_price'] = [];
        $_SESSION['cart_p_name'] = [];
        $_SESSION['cart_p_featured_photo'] = [];

        // Load from database
        $statement = $pdo->prepare("SELECT
            product_id, size_id, size_name, color_id, color_name,
            quantity, price_at_add, product_name, product_photo
            FROM tbl_customer_carts
            WHERE customer_id = ? ORDER BY added_at ASC");
        $statement->execute([$customer_id]);
        $cart_items = $statement->fetchAll(PDO::FETCH_ASSOC);

        // Populate session from database
        if (!empty($cart_items)) {
            foreach ($cart_items as $item) {
                $_SESSION['cart_p_id'][] = $item['product_id'];
                $_SESSION['cart_size_id'][] = $item['size_id'];
                $_SESSION['cart_size_name'][] = $item['size_name'];
                $_SESSION['cart_color_id'][] = $item['color_id'];
                $_SESSION['cart_color_name'][] = $item['color_name'];
                $_SESSION['cart_p_qty'][] = $item['quantity'];
                $_SESSION['cart_p_current_price'][] = $item['price_at_add'];
                $_SESSION['cart_p_name'][] = $item['product_name'];
                $_SESSION['cart_p_featured_photo'][] = $item['product_photo'];
            }
        }
        return true;
    } catch (PDOException $e) {
        error_log("Error loading cart: " . $e->getMessage());
        return false;
    }
}
```

### Clear Customer Cart
```php
function clearCustomerCart($pdo, $customer_id) {
    try {
        $statement = $pdo->prepare("DELETE FROM tbl_customer_carts WHERE customer_id = ?");
        $statement->execute([$customer_id]);
        return true;
    } catch (PDOException $e) {
        error_log("Error clearing cart: " . $e->getMessage());
        return false;
    }
}
```

### Remove Item from Cart
```php
function removeItemFromCart($pdo, $customer_id, $product_id, $size_id, $color_id) {
    try {
        $statement = $pdo->prepare("DELETE FROM tbl_customer_carts 
            WHERE customer_id = ? AND product_id = ? AND size_id = ? AND color_id = ?");
        $statement->execute([$customer_id, $product_id, $size_id, $color_id]);
        return true;
    } catch (PDOException $e) {
        error_log("Error removing item: " . $e->getMessage());
        return false;
    }
}
```

### Update Cart Item Quantity
```php
function updateCartItemQuantity($pdo, $customer_id, $product_id, $size_id, $color_id, $new_quantity) {
    try {
        $statement = $pdo->prepare("UPDATE tbl_customer_carts 
            SET quantity = ? 
            WHERE customer_id = ? AND product_id = ? AND size_id = ? AND color_id = ?");
        $statement->execute([$new_quantity, $customer_id, $product_id, $size_id, $color_id]);
        return true;
    } catch (PDOException $e) {
        error_log("Error updating quantity: " . $e->getMessage());
        return false;
    }
}
```

---

## Administration

### Verify Table Exists
```php
try {
    $test_stmt = $pdo->prepare("SELECT 1 FROM tbl_customer_carts LIMIT 1");
    $test_stmt->execute();
    echo "✓ Table exists";
} catch (PDOException $e) {
    echo "✗ Table does not exist";
}
```

### Check Table Size
```php
SELECT 
    ROUND(((data_length + index_length) / 1024 / 1024), 2) as size_in_mb,
    COUNT(*) as total_rows
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'ecommerceweb' 
AND TABLE_NAME = 'tbl_customer_carts';
```

### Backup Table
```sql
CREATE TABLE tbl_customer_carts_backup AS 
SELECT * FROM tbl_customer_carts;
```

### Export Table Data
```sql
SELECT * INTO OUTFILE '/path/to/carts.csv'
FIELDS TERMINATED BY ',' 
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
FROM tbl_customer_carts;
```

---

## Best Practices

1. **Always validate customer_id** - Ensure user is logged in before saving/loading
2. **Use transactions** - For critical operations involving multiple queries
3. **Index frequently queried columns** - `customer_id` and `product_id` are already indexed
4. **Archive old carts** - Periodically delete carts older than 30-90 days
5. **Monitor table size** - Clean up regularly to prevent bloat
6. **Validate quantities** - Ensure quantity > 0 before saving
7. **Use prepared statements** - Always to prevent SQL injection
8. **Log errors** - Use error_log() for debugging

---

## Troubleshooting

### Table doesn't exist?
```php
// Run create_cart_table.php from your project root
// Or execute this SQL directly in phpMyAdmin:
// [See schema above]
```

### Foreign key constraint fails?
- Ensure customer_id exists in tbl_customer
- Check customer hasn't been deleted

### Slow queries?
- Indexes on customer_id and product_id are already in place
- Consider archiving old data

### Data not saving?
- Check PDO connection is active
- Verify cart_p_id array is not empty
- Check server error logs

### Data not loading?
- Verify customer is properly logged in
- Check session_start() is called
- Verify customer_id is correct

---

## Performance Tips

1. **Use pagination** for large carts (100+ items)
2. **Cache cart totals** instead of calculating each time
3. **Batch operations** when possible
4. **Archive old carts** monthly
5. **Vacuum table** periodically to reclaim space

---

## Security Notes

✅ Uses parameterized queries (SQL injection safe)
✅ Foreign key constraints ensure data integrity
✅ Customer isolation (users see only their carts)
✅ Session-based verification
✅ Cascade delete prevents orphaned records
✅ Timestamps track modifications

---

## Related Tables

- `tbl_customer` - Customer information
- `tbl_product` - Product details
- `tbl_size` - Size options
- `tbl_color` - Color options

---

## Version History

| Date | Change | Details |
|------|--------|---------|
| 2026-01-10 | Created | Initial table creation with all fields |

---

*Last Updated: January 10, 2026*
