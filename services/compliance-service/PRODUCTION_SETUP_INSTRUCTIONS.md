# Compliance Service - Production Setup Instructions

## CRITICAL: Institution Information Configuration

Before deploying to production, you **MUST** configure the following environment variables with your actual financial institution information. Failure to do so will cause:
- **FinCEN filing rejections** (SARs, CTRs, Form 8300)
- **Regulatory violations** ($25K-$100K fines per violation)
- **Production deployment failures** (service will refuse to start if EIN is not configured)

---

## Required Environment Variables

### 1. Institution Identity

```bash
# Legal name of your financial institution
export INSTITUTION_NAME="Your Financial Institution Name Inc."

# CRITICAL: Your actual EIN (Employer Identification Number)
# Format: XX-XXXXXXX (e.g., 12-3456789)
# NEVER use placeholder values like XX-XXXXXXX in production
export INSTITUTION_EIN="12-3456789"
```

**⚠️ WARNING:** The service will **REFUSE TO FILE SARS** if the EIN is not properly configured. It will throw an `IllegalStateException` with the message:
```
CRITICAL: Institution EIN is not properly configured. Current value: XX-XXXXXXX.
SAR filings will be rejected by FinCEN. Please configure a valid EIN in production environment.
```

### 2. Physical Address

```bash
# Physical address of your financial institution
export INSTITUTION_ADDRESS_STREET="123 Financial Plaza"
export INSTITUTION_ADDRESS_CITY="New York"
export INSTITUTION_ADDRESS_STATE="NY"
export INSTITUTION_ADDRESS_ZIP="10001"
export INSTITUTION_ADDRESS_COUNTRY="United States"
```

### 3. Compliance Officer Contact Information

```bash
# Contact information for the designated compliance officer
export COMPLIANCE_OFFICER_NAME="Jane Doe"
export COMPLIANCE_OFFICER_TITLE="Chief Compliance Officer"
export COMPLIANCE_OFFICER_PHONE="+1-800-555-0100"
export COMPLIANCE_OFFICER_EMAIL="compliance@yourfinancialinstitution.com"
```

### 4. Regulatory Identifiers

```bash
# Filing institution code assigned by FinCEN
# Contact FinCEN to obtain your code if you don't have one
export FILING_INSTITUTION_CODE="YOURINST001"

# RSSD ID (Research, Statistics, Supervision, Discount & Credit ID)
# Assigned by Federal Reserve - Required for banks, optional for MSBs
export RSSD_ID="1234567"  # Leave empty if not applicable

# Primary regulatory authority
export PRIMARY_REGULATOR="FinCEN"

# Charter type
# Options: Money Services Business, National Bank, State Bank, Credit Union, etc.
export CHARTER_TYPE="Money Services Business"
```

---

## Kubernetes Deployment Configuration

### Option 1: Using Kubernetes Secrets (Recommended)

1. Create a Kubernetes secret with institution information:

```bash
kubectl create secret generic compliance-institution-config \
  --from-literal=INSTITUTION_NAME="Your Financial Institution Inc." \
  --from-literal=INSTITUTION_EIN="12-3456789" \
  --from-literal=INSTITUTION_ADDRESS_STREET="123 Financial Plaza" \
  --from-literal=INSTITUTION_ADDRESS_CITY="New York" \
  --from-literal=INSTITUTION_ADDRESS_STATE="NY" \
  --from-literal=INSTITUTION_ADDRESS_ZIP="10001" \
  --from-literal=COMPLIANCE_OFFICER_NAME="Jane Doe" \
  --from-literal=COMPLIANCE_OFFICER_TITLE="Chief Compliance Officer" \
  --from-literal=COMPLIANCE_OFFICER_PHONE="+1-800-555-0100" \
  --from-literal=COMPLIANCE_OFFICER_EMAIL="compliance@yourbank.com" \
  --from-literal=FILING_INSTITUTION_CODE="YOURINST001" \
  --from-literal=RSSD_ID="1234567" \
  --from-literal=PRIMARY_REGULATOR="FinCEN" \
  --from-literal=CHARTER_TYPE="Money Services Business" \
  --namespace=waqiti-production
```

2. Reference the secret in your deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: compliance-service
spec:
  template:
    spec:
      containers:
      - name: compliance-service
        image: waqiti/compliance-service:latest
        envFrom:
        - secretRef:
            name: compliance-institution-config
```

### Option 2: Using HashiCorp Vault (Enterprise Recommended)

1. Store institution information in Vault:

```bash
vault kv put secret/waqiti/production/compliance/institution \
  name="Your Financial Institution Inc." \
  ein="12-3456789" \
  address_street="123 Financial Plaza" \
  address_city="New York" \
  address_state="NY" \
  address_zip="10001" \
  officer_name="Jane Doe" \
  officer_title="Chief Compliance Officer" \
  officer_phone="+1-800-555-0100" \
  officer_email="compliance@yourbank.com" \
  filing_code="YOURINST001" \
  rssd_id="1234567" \
  primary_regulator="FinCEN" \
  charter_type="Money Services Business"
```

2. Configure Vault agent injection in Kubernetes deployment

---

## Validation

### Pre-Deployment Validation

Before deploying to production, validate your configuration:

1. Check that EIN is in correct format (XX-XXXXXXX):
```bash
echo $INSTITUTION_EIN | grep -E '^\d{2}-\d{7}$'
```

2. Check that ZIP code is valid (5 or 9 digits):
```bash
echo $INSTITUTION_ADDRESS_ZIP | grep -E '^\d{5}(-\d{4})?$'
```

3. Check that state is 2-letter code:
```bash
echo $INSTITUTION_ADDRESS_STATE | grep -E '^[A-Z]{2}$'
```

4. Check that phone is valid US format:
```bash
echo $COMPLIANCE_OFFICER_PHONE | grep -E '^\+?1?-?\(?\d{3}\)?-?\d{3}-?\d{4}$'
```

5. Check that email is valid:
```bash
echo $COMPLIANCE_OFFICER_EMAIL | grep -E '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
```

### Post-Deployment Validation

After deploying, the service will log a message when it successfully loads institution configuration:

```
INFO  c.w.compliance.kafka.SARFilingQueueConsumer - SAR document enriched with institution info - Name: Your Financial Institution Inc., EIN: XX-XXX6789, Filing Code: YOURINST001
```

Notice that the EIN is masked in logs (only last 4 digits shown) for security.

---

## Troubleshooting

### Error: "Institution EIN must be configured for regulatory filings"

**Cause:** The `INSTITUTION_EIN` environment variable is not set or is empty.

**Solution:** Set the environment variable with your actual EIN:
```bash
export INSTITUTION_EIN="12-3456789"
```

### Error: "Institution EIN must be in format XX-XXXXXXX"

**Cause:** The EIN is not in the correct format.

**Valid formats:**
- `12-3456789` ✅
- `123456789` ❌ (missing hyphen)
- `12-34567` ❌ (too short)

**Solution:** Ensure EIN is exactly 2 digits, hyphen, 7 digits.

### Error: "CRITICAL: Institution EIN is not properly configured. Current value: XX-XXXXXXX"

**Cause:** The EIN is still set to the placeholder value `XX-XXXXXXX`.

**Solution:** Replace with your actual EIN. **NEVER deploy to production with placeholder values.**

### Service Fails to Start with "Binding validation errors"

**Cause:** One or more required configuration fields failed validation.

**Solution:** Check the error message to see which field failed validation, then correct the format. Common issues:
- State not 2-letter code (must be NY, not New York)
- ZIP code not 5 or 9 digits
- Phone not in US format
- Email not valid format

---

## Security Best Practices

1. **Never commit EIN or other sensitive information to version control**
   - Use environment variables or secrets management
   - Add `.env` files to `.gitignore`

2. **Rotate compliance officer credentials regularly**
   - Update email/phone if officer changes
   - Maintain audit trail of changes

3. **Use read-only access for most services**
   - Only compliance-service needs write access to institution config
   - Other services should use read-only secrets

4. **Monitor for unauthorized changes**
   - Set up alerts for changes to institution configuration
   - Log all access to institution information

5. **Encrypt secrets at rest**
   - Use Kubernetes secrets encryption
   - Use Vault encryption
   - Enable AWS KMS encryption for secrets

---

## Compliance Checklist

Before going to production, verify:

- [ ] Institution EIN is your actual EIN (not placeholder)
- [ ] Institution name matches legal business name
- [ ] Address is physical location (not P.O. Box)
- [ ] Compliance officer information is current
- [ ] Filing institution code obtained from FinCEN
- [ ] RSSD ID obtained (if applicable)
- [ ] Primary regulator identified correctly
- [ ] Charter type matches your license
- [ ] All environment variables set in production environment
- [ ] Secrets encrypted at rest
- [ ] Access to secrets restricted to compliance team
- [ ] Monitoring and alerting configured
- [ ] Backup and disaster recovery plan in place

---

## Support

For questions about:
- **FinCEN filing codes:** Contact FinCEN at 1-800-949-2732
- **RSSD IDs:** Contact Federal Reserve
- **Institution configuration:** Contact your compliance officer
- **Technical setup:** Contact DevOps team

---

**Last Updated:** October 9, 2025
**Document Owner:** Compliance Team
**Classification:** Internal - Restricted
