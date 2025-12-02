# Waqiti P2P Payment Platform - Terraform Variables
# Variable definitions for infrastructure configuration

# Global Configuration
variable "project_name" {
  description = "Name of the project"
  type        = string
  default     = "waqiti-p2p-platform"
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "production"], var.environment)
    error_message = "Environment must be one of: dev, staging, production."
  }
}

variable "aws_region" {
  description = "AWS region for infrastructure deployment"
  type        = string
  default     = "us-west-2"
}

variable "domain_name" {
  description = "Domain name for the application"
  type        = string
}

variable "route53_zone_id" {
  description = "Route 53 hosted zone ID for DNS management"
  type        = string
}

variable "alert_email" {
  description = "Email address for infrastructure alerts"
  type        = string
}

# Terraform State Configuration
variable "terraform_state_bucket" {
  description = "S3 bucket for Terraform state storage"
  type        = string
}

variable "terraform_lock_table" {
  description = "DynamoDB table for Terraform state locking"
  type        = string
}

# Network Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# Kubernetes Configuration
variable "kubernetes_version" {
  description = "Kubernetes version for EKS cluster"
  type        = string
  default     = "1.28"
}

# Database Configuration
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r5.large"
  validation {
    condition = can(regex("^db\\.(t3|r5|r6g)\\.(micro|small|medium|large|xlarge|2xlarge|4xlarge|8xlarge|12xlarge|16xlarge|24xlarge)", var.db_instance_class))
    error_message = "DB instance class must be a valid RDS instance type."
  }
}

variable "db_allocated_storage" {
  description = "Initial allocated storage for RDS instance (GB)"
  type        = number
  default     = 100
  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 65536
    error_message = "DB allocated storage must be between 20 and 65536 GB."
  }
}

variable "db_max_allocated_storage" {
  description = "Maximum allocated storage for RDS autoscaling (GB)"
  type        = number
  default     = 1000
  validation {
    condition     = var.db_max_allocated_storage >= var.db_allocated_storage
    error_message = "DB max allocated storage must be greater than or equal to allocated storage."
  }
}

# Cache Configuration
variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.r6g.large"
  validation {
    condition = can(regex("^cache\\.(t3|r6g|r5)\\.(micro|small|medium|large|xlarge|2xlarge|4xlarge|8xlarge|12xlarge|16xlarge|24xlarge)", var.redis_node_type))
    error_message = "Redis node type must be a valid ElastiCache instance type."
  }
}

variable "redis_num_nodes" {
  description = "Number of cache nodes in the Redis cluster"
  type        = number
  default     = 3
  validation {
    condition     = var.redis_num_nodes >= 1 && var.redis_num_nodes <= 20
    error_message = "Redis number of nodes must be between 1 and 20."
  }
}

# Messaging Configuration
variable "kafka_instance_type" {
  description = "MSK Kafka broker instance type"
  type        = string
  default     = "kafka.m5.large"
  validation {
    condition = can(regex("^kafka\\.(t3|m5|r5)\\.(micro|small|medium|large|xlarge|2xlarge|4xlarge|8xlarge|12xlarge|16xlarge|24xlarge)", var.kafka_instance_type))
    error_message = "Kafka instance type must be a valid MSK instance type."
  }
}

# Vault Configuration
variable "vault_instance_type" {
  description = "HashiCorp Vault instance type"
  type        = string
  default     = "t3.medium"
  validation {
    condition = can(regex("^(t3|t2|m5|c5|r5)\\.(micro|small|medium|large|xlarge|2xlarge|4xlarge|8xlarge|12xlarge|16xlarge|24xlarge)", var.vault_instance_type))
    error_message = "Vault instance type must be a valid EC2 instance type."
  }
}

# Monitoring Configuration
variable "log_retention_days" {
  description = "CloudWatch log retention period in days"
  type        = number
  default     = 30
  validation {
    condition = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.log_retention_days)
    error_message = "Log retention days must be a valid CloudWatch retention period."
  }
}

# Security Configuration
variable "enable_encryption" {
  description = "Enable encryption for all supported resources"
  type        = bool
  default     = true
}

variable "enable_backup" {
  description = "Enable automated backups"
  type        = bool
  default     = true
}

variable "enable_monitoring" {
  description = "Enable enhanced monitoring"
  type        = bool
  default     = true
}

variable "enable_multi_az" {
  description = "Enable Multi-AZ deployment for high availability"
  type        = bool
  default     = true
}

# Cost Optimization
variable "enable_spot_instances" {
  description = "Enable spot instances for non-critical workloads"
  type        = bool
  default     = true
}

variable "auto_scaling_enabled" {
  description = "Enable auto scaling for EKS node groups"
  type        = bool
  default     = true
}

# Compliance and Governance
variable "compliance_framework" {
  description = "Compliance framework to adhere to (PCI-DSS, SOC2, etc.)"
  type        = string
  default     = "PCI-DSS"
  validation {
    condition = contains(["PCI-DSS", "SOC2", "ISO27001", "GDPR"], var.compliance_framework)
    error_message = "Compliance framework must be one of: PCI-DSS, SOC2, ISO27001, GDPR."
  }
}

variable "data_residency_region" {
  description = "Region where sensitive data must reside"
  type        = string
  default     = ""
}

# Feature Flags
variable "enable_waf" {
  description = "Enable AWS WAF for application protection"
  type        = bool
  default     = true
}

variable "enable_cloudtrail" {
  description = "Enable AWS CloudTrail for audit logging"
  type        = bool
  default     = true
}

variable "enable_config" {
  description = "Enable AWS Config for compliance monitoring"
  type        = bool
  default     = true
}

variable "enable_guardduty" {
  description = "Enable AWS GuardDuty for threat detection"
  type        = bool
  default     = true
}

variable "enable_secrets_manager" {
  description = "Enable AWS Secrets Manager integration"
  type        = bool
  default     = true
}

# Environment-specific Overrides
variable "environment_config" {
  description = "Environment-specific configuration overrides"
  type = object({
    min_capacity = optional(number, 2)
    max_capacity = optional(number, 20)
    desired_capacity = optional(number, 6)
    enable_deletion_protection = optional(bool, true)
    backup_retention_days = optional(number, 30)
    monitoring_level = optional(string, "detailed")
  })
  default = {}
}

# Resource Tagging
variable "additional_tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}

# Network Security
variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access the infrastructure"
  type        = list(string)
  default     = []
}

variable "vpn_cidr_blocks" {
  description = "CIDR blocks for VPN access"
  type        = list(string)
  default     = []
}

# Performance Configuration
variable "performance_mode" {
  description = "Performance mode for the infrastructure (standard, optimized, high-performance)"
  type        = string
  default     = "optimized"
  validation {
    condition = contains(["standard", "optimized", "high-performance"], var.performance_mode)
    error_message = "Performance mode must be one of: standard, optimized, high-performance."
  }
}