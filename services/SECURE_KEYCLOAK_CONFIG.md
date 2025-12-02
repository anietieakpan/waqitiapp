# Secure Keycloak Configuration Guide

## ⚠️ CRITICAL SECURITY UPDATE REQUIRED

All Keycloak client secrets must be updated from their default values to secure random strings.

## Services Requiring Updates:

1. discovery-service
2. core-banking-service  
3. bill-payment-service
4. websocket-service
5. (and all other services with application-keycloak.yml)

## Secure Configuration Template

### Step 1: Generate Secure Secrets

Run this command for each service to generate a secure random secret:

```bash
# Generate a 32-character random secret
openssl rand -hex 32
```

### Step 2: Set Environment Variables

Add these to your secure environment configuration (e.g., Kubernetes secrets, Vault, etc.):

```bash
# Example secure secrets (generate your own!)
export DISCOVERY_SERVICE_CLIENT_SECRET=7f4a8e2b9c1d5f3a6e8b2c4d7f9a1b3e5c7d9f2a4b6c8e1a3d5f7b9c2e4a6b8d
export CORE_BANKING_SERVICE_CLIENT_SECRET=9a3b5c7d1e3f5a7b9c2d4e6f8a1b3c5d7e9f2a4b6c8d1e3f5a7b9d2e4f6a8b
export BILL_PAYMENT_SERVICE_CLIENT_SECRET=2e4f6a8b9c1d3e5f7a9b2c4d6e8f1a3b5c7d9e2f4a6b8c1d3e5f7a9b2c4d6e
export WEBSOCKET_SERVICE_CLIENT_SECRET=5c7d9e2f4a6b8c1d3e5f7a9b2c4d6e8f1a3b5c7d9e2f4a6b8c1d3e5f7a9b2c
```

### Step 3: Update Keycloak Admin Console

For each service client in Keycloak:

1. Login to Keycloak Admin Console
2. Navigate to Clients → [service-name]
3. Go to Credentials tab
4. Update the secret with the generated value
5. Save changes

### Step 4: Remove Default Values

Update all `application-keycloak.yml` files to remove default values:

```yaml
keycloak:
  auth-server-url: ${KEYCLOAK_AUTH_SERVER_URL}
  realm: ${KEYCLOAK_REALM}
  resource: ${SERVICE_NAME}
  credentials:
    secret: ${SERVICE_CLIENT_SECRET}  # No default value!
  use-resource-role-mappings: true
```

## Security Checklist

- [ ] All secrets are at least 32 characters
- [ ] All secrets are randomly generated
- [ ] No default values in configuration files
- [ ] Secrets stored in secure vault/secret manager
- [ ] Different secret for each service
- [ ] Secrets rotated regularly (every 90 days)
- [ ] Audit logging enabled for secret access

## Emergency Rotation Procedure

If a secret is compromised:

1. Generate new secret immediately
2. Update Keycloak first
3. Update environment variables
4. Rolling restart of affected services
5. Audit logs for unauthorized access

## Monitoring

Monitor for:
- Failed authentication attempts
- Unexpected service-to-service calls
- Token validation failures
- Suspicious patterns in service communication

---

**IMPORTANT**: Never commit actual secrets to version control. This file contains examples only.