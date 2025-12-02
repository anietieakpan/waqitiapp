# CloudFront CDN Module Outputs

output "distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.cdn.id
}

output "distribution_arn" {
  description = "CloudFront distribution ARN"
  value       = aws_cloudfront_distribution.cdn.arn
}

output "distribution_domain_name" {
  description = "CloudFront distribution domain name"
  value       = aws_cloudfront_distribution.cdn.domain_name
}

output "distribution_hosted_zone_id" {
  description = "CloudFront distribution hosted zone ID"
  value       = aws_cloudfront_distribution.cdn.hosted_zone_id
}

output "s3_bucket_name" {
  description = "S3 bucket name for static assets"
  value       = aws_s3_bucket.static_assets.id
}

output "s3_bucket_arn" {
  description = "S3 bucket ARN"
  value       = aws_s3_bucket.static_assets.arn
}

output "s3_bucket_domain_name" {
  description = "S3 bucket regional domain name"
  value       = aws_s3_bucket.static_assets.bucket_regional_domain_name
}

output "oai_iam_arn" {
  description = "CloudFront Origin Access Identity IAM ARN"
  value       = aws_cloudfront_origin_access_identity.oai.iam_arn
}

output "acm_certificate_arn" {
  description = "ACM certificate ARN"
  value       = aws_acm_certificate.cdn.arn
}

output "cdn_url" {
  description = "Full CDN URL"
  value       = "https://${var.domain_name}"
}

output "cloudwatch_alarm_4xx_name" {
  description = "CloudWatch alarm name for 4xx errors"
  value       = aws_cloudwatch_metric_alarm.cdn_4xx_error_rate.alarm_name
}

output "cloudwatch_alarm_5xx_name" {
  description = "CloudWatch alarm name for 5xx errors"
  value       = aws_cloudwatch_metric_alarm.cdn_5xx_error_rate.alarm_name
}
