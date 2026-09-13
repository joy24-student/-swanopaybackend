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
