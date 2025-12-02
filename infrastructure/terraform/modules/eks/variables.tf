# EKS Module Variables

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.28"
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where EKS cluster will be created"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for the EKS cluster"
  type        = list(string)
}

variable "endpoint_public_access" {
  description = "Enable public API server endpoint"
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "List of CIDR blocks that can access the public endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_groups" {
  description = "Map of EKS node group configurations"
  type = map(object({
    instance_types = list(string)
    min_size       = number
    max_size       = number
    desired_size   = number
    capacity_type  = string
    ami_type       = optional(string, "AL2_x86_64")
    disk_size      = optional(number, 50)
    node_labels    = optional(map(string), {})
    taints = optional(list(object({
      key    = string
      value  = string
      effect = string
    })), [])
  }))
  default = {}
}

variable "node_ssh_key" {
  description = "EC2 Key Pair name for SSH access to nodes"
  type        = string
  default     = null
}

variable "vpc_cni_version" {
  description = "Version of the VPC CNI addon"
  type        = string
  default     = null
}

variable "kube_proxy_version" {
  description = "Version of the kube-proxy addon"
  type        = string
  default     = null
}

variable "coredns_version" {
  description = "Version of the CoreDNS addon"
  type        = string
  default     = null
}

variable "ebs_csi_version" {
  description = "Version of the EBS CSI driver addon"
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

variable "allowed_cidr_blocks" {
  description = "List of CIDR blocks allowed to access EKS API server (VPN, CI/CD, bastion IPs)"
  type        = list(string)
  default     = []
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC"
  type        = string
}

variable "ecr_prefix_list_id" {
  description = "Prefix list ID for ECR VPC endpoint"
  type        = string
}

variable "s3_prefix_list_id" {
  description = "Prefix list ID for S3 VPC endpoint"
  type        = string
}

variable "external_api_cidrs" {
  description = "CIDR blocks for external APIs (payment processors, etc.)"
  type        = list(string)
  default     = []
}