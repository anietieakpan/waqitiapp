# Waqiti Financial Platform - Least Privilege IAM Policies
# Comprehensive IAM policies following the principle of least privilege for all services

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Variables for resource naming and tagging
variable "environment" {
  description = "Environment name"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "region" {
  description = "AWS region"
  type        = string
}

variable "account_id" {
  description = "AWS account ID"
  type        = string
}

variable "tags" {
  description = "Common tags"
  type        = map(string)
  default     = {}
}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ============================================================================
# 1. PAYMENT SERVICE IAM POLICIES - Highly Restrictive for Financial Operations
# ============================================================================

resource "aws_iam_policy" "payment_service" {
  name        = "waqiti-payment-service-${var.environment}"
  description = "Least privilege policy for payment service operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # KMS permissions for payment encryption (very specific)
      {
        Effect = "Allow"
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey"
        ]
        Resource = [
          "arn:aws:kms:${var.region}:${var.account_id}:key/payment-encryption-key-${var.environment}"
        ]
        Condition = {
          StringEquals = {
            "kms:ViaService" = "s3.${var.region}.amazonaws.com"
          }
          StringLike = {
            "kms:EncryptionContext:service" = "waqiti-payment"
          }
        }
      },
      # S3 permissions for payment documents (very restrictive)
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject"
        ]
        Resource = [
          "arn:aws:s3:::waqiti-payment-documents-${var.environment}/payments/*",
          "arn:aws:s3:::waqiti-payment-documents-${var.environment}/receipts/*"
        ]
        Condition = {
          StringEquals = {
            "s3:ExistingObjectTag/service" = "payment"
            "s3:ExistingObjectTag/environment" = var.environment
          }
          IpAddress = {
            "aws:SourceIp" = [
              "10.0.0.0/8",  # VPC CIDR
              "172.16.0.0/12" # Private networks only
            ]
          }
        }
      },
      # CloudWatch metrics (payment-specific)
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "Waqiti/Payments"
          }
        }
      },
      # SES for payment notifications (specific templates)
      {
        Effect = "Allow"
        Action = [
          "ses:SendEmail",
          "ses:SendRawEmail"
        ]
        Resource = [
          "arn:aws:ses:${var.region}:${var.account_id}:identity/notifications@example.com",
          "arn:aws:ses:${var.region}:${var.account_id}:template/payment-confirmation",
          "arn:aws:ses:${var.region}:${var.account_id}:template/payment-failed"
        ]
        Condition = {
          StringLike = {
            "ses:Tags/service" = "payment"
          }
        }
      },
      # Parameter Store for payment configuration (read-only)
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = [
          "arn:aws:ssm:${var.region}:${var.account_id}:parameter/waqiti/${var.environment}/payment/*"
        ]
        Condition = {
          StringEquals = {
            "ssm:Recursive" = "false"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Service     = "payment"
    Component   = "iam"
    Compliance  = "pci-dss"
    DataClassification = "restricted"
  })
}

# ============================================================================
# 2. WALLET SERVICE IAM POLICIES - Balance and Account Management
# ============================================================================

resource "aws_iam_policy" "wallet_service" {
  name        = "waqiti-wallet-service-${var.environment}"
  description = "Least privilege policy for wallet service operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # DynamoDB permissions for wallet data (specific table and indexes)
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
          "dynamodb:BatchGetItem"
        ]
        Resource = [
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/waqiti-wallets-${var.environment}",
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/waqiti-wallets-${var.environment}/index/*"
        ]
        Condition = {
          StringEquals = {
            "dynamodb:LeadingKeys" = ["USER#*", "WALLET#*"]
          }
          ForAllValues:StringEquals = {
            "dynamodb:Attributes" = [
              "user_id", "wallet_id", "balance", "currency", 
              "status", "created_at", "updated_at"
            ]
          }
        }
      },
      # KMS for wallet encryption
      {
        Effect = "Allow"
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey"
        ]
        Resource = [
          "arn:aws:kms:${var.region}:${var.account_id}:key/wallet-encryption-key-${var.environment}"
        ]
        Condition = {
          StringEquals = {
            "kms:EncryptionContext:service" = "waqiti-wallet"
          }
        }
      },
      # CloudWatch metrics for wallet operations
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "Waqiti/Wallets"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Service    = "wallet"
    Component  = "iam"
    DataClassification = "confidential"
  })
}

# ============================================================================
# 3. USER SERVICE IAM POLICIES - Identity and Authentication
# ============================================================================

resource "aws_iam_policy" "user_service" {
  name        = "waqiti-user-service-${var.environment}"
  description = "Least privilege policy for user service operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Cognito User Pool management (specific pool)
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:AdminGetUser",
          "cognito-idp:AdminCreateUser",
          "cognito-idp:AdminUpdateUserAttributes",
          "cognito-idp:AdminSetUserPassword",
          "cognito-idp:AdminInitiateAuth"
        ]
        Resource = [
          "arn:aws:cognito-idp:${var.region}:${var.account_id}:userpool/waqiti-users-${var.environment}"
        ]
        Condition = {
          StringEquals = {
            "cognito-idp:ClientId" = "waqiti-client-${var.environment}"
          }
        }
      },
      # S3 for user documents (KYC, profile pictures)
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = [
          "arn:aws:s3:::waqiti-user-documents-${var.environment}/profile/*",
          "arn:aws:s3:::waqiti-user-documents-${var.environment}/kyc/*"
        ]
        Condition = {
          StringEquals = {
            "s3:ExistingObjectTag/service" = "user"
          }
          StringLike = {
            "s3:ExistingObjectTag/user-id" = "*"
          }
        }
      },
      # SES for user communications
      {
        Effect = "Allow"
        Action = [
          "ses:SendEmail"
        ]
        Resource = [
          "arn:aws:ses:${var.region}:${var.account_id}:identity/support@example.com",
          "arn:aws:ses:${var.region}:${var.account_id}:template/welcome",
          "arn:aws:ses:${var.region}:${var.account_id}:template/verification"
        ]
      }
    ]
  })

  tags = merge(var.tags, {
    Service    = "user"
    Component  = "iam"
    DataClassification = "confidential"
  })
}

# ============================================================================
# 4. COMPLIANCE SERVICE IAM POLICIES - Audit and Regulatory
# ============================================================================

resource "aws_iam_policy" "compliance_service" {
  name        = "waqiti-compliance-service-${var.environment}"
  description = "Least privilege policy for compliance and audit operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # S3 for compliance reports and audit logs
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::waqiti-compliance-${var.environment}",
          "arn:aws:s3:::waqiti-compliance-${var.environment}/audit-logs/*",
          "arn:aws:s3:::waqiti-compliance-${var.environment}/reports/*"
        ]
        Condition = {
          StringEquals = {
            "s3:x-amz-server-side-encryption" = "AES256"
          }
          Bool = {
            "aws:SecureTransport" = "true"
          }
        }
      },
      # CloudTrail read access for audit
      {
        Effect = "Allow"
        Action = [
          "cloudtrail:LookupEvents"
        ]
        Resource = "*"
        Condition = {
          DateGreaterThan = {
            "aws:CurrentTime" = "2024-01-01T00:00:00Z"
          }
          StringEquals = {
            "cloudtrail:EventName" = [
              "AssumeRole", "CreateUser", "DeleteUser", 
              "PutBucketPolicy", "DeleteBucketPolicy"
            ]
          }
        }
      },
      # Config for compliance monitoring
      {
        Effect = "Allow"
        Action = [
          "config:GetComplianceDetailsByConfigRule",
          "config:GetComplianceSummaryByConfigRule",
          "config:DescribeConfigRules"
        ]
        Resource = [
          "arn:aws:config:${var.region}:${var.account_id}:config-rule/waqiti-*"
        ]
      }
    ]
  })

  tags = merge(var.tags, {
    Service    = "compliance"
    Component  = "iam"
    Compliance = "sox-pci-gdpr"
    DataClassification = "restricted"
  })
}

# ============================================================================
# 5. FRAUD DETECTION SERVICE IAM POLICIES - Security and Risk
# ============================================================================

resource "aws_iam_policy" "fraud_detection_service" {
  name        = "waqiti-fraud-detection-service-${var.environment}"
  description = "Least privilege policy for fraud detection operations"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # SageMaker inference for fraud models
      {
        Effect = "Allow"
        Action = [
          "sagemaker:InvokeEndpoint"
        ]
        Resource = [
          "arn:aws:sagemaker:${var.region}:${var.account_id}:endpoint/waqiti-fraud-model-${var.environment}"
        ]
        Condition = {
          StringEquals = {
            "sagemaker:TargetModel" = "fraud-detection-v1"
          }
        }
      },
      # DynamoDB for fraud patterns and rules
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:Query",
          "dynamodb:PutItem"
        ]
        Resource = [
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/waqiti-fraud-patterns-${var.environment}",
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/waqiti-risk-scores-${var.environment}"
        ]
      },
      # SNS for fraud alerts (specific topic)
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = [
          "arn:aws:sns:${var.region}:${var.account_id}:waqiti-fraud-alerts-${var.environment}"
        ]
        Condition = {
          StringEquals = {
            "sns:MessageAttributes/severity" = ["high", "critical"]
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Service    = "fraud-detection"
    Component  = "iam"
    SecurityLevel = "critical"
  })
}

# ============================================================================
# 6. MONITORING SERVICE IAM POLICIES - Observability
# ============================================================================

resource "aws_iam_policy" "monitoring_service" {
  name        = "waqiti-monitoring-service-${var.environment}"
  description = "Least privilege policy for monitoring and observability"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # CloudWatch read-only access for metrics
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:GetMetricData",
          "cloudwatch:ListMetrics"
        ]
        Resource = "*"
        Condition = {
          StringLike = {
            "cloudwatch:namespace" = "Waqiti/*"
          }
        }
      },
      # CloudWatch Logs for log aggregation
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = [
          "arn:aws:logs:${var.region}:${var.account_id}:log-group:/waqiti/${var.environment}/*"
        ]
      },
      # X-Ray tracing
      {
        Effect = "Allow"
        Action = [
          "xray:PutTraceSegments",
          "xray:PutTelemetryRecords",
          "xray:GetSamplingRules",
          "xray:GetSamplingTargets"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "xray:TraceId" = "*"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Service    = "monitoring"
    Component  = "iam"
  })
}

# ============================================================================
# 7. EKS NODE GROUP IAM POLICIES - Container Runtime
# ============================================================================

resource "aws_iam_role" "eks_node_group" {
  name = "waqiti-eks-node-group-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = var.tags
}

# Attach minimal required policies for EKS nodes
resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node_group.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_node_group.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry_readonly" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_node_group.name
}

# Custom policy for CloudWatch Container Insights (minimal)
resource "aws_iam_policy" "eks_cloudwatch_container_insights" {
  name        = "waqiti-eks-cloudwatch-container-insights-${var.environment}"
  description = "Minimal CloudWatch Container Insights permissions for EKS nodes"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData",
          "logs:PutLogEvents",
          "logs:CreateLogGroup",
          "logs:CreateLogStream"
        ]
        Resource = [
          "arn:aws:logs:${var.region}:${var.account_id}:*",
          "arn:aws:cloudwatch:${var.region}:${var.account_id}:*"
        ]
        Condition = {
          StringLike = {
            "cloudwatch:namespace" = [
              "ContainerInsights",
              "AWS/EKS"
            ]
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cloudwatch_container_insights" {
  policy_arn = aws_iam_policy.eks_cloudwatch_container_insights.arn
  role       = aws_iam_role.eks_node_group.name
}

# ============================================================================
# 8. SERVICE ACCOUNT ROLES FOR IRSA (IAM Roles for Service Accounts)
# ============================================================================

# OIDC Identity Provider for EKS cluster
data "tls_certificate" "eks_oidc" {
  url = "https://oidc.eks.${var.region}.amazonaws.com"
}

resource "aws_iam_openid_connect_provider" "eks" {
  count = var.environment == "production" ? 1 : 0

  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
  url             = "https://oidc.eks.${var.region}.amazonaws.com/id/EXAMPLE"

  tags = var.tags
}

# Payment Service IRSA Role
resource "aws_iam_role" "payment_service_irsa" {
  name = "waqiti-payment-service-irsa-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = "arn:aws:iam::${var.account_id}:oidc-provider/oidc.eks.${var.region}.amazonaws.com/id/EXAMPLE"
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "oidc.eks.${var.region}.amazonaws.com/id/EXAMPLE:sub" = "system:serviceaccount:waqiti:payment-service"
            "oidc.eks.${var.region}.amazonaws.com/id/EXAMPLE:aud" = "sts.amazonaws.com"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Service   = "payment"
    Type      = "irsa"
    Compliance = "pci-dss"
  })
}

resource "aws_iam_role_policy_attachment" "payment_service_irsa" {
  policy_arn = aws_iam_policy.payment_service.arn
  role       = aws_iam_role.payment_service_irsa.name
}

# ============================================================================
# 9. CROSS-ACCOUNT ACCESS POLICIES (if needed)
# ============================================================================

resource "aws_iam_policy" "cross_account_backup" {
  count = var.environment == "production" ? 1 : 0
  
  name        = "waqiti-cross-account-backup-${var.environment}"
  description = "Cross-account backup access for disaster recovery"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:ReplicateObject",
          "s3:ReplicateDelete",
          "s3:ReplicateTags"
        ]
        Resource = [
          "arn:aws:s3:::waqiti-backup-${var.environment}/*"
        ]
        Condition = {
          StringEquals = {
            "s3:x-amz-server-side-encryption" = "aws:kms"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Purpose = "disaster-recovery"
    Type    = "cross-account"
  })
}

# ============================================================================
# 10. OUTPUTS
# ============================================================================

output "iam_roles" {
  description = "Created IAM roles and their ARNs"
  value = {
    eks_node_group      = aws_iam_role.eks_node_group.arn
    payment_service     = aws_iam_role.payment_service_irsa.arn
  }
}

output "iam_policies" {
  description = "Created IAM policies and their ARNs"
  value = {
    payment_service     = aws_iam_policy.payment_service.arn
    wallet_service      = aws_iam_policy.wallet_service.arn
    user_service        = aws_iam_policy.user_service.arn
    compliance_service  = aws_iam_policy.compliance_service.arn
    fraud_detection     = aws_iam_policy.fraud_detection_service.arn
    monitoring_service  = aws_iam_policy.monitoring_service.arn
  }
}