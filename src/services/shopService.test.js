import test from 'node:test'
import assert from 'node:assert/strict'
import { PGlite } from '@electric-sql/pglite'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import bcrypt from 'bcryptjs'
import sharp from 'sharp'
import { ShopService, shopConfiguration } from './shopService.js'
import { encryptConfig, decryptConfig, launchInput, inventoryItems, schemaName, ShopError } from './shopValidation.js'
import { checkShopDns } from './shopReadiness.js'

const merchant='11111111-1111-4111-8111-111111111111', second='22222222-2222-4222-8222-222222222222'
const launch={merchant_id:merchant,store_name:'Sample Store',shop_slug:'sample-store',admin_email:'owner@example.com',admin_password:'a secure password 123'}
test('launch validation rejects invalid hosts, weak passwords, currencies and unstable inventory IDs',()=>{
  for(const input of [{custom_domain:'https://example.com'},{custom_domain:'127.0.0.1'},{shop_slug:'../admin'},{admin_password:'short'},{primary_currency:'USD'}]) assert.throws(()=>launchInput({...launch,...input}),ShopError)
  assert.throws(()=>inventoryItems([{name:'Missing ID',price:10}]),ShopError)
  assert.throws(()=>inventoryItems([{id:'1',name:'A',price:10,stock:-1}]),ShopError)
  assert.throws(()=>shopConfiguration({}),/not configured/)
})
test('encrypted provisioning secrets cannot be read or tampered with',()=>{
  const key='ab'.repeat(32), value=encryptConfig({password:'private'},key)
  assert.ok(!value.includes('private'))
  assert.deepEqual(decryptConfig(value,key),{password:'private'})
  assert.throws(()=>decryptConfig(value,'cd'.repeat(32)))
})
test('DNS verification rejects missing or unexpected addresses',async()=>{
  assert.equal(await checkShopDns('example.com',['203.0.113.1'],async()=>[{address:'203.0.113.2'}]),false)
})
test('clean PostgreSQL schema, real provisioning, retries, tenant privileges and idempotent catalog',async()=>{
  const db=new PGlite()
  const directory=await fs.mkdtemp(path.join(process.env.TEMP || os.tmpdir(),'shop-test-'))
  try {
    // PGlite runs one connection. Advisory locks are PostgreSQL server primitives;
    // emulate only these locks, while executing every table/query/grant for real.
    const query=async(sql,params=[])=>{
      if(sql.includes('pg_try_advisory_lock')) return {rows:[{locked:true}],rowCount:1}
      if(sql.includes('pg_advisory_')) return {rows:[],rowCount:1}
      if(!params.length && sql.includes(';')) { let result; try {result=await db.exec(sql)} catch(error) { console.error(error.message,sql.slice(Math.max(0,Number(error.position)-80),Number(error.position)+80)); throw error } const last=result.at(-1); return {...last,rowCount:last?.affectedRows ?? last?.rows?.length ?? 0} }
      const result=await db.query(sql,params)
      return {...result,rowCount:result.affectedRows ?? result.rows.length}
    }
    const pool={query,connect:async()=>({query,release(){}})}
    const config={key:'ab'.repeat(32),baseDomain:'shops.example.com',addresses:['203.0.113.1'],runtime:path.join(directory,'runtime'),sites:path.join(directory,'sites'),template:path.join(directory,'template'),dbHost:'localhost',dbPort:5432,dbName:'hosting',sslmode:'disable',backendUrl:'https://api.example.com'}
    await fs.mkdir(path.join(config.template,'assets','store-defaults'),{recursive:true})
    await fs.writeFile(path.join(config.template,'index.php'),'<?php echo "store";')
    await fs.writeFile(path.join(config.template,'.env'),'PRIVATE=secret')
    await fs.writeFile(path.join(config.template,'debug_test.php'),'unsafe')
    let dnsReady=false, httpsReady=false
    const shop=new ShopService(config,{pool,dns:async()=>dnsReady,probe:async()=>({ready:httpsReady,message:'Waiting for HTTPS'})})
    assert.equal((await shop.status(merchant)).deployed,false)
    const queued=await shop.enqueue(launch)
    assert.equal(queued.status,'QUEUED'); assert.equal(queued.deployed,false)
    assert.equal((await shop.enqueue(launch)).job_id,queued.job_id)
    await assert.rejects(()=>shop.enqueue({...launch,store_name:'Changed while busy'}),/already in progress/)
    await assert.rejects(()=>shop.enqueue({...launch,merchant_id:second}),/belongs to another/)
    await shop.tick()
    let status=await shop.status(merchant)
    assert.equal(status.status,'WAITING_DNS')
    const schema=schemaName(merchant)
    assert.equal((await db.query(`SELECT count(*)::int AS count FROM ${schema}.tbl_customer`)).rows[0].count,0)
    assert.equal((await db.query(`SELECT count(*)::int AS count FROM ${schema}.tbl_payment`)).rows[0].count,0)
    const admin=(await db.query(`SELECT password FROM ${schema}.tbl_user WHERE id=1`)).rows[0]
    assert.ok(await bcrypt.compare(launch.admin_password,admin.password))
    await assert.rejects(fs.access(path.join(config.sites,'stores',merchant,'.env')))
    await assert.rejects(fs.access(path.join(config.sites,'stores',merchant,'debug_test.php')))
    dnsReady=true
    await db.exec('UPDATE shop_control.launches SET next_attempt=now()')
    await shop.tick(); assert.equal((await shop.status(merchant)).status,'WAITING_TLS')
    httpsReady=true
    await db.exec('UPDATE shop_control.launches SET next_attempt=now()')
    await shop.tick(); assert.equal((await shop.status(merchant)).status,'LIVE')
    await db.exec("UPDATE shop_control.launches SET updated_at=now()-interval '2 minutes'")
    httpsReady=false
    assert.equal((await shop.status(merchant)).status,'DEGRADED')
    await db.exec("UPDATE shop_control.launches SET updated_at=now()-interval '2 minutes'")
    httpsReady=true
    assert.equal((await shop.status(merchant)).status,'LIVE')
    const items=[{id:'product-1',name:'Tea',price:99.50,stock:5}]
    assert.equal((await shop.sync(merchant,items)).products_count,1)
    assert.equal((await shop.sync(merchant,[{...items[0],stock:7}])).products_count,1)
    assert.equal((await db.query(`SELECT p_qty FROM ${schema}.tbl_product`)).rows[0].p_qty,7)
    const jpeg=await sharp({create:{width:2400,height:1200,channels:3,background:'#3155aa'}}).withMetadata().jpeg().toBuffer()
    const upload=await shop.uploadImage(merchant,{kind:'featured',base64:jpeg.toString('base64')})
    const gallery=await shop.uploadImage(merchant,{kind:'gallery',base64:jpeg.toString('base64')})
    assert.equal((await shop.uploadImage(merchant,{kind:'featured',base64:jpeg.toString('base64')})).filename,upload.filename)
    const uploadedFile=path.join(config.sites,'stores',merchant,'assets','uploads',upload.filename)
    const meta=await sharp(await fs.readFile(uploadedFile)).metadata()
    assert.equal(meta.width,2048);assert.equal(meta.exif,undefined)
    await assert.rejects(()=>shop.uploadImage(merchant,{kind:'featured',base64:Buffer.from('<svg></svg>').toString('base64')}),ShopError)
    const details={old_price:150,top_category:'Clothing',mid_category:'Adult',end_category:'Shirts',description:'Cotton <script>alert(1)</script>',short_description:'Soft cotton',features:'Washable\nBreathable',condition:'New',return_policy:'Within seven days',video_url:'https://youtu.be/abcdefghijk',sizes:['M','L'],colors:['Blue'],featured_image:upload.filename,gallery:[gallery.filename],is_featured:true,is_active:true}
    await shop.sync(merchant,[{...items[0],storefront:details}])
    await shop.sync(merchant,[{...items[0],storefront:details}])
    const product=(await db.query(`SELECT * FROM ${schema}.tbl_product`)).rows[0]
    assert.equal(Number(product.p_old_price),150);assert.equal(product.p_short_description,'Soft cotton');assert.equal(product.p_condition,'New');assert.equal(product.p_return_policy,'Within seven days')
    assert.equal(product.p_video_link,details.video_url);assert.equal(product.p_featured_photo,upload.filename);assert.equal(product.p_is_featured,1)
    assert.ok(!product.p_description.includes('<script>'));assert.ok(product.p_feature.includes('<br>'))
    assert.equal((await db.query(`SELECT count(*)::int AS n FROM ${schema}.tbl_product_size`)).rows[0].n,2)
    assert.equal((await db.query(`SELECT count(*)::int AS n FROM ${schema}.tbl_product_color`)).rows[0].n,1)
    assert.equal((await db.query(`SELECT count(*)::int AS n FROM ${schema}.tbl_product_photo`)).rows[0].n,1)
    assert.equal((await db.query(`SELECT ecat_name FROM ${schema}.tbl_end_category WHERE ecat_id=$1`,[product.ecat_id])).rows[0].ecat_name,'Shirts')
    await assert.rejects(()=>shop.sync(merchant,[{...items[0],storefront:{...details,featured_image:'../../other-store/image.jpg'}}]),ShopError)
    await assert.rejects(()=>shop.sync(merchant,[{...items[0],storefront:{...details,old_price:-1}}]),ShopError)
    await shop.sync(merchant,[{...items[0],storefront:{...details,is_active:false,sizes:[],colors:[],gallery:[]}}])
    assert.equal((await db.query(`SELECT p_is_active FROM ${schema}.tbl_product`)).rows[0].p_is_active,0)
    assert.equal((await db.query(`SELECT count(*)::int AS n FROM ${schema}.tbl_product_photo`)).rows[0].n,0)
    await shop.sync(merchant,items) // Older app versions preserve metadata and archival.
    assert.equal((await db.query(`SELECT p_is_active FROM ${schema}.tbl_product`)).rows[0].p_is_active,0)
    await shop.enqueue({...launch,merchant_id:second,shop_slug:'second-store'})
    await shop.tick()
    await db.exec(`SET ROLE "${schema}"`)
    assert.equal((await db.query(`SELECT count(*)::int AS count FROM ${schema}.tbl_product`)).rows[0].count,1)
    await assert.rejects(()=>db.query(`SELECT * FROM ${schemaName(second)}.tbl_product`),/permission denied/)
    await assert.rejects(()=>db.query('SELECT * FROM shop_control.launches'),/permission denied/)
    await db.exec('RESET ROLE')
  } finally { await db.close(); await fs.rm(directory,{recursive:true,force:true}) }
})
