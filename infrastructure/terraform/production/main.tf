terraform {
  required_version = ">= 1.5.0"
  
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
  }
  
  backend "s3" {
    bucket         = "example-terraform-state"
    key            = "production/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

# Provider configurations
provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Environment = "production"
      ManagedBy   = "terraform"
      Project     = "waqiti"
    }
  }
}

provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
  }
}

# VPC Module
module "vpc" {
  source = "terraform-aws-modules/vpc/aws"
  version = "5.1.2"
  
  name = "example-production-vpc"
  cidr = "10.0.0.0/16"
  
  azs             = data.aws_availability_zones.available.names
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  database_subnets = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]
  
  enable_nat_gateway   = true
  single_nat_gateway   = false # High availability
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  # VPC Flow Logs
  enable_flow_log                      = true
  create_flow_log_cloudwatch_iam_role  = true
  create_flow_log_cloudwatch_log_group = true
  
  tags = {
    "kubernetes.io/cluster/example-production" = "shared"
  }
  
  public_subnet_tags = {
    "kubernetes.io/cluster/example-production" = "shared"
    "kubernetes.io/role/elb"                  = "1"
  }
  
  private_subnet_tags = {
    "kubernetes.io/cluster/example-production" = "shared"
    "kubernetes.io/role/internal-elb"         = "1"
  }
}

# EKS Cluster
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.17.2"
  
  cluster_name    = "example-production"
  cluster_version = "1.28"
  
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
  
  # Control plane logging
  cluster_enabled_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
  
  # Encryption
  create_kms_key = true
  cluster_encryption_config = {
    resources = ["secrets"]
  }
  
  # Node groups
  eks_managed_node_groups = {
    general = {
      desired_size = 3
      min_size     = 3
      max_size     = 10
      
      instance_types = ["t3.xlarge"]
      capacity_type  = "ON_DEMAND"
      
      # Launch template
      create_launch_template = true
      launch_template_name   = "example-general-nodes"
      
      disk_size = 100
      disk_type = "gp3"
      
      # Security
      enable_monitoring = true
      
      labels = {
        role = "general"
      }
      
      taints = []
    }
    
    compute = {
      desired_size = 2
      min_size     = 2
      max_size     = 20
      
      instance_types = ["c5.2xlarge"]
      capacity_type  = "SPOT"
      
      disk_size = 200
      disk_type = "gp3"
      
      labels = {
        role = "compute"
      }
      
      taints = [{
        key    = "compute"
        value  = "true"
        effect = "NO_SCHEDULE"
      }]
    }
  }
  
  # OIDC Provider
  enable_irsa = true
  
  # Security group rules
  node_security_group_additional_rules = {
    ingress_self_all = {
      description = "Node to node all ports/protocols"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      type        = "ingress"
      self        = true
    }
  }
}

# RDS Aurora PostgreSQL
module "rds_aurora" {
  source  = "terraform-aws-modules/rds-aurora/aws"
  version = "8.5.0"
  
  name           = "example-production-aurora"
  engine         = "aurora-postgresql"
  engine_version = "15.4"
  
  vpc_id                = module.vpc.vpc_id
  subnets               = module.vpc.database_subnets
  create_security_group = true
  allowed_cidr_blocks   = module.vpc.private_subnets_cidr_blocks
  
  # High availability
  instances = {
    1 = {
      instance_class = "db.r6g.2xlarge"
    }
    2 = {
      instance_class = "db.r6g.2xlarge"
    }
    3 = {
      instance_class = "db.r6g.xlarge"
      promotion_tier = 15
    }
  }
  
  # Backup and maintenance
  backup_retention_period = 30
  preferred_backup_window = "03:00-04:00"
  preferred_maintenance_window = "sun:04:00-sun:05:00"
  
  # Performance and monitoring
  enabled_cloudwatch_logs_exports = ["postgresql"]
  performance_insights_enabled    = true
  performance_insights_retention_period = 7
  
  # Security
  storage_encrypted = true
  kms_key_id       = aws_kms_key.rds.arn
  
  # Database parameters
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora_pg.name
  db_parameter_group_name         = aws_db_parameter_group.aurora_pg.name
}

# ElastiCache Redis Cluster
module "elasticache" {
  source = "terraform-aws-modules/elasticache/aws"
  version = "1.0.0"
  
  cluster_id               = "example-production-redis"
  engine                   = "redis"
  engine_version          = "7.1"
  port                    = 6379
  node_type               = "cache.r7g.xlarge"
  num_cache_nodes         = 3
  
  # High availability
  automatic_failover_enabled = true
  multi_az_enabled          = true
  
  # Security
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token_enabled        = true
  
  subnet_ids = module.vpc.private_subnets
  vpc_id     = module.vpc.vpc_id
  
  # Backup
  snapshot_retention_limit = 7
  snapshot_window         = "03:00-05:00"
  
  # Parameters
  parameter = [
    {
      name  = "maxmemory-policy"
      value = "allkeys-lru"
    }
  ]
}

# S3 Buckets
resource "aws_s3_bucket" "application_data" {
  bucket = "example-production-application-data"
}

resource "aws_s3_bucket_versioning" "application_data" {
  bucket = aws_s3_bucket.application_data.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "application_data" {
  bucket = aws_s3_bucket.application_data.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
  }
}

# CloudFront Distribution
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  is_ipv6_enabled    = true
  default_root_object = "index.html"
  
  origin {
    domain_name = aws_s3_bucket.frontend_assets.bucket_regional_domain_name
    origin_id   = "S3-${aws_s3_bucket.frontend_assets.id}"
    
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.frontend.cloudfront_access_identity_path
    }
  }
  
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.frontend_assets.id}"
    
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
    
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
    compress               = true
  }
  
  price_class = "PriceClass_100"
  
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  
  viewer_certificate {
    acm_certificate_arn = aws_acm_certificate.cloudfront.arn
    ssl_support_method  = "sni-only"
  }
  
  web_acl_id = aws_wafv2_web_acl.main.arn
}

# WAF for CloudFront
resource "aws_wafv2_web_acl" "main" {
  name  = "example-production-waf"
  scope = "CLOUDFRONT"
  
  default_action {
    allow {}
  }
  
  rule {
    name     = "RateLimitRule"
    priority = 1
    
    action {
      block {}
    }
    
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name               = "RateLimitRule"
      sampled_requests_enabled   = true
    }
  }
  
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 2
    
    override_action {
      none {}
    }
    
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name               = "CommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }
}

# KMS Keys
resource "aws_kms_key" "master" {
  description             = "Waqiti Production Master Key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_key" "rds" {
  description             = "Waqiti Production RDS Encryption Key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_key" "s3" {
  description             = "Waqiti Production S3 Encryption Key"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

# Outputs
output "eks_cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "RDS Aurora endpoint"
  value       = module.rds_aurora.cluster_endpoint
  sensitive   = true
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint"
  value       = module.elasticache.primary_endpoint_address
  sensitive   = true
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.main.id
}

# Variables
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}