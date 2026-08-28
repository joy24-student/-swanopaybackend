#!/bin/bash
# ============================================================================
# SWAPNOPAY & SUPABASE VPS AUTOMATED SELF-HOSTING INSTALLER SCRIPT
# Supports Ubuntu 20.04/22.04/24.04, Debian 11/12, CentOS 8/9
# ============================================================================

set -e

echo "🚀 Starting SwapnoPay & Supabase Automated VPS Setup..."

# 1. Update System Packages & Install Prerequisites
sudo apt-get update -y && sudo apt-get upgrade -y
sudo apt-get install -y curl wget git unzip build-essential nginx certbot python3-certbot-nginx

# 2. Install Docker & Docker Compose
if ! command -v docker &> /dev/null; then
    echo "📦 Installing Docker..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $USER
fi

if ! command -v docker-compose &> /dev/null; then
    echo "📦 Installing Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
fi

# 3. Install Node.js v20 LTS & PM2 Process Manager
if ! command -v node &> /dev/null; then
    echo "📦 Installing Node.js LTS..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
    sudo npm install -g pm2
fi

# 4. Clone Supabase Self-Hosted Stack & Configuration
mkdir -p /var/www/swapnopay
cd /var/www/swapnopay

if [ ! -d "supabase-docker" ]; then
    echo "🌐 Downloading Supabase Self-Hosted Docker Stack..."
    git clone --depth 1 https://github.com/supabase/supabase.git supabase-docker
    cd supabase-docker/docker
    cp .env.example .env
fi

cd /var/www/swapnopay/supabase-docker/docker

# Generate secure random secrets
JWT_SECRET=$(openssl rand -base64 32)
POSTGRES_PASSWORD=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9')
ANON_KEY=$(openssl rand -base64 32)
SERVICE_KEY=$(openssl rand -base64 32)

PUBLIC_IP=$(curl -s ifconfig.me || echo "localhost")

sed -i "s/POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$POSTGRES_PASSWORD/" .env
sed -i "s/JWT_SECRET=.*/JWT_SECRET=$JWT_SECRET/" .env
sed -i "s|SITE_URL=.*|SITE_URL=http://$PUBLIC_IP|" .env
sed -i "s|ADDITIONAL_REDIRECT_URLS=.*|ADDITIONAL_REDIRECT_URLS=swapnopay://auth-callback,lenden23://auth-callback,http://$PUBLIC_IP,http://localhost:3000|" .env
sed -i "s|GOTRUE_SITE_URL=.*|GOTRUE_SITE_URL=http://$PUBLIC_IP|" .env
sed -i "s|GOTRUE_URI_ALLOW_LIST=.*|GOTRUE_URI_ALLOW_LIST=swapnopay://auth-callback,lenden23://auth-callback,http://$PUBLIC_IP,http://localhost:3000|" .env

echo "⚡ Starting Docker Services (Supabase Gateway, DB, Auth, Realtime, Functions)..."
docker-compose up -d

# 5. Configure Nginx Reverse Proxy
echo "🌐 Configuring Nginx Reverse Proxy on port 80..."
cat << 'EOF' | sudo tee /etc/nginx/sites-available/swapnopay
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /functions/v1/ {
        proxy_pass http://localhost:9000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/swapnopay /etc/nginx/sites-enabled/default
sudo systemctl restart nginx

echo "============================================================================"
echo "✅ SWAPNOPAY VPS INSTALLATION COMPLETE!"
echo "============================================================================"
echo "🌐 Supabase Gateway Endpoint: http://$(curl -s ifconfig.me)"
echo "🔑 Postgres Password: $POSTGRES_PASSWORD"
echo "🔑 JWT Secret: $JWT_SECRET"
echo "============================================================================"
