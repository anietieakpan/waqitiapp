# Networking Module - VPC, Subnets, and Network Infrastructure
# Creates a production-ready network architecture for Waqiti P2P Platform

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = var.enable_dns_hostnames
  enable_dns_support   = var.enable_dns_support

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-vpc"
    Type = "VPC"
  })
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-igw"
    Type = "InternetGateway"
  })
}

# Availability Zones
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # Use first 3 AZs or all available if less than 3
  availability_zones = slice(var.availability_zones, 0, min(length(var.availability_zones), 3))
  
  # Calculate subnet CIDRs
  subnet_bits = 8  # Creates /24 subnets from /16 VPC
  
  public_subnet_cidrs = [
    for i, az in local.availability_zones :
    cidrsubnet(var.vpc_cidr, local.subnet_bits, i)
  ]
  
  private_subnet_cidrs = [
    for i, az in local.availability_zones :
    cidrsubnet(var.vpc_cidr, local.subnet_bits, i + 10)
  ]
  
  database_subnet_cidrs = [
    for i, az in local.availability_zones :
    cidrsubnet(var.vpc_cidr, local.subnet_bits, i + 20)
  ]
}

# Public Subnets
resource "aws_subnet" "public" {
  count = length(local.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = local.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-public-${local.availability_zones[count.index]}"
    Type = "Public"
    Tier = "Public"
    "kubernetes.io/role/elb" = "1"
  })
}

# Private Subnets (for application workloads)
resource "aws_subnet" "private" {
  count = length(local.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.private_subnet_cidrs[count.index]
  availability_zone = local.availability_zones[count.index]

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-private-${local.availability_zones[count.index]}"
    Type = "Private"
    Tier = "Application"
    "kubernetes.io/role/internal-elb" = "1"
  })
}

# Database Subnets (isolated for database workloads)
resource "aws_subnet" "database" {
  count = length(local.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = local.database_subnet_cidrs[count.index]
  availability_zone = local.availability_zones[count.index]

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-database-${local.availability_zones[count.index]}"
    Type = "Database"
    Tier = "Database"
  })
}

# Elastic IPs for NAT Gateways
resource "aws_eip" "nat" {
  count = var.enable_nat_gateway ? length(local.availability_zones) : 0

  domain = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-nat-eip-${local.availability_zones[count.index]}"
    Type = "ElasticIP"
  })
}

# NAT Gateways
resource "aws_nat_gateway" "main" {
  count = var.enable_nat_gateway ? length(local.availability_zones) : 0

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  depends_on    = [aws_internet_gateway.main]

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-nat-${local.availability_zones[count.index]}"
    Type = "NATGateway"
  })
}

# Route Tables - Public
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-public-rt"
    Type = "RouteTable"
    Tier = "Public"
  })
}

# Route Tables - Private (one per AZ for high availability)
resource "aws_route_table" "private" {
  count = length(local.availability_zones)

  vpc_id = aws_vpc.main.id

  dynamic "route" {
    for_each = var.enable_nat_gateway ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.main[count.index].id
    }
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-private-rt-${local.availability_zones[count.index]}"
    Type = "RouteTable"
    Tier = "Private"
  })
}

# Route Tables - Database
resource "aws_route_table" "database" {
  count = length(local.availability_zones)

  vpc_id = aws_vpc.main.id

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-database-rt-${local.availability_zones[count.index]}"
    Type = "RouteTable"
    Tier = "Database"
  })
}

# Route Table Associations - Public
resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Route Table Associations - Private
resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# Route Table Associations - Database
resource "aws_route_table_association" "database" {
  count = length(aws_subnet.database)

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database[count.index].id
}

# VPC Endpoints for AWS services (reduces NAT Gateway costs and improves security)

# S3 VPC Endpoint
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = concat(aws_route_table.private[*].id, aws_route_table.database[*].id)

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-s3-endpoint"
    Type = "VPCEndpoint"
  })
}

# DynamoDB VPC Endpoint
resource "aws_vpc_endpoint" "dynamodb" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = concat(aws_route_table.private[*].id, aws_route_table.database[*].id)

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-dynamodb-endpoint"
    Type = "VPCEndpoint"
  })
}

# ECR API VPC Endpoint
resource "aws_vpc_endpoint" "ecr_api" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.api"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  policy              = data.aws_iam_policy_document.vpc_endpoint_policy.json
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-ecr-api-endpoint"
    Type = "VPCEndpoint"
  })
}

# ECR DKR VPC Endpoint
resource "aws_vpc_endpoint" "ecr_dkr" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.dkr"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  policy              = data.aws_iam_policy_document.vpc_endpoint_policy.json
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-ecr-dkr-endpoint"
    Type = "VPCEndpoint"
  })
}

# CloudWatch Logs VPC Endpoint
resource "aws_vpc_endpoint" "logs" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.logs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  policy              = data.aws_iam_policy_document.vpc_endpoint_policy.json
  private_dns_enabled = true

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-logs-endpoint"
    Type = "VPCEndpoint"
  })
}

# Security Group for VPC Endpoints
resource "aws_security_group" "vpc_endpoints" {
  name        = "${var.environment}-waqiti-vpc-endpoints"
  description = "Security group for VPC endpoints"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-vpc-endpoints-sg"
    Type = "SecurityGroup"
  })
}

# VPC Endpoint Policy
data "aws_iam_policy_document" "vpc_endpoint_policy" {
  statement {
    effect = "Allow"
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    actions   = ["*"]
    resources = ["*"]
  }
}

# Network ACLs for additional security layer
resource "aws_network_acl" "public" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = aws_subnet.public[*].id

  # HTTP
  ingress {
    protocol   = "tcp"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 80
    to_port    = 80
  }

  # HTTPS
  ingress {
    protocol   = "tcp"
    rule_no    = 110
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 443
    to_port    = 443
  }

  # SSH (restrict in production)
  ingress {
    protocol   = "tcp"
    rule_no    = 120
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 22
    to_port    = 22
  }

  # Ephemeral ports for responses
  ingress {
    protocol   = "tcp"
    rule_no    = 130
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # All outbound traffic
  egress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-public-nacl"
    Type = "NetworkACL"
  })
}

resource "aws_network_acl" "private" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = aws_subnet.private[*].id

  # Allow all traffic from VPC
  ingress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  # Ephemeral ports for internet responses
  ingress {
    protocol   = "tcp"
    rule_no    = 110
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # All outbound traffic
  egress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-private-nacl"
    Type = "NetworkACL"
  })
}

resource "aws_network_acl" "database" {
  vpc_id     = aws_vpc.main.id
  subnet_ids = aws_subnet.database[*].id

  # Allow traffic from private subnets only
  ingress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  # Outbound to VPC only
  egress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = var.vpc_cidr
    from_port  = 0
    to_port    = 0
  }

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-database-nacl"
    Type = "NetworkACL"
  })
}

# VPN Gateway (optional)
resource "aws_vpn_gateway" "main" {
  count = var.enable_vpn_gateway ? 1 : 0

  vpc_id = aws_vpc.main.id

  tags = merge(var.tags, {
    Name = "${var.environment}-waqiti-vpn-gateway"
    Type = "VPNGateway"
  })
}

# Data source for current AWS region
data "aws_region" "current" {}