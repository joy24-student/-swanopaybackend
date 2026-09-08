# Troubleshooting Guide - Cart Real-Time Sync

## Common Issues & Solutions

### Issue 1: Items Not Appearing in Database After Adding

**Symptoms:**
- Add item to cart
- Item appears in session/cart view
- But NOT in database table

**Check List:**
```
☐ Are you LOGGED IN? (most important!)
  → If guest, items won't sync to database
  → Solution: Login first, then add items

☐ Check error log for PHP errors
  → File: /admin/
  → Look for "Error" messages

☐ Verify functions.php has new functions
  → Open: admin/inc/functions.php
  → Search: addOrUpdateCartItem
  → If missing, file wasn't updated properly

☐ Check database connection
  → Open: admin/inc/config.php
  → Verify database credentials
  → Test connection

☐ Restart Apache/Web Server
  → Sometimes PHP caches need clearing
  → Restart to load updated functions
```

**Solutions:**
```
1. MUST BE LOGGED IN
   - Go to login page
   - Enter email and password
   - Then try adding items

2. Check for errors
   - Open browser console (F12)
   - Check for JavaScript errors
   - Check server error log

3. Verify file updates
   - Open admin/inc/functions.php
   - Line ~110-140 should have addOrUpdateCartItem()
   - If not there, update was incomplete

4. Restart server
   - Restart Apache in XAMPP control panel
   - Or restart your web service
   - Clear browser cache (Ctrl+Shift+Del)
```

---

### Issue 2: Delete Not Working

**Symptoms:**
- Click delete button
- Item removed from view
- But still in database
- Or error message appears

**Check List:**
```
☐ Are you LOGGED IN?
  → Must be logged in for database delete

☐ Check cart-item-delete.php updated
  → Should have removeCartItem() call
  → Around line 12-17

☐ Check for errors
  → Browser console (F12)
  → Server error log
  → PHP error messages

☐ Verify function exists
  → admin/inc/functions.php
  → Search: removeCartItem
  → Should exist around line 140-165
```

**Solutions:**
```
1. Verify logged in
   - Check at top right of page
   - Should show username

2. Check file was updated
   - Open cart-item-delete.php
   - Should have this code at top:
   
   if (isset($_SESSION['customer']['cust_id'])) {
       require_once('admin/inc/functions.php');
       removeCartItem($pdo, ...);
   }

3. Try manual delete in phpMyAdmin
   - If phpMyAdmin delete works, it's PHP issue
   - Check for SQL errors

4. Restart server and browser
   - Restart Apache
   - Clear cache
   - Try again
```

---

### Issue 3: Quantity Not Updating in Database

**Symptoms:**
- Change quantity in cart
- Click update
- Session updates but database doesn't
- Quantity reverts on reload

**Check List:**
```
☐ Are you LOGGED IN?
  → Essential for database updates

☐ Check cart.php updated
  → Should have updateCartItemQuantity() calls
  → Around line 40-55

☐ Verify update form submits
  → Browser console (F12)
  → Network tab
  → Check POST request sent

☐ Check for JavaScript errors
  → F12 console
  → Look for red error messages
```

**Solutions:**
```
1. Verify logged in
   - Must be logged in
   - Session active

2. Check update form
   - Scroll to update button
   - Make sure form has product_id, quantity fields
   - Click Update Cart button

3. Verify file updated
   - Open cart.php
   - Search: updateCartItemQuantity
   - Should have multiple calls

4. Check database directly
   - Open phpMyAdmin
   - tbl_customer_carts
   - Manually check quantity values
   - If can't update there either, database issue

5. Restart and retry
   - Restart Apache
   - Reload page
   - Try updating again
```

---

### Issue 4: Cart Empty After Login

**Symptoms:**
- Add items while logged in
- Logout
- Login again
- Cart is empty!

**Check List:**
```
☐ Were items actually saved?
  → Check phpMyAdmin
  → tbl_customer_carts table
  → Should have rows for your customer_id

☐ Check login function called loadCartFromDatabase
  → Open: login.php
  → Search: loadCartFromDatabase
  → Should be called after login

☐ Verify customer_id matches
  → In database: check customer_id column
  → In session: $_SESSION['customer']['cust_id']
  → Must match

☐ Check for errors during load
  → Look at PHP error log
  → Check for "Error loading cart" messages
```

**Solutions:**
```
1. Verify items saved
   Step 1: Logout
   Step 2: Open phpMyAdmin
   Step 3: Go to tbl_customer_carts
   Step 4: Look for rows with your customer_id
   Step 5: If no rows, items weren't saved
          Go to Issue 1

2. Check login.php has loader
   Step 1: Open login.php
   Step 2: Search line 57
   Step 3: Should see:
           if(function_exists('loadCartFromDatabase')) {
               loadCartFromDatabase($pdo, $customer_id);
           }
   Step 4: If not there, add it

3. Verify customer_id
   Step 1: Check phpMyAdmin
   Step 2: tbl_customer_carts
   Step 3: Note customer_id value (e.g., 1)
   Step 4: After login, check session
   Step 5: $_SESSION['customer']['cust_id'] should be 1

4. Check for errors
   Step 1: Look at error log
   Step 2: Search: "Error loading cart"
   Step 3: Fix any database errors
   Step 4: Check database connection
```

---

### Issue 5: Functions Not Found Error

**Symptoms:**
- Error: "Call to undefined function addOrUpdateCartItem()"
- Or similar for removeCartItem or updateCartItemQuantity

**Check List:**
```
☐ Is admin/inc/functions.php being included?
  → Check product.php has require_once()
  → Check cart.php has require_once()

☐ Do functions actually exist in functions.php?
  → Open admin/inc/functions.php
  → Search for function name
  → If not found, file wasn't updated

☐ Is there a PHP syntax error?
  → Open admin/inc/functions.php
  → Check for missing brackets
  → Check for typos

☐ Did you restart server?
  → Sometimes PHP caches need refresh
```

**Solutions:**
```
1. Verify include statement
   In product.php around line 200, should have:
   require_once('admin/inc/functions.php');

   In cart.php around line 40, should have:
   require_once('admin/inc/functions.php');

2. Check functions exist
   Step 1: Open admin/inc/functions.php
   Step 2: Press Ctrl+F (find)
   Step 3: Search: function addOrUpdateCartItem
   Step 4: Should find it
   Step 5: If not, re-apply changes

3. Check syntax
   Step 1: Look for red squiggly underlines
   Step 2: Check closing brackets }
   Step 3: Check semicolons at end
   Step 4: Check for typos

4. Restart server
   Step 1: Stop Apache in XAMPP
   Step 2: Start Apache again
   Step 3: Reload page
   Step 4: Try again
```

---

### Issue 6: SQL Error When Adding Item

**Symptoms:**
- Try to add item
- Get SQL error message
- Item not added
- Database error details visible

**Check List:**
```
☐ Check table exists
  → phpMyAdmin
  → tbl_customer_carts
  → Should be there

☐ Check column names match
  → product_id (not p_id)
  → customer_id (not cust_id)
  → quantity (not p_qty)

☐ Check data types match
  → customer_id: int
  → product_id: int
  → quantity: int
  → price_at_add: decimal

☐ Check foreign key
  → customer_id references tbl_customer.cust_id
  → Value must exist in tbl_customer
```

**Solutions:**
```
1. Create table if missing
   Step 1: Open create_cart_table.php
   Step 2: Run in browser
   Step 3: Should see success message

2. Check column names
   Step 1: phpMyAdmin
   Step 2: tbl_customer_carts
   Step 3: Structure tab
   Step 4: Compare with SQL in TBL_CUSTOMER_CARTS_GUIDE.md

3. Verify data types
   Step 1: Check each column
   Step 2: Quantity should be int
   Step 3: Price should be decimal(10,2)
   Step 4: customer_id should be int

4. Check customer exists
   Step 1: phpMyAdmin
   Step 2: tbl_customer
   Step 3: Verify cust_id exists
   Step 4: Your customer_id should be there
```

---

## Quick Troubleshooting Flow

```
Problem: Items not in database

Is user logged in?
├─ NO → Login first!
└─ YES → Continue

Does admin/inc/functions.php have new functions?
├─ NO → Update file (check IMPLEMENTATION_SUMMARY.txt)
└─ YES → Continue

Does product.php call addOrUpdateCartItem()?
├─ NO → Update file (check REAL_TIME_SYNC_SOLUTION.md)
└─ YES → Continue

Does tbl_customer_carts table exist?
├─ NO → Run create_cart_table.php
└─ YES → Continue

Check error log for errors
├─ Found error → Fix database issue
└─ No error → Check phpMyAdmin directly
              Try adding item with phpMyAdmin
              If works there, it's PHP issue
              Restart Apache and try again
```

---

## Checking Error Log

### Where is PHP error log?

**XAMPP on Windows:**
```
C:\xampp\apache\logs\error.log
or
C:\xampp\php\logs\php_error.log
```

**Check for errors:**
```
1. Open the error log file
2. Look for recent entries
3. Search for "cart" or "ERROR"
4. Read the error message
5. It will tell you what's wrong
```

### Using error_log() function

The functions write errors using:
```php
error_log("Error message: " . $e->getMessage());
```

**To find these:**
1. Add errors to database? No
2. Errors only in PHP error log
3. Open error.log file
4. Search for function name (e.g., "addOrUpdateCartItem")
5. Read the error details

---

## Database Verification

### Check if table exists:
```sql
SHOW TABLES LIKE 'tbl_customer_carts';
```

### Check table structure:
```sql
DESCRIBE tbl_customer_carts;
```

### Check customer's cart items:
```sql
SELECT * FROM tbl_customer_carts 
WHERE customer_id = 1;
```

### Manual test insert:
```sql
INSERT INTO tbl_customer_carts 
(customer_id, product_id, size_id, size_name, color_id, color_name, quantity, price_at_add, product_name, product_photo)
VALUES (1, 42, 5, 'Large', 3, 'Red', 2, 19.99, 'T-Shirt', 'photo.jpg');
```

If this works, database is fine.
If error, fix SQL syntax or table structure.

---

## Getting Help

### Step 1: Gather Information
- [ ] What were you doing when error occurred?
- [ ] Did you login first?
- [ ] What error message appeared?
- [ ] Check error log - any errors?
- [ ] Check phpMyAdmin - any data?

### Step 2: Check Documentation
- [ ] Read REAL_TIME_SYNC_SOLUTION.md
- [ ] Read REAL_TIME_SYNC_CHECKLIST.md
- [ ] Check TBL_CUSTOMER_CARTS_GUIDE.md

### Step 3: Verify Files
- [ ] admin/inc/functions.php updated?
- [ ] product.php updated?
- [ ] cart.php updated?
- [ ] cart-item-delete.php updated?

### Step 4: Test Components
- [ ] Can you add to session? (works on page)
- [ ] Can you query database? (phpMyAdmin works)
- [ ] Can you call functions? (no errors)

---

## Common Questions

**Q: Do I need to be logged in?**
A: YES! Cart sync only works for logged-in users.

**Q: Why is cart empty after login?**
A: Items weren't saved to database. Check Issue 1.

**Q: Does this work with social login?**
A: YES! Works with Google, Facebook, OTP, and email.

**Q: Will guest carts sync?**
A: NO. Currently only for logged-in users.

**Q: How do I check if data was saved?**
A: Open phpMyAdmin → tbl_customer_carts → browse data.

**Q: What if I see SQL errors?**
A: Usually means table doesn't exist. Run create_cart_table.php.

---

## Still Having Issues?

1. ✅ Read this entire guide
2. ✅ Follow the "Quick Troubleshooting Flow"
3. ✅ Check error log
4. ✅ Verify all files updated
5. ✅ Restart Apache
6. ✅ Clear browser cache
7. ✅ Test step-by-step

If still stuck:
- Check error log for specific error
- Verify database table exists
- Verify you're logged in
- Restart web server and try again

---

*Last Updated: January 10, 2026*
