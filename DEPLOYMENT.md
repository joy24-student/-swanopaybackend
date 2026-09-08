# 🚀 SwapnoPay Production Deployment & Hosting Guide

This guide covers how to deploy and host the **`web`** frontend folder and **`swapnopay-backend`** Node.js + Express + Socket.io server.

---

## 📁 Repository Structure

| Folder | Purpose | Hosting Options |
|---|---|---|
| **`swapnopay-backend/`** | Node.js Express API + Socket.io WebSockets | Render, Railway, DigitalOcean, VPS (Docker/PM2), AWS, GCP |
| **`web/`** | Static Web Assets (Widget, Portal, Docs, Forms) | Cloudflare Pages, Netlify, Vercel, Nginx, GitHub Pages |

---

## ⚡ Option 1: 1-Click Automated VPS Installer (Recommended)

Run our automated production setup script on any clean Ubuntu 22.04 / 24.04 VPS:

```bash
# 1. Clone repository to VPS
git clone https://github.com/joy24-student/-swanopaybackend.git /var/www/swapnopay
cd /var/www/swapnopay

# 2. Make script executable and run with your domain
chmod +x deploy-vps.sh
sudo ./deploy-vps.sh swapnopay.top admin@swapnopay.top
```

This automated script:
- Installs and hardens UFW Firewall (ports 22, 80, 443)
- Installs Node.js 20 LTS, npm, and PM2
- Installs backend dependencies & creates secure `uploads/kyc` folders
- Auto-generates cryptographic 32-byte security keys (`ADMIN_SECRET`, `API_KEY_PEPPER`, `PAYMENT_WEBHOOK_SECRET`)
- Builds the Vite React Admin Panel into production static assets
- Deploys PM2 process service for `swapnopay-backend`
- Generates Nginx virtual hosts for `pay.swapnopay.top`, `admin.swapnopay.top`, `api.swapnopay.top`
- Issues and activates free Let's Encrypt SSL certificates via Certbot
- Tests backend healthcheck at `http://127.0.0.1:4000/healthz`

---

## ⚡ Option 2: 1-Click Docker Compose Deployment

Deploy both `web` and `swapnopay-backend` via Docker Compose on Ubuntu VPS:

### Step 1: Clone Repository & Create Environment File
```bash
git clone <your-repository-url>
cd lenden23

# Create production environment variables
cat << 'EOF' > .env
ADMIN_SUPABASE_URL=https://your-admin-project.supabase.co
ADMIN_SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
ADMIN_SECRET=change-me-to-a-random-32-char-string
API_KEY_PEPPER=change-me-to-another-random-32-char-string
PAYMENT_WEBHOOK_SECRET=change-me-webhook-secret-32-chars
ALLOWED_ORIGINS=*
EOF
```

### Step 2: Start Services via Docker Compose
```bash
docker-compose up -d --build
```

### Step 3: Verify Deployment
- **Web Checkout Widget**: `http://<your-vps-ip>/widget.html`
- **Developer Documentation**: `http://<your-vps-ip>/docs.html`
- **Backend Health Check**: `http://<your-vps-ip>/healthz`

---

## 🌐 Option 2: Cloud PaaS Deployment (Render / Railway + Netlify / Cloudflare)

### A. Deploy `swapnopay-backend` (Node.js API & Socket.io)

#### On Render.com / Railway.app:
1. Create a **Web Service** pointing to the `swapnopay-backend` directory.
2. Set Build Command: `npm install`
3. Set Start Command: `npm start`
4. Add Environment Variables:
   - `ADMIN_SUPABASE_URL` = `https://your-admin-project.supabase.co`
   - `ADMIN_SUPABASE_SERVICE_ROLE_KEY` = `eyJhbG...`
   - `ADMIN_SECRET` = `your-admin-secret-key`
   - `API_KEY_PEPPER` = `your-api-key-pepper`
   - `PAYMENT_WEBHOOK_SECRET` = `your-webhook-secret`
   - `ALLOWED_ORIGINS` = `https://pay.swapnopay.top,https://admin.swapnopay.top`

---

### B. Deploy `web` Folder (Static Frontend & Widget)

#### On Cloudflare Pages / Netlify / Vercel:
1. Create a new static site project pointing to the `web` folder.
2. Build Command: *(Leave blank)*
3. Publish Directory: `web`
4. Custom Domain: `pay.swapnopay.top`

---

## 🛠 Option 3: Traditional VPS Deployment (Nginx + PM2 + SSL Certbot)

### Step 1: Install Node.js, PM2 & Nginx
```bash
sudo apt update && sudo apt install -y nodejs npm nginx certbot python3-certbot-nginx
sudo npm install -g pm2
```

### Step 2: Start SwapnoPay Backend with PM2
```bash
cd swapnopay-backend
npm install --production
pm2 start ecosystem.config.js --env production
pm2 save
pm2 startup
```

### Step 3: Configure Nginx & SSL Certificate
```bash
sudo cp nginx.conf /etc/nginx/sites-available/swapnopay
sudo ln -s /etc/nginx/sites-available/swapnopay /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# Issue Free SSL Certificate
sudo certbot --nginx -d pay.swapnopay.top -d api.swapnopay.top
```

---

## ✅ Production Checklist

- [x] Run `ADMIN_DATABASE_SCHEMA.sql` in your Admin Supabase project SQL Editor
- [x] Configure backend environment variables (`ADMIN_SUPABASE_URL`, `ADMIN_SUPABASE_SERVICE_ROLE_KEY`)
- [x] Set `SWAPNOPAY_BACKEND_URL` and `PAYMENT_WEBHOOK_SECRET` in edge function secrets
- [x] Test `GET /healthz` endpoint returns HTTP 200 `ok`
- [x] Verify Socket.io connection on `ws://` / `wss://`
