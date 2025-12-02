# JWT Secret Management Policy for Waqiti Platform
# This policy provides secure access to JWT secrets with automatic rotation

# Read access to JWT secrets
path "secret/data/jwt" {
  capabilities = ["read"]
}

# Write access for key rotation (restricted to admin/rotation service)
path "secret/data/jwt" {
  capabilities = ["create", "update"]
  required_parameters = ["secret", "rotated_at", "version"]
  
  # Ensure minimum secret length
  allowed_parameters = {
    "secret" = ["*"]
    "rotated_at" = ["*"]
    "version" = ["*"]
  }
  
  min_wrapping_ttl = "1h"
  max_wrapping_ttl = "24h"
}

# List versions for audit
path "secret/metadata/jwt" {
  capabilities = ["read", "list"]
}

# Delete old versions (cleanup after rotation)
path "secret/destroy/jwt" {
  capabilities = ["update"]
}

# Access to transit encryption for JWT payload encryption
path "transit/encrypt/jwt-payload" {
  capabilities = ["update"]
}

path "transit/decrypt/jwt-payload" {
  capabilities = ["update"]
}

# Audit log access for JWT operations
path "sys/audit" {
  capabilities = ["read"]
}

# Key rotation scheduling
path "sys/leases/renew" {
  capabilities = ["update"]
}

# Performance metrics for JWT operations
path "sys/metrics" {
  capabilities = ["read"]
}