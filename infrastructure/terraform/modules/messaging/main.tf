# Apache Kafka Messaging Module for Waqiti Banking Platform
# Provides managed Kafka cluster using Amazon MSK

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# KMS Key for Kafka encryption
resource "aws_kms_key" "kafka" {
  description             = "KMS key for ${var.project_name} ${var.environment} Kafka encryption"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-kafka-key"
    Type = "Encryption"
  })
}

resource "aws_kms_alias" "kafka" {
  name          = "alias/${var.project_name}-${var.environment}-kafka"
  target_key_id = aws_kms_key.kafka.key_id
}

# Security Group for Kafka
resource "aws_security_group" "kafka" {
  name_prefix = "${var.project_name}-${var.environment}-kafka-"
  vpc_id      = var.vpc_id

  # Kafka broker access
  ingress {
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Kafka broker access from application tier"
  }

  # Kafka broker SSL access
  ingress {
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Kafka broker SSL access from application tier"
  }

  # Kafka SASL/SCRAM access
  ingress {
    from_port       = 9096
    to_port         = 9096
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Kafka SASL/SCRAM access from application tier"
  }

  # Zookeeper access (for client compatibility)
  ingress {
    from_port       = 2181
    to_port         = 2181
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Zookeeper access from application tier"
  }

  # Kafka Connect access
  ingress {
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Kafka Connect API access"
  }

  # Schema Registry access
  ingress {
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = var.application_security_groups
    description     = "Schema Registry access"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "All outbound traffic"
  }

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-kafka-sg"
    Type = "Security"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# CloudWatch Log Group for Kafka logs
resource "aws_cloudwatch_log_group" "kafka" {
  name              = "/aws/msk/${var.project_name}-${var.environment}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.kafka.arn

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-kafka-logs"
    Type = "Logging"
  })
}

# MSK Configuration
resource "aws_msk_configuration" "main" {
  kafka_versions = [var.kafka_version]
  name           = "${var.project_name}-${var.environment}-kafka-config"

  server_properties = <<-PROPERTIES
    # Cluster settings
    auto.create.topics.enable=false
    default.replication.factor=${var.replication_factor}
    min.insync.replicas=${var.min_insync_replicas}
    num.partitions=${var.default_partitions}

    # Log settings
    log.retention.hours=${var.log_retention_hours}
    log.retention.bytes=${var.log_retention_bytes}
    log.segment.bytes=1073741824
    log.cleanup.policy=delete

    # Performance settings
    num.network.threads=8
    num.io.threads=16
    socket.send.buffer.bytes=102400
    socket.receive.buffer.bytes=102400
    socket.request.max.bytes=104857600
    num.replica.fetchers=4

    # Transaction settings
    transaction.state.log.replication.factor=${var.replication_factor}
    transaction.state.log.min.isr=${var.min_insync_replicas}

    # Compression
    compression.type=snappy

    # Security settings
    security.inter.broker.protocol=SSL
    ssl.client.auth=required
    
    # JMX settings for monitoring
    jmx.port=9999
  PROPERTIES

  lifecycle {
    create_before_destroy = true
  }
}

# MSK Cluster
resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.project_name}-${var.environment}-kafka"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.number_of_broker_nodes
  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.private_subnet_ids
    security_groups = [aws_security_group.kafka.id]
    
    storage_info {
      ebs_storage_info {
        volume_size = var.broker_volume_size
      }
    }
  }

  # Encryption settings
  encryption_info {
    encryption_at_rest_kms_key_id = aws_kms_key.kafka.arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  # Enhanced monitoring
  enhanced_monitoring = var.enhanced_monitoring

  # Open monitoring with Prometheus
  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  # Logging
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka.name
      }
      firehose {
        enabled = false
      }
      s3 {
        enabled = var.s3_logging_enabled
        bucket  = var.s3_logging_enabled ? var.s3_logs_bucket : null
        prefix  = var.s3_logging_enabled ? "${var.project_name}/${var.environment}/kafka-logs/" : null
      }
    }
  }

  client_authentication {
    sasl {
      scram = var.enable_scram_auth
    }
    tls {
      certificate_authority_arns = var.certificate_authority_arns
    }
  }

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-kafka-cluster"
    Type = "Messaging"
  })

  lifecycle {
    prevent_destroy = true
  }
}

# MSK Connect - for data pipeline integration
resource "aws_mskconnect_connector" "debezium" {
  count = var.enable_cdc_connector ? 1 : 0

  name = "${var.project_name}-${var.environment}-debezium-connector"

  kafkaconnect_version = "2.7.1"

  capacity {
    autoscaling {
      mcu_count    = 2
      min_worker_count = 1
      max_worker_count = 4
      scale_in_policy {
        cpu_utilization_percentage = 20
      }
      scale_out_policy {
        cpu_utilization_percentage = 80
      }
    }
  }

  connector_configuration = {
    "connector.class"              = "io.debezium.connector.postgresql.PostgresConnector"
    "database.hostname"            = var.database_hostname
    "database.port"                = "5432"
    "database.user"                = var.database_username
    "database.password"            = var.database_password
    "database.dbname"              = var.database_name
    "database.server.name"         = "${var.project_name}-${var.environment}"
    "table.include.list"           = var.cdc_table_whitelist
    "plugin.name"                  = "pgoutput"
    "slot.name"                    = "${var.project_name}_${var.environment}_debezium"
    "publication.name"             = "${var.project_name}_${var.environment}_publication"
    "tasks.max"                    = "1"
    "transforms"                   = "route"
    "transforms.route.type"        = "org.apache.kafka.connect.transforms.RegexRouter"
    "transforms.route.regex"       = "([^.]+)\\.([^.]+)\\.([^.]+)"
    "transforms.route.replacement" = "${var.project_name}-${var.environment}.$3"
  }

  kafka_cluster {
    apache_kafka_cluster {
      bootstrap_servers = aws_msk_cluster.main.bootstrap_brokers_tls
      vpc {
        security_groups = [aws_security_group.kafka.id]
        subnets         = var.private_subnet_ids
      }
    }
  }

  kafka_cluster_client_authentication {
    authentication_type = "NONE"
  }

  kafka_cluster_encryption_in_transit {
    encryption_type = "TLS"
  }

  plugin {
    custom_plugin {
      arn      = var.debezium_plugin_arn
      revision = 1
    }
  }

  service_execution_role_arn = aws_iam_role.msk_connect[0].arn

  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-debezium-connector"
    Type = "DataPipeline"
  })
}

# IAM Role for MSK Connect
resource "aws_iam_role" "msk_connect" {
  count = var.enable_cdc_connector ? 1 : 0

  name = "${var.project_name}-${var.environment}-msk-connect-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "kafkaconnect.amazonaws.com"
        }
      }
    ]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy" "msk_connect" {
  count = var.enable_cdc_connector ? 1 : 0

  name = "${var.project_name}-${var.environment}-msk-connect-policy"
  role = aws_iam_role.msk_connect[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:AlterCluster",
          "kafka-cluster:DescribeCluster"
        ]
        Resource = aws_msk_cluster.main.arn
      },
      {
        Effect = "Allow"
        Action = [
          "kafka-cluster:*Topic*",
          "kafka-cluster:WriteData",
          "kafka-cluster:ReadData"
        ]
        Resource = "${aws_msk_cluster.main.arn}/topic/*"
      },
      {
        Effect = "Allow"
        Action = [
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DescribeGroup"
        ]
        Resource = "${aws_msk_cluster.main.arn}/group/*"
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogGroups",
          "logs:DescribeLogStreams"
        ]
        Resource = "*"
      }
    ]
  })
}

# CloudWatch Alarms for monitoring
resource "aws_cloudwatch_metric_alarm" "kafka_cpu" {
  alarm_name          = "${var.project_name}-${var.environment}-kafka-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CpuUser"
  namespace           = "AWS/Kafka"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors Kafka CPU utilization"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "kafka_memory" {
  alarm_name          = "${var.project_name}-${var.environment}-kafka-memory-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "MemoryUsed"
  namespace           = "AWS/Kafka"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "This metric monitors Kafka memory utilization"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "kafka_disk" {
  alarm_name          = "${var.project_name}-${var.environment}-kafka-disk-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "KafkaDataLogsDiskUsed"
  namespace           = "AWS/Kafka"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors Kafka disk utilization"
  alarm_actions       = var.alarm_sns_topic_arn != null ? [var.alarm_sns_topic_arn] : []

  dimensions = {
    "Cluster Name" = aws_msk_cluster.main.cluster_name
  }

  tags = var.common_tags
}