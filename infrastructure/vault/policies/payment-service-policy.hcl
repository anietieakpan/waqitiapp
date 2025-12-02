# HashiCorp Vault Policy for Payment Service
#
# This policy grants the payment-service application access to its secrets
# while following the principle of least privilege.
#
# Security Classification: CRITICAL
# PCI DSS Compliance: Level 1
# Author: Waqiti Platform Team
# Date: 2025-10-01
#
# Apply this policy:
#   vault policy write payment-service payment-service-policy.hcl
#
# Attach to AppRole:
#   vault write auth/approle/role/payment-service \
#     token_policies="payment-service" \
#     token_ttl=1h \
#     token_max_ttl=24h

# ============================================================================
# Payment Provider Secrets (Read-Only)
# ============================================================================

# Allow read access to all payment provider secrets
path "secret/data/payment-service/providers/*" {
  capabilities = ["read", "list"]
}

# Allow read access to encryption keys
path "secret/data/payment-service/encryption/*" {
  capabilities = ["read", "list"]
}

# Allow read access to security secrets (JWT, etc.)
path "secret/data/payment-service/security/*" {
  capabilities = ["read", "list"]
}

# Allow read access to Keycloak integration secrets
path "secret/data/payment-service/keycloak/*" {
  capabilities = ["read", "list"]
}

# Allow read access to banking API credentials
path "secret/data/payment-service/banking/*" {
  capabilities = ["read", "list"]
}

# ============================================================================
# Database Dynamic Credentials
# ============================================================================

# Allow payment-service to request dynamic database credentials
path "database/creds/payment-service-db-role" {
  capabilities = ["read"]
}

# Allow lease renewal for database credentials
path "sys/leases/renew" {
  capabilities = ["update"]
}

# Allow lease revocation (for cleanup)
path "sys/leases/revoke" {
  capabilities = ["update"]
}

# ============================================================================
# Transit Encryption Engine
# ============================================================================

# Allow encryption/decryption using transit engine for PII data
path "transit/encrypt/payment-service-*" {
  capabilities = ["update"]
}

path "transit/decrypt/payment-service-*" {
  capabilities = ["update"]
}

# Allow data key generation for field-level encryption
path "transit/datakey/plaintext/payment-service-*" {
  capabilities = ["update"]
}

# ============================================================================
# Metadata Access (for secret versioning)
# ============================================================================

# Allow reading secret metadata (versions, timestamps)
path "secret/metadata/payment-service/*" {
  capabilities = ["read", "list"]
}

# ============================================================================
# Token Management
# ============================================================================

# Allow token renewal (important for long-running services)
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Allow token lookup (for debugging)
path "auth/token/lookup-self" {
  capabilities = ["read"]
}

# ============================================================================
# Deny Destructive Operations (Defense in Depth)
# ============================================================================

# Explicitly deny ability to delete secrets (even if granted elsewhere)
path "secret/data/payment-service/*" {
  capabilities = ["deny"]
  denied_parameters = {
    "delete" = []
  }
}

# Deny access to other services' secrets
path "secret/data/wallet-service/*" {
  capabilities = ["deny"]
}

path "secret/data/user-service/*" {
  capabilities = ["deny"]
}

# Deny access to Vault system configuration
path "sys/config/*" {
  capabilities = ["deny"]
}

path "sys/policies/*" {
  capabilities = ["deny"]
}
