#!/usr/bin/env bash
# ==============================================================================
# SwapnoPay Web Shop & PostgreSQL Production VPS Deployment Script
# Targets Ubuntu 22.04 / 24.04 LTS or Debian 11 / 12
# ==============================================================================

set -euo pipefail

echo "================================================================="
echo "   SwapnoPay Enterprise Storefront - VPS Deployment Automation   "
echo "================================================================="

DOMAIN="${1:-}"
DB_USER="${2:-postgres}"
DB_PASS="${3:-}"
DB_NAME="${4:-swapnopay_shop}"

if [ -z "$DOMAIN" ]; then
    echo "Usage: ./deploy-vps-shop.sh <your-domain.com> [db_user] [db_password] [db_name]"
    echo "Example: ./deploy-vps-shop.sh mystore.swapnopay.top postgres mysecretpass swapnopay_shop"
    exit 1
fi

echo "[1/6] Updating system packages..."
sudo apt-get update && sudo apt-get upgrade -y

echo "[2/6] Installing Nginx, PHP 8.2 & PostgreSQL extensions..."
sudo apt-get install -y software-properties-common ca-certificates lsb-release apt-transport-https
sudo add-apt-repository ppa:ondrej/php -y || true
sudo apt-get update
sudo apt-get install -y \
    nginx \
    certbot \
    python3-certbot-nginx \
    php8.2-fpm \
    php8.2-cli \
    php8.2-pgsql \
    php8.2-mysql \
    php8.2-gd \
    php8.2-curl \
    php8.2-mbstring \
    php8.2-zip \
    php8.2-intl \
    php8.2-bcmath \
    php8.2-opcache \
    postgresql-client \
    curl \
    unzip \
    git

echo "[3/6] Setting up web root directory..."
sudo mkdir -p /var/www/shop
sudo chown -R www-data:www-data /var/www/shop
sudo chmod -R 755 /var/www/shop

# If current directory has shop files, copy them
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"
if [ -d "$PARENT_DIR/shop" ]; then
    echo "Copying storefront files from $PARENT_DIR/shop to /var/www/shop..."
    sudo cp -r "$PARENT_DIR/shop/." /var/www/shop/
    sudo cp "$PARENT_DIR/shop/.env.example" /var/www/shop/.env || true
fi

echo "[4/6] Configuring Nginx virtual host for $DOMAIN..."
NGINX_CONF="/etc/nginx/sites-available/$DOMAIN"
sudo bash -c "cat > '$NGINX_CONF'" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    root /var/www/shop;
    index index.php index.html index.htm;

    access_log /var/log/nginx/${DOMAIN}_access.log;
    error_log  /var/log/nginx/${DOMAIN}_error.log error;

    client_max_body_size 64M;

    location / {
        try_files \$uri \$uri/ /index.php?\$query_string;
    }

    location ~ \.php$ {
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/var/run/php/php8.2-fpm.sock;
        fastcgi_param SCRIPT_FILENAME \$document_root\$fastcgi_script_name;
        include fastcgi_params;
    }

    location ~ /\.ht {
        deny all;
    }

    location ~ /\.(?!well-known).* {
        deny all;
    }
}
EOF

sudo ln -sf "$NGINX_CONF" "/etc/nginx/sites-enabled/$DOMAIN"
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl restart php8.2-fpm

echo "[5/6] Checking PostgreSQL database schema..."
if [ -f "$PARENT_DIR/supabase/SHOP_POSTGRES_SCHEMA.sql" ] && [ -n "$DB_PASS" ]; then
    echo "Importing PostgreSQL production schema to $DB_NAME..."
    PGPASSWORD="$DB_PASS" psql -h localhost -U "$DB_USER" -d "$DB_NAME" -f "$PARENT_DIR/supabase/SHOP_POSTGRES_SCHEMA.sql" || {
        echo "PostgreSQL import warning: please verify database credentials or run the script manually in Supabase / Postgres."
    }
fi

echo "[6/6] Requesting Let's Encrypt SSL Certificate..."
echo "To obtain automatic SSL, execute:"
echo "sudo certbot --nginx -d $DOMAIN --non-interactive --agree-tos -m admin@$DOMAIN || true"

echo "================================================================="
echo "🎉 SwapnoPay VPS Storefront Deployment Completed Successfully!"
echo "   Site URL: https://$DOMAIN"
echo "   Root Path: /var/www/shop"
echo "   PHP Engine: PHP 8.2-FPM (PostgreSQL Enabled)"
echo "================================================================="
