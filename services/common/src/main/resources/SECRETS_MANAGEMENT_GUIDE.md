# Waqiti Secrets Management Guide

## Overview
This document outlines the enterprise-grade secrets management practices for the Waqiti fintech platform.

**CRITICAL SECURITY REQUIREMENT**: All secrets MUST be managed through Vault or environment variables. 
Hardcoded secrets in configuration files are a CRITICAL security vulnerability.

## Recent Security Fixes

### Removed Hardcoded Secrets
- ❌ **REMOVED**: `international-transfer-service-secret` from application-keycloak.yml
- ❌ **REMOVED**: Trust-all SSL configuration from RestTemplateConfig
- ❌ **REMOVED**: Default empty passwords from Redis/Database configs

## Secrets Management Hierarchy

### 1. Production (Highest Security)
```yaml
# ALL secrets MUST come from HashiCorp Vault
spring:
  cloud:
    vault:
      enabled: true
      host: ${VAULT_HOST}
      port: ${VAULT_PORT:8200}
      scheme: https
      authentication: KUBERNETES # or APPROLE
      kubernetes:
        role: ${VAULT_ROLE}
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token

# Example secret retrieval
database:
  password: ${vault.database.password}
keycloak:
  credentials:
    secret: ${vault.keycloak.client-secret}
```

### 2. Staging/UAT
```yaml
# Secrets from Vault or encrypted environment variables
# Use separate Vault namespace for staging
vault:
  namespace: staging
```

### 3. Development (Local)
```yaml
# Use local Vault instance or encrypted .env files
# NEVER commit .env files to git

# .env file (add to .gitignore):
export DATABASE_PASSWORD="dev_password_from_vault"
export KEYCLOAK_CLIENT_SECRET="dev_secret_from_vault"
```

## Secrets Categories

### A. Authentication & Authorization
- **Keycloak Client Secrets**: Unique per service
- **JWT Signing Keys**: Rotated every 90 days
- **OAuth2 Tokens**: Short-lived, refreshable
- **API Keys**: For external service integration

### B. Database Credentials
- **PostgreSQL Passwords**: Rotated every 30 days
- **Redis Passwords**: Rotated every 30 days
- **MongoDB Credentials**: Rotated every 30 days

### C. External Service Credentials
- **Payment Provider Keys**: Stripe, PayPal, etc.
- **KYC Provider Keys**: Jumio, Onfido
- **Banking API Credentials**: SWIFT, correspondent banks
- **SMS/Email Provider Keys**: Twilio, SendGrid

### D. Encryption Keys
- **Data Encryption Keys**: For PII/PCI data
- **TLS Certificates**: For SSL/TLS
- **HSM Keys**: For payment processing

## Vault Integration

### Path Structure
```
secret/
├── waqiti/
│   ├── production/
│   │   ├── database/
│   │   │   ├── postgresql-password
│   │   │   └── redis-password
│   │   ├── keycloak/
│   │   │   ├── payment-service-secret
│   │   │   ├── wallet-service-secret
│   │   │   └── ...
│   │   ├── external-apis/
│   │   │   ├── stripe-api-key
│   │   │   ├── jumio-api-token
│   │   │   └── ...
│   │   └── encryption/
│   │       ├── data-encryption-key
│   │       └── tls-certificates
│   ├── staging/
│   └── development/
```

### Accessing Secrets in Spring Boot
```java
@Configuration
public class VaultSecretsConfig {
    
    @Value("${vault.database.password}")
    private String databasePassword;
    
    @Value("${vault.keycloak.client-secret}")
    private String keycloakSecret;
    
    // Secrets are injected at runtime from Vault
}
```

## Secret Rotation Policy

### Critical Secrets (Rotate every 30 days)
- Database passwords
- External API keys
- Payment provider keys

### Standard Secrets (Rotate every 90 days)
- Keycloak client secrets
- JWT signing keys
- Service-to-service credentials

### Certificates (Rotate before expiry)
- TLS certificates: 90 days before expiry
- Code signing certificates: 60 days before expiry

## Emergency Secret Rotation

In case of suspected compromise:

1. **Immediate**: Revoke compromised secret in Vault
2. **Within 1 hour**: Generate new secret
3. **Within 4 hours**: Deploy to all services
4. **Within 24 hours**: Complete security audit

## Configuration File Guidelines

### ✅ CORRECT - Fail-Secure Configuration
```yaml
# Service WILL NOT START without required secrets
database:
  password: ${DATABASE_PASSWORD}  # No fallback

keycloak:
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET}  # No fallback
```

### ❌ INCORRECT - Security Vulnerability
```yaml
# NEVER do this - hardcoded fallbacks allow bypassing security
database:
  password: ${DATABASE_PASSWORD:dev_password}  # ❌ SECURITY VIOLATION

keycloak:
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET:hardcoded-secret}  # ❌ CRITICAL VULNERABILITY
```

### ✅ CORRECT - Development Override (via profiles)
```yaml
# application-dev.yml (for local development only)
database:
  password: ${DATABASE_PASSWORD:dev_override_via_profile}

# But production profile MUST NOT have fallbacks
```

## Environment Variables vs Vault

### When to Use Environment Variables
- **Non-sensitive configuration**: Timeouts, URLs, feature flags
- **Development/Testing**: When Vault is not available

### When to Use Vault (Always in Production)
- **ALL secrets**: Passwords, keys, tokens
- **PII encryption keys**
- **Payment processing credentials**
- **Banking API credentials**

## Secret Detection in CI/CD

### Pre-commit Hooks
```bash
#!/bin/bash
# Detect hardcoded secrets before commit
if git diff --cached | grep -E '(password|secret|key|token).*:.*[a-zA-Z0-9]{8,}'; then
    echo "ERROR: Potential secret detected in commit"
    exit 1
fi
```

### GitLab CI/CD Secret Scanning
```yaml
# .gitlab-ci.yml
secret_detection:
  stage: test
  script:
    - gitleaks detect --verbose --no-git
  allow_failure: false
```

## Monitoring & Alerting

### Secret Access Logging
```yaml
# Enable Vault audit logging
vault:
  audit:
    enabled: true
    log-raw: false  # Don't log secret values
    file:
      path: /vault/logs/audit.log
```

### Alert on Suspicious Access
- Multiple failed secret retrievals
- Access from unexpected IP addresses
- Access outside business hours
- Secrets accessed by unauthorized services

## Compliance Requirements

### PCI DSS
- Secrets must be encrypted in transit and at rest
- Access to secrets must be logged and monitored
- Secrets must be rotated regularly

### SOX
- Secrets access must be auditable
- Separation of duties for secret management
- Automated secret rotation with approval workflows

### GDPR
- PII encryption keys must be managed separately
- Right to erasure requires key management
- Data breach notification within 72 hours

## Troubleshooting

### Service Fails to Start - Missing Secret
```
Error: Could not resolve placeholder 'KEYCLOAK_CLIENT_SECRET'
Solution: Set environment variable or configure Vault
```

### Vault Connection Timeout
```
Error: Connection timeout to Vault
Solution: Check network connectivity, Vault health, and authentication
```

### Secret Rotation Caused Service Outage
```
Issue: Old secret cached, new secret not propagated
Solution: Implement graceful secret refresh or rolling deployment
```

## Additional Resources

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [Spring Cloud Vault Reference](https://spring.io/projects/spring-cloud-vault)
- [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [PCI DSS Requirements](https://www.pcisecuritystandards.org/)

## Contact

For secrets management issues:
- Security Team: security@example.com
- DevOps Team: devops@example.com
- Emergency Hotline: +1-XXX-XXX-XXXX