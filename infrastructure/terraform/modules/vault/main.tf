# HashiCorp Vault Module for External Secrets Management
# Production-ready Vault cluster for secure secrets management

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.21"
    }
  }
}

# Data sources
data "aws_ami" "vault" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
  
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# IAM role for Vault instances
resource "aws_iam_role" "vault" {
  name = "${var.environment}-vault-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = var.tags
}

# IAM policy for Vault auto-unseal using KMS
resource "aws_iam_role_policy" "vault_kms" {
  name = "${var.environment}-vault-kms-policy"
  role = aws_iam_role.vault.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:DescribeKey",
          "kms:Encrypt",
          "kms:GenerateDataKey",
          "kms:GenerateDataKeyWithoutPlaintext",
          "kms:ReEncryptFrom",
          "kms:ReEncryptTo"
        ]
        Resource = [var.kms_key_id]
      }
    ]
  })
}

# IAM policy for Vault clustering
resource "aws_iam_role_policy" "vault_clustering" {
  name = "${var.environment}-vault-clustering-policy"
  role = aws_iam_role.vault.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "ec2:DescribeTags",
          "autoscaling:DescribeAutoScalingGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# IAM instance profile
resource "aws_iam_instance_profile" "vault" {
  name = "${var.environment}-vault-instance-profile"
  role = aws_iam_role.vault.name

  tags = var.tags
}

# Security Group for Vault cluster
resource "aws_security_group" "vault" {
  name        = "${var.environment}-vault-sg"
  description = "Security group for Vault cluster"
  vpc_id      = var.vpc_id

  # Vault API
  ingress {
    from_port   = 8200
    to_port     = 8200
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }

  # Vault cluster communication
  ingress {
    from_port = 8201
    to_port   = 8201
    protocol  = "tcp"
    self      = true
  }

  # SSH (for administration)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-sg"
  })
}

# Launch Template for Vault instances
resource "aws_launch_template" "vault" {
  name_prefix   = "${var.environment}-vault-"
  image_id      = data.aws_ami.vault.id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.vault.id]

  iam_instance_profile {
    name = aws_iam_instance_profile.vault.name
  }

  user_data = base64encode(templatefile("${path.module}/vault-userdata.sh", {
    kms_key_id    = var.kms_key_id
    environment   = var.environment
    vault_version = var.vault_version
    aws_region    = data.aws_region.current.name
  }))

  tag_specifications {
    resource_type = "instance"
    tags = merge(var.tags, {
      Name = "${var.environment}-vault"
      Type = "vault-cluster"
    })
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Auto Scaling Group for Vault cluster
resource "aws_autoscaling_group" "vault" {
  name                = "${var.environment}-vault-asg"
  vpc_zone_identifier = var.subnet_ids
  target_group_arns   = [aws_lb_target_group.vault.arn]
  health_check_type   = "ELB"
  health_check_grace_period = 300

  min_size         = var.min_size
  max_size         = var.max_size
  desired_capacity = var.desired_capacity

  launch_template {
    id      = aws_launch_template.vault.id
    version = "$Latest"
  }

  dynamic "tag" {
    for_each = var.tags
    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.environment}-vault"
    propagate_at_launch = true
  }

  tag {
    key                 = "vault-cluster"
    value               = "${var.environment}-vault"
    propagate_at_launch = true
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Application Load Balancer for Vault
resource "aws_lb" "vault" {
  name               = "${var.environment}-vault-lb"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.vault_lb.id]
  subnets            = var.subnet_ids

  enable_deletion_protection = var.environment == "production"

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-lb"
  })
}

# Security Group for Vault Load Balancer
resource "aws_security_group" "vault_lb" {
  name        = "${var.environment}-vault-lb-sg"
  description = "Security group for Vault load balancer"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 8200
    to_port     = 8200
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-lb-sg"
  })
}

# Target Group for Vault
resource "aws_lb_target_group" "vault" {
  name     = "${var.environment}-vault-tg"
  port     = 8200
  protocol = "HTTPS"
  vpc_id   = var.vpc_id

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = "/v1/sys/health"
    matcher             = "200,429,473,501"
    port                = "traffic-port"
    protocol            = "HTTPS"
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-tg"
  })
}

# Load Balancer Listener
resource "aws_lb_listener" "vault" {
  load_balancer_arn = aws_lb.vault.arn
  port              = "8200"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"
  certificate_arn   = aws_acm_certificate.vault.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.vault.arn
  }
}

# ACM Certificate for Vault
resource "aws_acm_certificate" "vault" {
  domain_name       = "vault.${var.domain_name}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-cert"
  })
}

# Route53 record for Vault
resource "aws_route53_record" "vault" {
  zone_id = var.route53_zone_id
  name    = "vault.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_lb.vault.dns_name
    zone_id                = aws_lb.vault.zone_id
    evaluate_target_health = true
  }
}

# S3 bucket for Vault storage backend (optional)
resource "aws_s3_bucket" "vault_storage" {
  count  = var.use_s3_backend ? 1 : 0
  bucket = "${var.environment}-vault-storage-${random_id.bucket_suffix.hex}"

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-storage"
  })
}

resource "aws_s3_bucket_versioning" "vault_storage" {
  count  = var.use_s3_backend ? 1 : 0
  bucket = aws_s3_bucket.vault_storage[0].id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_encryption" "vault_storage" {
  count  = var.use_s3_backend ? 1 : 0
  bucket = aws_s3_bucket.vault_storage[0].id

  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        kms_master_key_id = var.kms_key_id
        sse_algorithm     = "aws:kms"
      }
    }
  }
}

resource "aws_s3_bucket_public_access_block" "vault_storage" {
  count  = var.use_s3_backend ? 1 : 0
  bucket = aws_s3_bucket.vault_storage[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Random ID for unique bucket naming
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# DynamoDB table for Vault HA (if using DynamoDB backend)
resource "aws_dynamodb_table" "vault_ha" {
  count          = var.use_dynamodb_backend ? 1 : 0
  name           = "${var.environment}-vault-ha"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "Path"
  range_key      = "Key"

  attribute {
    name = "Path"
    type = "S"
  }

  attribute {
    name = "Key"
    type = "S"
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = var.kms_key_id
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-vault-ha"
  })
}

# Data sources
data "aws_vpc" "main" {
  id = var.vpc_id
}

data "aws_region" "current" {}

# CloudWatch Log Group for Vault
resource "aws_cloudwatch_log_group" "vault" {
  name              = "/aws/vault/${var.environment}"
  retention_in_days = 30
  kms_key_id        = var.kms_key_id

  tags = var.tags
}

# Vault Kubernetes Auth configuration
resource "vault_auth_backend" "kubernetes" {
  count = var.enable_kubernetes_auth ? 1 : 0
  type  = "kubernetes"
  path  = "kubernetes"

  depends_on = [vault_policy.kubernetes]
}

# Vault policy for Kubernetes authentication
resource "vault_policy" "kubernetes" {
  count = var.enable_kubernetes_auth ? 1 : 0
  name  = "kubernetes-policy"

  policy = <<EOT
# Allow access to secrets for Kubernetes
path "secret/data/kubernetes/*" {
  capabilities = ["read", "list"]
}

path "database/creds/waqiti-*" {
  capabilities = ["read"]
}

path "auth/token/lookup-self" {
  capabilities = ["read"]
}

path "auth/token/renew-self" {
  capabilities = ["update"]
}

path "auth/token/revoke-self" {
  capabilities = ["update"]
}
EOT
}

# Vault database secrets engine
resource "vault_database_secrets_mount" "postgres" {
  count = var.enable_database_secrets ? 1 : 0
  path  = "database"

  postgresql {
    name           = "waqiti-postgres"
    url            = "postgresql://{{username}}:{{password}}@${var.database_endpoint}:5432/waqiti?sslmode=require"
    username       = var.database_username
    password       = var.database_password
    verify_connection = true
    allowed_roles  = ["waqiti-app", "waqiti-readonly"]
  }
}

# Database role for application
resource "vault_database_secret_backend_role" "app" {
  count   = var.enable_database_secrets ? 1 : 0
  backend = vault_database_secrets_mount.postgres[0].path
  name    = "waqiti-app"
  db_name = vault_database_secrets_mount.postgres[0].postgresql[0].name

  creation_statements = [
    "CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';",
    "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";",
    "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"{{name}}\";",
    "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO \"{{name}}\";"
  ]

  revocation_statements = [
    "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM \"{{name}}\";",
    "REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM \"{{name}}\";",
    "REVOKE USAGE ON SCHEMA public FROM \"{{name}}\";",
    "DROP ROLE IF EXISTS \"{{name}}\";"
  ]

  default_ttl = 3600
  max_ttl     = 86400
}

# Vault monitoring
resource "aws_cloudwatch_metric_alarm" "vault_cpu" {
  alarm_name          = "${var.environment}-vault-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors vault cpu utilization"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.vault.name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "vault_memory" {
  alarm_name          = "${var.environment}-vault-high-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "MemoryUtilization"
  namespace           = "CWAgent"
  period              = "120"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "This metric monitors vault memory utilization"
  alarm_actions       = [var.sns_topic_arn]

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.vault.name
  }

  tags = var.tags
}