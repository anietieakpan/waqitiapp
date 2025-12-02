# Vault policies for Waqiti services

# Base policy for all services
path "secret/data/waqiti/common/*" {
  capabilities = ["read"]
}

# Service-specific policies
path "secret/data/waqiti/payment-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/user-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/wallet-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/notification-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/kyc-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/ml-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/security-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/event-sourcing-service/*" {
  capabilities = ["read", "list"]
}

path "secret/data/waqiti/recurring-payment-service/*" {
  capabilities = ["read", "list"]
}

# Database dynamic credentials
path "database/creds/waqiti-*" {
  capabilities = ["read"]
}

# PKI for internal TLS
path "pki_int/issue/waqiti-service" {
  capabilities = ["create", "update"]
}

# Transit engine for encryption
path "transit/encrypt/waqiti" {
  capabilities = ["update"]
}

path "transit/decrypt/waqiti" {
  capabilities = ["update"]
}

path "transit/keys/waqiti" {
  capabilities = ["read"]
}

# AWS credentials for services
path "aws/creds/waqiti-s3" {
  capabilities = ["read"]
}

path "aws/creds/waqiti-kms" {
  capabilities = ["read"]
}

# Kubernetes auth
path "auth/kubernetes/login" {
  capabilities = ["update"]
}