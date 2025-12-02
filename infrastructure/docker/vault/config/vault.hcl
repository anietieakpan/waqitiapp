# Production Vault Configuration
storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address = "0.0.0.0:8200"
  tls_disable = "true"
  # In production, set tls_disable = "false" and configure TLS certificates
}

ui = true

# Seal configuration (use auto-unseal in production)
seal "awskms" {
  region = "us-east-1"
  kms_key_id = "alias/vault-key"
}

# API address
api_addr = "http://0.0.0.0:8200"
cluster_addr = "http://0.0.0.0:8201"

# Log level
log_level = "INFO"

# Disable memory locking in containerized environments
disable_mlock = true

# Maximum lease TTL
max_lease_ttl = "768h"
default_lease_ttl = "768h"