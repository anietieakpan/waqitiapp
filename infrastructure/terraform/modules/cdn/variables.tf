# CloudFront CDN Module Variables

variable "environment" {
  description = "Environment name (production, staging, development)"
  type        = string

  validation {
    condition     = can(regex("^(production|staging|development)$", var.environment))
    error_message = "Environment must be production, staging, or development."
  }
}

variable "bucket_name" {
  description = "S3 bucket name for static assets"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "Bucket name must be valid S3 bucket name."
  }
}

variable "domain_name" {
  description = "Primary domain name for CloudFront (e.g., cdn.waqiti.com)"
  type        = string
}

variable "subject_alternative_names" {
  description = "Additional domain names for the certificate"
  type        = list(string)
  default     = []
}

variable "price_class" {
  description = "CloudFront price class (PriceClass_All, PriceClass_200, PriceClass_100)"
  type        = string
  default     = "PriceClass_All"

  validation {
    condition     = contains(["PriceClass_All", "PriceClass_200", "PriceClass_100"], var.price_class)
    error_message = "Price class must be PriceClass_All, PriceClass_200, or PriceClass_100."
  }
}

variable "waf_web_acl_arn" {
  description = "AWS WAF Web ACL ARN to associate with CloudFront"
  type        = string
  default     = ""
}

variable "origin_verify_secret" {
  description = "Secret value for X-Origin-Verify header"
  type        = string
  sensitive   = true
}

variable "lambda_edge_security_headers_arn" {
  description = "Lambda@Edge function ARN for security headers"
  type        = string
  default     = ""
}

variable "geo_restriction_type" {
  description = "Geo restriction type (none, whitelist, blacklist)"
  type        = string
  default     = "none"

  validation {
    condition     = contains(["none", "whitelist", "blacklist"], var.geo_restriction_type)
    error_message = "Geo restriction type must be none, whitelist, or blacklist."
  }
}

variable "geo_restriction_locations" {
  description = "List of country codes for geo restriction"
  type        = list(string)
  default     = []
}

variable "logging_bucket" {
  description = "S3 bucket for CloudFront access logs"
  type        = string
}

variable "create_dns_record" {
  description = "Whether to create Route53 DNS record"
  type        = bool
  default     = true
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID for DNS record"
  type        = string
  default     = ""
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic ARN for CloudWatch alarms"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {}
}
