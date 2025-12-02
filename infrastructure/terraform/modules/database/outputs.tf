# Database Module Outputs

output "primary_db_instance_id" {
  description = "RDS instance ID of the primary database"
  value       = aws_db_instance.primary.id
}

output "primary_db_instance_arn" {
  description = "RDS instance ARN of the primary database"
  value       = aws_db_instance.primary.arn
}

output "primary_db_endpoint" {
  description = "RDS instance endpoint of the primary database"
  value       = aws_db_instance.primary.endpoint
}

output "primary_db_port" {
  description = "RDS instance port of the primary database"
  value       = aws_db_instance.primary.port
}

output "primary_db_name" {
  description = "Database name"
  value       = aws_db_instance.primary.db_name
}

output "primary_db_username" {
  description = "Database master username"
  value       = aws_db_instance.primary.username
  sensitive   = true
}

output "read_replica_endpoints" {
  description = "List of read replica endpoints"
  value       = aws_db_instance.read_replica[*].endpoint
}

output "read_replica_instance_ids" {
  description = "List of read replica instance IDs"
  value       = aws_db_instance.read_replica[*].id
}

output "database_security_group_id" {
  description = "Security group ID for the database"
  value       = aws_security_group.database.id
}

output "database_subnet_group_name" {
  description = "Database subnet group name"
  value       = aws_db_subnet_group.main.name
}

output "database_parameter_group_name" {
  description = "Database parameter group name"
  value       = aws_db_parameter_group.main.name
}

output "database_kms_key_id" {
  description = "KMS key ID used for database encryption"
  value       = aws_kms_key.database.key_id
}

output "database_kms_key_arn" {
  description = "KMS key ARN used for database encryption"
  value       = aws_kms_key.database.arn
}

output "secrets_manager_secret_arn" {
  description = "Secrets Manager secret ARN containing database credentials"
  value       = aws_secretsmanager_secret.database_credentials.arn
}

output "secrets_manager_secret_name" {
  description = "Secrets Manager secret name containing database credentials"
  value       = aws_secretsmanager_secret.database_credentials.name
}

output "monitoring_role_arn" {
  description = "IAM role ARN for RDS enhanced monitoring"
  value       = aws_iam_role.rds_monitoring.arn
}

# Connection string for applications
output "database_connection_string" {
  description = "Database connection string for applications"
  value       = "postgresql://${aws_db_instance.primary.username}:${random_password.master_password.result}@${aws_db_instance.primary.endpoint}:${aws_db_instance.primary.port}/${aws_db_instance.primary.db_name}"
  sensitive   = true
}

# JDBC URL
output "database_jdbc_url" {
  description = "JDBC URL for the database"
  value       = "jdbc:postgresql://${aws_db_instance.primary.endpoint}:${aws_db_instance.primary.port}/${aws_db_instance.primary.db_name}"
}

# Read replica connection strings
output "read_replica_connection_strings" {
  description = "Read replica connection strings"
  value = [
    for replica in aws_db_instance.read_replica :
    "postgresql://${aws_db_instance.primary.username}:${random_password.master_password.result}@${replica.endpoint}:${replica.port}/${aws_db_instance.primary.db_name}"
  ]
  sensitive = true
}

# Database backup information
output "backup_retention_period" {
  description = "Database backup retention period"
  value       = aws_db_instance.primary.backup_retention_period
}

output "backup_window" {
  description = "Database backup window"
  value       = aws_db_instance.primary.backup_window
}

output "maintenance_window" {
  description = "Database maintenance window"
  value       = aws_db_instance.primary.maintenance_window
}