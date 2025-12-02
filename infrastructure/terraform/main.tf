# Waqiti P2P Payment Platform - Infrastructure as Code
# Terraform configuration for production-ready deployment

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.21"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.4"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    bucket         = "example-terraform-state"
    key            = "infrastructure/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "waqiti-terraform-locks"
  }
}

# Configure AWS Provider
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "waqiti-p2p-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
      Owner       = "platform-team"
    }
  }
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

# VPC and Networking
module "networking" {
  source = "./modules/networking"

  environment          = var.environment
  vpc_cidr            = var.vpc_cidr
  availability_zones  = data.aws_availability_zones.available.names
  enable_nat_gateway  = true
  enable_vpn_gateway  = false
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = local.common_tags
}

# EKS Cluster
module "eks" {
  source = "./modules/eks"

  environment = var.environment
  cluster_name = "${var.project_name}-${var.environment}"
  cluster_version = var.kubernetes_version

  vpc_id     = module.networking.vpc_id
  subnet_ids = module.networking.private_subnet_ids

  node_groups = {
    system = {
      instance_types = ["t3.medium"]
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      capacity_type  = "ON_DEMAND"
      node_labels = {
        role = "system"
      }
      taints = [
        {
          key    = "CriticalAddonsOnly"
          value  = "true"
          effect = "NO_SCHEDULE"
        }
      ]
    }
    
    application = {
      instance_types = ["t3.large", "t3.xlarge"]
      min_size       = 3
      max_size       = 20
      desired_size   = 6
      capacity_type  = "SPOT"
      node_labels = {
        role = "application"
      }
    }
    
    database = {
      instance_types = ["r5.large"]
      min_size       = 2
      max_size       = 6
      desired_size   = 2
      capacity_type  = "ON_DEMAND"
      node_labels = {
        role = "database"
      }
      taints = [
        {
          key    = "database"
          value  = "true"
          effect = "NO_SCHEDULE"
        }
      ]
    }
  }

  tags = local.common_tags
}

# RDS PostgreSQL for persistent data
module "database" {
  source = "./modules/database"

  environment = var.environment
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.database_subnet_ids

  # Multi-AZ deployment for high availability
  multi_az = true
  
  # Database configuration
  engine_version = "15.4"
  instance_class = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  
  # Backup and maintenance
  backup_retention_period = 30
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
  
  # Security
  storage_encrypted = true
  deletion_protection = var.environment == "production"
  
  # Performance Insights
  performance_insights_enabled = true
  performance_insights_retention_period = 7

  tags = local.common_tags
}

# ElastiCache Redis for caching and sessions
module "cache" {
  source = "./modules/cache"

  environment = var.environment
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.private_subnet_ids

  # Redis cluster configuration
  node_type = var.redis_node_type
  num_cache_nodes = var.redis_num_nodes
  engine_version = "7.0"
  
  # High availability
  automatic_failover_enabled = true
  multi_az_enabled = true
  
  # Security
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token_enabled = true

  tags = local.common_tags
}

# Message Queue (MSK for Kafka)
module "messaging" {
  source = "./modules/messaging"

  environment = var.environment
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.private_subnet_ids

  # Kafka cluster configuration
  kafka_version = "2.8.1"
  instance_type = var.kafka_instance_type
  ebs_volume_size = 1000
  
  # Security
  encryption_in_transit = "TLS"
  encryption_at_rest = true
  
  # Monitoring
  enhanced_monitoring = "PER_TOPIC_PER_PARTITION"

  tags = local.common_tags
}

# Application Load Balancer
module "load_balancer" {
  source = "./modules/load_balancer"

  environment = var.environment
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.public_subnet_ids

  # SSL Certificate
  domain_name = var.domain_name
  certificate_arn = module.ssl_certificate.certificate_arn

  tags = local.common_tags
}

# SSL Certificate
module "ssl_certificate" {
  source = "./modules/ssl_certificate"

  domain_name = var.domain_name
  zone_id     = var.route53_zone_id

  tags = local.common_tags
}

# HashiCorp Vault for secrets management
module "vault" {
  source = "./modules/vault"

  environment = var.environment
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.private_subnet_ids

  # Vault cluster
  instance_type = var.vault_instance_type
  min_size = 3
  max_size = 5
  desired_capacity = 3

  # Storage
  kms_key_id = module.kms.vault_key_id

  tags = local.common_tags
}

# KMS for encryption
module "kms" {
  source = "./modules/kms"

  environment = var.environment
  
  tags = local.common_tags
}

# CloudWatch and monitoring
module "monitoring" {
  source = "./modules/monitoring"

  environment = var.environment
  cluster_name = module.eks.cluster_name

  # Log retention
  log_retention_days = var.log_retention_days

  # Alerting
  sns_topic_arn = module.alerting.sns_topic_arn

  tags = local.common_tags
}

# SNS for alerting
module "alerting" {
  source = "./modules/alerting"

  environment = var.environment
  alert_email = var.alert_email

  tags = local.common_tags
}

# S3 buckets for storage
module "storage" {
  source = "./modules/storage"

  environment = var.environment
  
  buckets = {
    backup = {
      versioning = true
      lifecycle_rules = [
        {
          enabled = true
          transitions = [
            {
              days          = 30
              storage_class = "STANDARD_IA"
            },
            {
              days          = 90
              storage_class = "GLACIER"
            }
          ]
        }
      ]
    }
    
    documents = {
      versioning = true
      encryption = true
    }
    
    logs = {
      versioning = false
      lifecycle_rules = [
        {
          enabled = true
          expiration = {
            days = 90
          }
        }
      ]
    }
  }

  tags = local.common_tags
}

# WAF for application security
module "waf" {
  source = "./modules/waf"

  environment = var.environment
  load_balancer_arn = module.load_balancer.load_balancer_arn

  # WAF rules
  enable_rate_limiting = true
  enable_geo_blocking = true
  blocked_countries = ["CN", "RU", "KP"]

  tags = local.common_tags
}

# Backup and disaster recovery
module "backup" {
  source = "./modules/backup"

  environment = var.environment
  
  # RDS backup
  rds_instance_arn = module.database.db_instance_arn
  
  # EFS backup (if used)
  backup_schedule = "cron(0 2 * * ? *)"  # Daily at 2 AM
  backup_retention_days = 30

  tags = local.common_tags
}

# Local values
locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = "platform-team"
    CreatedAt   = timestamp()
  }
}