# Waqiti Tax & Investment Services - Production Implementation Guide

## Overview

This document provides comprehensive guidance for the production-ready tax and investment services implementation in the Waqiti platform.

## Table of Contents

1. [Service Architecture](#service-architecture)
2. [Tax Form Generation](#tax-form-generation)
3. [IRS FIRE Integration](#irs-fire-integration)
4. [Security & Compliance](#security--compliance)
5. [Data Retention](#data-retention)
6. [Configuration](#configuration)
7. [Deployment](#deployment)
8. [Monitoring & Alerts](#monitoring--alerts)
9. [Testing](#testing)
10. [Troubleshooting](#troubleshooting)

---

## Service Architecture

### Investment Service Components

#### Tax Reporting Services
- **Form1099GenerationService**: Master orchestrator for all 1099 forms
- **Form1099BService**: Broker transaction reporting (stock sales)
- **Form1099DIVService**: Dividend income reporting
- **Form1099INTService**: Interest income reporting
- **TaxCalculationService**: Tax liability calculations
- **WashSaleDetectionService**: Wash sale rule enforcement (IRC Section 1091)

#### IRS Integration
- **IRSFireIntegrationService**: Electronic filing with IRS FIRE system
- **IrsFIREXmlService**: XML generation per IRS Publication 1220
- **IRSCredentialsService**: Secure credential management

#### Storage & Compliance
- **TaxDocumentStorageService**: 7-year retention with lifecycle management
- **TaxDocumentGenerationService**: PDF generation for taxpayer delivery

### Tax Service Components

#### Filing Services
- **TaxFilingService**: End-to-end tax return preparation and e-filing
- **TaxCalculationService**: Federal and state tax calculations
- **TaxOptimizationEngine**: AI-powered tax savings recommendations

#### IRS Integration
- **IRSIntegrationService**: E-file submission and refund tracking
- **TaxFormPackage**: MeF (Modernized e-File) format generation

---

## Tax Form Generation

### Form 1099-B (Broker Transactions)

**Purpose**: Report proceeds from securities sales

**Reporting Requirements**:
- ALL stock sales must be reported (no minimum threshold)
- Cost basis required for covered securities (acquired after 2011)
- Wash sale loss adjustments must be calculated and reported
- Short-term vs long-term classification required

**IRS Specifications**:
- IRC Section 6045
- IRS Form 1099-B Instructions
- Publication 1220 (FIRE specifications)

**Implementation**:
```java
// Generate Form 1099-B for user
TaxDocument form1099B = form1099BService.generateForm1099B(
    userId,
    investmentAccountId,
    taxYear,
    taxpayerInfo
);
```

**Key Fields**:
- Box 1d: Proceeds from sale
- Box 1e: Cost basis (if covered security)
- Box 1g: Wash sale loss disallowed
- Box 2: Short-term or long-term designation

**Covered Securities**:
- Stocks acquired after 1/1/2011
- Mutual funds acquired after 1/1/2012
- Other specified securities per IRS regulations

### Form 1099-DIV (Dividends)

**Purpose**: Report dividend and capital gain distributions

**Reporting Threshold**: $10 or more in dividends

**IRS Specifications**:
- IRC Section 6042
- IRS Form 1099-DIV Instructions

**Implementation**:
```java
// Generate Form 1099-DIV for user
TaxDocument form1099DIV = form1099DIVService.generateForm1099DIV(
    userId,
    investmentAccountId,
    taxYear,
    taxpayerInfo
);
```

**Key Fields**:
- Box 1a: Total ordinary dividends
- Box 1b: Qualified dividends (preferential 0-20% tax rate)
- Box 2a: Total capital gain distributions
- Box 5: Section 199A dividends (20% pass-through deduction)
- Box 7: Foreign tax paid

**Qualified Dividends**:
- Must be from U.S. corporations or qualified foreign corporations
- Must meet holding period requirements (60 days during 121-day period)
- Taxed at long-term capital gains rates (0%, 15%, or 20%)

### Form 1099-INT (Interest Income)

**Purpose**: Report interest income paid to depositors

**Reporting Threshold**: $10 or more in interest

**IRS Specifications**:
- IRC Section 6049
- IRS Form 1099-INT Instructions

**Implementation**:
```java
// Generate Form 1099-INT for user
TaxDocument form1099INT = form1099INTService.generateForm1099INT(
    userId,
    investmentAccountId,
    taxYear,
    taxpayerInfo
);
```

**Key Fields**:
- Box 1: Interest income
- Box 2: Early withdrawal penalty
- Box 3: Interest on U.S. Savings Bonds and Treasury obligations
- Box 8: Tax-exempt interest (municipal bonds)

---

## IRS FIRE Integration

### FIRE System Overview

**FIRE** = Filing Information Returns Electronically

**Requirements**:
- Transmitter Control Code (TCC) from IRS
- 250+ information returns: MUST file electronically
- Less than 250: May file on paper or electronically
- Test files must be submitted and approved before production

### Obtaining TCC

1. Apply via IRS FIRE Application: https://fire.irs.gov
2. Complete Form 4419 (Application for Filing Information Returns Electronically)
3. Pass IRS software testing requirements
4. Receive TCC (typically 5-10 alphanumeric characters)

### Production Filing Process

**Timeline**:
- January 1 - January 31: Send forms to recipients
- January 1 - March 31: File electronically with IRS via FIRE

**Steps**:

1. **Generate XML**:
```java
String fireXml = irsFIREXmlService.generateFIREXml(
    taxDocuments,
    taxYear,
    false // production mode
);
```

2. **Submit to IRS**:
```java
FireSubmissionResult result = irsFireIntegrationService.submitTaxDocumentsToIRS(taxYear);
```

3. **Process Acknowledgment**:
- IRS sends acknowledgment file within 24-48 hours
- Accept/reject status for each return
- Correction workflow for rejected returns

4. **Store Confirmation Numbers**:
- Maintain IRS confirmation numbers for 7 years
- Required for audit defense

### Credentials Configuration

**AWS Secrets Manager** (Production):
```bash
# Store TCC
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/transmitter-control-code \
    --secret-string "YOUR_TCC_HERE"

# Store Transmitter EIN
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/transmitter-ein \
    --secret-string "XX-XXXXXXX"

# Store Payer TIN
aws secretsmanager create-secret \
    --name waqiti/investment-service/irs/payer-tin \
    --secret-string "XX-XXXXXXX"
```

**Environment Variables** (Development/Staging):
```properties
waqiti.irs.transmitter-control-code=YOUR_TCC
waqiti.irs.transmitter-ein=99-9999999
waqiti.tax.payer.tin=99-9999999
```

### FIRE XML Schema Validation

Before submitting to IRS, validate XML against official IRS schemas:

```bash
# Download IRS schemas
wget https://www.irs.gov/pub/irs-schema/IRS1099B.xsd
wget https://www.irs.gov/pub/irs-schema/IRS1099DIV.xsd
wget https://www.irs.gov/pub/irs-schema/IRS1099INT.xsd

# Validate XML
xmllint --schema IRS1099B.xsd fire-submission.xml --noout
```

---

## Security & Compliance

### Data Protection

**PII Encryption**:
- SSN/TIN: AES-256 encryption via HashiCorp Vault
- At-rest: AWS KMS for S3 encryption
- In-transit: TLS 1.2+ required

**Access Control**:
- Role-based access control (RBAC)
- Principle of least privilege
- Multi-factor authentication required for production access

### IRS Publication 1075 Compliance

**Safeguarding Requirements**:
- Physical security controls
- Logical access controls
- Network security controls
- Incident response procedures
- Annual security awareness training

**Audit Logging**:
```java
log.info("SECURITY AUDIT: Retrieving TIN for tax form generation - Customer: {}",
    customerId);
```

All TIN access is logged for IRS Publication 1075 compliance.

### WCAG 2.1 AA Accessibility

Tax documents must be accessible to users with disabilities:
- Screen reader compatible PDFs
- High contrast mode support
- Keyboard navigation
- Alternative text for all images

---

## Data Retention

### IRS Requirements

**Retention Periods**:
- Tax returns: 7 years minimum
- Employment tax records: 4 years
- Property records: 7 years after disposition
- Indefinite retention for:
  - Fraudulent returns
  - Unreported income
  - Worthless securities claims

### Storage Lifecycle

**Hot Storage** (0-3 years):
- PostgreSQL database
- S3 Standard storage class
- Instant retrieval

**Warm Storage** (3-7 years):
- S3 Standard-IA (Infrequent Access)
- Retrieval: Milliseconds
- Cost: ~50% lower than Standard

**Cold Storage** (7+ years):
- S3 Glacier Deep Archive
- Retrieval: 12-48 hours
- Cost: ~95% lower than Standard

### Automatic Lifecycle Management

**Configuration**:
```java
@Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
public void archiveOldDocuments() {
    taxDocumentStorageService.archiveOldDocuments();
}

@Scheduled(cron = "0 0 3 * * SUN") // Weekly on Sunday
public void deleteExpiredDocuments() {
    taxDocumentStorageService.deleteExpiredDocuments();
}
```

### Legal Hold

Prevent deletion during litigation or audit:

```java
// Apply legal hold
taxDocumentStorageService.applyLegalHold(
    documentId,
    "IRS audit - case #2024-123456"
);

// Remove legal hold when resolved
taxDocumentStorageService.removeLegalHold(documentId);
```

---

## Configuration

### Application Properties

**investment-service/application.properties**:
```properties
# IRS FIRE Configuration
waqiti.irs.transmitter-control-code=${IRS_TCC:XXXXX}
waqiti.irs.transmitter-ein=${IRS_EIN:XX-XXXXXXX}
waqiti.irs.transmitter-name=Waqiti Inc
waqiti.irs.contact-name=Tax Compliance Team
waqiti.irs.contact-email=tax@example.com
waqiti.irs.contact-phone=+1-555-0100

# Payer Information (for 1099 forms)
waqiti.tax.payer.tin=${PAYER_TIN:XX-XXXXXXX}
waqiti.tax.payer.name=Waqiti Financial Services
waqiti.tax.payer.address=123 Waqiti Plaza, New York, NY 10001

# Reporting Thresholds
waqiti.tax.1099-div-threshold=10.00
waqiti.tax.1099-int-threshold=10.00

# Storage Configuration
waqiti.tax.storage.bucket=waqiti-tax-documents
waqiti.tax.storage.archive-bucket=waqiti-tax-documents-archive
waqiti.tax.retention.years=7
waqiti.tax.storage.enabled=true

# Security
vault.enabled=true
vault.address=${VAULT_ADDR:https://vault.example.com}
vault.token=${VAULT_TOKEN}
```

**tax-service/application.properties**:
```properties
# IRS E-File Configuration
irs.api.base-url=${IRS_API_URL:https://api.irs.gov}
irs.efin=${IRS_EFIN:XXXXXX}
irs.etin=${IRS_ETIN:XXXXXXX}
irs.test-mode=false

# Tax Filing
tax.filing.free.income.limit=73000
tax.filing.premium.fee=0
```

### AWS Infrastructure

**S3 Buckets**:
```bash
# Create tax document buckets
aws s3 mb s3://waqiti-tax-documents --region us-east-1
aws s3 mb s3://waqiti-tax-documents-archive --region us-east-1

# Enable versioning
aws s3api put-bucket-versioning \
    --bucket waqiti-tax-documents \
    --versioning-configuration Status=Enabled

# Enable encryption
aws s3api put-bucket-encryption \
    --bucket waqiti-tax-documents \
    --server-side-encryption-configuration '{
        "Rules": [{
            "ApplyServerSideEncryptionByDefault": {
                "SSEAlgorithm": "AES256"
            }
        }]
    }'

# Configure lifecycle policy
aws s3api put-bucket-lifecycle-configuration \
    --bucket waqiti-tax-documents \
    --lifecycle-configuration file://lifecycle-policy.json
```

**lifecycle-policy.json**:
```json
{
  "Rules": [
    {
      "Id": "TransitionToIA",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 1095,
          "StorageClass": "STANDARD_IA"
        }
      ]
    },
    {
      "Id": "TransitionToGlacier",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 2555,
          "StorageClass": "DEEP_ARCHIVE"
        }
      ]
    }
  ]
}
```

---

## Deployment

### Prerequisites

1. **IRS Registration**:
   - Obtain Transmitter Control Code (TCC)
   - Complete IRS software testing
   - Receive production approval

2. **AWS Resources**:
   - S3 buckets created
   - Secrets Manager configured
   - IAM roles assigned
   - KMS keys provisioned

3. **Database Migrations**:
```bash
flyway migrate -configFiles=investment-service/db/migration
flyway migrate -configFiles=tax-service/db/migration
```

### Production Deployment

**Docker Build**:
```bash
# Investment Service
docker build -t waqiti/investment-service:latest ./services/investment-service

# Tax Service
docker build -t waqiti/tax-service:latest ./services/tax-service
```

**Kubernetes Deployment**:
```bash
kubectl apply -f k8s/investment-service/deployment.yaml
kubectl apply -f k8s/tax-service/deployment.yaml
```

**Health Checks**:
```bash
# Investment Service
curl https://api.example.com/investment/actuator/health

# Tax Service
curl https://api.example.com/tax/actuator/health
```

---

## Monitoring & Alerts

### Key Metrics

**Tax Form Generation**:
- Forms generated per day
- Generation success rate
- Average generation time
- Error rate by form type

**IRS FIRE Submission**:
- Submissions per batch
- IRS acceptance rate
- IRS rejection reasons
- Average turnaround time

**Storage**:
- Documents stored per day
- Storage costs by tier
- Archival success rate
- Retrieval latency

### CloudWatch Alarms

```bash
# High error rate
aws cloudwatch put-metric-alarm \
    --alarm-name tax-form-generation-errors \
    --alarm-description "Alert when tax form errors exceed 5%" \
    --metric-name ErrorRate \
    --namespace Waqiti/TaxServices \
    --statistic Average \
    --period 300 \
    --threshold 5 \
    --comparison-operator GreaterThanThreshold

# IRS submission failures
aws cloudwatch put-metric-alarm \
    --alarm-name irs-fire-rejection-rate \
    --alarm-description "Alert when IRS rejection rate exceeds 10%" \
    --metric-name RejectionRate \
    --namespace Waqiti/IRS \
    --statistic Average \
    --period 300 \
    --threshold 10 \
    --comparison-operator GreaterThanThreshold
```

### Logging

**Structured Logging**:
```java
log.info("TAX: Generated Form 1099-B {} with {} transactions - Proceeds: ${}, Cost Basis: ${}, Gain/Loss: ${}",
    documentNumber, transactionCount, totalProceeds, totalCostBasis, totalGainLoss);
```

**Log Aggregation**:
- Centralized logging via CloudWatch Logs
- Log retention: 90 days hot, 7 years cold
- Real-time alerting on ERROR level logs

---

## Testing

### Unit Tests

```bash
# Investment Service
mvn test -pl investment-service

# Tax Service
mvn test -pl tax-service
```

### Integration Tests

```bash
# Test IRS FIRE integration (test mode)
mvn verify -Dtest=IRSFireIntegrationServiceTest

# Test tax form generation
mvn verify -Dtest=Form1099*Test
```

### IRS Test Environment

**Test with IRS FIRE**:
1. Set `waqiti.irs.fire.test-mode=true`
2. Submit test files to IRS FIRE test environment
3. Review IRS acknowledgment files
4. Fix any validation errors
5. Resubmit until all tests pass

**IRS Test Data**:
- Use fictitious SSNs: 900-00-0001 through 900-00-9999
- Test TCC: Use "00000" for testing
- IRS test environment: https://testfire.irs.gov

---

## Troubleshooting

### Common Issues

#### 1. TIN Decryption Failures

**Symptom**: `TIN decryption failed for customer`

**Solution**:
```bash
# Verify Vault connectivity
curl -H "X-Vault-Token: $VAULT_TOKEN" https://vault.example.com/v1/sys/health

# Check encryption key
vault read transit/keys/tax-pii-encryption
```

#### 2. IRS FIRE Rejections

**Symptom**: `IRS FIRE rejection: TIN mismatch`

**Solution**:
- Verify TIN format (XXX-XX-XXXX for SSN, XX-XXXXXXX for EIN)
- Check TIN against user records
- Validate recipient name matches IRS records
- Resubmit corrected returns

#### 3. Wash Sale Detection Issues

**Symptom**: Incorrect wash sale calculations

**Solution**:
```java
// Re-run wash sale detection
washSaleDetectionService.detectWashSalesForTaxYear(userId, taxYear);

// Review calculation logs
log.debug("Wash sale detected: {} shares of {} - Loss disallowed: ${}",
    quantity, symbol, lossDisallowed);
```

#### 4. S3 Storage Failures

**Symptom**: `Failed to store tax document in S3`

**Solution**:
```bash
# Verify S3 permissions
aws s3api get-bucket-policy --bucket waqiti-tax-documents

# Check encryption
aws s3api get-bucket-encryption --bucket waqiti-tax-documents

# Test write access
aws s3 cp test.txt s3://waqiti-tax-documents/test/
```

### Support Contacts

**IRS Technical Support**:
- FIRE Help Desk: 1-866-255-0654
- Hours: Monday-Friday 8:30 AM - 6:00 PM ET
- Email: firesupport@irs.gov

**Waqiti Internal**:
- Tax Compliance Team: tax-compliance@example.com
- Engineering On-Call: +1-555-0199
- Slack: #tax-engineering

---

## Appendix

### IRS Publications

- **Publication 1220**: Specifications for Electronic Filing
- **Publication 1179**: General Rules and Specifications for Substitute Forms 1096, 1098, 1099, 5498, and Certain Other Information Returns
- **Publication 583**: Starting a Business and Keeping Records
- **Publication 1075**: Tax Information Security Guidelines

### Regulatory References

- **IRC Section 6045**: Returns of brokers
- **IRC Section 6042**: Returns regarding payments of dividends
- **IRC Section 6049**: Returns regarding payments of interest
- **IRC Section 1091**: Loss from wash sales
- **IRC Section 199A**: Qualified business income deduction

### Third-Party Integrations

- **Alpaca**: Brokerage API for trading
- **Plaid**: Bank account connections
- **TaxBit**: Crypto tax calculations
- **IRS e-Services**: Direct IRS integration

---

**Version**: 1.0
**Last Updated**: 2025-10-18
**Maintained By**: Waqiti Tax Engineering Team
