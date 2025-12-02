# PostgreSQL Database Module for Waqiti Banking Platform
# Provides high-availability PostgreSQL with read replicas and automated backups

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-db-subnet-group"
    Type = "Database"
  })
}

# DB Parameter Group for PostgreSQL optimization
resource "aws_db_parameter_group" "main" {
  family = "postgres15"
  name   = "${var.project_name}-${var.environment}-postgres-params"

  # Performance optimization parameters
  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name  = "log_statement"
    value = "all"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # Log queries taking more than 1 second
  }

  parameter {
    name  = "max_connections"
    value = var.max_connections
  }

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/4}"
  }

  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory/2}"
  }

  parameter {
    name  = "maintenance_work_mem"
    value = "2048MB"
  }

  parameter {
    name  = "checkpoint_completion_target"
    value = "0.9"
  }

  parameter {
    name  = "wal_buffers"
    value = "16MB"
  }

  parameter {
    name  = "default_statistics_target"
    value = "100"
  }

  parameter {
    name  = "random_page_cost"
    value = "1.1"
  }

  parameter {
    name  = "effective_io_concurrency"
    value = "200"
  }

  tags = var.common_tags
}

# Security Group for Database
resource "aws_security_group" "database" {
  name_prefix = "${var.project_name}-${var.environment}-db-"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "PostgreSQL access from application tier"
  }

  # Egress to S3 for backups (via VPC endpoint - requires VPC endpoint setup)
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    prefix_list_ids = [var.s3_prefix_list_id]
    description = "HTTPS to S3 for backups"
  }

  # Egress to KMS for encryption (via VPC endpoint - requires VPC endpoint setup)
  egress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    prefix_list_ids = [var.kms_prefix_list_id]
    description = "HTTPS to KMS for encryption operations"
  }

  # Egress to application subnets only for replication
  egress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.application_subnet_cidrs
    description = "PostgreSQL replication to read replicas in application subnets"
  }

  # DNS resolution
  egress {
    from_port   = 53
    to_port     = 53
    protocol    = "udp"
    cidr_blocks = [var.vpc_cidr]
    description = "DNS resolution within VPC"
  }

  egress {
    from_port   = 53
    to_port     = 53
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "DNS resolution within VPC (TCP)"
  }

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-database-sg"
    Type = "Security"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# KMS Key for database encryption
resource "aws_kms_key" "database" {
  description             = "KMS key for ${var.project_name} ${var.environment} database encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-db-key"
    Type = "Encryption"
  })
}

resource "aws_kms_alias" "database" {
  name          = "alias/${var.project_name}-${var.environment}-database"
  target_key_id = aws_kms_key.database.key_id
}

# Random password for master user
resource "random_password" "master_password" {
  length  = 32
  special = true
}

# Primary PostgreSQL instance
resource "aws_db_instance" "primary" {
  identifier = "${var.project_name}-${var.environment}-primary"

  # Engine configuration
  engine                      = "postgres"
  engine_version             = var.postgres_version
  instance_class             = var.instance_class
  allocated_storage          = var.allocated_storage
  max_allocated_storage      = var.max_allocated_storage
  storage_type               = "gp3"
  storage_encrypted          = true
  kms_key_id                = aws_kms_key.database.arn

  # Database configuration
  db_name  = var.database_name
  username = var.master_username
  password = random_password.master_password.result
  port     = 5432

  # Network configuration
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false

  # Parameter and option groups
  parameter_group_name = aws_db_parameter_group.main.name

  # Backup configuration
  backup_retention_period = var.backup_retention_period
  backup_window          = "03:00-04:00"  # UTC
  maintenance_window     = "sun:04:00-sun:05:00"  # UTC

  # Monitoring and logging
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn
  
  enabled_cloudwatch_logs_exports = [
    "postgresql"
  ]

  # Performance Insights
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  performance_insights_kms_key_id      = aws_kms_key.database.arn

  # Security
  deletion_protection      = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.project_name}-${var.environment}-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Multi-AZ deployment for high availability
  multi_az = var.multi_az

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-primary-database"
    Type = "Database"
    Role = "Primary"
  })

  lifecycle {
    prevent_destroy = true
    ignore_changes = [
      password,  # Managed by secrets manager
      final_snapshot_identifier
    ]
  }
}

# Read replica for read-heavy workloads
resource "aws_db_instance" "read_replica" {
  count = var.create_read_replica ? var.read_replica_count : 0

  identifier = "${var.project_name}-${var.environment}-replica-${count.index + 1}"

  # Source database
  replicate_source_db = aws_db_instance.primary.identifier

  # Instance configuration
  instance_class        = var.replica_instance_class
  publicly_accessible   = false
  auto_minor_version_upgrade = false

  # Monitoring
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  # Performance Insights
  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  performance_insights_kms_key_id      = aws_kms_key.database.arn

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-replica-${count.index + 1}"
    Type = "Database"
    Role = "ReadReplica"
  })

  lifecycle {
    prevent_destroy = true
  }
}

# IAM Role for RDS Enhanced Monitoring
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.project_name}-${var.environment}-rds-monitoring-role"

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

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Store database credentials in AWS Secrets Manager
resource "aws_secretsmanager_secret" "database_credentials" {
  name        = "${var.project_name}/${var.environment}/database/credentials"
  description = "Database credentials for ${var.project_name} ${var.environment}"
  
  kms_key_id = aws_kms_key.database.arn

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-db-credentials"
    Type = "Secret"
  })
}

resource "aws_secretsmanager_secret_version" "database_credentials" {
  secret_id = aws_secretsmanager_secret.database_credentials.id
  
  secret_string = jsonencode({
    username = aws_db_instance.primary.username
    password = random_password.master_password.result
    engine   = "postgres"
    host     = aws_db_instance.primary.endpoint
    port     = aws_db_instance.primary.port
    dbname   = aws_db_instance.primary.db_name
    dbInstanceIdentifier = aws_db_instance.primary.id
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# CloudWatch Alarms for monitoring
resource "aws_cloudwatch_metric_alarm" "database_cpu" {
  alarm_name          = "${var.project_name}-${var.environment}-database-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = "120"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors database cpu utilization"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.primary.id
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "database_connections" {
  alarm_name          = "${var.project_name}-${var.environment}-database-connections"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = "120"
  statistic           = "Average"
  threshold           = var.max_connections * 0.8
  alarm_description   = "This metric monitors database connection count"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.primary.id
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "database_freeable_memory" {
  alarm_name          = "${var.project_name}-${var.environment}-database-freeable-memory"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "FreeableMemory"
  namespace           = "AWS/RDS"
  period              = "120"
  statistic           = "Average"
  threshold           = "268435456"  # 256 MB in bytes
  alarm_description   = "This metric monitors database freeable memory"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.primary.id
  }

  tags = var.common_tags
}

# Database option group (if needed for extensions)
resource "aws_db_option_group" "main" {
  name                     = "${var.project_name}-${var.environment}-postgres-options"
  option_group_description = "Option group for ${var.project_name} ${var.environment} PostgreSQL"
  engine_name              = "postgres"
  major_engine_version     = split(".", var.postgres_version)[0]

  tags = var.common_tags
}