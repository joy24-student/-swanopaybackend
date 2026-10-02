#!/usr/bin/env bash
# ==============================================================================
# SwapnoPay Ingress Coexistence Fixer
# Configures Nginx on port 8088 and Caddy (SkillBridge) on ports 80/443
# ==============================================================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  🔧 Configuring SwapnoPay + Caddy Coexistence     ${NC}"
echo -e "${CYAN}====================================================${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 1. Update Nginx configuration to listen on port 8088
echo -e "${YELLOW}[1/4] Configuring Nginx to listen on internal port 8088...${NC}"
rm -f /etc/nginx/sites-enabled/default

# Use swapnopay.top.conf template
CONF_SRC="$SCRIPT_DIR/deploy/swapnopay.top.conf"
if [ ! -f "$CONF_SRC" ]; then
    CONF_SRC="/var/www/swapnopay/deploy/swapnopay.top.conf"
fi

if [ -f "$CONF_SRC" ]; then
    cp "$CONF_SRC" /etc/nginx/sites-available/swapnopay.top
    # Replace all listen 80 directives with 8088
    sed -i -E 's/listen ([^;]* )?80([^0-9;]*);/listen \18088\2;/g' /etc/nginx/sites-available/swapnopay.top
    sed -i -E 's/listen \[::\]:80([^0-9;]*);/listen [::]:8088\1;/g' /etc/nginx/sites-available/swapnopay.top
fi

# Also replace in any existing enabled sites
for f in /etc/nginx/sites-available/* /etc/nginx/sites-enabled/*; do
    if [ -f "$f" ]; then
        sed -i -E 's/listen ([^;]* )?80([^0-9;]*);/listen \18088\2;/g' "$f" 2>/dev/null || true
        sed -i -E 's/listen \[::\]:80([^0-9;]*);/listen [::]:8088\1;/g' "$f" 2>/dev/null || true
    fi
done

ln -sf /etc/nginx/sites-available/swapnopay.top /etc/nginx/sites-enabled/swapnopay.top

echo -e "${YELLOW}Testing Nginx syntax...${NC}"
nginx -t

echo -e "${YELLOW}Restarting Nginx service...${NC}"
systemctl restart nginx
echo -e "${GREEN}✓ Nginx is active and listening on port 8088!${NC}"

# 2. Get Docker Gateway IP for Caddy
echo -e "${YELLOW}[2/4] Detecting Caddy Docker network gateway IP...${NC}"
GATEWAY_IP=$(docker exec infra-caddy-1 ip route 2>/dev/null | awk '/default/ {print $3}' || true)
if [ -z "$GATEWAY_IP" ]; then
    GATEWAY_IP="172.17.0.1"
fi
echo -e "${GREEN}✓ Gateway IP: ${GATEWAY_IP}${NC}"

# 3. Clean and update /opt/skillbridge/infra/Caddyfile
echo -e "${YELLOW}[3/4] Updating /opt/skillbridge/infra/Caddyfile...${NC}"
CADDYFILE="/opt/skillbridge/infra/Caddyfile"

# Backup Caddyfile
cp "$CADDYFILE" "${CADDYFILE}.bak_$(date +%s)" 2>/dev/null || true

# Strip any previous SwapnoPay lines
python3 -c "
with open('$CADDYFILE', 'r') as f:
    lines = f.readlines()
clean = []
skip = False
for line in lines:
    if 'SwapnoPay' in line or 'swapnopay.top' in line:
        skip = True
        continue
    if skip and line.strip().startswith('}'):
        skip = False
        continue
    if not skip:
        clean.append(line)
with open('$CADDYFILE', 'w') as f:
    f.writelines(clean)
" 2>/dev/null || sed -i '/swapnopay/Id' "$CADDYFILE"

# Append clean SwapnoPay block
cat << EOF >> "$CADDYFILE"

# ── SwapnoPay Platform (Admin, Web & API) ──────────────────────────────────────
admin.swapnopay.top, pay.swapnopay.top, swapnopay.top, www.swapnopay.top, api.swapnopay.top {
    reverse_proxy ${GATEWAY_IP}:8088
}
EOF

# 4. Reload Caddy
echo -e "${YELLOW}[4/4] Reloading Caddy in infra-caddy-1...${NC}"
docker exec infra-caddy-1 caddy reload --config /etc/caddy/Caddyfile
echo -e "${GREEN}✓ Caddy reloaded successfully!${NC}"

echo -e "${CYAN}====================================================${NC}"
echo -e "${GREEN}  🎉 All set! Caddy is obtaining SSL certificates.    ${NC}"
echo -e "${CYAN}====================================================${NC}"
sleep 3
echo -e "${YELLOW}Testing local response from Nginx on :8088...${NC}"
curl -s -o /dev/null -w "Nginx port 8088 HTTP status: %{http_code}\n" -H "Host: admin.swapnopay.top" http://127.0.0.1:8088/ || true
