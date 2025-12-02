# Production Vault Configuration with TLS Enabled
# Description: Secure production configuration for HashiCorp Vault with TLS 1.3
# Security: PCI DSS compliant, encryption in transit, mTLS ready

# Storage backend - Consider Consul or etcd in production for HA
storage "consul" {
  address = "consul:8500"
  path    = "vault/"
  token   = "${CONSUL_TOKEN}"  # Managed by Vault Agent

  # High availability settings
  ha_enabled = "true"

  # TLS for Consul communication
  tls_ca_file   = "/vault/tls/consul-ca.pem"
  tls_cert_file = "/vault/tls/consul-cert.pem"
  tls_key_file  = "/vault/tls/consul-key.pem"
}

# Primary listener with TLS 1.3
listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_disable   = "false"  # CRITICAL: TLS ENABLED

  # TLS Configuration
  tls_cert_file            = "/vault/tls/vault-cert.pem"
  tls_key_file             = "/vault/tls/vault-key.pem"
  tls_client_ca_file       = "/vault/tls/ca.pem"

  # TLS 1.3 only (most secure)
  tls_min_version          = "tls13"

  # Require client certificates for mTLS
  tls_require_and_verify_client_cert = "true"

  # Cipher suites (TLS 1.3 has built-in secure suites)
  tls_cipher_suites = "TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256,TLS_CHACHA20_POLY1305_SHA256"

  # OCSP stapling for certificate validation
  tls_disable_client_certs = "false"
}

# Metrics listener (Prometheus) - separate from API
listener "tcp" {
  address       = "0.0.0.0:8202"
  tls_disable   = "false"

  tls_cert_file = "/vault/tls/vault-cert.pem"
  tls_key_file  = "/vault/tls/vault-key.pem"

  # Telemetry-specific settings
  telemetry {
    unauthenticated_metrics_access = "false"
  }
}

# UI Configuration
ui = true

# Auto-unseal with AWS KMS (production-grade)
seal "awskms" {
  region     = "us-east-1"
  kms_key_id = "alias/example-vault-unseal-key"
  endpoint   = "https://kms.us-east-1.amazonaws.com"
}

# API and Cluster addresses (HTTPS)
api_addr     = "https://vault.example.com:8200"
cluster_addr = "https://vault-cluster.example.internal:8201"

# Cluster listener with mTLS
listener "tcp" {
  address            = "0.0.0.0:8201"
  cluster_address    = "0.0.0.0:8201"
  tls_disable        = "false"

  tls_cert_file      = "/vault/tls/vault-cert.pem"
  tls_key_file       = "/vault/tls/vault-key.pem"
  tls_client_ca_file = "/vault/tls/ca.pem"

  tls_require_and_verify_client_cert = "true"
}

# Telemetry
telemetry {
  prometheus_retention_time = "30s"
  disable_hostname          = false

  # StatsD for additional metrics
  statsd_address = "statsd:8125"
}

# Log configuration
log_level  = "INFO"
log_format = "json"

# Performance tuning
disable_mlock = false  # Enable in production for security

# Lease configuration
max_lease_ttl     = "768h"  # 32 days
default_lease_ttl = "168h"  # 7 days

# Plugin directory
plugin_directory = "/vault/plugins"

# Entropy Augmentation (for enhanced RNG)
entropy "seal" {
  mode = "augmentation"
}

# License path (if using Vault Enterprise)
# license_path = "/vault/license/vault.hclic"

# HA settings
cluster_name = "example-vault-prod"

# Raft integrated storage (alternative to Consul for HA)
# Uncomment for Raft-based HA without external dependencies
# storage "raft" {
#   path    = "/vault/data"
#   node_id = "vault-node-1"
#
#   retry_join {
#     leader_api_addr         = "https://vault-node-1:8200"
#     leader_ca_cert_file     = "/vault/tls/ca.pem"
#     leader_client_cert_file = "/vault/tls/vault-cert.pem"
#     leader_client_key_file  = "/vault/tls/vault-key.pem"
#   }
#
#   retry_join {
#     leader_api_addr         = "https://vault-node-2:8200"
#     leader_ca_cert_file     = "/vault/tls/ca.pem"
#     leader_client_cert_file = "/vault/tls/vault-cert.pem"
#     leader_client_key_file  = "/vault/tls/vault-key.pem"
#   }
#
#   retry_join {
#     leader_api_addr         = "https://vault-node-3:8200"
#     leader_ca_cert_file     = "/vault/tls/ca.pem"
#     leader_client_cert_file = "/vault/tls/vault-cert.pem"
#     leader_client_key_file  = "/vault/tls/vault-key.pem"
#   }
# }
