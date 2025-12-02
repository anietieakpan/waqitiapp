# CloudFront CDN Module for Waqiti Platform
# Description: Global content delivery network for static assets
# Features: S3 origin, HTTPS only, custom domain, WAF integration

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# S3 Bucket for static assets
resource "aws_s3_bucket" "static_assets" {
  bucket = var.bucket_name

  tags = merge(
    var.tags,
    {
      Name        = "example-static-assets"
      Environment = var.environment
      Purpose     = "CloudFront Origin"
    }
  )
}

# S3 Bucket versioning
resource "aws_s3_bucket_versioning" "static_assets" {
  bucket = aws_s3_bucket.static_assets.id

  versioning_configuration {
    status = "Enabled"
  }
}

# S3 Bucket encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "static_assets" {
  bucket = aws_s3_bucket.static_assets.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# S3 Bucket public access block
resource "aws_s3_bucket_public_access_block" "static_assets" {
  bucket = aws_s3_bucket.static_assets.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# CloudFront Origin Access Identity
resource "aws_cloudfront_origin_access_identity" "oai" {
  comment = "OAI for ${var.bucket_name}"
}

# S3 Bucket Policy for CloudFront
resource "aws_s3_bucket_policy" "static_assets" {
  bucket = aws_s3_bucket.static_assets.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontOAI"
        Effect = "Allow"
        Principal = {
          AWS = aws_cloudfront_origin_access_identity.oai.iam_arn
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.static_assets.arn}/*"
      }
    ]
  })
}

# ACM Certificate for custom domain (must be in us-east-1)
resource "aws_acm_certificate" "cdn" {
  provider = aws.us-east-1  # CloudFront requires certificates in us-east-1

  domain_name               = var.domain_name
  subject_alternative_names = var.subject_alternative_names
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(
    var.tags,
    {
      Name        = "waqiti-cdn-certificate"
      Environment = var.environment
    }
  )
}

# CloudFront Distribution
resource "aws_cloudfront_distribution" "cdn" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "Waqiti ${var.environment} CDN"
  default_root_object = "index.html"
  price_class         = var.price_class
  aliases             = concat([var.domain_name], var.subject_alternative_names)
  web_acl_id          = var.waf_web_acl_arn

  # S3 Origin
  origin {
    domain_name = aws_s3_bucket.static_assets.bucket_regional_domain_name
    origin_id   = "S3-${aws_s3_bucket.static_assets.id}"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.oai.cloudfront_access_identity_path
    }

    # Custom headers for origin
    custom_header {
      name  = "X-Origin-Verify"
      value = var.origin_verify_secret
    }
  }

  # Default cache behavior
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.static_assets.id}"

    forwarded_values {
      query_string = false
      headers      = ["Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"]

      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400   # 1 day
    max_ttl                = 31536000 # 1 year
    compress               = true

    # Security headers via Lambda@Edge (if configured)
    dynamic "lambda_function_association" {
      for_each = var.lambda_edge_security_headers_arn != "" ? [1] : []
      content {
        event_type   = "origin-response"
        lambda_arn   = var.lambda_edge_security_headers_arn
        include_body = false
      }
    }
  }

  # Ordered cache behaviors for different asset types

  # Images - longer cache
  ordered_cache_behavior {
    path_pattern     = "/images/*"
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.static_assets.id}"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl                = 0
    default_ttl            = 2592000  # 30 days
    max_ttl                = 31536000 # 1 year
    compress               = true
    viewer_protocol_policy = "redirect-to-https"
  }

  # CSS/JS - moderate cache with query string for versioning
  ordered_cache_behavior {
    path_pattern     = "/static/*"
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.static_assets.id}"

    forwarded_values {
      query_string = true  # For cache busting
      cookies {
        forward = "none"
      }
    }

    min_ttl                = 0
    default_ttl            = 604800   # 7 days
    max_ttl                = 31536000 # 1 year
    compress               = true
    viewer_protocol_policy = "redirect-to-https"
  }

  # API requests - no cache
  ordered_cache_behavior {
    path_pattern     = "/api/*"
    allowed_methods  = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.static_assets.id}"

    forwarded_values {
      query_string = true
      headers      = ["*"]

      cookies {
        forward = "all"
      }
    }

    min_ttl                = 0
    default_ttl            = 0
    max_ttl                = 0
    compress               = false
    viewer_protocol_policy = "redirect-to-https"
  }

  # Custom error responses
  custom_error_response {
    error_code            = 403
    response_code         = 404
    response_page_path    = "/404.html"
    error_caching_min_ttl = 300
  }

  custom_error_response {
    error_code            = 404
    response_code         = 404
    response_page_path    = "/404.html"
    error_caching_min_ttl = 300
  }

  # SSL/TLS Configuration
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.cdn.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  # Restrictions
  restrictions {
    geo_restriction {
      restriction_type = var.geo_restriction_type
      locations        = var.geo_restriction_locations
    }
  }

  # Logging
  logging_config {
    include_cookies = false
    bucket          = var.logging_bucket
    prefix          = "cloudfront/${var.environment}/"
  }

  tags = merge(
    var.tags,
    {
      Name        = "waqiti-cdn-${var.environment}"
      Environment = var.environment
    }
  )
}

# Route53 DNS Record
resource "aws_route53_record" "cdn" {
  count   = var.create_dns_record ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.cdn.domain_name
    zone_id                = aws_cloudfront_distribution.cdn.hosted_zone_id
    evaluate_target_health = false
  }
}

# CloudWatch Alarms for monitoring

# 4xx Error Rate Alarm
resource "aws_cloudwatch_metric_alarm" "cdn_4xx_error_rate" {
  alarm_name          = "waqiti-cdn-${var.environment}-4xx-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "4xxErrorRate"
  namespace           = "AWS/CloudFront"
  period              = 300
  statistic           = "Average"
  threshold           = 5  # 5% error rate
  alarm_description   = "CDN 4xx error rate exceeds 5%"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DistributionId = aws_cloudfront_distribution.cdn.id
  }

  alarm_actions = var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
}

# 5xx Error Rate Alarm
resource "aws_cloudfront_metric_alarm" "cdn_5xx_error_rate" {
  alarm_name          = "waqiti-cdn-${var.environment}-5xx-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "5xxErrorRate"
  namespace           = "AWS/CloudFront"
  period              = 300
  statistic           = "Average"
  threshold           = 1  # 1% error rate
  alarm_description   = "CDN 5xx error rate exceeds 1%"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DistributionId = aws_cloudfront_distribution.cdn.id
  }

  alarm_actions = var.alarm_sns_topic_arn != "" ? [var.alarm_sns_topic_arn] : []
}

# Outputs
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

output "s3_bucket_name" {
  description = "S3 bucket name for static assets"
  value       = aws_s3_bucket.static_assets.id
}

output "s3_bucket_arn" {
  description = "S3 bucket ARN"
  value       = aws_s3_bucket.static_assets.arn
}

output "cdn_url" {
  description = "Full CDN URL"
  value       = "https://${var.domain_name}"
}
