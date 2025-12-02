# Waqiti P2P Platform - System Recovery Runbook

## Overview
This runbook provides comprehensive procedures for recovering the Waqiti P2P payment platform from various failure scenarios, including complete system outages, data corruption, and infrastructure failures.

## Recovery Scenarios

### 1. Complete System Recovery from Backup

#### When to Use
- Complete infrastructure failure
- Kubernetes cluster destroyed
- Data center outage
- Disaster recovery activation

#### Prerequisites
- Access to backup storage (S3/Glacier)
- New Kubernetes cluster provisioned
- Database infrastructure restored
- Network connectivity established

#### Recovery Steps

##### Step 1: Infrastructure Preparation
```bash
# 1. Verify new cluster access
kubectl cluster-info
kubectl get nodes

# 2. Create namespace
kubectl create namespace waqiti
kubectl label namespace waqiti environment=production

# 3. Apply network policies
kubectl apply -f /infrastructure/kubernetes/production/waqiti-production-deployment.yaml

# 4. Create service accounts and RBAC
kubectl apply -f /infrastructure/kubernetes/rbac/
```

##### Step 2: Secrets and ConfigMaps Recovery
```bash
# 1. Restore secrets from HashiCorp Vault
vault kv get -field=postgres-password secret/waqiti/database
vault kv get -field=jwt-secret secret/waqiti/auth
vault kv get -field=stripe-api-key secret/waqiti/payment

# 2. Create Kubernetes secrets
kubectl create secret generic waqiti-database-secrets \
  --from-literal=postgres-username=waqiti_user \
  --from-literal=postgres-password="$(vault kv get -field=postgres-password secret/waqiti/database)" \
  -n waqiti

kubectl create secret generic waqiti-jwt-secrets \
  --from-literal=jwt-secret="$(vault kv get -field=jwt-secret secret/waqiti/auth)" \
  -n waqiti

kubectl create secret generic waqiti-api-keys \
  --from-literal=stripe-api-key="$(vault kv get -field=stripe-api-key secret/waqiti/payment)" \
  --from-literal=paystack-api-key="$(vault kv get -field=paystack-api-key secret/waqiti/payment)" \
  -n waqiti

# 3. Create ConfigMaps
kubectl create configmap waqiti-config \
  --from-literal=database.url="jdbc:postgresql://postgres:5432/waqiti" \
  --from-literal=keycloak.auth-server-url="https://auth.example.com" \
  --from-literal=kafka.bootstrap-servers="kafka:9092" \
  -n waqiti
```

##### Step 3: Database Recovery
```bash
# 1. Deploy PostgreSQL
kubectl apply -f /infrastructure/kubernetes/database/postgresql.yaml

# 2. Wait for database to be ready
kubectl wait --for=condition=ready pod/postgres-0 --timeout=600s -n waqiti

# 3. Download and restore latest backup
LATEST_BACKUP=$(aws s3 ls s3://waqiti-backups-prod/ --recursive | grep postgresql | sort | tail -1 | awk '{print $4}')
aws s3 cp "s3://waqiti-backups-prod/${LATEST_BACKUP}" /tmp/

# 4. Decrypt and restore backup
openssl enc -aes-256-cbc -d -in "/tmp/$(basename ${LATEST_BACKUP})" -out /tmp/waqiti-restore.sql.gz -pass file:/etc/waqiti/backup-encryption.key
gunzip /tmp/waqiti-restore.sql.gz

# 5. Restore database
kubectl exec -i postgres-0 -n waqiti -- psql -U postgres -d waqiti < /tmp/waqiti-restore.sql

# 6. Verify database integrity
kubectl exec postgres-0 -n waqiti -- psql -U postgres -d waqiti -c "SELECT COUNT(*) FROM users;"
kubectl exec postgres-0 -n waqiti -- psql -U postgres -d waqiti -c "SELECT COUNT(*) FROM transactions;"
kubectl exec postgres-0 -n waqiti -- psql -U postgres -d waqiti -c "SELECT COUNT(*) FROM wallets;"
```

##### Step 4: Redis Recovery
```bash
# 1. Deploy Redis
kubectl apply -f /infrastructure/kubernetes/redis/redis.yaml

# 2. Wait for Redis to be ready
kubectl wait --for=condition=ready pod/redis-0 --timeout=300s -n waqiti

# 3. Restore Redis data if needed
REDIS_BACKUP=$(aws s3 ls s3://waqiti-backups-prod/ --recursive | grep redis | sort | tail -1 | awk '{print $4}')
aws s3 cp "s3://waqiti-backups-prod/${REDIS_BACKUP}" /tmp/
openssl enc -aes-256-cbc -d -in "/tmp/$(basename ${REDIS_BACKUP})" -out /tmp/redis-dump.rdb -pass file:/etc/waqiti/backup-encryption.key

# Copy Redis dump to container
kubectl cp /tmp/redis-dump.rdb waqiti/redis-0:/data/dump.rdb

# Restart Redis to load data
kubectl delete pod redis-0 -n waqiti
kubectl wait --for=condition=ready pod/redis-0 --timeout=300s -n waqiti
```

##### Step 5: Application Services Recovery
```bash
# 1. Deploy services in dependency order
kubectl apply -f /infrastructure/kubernetes/production/waqiti-production-deployment.yaml

# 2. Monitor deployment progress
kubectl get pods -n waqiti -w

# 3. Check service health
kubectl wait --for=condition=ready pod -l app=waqiti --timeout=600s -n waqiti

# 4. Verify service connectivity
kubectl exec -n waqiti deployment/api-gateway -- curl -f http://payment-service:8080/actuator/health
kubectl exec -n waqiti deployment/api-gateway -- curl -f http://wallet-service:8080/actuator/health
kubectl exec -n waqiti deployment/api-gateway -- curl -f http://ledger-service:8080/actuator/health
```

##### Step 6: External Dependencies
```bash
# 1. Test payment gateway connectivity
kubectl exec -n waqiti deployment/payment-service -- curl -I https://api.stripe.com/v1/charges
kubectl exec -n waqiti deployment/payment-service -- curl -I https://api.paystack.co/transaction

# 2. Verify Keycloak connectivity
kubectl exec -n waqiti deployment/api-gateway -- curl -f https://auth.example.com/auth/realms/waqiti

# 3. Test Kafka connectivity
kubectl exec -n waqiti deployment/payment-service -- nc -z kafka 9092
```

##### Step 7: System Verification
```bash
# 1. Run health checks
curl -f https://api.example.com/health
curl -f https://api.example.com/actuator/health

# 2. Verify critical functionalities
# Test user authentication
curl -X POST https://api.example.com/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"testpass"}'

# Test payment processing (with test data)
curl -X POST https://api.example.com/api/v1/payments \
  -H "Authorization: Bearer ${TEST_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"amount":100,"currency":"USD","recipient":"test-recipient"}'

# 3. Monitor metrics and logs
kubectl logs -f -n waqiti deployment/api-gateway
kubectl logs -f -n waqiti deployment/payment-service
kubectl logs -f -n waqiti deployment/wallet-service
```

### 2. Database-Only Recovery

#### When to Use
- Database corruption
- Data loss scenarios
- Database server failure (application servers running)

#### Recovery Steps
```bash
# 1. Scale down applications to prevent writes
kubectl scale deployment api-gateway --replicas=0 -n waqiti
kubectl scale deployment payment-service --replicas=0 -n waqiti
kubectl scale deployment wallet-service --replicas=0 -n waqiti
kubectl scale deployment ledger-service --replicas=0 -n waqiti

# 2. Backup current state (if possible)
kubectl exec postgres-0 -n waqiti -- pg_dump -U postgres waqiti > /tmp/pre-recovery-backup.sql

# 3. Stop database
kubectl scale statefulset postgres --replicas=0 -n waqiti

# 4. Remove corrupted data
kubectl delete pvc data-postgres-0 -n waqiti

# 5. Start database with fresh volume
kubectl scale statefulset postgres --replicas=1 -n waqiti
kubectl wait --for=condition=ready pod/postgres-0 --timeout=600s -n waqiti

# 6. Restore from backup (follow database recovery steps from above)

# 7. Scale applications back up
kubectl scale deployment api-gateway --replicas=3 -n waqiti
kubectl scale deployment payment-service --replicas=5 -n waqiti
kubectl scale deployment wallet-service --replicas=4 -n waqiti
kubectl scale deployment ledger-service --replicas=3 -n waqiti

# 8. Verify system health
kubectl wait --for=condition=ready pod -l app=waqiti --timeout=600s -n waqiti
```

### 3. Single Service Recovery

#### When to Use
- Individual service failure
- Deployment issues
- Configuration problems

#### Recovery Steps
```bash
# 1. Identify the failing service
SERVICE_NAME="payment-service"  # Replace with actual service

# 2. Check current status
kubectl get pods -n waqiti -l component=${SERVICE_NAME}
kubectl describe deployment ${SERVICE_NAME} -n waqiti

# 3. Review recent changes
kubectl rollout history deployment/${SERVICE_NAME} -n waqiti

# 4. Rollback if recent deployment caused issues
kubectl rollout undo deployment/${SERVICE_NAME} -n waqiti

# 5. If rollback doesn't work, restart deployment
kubectl rollout restart deployment/${SERVICE_NAME} -n waqiti

# 6. Monitor recovery
kubectl rollout status deployment/${SERVICE_NAME} -n waqiti --timeout=600s

# 7. Verify service health
kubectl wait --for=condition=ready pod -l component=${SERVICE_NAME} --timeout=300s -n waqiti
kubectl exec -n waqiti deployment/${SERVICE_NAME} -- curl -f http://localhost:8080/actuator/health
```

### 4. Configuration Recovery

#### When to Use
- Invalid configuration deployed
- ConfigMap/Secret corruption
- Environment variable issues

#### Recovery Steps
```bash
# 1. Backup current configuration
kubectl get configmap waqiti-config -n waqiti -o yaml > /tmp/current-config-backup.yaml
kubectl get secrets -n waqiti -o yaml > /tmp/current-secrets-backup.yaml

# 2. Restore configuration from git
git checkout HEAD -- /infrastructure/kubernetes/config/
kubectl apply -f /infrastructure/kubernetes/config/

# 3. Or restore from Vault
source /scripts/restore-config-from-vault.sh

# 4. Restart affected services
kubectl rollout restart deployment/api-gateway -n waqiti
kubectl rollout restart deployment/payment-service -n waqiti
kubectl rollout restart deployment/wallet-service -n waqiti

# 5. Verify configuration
kubectl exec -n waqiti deployment/api-gateway -- env | grep SPRING_
kubectl logs -n waqiti deployment/api-gateway --tail=50 | grep -i error
```

## Recovery Time Objectives (RTO) and Recovery Point Objectives (RPO)

### Production SLA Targets
- **RTO**: 4 hours maximum downtime
- **RPO**: 1 hour maximum data loss
- **Availability**: 99.9% uptime

### Recovery Time Estimates
| Scenario | Estimated RTO | Prerequisites |
|----------|---------------|---------------|
| Complete System Recovery | 2-4 hours | New infrastructure ready |
| Database-Only Recovery | 1-2 hours | Applications can be stopped |
| Single Service Recovery | 5-15 minutes | Other services healthy |
| Configuration Recovery | 10-30 minutes | Valid configuration available |

## Validation Checklist

### Post-Recovery Validation
- [ ] All pods are running and ready
- [ ] Health checks passing for all services
- [ ] Database connectivity verified
- [ ] External API connectivity confirmed
- [ ] Payment processing functional (test transaction)
- [ ] User authentication working
- [ ] Monitoring and alerting operational
- [ ] Backup systems resumed

### Critical Business Functions
- [ ] User login/registration
- [ ] Payment initiation
- [ ] Payment completion
- [ ] Wallet balance updates
- [ ] Transaction history
- [ ] Admin dashboard access
- [ ] Reporting functions

## Rollback Procedures

### Application Rollback
```bash
# 1. Rollback to previous deployment
kubectl rollout undo deployment/payment-service -n waqiti

# 2. Rollback to specific revision
kubectl rollout undo deployment/payment-service --to-revision=3 -n waqiti

# 3. Monitor rollback
kubectl rollout status deployment/payment-service -n waqiti
```

### Database Rollback
```bash
# 1. Stop applications
kubectl scale deployment --all --replicas=0 -n waqiti

# 2. Create rollback point
kubectl exec postgres-0 -n waqiti -- pg_dump -U postgres waqiti > /tmp/rollback-point.sql

# 3. Restore from earlier backup
# (Follow database recovery procedures with desired backup)

# 4. Restart applications
kubectl scale deployment api-gateway --replicas=3 -n waqiti
# ... scale other services
```

## Emergency Contacts

### Internal Teams
- **Platform Team Lead**: +1-555-0100
- **Database Admin**: +1-555-0101
- **Security Team**: +1-555-0102
- **Product Manager**: +1-555-0103

### External Vendors
- **AWS Support**: Enterprise Support Portal
- **Kubernetes Support**: Professional Support
- **Database Vendor**: Priority Support

## Recovery Tools and Scripts

### Automated Recovery Scripts
- `/scripts/full-system-recovery.sh` - Complete system recovery automation
- `/scripts/database-recovery.sh` - Database-specific recovery
- `/scripts/service-recovery.sh` - Individual service recovery
- `/scripts/config-recovery.sh` - Configuration restoration

### Monitoring During Recovery
```bash
# Real-time monitoring script
#!/bin/bash
watch -n 5 'kubectl get pods -n waqiti && echo "=== Services ===" && kubectl get svc -n waqiti && echo "=== Ingress ===" && kubectl get ingress -n waqiti'

# Health check monitoring
while true; do
  echo "$(date): Checking health..."
  curl -s https://api.example.com/health | jq .status
  sleep 30
done
```

## Post-Recovery Actions

### Immediate (Within 1 hour)
1. Update status page with resolution
2. Notify stakeholders of service restoration
3. Document recovery timeline
4. Monitor system stability

### Short-term (Within 24 hours)
1. Conduct post-mortem meeting
2. Update runbooks based on learnings
3. Review and improve monitoring
4. Test backup systems

### Long-term (Within 1 week)
1. Implement preventive measures
2. Update disaster recovery plans
3. Conduct recovery drill post-mortem
4. Train team on lessons learned

## Document Maintenance

### Regular Updates
- Review quarterly or after major incidents
- Update contact information
- Validate recovery procedures
- Test automation scripts

### Version Control
- Store runbooks in git repository
- Track changes and updates
- Maintain backup copies
- Review access permissions

---
**Document Version**: 1.0  
**Last Updated**: 2024-08-21  
**Next Review**: 2024-11-21  
**Owner**: Platform Team