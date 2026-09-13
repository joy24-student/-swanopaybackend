// Integration test against real PHP/PDO requests and an isolated PostgreSQL engine.
import { PGlite } from '@electric-sql/pglite'
import { PGLiteSocketServer } from '@electric-sql/pglite-socket'
import { spawn } from 'node:child_process'
import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import net from 'node:net'
import assert from 'node:assert/strict'
import bcrypt from 'bcryptjs'
import { fileURLToPath } from 'node:url'

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..')
const php=process.env.PHP_BINARY || 'php'
const temp=await fs.mkdtemp(path.join(os.tmpdir(),'shop-http-test-'))
const db=new PGlite()
let server,processPhp,logs=''
const freePort=()=>new Promise(resolve=>{const socket=net.createServer();socket.listen(0,'127.0.0.1',()=>{const port=socket.address().port;socket.close(()=>resolve(port))})})
try {
  await db.exec(await fs.readFile(path.join(root,'swapnopay-backend/sql/storefront.sql'),'utf8'))
  await db.query("INSERT INTO tbl_user(id,full_name,email,phone,password,role,status) VALUES(1,'Test Owner','owner@example.com','',$1,'Top Admin','Active')",[await bcrypt.hash('a secure password 123',12)])
  await db.exec("INSERT INTO tbl_product(p_name,p_current_price,p_qty,p_is_active,ecat_id) VALUES('Test product',100,2,1,1)")
  const port=await freePort(),pgPort=await freePort(),base=`http://127.0.0.1:${port}`
  server=new PGLiteSocketServer({db,port:pgPort,host:'127.0.0.1'})
  await server.start()
  await fs.mkdir(path.join(temp,'hosts'))
  await fs.writeFile(path.join(temp,'hosts','store.example.com.json'),JSON.stringify({merchant_id:'11111111-1111-4111-8111-111111111111',base_url:`${base}/`,db:{host:'127.0.0.1',port:pgPort,database:'postgres',user:'postgres',password:'postgres',sslmode:'disable'}}))
  const prepend=path.join(temp,'prepend.php')
  await fs.writeFile(prepend,"<?php $_SERVER['HTTP_HOST']='store.example.com';")
  const router=path.join(temp,'router.php')
  await fs.writeFile(router,`<?php $_SERVER['HTTP_HOST']='store.example.com'; return require ${JSON.stringify(path.join(root,'shop/router.php').replaceAll('\\','/'))};`)
  const args=['-d','display_errors=0','-d',`error_log=${path.join(temp,'php-error.log')}`,'-d',`session.save_path=${temp}`,'-d',`auto_prepend_file=${prepend}`,'-d','session.cookie_secure=0']
  if(process.env.PHP_PGSQL_EXTENSION) args.push('-d',`extension=${process.env.PHP_PGSQL_EXTENSION}`)
  args.push('-S',`127.0.0.1:${port}`,'-t',path.join(root,'shop'),router)
  processPhp=spawn(php,args,{cwd:path.join(root,'shop'),env:{...process.env,SHOP_RUNTIME_DIR:temp},windowsHide:true,stdio:['ignore','pipe','pipe']})
  processPhp.stderr.on('data',chunk=>{logs+=chunk.toString()})
  processPhp.on('error',error=>{logs+=error.message})
  let cookie=''
  const request=async(route,body=null)=>{
    const response=await fetch(`${base}/${route}`,{method:body ? 'POST' : 'GET',headers:{Cookie:cookie,...(body ? {'Content-Type':'application/x-www-form-urlencoded'} : {})},body:body ? new URLSearchParams(body) : undefined,redirect:'manual'})
    const set=response.headers.get('set-cookie'); if(set) cookie=set.split(';')[0]
    return {status:response.status,body:await response.text(),location:response.headers.get('location')}
  }
  let ready=false
  for(let attempt=0;attempt<40;attempt++) {try{const r=await request('health.php');if(r.status===200){ready=true;break}}catch{}await new Promise(r=>setTimeout(r,150))}
  assert.ok(ready,`PHP health check failed: ${logs}\n${await fs.readFile(path.join(temp,'php-error.log'),'utf8').catch(()=>'')}`)
  const home=await request('index.php');assert.equal(home.status,200,home.body.slice(0,1000))
  const login=await request('admin/login.php'); assert.equal(login.status,200)
  const csrf=html=>html.match(/name="_csrf" value="([^"]+)"/)?.[1]
  assert.ok(csrf(login.body),'Admin CSRF input is missing')
  const bad=await request('admin/login.php',{form1:'1',email:'owner@example.com',password:'a secure password 123'});assert.equal(bad.status,200);assert.ok(bad.body.includes('session expired'))
  const good=await request('admin/login.php',{form1:'1',email:'owner@example.com',password:'a secure password 123',_csrf:csrf(login.body)});assert.equal(good.status,302)
  const admin=await request('admin/index.php');assert.equal(admin.status,200)
  const adminCookie=cookie
  const adminPages=(await fs.readdir(path.join(root,'shop/admin'))).filter(file=>file.endsWith('.php') && !/delete|remove|approve|change-status|login|logout|header|footer|subscriber-csv|invoice|order-summary|fetch_|get-/.test(file))
  const adminFailures=[]
  for(const file of adminPages) {
    const result=await request(`admin/${file}${file.includes('-edit') ? '?id=1' : ''}`)
    if(result.status>=500) adminFailures.push(`${file}: HTTP ${result.status}`)
  }
  assert.deepEqual(adminFailures,[],`Admin failures: ${adminFailures.join(', ')}\n${await fs.readFile(path.join(temp,'php-error.log'),'utf8').catch(()=>'')}`)
  const settings=await request('admin/settings.php');assert.equal(settings.status,200)
  const token=csrf(settings.body);assert.ok(token,'Settings form needs CSRF')
  const deniedSettings=await request('admin/settings.php',{form_general_settings:'1'});assert.equal(deniedSettings.status,403)
  const updatedSettings=await request('admin/settings.php',{_csrf:token,form_general_settings:'1',meta_title_home:'Updated store title',contact_email:'owner@example.com',hide_banner_desktop:'1',hide_banner_mobile:'1',hide_free_delivery_desktop:'1',hide_free_delivery_mobile:'1'})
  assert.equal(updatedSettings.status,200);assert.ok(updatedSettings.body.includes('updated successfully'))
  assert.equal((await db.query('SELECT meta_title_home FROM tbl_settings WHERE id=1')).rows[0].meta_title_home,'Updated store title')
  const settingsForms=['home_features','popup_settings','payment_gateways','api_integrations','review_delivery_settings','sms_settings','social_settings','footer_settings','email_settings','email_content_settings','blog_post_counts','ads_settings']
  for(const form of settingsForms) {
    const result=await request('admin/settings.php',{_csrf:token,[`form_${form}`]:'1',cod_enabled:'1',copyright_text:'Store copyright',footer_about_us:'About this store',smtp_port:'587',smtp_from_email:'store@example.com'})
    assert.equal(result.status,200,`${form}: HTTP ${result.status}`)
    assert.ok(/updated successfully/i.test(result.body),`${form} did not save: ${result.body.match(/callout-danger[\s\S]{0,500}/)?.[0] || result.body.slice(-500)}`)
  }
  assert.equal((await db.query('SELECT footer_copyright FROM tbl_settings WHERE id=1')).rows[0].footer_copyright,'Store copyright')
  await db.exec("UPDATE tbl_settings SET home_featured_product_on_off=1,home_latest_product_on_off=1,total_featured_product_home=8,total_latest_product_home=12,home_slider_on_off=0,home_features_on_off=0,home_category_on_off=1,featured_product_title='Featured products',latest_product_title='New arrivals'")
  for(const category of ['top','mid','end']) {
    const blocked=await request(`admin/${category}-category-delete.php?id=1`,{_csrf:token})
    assert.equal(blocked.status,409,`An in-use ${category} category must be retained`)
  }
  assert.equal((await db.query('SELECT count(*)::int AS n FROM tbl_product')).rows[0].n,1)
  const productData={_csrf:token,form1:'1',tcat_id:'1',mcat_id:'1',ecat_id:'1',p_name:'Admin product',p_current_price:'25.50',p_old_price:'',p_qty:'0',p_is_active:'1',p_is_featured:'0'}
  const newProduct=await request('admin/product-add.php',productData);assert.equal(newProduct.status,302,newProduct.body.slice(0,800))
  const created=(await db.query("SELECT * FROM tbl_product WHERE p_name='Admin product'")).rows[0];assert.equal(created.p_qty,0);assert.equal(Number(created.p_current_price),25.5)
  const badProduct=await request(`admin/product-edit.php?id=${created.p_id}`,{...productData,p_qty:'-1'})
  assert.ok(badProduct.body.includes('Check the product'));assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=$1',[created.p_id])).rows[0].p_qty,0)
  const confirmDelete=await request(`admin/product-delete.php?id=${created.p_id}`);assert.ok(confirmDelete.body.includes('Confirm this change'))
  assert.equal((await db.query('SELECT p_is_active FROM tbl_product WHERE p_id=$1',[created.p_id])).rows[0].p_is_active,1)
  const deleted=await request(`admin/product-delete.php?id=${created.p_id}`,{_csrf:token});assert.equal(deleted.status,302)
  assert.equal((await db.query('SELECT p_is_active FROM tbl_product WHERE p_id=$1',[created.p_id])).rows[0].p_is_active,0)
  console.log(`PASS: ${adminPages.length} admin page requests, settings, product creation/validation and confirmed product archival.`)
  cookie=''
  const register=await request('registration.php');assert.equal(register.status,200)
  const signup=await request('registration.php',{_csrf:csrf(register.body),cust_name:'Test Customer',cust_email:'customer@example.com',cust_phone:'01712345678',cust_password:'a customer password 123',cust_re_password:'a customer password 123',cust_address:'10 Test Road',cust_city:'Dhaka',cust_state:'Dhaka',cust_zip:'1207'})
  assert.equal(signup.status,302,signup.body.slice(-1500))
  const customer=(await db.query("SELECT cust_id,cust_status FROM tbl_customer WHERE cust_email='customer@example.com'")).rows[0]
  assert.ok(customer);assert.equal(customer.cust_status,1)
  let detail=await request('product.php?id=1');
  if(detail.status===301 || detail.status===302) detail=await request(detail.location.replace(base+'/',''))
  assert.equal(detail.status,200,detail.body.slice(-1200))
  assert.ok(csrf(detail.body),'Product CSRF input missing')
  const added=await request('product/test-product-1',{_csrf:csrf(detail.body),form_buy_now:'1',p_qty:'1',size_id:'0',color_id:'0'})
  assert.equal(added.status,302,added.body.slice(-1200))
  const checkout=await request('checkout.php');assert.equal(checkout.status,200,checkout.body.slice(-1200))
  const checkoutToken=checkout.body.match(/name="checkout_token" value="([^"]+)"/)?.[1];assert.ok(checkoutToken,'Missing checkout token')
  const order=await request('payment/cod/process.php',{_csrf:csrf(checkout.body),checkout_token:checkoutToken});assert.equal(order.status,302,order.body)
  const repeated=await request('payment/cod/process.php',{_csrf:csrf(checkout.body),checkout_token:checkoutToken});assert.equal(repeated.location,order.location)
  assert.equal((await db.query('SELECT count(*)::int AS count FROM tbl_payment')).rows[0].count,1)
  assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=1')).rows[0].p_qty,1)
  const receipt=await request(order.location.replace(/^\.\.\/\.\.\//,''));assert.equal(receipt.status,200)
  const publicPages=['about.php','contact.php','faq.php','features.php','cart.php','search-result.php?search_text=Test','product-category.php?id=1&type=top-category','customer-order.php','customer-orders.php','customer-orders.php?status=to_cancel','customer-profile.php','customer-profile-update.php','customer-billing-shipping-update.php','customer-password-update.php','customer-wishlist.php','customer-reviews.php','customer-returns.php','dashboard.php','sitemap.php']
  for(const page of publicPages) {
    const result=await request(page);assert.ok(result.status<500,`${page}: HTTP ${result.status}\n${await fs.readFile(path.join(temp,'php-error.log'),'utf8').catch(()=>'')}`)
  }
  assert.equal((await request('ajax/cancel-order.php',{payment_id:'test'})).status,403)
  assert.equal((await request('payment/swapnopay/verify.php')).status,503)
  assert.equal((await request('payment/sslcommerz/success_handler.php')).status,503)
  const detailAgain=await request('product/test-product-1')
  const addAgain=await request('product/test-product-1',{_csrf:csrf(detailAgain.body),form_buy_now:'1',p_qty:'1',size_id:'0',color_id:'0'})
  assert.equal(addAgain.status,302)
  const checkoutAgain=await request('checkout.php')
  const tokenAgain=checkoutAgain.body.match(/name="checkout_token" value="([^"]+)"/)?.[1]
  await db.exec('UPDATE tbl_product SET p_qty=0 WHERE p_id=1')
  const soldOut=await request('payment/cod/process.php',{_csrf:csrf(checkoutAgain.body),checkout_token:tokenAgain})
  assert.equal(soldOut.status,409); assert.ok(soldOut.body.includes('no longer available'))
  assert.equal((await db.query('SELECT count(*)::int AS count FROM tbl_payment')).rows[0].count,1)
  assert.equal((await db.query('SELECT count(*)::int AS count FROM tbl_order')).rows[0].count,1)
  await db.exec('UPDATE tbl_product SET p_qty=1 WHERE p_id=1')
  // A SQL failure after payment insertion must roll back both payment and stock.
  await db.exec('ALTER TABLE tbl_order ADD CONSTRAINT force_test_order_failure CHECK(quantity<2)')
  await db.exec('CREATE FUNCTION fail_test_order() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION \'test order failure\'; END; $$; CREATE TRIGGER fail_test_order BEFORE INSERT ON tbl_order FOR EACH ROW EXECUTE FUNCTION fail_test_order()')
  const failedOrder=await request('payment/cod/process.php',{_csrf:csrf(checkoutAgain.body),checkout_token:tokenAgain})
  assert.equal(failedOrder.status,409)
  assert.equal((await db.query('SELECT count(*)::int AS count FROM tbl_payment')).rows[0].count,1)
  assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=1')).rows[0].p_qty,1)
  await db.exec('DROP TRIGGER fail_test_order ON tbl_order')
  const customerCookie=cookie
  cookie=adminCookie
  const orderList=await request('admin/order.php');assert.equal(orderList.status,200)
  const reference=(await db.query('SELECT payment_id FROM tbl_payment LIMIT 1')).rows[0].payment_id
  const summary=await request(`admin/order-summary.php?payment_id=${reference}`);assert.equal(summary.status,200);assert.ok(summary.body.includes('Test Customer'))
  const orderToken=csrf(orderList.body)
  const cancelled=await request(`admin/order-delete.php?id=${reference}`,{_csrf:orderToken});assert.equal(cancelled.status,302)
  assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=1')).rows[0].p_qty,2)
  await request(`admin/order-delete.php?id=${reference}`,{_csrf:orderToken})
  assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=1')).rows[0].p_qty,2)
  assert.equal((await db.query('SELECT count(*)::int AS count FROM tbl_order')).rows[0].count,1)
  cookie=customerCookie
  // Customer cancellation shares the locked, idempotent stock transaction.
  const customerPage=await request('customer-order.php')
  const customerToken=csrf(customerPage.body) || customerPage.body.match(/_csrf: "([^"]+)"/)?.[1]
  assert.ok(customerToken)
  await db.exec("UPDATE tbl_payment SET payment_status='Pending',shipping_status='Pending'; UPDATE tbl_product SET p_qty=1 WHERE p_id=1")
  const unauthorizedCancel=await request('ajax/cancel-order.php',{_csrf:customerToken,payment_id:'another-customer-order'})
  assert.equal(unauthorizedCancel.status,409)
  await db.exec("UPDATE tbl_payment SET payment_status='Completed'")
  assert.equal((await request('ajax/cancel-order.php',{_csrf:customerToken,payment_id:reference})).status,409)
  await db.exec("UPDATE tbl_payment SET payment_status='Pending'")
  for(let attempt=0;attempt<2;attempt++) {
    const cancellation=await request('ajax/cancel-order.php',{_csrf:customerToken,payment_id:reference})
    assert.equal(cancellation.status,200);assert.equal(JSON.parse(cancellation.body).status,'success')
    assert.equal((await db.query('SELECT p_qty FROM tbl_product WHERE p_id=1')).rows[0].p_qty,2)
  }
  cookie=''; const otherReceipt=await request(order.location.replace(/^\.\.\/\.\.\//,''));assert.equal(otherReceipt.status,404)
  const errors=await fs.readFile(path.join(temp,'php-error.log'),'utf8').catch(()=>'')
  assert.ok(!/PHP (Fatal error|Parse error|Warning)/.test(errors),errors)
  console.log(`PASS: ${settingsForms.length} additional settings forms, category protection, ${publicPages.length} storefront/customer pages, customer cancellation and stock restoration.`)
  console.log('PASS: PHP home, admin authentication/CSRF, customer registration, product, add to cart, checkout, stock, duplicate submission, sold-out rollback, database failure rollback and receipt ownership.')
  if(errors) console.log('PHP notices:',errors.slice(-3000))
  if(process.env.SHOP_TEST_PREVIEW==='1') { console.log(`PREVIEW: ${base}`);await new Promise(resolve=>process.once('SIGINT',resolve)) }
} finally {
  processPhp?.kill()
  if(processPhp) await new Promise(resolve=>processPhp.once('exit',resolve))
  await server?.stop();await db.close()
  const resolved=path.resolve(temp),parent=path.resolve(os.tmpdir())+path.sep
  if(resolved.startsWith(parent)) await fs.rm(resolved,{recursive:true,force:true})
}
