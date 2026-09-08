# Cart Sync Issues - Diagnostic Checklist

## The Problem: Database Not Updating

If your cart database is not being updated when you add/delete items, follow this checklist.

---

## PRIORITY 1: MUST BE LOGGED IN!

**This is the #1 reason database doesn't update!**

```
Check: Are you logged in?

YES ✓ → Continue to next check
NO ✗ → Go to login.php FIRST!
        Login with email and password
        Then try adding items again
```

**How to verify you're logged in:**
- Look at top right of website
- Should see your name or profile
- If blank/no name shown → NOT logged in

---

## PRIORITY 2: Check Database Table Exists

```
Go to: http://localhost/phpmyadmin
Click: Database "ecommerceweb"
Look for table: tbl_customer_carts

EXISTS ✓ → Continue to next check
MISSING ✗ → Run http://localhost/eCommerceSite-PHP/create_cart_table.php
             Then come back here
```

---

## PRIORITY 3: Verify Functions Exist

**Go to:** admin/inc/functions.php

**Search for these 4 functions:**

1. `function addOrUpdateCartItem(` - Should exist
2. `function removeCartItem(` - Should exist
3. `function updateCartItemQuantity(` - Should exist
4. `function loadCartFromDatabase(` - Should exist

**All 4 exist ✓ → Continue to next check**
**Any missing ✗ → Copy and paste the functions from below**

---

## Copy-Paste These Functions (If Missing)

If any functions are missing from admin/inc/functions.php, add them:

```php
/**
 * Adds or updates a cart item in the database.
 */
function addOrUpdateCartItem($pdo, $customer_id, $product_id, $size_id, $size_name, $color_id, $color_name, $quantity, $price, $product_name, $product_photo) {
    try {
        $check_stmt = $pdo->prepare("SELECT cart_id, quantity FROM tbl_customer_carts 
            WHERE customer_id = ? AND product_id = ? AND size_id = ? AND color_id = ?");
        $check_stmt->execute([$customer_id, $product_id, $size_id, $color_id]);
        $existing = $check_stmt->fetch(PDO::FETCH_ASSOC);

        if ($existing) {
            $new_quantity = $existing['quantity'] + $quantity;
            $update_stmt = $pdo->prepare("UPDATE tbl_customer_carts 
                SET quantity = ?, updated_at = NOW() 
                WHERE cart_id = ?");
            $update_stmt->execute([$new_quantity, $existing['cart_id']]);
        } else {
            $insert_stmt = $pdo->prepare("INSERT INTO tbl_customer_carts (
                customer_id, product_id, size_id, size_name, color_id, color_name,
                quantity, price_at_add, product_name, product_photo
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            
            $insert_stmt->execute([
                $customer_id, $product_id, $size_id, $size_name, $color_id, $color_name,
                $quantity, $price, $product_name, $product_photo
            ]);
        }
        return true;
    } catch (PDOException $e) {
        error_log("Error adding/updating cart item: " . $e->getMessage());
        return false;
    }
}

function removeCartItem($pdo, $customer_id, $product_id, $size_id, $color_id) {
    try {
        $delete_stmt = $pdo->prepare("DELETE FROM tbl_customer_carts 
            WHERE customer_id = ? AND product_id = ? AND size_id = ? AND color_id = ?");
        $delete_stmt->execute([$customer_id, $product_id, $size_id, $color_id]);
        return true;
    } catch (PDOException $e) {
        error_log("Error removing cart item: " . $e->getMessage());
        return false;
    }
}

function updateCartItemQuantity($pdo, $customer_id, $product_id, $size_id, $color_id, $quantity) {
    try {
        if ($quantity <= 0) {
            return removeCartItem($pdo, $customer_id, $product_id, $size_id, $color_id);
        }
        
        $update_stmt = $pdo->prepare("UPDATE tbl_customer_carts 
            SET quantity = ?, updated_at = NOW() 
            WHERE customer_id = ? AND product_id = ? AND size_id = ? AND color_id = ?");
        $update_stmt->execute([$quantity, $customer_id, $product_id, $size_id, $color_id]);
        return true;
    } catch (PDOException $e) {
        error_log("Error updating cart item quantity: " . $e->getMessage());
        return false;
    }
}

function loadCartFromDatabase($pdo, $customer_id) {
    try {
        $_SESSION['cart_p_id'] = [];
        $_SESSION['cart_size_id'] = [];
        $_SESSION['cart_size_name'] = [];
        $_SESSION['cart_color_id'] = [];
        $_SESSION['cart_color_name'] = [];
        $_SESSION['cart_p_qty'] = [];
        $_SESSION['cart_p_current_price'] = [];
        $_SESSION['cart_p_name'] = [];
        $_SESSION['cart_p_featured_photo'] = [];

        $statement = $pdo->prepare("SELECT
            product_id, size_id, size_name, color_id, color_name,
            quantity, price_at_add, product_name, product_photo
            FROM tbl_customer_carts
            WHERE customer_id = ? ORDER BY added_at ASC");
        $statement->execute([$customer_id]);
        $cart_items = $statement->fetchAll(PDO::FETCH_ASSOC);

        if (!empty($cart_items)) {
            $index = 1;
            foreach ($cart_items as $item) {
                $_SESSION['cart_p_id'][$index] = $item['product_id'];
                $_SESSION['cart_size_id'][$index] = $item['size_id'];
                $_SESSION['cart_size_name'][$index] = $item['size_name'];
                $_SESSION['cart_color_id'][$index] = $item['color_id'];
                $_SESSION['cart_color_name'][$index] = $item['color_name'];
                $_SESSION['cart_p_qty'][$index] = $item['quantity'];
                $_SESSION['cart_p_current_price'][$index] = $item['price_at_add'];
                $_SESSION['cart_p_name'][$index] = $item['product_name'];
                $_SESSION['cart_p_featured_photo'][$index] = $item['product_photo'];
                $index++;
            }
        }
        return true;
    } catch (PDOException $e) {
        error_log("Error loading cart from database for customer ID " . $customer_id . ": " . $e->getMessage());
        return false;
    }
}
```

---

## PRIORITY 4: Verify product.php Calls Function

**Open:** product.php
**Search for:** `addOrUpdateCartItem`

**Should find this code around line 220:**

```php
// Add to database if customer is logged in
if (isset($_SESSION['customer']['cust_id'])) {
    require_once('admin/inc/functions.php');
    addOrUpdateCartItem($pdo, $_SESSION['customer']['cust_id'], $p_id, $size_id_added, 
                       $size_name_added, $color_id_added, $color_name_added, 
                       $p_qty_added, $p_current_price, $p_name, $p_featured_photo);
}
```

**Found ✓ → Continue**
**Not found ✗ → Copy and paste the code above into product.php at line 220**

---

## PRIORITY 5: Verify cart.php Calls Function

**Open:** cart.php
**Search for:** `updateCartItemQuantity`

**Should find this code around line 40-55:**

```php
// NEW: Update database with new quantity if customer is logged in
if (isset($_SESSION['customer']['cust_id'])) {
    require_once('admin/inc/functions.php');
    $product_id = $arr1[$i];
    $size_id = $_SESSION['cart_size_id'][$i] ?? null;
    $color_id = $_SESSION['cart_color_id'][$i] ?? null;
    $new_qty = $arr2[$i];
    
    updateCartItemQuantity($pdo, $_SESSION['customer']['cust_id'], 
                          $product_id, $size_id, $color_id, $new_qty);
}
```

**Found ✓ → Continue**
**Not found ✗ → Copy and paste above code into cart.php**

---

## PRIORITY 6: Verify cart-item-delete.php Calls Function

**Open:** cart-item-delete.php
**Search for:** `removeCartItem`

**Should find this code near the top (line 12-17):**

```php
// Delete from database if customer is logged in
if (isset($_SESSION['customer']['cust_id'])) {
    require_once('admin/inc/functions.php');
    removeCartItem($pdo, $_SESSION['customer']['cust_id'], $product_id, $size_id, $color_id);
}
```

**Found ✓ → Continue**
**Not found ✗ → Copy and paste above code at line 12**

---

## PRIORITY 7: Restart Apache

```
1. Open XAMPP Control Panel
2. Find Apache - click STOP
3. Wait 2-3 seconds
4. Click START
5. Wait for it to show "running"
6. Close Control Panel
```

---

## PRIORITY 8: Clear Browser Cache

```
Windows:
Press: Ctrl + Shift + Delete

Select:
☐ Cookies and cached images
☐ All time

Click: Clear data

Reload your site
```

---

## PRIORITY 9: Test It

```
1. Login to your account
2. Go to store
3. Add item to cart
4. Go to phpMyAdmin
5. Check tbl_customer_carts table
6. Click Browse
7. Look for your customer_id
8. Item should be there!
```

**Item appears ✓ → WORKING!**
**Item doesn't appear ✗ → Check error log**

---

## PRIORITY 10: Check Error Log

**Open file:** C:\xampp\apache\logs\error.log

**Search for:**
- "addOrUpdateCartItem"
- "error"
- "Error"
- "PDO"

**Read the error message**

**Examples:**

```
Error: Table 'ecommerceweb.tbl_customer_carts' doesn't exist
→ Solution: Run create_cart_table.php

Error: Call to undefined function addOrUpdateCartItem()
→ Solution: Functions.php wasn't updated, add functions manually

Error: SQLSTATE[HY000]: General error
→ Solution: Database connection issue, check config.php
```

---

## Final Verification

**To confirm everything is working:**

1. Open: http://localhost/eCommerceSite-PHP/debug_cart_sync.php
2. Check all items show ✓ (green)
3. If any show ✗ (red), fix that issue first
4. Go shopping and add item
5. Go back to debug page
6. Item should appear in database cart contents

---

## Still Not Working?

If you've checked all above and it still doesn't work:

1. **Get error log**
   - Open: C:\xampp\apache\logs\error.log
   - Copy last 20 lines
   - Find the actual error message

2. **Check phpMyAdmin directly**
   - Open: http://localhost/phpmyadmin
   - Click: ecommerceweb
   - Look for: tbl_customer_carts
   - Does it exist?
   - What columns does it have?

3. **Test database manually**
   - In phpMyAdmin
   - Open: SQL tab
   - Run this query:
   ```sql
   INSERT INTO tbl_customer_carts (customer_id, product_id, size_id, size_name, color_id, color_name, quantity, price_at_add, product_name, product_photo)
   VALUES (1, 42, 5, 'Large', 3, 'Red', 2, 19.99, 'Test', 'test.jpg');
   ```
   - Does it insert?
   - If error, fix the error

4. **Verify logged in customer**
   - Check: $_SESSION['customer']['cust_id']
   - Should have a customer ID (usually 1, 2, 3, etc.)
   - Not empty or null

---

*Use this checklist systematically from top to bottom until issue is resolved.*
