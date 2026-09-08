# Cart Data Persistence Setup Guide

## Overview
Your eCommerce site now has persistent cart storage! When customers log out, their cart data is saved to the database. When they log back in, their cart is automatically restored.

## Changes Made

### 1. **Database Table Creation**
A new table `tbl_customer_carts` has been created with the following structure:

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

### 2. **Updated Functions**

#### `admin/inc/functions.php`
Two key functions handle cart persistence:

- **`saveCartToDatabase($pdo, $customer_id, $session_data)`**
  - Called when user logs out
  - Saves all session cart items to the database
  - Clears previous cart data for the customer

- **`loadCartFromDatabase($pdo, $customer_id)`**
  - Called when user logs in
  - Restores cart from database into session
  - Clears session cart arrays first to avoid duplicates

### 3. **Updated Files**

#### `logout.php`
- Already had cart saving functionality
- Saves cart BEFORE clearing customer session
- Ensures no cart data is lost on logout

#### `login.php`
- Standard login: Loads cart after successful authentication
- Social login (Google/Facebook): Loads cart for existing or newly registered users
- All paths include cart restoration logic

#### `verify_otp.php`
- **NEW**: Added cart loading after OTP verification
- Works for both existing customers and new registrations via OTP

## Installation Steps

### Step 1: Create the Database Table

1. Open your browser and navigate to:
   ```
   http://localhost/eCommerceSite-PHP/create_cart_table.php
   ```

2. You should see a success message: "The tbl_customer_carts table has been created successfully."

3. Delete the `create_cart_table.php` file after confirming the table was created:
   ```
   d:\xampp\htdocs\eCommerceSite-PHP\create_cart_table.php
   ```

### Step 2: Verify the Changes

The following files have been updated:

1. **admin/inc/functions.php**
   - `loadCartFromDatabase()` function is fully implemented

2. **verify_otp.php**
   - Cart loading added after OTP verification

3. **logout.php**
   - No changes needed (already had saving logic)

4. **login.php**
   - No changes needed (already had loading logic)

## How It Works

### When User Logs Out:
1. System checks if customer has items in session cart
2. If cart has items, calls `saveCartToDatabase()` to store them
3. Clears all session cart variables
4. Redirects to login page

### When User Logs In:
1. **Standard Login**: 
   - User enters email and password
   - After successful authentication, `loadCartFromDatabase()` is called
   - Session cart arrays are restored with saved items

2. **Social Login (Google/Facebook)**:
   - User authenticates via social provider
   - System checks if user exists or creates new account
   - In all cases, `loadCartFromDatabase()` is called

3. **OTP/Mobile Login**:
   - User enters OTP
   - System verifies OTP
   - Logs in existing user or registers new user
   - `loadCartFromDatabase()` is called to restore cart

## Session Cart Structure

The cart data in session is stored as arrays:

```php
$_SESSION['cart_p_id']              // Array of product IDs
$_SESSION['cart_size_id']           // Array of size IDs
$_SESSION['cart_size_name']         // Array of size names
$_SESSION['cart_color_id']          // Array of color IDs
$_SESSION['cart_color_name']        // Array of color names
$_SESSION['cart_p_qty']             // Array of quantities
$_SESSION['cart_p_current_price']   // Array of prices at time of adding
$_SESSION['cart_p_name']            // Array of product names
$_SESSION['cart_p_featured_photo']  // Array of product photos
```

## Database Structure

The database stores each cart item as a separate row with:
- Customer reference (customer_id)
- Product details (product_id, product_name, product_photo)
- Variants (size_id, size_name, color_id, color_name)
- Quantity and price at time of adding
- Timestamps (added_at, updated_at)

## Benefits

✅ **Cart Persistence**: Customers don't lose items after logout
✅ **Cross-Device**: Cart is stored in database, not just browser storage
✅ **Automatic Restoration**: Cart loads automatically on login
✅ **All Login Methods Supported**: Works with email, social, and OTP login
✅ **Data Integrity**: Foreign keys ensure data consistency

## Troubleshooting

### Cart not loading after login?
- Check if `tbl_customer_carts` table was created
- Verify `admin/inc/functions.php` has the `loadCartFromDatabase()` function
- Check browser console and server error logs for PHP errors

### Cart not saving on logout?
- Ensure customer is logged in before logging out
- Check that `admin/inc/functions.php` has the `saveCartToDatabase()` function
- Verify database permissions allow INSERT/DELETE operations

### Function not found errors?
- Make sure `admin/inc/functions.php` is included in the file
- Check the exact function names (case-sensitive)
- Verify no syntax errors in the functions file

## Next Steps (Optional Enhancements)

1. **Clear Old Carts**: Add a scheduled task to clear abandoned carts older than 30 days
2. **Cart Notifications**: Email customers about saved carts
3. **Cart Analytics**: Track which products are frequently left in carts
4. **Guest Cart Cookies**: Extend functionality to save guest carts via cookies

---

**Setup completed successfully!** Your cart data is now persistent across sessions.
