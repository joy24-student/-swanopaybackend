# Cloudflare WAF Configuration Guide

## Overview
This guide covers configuring Cloudflare Web Application Firewall (WAF) for maximum security.

---

## CLOUDFLARE SETUP (CLOUD-BASED WAF)

### Step 1: Enable Cloudflare
1. Go to https://dash.cloudflare.com
2. Add your domain
3. Update nameservers at domain registrar
4. Wait for DNS propagation (24-48 hours)

### Step 2: Configure WAF Rules

#### MANAGED RULES
Navigate: Security → WAF

##### Enable OWASP Core Rule Set
- **Path:** Security → WAF
- **Enable:** OWASP ModSecurity Core Rule Set
- **Sensitivity:** High (for e-commerce)
- **Action:** Block

##### Additional Managed Rules
1. **Cloudflare Specials** - Block common exploits
2. **PHP Injection** - Block PHP injection attempts
3. **CVE Protection** - Block known vulnerabilities

#### RATE LIMITING
Navigate: Security → Rate Limiting

```
Rule Name: Aggressive Rate Limiting
Request Rate: 10 requests per 10 seconds
Action: Block
Scope: Entire site
```

#### BOT MANAGEMENT
Navigate: Security → Bots

##### Enable Super Bot Fight Mode
- **Definitely Automated** → Block
- **Likely Automated** → Challenge
- **Verified Bots** → Allow (if needed)
- **Static Resource Protection** → Enable

##### Configure Challenge
```
Sensitivity: Strict
Challenge Page: Show
Block Duration: 24 hours
```

---

## FIREWALL RULES (CLOUDFLARE)

### Rule 1: SQL Injection Detection
```
Name: SQL Injection Protection
Condition: (cf.threat_score >= 50) OR 
           (http.request.uri.query contains "union") OR
           (http.request.uri.query contains "select") OR
           (http.request.body contains "drop table") OR
           (http.request.body contains "delete from")
Action: Block
```

### Rule 2: XSS Protection
```
Name: XSS Attack Protection
Condition: (http.request.uri.query contains "<script") OR
           (http.request.body contains "<script") OR
           (http.request.uri.query contains "javascript:") OR
           (http.request.body contains "on" + match any: 
              ["click", "error", "load", "submit"])
Action: Block
```

### Rule 3: Path Traversal Protection
```
Name: Path Traversal Protection
Condition: (http.request.uri.path contains "..") OR
           (http.request.uri.path contains ".%2e") OR
           (http.request.uri.path contains "%2e%2e") OR
           (http.request.uri.path contains "etc/passwd")
Action: Block
```

### Rule 4: Bot Protection
```
Name: Block Known Malicious Bots
Condition: (cf.verified_bot_category in 
            {"Search Engine Crawler" "Monitoring & Analytics"}) OR
           (http.user_agent contains any: 
            ["sqlmap", "nmap", "nikto", "nessus", "burp"])
Action: Block
```

### Rule 5: Strict Rate Limiting
```
Name: Global Rate Limit
Condition: true
Rate Limit: 100 requests per minute per IP
Action: Challenge (then Block if continues)
```

### Rule 6: Country-Based Access (Optional)
```
Name: Restrict by Country
Condition: ip.geoip.country NOT in {"US" "GB" "CA" "AU"}
Action: Challenge
```

---

## CLOUDFLARE PAGE RULES

Navigate: Rules → Page Rules

### Rule 1: API Endpoints
```
URL Pattern: example.com/api/*
Security Level: High
Cache Level: Bypass
```

### Rule 2: Admin Panel
```
URL Pattern: example.com/admin/*
Security Level: High
Cache Level: Bypass
```

### Rule 3: Login Pages
```
URL Pattern: example.com/login*
Security Level: High
Cache Level: Bypass
```

### Rule 4: User Dashboard
```
URL Pattern: example.com/dashboard/*
Security Level: High
Cache Level: Bypass
```

---

## CLOUDFLARE SETTINGS

### Performance
- **Minify CSS, JS, HTML:** Enable
- **Brotli Compression:** Enable
- **HTTP/2 Push:** Enable
- **HTTP/3:** Enable

### Caching
- **Browser Cache TTL:** 4 hours
- **Cache Level:** Standard
- **Cache on Cookie:** PHPSESSID

### SSL/TLS
- **SSL/TLS Mode:** Full (Strict)
- **Minimum TLS Version:** 1.2
- **HSTS:** Enable (max-age=31536000)

### Network
- **QUIC:** Enable
- **HTTP/2 Edge Coalescing:** Enable
- **Opportunistic Encryption:** Enable

---

## CLOUDFLARE API TOKEN (FOR AUTOMATION)

If using Cloudflare API:

1. Go to Profile → API Tokens
2. Create token with:
   - Zone: DNS Read/Write
   - WAF: Write
   - Firewall Rules: Write
3. Save token securely

---

## EXAMPLE: BLOCK WORDPRESS ATTACKS

```
Name: Block WordPress Attacks
Condition: (http.request.uri.path contains "/wp-admin") OR
           (http.request.uri.path contains "/wp-login") OR
           (http.request.uri.path contains "/wp-includes") OR
           (http.request.uri.path contains "/plugins/") AND
           (NOT cf.verified_bot_category in {"Search Engine Crawler"})
Action: Block
```

---

## EXAMPLE: PROTECT API ENDPOINTS

```
Name: API Rate Limiting
Condition: (http.request.uri.path starts_with "/api/")
Rate Limit: 50 requests per minute per IP
Action: Block
Challenge Disable: 3600 seconds
```

---

## EXAMPLE: PAYMENT PAGE PROTECTION

```
Name: Protect Payment Pages
Condition: (http.request.uri.path contains "/checkout") OR
           (http.request.uri.path contains "/payment")
Security Level: High
Bot Score Threshold: 30
Action: Challenge
```

---

## MONITORING IN CLOUDFLARE

### View WAF Events
1. Go to Security → Events
2. Filter by:
   - Threat Type
   - Date Range
   - Country
   - HTTP Status

### Analyze Bot Traffic
1. Go to Security → Bots
2. View:
   - Bot requests by type
   - Challenge pass rates
   - Blocked bot IPs

### Review Rate Limiting
1. Go to Security → Rate Limiting
2. See:
   - Triggered rules
   - Blocked IP addresses
   - Request patterns

---

## INTEGRATION WITH LOCAL WAF

Use Cloudflare for cloud-level protection and `WAFSecuritySystem.php` for application-level protection (defense in depth).

### Flowchart:
```
Request → Cloudflare WAF (cloud) → Your Server
                                    ↓
                            WAFSecuritySystem.php (local)
                                    ↓
                            PHP Application
```

### Information Passed to Your App:
- `HTTP_CF_RAY` - Cloudflare ray ID
- `HTTP_CF_CONNECTING_IP` - Real client IP
- `HTTP_CF_THREAT_SCORE` - Threat score (0-100)
- `HTTP_CF_BOT_MANAGEMENT_SCORE` - Bot score

---

## TESTING CLOUDFLARE RULES

### Test SQL Injection Block
```bash
curl "https://example.com/?id=1' OR '1'='1"
# Should return 403 Forbidden
```

### Test XSS Block
```bash
curl "https://example.com/?search=<script>alert('xss')</script>"
# Should return 403 Forbidden
```

### Test Rate Limiting
```bash
# Make >100 requests in 1 minute
for i in {1..150}; do curl https://example.com/; done
# Should get 429 Too Many Requests
```

---

## TROUBLESHOOTING

### Legitimate Traffic Blocked
1. Go to Security → Events
2. Find the rule
3. Add exception:
   - By IP address
   - By path pattern
   - By header value

### WAF Too Strict
1. Reduce sensitivity level
2. Review false positives
3. Add specific exceptions
4. Use "Challenge" instead of "Block" initially

### Performance Degradation
1. Check Security Level (should be Standard)
2. Disable unnecessary rules
3. Use Cloudflare Analytics to identify bottlenecks

---

## SECURITY CHECKLIST

✅ **Cloudflare Setup**
- [ ] Domain added to Cloudflare
- [ ] Nameservers updated
- [ ] SSL/TLS enabled (Full Strict)

✅ **WAF Rules**
- [ ] OWASP Core Rule Set enabled
- [ ] SQL injection rules enabled
- [ ] XSS protection enabled
- [ ] Path traversal protection enabled

✅ **Bot Management**
- [ ] Super Bot Fight Mode enabled
- [ ] Challenge mode configured
- [ ] Malicious bots blocked

✅ **Rate Limiting**
- [ ] Global rate limit enabled
- [ ] API rate limit enabled
- [ ] Login attempt rate limit enabled

✅ **Monitoring**
- [ ] Check WAF events daily
- [ ] Review blocked IPs
- [ ] Analyze security logs

---

## ADDITIONAL RESOURCES

- Cloudflare WAF Docs: https://developers.cloudflare.com/waf/
- Cloudflare API: https://api.cloudflare.com/
- OWASP Rules: https://owasp.org/
- CRS Rules: https://github.com/coreruleset/coreruleset

---

## NEXT STEPS

1. Set up Cloudflare account
2. Add domain and update DNS
3. Configure firewall rules (copy examples above)
4. Enable bot management
5. Test protection (see Testing section)
6. Monitor events daily
7. Adjust rules based on findings

**Status:** Ready to implement Cloudflare WAF
