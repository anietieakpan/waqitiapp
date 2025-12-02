# CloudFront CDN Configuration for Production Environment

# Provider configuration for us-east-1 (required for ACM certificates)
provider "aws" {
  alias  = "us-east-1"
  region = "us-east-1"
}

# S3 bucket for CloudFront logs
resource "aws_s3_bucket" "cdn_logs" {
  bucket = "example-cdn-logs-${var.environment}"

  tags = {
    Name        = "example-cdn-logs"
    Environment = var.environment
    Purpose     = "CloudFront Access Logs"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "cdn_logs" {
  bucket = aws_s3_bucket.cdn_logs.id

  rule {
    id     = "delete-old-logs"
    status = "Enabled"

    expiration {
      days = 90
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 60
      storage_class = "GLACIER"
    }
  }
}

# Main CDN module
module "cdn" {
  source = "../../modules/cdn"

  environment                     = var.environment
  bucket_name                     = "example-static-assets-${var.environment}"
  domain_name                     = "cdn.example.com"
  subject_alternative_names       = ["static.example.com", "assets.example.com"]
  price_class                     = "PriceClass_All"
  waf_web_acl_arn                 = aws_wafv2_web_acl.cdn.arn
  origin_verify_secret            = var.cdn_origin_verify_secret
  lambda_edge_security_headers_arn = aws_lambda_function.cdn_security_headers.qualified_arn
  logging_bucket                  = "${aws_s3_bucket.cdn_logs.bucket}.s3.amazonaws.com"
  create_dns_record               = true
  route53_zone_id                 = data.aws_route53_zone.main.zone_id
  alarm_sns_topic_arn             = aws_sns_topic.cloudwatch_alarms.arn

  # Geo-restriction (optional - restrict high-risk countries)
  geo_restriction_type = "blacklist"
  geo_restriction_locations = [
    "KP", # North Korea
    "IR", # Iran
    "SY", # Syria
  ]

  tags = {
    Project     = "Waqiti"
    Environment = var.environment
    ManagedBy   = "Terraform"
    CostCenter  = "Infrastructure"
  }

  providers = {
    aws.us-east-1 = aws.us-east-1
  }
}

# Lambda@Edge for security headers
resource "aws_lambda_function" "cdn_security_headers" {
  provider         = aws.us-east-1  # Lambda@Edge must be in us-east-1
  filename         = "${path.module}/lambda/security-headers.zip"
  function_name    = "example-cdn-security-headers-${var.environment}"
  role             = aws_iam_role.lambda_edge.arn
  handler          = "index.handler"
  source_code_hash = filebase64sha256("${path.module}/lambda/security-headers.zip")
  runtime          = "nodejs18.x"
  publish          = true

  tags = {
    Name        = "example-cdn-security-headers"
    Environment = var.environment
  }
}

# IAM role for Lambda@Edge
resource "aws_iam_role" "lambda_edge" {
  name = "example-lambda-edge-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = [
            "lambda.amazonaws.com",
            "edgelambda.amazonaws.com"
          ]
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_edge" {
  role       = aws_iam_role.lambda_edge.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Route53 hosted zone data source
data "aws_route53_zone" "main" {
  name = "example.com"
}

# SNS topic for alarms
resource "aws_sns_topic" "cloudwatch_alarms" {
  name = "example-cloudwatch-alarms-${var.environment}"

  tags = {
    Environment = var.environment
  }
}

# Outputs
output "cdn_url" {
  description = "CloudFront CDN URL"
  value       = module.cdn.cdn_url
}

output "cdn_distribution_id" {
  description = "CloudFront distribution ID"
  value       = module.cdn.distribution_id
}

output "cdn_s3_bucket" {
  description = "S3 bucket for static assets"
  value       = module.cdn.s3_bucket_name
}
