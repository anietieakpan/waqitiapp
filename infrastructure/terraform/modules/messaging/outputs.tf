# Messaging Module Outputs

output "kafka_cluster_arn" {
  description = "Amazon MSK cluster ARN"
  value       = aws_msk_cluster.main.arn
}

output "kafka_cluster_name" {
  description = "Amazon MSK cluster name"
  value       = aws_msk_cluster.main.cluster_name
}

output "kafka_version" {
  description = "Apache Kafka version"
  value       = aws_msk_cluster.main.kafka_version
}

output "kafka_bootstrap_brokers" {
  description = "Plaintext connection host:port pairs"
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "kafka_bootstrap_brokers_tls" {
  description = "TLS connection host:port pairs"
  value       = aws_msk_cluster.main.bootstrap_brokers_tls
}

output "kafka_bootstrap_brokers_sasl_scram" {
  description = "SASL/SCRAM connection host:port pairs"
  value       = aws_msk_cluster.main.bootstrap_brokers_sasl_scram
}

output "kafka_zookeeper_connect_string" {
  description = "Apache Zookeeper connection string"
  value       = aws_msk_cluster.main.zookeeper_connect_string
}

output "kafka_security_group_id" {
  description = "Security group ID for Kafka"
  value       = aws_security_group.kafka.id
}

output "kafka_configuration_arn" {
  description = "Amazon MSK configuration ARN"
  value       = aws_msk_configuration.main.arn
}

output "kafka_configuration_revision" {
  description = "Amazon MSK configuration revision"
  value       = aws_msk_configuration.main.latest_revision
}

output "kafka_kms_key_id" {
  description = "KMS key ID used for Kafka encryption"
  value       = aws_kms_key.kafka.key_id
}

output "kafka_kms_key_arn" {
  description = "KMS key ARN used for Kafka encryption"
  value       = aws_kms_key.kafka.arn
}

output "kafka_log_group_name" {
  description = "CloudWatch log group name for Kafka logs"
  value       = aws_cloudwatch_log_group.kafka.name
}

output "kafka_log_group_arn" {
  description = "CloudWatch log group ARN for Kafka logs"
  value       = aws_cloudwatch_log_group.kafka.arn
}

# CDC Connector outputs
output "cdc_connector_arn" {
  description = "MSK Connect connector ARN for CDC"
  value       = var.enable_cdc_connector ? aws_mskconnect_connector.debezium[0].arn : null
}

output "cdc_connector_name" {
  description = "MSK Connect connector name for CDC"
  value       = var.enable_cdc_connector ? aws_mskconnect_connector.debezium[0].name : null
}

output "msk_connect_role_arn" {
  description = "IAM role ARN for MSK Connect"
  value       = var.enable_cdc_connector ? aws_iam_role.msk_connect[0].arn : null
}

# Configuration information for applications
output "kafka_broker_configuration" {
  description = "Kafka broker configuration details"
  value = {
    cluster_arn               = aws_msk_cluster.main.arn
    bootstrap_brokers         = aws_msk_cluster.main.bootstrap_brokers_tls
    security_protocol         = "SSL"
    ssl_ca_location          = "/opt/kafka/config/ca-cert"
    client_authentication    = var.enable_scram_auth ? "SASL_SSL" : "SSL"
    compression_type         = "snappy"
    acks                     = "all"
    retries                  = 3
    enable_idempotence       = true
    max_in_flight_requests   = 5
    batch_size               = 16384
    linger_ms                = 5
    buffer_memory            = 33554432
  }
}

# Connection details for Spring Boot applications
output "spring_kafka_properties" {
  description = "Kafka properties for Spring Boot applications"
  value = {
    "spring.kafka.bootstrap-servers"                           = aws_msk_cluster.main.bootstrap_brokers_tls
    "spring.kafka.security.protocol"                          = "SSL"
    "spring.kafka.ssl.trust-store-location"                   = "/opt/kafka/config/kafka.client.truststore.jks"
    "spring.kafka.ssl.trust-store-password"                   = "changeit"
    "spring.kafka.producer.acks"                              = "all"
    "spring.kafka.producer.retries"                           = "3"
    "spring.kafka.producer.properties.enable.idempotence"     = "true"
    "spring.kafka.producer.properties.max.in.flight.requests.per.connection" = "5"
    "spring.kafka.producer.compression-type"                  = "snappy"
    "spring.kafka.consumer.group-id"                          = "waqiti-service-group"
    "spring.kafka.consumer.auto-offset-reset"                 = "earliest"
    "spring.kafka.consumer.properties.spring.json.trusted.packages" = "com.waqiti"
  }
}

# Topic configurations for application setup
output "recommended_topics" {
  description = "Recommended Kafka topics for the banking platform"
  value = {
    "account-events" = {
      partitions         = var.default_partitions
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = var.log_retention_hours * 3600000
    }
    "transaction-events" = {
      partitions         = var.default_partitions * 2  # Higher volume
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = var.log_retention_hours * 3600000
    }
    "payment-events" = {
      partitions         = var.default_partitions
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = var.log_retention_hours * 3600000
    }
    "notification-events" = {
      partitions         = var.default_partitions
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = 86400000  # 24 hours for notifications
    }
    "audit-events" = {
      partitions         = var.default_partitions
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = 2592000000  # 30 days for audit
    }
    "compliance-events" = {
      partitions         = var.default_partitions
      replication_factor = var.replication_factor
      cleanup_policy     = "delete"
      retention_ms       = 31536000000  # 1 year for compliance
    }
  }
}

# Cluster metrics endpoints
output "kafka_cluster_metrics" {
  description = "Kafka cluster metrics information"
  value = {
    prometheus_jmx_exporter_port = 11001
    prometheus_node_exporter_port = 11002
    cloudwatch_log_group = aws_cloudwatch_log_group.kafka.name
    enhanced_monitoring_level = var.enhanced_monitoring
  }
}