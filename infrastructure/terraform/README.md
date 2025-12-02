# Waqiti P2P Payment Platform - Infrastructure as Code

This Terraform configuration provisions a production-ready infrastructure for the Waqiti P2P payment platform on AWS.

## Architecture Overview

The infrastructure includes:

### Core Infrastructure
- **VPC**: Multi-AZ virtual private cloud with public, private, and database subnets
- **EKS Cluster**: Managed Kubernetes cluster with multiple node groups
- **RDS PostgreSQL**: High-availability database with encryption and automated backups
- **ElastiCache Redis**: Distributed caching layer for performance
- **MSK Kafka**: Managed streaming platform for event-driven architecture

### Security & Compliance
- **HashiCorp Vault**: Secrets management and encryption
- **AWS KMS**: Key management for encryption at rest
- **WAF**: Web application firewall protection
- **Security Groups**: Network-level access controls
- **IAM Roles**: Least-privilege access policies

### Monitoring & Observability
- **CloudWatch**: Metrics, logs, and alarms
- **Prometheus/Grafana**: Application metrics and dashboards (via Helm)
- **ELK Stack**: Centralized logging (via Kubernetes)
- **Performance Insights**: Database performance monitoring

### Backup & Disaster Recovery
- **AWS Backup**: Automated backup strategies
- **S3**: Object storage with lifecycle policies
- **Multi-AZ**: High availability across availability zones
- **Disaster Recovery**: Automated recovery procedures

## Prerequisites

1. **AWS CLI** configured with appropriate credentials
2. **Terraform** >= 1.6
3. **kubectl** for Kubernetes management
4. **Helm** for application deployment

## Quick Start

### 1. Clone and Configure

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars
```

### 2. Edit Configuration

Edit `terraform.tfvars` with your specific values:

```hcl
# Required configurations
project_name = "waqiti-p2p-platform"
environment  = "production"
aws_region   = "us-west-2"
domain_name  = "api.yourdomain.com"
route53_zone_id = "YOUR_ZONE_ID"
alert_email  = "alerts@yourdomain.com"
```

### 3. Initialize Terraform

```bash
terraform init
```

### 4. Plan and Apply

```bash
# Review the execution plan
terraform plan

# Apply the configuration
terraform apply
```

### 5. Configure kubectl

```bash
# Update kubeconfig for EKS cluster
aws eks update-kubeconfig --region us-west-2 --name $(terraform output -raw eks_cluster_name)
```

## Module Structure

```
modules/
├── networking/     # VPC, subnets, security groups
├── eks/           # Kubernetes cluster and node groups
├── database/      # RDS PostgreSQL configuration
├── cache/         # ElastiCache Redis cluster
├── messaging/     # MSK Kafka cluster
├── vault/         # HashiCorp Vault deployment
├── monitoring/    # CloudWatch and alerting
├── storage/       # S3 buckets and policies
├── backup/        # AWS Backup configuration
└── waf/          # Web Application Firewall
```

## Environment Configuration

### Development
```hcl
environment = "dev"
db_instance_class = "db.t3.micro"
redis_node_type = "cache.t3.micro"
enable_multi_az = false
```

### Staging
```hcl
environment = "staging"
db_instance_class = "db.r5.large"
redis_node_type = "cache.r6g.large"
enable_multi_az = true
```

### Production
```hcl
environment = "production"
db_instance_class = "db.r5.xlarge"
redis_node_type = "cache.r6g.xlarge"
enable_multi_az = true
enable_deletion_protection = true
```

## Security Considerations

### Secrets Management
- Database passwords stored in AWS Secrets Manager
- Redis auth tokens encrypted with KMS
- Vault unseal keys managed securely
- All sensitive outputs marked as sensitive

### Network Security
- Private subnets for application workloads
- Database isolated in dedicated subnets
- Security groups with minimal required access
- Network ACLs for additional protection
- VPC endpoints to reduce internet traffic

### Encryption
- EKS secrets encrypted with customer-managed KMS keys
- RDS encryption at rest and in transit
- Redis encryption at rest and in transit
- S3 buckets with default encryption
- CloudWatch logs encrypted

## Monitoring and Alerting

### CloudWatch Alarms
- Database CPU, memory, and connection monitoring
- Redis CPU, memory, and eviction monitoring
- EKS node and pod resource monitoring
- Application-level custom metrics

### SNS Notifications
- Critical alerts to operations team
- Performance degradation warnings
- Security event notifications
- Backup success/failure alerts

## Backup Strategy

### Automated Backups
- RDS automated backups with 30-day retention
- Redis snapshots with configurable retention
- EBS volume snapshots for persistent storage
- S3 cross-region replication for critical data

### Disaster Recovery
- Multi-AZ deployment for high availability
- Automated failover for database and cache
- Infrastructure as Code for rapid rebuilding
- Documented recovery procedures

## Cost Optimization

### Resource Optimization
- Spot instances for non-critical workloads
- Auto-scaling for dynamic workloads
- S3 lifecycle policies for long-term storage
- Reserved instances for predictable workloads

### Monitoring Costs
- CloudWatch billing alarms
- Resource tagging for cost allocation
- Regular cost reviews and optimization

## Compliance

### PCI DSS Compliance
- Encrypted data at rest and in transit
- Network segmentation and access controls
- Audit logging and monitoring
- Regular security assessments

### SOC 2 Compliance
- Access controls and authentication
- Change management procedures
- Incident response processes
- Vendor management protocols

## Troubleshooting

### Common Issues

1. **EKS Node Groups Not Ready**
   ```bash
   kubectl get nodes
   kubectl describe nodes
   ```

2. **Database Connection Issues**
   ```bash
   # Check security groups
   aws ec2 describe-security-groups --group-ids sg-xxx
   ```

3. **Redis Connection Issues**
   ```bash
   # Test from EKS pod
   kubectl exec -it test-pod -- redis-cli -h <endpoint> ping
   ```

### Logs and Debugging

```bash
# Terraform debugging
TF_LOG=DEBUG terraform apply

# AWS CLI debugging
aws --debug eks describe-cluster --name cluster-name

# Kubernetes debugging
kubectl logs -f deployment/app-name
```

## Maintenance

### Regular Tasks
- Monthly security patches for EKS nodes
- Quarterly review of security groups and NACLs
- Annual review of backup and disaster recovery procedures
- Continuous monitoring of costs and resource utilization

### Updates
- Terraform provider updates
- Kubernetes version upgrades
- Database engine version updates
- Application dependency updates

## Support

For infrastructure issues:
1. Check CloudWatch logs and metrics
2. Review Terraform state and configuration
3. Consult AWS documentation
4. Contact platform team: platform-team@example.com

## Contributing

1. Follow infrastructure as code best practices
2. Test changes in development environment first
3. Document all configuration changes
4. Review security implications of changes
5. Update this README for significant changes

---

**⚠️ Important**: This infrastructure handles financial data. Always follow security best practices and comply with relevant regulations (PCI DSS, SOC 2, etc.).