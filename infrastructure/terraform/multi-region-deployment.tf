# Multi-region deployment configuration for Waqiti platform
terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.20"
    }
  }
}

# Variables
variable "primary_region" {
  description = "Primary AWS region"
  type        = string
  default     = "us-west-2"
}

variable "secondary_regions" {
  description = "Secondary AWS regions for disaster recovery"
  type        = list(string)
  default     = ["us-east-1", "eu-west-1"]
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "waqiti-cluster"
}

# Local values
locals {
  all_regions = concat([var.primary_region], var.secondary_regions)
  
  common_tags = {
    Environment = var.environment
    Project     = "waqiti"
    ManagedBy   = "terraform"
  }
}

# Primary region provider
provider "aws" {
  alias  = "primary"
  region = var.primary_region
  
  default_tags {
    tags = local.common_tags
  }
}

# Secondary region providers
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  
  default_tags {
    tags = local.common_tags
  }
}

provider "aws" {
  alias  = "eu_west_1"
  region = "eu-west-1"
  
  default_tags {
    tags = local.common_tags
  }
}

# Data sources
data "aws_availability_zones" "primary" {
  provider = aws.primary
  state    = "available"
}

data "aws_availability_zones" "us_east_1" {
  provider = aws.us_east_1
  state    = "available"
}

data "aws_availability_zones" "eu_west_1" {
  provider = aws.eu_west_1
  state    = "available"
}

# VPC for each region
module "vpc_primary" {
  source = "terraform-aws-modules/vpc/aws"
  
  providers = {
    aws = aws.primary
  }
  
  name = "${var.cluster_name}-vpc-${var.primary_region}"
  cidr = "10.0.0.0/16"
  
  azs             = slice(data.aws_availability_zones.primary.names, 0, 3)
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = true
  enable_dns_hostnames = true
  enable_dns_support = true
  
  # Enable VPC Flow Logs
  enable_flow_log                      = true
  create_flow_log_cloudwatch_iam_role  = true
  create_flow_log_cloudwatch_log_group = true
  
  tags = merge(local.common_tags, {
    "kubernetes.io/cluster/${var.cluster_name}-${var.primary_region}" = "shared"
    Region = var.primary_region
    Type   = "primary"
  })
  
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }
}

module "vpc_us_east_1" {
  source = "terraform-aws-modules/vpc/aws"
  
  providers = {
    aws = aws.us_east_1
  }
  
  name = "${var.cluster_name}-vpc-us-east-1"
  cidr = "10.1.0.0/16"
  
  azs             = slice(data.aws_availability_zones.us_east_1.names, 0, 3)
  private_subnets = ["10.1.1.0/24", "10.1.2.0/24", "10.1.3.0/24"]
  public_subnets  = ["10.1.101.0/24", "10.1.102.0/24", "10.1.103.0/24"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = true
  enable_dns_hostnames = true
  enable_dns_support = true
  
  enable_flow_log                      = true
  create_flow_log_cloudwatch_iam_role  = true
  create_flow_log_cloudwatch_log_group = true
  
  tags = merge(local.common_tags, {
    "kubernetes.io/cluster/${var.cluster_name}-us-east-1" = "shared"
    Region = "us-east-1"
    Type   = "secondary"
  })
  
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }
}

module "vpc_eu_west_1" {
  source = "terraform-aws-modules/vpc/aws"
  
  providers = {
    aws = aws.eu_west_1
  }
  
  name = "${var.cluster_name}-vpc-eu-west-1"
  cidr = "10.2.0.0/16"
  
  azs             = slice(data.aws_availability_zones.eu_west_1.names, 0, 3)
  private_subnets = ["10.2.1.0/24", "10.2.2.0/24", "10.2.3.0/24"]
  public_subnets  = ["10.2.101.0/24", "10.2.102.0/24", "10.2.103.0/24"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = true
  enable_dns_hostnames = true
  enable_dns_support = true
  
  enable_flow_log                      = true
  create_flow_log_cloudwatch_iam_role  = true
  create_flow_log_cloudwatch_log_group = true
  
  tags = merge(local.common_tags, {
    "kubernetes.io/cluster/${var.cluster_name}-eu-west-1" = "shared"
    Region = "eu-west-1"
    Type   = "secondary"
  })
  
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# EKS clusters for each region
module "eks_primary" {
  source = "terraform-aws-modules/eks/aws"
  
  providers = {
    aws = aws.primary
  }
  
  cluster_name    = "${var.cluster_name}-${var.primary_region}"
  cluster_version = "1.27"
  
  vpc_id                         = module.vpc_primary.vpc_id
  subnet_ids                     = module.vpc_primary.private_subnets
  cluster_endpoint_public_access = true
  
  # Cluster encryption
  cluster_encryption_config = {
    provider_key_arn = aws_kms_key.eks_primary.arn
    resources        = ["secrets"]
  }
  
  # Node groups
  eks_managed_node_groups = {
    application = {
      name = "application-nodes"
      
      instance_types = ["m5.2xlarge"]
      
      min_size     = 3
      max_size     = 30
      desired_size = 5
      
      disk_size = 100
      
      labels = {
        node-type = "application"
      }
      
      taints = []
      
      update_config = {
        max_unavailable_percentage = 25
      }
    }
    
    database = {
      name = "database-nodes"
      
      instance_types = ["r5.xlarge"]
      
      min_size     = 2
      max_size     = 10
      desired_size = 3
      
      disk_size = 200
      
      labels = {
        node-type = "database"
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
  
  tags = merge(local.common_tags, {
    Region = var.primary_region
    Type   = "primary"
  })
}

module "eks_us_east_1" {
  source = "terraform-aws-modules/eks/aws"
  
  providers = {
    aws = aws.us_east_1
  }
  
  cluster_name    = "${var.cluster_name}-us-east-1"
  cluster_version = "1.27"
  
  vpc_id                         = module.vpc_us_east_1.vpc_id
  subnet_ids                     = module.vpc_us_east_1.private_subnets
  cluster_endpoint_public_access = true
  
  cluster_encryption_config = {
    provider_key_arn = aws_kms_key.eks_us_east_1.arn
    resources        = ["secrets"]
  }
  
  eks_managed_node_groups = {
    application = {
      name = "application-nodes"
      
      instance_types = ["m5.2xlarge"]
      
      min_size     = 2
      max_size     = 20
      desired_size = 3
      
      disk_size = 100
      
      labels = {
        node-type = "application"
      }
    }
  }
  
  tags = merge(local.common_tags, {
    Region = "us-east-1"
    Type   = "secondary"
  })
}

module "eks_eu_west_1" {
  source = "terraform-aws-modules/eks/aws"
  
  providers = {
    aws = aws.eu_west_1
  }
  
  cluster_name    = "${var.cluster_name}-eu-west-1"
  cluster_version = "1.27"
  
  vpc_id                         = module.vpc_eu_west_1.vpc_id
  subnet_ids                     = module.vpc_eu_west_1.private_subnets
  cluster_endpoint_public_access = true
  
  cluster_encryption_config = {
    provider_key_arn = aws_kms_key.eks_eu_west_1.arn
    resources        = ["secrets"]
  }
  
  eks_managed_node_groups = {
    application = {
      name = "application-nodes"
      
      instance_types = ["m5.2xlarge"]
      
      min_size     = 2
      max_size     = 20
      desired_size = 3
      
      disk_size = 100
      
      labels = {
        node-type = "application"
      }
    }
  }
  
  tags = merge(local.common_tags, {
    Region = "eu-west-1"
    Type   = "secondary"
  })
}

# KMS keys for encryption
resource "aws_kms_key" "eks_primary" {
  provider = aws.primary
  
  description             = "EKS cluster encryption key for ${var.primary_region}"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-${var.primary_region}-key"
    Region = var.primary_region
  })
}

resource "aws_kms_key" "eks_us_east_1" {
  provider = aws.us_east_1
  
  description             = "EKS cluster encryption key for us-east-1"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-us-east-1-key"
    Region = "us-east-1"
  })
}

resource "aws_kms_key" "eks_eu_west_1" {
  provider = aws.eu_west_1
  
  description             = "EKS cluster encryption key for eu-west-1"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-eu-west-1-key"
    Region = "eu-west-1"
  })
}

# Global Load Balancer using Route 53
resource "aws_route53_zone" "main" {
  provider = aws.primary
  name     = "waqiti.com"
  
  tags = local.common_tags
}

# Health checks for each region
resource "aws_route53_health_check" "primary" {
  provider = aws.primary
  
  fqdn                            = "api-${var.primary_region}.waqiti.com"
  port                            = 443
  type                            = "HTTPS"
  resource_path                   = "/health"
  failure_threshold               = "3"
  request_interval                = "30"
  cloudwatch_alarm_region         = var.primary_region
  cloudwatch_alarm_name           = "${var.cluster_name}-${var.primary_region}-health"
  insufficient_data_health_status = "Failure"
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-${var.primary_region}-health"
    Region = var.primary_region
  })
}

resource "aws_route53_health_check" "us_east_1" {
  provider = aws.primary
  
  fqdn                            = "api-us-east-1.waqiti.com"
  port                            = 443
  type                            = "HTTPS"
  resource_path                   = "/health"
  failure_threshold               = "3"
  request_interval                = "30"
  cloudwatch_alarm_region         = "us-east-1"
  cloudwatch_alarm_name           = "${var.cluster_name}-us-east-1-health"
  insufficient_data_health_status = "Failure"
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-us-east-1-health"
    Region = "us-east-1"
  })
}

resource "aws_route53_health_check" "eu_west_1" {
  provider = aws.primary
  
  fqdn                            = "api-eu-west-1.waqiti.com"
  port                            = 443
  type                            = "HTTPS"
  resource_path                   = "/health"
  failure_threshold               = "3"
  request_interval                = "30"
  cloudwatch_alarm_region         = "eu-west-1"
  cloudwatch_alarm_name           = "${var.cluster_name}-eu-west-1-health"
  insufficient_data_health_status = "Failure"
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-eu-west-1-health"
    Region = "eu-west-1"
  })
}

# Route 53 records with failover routing
resource "aws_route53_record" "api_primary" {
  provider = aws.primary
  
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.waqiti.com"
  type    = "A"
  
  set_identifier = "primary"
  
  failover_routing_policy {
    type = "PRIMARY"
  }
  
  health_check_id = aws_route53_health_check.primary.id
  
  alias {
    name                   = module.eks_primary.cluster_endpoint
    zone_id                = "Z1D633PJN98FT9" # ELB zone ID for us-west-2
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "api_secondary_us" {
  provider = aws.primary
  
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.waqiti.com"
  type    = "A"
  
  set_identifier = "secondary-us"
  
  failover_routing_policy {
    type = "SECONDARY"
  }
  
  health_check_id = aws_route53_health_check.us_east_1.id
  
  alias {
    name                   = module.eks_us_east_1.cluster_endpoint
    zone_id                = "Z35SXDOTRQ7X7K" # ELB zone ID for us-east-1
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "api_secondary_eu" {
  provider = aws.primary
  
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.waqiti.com"
  type    = "A"
  
  set_identifier = "secondary-eu"
  
  failover_routing_policy {
    type = "SECONDARY"
  }
  
  health_check_id = aws_route53_health_check.eu_west_1.id
  
  alias {
    name                   = module.eks_eu_west_1.cluster_endpoint
    zone_id                = "Z32O12XQLNTSW2" # ELB zone ID for eu-west-1
    evaluate_target_health = true
  }
}

# Cross-region VPC peering
resource "aws_vpc_peering_connection" "primary_to_us_east" {
  provider = aws.primary
  
  vpc_id        = module.vpc_primary.vpc_id
  peer_vpc_id   = module.vpc_us_east_1.vpc_id
  peer_region   = "us-east-1"
  auto_accept   = false
  
  tags = merge(local.common_tags, {
    Name = "${var.cluster_name}-primary-to-us-east-1"
  })
}

resource "aws_vpc_peering_connection_accepter" "primary_to_us_east" {
  provider = aws.us_east_1
  
  vpc_peering_connection_id = aws_vpc_peering_connection.primary_to_us_east.id
  auto_accept               = true
  
  tags = merge(local.common_tags, {
    Name = "${var.cluster_name}-primary-to-us-east-1-accepter"
  })
}

# Database replication setup
resource "aws_rds_global_cluster" "waqiti" {
  provider = aws.primary
  
  global_cluster_identifier      = "${var.cluster_name}-global"
  engine                         = "aurora-postgresql"
  engine_version                 = "14.9"
  database_name                  = "waqiti"
  deletion_protection            = true
  storage_encrypted              = true
  
  tags = local.common_tags
}

# Primary RDS cluster
resource "aws_rds_cluster" "primary" {
  provider = aws.primary
  
  cluster_identifier              = "${var.cluster_name}-primary"
  global_cluster_identifier       = aws_rds_global_cluster.waqiti.id
  engine                          = "aurora-postgresql"
  engine_version                  = "14.9"
  database_name                   = "waqiti"
  master_username                 = "waqiti_admin"
  manage_master_user_password     = true
  
  db_subnet_group_name            = aws_db_subnet_group.primary.name
  vpc_security_group_ids          = [aws_security_group.rds_primary.id]
  
  backup_retention_period         = 30
  preferred_backup_window         = "03:00-04:00"
  preferred_maintenance_window    = "Sun:04:00-Sun:05:00"
  
  storage_encrypted               = true
  kms_key_id                      = aws_kms_key.rds_primary.arn
  
  deletion_protection             = true
  skip_final_snapshot            = false
  final_snapshot_identifier      = "${var.cluster_name}-primary-final-snapshot"
  
  tags = merge(local.common_tags, {
    Region = var.primary_region
    Type   = "primary"
  })
}

# Secondary RDS clusters
resource "aws_rds_cluster" "us_east_1" {
  provider = aws.us_east_1
  
  cluster_identifier         = "${var.cluster_name}-us-east-1"
  global_cluster_identifier  = aws_rds_global_cluster.waqiti.id
  engine                     = "aurora-postgresql"
  engine_version             = "14.9"
  
  db_subnet_group_name       = aws_db_subnet_group.us_east_1.name
  vpc_security_group_ids     = [aws_security_group.rds_us_east_1.id]
  
  storage_encrypted          = true
  kms_key_id                 = aws_kms_key.rds_us_east_1.arn
  
  skip_final_snapshot        = false
  final_snapshot_identifier = "${var.cluster_name}-us-east-1-final-snapshot"
  
  depends_on = [aws_rds_cluster_instance.primary]
  
  tags = merge(local.common_tags, {
    Region = "us-east-1"
    Type   = "secondary"
  })
}

# RDS instances
resource "aws_rds_cluster_instance" "primary" {
  provider = aws.primary
  count    = 2
  
  identifier              = "${var.cluster_name}-primary-${count.index}"
  cluster_identifier      = aws_rds_cluster.primary.id
  instance_class          = "db.r6g.2xlarge"
  engine                  = "aurora-postgresql"
  engine_version          = "14.9"
  publicly_accessible     = false
  monitoring_interval     = 60
  monitoring_role_arn     = aws_iam_role.rds_monitoring.arn
  performance_insights_enabled = true
  
  tags = merge(local.common_tags, {
    Region = var.primary_region
    Type   = "primary"
  })
}

# Additional KMS keys for RDS
resource "aws_kms_key" "rds_primary" {
  provider = aws.primary
  
  description             = "RDS encryption key for ${var.primary_region}"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-rds-${var.primary_region}"
    Region = var.primary_region
  })
}

resource "aws_kms_key" "rds_us_east_1" {
  provider = aws.us_east_1
  
  description             = "RDS encryption key for us-east-1"
  deletion_window_in_days = 7
  enable_key_rotation     = true
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-rds-us-east-1"
    Region = "us-east-1"
  })
}

# DB subnet groups
resource "aws_db_subnet_group" "primary" {
  provider = aws.primary
  
  name       = "${var.cluster_name}-primary"
  subnet_ids = module.vpc_primary.private_subnets
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-primary"
    Region = var.primary_region
  })
}

resource "aws_db_subnet_group" "us_east_1" {
  provider = aws.us_east_1
  
  name       = "${var.cluster_name}-us-east-1"
  subnet_ids = module.vpc_us_east_1.private_subnets
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-us-east-1"
    Region = "us-east-1"
  })
}

# Security groups for RDS
resource "aws_security_group" "rds_primary" {
  provider = aws.primary
  
  name_prefix = "${var.cluster_name}-rds-primary"
  vpc_id      = module.vpc_primary.vpc_id
  
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [module.vpc_primary.vpc_cidr_block]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-rds-primary"
    Region = var.primary_region
  })
}

resource "aws_security_group" "rds_us_east_1" {
  provider = aws.us_east_1
  
  name_prefix = "${var.cluster_name}-rds-us-east-1"
  vpc_id      = module.vpc_us_east_1.vpc_id
  
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [module.vpc_us_east_1.vpc_cidr_block]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = merge(local.common_tags, {
    Name   = "${var.cluster_name}-rds-us-east-1"
    Region = "us-east-1"
  })
}

# IAM role for RDS monitoring
resource "aws_iam_role" "rds_monitoring" {
  provider = aws.primary
  
  name = "${var.cluster_name}-rds-monitoring"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })
  
  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  provider = aws.primary
  
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Outputs
output "primary_cluster_endpoint" {
  description = "Primary EKS cluster endpoint"
  value       = module.eks_primary.cluster_endpoint
}

output "primary_cluster_name" {
  description = "Primary EKS cluster name"
  value       = module.eks_primary.cluster_name
}

output "secondary_cluster_endpoints" {
  description = "Secondary EKS cluster endpoints"
  value = {
    us_east_1 = module.eks_us_east_1.cluster_endpoint
    eu_west_1 = module.eks_eu_west_1.cluster_endpoint
  }
}

output "rds_global_cluster_id" {
  description = "RDS Global cluster identifier"
  value       = aws_rds_global_cluster.waqiti.id
}

output "route53_zone_id" {
  description = "Route 53 hosted zone ID"
  value       = aws_route53_zone.main.zone_id
}