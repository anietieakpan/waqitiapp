# Database Module Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the database will be created"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for the database"
  type        = list(string)
}

variable "application_security_groups" {
  description = "List of security group IDs that should have access to the database"
  type        = list(string)
}

variable "postgres_version" {
  description = "PostgreSQL version"
  type        = string
  default     = "15.4"
}

variable "instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.medium"
}

variable "allocated_storage" {
  description = "Initial allocated storage in GB"
  type        = number
  default     = 100
}

variable "max_allocated_storage" {
  description = "Maximum allocated storage in GB for autoscaling"
  type        = number
  default     = 1000
}

variable "database_name" {
  description = "Name of the default database"
  type        = string
  default     = "waqiti"
}

variable "master_username" {
  description = "Master username for the database"
  type        = string
  default     = "postgres"
}

variable "max_connections" {
  description = "Maximum number of database connections"
  type        = string
  default     = "200"
}

variable "backup_retention_period" {
  description = "Number of days to retain automated backups"
  type        = number
  default     = 7
}

variable "multi_az" {
  description = "Enable Multi-AZ deployment for high availability"
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Skip final snapshot when deleting"
  type        = bool
  default     = false
}

variable "create_read_replica" {
  description = "Create read replica"
  type        = bool
  default     = true
}

variable "read_replica_count" {
  description = "Number of read replicas to create"
  type        = number
  default     = 1
}

variable "replica_instance_class" {
  description = "Instance class for read replicas"
  type        = string
  default     = "db.t3.medium"
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

variable "s3_prefix_list_id" {
  description = "Prefix list ID for S3 VPC endpoint"
  type        = string
}

variable "kms_prefix_list_id" {
  description = "Prefix list ID for KMS VPC endpoint"
  type        = string
}

variable "application_subnet_cidrs" {
  description = "CIDR blocks of application subnets for egress rules"
  type        = list(string)
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC for DNS resolution"
  type        = string
}