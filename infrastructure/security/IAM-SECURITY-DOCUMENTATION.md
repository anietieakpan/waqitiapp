# IAM Security Audit & Least Privilege Implementation Documentation

## Overview
This document provides comprehensive documentation for the Waqiti Financial Platform's IAM security implementation, including least privilege policies, RBAC configurations, and security audit procedures.

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [IAM Least Privilege Policies](#iam-least-privilege-policies)
3. [Kubernetes RBAC Configuration](#kubernetes-rbac-configuration)
4. [Security Audit Script](#security-audit-script)
5. [Implementation Guide](#implementation-guide)
6. [Compliance Mapping](#compliance-mapping)
7. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### Security Layers
```
┌─────────────────────────────────────────────────────────────┐
│                     AWS Account Boundary                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────┐     │
│  │              IAM Policies (AWS Level)               │     │
│  │  • Service-specific roles                          │     │
│  │  • Condition-based access                          │     │
│  │  • Resource-specific ARNs                          │     │
│  └────────────────────────────────────────────────────┘     │
│                           ↓                                  │
│  ┌────────────────────────────────────────────────────┐     │
│  │         IRSA (IAM Roles for Service Accounts)      │     │
│  │  • Kubernetes-AWS integration                      │     │
│  │  • Pod-level AWS permissions                       │     │
│  │  • OIDC federation                                 │     │
│  └────────────────────────────────────────────────────┘     │
│                           ↓                                  │
│  ┌────────────────────────────────────────────────────┐     │
│  │          Kubernetes RBAC (Cluster Level)           │     │
│  │  • Service accounts                                │     │
│  │  • Roles and RoleBindings                          │     │
│  │  • Network policies                                │     │
│  └────────────────────────────────────────────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Service Permission Matrix

| Service | AWS Resources | Kubernetes Resources | Risk Level |
|---------|--------------|---------------------|------------|
| Payment Service | KMS (encrypt/decrypt), S3 (receipts), SES (notifications) | ConfigMaps (payment-config), Secrets (stripe, paypal) | CRITICAL |
| Wallet Service | DynamoDB (wallets), KMS (encryption) | ConfigMaps (wallet-config), Secrets (encryption-keys) | HIGH |
| User Service | Cognito (users), S3 (KYC docs) | ConfigMaps (auth-config), Secrets (jwt-keys) | HIGH |
| Fraud Detection | SageMaker (ML models), DynamoDB (patterns) | ConfigMaps (ml-config), Secrets (api-keys) | CRITICAL |
| Compliance Service | S3 (audit logs), CloudTrail, Config | All pods/logs (read-only) | CRITICAL |
| Monitoring Service | CloudWatch, X-Ray | All pods/services (read-only) | MEDIUM |

---

## IAM Least Privilege Policies

### Policy Structure
Each service follows a strict least-privilege model with the following components:

#### 1. Payment Service Policy
```hcl
# Location: infrastructure/terraform/modules/iam/least-privilege-policies.tf

Key Features:
- KMS encryption limited to payment-specific keys
- S3 access restricted to payment documents with object tagging
- SES limited to specific email templates
- Condition-based access with IP restrictions
- Read-only SSM parameter access
```

**Critical Security Controls:**
- ✅ No wildcard (*) resources
- ✅ Condition-based access (IP, tags, encryption context)
- ✅ Time-based session limits (1 hour max)
- ✅ MFA required for sensitive operations

#### 2. Wallet Service Policy
```hcl
Key Features:
- DynamoDB access with leading key restrictions
- Attribute-level access control
- KMS encryption with service-specific context
- CloudWatch metrics limited to Waqiti/Wallets namespace
```

#### 3. User Service Policy
```hcl
Key Features:
- Cognito operations limited to specific user pool
- S3 access with user-specific object tagging
- SES limited to user communication templates
```

### IRSA Configuration
IAM Roles for Service Accounts (IRSA) provides pod-level AWS permissions:

```yaml
# Service Account with IRSA
apiVersion: v1
kind: ServiceAccount
metadata:
  name: payment-service
  namespace: waqiti
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT_ID:role/waqiti-payment-service-irsa-production
```

---

## Kubernetes RBAC Configuration

### RBAC Hierarchy
```
ClusterRole (monitoring only)
    ↓
Role (namespace-specific)
    ↓
RoleBinding (service account binding)
    ↓
ServiceAccount (pod identity)
    ↓
Pod (runtime permissions)
```

### Service Account Permissions

#### Payment Service RBAC
```yaml
# Location: infrastructure/kubernetes/security/rbac-least-privilege.yaml

Allowed Operations:
- ConfigMaps: GET, LIST (payment-config only)
- Secrets: GET (payment provider credentials only)
- Services: GET, LIST (internal services only)
- Pods/logs: GET (own pods only)
```

### Network Policies
Micro-segmentation through NetworkPolicies:

```yaml
Payment Service Network Policy:
- Ingress: API Gateway, Wallet Service, Fraud Detection
- Egress: Wallet Service, Compliance Service, DNS, HTTPS (443)
```

---

## Security Audit Script

### Script Overview
**Location:** `infrastructure/security/iam-security-audit.sh`

### Functionality

#### 1. IAM Role Auditing
```bash
Checks:
- Session duration limits (max 1 hour)
- Assume role policy wildcards
- External ID requirements
- Attached policy count
- Overly permissive AWS managed policies
- Inline policy analysis
```

#### 2. IAM User Auditing
```bash
Checks:
- Access key count and age
- Console access without MFA
- Administrative permissions on users
- Password policy compliance
```

#### 3. IAM Policy Auditing
```bash
Checks:
- Policy size limits (6KB max)
- Wildcard actions and resources
- Sensitive action usage
- Condition-less broad permissions
```

#### 4. Kubernetes RBAC Auditing
```bash
Checks:
- Overly permissive cluster roles
- Service account token automounting
- Unused service accounts
- Wildcard verbs and resources
```

### Running the Audit

#### Prerequisites
```bash
# AWS CLI Configuration
aws configure
aws sts get-caller-identity  # Verify credentials

# Kubectl Configuration
kubectl config current-context
kubectl cluster-info  # Verify cluster access

# Required Tools
- jq (JSON processor)
- AWS CLI v2
- kubectl v1.24+
```

#### Execution
```bash
# Basic audit (production environment)
./iam-security-audit.sh production

# Audit with specific environment
./iam-security-audit.sh staging

# Audit with automated remediation suggestions
./iam-security-audit.sh production --fix-issues
```

#### Output Format
```json
{
  "audit_metadata": {
    "environment": "production",
    "timestamp": "2024-12-01T10:30:00Z",
    "account_id": "123456789012",
    "region": "us-west-2"
  },
  "executive_summary": {
    "total_findings": 15,
    "critical_findings": 2,
    "high_findings": 5,
    "medium_findings": 6,
    "low_findings": 2,
    "risk_score": 42,
    "compliance_status": "PARTIAL_COMPLIANCE"
  },
  "findings": [...],
  "compliance_checks": {
    "pci_dss": {...},
    "sox": {...},
    "nist": {...}
  }
}
```

### Finding Severity Levels

| Severity | Description | Example | Action Required |
|----------|-------------|---------|-----------------|
| CRITICAL | Immediate security risk | AdministratorAccess on production role | Immediate remediation |
| HIGH | Significant security concern | Missing MFA on console user | Remediate within 24 hours |
| MEDIUM | Security improvement needed | Old access keys (>90 days) | Remediate within 1 week |
| LOW | Minor security enhancement | Broad read access | Review and plan remediation |
| INFO | Informational finding | Service using external ID | No action required |

---

## Implementation Guide

### Step 1: Deploy IAM Policies
```bash
# Navigate to Terraform module
cd infrastructure/terraform/modules/iam

# Initialize Terraform
terraform init

# Plan deployment
terraform plan -var="environment=production" \
              -var="cluster_name=waqiti-production" \
              -var="region=us-west-2" \
              -var="account_id=123456789012"

# Apply policies
terraform apply -auto-approve
```

### Step 2: Configure IRSA
```bash
# Create OIDC provider for EKS
eksctl utils associate-iam-oidc-provider \
    --cluster waqiti-production \
    --approve

# Create IRSA for each service
eksctl create iamserviceaccount \
    --cluster=waqiti-production \
    --namespace=waqiti \
    --name=payment-service \
    --attach-policy-arn=arn:aws:iam::ACCOUNT_ID:policy/waqiti-payment-service-production
```

### Step 3: Deploy Kubernetes RBAC
```bash
# Apply RBAC configuration
kubectl apply -f infrastructure/kubernetes/security/rbac-least-privilege.yaml

# Verify service accounts
kubectl get serviceaccounts -n waqiti

# Verify roles and bindings
kubectl get roles,rolebindings -n waqiti
```

### Step 4: Validate Configuration
```bash
# Test service permissions
kubectl auth can-i get secrets --as=system:serviceaccount:waqiti:payment-service -n waqiti

# Run security audit
./infrastructure/security/iam-security-audit.sh production

# Review audit report
cat reports/iam-security-audit-production-*.json | jq '.executive_summary'
```

---

## Compliance Mapping

### PCI DSS Requirements

| Requirement | Implementation | Validation |
|-------------|---------------|------------|
| 7.1 Limit access to system components | Least privilege IAM policies | IAM audit script |
| 7.2 Establish access control systems | RBAC with service accounts | kubectl auth can-i |
| 8.3 Secure all access with MFA | MFA enforcement on console users | AWS IAM MFA report |
| 8.5 Do not use group/shared IDs | Individual service accounts | Service account audit |

### SOX Compliance

| Control | Implementation | Evidence |
|---------|---------------|----------|
| Access Controls | Role-based permissions | IAM policy documents |
| Segregation of Duties | Separate roles per service | RBAC configuration |
| Audit Trail | CloudTrail + Audit logs | Audit log retention |
| Change Management | Terraform version control | Git history |

### NIST SP 800-53

| Control | Description | Implementation |
|---------|-------------|----------------|
| AC-6 | Least Privilege | Minimal required permissions |
| AC-2 | Account Management | Service account lifecycle |
| AU-2 | Audit Events | Comprehensive audit logging |
| IA-2 | Authentication | MFA + service authentication |

---

## Troubleshooting

### Common Issues

#### 1. Permission Denied Errors
```bash
# Check current permissions
aws sts get-caller-identity

# Verify role assumption
aws sts assume-role --role-arn arn:aws:iam::ACCOUNT_ID:role/role-name \
                    --role-session-name test-session

# Check Kubernetes permissions
kubectl auth can-i <verb> <resource> --as=system:serviceaccount:namespace:sa-name
```

#### 2. IRSA Not Working
```bash
# Verify OIDC provider
aws eks describe-cluster --name cluster-name \
    --query "cluster.identity.oidc.issuer" --output text

# Check service account annotation
kubectl describe sa service-account-name -n namespace

# Verify pod has AWS credentials
kubectl exec -it pod-name -n namespace -- env | grep AWS
```

#### 3. Audit Script Failures
```bash
# Debug mode
bash -x ./iam-security-audit.sh production

# Check AWS CLI configuration
aws configure list

# Verify jq installation
jq --version

# Test specific audit function
./iam-security-audit.sh production 2>&1 | grep -A 5 "ERROR"
```

### Security Best Practices

1. **Regular Audits**
   - Run weekly in production
   - Daily in development
   - After any IAM changes

2. **Access Review**
   - Quarterly review of all IAM roles
   - Monthly review of user access
   - Weekly review of critical service permissions

3. **Rotation Schedule**
   - Access keys: 90 days
   - Service account tokens: 30 days
   - Root credentials: Never use

4. **Monitoring**
   - CloudTrail for API calls
   - CloudWatch for permission denials
   - Custom metrics for failed authentications

---

## Maintenance

### Update Schedule
- **Weekly:** Run security audit
- **Monthly:** Review and update policies
- **Quarterly:** Full security review
- **Annually:** Compliance audit

### Version Control
All IAM policies and RBAC configurations are version controlled in Git:
```
infrastructure/
├── terraform/modules/iam/      # IAM policies
├── kubernetes/security/         # RBAC configs
└── security/                    # Audit scripts
```

### Contact Information
- **Security Team:** security@example.com
- **DevOps Team:** devops@example.com
- **Compliance:** compliance@example.com

---

## Appendix

### A. IAM Policy Templates
[Link to policy templates]

### B. RBAC Examples
[Link to RBAC examples]

### C. Audit Report Samples
[Link to sample reports]

### D. Remediation Playbooks
[Link to remediation guides]

---

*Last Updated: December 2024*
*Version: 1.0.0*
*Classification: INTERNAL - CONFIDENTIAL*