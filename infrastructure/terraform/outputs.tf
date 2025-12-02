# Waqiti P2P Payment Platform - Terraform Outputs
# Output values for infrastructure components

# VPC and Networking Outputs
output "vpc_id" {
  description = "ID of the VPC"
  value       = module.networking.vpc_id
}

output "vpc_cidr_block" {
  description = "CIDR block of the VPC"
  value       = module.networking.vpc_cidr_block
}

output "public_subnet_ids" {
  description = "List of IDs of public subnets"
  value       = module.networking.public_subnet_ids
}

output "private_subnet_ids" {
  description = "List of IDs of private subnets"
  value       = module.networking.private_subnet_ids
}

output "database_subnet_ids" {
  description = "List of IDs of database subnets"
  value       = module.networking.database_subnet_ids
}

output "nat_gateway_ids" {
  description = "List of IDs of NAT Gateways"
  value       = module.networking.nat_gateway_ids
}

# EKS Cluster Outputs
output "cluster_id" {
  description = "EKS cluster ID"
  value       = module.eks.cluster_id
}

output "cluster_arn" {
  description = "EKS cluster ARN"
  value       = module.eks.cluster_arn
}

output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = module.eks.cluster_endpoint
  sensitive   = true
}

output "cluster_security_group_id" {
  description = "Security group ID attached to the EKS cluster"
  value       = module.eks.cluster_security_group_id
}

output "cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = module.eks.cluster_certificate_authority_data
  sensitive   = true
}

output "cluster_oidc_issuer_url" {
  description = "The URL on the EKS cluster for the OpenID Connect identity provider"
  value       = module.eks.cluster_oidc_issuer_url
}

output "node_groups" {
  description = "EKS node groups information"
  value       = module.eks.node_groups
  sensitive   = true
}

# Database Outputs
output "rds_instance_id" {
  description = "RDS instance ID"
  value       = module.database.db_instance_id
}

output "rds_instance_arn" {
  description = "RDS instance ARN"
  value       = module.database.db_instance_arn
}

output "rds_instance_endpoint" {
  description = "RDS instance endpoint"
  value       = module.database.db_instance_endpoint
  sensitive   = true
}

output "rds_instance_port" {
  description = "RDS instance port"
  value       = module.database.db_instance_port
}

output "rds_database_name" {
  description = "RDS database name"
  value       = module.database.db_name
}

output "rds_subnet_group_id" {
  description = "RDS subnet group ID"
  value       = module.database.db_subnet_group_id
}

output "rds_security_group_id" {
  description = "RDS security group ID"
  value       = module.database.db_security_group_id
}

# Cache Outputs
output "redis_cluster_id" {
  description = "ElastiCache Redis cluster ID"
  value       = module.cache.cluster_id
}

output "redis_cluster_address" {
  description = "ElastiCache Redis cluster address"
  value       = module.cache.cluster_address
  sensitive   = true
}

output "redis_cluster_port" {
  description = "ElastiCache Redis cluster port"
  value       = module.cache.cluster_port
}

output "redis_auth_token" {
  description = "ElastiCache Redis auth token"
  value       = module.cache.auth_token
  sensitive   = true
}

# Messaging Outputs
output "kafka_cluster_arn" {
  description = "MSK Kafka cluster ARN"
  value       = module.messaging.cluster_arn
}

output "kafka_cluster_name" {
  description = "MSK Kafka cluster name"
  value       = module.messaging.cluster_name
}

output "kafka_bootstrap_brokers" {
  description = "MSK Kafka bootstrap brokers"
  value       = module.messaging.bootstrap_brokers
  sensitive   = true
}

output "kafka_bootstrap_brokers_tls" {
  description = "MSK Kafka bootstrap brokers (TLS)"
  value       = module.messaging.bootstrap_brokers_tls
  sensitive   = true
}

output "kafka_zookeeper_connect_string" {
  description = "MSK Kafka ZooKeeper connection string"
  value       = module.messaging.zookeeper_connect_string
  sensitive   = true
}

# Load Balancer Outputs
output "load_balancer_arn" {
  description = "Application Load Balancer ARN"
  value       = module.load_balancer.load_balancer_arn
}

output "load_balancer_dns_name" {
  description = "Application Load Balancer DNS name"
  value       = module.load_balancer.load_balancer_dns_name
}

output "load_balancer_zone_id" {
  description = "Application Load Balancer zone ID"
  value       = module.load_balancer.load_balancer_zone_id
}

output "target_group_arns" {
  description = "Target group ARNs"
  value       = module.load_balancer.target_group_arns
}

# SSL Certificate Outputs
output "certificate_arn" {
  description = "SSL certificate ARN"
  value       = module.ssl_certificate.certificate_arn
}

output "certificate_domain_validation_options" {
  description = "SSL certificate domain validation options"
  value       = module.ssl_certificate.domain_validation_options
  sensitive   = true
}

# Vault Outputs
output "vault_cluster_endpoint" {
  description = "HashiCorp Vault cluster endpoint"
  value       = module.vault.cluster_endpoint
  sensitive   = true
}

output "vault_cluster_ca_cert" {
  description = "HashiCorp Vault cluster CA certificate"
  value       = module.vault.cluster_ca_cert
  sensitive   = true
}

output "vault_cluster_token" {
  description = "HashiCorp Vault initial root token"
  value       = module.vault.root_token
  sensitive   = true
}

# KMS Outputs
output "kms_key_ids" {
  description = "KMS key IDs for encryption"
  value       = module.kms.key_ids
  sensitive   = true
}

output "kms_key_arns" {
  description = "KMS key ARNs for encryption"
  value       = module.kms.key_arns
  sensitive   = true
}

# Monitoring Outputs
output "cloudwatch_log_groups" {
  description = "CloudWatch log group names"
  value       = module.monitoring.log_group_names
}

output "cloudwatch_log_group_arns" {
  description = "CloudWatch log group ARNs"
  value       = module.monitoring.log_group_arns
}

output "prometheus_endpoint" {
  description = "Prometheus endpoint for metrics"
  value       = module.monitoring.prometheus_endpoint
  sensitive   = true
}

output "grafana_endpoint" {
  description = "Grafana endpoint for dashboards"
  value       = module.monitoring.grafana_endpoint
  sensitive   = true
}

# Alerting Outputs
output "sns_topic_arn" {
  description = "SNS topic ARN for alerts"
  value       = module.alerting.sns_topic_arn
}

output "sns_topic_name" {
  description = "SNS topic name for alerts"
  value       = module.alerting.sns_topic_name
}

# Storage Outputs
output "s3_bucket_names" {
  description = "S3 bucket names"
  value       = module.storage.bucket_names
}

output "s3_bucket_arns" {
  description = "S3 bucket ARNs"
  value       = module.storage.bucket_arns
}

output "s3_bucket_domain_names" {
  description = "S3 bucket domain names"
  value       = module.storage.bucket_domain_names
}

# WAF Outputs
output "waf_web_acl_id" {
  description = "WAF Web ACL ID"
  value       = module.waf.web_acl_id
}

output "waf_web_acl_arn" {
  description = "WAF Web ACL ARN"
  value       = module.waf.web_acl_arn
}

# Backup Outputs
output "backup_vault_arn" {
  description = "AWS Backup vault ARN"
  value       = module.backup.backup_vault_arn
}

output "backup_plan_arn" {
  description = "AWS Backup plan ARN"
  value       = module.backup.backup_plan_arn
}

# DNS Outputs
output "route53_zone_id" {
  description = "Route 53 hosted zone ID"
  value       = var.route53_zone_id
}

output "application_url" {
  description = "Application URL"
  value       = "https://${var.domain_name}"
}

output "api_url" {
  description = "API Gateway URL"
  value       = "https://api.${var.domain_name}"
}

# Connection Information
output "kubectl_config_command" {
  description = "Command to configure kubectl"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "database_connection_command" {
  description = "Database connection command (use with appropriate credentials)"
  value       = "psql -h ${module.database.db_instance_endpoint} -p ${module.database.db_instance_port} -U admin -d ${module.database.db_name}"
  sensitive   = true
}

# Security Information
output "security_groups" {
  description = "Security group IDs created"
  value = {
    eks_cluster    = module.eks.cluster_security_group_id
    database       = module.database.db_security_group_id
    load_balancer  = module.load_balancer.security_group_id
    cache         = module.cache.security_group_id
  }
}

# Resource ARNs for IAM policies
output "resource_arns" {
  description = "ARNs of created resources for IAM policy creation"
  value = {
    eks_cluster     = module.eks.cluster_arn
    database        = module.database.db_instance_arn
    kafka_cluster   = module.messaging.cluster_arn
    s3_buckets      = module.storage.bucket_arns
    kms_keys        = module.kms.key_arns
    sns_topic       = module.alerting.sns_topic_arn
  }
  sensitive = true
}