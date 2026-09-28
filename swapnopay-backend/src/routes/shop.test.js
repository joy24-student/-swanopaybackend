import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { createShopRouter } from './shop.js'
import { ShopError } from '../services/shopValidation.js'

const id='11111111-1111-4111-8111-111111111111'
test('shop API enforces authentication, validates errors and returns queued launches without claiming LIVE',async()=>{
  let calls=0
  const app=express()
  app.use(express.json())
  app.use('/v1/shop',createShopRouter({
    authenticate:(req,res,next)=>req.headers.authorization==='Bearer test-owner' ? next() : res.status(401).json({ok:false}),
    service:()=>({status:async()=>({ok:true,deployed:false,status:'NOT_DEPLOYED'}),enqueue:async body=>{calls++;if(body.shop_slug==='taken') throw new ShopError(409,'ADDRESS_TAKEN','Address is taken');return {ok:true,deployed:false,status:'QUEUED'}},sync:async()=>{throw new Error('database password must not leak')}})
  }))
  const server=app.listen(0,'127.0.0.1'); await new Promise(resolve=>server.once('listening',resolve))
  const base=`http://127.0.0.1:${server.address().port}/v1/shop`
  const headers={'Content-Type':'application/json',Authorization:'Bearer test-owner'}
  try {
    assert.equal((await fetch(`${base}/status?merchant_id=${id}`)).status,401)
    assert.equal((await fetch(`${base}/deploy`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({merchant_id:id})})).status,401)
    assert.equal(calls,0)
    const queued=await fetch(`${base}/deploy`,{method:'POST',headers,body:JSON.stringify({merchant_id:id})})
    assert.equal(queued.status,202); assert.equal((await queued.json()).deployed,false)
    assert.equal((await fetch(`${base}/deploy`,{method:'POST',headers,body:JSON.stringify({merchant_id:id,shop_slug:'taken'})})).status,409)
    assert.equal((await fetch(`${base}/status?merchant_id=${id}`,{headers})).status,200)
    assert.equal((await fetch(`${base}/deploy?merchant_id=22222222-2222-4222-8222-222222222222`,{method:'POST',headers,body:JSON.stringify({merchant_id:id})})).status,400)
    const failure=await fetch(`${base}/sync-inventory`,{method:'POST',headers,body:JSON.stringify({merchant_id:id,items:[]})})
    assert.equal(failure.status,503); assert.ok(!(await failure.text()).includes('password'))
  } finally { await new Promise(resolve=>server.close(resolve)) }
})

test('requireShopAuth authenticates via device-id, anon-key and connect-database credentials', async () => {
  const { requireShopAuth } = await import('../middleware/shopAuth.js')
  const merchantUuid = '11111111-1111-4111-8111-111111111111'

  // 1. Device ID matching merchant ID
  const reqDevice = {
    headers: { 'x-device-id': merchantUuid },
    shopMerchantId: merchantUuid,
    path: '/status'
  }
  let calledDevice = false
  await requireShopAuth(reqDevice, {}, () => { calledDevice = true })
  assert.equal(calledDevice, true)
  assert.equal(reqDevice.merchantUser?.id, merchantUuid)
  assert.equal(reqDevice.authMethod, 'device_id')

  // 2. /connect-database onboarding with publishable anon_key in body
  const sampleAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRlc3QiLCJyb2xlIjoiYW5vbiIsImlhdCI6MTYwOTQ1OTIwMCwiZXhwIjoxOTI1MDM1MjAwfQ.test_signature_valid_anon_key_length_at_least_20'
  const reqConnect = {
    headers: {},
    shopMerchantId: merchantUuid,
    path: '/connect-database',
    body: {
      merchant_id: merchantUuid,
      supabase_url: 'https://testproj.supabase.co',
      supabase_anon_key: sampleAnonKey
    }
  }
  let calledConnect = false
  await requireShopAuth(reqConnect, {}, () => { calledConnect = true })
  assert.equal(calledConnect, true)
  assert.equal(reqConnect.authMethod, 'merchant_anon_key')

  // 3. Client provides registered merchant anon key via Bearer token
  const { setMerchantGatewayConfig } = await import('../services/adminSupabase.js')
  await setMerchantGatewayConfig(merchantUuid, {
    supabase_url: 'https://testproj.supabase.co',
    supabase_anon_key: sampleAnonKey
  })
  const reqAnon = {
    headers: { authorization: `Bearer ${sampleAnonKey}` },
    shopMerchantId: merchantUuid,
    path: '/status'
  }
  let calledAnon = false
  await requireShopAuth(reqAnon, {}, () => { calledAnon = true })
  assert.equal(calledAnon, true)
  assert.equal(reqAnon.authMethod, 'merchant_anon_key')

  // 4. Unauthenticated request returns 401 with informative error message
  const reqUnauth = {
    headers: {},
    shopMerchantId: '22222222-2222-4222-8222-222222222222',
    path: '/status'
  }
  let calledUnauth = false
  let statusCode = 0
  let respJson = null
  const resMock = {
    status(code) { statusCode = code; return this },
    json(body) { respJson = body; return this }
  }
  await requireShopAuth(reqUnauth, resMock, () => { calledUnauth = true })
  assert.equal(calledUnauth, false)
  assert.equal(statusCode, 401)
  assert.ok(respJson.error.includes('Sign in as the owner'))
})
