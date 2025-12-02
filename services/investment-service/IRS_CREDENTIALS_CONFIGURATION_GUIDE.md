# IRS CREDENTIALS CONFIGURATION GUIDE
## Investment Service - Tax Reporting Compliance

**CRITICAL**: This guide provides step-by-step instructions to configure real IRS credentials for production tax filing.

---

## 🔴 CURRENT STATUS: PRODUCTION BLOCKER

**Issue**: Placeholder IRS credentials detected in 7 files:
1. `IrsFIREXmlService.java` (Lines 58, 61)
2. `Form1099GenerationService.java` (Line 70)
3. `Form1099DIVService.java` (Line 64)
4. `Form1099BService.java` (Line 58)
5. `IRSFireIntegrationService.java` (Line 57)
6. `CryptoRegulatoryService.java` (Line 100)
7. `TaxFilingService.java` (Line 613)

**Placeholder Values**:
```
transmitter-control-code: XXXXX
transmitter-ein: XX-XXXXXXX
payer-tin: XX-XXXXXXX
```

**Impact**: ALL IRS tax form filings will be rejected. Cannot legally operate investment platform.

---

## STEP 1: OBTAIN IRS CREDENTIALS

### A. Apply for Transmitter Control Code (TCC)

1. **Who Can Apply**: Tax professionals, financial institutions, or software vendors filing 250+ information returns
2. **Application Process**:
   - Complete IRS Form 4419 (Application for Filing Information Returns Electronically)
   - Submit to IRS FIRE Help Desk
   - Phone: 866-455-7438 (option 1, then option 1 again)
   - Email: fire@irs.gov
   - Expected processing time: 30-45 days

3. **Requirements**:
   - Valid EIN (Employer Identification Number)
   - Business tax ID verification
   - Designated Responsible Official (DRO)
   - Technical contact information

4. **Testing Required**:
   - Must submit test files before production authorization
   - Test TCC provided first
   - Production TCC issued after successful test submissions

### B. Obtain Employer Identification Number (EIN)

If you don't have an EIN:
1. Apply online: https://www.irs.gov/businesses/small-businesses-self-employed/apply-for-an-employer-identification-number-ein-online
2. Apply by mail: Form SS-4
3. Apply by fax: Form SS-4
4. Format: XX-XXXXXXX (e.g., 12-3456789)

### C. Payer TIN (Tax Identification Number)

- For corporations: Use your EIN
- For individuals: Use SSN (not recommended for business operations)
- **Waqiti Inc should use corporate EIN as Payer TIN**

---

## STEP 2: CONFIGURE AWS SECRETS MANAGER

### A. Create Secrets in AWS Secrets Manager

```bash
# 1. Create IRS Transmitter Control Code secret
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/transmitter-control-code \
    --description "IRS TCC for FIRE electronic filing" \
    --secret-string "YOUR_REAL_TCC_HERE" \
    --region us-east-1

# 2. Create Transmitter EIN secret
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/transmitter-ein \
    --description "Waqiti Inc EIN for IRS filing" \
    --secret-string "YOUR_REAL_EIN_HERE" \
    --region us-east-1

# 3. Create Payer TIN secret
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/payer-tin \
    --description "Waqiti Inc TIN for 1099 forms" \
    --secret-string "YOUR_REAL_TIN_HERE" \
    --region us-east-1

# 4. Create IRS FIRE API credentials (if applicable)
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/fire-api-credentials \
    --description "IRS FIRE system API credentials" \
    --secret-string '{"username":"YOUR_USERNAME","password":"YOUR_PASSWORD"}' \
    --region us-east-1
```

### B. Grant IAM Permissions

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowInvestmentServiceSecretsAccess",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:*:secret:waqiti/investment-service/irs/*"
      ]
    }
  ]
}
```

Attach this policy to the investment-service IAM role or EC2 instance profile.

---

## STEP 3: UPDATE APPLICATION CONFIGURATION

### A. Production Configuration (application-production.yml)

```yaml
waqiti:
  irs:
    # These will be loaded from AWS Secrets Manager at startup
    transmitter-control-code: ${IRS_TCC:#{null}}
    transmitter-ein: ${IRS_EIN:#{null}}
    transmitter-name: "Waqiti Inc"
    contact-name: "Tax Compliance Team"
    contact-phone: "+1-555-TAX-HELP"  # Replace with real number
    contact-email: "tax-compliance@example.com"  # Replace with real email
    software-vendor: "Waqiti Platform"
    software-version: "1.0"

    # FIRE system configuration
    fire:
      enabled: true
      api-url: "https://fire.irs.gov/efile/services"  # Production endpoint
      test-mode: false  # CRITICAL: Must be false in production
      tcc: ${IRS_FIRE_TCC:#{null}}

  tax:
    payer:
      tin: ${PAYER_TIN:#{null}}
      name: "Waqiti Inc"
      address: "123 Financial District, New York, NY 10004"  # Real address

    # Enable strict validation
    validation:
      require-real-credentials: true
      block-placeholder-values: true
```

### B. Staging Configuration (application-staging.yml)

```yaml
waqiti:
  irs:
    # Use TEST TCC for staging
    transmitter-control-code: ${IRS_TEST_TCC}
    transmitter-ein: ${STAGING_EIN}

    fire:
      enabled: true
      api-url: "https://testfire.irs.gov/efile/services"  # Test endpoint
      test-mode: true  # Enable test mode for staging
```

### C. Development Configuration (application-dev.yml)

```yaml
waqiti:
  irs:
    # Development placeholders (will be rejected by IRS, but allow local dev)
    transmitter-control-code: "DEV00"
    transmitter-ein: "99-9999999"

    fire:
      enabled: false  # Disable FIRE submissions in dev
      test-mode: true
```

---

## STEP 4: UPDATE ENVIRONMENT VARIABLES

### A. Production Environment

Add to Kubernetes ConfigMap or ECS Task Definition:

```bash
# AWS Secrets Manager will inject these at runtime
IRS_TCC=arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:waqiti/investment-service/irs/transmitter-control-code
IRS_EIN=arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:waqiti/investment-service/irs/transmitter-ein
PAYER_TIN=arn:aws:secretsmanager:us-east-1:ACCOUNT:secret:waqiti/investment-service/irs/payer-tin
```

### B. Verify Configuration at Startup

The investment-service will now validate credentials on startup:
- ✅ Load from AWS Secrets Manager
- ✅ Validate format (XX-XXXXXXX for EIN/TIN)
- ✅ Check for placeholder values
- 🔴 **FAIL STARTUP** if placeholders detected in production

---

## STEP 5: TEST IRS FILING

### A. Submit Test Files

1. Generate test 1099 forms with real data structure
2. Submit to IRS Test FIRE system
3. Verify acceptance with TIN/EIN matching
4. Await IRS acknowledgment (ACK) file
5. Fix any errors reported by IRS
6. Resubmit until successful

### B. Production Readiness Checklist

- [ ] Real TCC obtained from IRS
- [ ] Real EIN verified with IRS
- [ ] Test submissions successful
- [ ] IRS production authorization received
- [ ] AWS Secrets Manager configured
- [ ] IAM permissions granted
- [ ] Application configuration updated
- [ ] Startup validation passing
- [ ] Integration tests passing
- [ ] Legal review complete
- [ ] Tax team trained on system

---

## STEP 6: ONGOING COMPLIANCE

### A. Annual Filings

- **1099-B**: Due January 31 (paper), March 31 (electronic)
- **1099-DIV**: Due January 31 (paper), March 31 (electronic)
- **1099-INT**: Due January 31 (paper), March 31 (electronic)
- **Corrected Forms**: Submit within 30 days of discovery

### B. Record Retention

- Keep all filed forms for 7 years
- Maintain audit trail in `tax_documents` table
- Store IRS acknowledgment files
- Log all submission attempts

### C. TCC Renewal

- TCC expires after 3 years of inactivity
- Reapply if not used for extended period
- Update Secrets Manager with new TCC

---

## TROUBLESHOOTING

### Error: "TCC not recognized by IRS"
**Solution**: Verify TCC is for production (not test). Contact IRS FIRE Help Desk.

### Error: "EIN does not match IRS records"
**Solution**: Verify EIN format (XX-XXXXXXX). Ensure hyphen is included.

### Error: "Payer TIN mismatch"
**Solution**: Ensure Payer TIN matches your corporate EIN or IRS-provided TIN.

### Error: "Service fails to start - placeholder credentials detected"
**Solution**: Configure real credentials in AWS Secrets Manager. Check IAM permissions.

---

## SECURITY CONSIDERATIONS

### ✅ DO:
- Store credentials in AWS Secrets Manager ONLY
- Use IAM roles for access (no hardcoded AWS keys)
- Rotate credentials annually
- Audit access logs monthly
- Encrypt credentials at rest (KMS)
- Use separate TCC for staging/production

### 🔴 DON'T:
- Never commit credentials to git
- Never log full TCC/EIN/TIN
- Never share TCC with unauthorized personnel
- Never use production TCC in development
- Never bypass startup validation

---

## PENALTIES FOR NON-COMPLIANCE

**IRS Penalties**:
- Failure to file: $50-$290 per form
- Intentional disregard: $570 per form (no maximum)
- Late filing: Reduced penalties if filed within 30 days

**Example**: 1,000 customer accounts = 1,000 Forms 1099
- Penalty range: $50,000 - $290,000 for non-filing
- Criminal charges possible for fraudulent filings

---

## SUPPORT CONTACTS

**IRS FIRE Help Desk**:
- Phone: 866-455-7438
- Email: fire@irs.gov
- Hours: Monday-Friday, 8:30 AM - 6:30 PM EST

**Waqiti Tax Compliance Team**:
- Email: tax-compliance@example.com
- Slack: #tax-compliance
- On-call: PagerDuty escalation

**External Tax Advisor**:
- [Your CPA Firm Name]
- Phone: [CPA Phone]
- Email: [CPA Email]

---

## NEXT STEPS

1. **IMMEDIATE** (This Week):
   - [ ] Apply for IRS TCC (Form 4419)
   - [ ] Verify EIN with IRS
   - [ ] Set up AWS Secrets Manager

2. **SHORT-TERM** (Next 2 Weeks):
   - [ ] Receive test TCC from IRS
   - [ ] Submit test 1099 files
   - [ ] Update application configuration

3. **BEFORE LAUNCH** (Required):
   - [ ] Receive production TCC authorization
   - [ ] Complete end-to-end testing
   - [ ] Legal sign-off
   - [ ] Deploy configuration to production

---

**Document Version**: 1.0
**Last Updated**: October 11, 2025
**Owner**: Platform Engineering Team
**Reviewers**: Tax Compliance Team, Legal Department

**END OF CONFIGURATION GUIDE**
