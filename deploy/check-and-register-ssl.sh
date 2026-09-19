#!/usr/bin/env bash
# ==============================================================================
# SwapnoPay SSL Certificate Inspector & Auto-Registrar
# Checks existing SSL certificates for all SwapnoPay subdomains,
# verifies DNS A-record propagation, and registers HTTPS for any missing domains.
# ==============================================================================

set -eo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

BASE_DOMAIN="${1:-swapnopay.top}"
ADMIN_EMAIL="${2:-admin@$BASE_DOMAIN}"

SUBDOMAINS=(
    "$BASE_DOMAIN"
    "www.$BASE_DOMAIN"
    "pay.$BASE_DOMAIN"
    "api.$BASE_DOMAIN"
    "admin.$BASE_DOMAIN"
    "shop.$BASE_DOMAIN"
    "shops.$BASE_DOMAIN"
)

echo -e "${CYAN}${BOLD}====================================================================${NC}"
echo -e "${CYAN}${BOLD}     🔒 SwapnoPay SSL Inspector & Auto-Registrar (${BASE_DOMAIN})   ${NC}"
echo -e "${CYAN}${BOLD}====================================================================${NC}"
echo -e "${BLUE}Email for SSL notices:${NC} $ADMIN_EMAIL"
echo ""

# 1. Verify root privileges
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ Please run as root or with sudo: sudo bash $0${NC}"
    exit 1
fi

# 2. Check certbot and nginx installation
if ! command -v certbot >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Certbot not found. Installing certbot and python3-certbot-nginx...${NC}"
    apt-get update -y
    apt-get install -y certbot python3-certbot-nginx
fi

# Get VPS Public IP
VPS_IP=$(curl -s -4 https://ifconfig.me 2>/dev/null || curl -s -4 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
echo -e "${BLUE}VPS Public IPv4:${NC} ${BOLD}$VPS_IP${NC}"
echo ""

# 3. Check existing certbot certificates
echo -e "${YELLOW}${BOLD}[1/4] Checking existing Certbot certificates on this system...${NC}"
if certbot certificates 2>/dev/null | grep -q "Certificate Name"; then
    certbot certificates
else
    echo -e "   ${YELLOW}No Certbot certificates found on this server yet.${NC}"
fi
echo ""

# 4. Detailed per-domain audit
echo -e "${YELLOW}${BOLD}[2/4] Auditing each domain (DNS & HTTPS Status)...${NC}"
printf "%-26s %-18s %-20s %-12s\n" "DOMAIN" "DNS RESOLUTION" "HTTPS / SSL" "ACTION"
printf "%-26s %-18s %-20s %-12s\n" "------" "--------------" "-----------" "------"

MISSING_SSL_DOMAINS=()
DNS_FAIL_DOMAINS=()

for domain in "${SUBDOMAINS[@]}"; do
    # Check DNS resolution
    RESOLVED_IP=$(getent ahosts "$domain" 2>/dev/null | awk '{print $1}' | head -n 1)
    
    if [ -z "$RESOLVED_IP" ]; then
        RESOLVED_IP="NXDOMAIN"
        DNS_STATUS="${RED}NOT RESOLVED${NC}"
        DNS_FAIL_DOMAINS+=("$domain")
    else
        DNS_STATUS="${GREEN}$RESOLVED_IP${NC}"
    fi

    # Check if SSL cert exists in /etc/letsencrypt/live/ or through port 443
    HAS_SSL=false
    SSL_EXPIRY=""

    # Check certbot files directly
    for cert_dir in /etc/letsencrypt/live/*; do
        if [ -d "$cert_dir" ] && [ -f "$cert_dir/cert.pem" ]; then
            if openssl x509 -in "$cert_dir/cert.pem" -noout -text 2>/dev/null | grep -q "DNS:$domain"; then
                HAS_SSL=true
                EXP_DATE=$(openssl x509 -in "$cert_dir/cert.pem" -noout -enddate 2>/dev/null | cut -d= -f2)
                SSL_EXPIRY="Valid (expires $EXP_DATE)"
                break
            fi
        fi
    done

    # If not found in files, test live HTTPS port 443
    if [ "$HAS_SSL" = false ] && [ "$RESOLVED_IP" != "NXDOMAIN" ]; then
        if echo | openssl s_client -connect "${domain}:443" -servername "$domain" 2>/dev/null | openssl x509 -noout 2>/dev/null; then
            HAS_SSL=true
            SSL_EXPIRY="Active on :443"
        fi
    fi

    if [ "$HAS_SSL" = true ]; then
        ACTION="${GREEN}✓ OK${NC}"
        printf "%-26s %-28b %-30s %-12b\n" "$domain" "$DNS_STATUS" "Active (SSL Installed)" "$ACTION"
    else
        if [ "$RESOLVED_IP" == "NXDOMAIN" ]; then
            ACTION="${RED}DNS NEEDED${NC}"
            printf "%-26s %-28b %-30s %-12b\n" "$domain" "$DNS_STATUS" "Missing (No SSL)" "$ACTION"
        else
            ACTION="${YELLOW}NEED REGISTER${NC}"
            printf "%-26s %-28b %-30s %-12b\n" "$domain" "$DNS_STATUS" "Missing (No SSL)" "$ACTION"
            MISSING_SSL_DOMAINS+=("$domain")
        fi
    fi
done

echo ""

# 5. Handle DNS Warnings
if [ ${#DNS_FAIL_DOMAINS[@]} -gt 0 ]; then
    echo -e "${YELLOW}${BOLD}⚠️  Warning: The following domains do not resolve in DNS yet:${NC}"
    for d in "${DNS_FAIL_DOMAINS[@]}"; do
        echo -e "   • ${RED}$d${NC} -> Please add an ${BOLD}A record${NC} in Cloudflare pointing to ${BOLD}$VPS_IP${NC}"
    done
    echo -e "   ${YELLOW}(Certbot cannot issue SSL certificates for domains without active DNS)${NC}"
    echo ""
fi

# 6. Register SSL for Missing Domains
if [ ${#MISSING_SSL_DOMAINS[@]} -eq 0 ]; then
    echo -e "${GREEN}${BOLD}🎉 All reachable subdomains already have valid SSL certificates!${NC}"
    echo -e "No new registrations needed."
else
    echo -e "${CYAN}${BOLD}[3/4] Registering SSL for missing subdomains:${NC}"
    for d in "${MISSING_SSL_DOMAINS[@]}"; do
        echo -e "   • ${GREEN}$d${NC}"
    done
    echo ""

    # Build certbot flags
    CERTBOT_ARGS=()
    for d in "${MISSING_SSL_DOMAINS[@]}"; do
        CERTBOT_ARGS+=("-d" "$d")
    done

    echo -e "${YELLOW}Executing Certbot with Nginx plugin...${NC}"
    
    # Try all together first with expand
    if certbot --nginx "${CERTBOT_ARGS[@]}" --expand --non-interactive --agree-tos -m "$ADMIN_EMAIL" --redirect 2>/dev/null; then
        echo -e "${GREEN}✓ Successfully registered SSL for: ${MISSING_SSL_DOMAINS[*]}${NC}"
    else
        echo -e "${YELLOW}Batch registration encountered a challenge error. Registering domains individually...${NC}"
        for d in "${MISSING_SSL_DOMAINS[@]}"; do
            echo -e "${BLUE}Requesting certificate for: $d...${NC}"
            if certbot --nginx -d "$d" --non-interactive --agree-tos -m "$ADMIN_EMAIL" --redirect; then
                echo -e "${GREEN}✓ Registered SSL for $d${NC}"
            else
                echo -e "${RED}❌ Failed to register SSL for $d. Check Nginx server_name or DNS.${NC}"
            fi
        done
    fi
fi

# 7. Test Nginx & Auto-Renewal
echo ""
echo -e "${YELLOW}${BOLD}[4/4] Testing Nginx configuration and automatic renewal...${NC}"
nginx -t && systemctl reload nginx
echo -e "${GREEN}✓ Nginx configuration tested and reloaded.${NC}"

# Dry run renewal check
certbot renew --dry-run 2>/dev/null && echo -e "${GREEN}✓ Certbot automatic renewal test passed (cron/systemd timer is active).${NC}" || true

echo ""
echo -e "${CYAN}${BOLD}====================================================================${NC}"
echo -e "${GREEN}${BOLD}                         SUMMARY COMPLETE                           ${NC}"
echo -e "${CYAN}${BOLD}====================================================================${NC}"
echo -e "To view all certificates anytime, run:  ${BOLD}sudo certbot certificates${NC}"
echo -e "To renew certificates anytime, run:      ${BOLD}sudo certbot renew${NC}"
echo ""
