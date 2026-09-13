#!/usr/bin/env bash
# ==============================================================================
# SwapnoPay Backend Zero-Downtime Upgrade Script for Production VPS
# Path: /var/www/swapnopay/swapnopay-backend
# ==============================================================================

set -euo pipefail

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}======================================================${NC}"
echo -e "${BLUE}   ?? Upgrading SwapnoPay Backend on VPS             ${NC}"
echo -e "${BLUE}======================================================${NC}"

BASE_DIR="/var/www/swapnopay"
BACKEND_DIR="$BASE_DIR/swapnopay-backend"

if [ ! -d "$BACKEND_DIR" ]; then
    echo -e "${RED}? Backend directory not found at $BACKEND_DIR${NC}"
    exit 1
fi

cd "$BASE_DIR"

if [ -d ".git" ]; then
    echo -e "${YELLOW}Step 1: Pulling latest changes from Git repository...${NC}"
    git pull || echo -e "${YELLOW}Warning: git pull had conflicts or is not connected. Continuing...${NC}"
fi

cd "$BACKEND_DIR"

echo -e "${YELLOW}Step 2: Installing new dependencies...${NC}"
npm install --production --no-audit --no-fund

echo -e "${YELLOW}Step 3: Reloading PM2 process (Zero Downtime)...${NC}"
if command -v pm2 >/dev/null 2>&1; then
    if pm2 list | grep -q "swapnopay-backend"; then
        pm2 reload swapnopay-backend --update-env
        echo -e "${GREEN}? Successfully reloaded swapnopay-backend via PM2.${NC}"
    elif [ -f ecosystem.config.cjs ]; then
        pm2 start ecosystem.config.cjs --env production
        echo -e "${GREEN}? Started swapnopay-backend with ecosystem.config.cjs.${NC}"
    else
        pm2 start src/index.js --name swapnopay-backend
        echo -e "${GREEN}? Started swapnopay-backend with src/index.js.${NC}"
    fi
    pm2 save
else
    echo -e "${RED}PM2 is not installed globally.${NC}"
    exit 1
fi

echo -e "${YELLOW}Step 4: Checking PM2 status...${NC}"
pm2 status swapnopay-backend

echo ""
echo -e "${GREEN}======================================================${NC}"
echo -e "${GREEN}   ? SwapnoPay Backend Upgrade Completed!          ${NC}"
echo -e "${GREEN}======================================================${NC}"
echo -e "To view live logs: ${YELLOW}pm2 logs swapnopay-backend${NC}"
