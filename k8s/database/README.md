# Waqiti Database Infrastructure

## Overview

Production-grade database infrastructure for the Waqiti fintech platform featuring high availability PostgreSQL clusters, Redis caching, automated backups, and comprehensive monitoring.

## Architecture Components

### PostgreSQL High Availability Cluster
- **3-node cluster** with automatic failover
- **Streaming replication** with synchronous commit
- **SSL/TLS encryption** for all connections
- **Point-in-time recovery** (PITR) capability
- **Backup encryption** and compression

### Redis High Availability Cluster  
- **6-node Redis cluster** (3 masters + 3 replicas)
- **Automatic sharding** and rebalancing
- **Password authentication** and security hardening
- **Persistent storage** with AOF and RDB

### Backup Strategy
- **Full backups**: Weekly on Sundays at 2 AM UTC
- **Incremental backups**: Every 6 hours (6 AM, 12 PM, 6 PM)
- **Retention policy**: 90 days for full, 7 days for incremental
- **S3 storage** with server-side encryption
- **Automated cleanup** of old backups

## Database Services

### Primary Services
- `waqiti-postgres-primary`: Write operations, primary database
- `waqiti-postgres-replica`: Read operations, replica databases  
- `redis-cluster`: Distributed caching and session storage

### Backup Services
- `postgres-backup-full`: Weekly full database backups
- `postgres-backup-incremental`: 6-hourly incremental backups
- `backup-validation-job`: Daily backup integrity verification

## Deployment Instructions

### Prerequisites
```bash
# Create namespace
kubectl create namespace database

# Install CloudNativePG operator
kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.21/releases/cnpg-1.21.0.yaml

# Create storage class for fast SSDs
kubectl apply -f - <<EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: kubernetes.io/aws-ebs
parameters:
  type: gp3
  iops: "3000"
  throughput: "125"
  encrypted: "true"
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
EOF
```

### Deploy Database Infrastructure
```bash
# Deploy PostgreSQL HA cluster
kubectl apply -f postgresql-ha-cluster.yaml

# Deploy Redis cluster
kubectl apply -f redis-ha-cluster.yaml

# Wait for cluster initialization
kubectl wait --for=condition=Ready cluster/waqiti-postgres-cluster -n database --timeout=600s

# Initialize Redis cluster
kubectl exec -it redis-cluster-0 -n database -- redis-cli --cluster create \
  $(kubectl get pods -n database -l app=redis -o jsonpath='{range .items[*]}{.status.podIP}:6379 {end}') \
  --cluster-replicas 1 --cluster-yes
```

### Setup Automated Backups
```bash
# Create backup credentials secret
kubectl create secret generic backup-credentials -n database \
  --from-literal=ACCESS_KEY_ID=your-access-key \
  --from-literal=SECRET_ACCESS_KEY=your-secret-key

# Create database credentials secret
kubectl create secret generic postgres-credentials -n database \
  --from-literal=username=waqiti_user \
  --from-literal=password=secure-database-password

# Deploy backup schedules
kubectl apply -f backup-scheduler.yaml

# Deploy restore jobs (on-demand)
kubectl apply -f backup-restore-jobs.yaml
```

### Setup Monitoring
```bash
# Deploy database monitoring
kubectl apply -f monitoring-alerts.yaml

# Verify exporters are running
kubectl get pods -n database -l app=postgresql-exporter
kubectl get pods -n database -l app=redis-exporter
```

## Configuration Details

### PostgreSQL Configuration
```yaml
Max Connections: 200
Shared Buffers: 256MB
Effective Cache Size: 1GB
Work Memory: 4MB
Maintenance Work Memory: 64MB
SSL: Required
Log Duration: >1000ms queries
```

### Redis Configuration
```yaml
Max Memory: 1GB
Memory Policy: allkeys-lru
Persistence: AOF + RDB
Cluster Mode: Enabled
Authentication: Required
```

### Storage Configuration
```yaml
PostgreSQL: 100Gi per instance (fast-ssd)
Redis: 20Gi per instance (fast-ssd)
Backup Storage: S3 with encryption
```

## Backup Operations

### Manual Backup
```bash
# Trigger manual full backup
kubectl create job --from=cronjob/postgres-backup-full postgres-backup-manual -n database

# Trigger Redis backup
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: redis-backup-manual
  namespace: database
spec:
  template:
    spec:
      containers:
      - name: redis-backup
        image: redis:7-alpine
        # ... (backup configuration)
      restartPolicy: Never
EOF
```

### Restore Operations
```bash
# List available backups
aws s3 ls s3://waqiti-postgres-backups/full-backups/

# Restore from backup
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: postgres-restore-$(date +%s)
  namespace: database
spec:
  template:
    spec:
      containers:
      - name: postgres-restore
        # ... (restore job configuration)
        env:
        - name: BACKUP_FILE
          value: "full-backups/waqiti_full_backup_20241201_020000.sql.gz"
        - name: TARGET_DATABASE
          value: "waqiti_restored"
      restartPolicy: Never
EOF
```

## Monitoring and Alerting

### Key Metrics
- **Database health**: Connection count, query performance, replication lag
- **Resource usage**: CPU, memory, disk space, I/O
- **Backup status**: Success/failure, duration, file size
- **Security events**: Failed logins, connection spikes

### Critical Alerts
- Database instance down (1 minute)
- Replication lag > 30 seconds (2 minutes)
- Disk space > 85% (5 minutes)
- Backup failure (immediate)
- Connection pool exhaustion (5 minutes)

### Dashboards
Access monitoring dashboards at:
- **PostgreSQL**: http://grafana.example.com/d/postgres-overview
- **Redis**: http://grafana.example.com/d/redis-cluster
- **Backups**: http://grafana.example.com/d/backup-status

## Security Features

### Encryption
- **At-rest**: All data encrypted with AES-256
- **In-transit**: TLS 1.2+ for all connections
- **Backups**: Server-side encryption in S3

### Access Control
- **Network policies**: Restrict inter-pod communication
- **RBAC**: Service accounts with minimal permissions
- **Password policies**: Strong authentication requirements
- **Audit logging**: All database operations logged

### Compliance
- **PCI-DSS**: Data encryption and access controls
- **SOC 2**: Audit trails and monitoring
- **GDPR**: Data retention and deletion policies

## Disaster Recovery

### RTO/RPO Targets
- **Recovery Time Objective (RTO)**: 15 minutes
- **Recovery Point Objective (RPO)**: 1 hour maximum data loss

### Failover Procedures
```bash
# Manual failover to replica
kubectl patch cluster waqiti-postgres-cluster -n database \
  --type='json' -p='[{"op": "replace", "path": "/spec/primaryUpdateStrategy", "value": "supervised"}]'

# Promote specific replica
kubectl cnpg promote waqiti-postgres-cluster-2 -n database
```

### Multi-Region Setup
- **Primary region**: us-west-2
- **Backup region**: us-east-1  
- **Cross-region replication**: Automated S3 sync
- **Failover automation**: Terraform + Route53

## Maintenance Procedures

### Routine Maintenance
- **Daily**: Monitor backup completion and database health
- **Weekly**: Review slow query logs and performance metrics
- **Monthly**: Update database statistics and vacuum operations
- **Quarterly**: Test disaster recovery procedures

### Updates and Patches
```bash
# Update PostgreSQL version (rolling update)
kubectl patch cluster waqiti-postgres-cluster -n database \
  --type='json' -p='[{"op": "replace", "path": "/spec/postgresql/parameters/image", "value": "postgres:15.5"}]'

# Update Redis version
kubectl patch statefulset redis-cluster -n database \
  --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/image", "value": "redis:7.2-alpine"}]'
```

## Performance Tuning

### PostgreSQL Optimization
```sql
-- Connection pooling
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';

-- Query optimization
ALTER SYSTEM SET work_mem = '4MB';
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;

-- Checkpoint tuning
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
```

### Redis Optimization
```bash
# Memory optimization
CONFIG SET maxmemory-policy allkeys-lru
CONFIG SET maxmemory 1gb

# Performance tuning
CONFIG SET tcp-keepalive 300
CONFIG SET timeout 0
```

## Troubleshooting

### Common Issues

#### PostgreSQL Connection Issues
```bash
# Check cluster status
kubectl get cluster waqiti-postgres-cluster -n database

# View cluster events
kubectl describe cluster waqiti-postgres-cluster -n database

# Check pod logs
kubectl logs waqiti-postgres-cluster-1 -n database
```

#### Redis Cluster Issues
```bash
# Check cluster nodes
kubectl exec redis-cluster-0 -n database -- redis-cli cluster nodes

# Check cluster status
kubectl exec redis-cluster-0 -n database -- redis-cli cluster info

# Fix cluster if needed
kubectl exec redis-cluster-0 -n database -- redis-cli cluster fix
```

#### Backup Failures
```bash
# Check backup job status
kubectl get jobs -n database | grep backup

# View backup job logs
kubectl logs job/postgres-backup-full -n database

# Test S3 connectivity
kubectl exec -it redis-cluster-0 -n database -- aws s3 ls s3://waqiti-postgres-backups/
```

## Cost Optimization

### Storage Optimization
- **Automated cleanup**: Remove old backups automatically
- **Compression**: Use gzip for backup files
- **Tiered storage**: Move old backups to IA/Glacier

### Resource Optimization
- **Right-sizing**: Monitor and adjust resource requests/limits
- **Spot instances**: Use spot nodes for non-critical replicas
- **Scheduled scaling**: Scale down during low usage periods

## Support and Contacts

- **Database Team**: database@example.com
- **On-call**: +1-555-WAQITI-DB
- **Documentation**: https://docs.example.com/database
- **Runbooks**: https://runbook.example.com/database

## License

Copyright © 2024 Waqiti. All rights reserved.

This database infrastructure configuration is proprietary and confidential.