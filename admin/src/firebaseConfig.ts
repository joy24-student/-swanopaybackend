// Replace the placeholders with your Firebase project config and enable Firestore and Auth
import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

const firebaseConfig = {
  apiKey: (import.meta.env.VITE_FIREBASE_API_KEY as string) || "YOUR_API_KEY",
  authDomain: (import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string) || "YOUR_PROJECT.firebaseapp.com",
  projectId: (import.meta.env.VITE_FIREBASE_PROJECT_ID as string) || "YOUR_PROJECT",
  storageBucket: (import.meta.env.VITE_FIREBASE_STORAGE_BUCKET as string) || "YOUR_PROJECT.appspot.com",
  messagingSenderId: (import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID as string) || "",
  appId: (import.meta.env.VITE_FIREBASE_APP_ID as string) || ""
}

const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)
export default app
