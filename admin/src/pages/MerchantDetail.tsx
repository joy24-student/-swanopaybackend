import React, { useEffect, useState } from 'react'
import { doc, getDoc, collection, query, getDocs, setDoc } from 'firebase/firestore'
import { db } from '../firebaseConfig'
import { useParams, Link } from 'react-router-dom'

export default function MerchantDetail(){
  const { id } = useParams()
  const [merchant,setMerchant]=useState<any|null>(null)
  const [forms,setForms]=useState<any[]>([])
  const [connection,setConnection]=useState<any|null>(null)
  const [publicEndpoint,setPublicEndpoint]=useState('')
  const [webhookUrl,setWebhookUrl]=useState('')
  const [testResult,setTestResult]=useState<string | null>(null)

  useEffect(()=>{(async()=>{
    if(!id) return
    try{
      const d = await getDoc(doc(db,'merchants',id))
      if(d.exists()) setMerchant({id:d.id,...d.data()})
      const q = query(collection(db,'payment_forms'))
      const snap = await getDocs(q)
      setForms(snap.docs.filter(s=>s.data().merchant_id===id).map(s=>({id:s.id,...s.data()})))
      // load optional public connection info (only stored with merchant consent)
      const connDoc = await getDoc(doc(db,'merchants',id,'connections','public'))
      if(connDoc.exists()){
        setConnection({id:connDoc.id,...connDoc.data()})
        setPublicEndpoint((connDoc.data() as any).publicEndpoint||'')
        setWebhookUrl((connDoc.data() as any).webhookUrl||'')
      }
    }catch(e){console.error(e)}
  })()},[id])

  if(!merchant) return <div className="container"><div className="card">Loading...</div></div>
  return (
    <div className="container">
      <div className="header"><h1>Merchant: {merchant.title||merchant.name}</h1><div><Link to="/merchants"><button className="button">Back</button></Link></div></div>
      <div className="card">
        <h3>Details</h3>
        <div><strong>ID:</strong> {merchant.id}</div>
        <div><strong>Email:</strong> {merchant.email||'--'}</div>
        <div><strong>Slug:</strong> {merchant.slug||'--'}</div>
      </div>

      <div style={{height:12}} />
      <div className="card">
        <h3>Hosted Forms</h3>
        <ul>
          {forms.map(f=> <li key={f.id}>{f.title||f.slug}</li>)}
        </ul>
      </div>

      <div style={{height:12}} />
      <div className="card">
        <h3>Connections (No DB keys collected)</h3>
        <p style={{fontSize:12,color:'#666'}}>Only add endpoints/webhooks with explicit merchant consent. This UI does not store or request database credentials.</p>
        <div style={{marginTop:8}}>
          <label>Public Endpoint (GET) — e.g. merchant hosted-form URL</label>
          <input value={publicEndpoint} onChange={e=>setPublicEndpoint(e.target.value)} placeholder="https://merchant-hosted.example/form/slug" style={{width:'100%'}} />
          <div style={{marginTop:6}}>
            <button className="button" onClick={async()=>{
              setTestResult('Testing...')
              try{
                const r = await fetch(publicEndpoint||'')
                setTestResult(`Status: ${r.status}`)
              }catch(err){ setTestResult('Fetch error: '+String(err)) }
            }}>Test Fetch</button>
            <button className="button" style={{marginLeft:8}} onClick={async()=>{
              if(!id) return
              try{
                await setDoc(doc(db,'merchants',id,'connections','public'),{ publicEndpoint, webhookUrl, updatedAt: new Date().toISOString() }, { merge: true })
                setConnection({ publicEndpoint, webhookUrl })
                setTestResult('Saved')
              }catch(err){ setTestResult('Save error: '+String(err)) }
            }}>Save</button>
          </div>
        </div>

        <div style={{height:12}} />
        <div>
          <label>Webhook URL (POST) — optional</label>
          <input value={webhookUrl} onChange={e=>setWebhookUrl(e.target.value)} placeholder="https://hooks.example.com/receive" style={{width:'100%'}} />
          <div style={{marginTop:6}}>
            <button className="button" onClick={async()=>{
              setTestResult('Sending test...')
              try{
                const r = await fetch(webhookUrl||'', { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ test: true, merchantId: id }) })
                setTestResult(`Status: ${r.status}`)
              }catch(err){ setTestResult('Send error: '+String(err)) }
            }}>Send Test POST</button>
            <span style={{marginLeft:12,color:'#666'}}>Or instruct merchant to POST to this platform webhook URL instead.</span>
          </div>
        </div>

        {testResult && <div style={{marginTop:12}}><strong>Result:</strong> {testResult}</div>}
      </div>
    </div>
  )
}
