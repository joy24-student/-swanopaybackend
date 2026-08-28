import React, { useState } from 'react'
import { signInWithEmailAndPassword } from 'firebase/auth'
import { auth } from '../firebaseConfig'
import { useNavigate } from 'react-router-dom'

export default function Login(){
  const [email,setEmail]=useState('')
  const [password,setPassword]=useState('')
  const nav = useNavigate()
  async function doLogin(){
    try{await signInWithEmailAndPassword(auth,email,password);nav('/dashboard')}
    catch(e){alert('Login failed: '+String(e))}
  }
  return (
    <div className="container">
      <div className="card" style={{maxWidth:420,margin:'40px auto'}}>
        <h2>Admin Login</h2>
        <input className="input" placeholder="email" value={email} onChange={e=>setEmail(e.target.value)} />
        <div style={{height:12}} />
        <input className="input" placeholder="password" type="password" value={password} onChange={e=>setPassword(e.target.value)} />
        <div style={{height:12}} />
        <button className="button" onClick={doLogin}>Sign in</button>
      </div>
    </div>
  )
}
