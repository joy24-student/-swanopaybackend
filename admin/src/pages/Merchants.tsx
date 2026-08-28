import React, { useEffect, useState } from 'react'
import { collection, query, getDocs } from 'firebase/firestore'
import { db } from '../firebaseConfig'
import { Link } from 'react-router-dom'

export default function Merchants(){
  const [rows,setRows]=useState<Array<any>>([])
  useEffect(()=>{(async()=>{
    try{
      const q = query(collection(db,'merchants'))
      const snap = await getDocs(q)
      setRows(snap.docs.map(d=>({id:d.id,...d.data()})))
    }catch(e){console.error(e)}
  })()},[])

  return (
    <div className="container">
      <div className="header"><h1>Merchants</h1><div><Link to="/dashboard"><button className="button">Back</button></Link></div></div>
      <div className="card">
        <table className="table"><thead><tr><th>Name</th><th>Slug</th><th>Actions</th></tr></thead>
          <tbody>
            {rows.map(r=> (
              <tr key={r.id}><td>{r.title||r.name||'--'}</td><td>{r.slug||'--'}</td><td><Link to={`/merchants/${r.id}`}><button className="button">View</button></Link></td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
