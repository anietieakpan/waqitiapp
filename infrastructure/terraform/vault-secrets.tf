# =====================================================================
# Vault Secrets Management for Waqiti Platform
# =====================================================================
# This Terraform configuration sets up HashiCorp Vault for secure
# secrets management across all microservices in production.
#
# SECURITY FEATURES:
# - All secrets encrypted at rest with AES-256-GCM
# - Automatic secret rotation policies
# - Fine-grained access control with Vault policies
# - Kubernetes service account authentication
# - Audit logging for all secret access
#
# PREREQUISITES:
# - Vault cluster deployed and unsealed
# - Kubernetes cluster with external-secrets-operator installed
# - Service accounts created for each microservice
# =====================================================================

terraform {
  required_providers {
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.23.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24.0"
    }
  }
}

provider "vault" {
  address = var.vault_address
  token   = var.vault_token
}

# =====================================================================
# Vault KV Secrets Engine Configuration
# =====================================================================

resource "vault_mount" "kv_secrets" {
  path        = "secret"
  type        = "kv-v2"
  description = "KV Secrets Engine for Waqiti platform secrets"

  options = {
    version = "2"
  }
}

# =====================================================================
# Database Credentials - PostgreSQL
# =====================================================================

resource "vault_kv_secret_v2" "postgres_primary" {
  mount = vault_mount.kv_secrets.path
  name  = "database/postgres"

  data_json = jsonencode({
    username             = var.postgres_username
    password             = var.postgres_password
    replication_password = var.postgres_replication_password
    connection_string    = "postgresql://${var.postgres_username}:${var.postgres_password}@postgres-primary.database:5432/waqiti?sslmode=require"
  })

  custom_metadata {
    max_versions = 10
    data = {
      environment  = "production"
      service_type = "database"
      compliance   = "pci-dss"
      rotation     = "90d"
    }
  }
}

resource "vault_kv_secret_v2" "ar_payment_service_db" {
  mount = vault_mount.kv_secrets.path
  name  = "database/ar-payment-service"

  data_json = jsonencode({
    database_url = "postgresql://waqiti-db.database:5432/waqiti_db?sslmode=require"
    username     = var.ar_payment_db_username
    password     = var.ar_payment_db_password
  })

  custom_metadata {
    max_versions = 10
    data = {
      environment  = "production"
      service      = "ar-payment-service"
      compliance   = "pci-dss"
      rotation     = "90d"
    }
  }
}

# =====================================================================
# Monitoring Credentials - Prometheus
# =====================================================================

resource "vault_kv_secret_v2" "prometheus_auth" {
  mount = vault_mount.kv_secrets.path
  name  = "monitoring/prometheus"

  data_json = jsonencode({
    self_monitoring_password     = var.prometheus_self_monitoring_password
    alertmanager_password        = var.prometheus_alertmanager_password
    remote_write_password        = var.prometheus_remote_write_password
    postgres_monitoring_password = var.prometheus_postgres_password
    redis_monitoring_password    = var.prometheus_redis_password
    kafka_monitoring_password    = var.prometheus_kafka_password
    financial_monitoring_password = var.prometheus_financial_password
  })

  custom_metadata {
    max_versions = 5
    data = {
      environment  = "production"
      service_type = "monitoring"
      rotation     = "180d"
    }
  }
}

# =====================================================================
# External Secrets Operator - ClusterSecretStore
# =====================================================================

resource "kubernetes_manifest" "cluster_secret_store" {
  manifest = {
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ClusterSecretStore"

    metadata = {
      name = "vault-backend"
      labels = {
        "app.kubernetes.io/name"       = "external-secrets"
        "security.waqiti.com/type"     = "vault-store"
        "security.waqiti.com/critical" = "true"
      }
    }

    spec = {
      provider = {
        vault = {
          server  = var.vault_address
          path    = "secret"
          version = "v2"

          auth = {
            kubernetes = {
              mountPath = "kubernetes"
              role      = "external-secrets-operator"

              serviceAccountRef = {
                name      = "external-secrets-operator"
                namespace = "external-secrets-system"
              }
            }
          }
        }
      }
    }
  }
}

# =====================================================================
# Vault Kubernetes Auth Configuration
# =====================================================================

resource "vault_auth_backend" "kubernetes" {
  type        = "kubernetes"
  path        = "kubernetes"
  description = "Kubernetes authentication for Waqiti services"
}

resource "vault_kubernetes_auth_backend_config" "kubernetes_config" {
  backend                = vault_auth_backend.kubernetes.path
  kubernetes_host        = var.kubernetes_api_host
  kubernetes_ca_cert     = var.kubernetes_ca_cert
  token_reviewer_jwt     = var.kubernetes_token_reviewer_jwt
  disable_iss_validation = false
}

# =====================================================================
# Vault Policies for Service Access
# =====================================================================

resource "vault_policy" "postgres_access" {
  name = "postgres-access"

  policy = <<EOT
# Read-only access to PostgreSQL credentials
path "secret/data/database/postgres" {
  capabilities = ["read"]
}

# Allow listing secrets
path "secret/metadata/database/postgres" {
  capabilities = ["list", "read"]
}
EOT
}

resource "vault_policy" "ar_payment_service_access" {
  name = "ar-payment-service-access"

  policy = <<EOT
# Read-only access to AR payment service database credentials
path "secret/data/database/ar-payment-service" {
  capabilities = ["read"]
}

# Allow listing secrets
path "secret/metadata/database/ar-payment-service" {
  capabilities = ["list", "read"]
}
EOT
}

resource "vault_policy" "prometheus_access" {
  name = "prometheus-access"

  policy = <<EOT
# Read-only access to Prometheus monitoring credentials
path "secret/data/monitoring/prometheus" {
  capabilities = ["read"]
}

# Allow listing secrets
path "secret/metadata/monitoring/prometheus" {
  capabilities = ["list", "read"]
}
EOT
}

resource "vault_policy" "external_secrets_operator" {
  name = "external-secrets-operator"

  policy = <<EOT
# External Secrets Operator needs broad read access
path "secret/data/*" {
  capabilities = ["read"]
}

path "secret/metadata/*" {
  capabilities = ["list", "read"]
}

# Allow token renewal
path "auth/token/renew-self" {
  capabilities = ["update"]
}
EOT
}

# =====================================================================
# Vault Kubernetes Auth Roles
# =====================================================================

resource "vault_kubernetes_auth_backend_role" "postgres" {
  backend                          = vault_auth_backend.kubernetes.path
  role_name                        = "postgres"
  bound_service_account_names      = ["postgres-primary", "postgres-standby"]
  bound_service_account_namespaces = ["database"]
  token_ttl                        = 3600
  token_max_ttl                    = 7200
  token_policies                   = [vault_policy.postgres_access.name]
}

resource "vault_kubernetes_auth_backend_role" "ar_payment_service" {
  backend                          = vault_auth_backend.kubernetes.path
  role_name                        = "ar-payment-service"
  bound_service_account_names      = ["ar-payment-service"]
  bound_service_account_namespaces = ["example-prod"]
  token_ttl                        = 3600
  token_max_ttl                    = 7200
  token_policies                   = [vault_policy.ar_payment_service_access.name]
}

resource "vault_kubernetes_auth_backend_role" "prometheus" {
  backend                          = vault_auth_backend.kubernetes.path
  role_name                        = "prometheus"
  bound_service_account_names      = ["prometheus"]
  bound_service_account_namespaces = ["monitoring"]
  token_ttl                        = 3600
  token_max_ttl                    = 7200
  token_policies                   = [vault_policy.prometheus_access.name]
}

resource "vault_kubernetes_auth_backend_role" "external_secrets_operator" {
  backend                          = vault_auth_backend.kubernetes.path
  role_name                        = "external-secrets-operator"
  bound_service_account_names      = ["external-secrets-operator"]
  bound_service_account_namespaces = ["external-secrets-system"]
  token_ttl                        = 3600
  token_max_ttl                    = 7200
  token_policies                   = [vault_policy.external_secrets_operator.name]
}

# =====================================================================
# Secret Rotation Policies
# =====================================================================

resource "vault_kv_secret_backend_v2" "secret_config" {
  mount        = vault_mount.kv_secrets.path
  max_versions = 10
  cas_required = false

  # Delete old versions after 1 year
  delete_version_after = 31536000
}

# =====================================================================
# Audit Logging
# =====================================================================

resource "vault_audit" "file_audit" {
  type = "file"

  options = {
    file_path = "/vault/logs/audit.log"
  }
}

# =====================================================================
# Outputs
# =====================================================================

output "vault_cluster_secret_store_name" {
  description = "Name of the ClusterSecretStore for external-secrets-operator"
  value       = "vault-backend"
}

output "vault_kubernetes_auth_path" {
  description = "Kubernetes auth backend mount path"
  value       = vault_auth_backend.kubernetes.path
}

output "vault_secrets_mount_path" {
  description = "KV secrets engine mount path"
  value       = vault_mount.kv_secrets.path
}
