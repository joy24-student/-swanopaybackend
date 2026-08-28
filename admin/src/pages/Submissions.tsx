import React, { useEffect, useState } from 'react'
import { collection, query, getDocs, orderBy } from 'firebase/firestore'
import { db } from '../firebaseConfig'
import { Link } from 'react-router-dom'

export default function Submissions(){
  const [rows,setRows]=useState<Array<any>>([])
  useEffect(()=>{(async()=>{
    try{
      const q = query(collection(db,'platform_submissions'), orderBy('createdAt','desc'))
      const snap = await getDocs(q)
      setRows(snap.docs.map(d=>({id:d.id,...d.data()})))
    }catch(e){console.error(e)}
  })()},[])

  return (
    <div className="container">
      <div className="header"><h1>Submissions</h1><div><Link to="/dashboard"><button className="button">Back</button></Link></div></div>
      <div className="card">
        <table className="table"><thead><tr><th>Time</th><th>Merchant</th><th>Form</th><th>Summary</th></tr></thead>
          <tbody>
            {rows.map(r=> (
              <tr key={r.id}><td>{new Date(r.createdAt||'').toLocaleString()}</td><td>{r.merchantId||'--'}</td><td>{r.formId||'--'}</td><td style={{maxWidth:500,whiteSpace:'nowrap',overflow:'hidden',textOverflow:'ellipsis'}}>{JSON.stringify(r.submission||{})}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
