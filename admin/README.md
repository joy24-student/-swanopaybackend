# Lenden Admin Panel (Firebase)

This is a minimal admin panel scaffold (Vite + React + TypeScript) that connects to Firebase Auth and Firestore.

Quick start:

```bash
cd admin
npm install
# configure src/firebaseConfig.ts with your Firebase project values
npm run dev
```

Pages:
- `/login` — Admin login (email/password)
- `/dashboard` — Overview
- `/merchants` — List merchants
- `/merchants/:id` — Merchant detail and hosted forms

Next steps (I can implement these):
- Merchant CRUD (create/update/delete)
- Forms & submissions viewer with filters
- Analytics charts and export
- Firebase security rules for admin roles
- GitHub Actions + Firebase Hosting deploy

