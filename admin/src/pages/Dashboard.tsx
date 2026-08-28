import React, { useEffect, useState } from 'react'
import { collection, query, getDocs } from 'firebase/firestore'
import { db } from '../firebaseConfig'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'

export default function Dashboard(){
  const [merchantCount,setMerchantCount]=useState<number|null>(null)
  const { signOut } = useAuth()
  useEffect(()=>{(async()=>{
    try{
      const q = query(collection(db,'merchants'))
      const snap = await getDocs(q)
      setMerchantCount(snap.size)
    }catch(e){console.error(e)}
  })()},[])
  return (
    <div className="container">
      <div className="header">
        <h1>Admin Dashboard</h1>
        <div>
          <Link to="/merchants"><button className="button">Merchants</button></Link>
          <Link to="/submissions"><button className="button" style={{marginLeft:8}}>Submissions</button></Link>
          <Link to="/connect-supabase"><button className="button" style={{marginLeft:8, background:'#3ECF8E', color:'#0f172a', fontWeight:'bold'}}>⚡ Connect Supabase</button></Link>
          <button className="button" style={{marginLeft:8}} onClick={()=>signOut()}>Sign out</button>
        </div>
      </div>
      <div className="card">
        <h3>Overview</h3>
        <div>Merchants: {merchantCount===null?'...':merchantCount}</div>
        <div style={{marginTop:16}}>
          <Link to="/connect-supabase" style={{textDecoration:'none'}}>
            <button className="button" style={{background:'#3ECF8E', color:'#0f172a', fontWeight:'bold', padding:'10px 18px', display:'inline-flex', alignItems:'center', gap:8}}>
              ⚡ Open Supabase OAuth & Management Integration
            </button>
          </Link>
        </div>
      </div>
    </div>
  )
}
