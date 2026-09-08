# Cart Database Sync - Verification Steps

## ⚠️ IMPORTANT: Must Be Logged In!

**Cart data ONLY syncs to database when user is LOGGED IN.**

If database is not being updated, the first thing to check is: **Are you logged in?**

---

## Quick Test (3 Steps)

### Step 1: Login
```
1. Go to: http://localhost/eCommerceSite-PHP/login.php
2. Enter your email and password
3. Click Login
4. Verify you see your name at top right
```

### Step 2: Open Debug Page
```
Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php

This will show:
✓ Login status
✓ Database connection
✓ Cart table exists
✓ Required functions present
✓ Session cart contents
✓ Database cart contents
```

### Step 3: Add Item to Cart
```
1. From debug page, click "Go Shopping"
2. Browse to any product
3. Click "Add to Cart"
4. Go back to debug page (refresh it)
5. Check "Database Cart Contents" section
6. Item should appear there ✓
```

---

## What To Check If Database Not Updated

### Check 1: Are You Logged In?
```
Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php

Look for:
"✓ Logged In" - GOOD
"✗ Not Logged In" - PROBLEM: Login first!
```

### Check 2: Does Table Exist?
```
Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php

Look for:
"✓ Table Exists" - GOOD
"✗ Table Not Found" - PROBLEM: Run create_cart_table.php
```

### Check 3: Do Functions Exist?
```
Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php

Look for all 4 functions:
✓ addOrUpdateCartItem()
✓ removeCartItem()
✓ updateCartItemQuantity()
✓ loadCartFromDatabase()

If any missing:
- Open: admin/inc/functions.php
- Verify they're in the file
- If not, re-apply the changes
- Restart Apache
```

---

## Simple Step-by-Step Test

### Test 1: Login and Check Status
```
Step 1: Login with your email/password
Step 2: Go to: http://localhost/eCommerceSite-PHP/test_cart_sync.php
Step 3: Check "Current Session Cart" section
Step 4: Check "Database Cart Contents" section
Step 5: Both should match OR database empty at first
```

### Test 2: Add Item and Verify
```
Step 1: From test_cart_sync.php, go to store
Step 2: Add product to cart
Step 3: Come back to test_cart_sync.php (refresh)
Step 4: Go to "Step 3: Database Cart Contents"
Step 5: Item should appear there! ✓
```

### Test 3: Delete Item and Verify
```
Step 1: Go to Cart page
Step 2: Delete an item
Step 3: Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php
Step 4: Check "Database Cart Contents"
Step 5: Item should be gone ✓
```

---

## If Still Not Working

### Debug Tool Links
1. **Full Debug:** http://localhost/eCommerceSite-PHP/debug_cart_sync.php
2. **Simple Test:** http://localhost/eCommerceSite-PHP/test_cart_sync.php

### Check Error Log
```
File: C:\xampp\apache\logs\error.log

Search for:
- "addOrUpdateCartItem"
- "Error"
- "PDOException"

Read the error message - it will tell you what's wrong
```

### Check Files Updated
```
admin/inc/functions.php
- Should have addOrUpdateCartItem() function
- Should have removeCartItem() function
- Should have updateCartItemQuantity() function
- Look around line 125-200

product.php
- Should call addOrUpdateCartItem() when adding items
- Look around line 220

cart.php
- Should call updateCartItemQuantity() when updating qty
- Look around line 40-55

cart-item-delete.php
- Should call removeCartItem() when deleting
- Look around line 12-17
```

### Restart Apache
```
1. Open XAMPP Control Panel
2. Click "Stop" for Apache
3. Wait 2 seconds
4. Click "Start" for Apache
5. Wait for it to start
6. Try again
```

### Clear Browser Cache
```
Press: Ctrl + Shift + Delete
Select: All time
Check: Cookies and cached images
Click: Clear data
Reload page
```

---

## Common Scenarios

### Scenario 1: Items Not in Database After Adding
```
CAUSE: Usually not logged in

CHECK:
☐ debug_cart_sync.php shows "✓ Logged In"?
☐ Not logged in?

SOLUTION:
1. Go to login page
2. Login with email
3. Go back to store
4. Add item
5. Check database again
```

### Scenario 2: Session Has Items But Database Empty
```
CAUSE: Items added while not logged in

CHECK:
☐ Session cart has items?
☐ Database cart is empty?

SOLUTION:
1. Delete all session items (clear cart)
2. Make sure you're logged in
3. Add items again
4. Check database
```

### Scenario 3: Deleted Item Still in Database
```
CAUSE: Delete didn't sync

CHECK:
☐ Was removeCartItem() called?
☐ Function exists?

SOLUTION:
1. Check cart-item-delete.php has removeCartItem() call
2. Check functions.php has removeCartItem() function
3. Restart Apache
4. Try deleting again
```

### Scenario 4: Database Error When Adding
```
CAUSE: SQL error or table issue

CHECK:
☐ Table exists in phpMyAdmin?
☐ Column names correct?

SOLUTION:
1. Open phpMyAdmin
2. Go to ecommerceweb database
3. Look for tbl_customer_carts table
4. If missing, run create_cart_table.php
5. If exists, check structure matches schema
```

---

## Verification Checklist

Before reporting issue, verify:

```
☐ I am logged in (checked debug page)
☐ Session cart shows items
☐ Database table exists (in phpMyAdmin)
☐ All 4 functions exist (in functions.php)
☐ Files were updated properly
☐ Apache restarted
☐ Browser cache cleared
☐ Tried again
```

If all checked and still not working:
1. Go to: http://localhost/eCommerceSite-PHP/debug_cart_sync.php
2. Note any red errors
3. Check error log: C:\xampp\apache\logs\error.log
4. Check for errors in log
5. Fix the specific error shown

---

## Quick Links

- **Debug Page:** http://localhost/eCommerceSite-PHP/debug_cart_sync.php
- **Test Page:** http://localhost/eCommerceSite-PHP/test_cart_sync.php
- **Login Page:** http://localhost/eCommerceSite-PHP/login.php
- **Store:** http://localhost/eCommerceSite-PHP/index.php
- **phpMyAdmin:** http://localhost/phpmyadmin
- **Error Log:** C:\xampp\apache\logs\error.log

---

*Last Updated: January 10, 2026*
