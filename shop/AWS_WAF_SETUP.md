# AWS WAF Configuration Guide

## Overview
AWS WAF provides cloud-based web application firewall protection with machine learning and threat intelligence.

---

## SETUP STEPS

### Step 1: Enable AWS WAF
1. Go to AWS Console
2. Navigate to WAF & Shield
3. Create web ACL
4. Choose CloudFront distribution
5. Configure rules

### Step 2: Create Managed Rules

#### OWASP Top 10 Rules
```
Enable:
- SQLiProtectionIP (SQL Injection)
- XSSProtectionIP (Cross-Site Scripting)
- CommonRuleSet (Common attacks)
- KnownBadInputsRuleSet (Known malicious patterns)
```

#### Bot Control
```
Enable:
- AWSManagedRulesCommonRuleSet
- AWSManagedRulesBotControlRuleSet
- AWSManagedRulesAmazonIpReputationList
```

### Step 3: SQL Injection Rules

```
Rule Name: SQLInjectionProtection
Managed Rules: AWSManagedRulesSQLiRuleSet
Action: Block
Logging: Enable
```

### Step 4: XSS Protection

```
Rule Name: XSSProtection
Managed Rules: AWSManagedRulesKnownBadInputsRuleSet
Action: Block
Logging: Enable
```

### Step 5: Rate Limiting

```
Rule Name: RateLimitRule
Type: Rate-based
Rate Limit: 2000 requests per 5 minutes
Action: Block
Duration: 5 minutes
```

### Step 6: Bot Management

```
Rule Name: BotControl
Managed Rules: AWSManagedRulesBotControlRuleSet
Action for verified bots: Allow
Action for suspicious bots: Block
Logging: Enable
```

---

## CUSTOM RULES

### Rule 1: Block SQL Keywords
```
Name: BlockSQLKeywords
Type: String match
String to match: SELECT, DROP, INSERT, DELETE, UNION
Action: Block
```

### Rule 2: Block XSS Patterns
```
Name: BlockXSSPatterns
Type: String match
String to match: <script, javascript:, onerror=
Action: Block
```

### Rule 3: Geo-blocking (Optional)
```
Name: CountryRestriction
Type: Geo match
Countries to block: [List as needed]
Action: Block
```

### Rule 4: IP Reputation
```
Name: IPReputation
Type: IP set
Use AWS IP reputation list
Action: Block
```

---

## LOGGING & MONITORING

### Enable Logging
1. Select Web ACL
2. Edit logging configuration
3. Choose S3 bucket or CloudWatch Logs
4. Save

### CloudWatch Dashboard
```
Metrics to monitor:
- AllowedRequests
- BlockedRequests
- CountedRequests
- Request rate
- Top rule actions
```

### Review Logs
```bash
# Query CloudWatch Logs
aws logs describe-log-groups --log-group-name-prefix "aws-waf"
aws logs get-log-events --log-group-name "aws-waf" --log-stream-name "..."
```

---

## TESTING

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
# Send 2500 requests in 5 minutes
for i in {1..2500}; do curl https://example.com/ & done
# Should get 403 Forbidden after limit
```

---

## COST ESTIMATION

```
Web ACL: $5.00/month
Rules: $1.00 per rule per month
Requests: $0.60 per million requests
Logging: CloudWatch charges apply

Example: 10 rules + 100M requests = ~$65/month
```

---

## INTEGRATION WITH LOCAL WAF

Use AWS WAF for cloud-level protection and local WAF for application-level defense.

```
Request → AWS WAF (cloud) → ALB/CloudFront
                              ↓
                         Your Application
                              ↓
                         Local WAF checks
```

---

## TROUBLESHOOTING

### Traffic Not Reaching WAF
- Verify Web ACL is associated
- Check CloudFront distribution
- Verify ALB target group

### False Positives
- Review CloudWatch logs
- Identify rule causing block
- Adjust rule sensitivity or add exception

### Performance Issues
- Check request rate
- Review rule complexity
- Optimize rule order (most common first)

---

## SECURITY CHECKLIST

✅ WAF Setup
- [ ] Web ACL created
- [ ] Rules enabled
- [ ] Logging configured
- [ ] CloudWatch dashboard set up

✅ SQL Injection
- [ ] SQLi rule enabled
- [ ] Tested with payload
- [ ] Logging verified

✅ XSS Protection
- [ ] XSS rule enabled
- [ ] Tested with payload
- [ ] Logging verified

✅ Bot Management
- [ ] Bot control enabled
- [ ] Rate limiting set
- [ ] Testing completed

✅ Monitoring
- [ ] CloudWatch configured
- [ ] Logs reviewed daily
- [ ] Alerts set up
- [ ] Dashboard created

---

## NEXT STEPS

1. Create AWS WAF Web ACL
2. Enable managed rules
3. Configure custom rules
4. Set up logging & monitoring
5. Test protection
6. Adjust rules as needed
7. Monitor metrics daily

**AWS WAF + Local WAF = Enterprise-Grade Protection**
