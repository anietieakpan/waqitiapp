# HashiCorp Vault Production Configuration
# This configuration provides enterprise-grade secret management with:
# - High Availability using Raft consensus
# - Auto-unseal using AWS KMS
# - TLS encryption for all communications
# - Audit logging for compliance
# - Performance tuning for production workloads

# Vault API and cluster communication
listener "tcp" {
  address       = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"
  
  # TLS Configuration
  tls_cert_file = "/vault/certs/vault.crt"
  tls_key_file  = "/vault/certs/vault.key"
  tls_client_ca_file = "/vault/certs/ca.crt"
  
  # Security Headers
  tls_min_version = "tls12"
  tls_cipher_suites = [
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
  ]
  
  # Performance tuning
  max_request_size = 33554432  # 32MB
  max_request_duration = "90s"
}

# Raft Storage Backend for HA
storage "raft" {
  path = "/vault/data"
  node_id = "vault-node-1"
  
  # Performance tuning
  performance_multiplier = 1
  max_entry_size = 1048576  # 1MB
  
  # Automatic snapshots for disaster recovery
  snapshot_threshold = 8192
  trailing_logs = 10000
  
  # HA Configuration
  retry_join {
    leader_api_addr = "https://vault-node-1.example.internal:8200"
  }

  retry_join {
    leader_api_addr = "https://vault-node-2.example.internal:8200"
  }

  retry_join {
    leader_api_addr = "https://vault-node-3.example.internal:8200"
  }
}

# AWS KMS Auto-Unseal
seal "awskms" {
  region     = "${AWS_REGION}"
  kms_key_id = "${AWS_KMS_KEY_ID}"
  
  # IAM role for EKS IRSA
  role_arn = "${AWS_VAULT_ROLE_ARN}"
  
  # Performance settings
  endpoints = ["https://kms.${AWS_REGION}.amazonaws.com"]
}

# Service Registration with Consul
service_registration "consul" {
  address = "consul.example.internal:8500"
  service = "vault"

  # Health check configuration
  check {
    interval = "10s"
    timeout  = "5s"
  }
}

# Telemetry Configuration
telemetry {
  prometheus_retention_time = "30s"
  disable_hostname = true

  # StatsD for metrics aggregation
  statsd_address = "statsd.example.internal:8125"
  statsite_address = "statsite.example.internal:8125"
}

# API Configuration
api_addr = "https://vault.example.com:8200"
cluster_addr = "https://vault.example.internal:8201"

# UI Configuration
ui = true

# Performance and Resource Configuration
max_lease_ttl = "768h"
default_lease_ttl = "768h"
cluster_name = "example-vault-prod"

# Audit Logging for Compliance
audit {
  enabled = true
}

# Log Configuration
log_level = "info"
log_format = "json"

# Cache Configuration for Performance
cache_size = 131072  # 128KB

# Plugin Directory
plugin_directory = "/vault/plugins"

# Disable features not needed in production
disable_mlock = false
disable_cache = false
disable_indexing = false
disable_sealwrap = false

# Enable performance standby nodes
enable_performance_standby = true
performance_standby_count = 2