# =====================================================================
# Vault Secrets Management - Variables
# =====================================================================

variable "vault_address" {
  description = "Vault server address"
  type        = string
  default     = "https://vault.vault.svc.cluster.local:8200"
}

variable "vault_token" {
  description = "Vault root or admin token for Terraform operations"
  type        = string
  sensitive   = true
}

# =====================================================================
# PostgreSQL Credentials
# =====================================================================

variable "postgres_username" {
  description = "PostgreSQL primary database username"
  type        = string
  sensitive   = true
}

variable "postgres_password" {
  description = "PostgreSQL primary database password"
  type        = string
  sensitive   = true
}

variable "postgres_replication_password" {
  description = "PostgreSQL replication user password"
  type        = string
  sensitive   = true
}

# =====================================================================
# AR Payment Service Database Credentials
# =====================================================================

variable "ar_payment_db_username" {
  description = "AR Payment Service database username"
  type        = string
  sensitive   = true
}

variable "ar_payment_db_password" {
  description = "AR Payment Service database password"
  type        = string
  sensitive   = true
}

# =====================================================================
# Prometheus Monitoring Credentials
# =====================================================================

variable "prometheus_self_monitoring_password" {
  description = "Prometheus self-monitoring password"
  type        = string
  sensitive   = true
}

variable "prometheus_alertmanager_password" {
  description = "Prometheus to Alertmanager connection password"
  type        = string
  sensitive   = true
}

variable "prometheus_remote_write_password" {
  description = "Prometheus remote write authentication password"
  type        = string
  sensitive   = true
}

variable "prometheus_postgres_password" {
  description = "Prometheus PostgreSQL monitoring password"
  type        = string
  sensitive   = true
}

variable "prometheus_redis_password" {
  description = "Prometheus Redis monitoring password"
  type        = string
  sensitive   = true
}

variable "prometheus_kafka_password" {
  description = "Prometheus Kafka monitoring password"
  type        = string
  sensitive   = true
}

variable "prometheus_financial_password" {
  description = "Prometheus financial services monitoring password (extra secure)"
  type        = string
  sensitive   = true
}

# =====================================================================
# Kubernetes Configuration
# =====================================================================

variable "kubernetes_api_host" {
  description = "Kubernetes API server address"
  type        = string
}

variable "kubernetes_ca_cert" {
  description = "Kubernetes CA certificate for Vault auth"
  type        = string
  sensitive   = true
}

variable "kubernetes_token_reviewer_jwt" {
  description = "Kubernetes service account JWT for token review"
  type        = string
  sensitive   = true
}
