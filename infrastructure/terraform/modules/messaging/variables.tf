# Messaging Module Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the Kafka cluster will be created"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for the Kafka cluster"
  type        = list(string)
}

variable "application_security_groups" {
  description = "List of security group IDs that should have access to Kafka"
  type        = list(string)
}

variable "kafka_version" {
  description = "Apache Kafka version"
  type        = string
  default     = "2.8.1"
}

variable "broker_instance_type" {
  description = "Instance type for Kafka brokers"
  type        = string
  default     = "kafka.m5.large"
}

variable "number_of_broker_nodes" {
  description = "Number of broker nodes in the cluster"
  type        = number
  default     = 3
}

variable "broker_volume_size" {
  description = "Size of the EBS volume for each broker (GB)"
  type        = number
  default     = 100
}

variable "replication_factor" {
  description = "Default replication factor for topics"
  type        = number
  default     = 3
}

variable "min_insync_replicas" {
  description = "Minimum number of in-sync replicas"
  type        = number
  default     = 2
}

variable "default_partitions" {
  description = "Default number of partitions for new topics"
  type        = number
  default     = 6
}

variable "log_retention_hours" {
  description = "Log retention time in hours"
  type        = number
  default     = 168  # 7 days
}

variable "log_retention_bytes" {
  description = "Log retention size in bytes (-1 for unlimited)"
  type        = number
  default     = -1
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "enhanced_monitoring" {
  description = "Enhanced monitoring level (PER_BROKER, PER_TOPIC_PER_BROKER, PER_TOPIC_PER_PARTITION)"
  type        = string
  default     = "PER_BROKER"
}

variable "enable_scram_auth" {
  description = "Enable SASL/SCRAM authentication"
  type        = bool
  default     = true
}

variable "certificate_authority_arns" {
  description = "List of ACM Certificate Authority ARNs for TLS client authentication"
  type        = list(string)
  default     = []
}

variable "s3_logging_enabled" {
  description = "Enable S3 logging for broker logs"
  type        = bool
  default     = false
}

variable "s3_logs_bucket" {
  description = "S3 bucket for storing broker logs"
  type        = string
  default     = null
}

variable "enable_cdc_connector" {
  description = "Enable CDC connector for database change streams"
  type        = bool
  default     = false
}

variable "database_hostname" {
  description = "Database hostname for CDC connector"
  type        = string
  default     = ""
}

variable "database_username" {
  description = "Database username for CDC connector"
  type        = string
  default     = ""
}

variable "database_password" {
  description = "Database password for CDC connector"
  type        = string
  default     = ""
  sensitive   = true
}

variable "database_name" {
  description = "Database name for CDC connector"
  type        = string
  default     = ""
}

variable "cdc_table_whitelist" {
  description = "Comma-separated list of tables to include in CDC"
  type        = string
  default     = "public.transactions,public.accounts,public.ledger_entries"
}

variable "debezium_plugin_arn" {
  description = "ARN of the Debezium connector plugin"
  type        = string
  default     = ""
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic ARN for CloudWatch alarms"
  type        = string
  default     = null
}

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {}
}