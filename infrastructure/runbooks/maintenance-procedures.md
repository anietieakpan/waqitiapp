# Waqiti P2P Platform - Maintenance Procedures

## Overview
This document outlines routine maintenance procedures for the Waqiti P2P payment platform to ensure optimal performance, security, and reliability.

## Maintenance Schedule

### Daily Maintenance (Automated)
- **Time**: 02:00 UTC (off-peak hours)
- **Duration**: 30 minutes
- **Responsibility**: Automated systems

#### Daily Tasks
1. **Database Maintenance**
   - VACUUM ANALYZE on high-transaction tables
   - Index maintenance
   - Statistics update
   - Log rotation

2. **System Health Checks**
   - Service health verification
   - Resource usage monitoring
   - Security scan results review
   - Backup verification

3. **Log Management**
   - Log rotation and compression
   - Old log cleanup
   - Log analysis for errors

### Weekly Maintenance (Semi-Automated)
- **Time**: Sunday 03:00 UTC
- **Duration**: 2 hours
- **Responsibility**: Platform team

#### Weekly Tasks
1. **Security Updates**
   - OS security patches (non-critical)
   - Library updates
   - Vulnerability scan reviews

2. **Performance Optimization**
   - Database performance analysis
   - Query optimization review
   - Index recommendations

3. **Capacity Planning**
   - Resource usage trends
   - Growth projections
   - Scaling recommendations

### Monthly Maintenance (Manual)
- **Time**: First Sunday of month, 01:00 UTC
- **Duration**: 4 hours (includes maintenance window)
- **Responsibility**: Platform team + SRE

#### Monthly Tasks
1. **Major Updates**
   - Application version updates
   - Database engine updates
   - Infrastructure updates

2. **Comprehensive Testing**
   - Disaster recovery testing
   - Security penetration testing
   - Performance benchmarking

3. **Documentation Updates**
   - Runbook reviews
   - Process documentation
   - Architecture updates

## Maintenance Procedures

### 1. Application Updates and Deployments

#### Pre-Deployment Checklist
- [ ] Code reviewed and approved
- [ ] Security scan completed
- [ ] Tests passing (unit, integration, e2e)
- [ ] Database migration tested
- [ ] Rollback plan prepared
- [ ] Maintenance window scheduled
- [ ] Stakeholders notified

#### Deployment Process
```bash
#!/bin/bash
# Application deployment procedure

# 1. Pre-deployment backup
echo "Creating pre-deployment backup..."
kubectl exec -n waqiti postgres-0 -- pg_dump -U postgres waqiti > /backups/pre-deployment-$(date +%Y%m%d-%H%M%S).sql

# 2. Database migrations (if needed)
echo "Running database migrations..."
kubectl exec -n waqiti deployment/api-gateway -- java -jar migration-tool.jar migrate

# 3. Blue-Green deployment
echo "Starting blue-green deployment..."

# Scale up new version
kubectl apply -f /deployments/waqiti-v2.yaml
kubectl wait --for=condition=ready pod -l version=v2 --timeout=300s -n waqiti

# Health check new version
NEW_PODS=$(kubectl get pods -n waqiti -l version=v2 -o name)
for pod in $NEW_PODS; do
    kubectl exec $pod -n waqiti -- curl -f http://localhost:8080/actuator/health
done

# Switch traffic
kubectl patch service api-gateway -n waqiti --patch '{"spec":{"selector":{"version":"v2"}}}'

# Monitor for 10 minutes
echo "Monitoring new deployment..."
sleep 600

# Verify success metrics
ERROR_RATE=$(kubectl exec -n waqiti deployment/api-gateway -- curl -s http://localhost:9090/actuator/prometheus | grep http_server_requests_seconds_count | grep status=\"5\" | awk -F' ' '{sum+=$2} END {print sum}')

if [ "${ERROR_RATE:-0}" -lt 10 ]; then
    echo "Deployment successful. Scaling down old version..."
    kubectl delete deployment -n waqiti -l version=v1
else
    echo "High error rate detected. Rolling back..."
    kubectl patch service api-gateway -n waqiti --patch '{"spec":{"selector":{"version":"v1"}}}'
    kubectl delete deployment -n waqiti -l version=v2
    exit 1
fi
```

#### Post-Deployment Verification
```bash
# Verify all services are healthy
kubectl get pods -n waqiti
kubectl get services -n waqiti

# Run smoke tests
curl -f https://api.example.com/health
curl -f https://api.example.com/actuator/health

# Check error rates
kubectl exec -n waqiti deployment/api-gateway -- curl http://localhost:9090/actuator/prometheus | grep error_rate

# Verify critical business functions
./scripts/smoke-test.sh
```

### 2. Database Maintenance

#### Daily Database Maintenance
```bash
#!/bin/bash
# Daily database maintenance script

DB_POD="postgres-0"
NAMESPACE="waqiti"

echo "Starting daily database maintenance..."

# 1. Update statistics
kubectl exec -n $NAMESPACE $DB_POD -- psql -U postgres -d waqiti -c "ANALYZE;"

# 2. Vacuum analyze high-traffic tables
TABLES=("transactions" "wallets" "users" "audit_logs")
for table in "${TABLES[@]}"; do
    echo "Vacuuming $table..."
    kubectl exec -n $NAMESPACE $DB_POD -- psql -U postgres -d waqiti -c "VACUUM ANALYZE $table;"
done

# 3. Check for bloated tables
kubectl exec -n $NAMESPACE $DB_POD -- psql -U postgres -d waqiti -c "
SELECT schemaname, tablename, 
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
       pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) as table_size
FROM pg_tables 
WHERE schemaname = 'public' 
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC 
LIMIT 10;"

# 4. Check for unused indexes
kubectl exec -n $NAMESPACE $DB_POD -- psql -U postgres -d waqiti -c "
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY pg_relation_size(indexrelid) DESC;"

echo "Daily database maintenance completed."
```

#### Weekly Database Optimization
```bash
#!/bin/bash
# Weekly database optimization

echo "Starting weekly database optimization..."

# 1. Reindex heavily used indexes
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "REINDEX INDEX CONCURRENTLY idx_transactions_user_id;"
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "REINDEX INDEX CONCURRENTLY idx_transactions_created_at;"

# 2. Check for missing indexes
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT schemaname, tablename, seq_scan, seq_tup_read, 
       seq_tup_read/seq_scan as avg_tup_per_scan
FROM pg_stat_user_tables
WHERE seq_scan > 0
ORDER BY seq_tup_read DESC
LIMIT 10;"

# 3. Update table statistics
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "ANALYZE VERBOSE;"

# 4. Check connection usage patterns
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "
SELECT usename, application_name, count(*), max(backend_start), max(state_change)
FROM pg_stat_activity
WHERE state != 'idle'
GROUP BY usename, application_name
ORDER BY count DESC;"

echo "Weekly database optimization completed."
```

### 3. Security Maintenance

#### Security Update Procedure
```bash
#!/bin/bash
# Security update procedure

echo "Starting security updates..."

# 1. Update base images
docker pull waqiti/api-gateway:latest
docker pull waqiti/payment-service:latest
docker pull waqiti/wallet-service:latest

# 2. Scan images for vulnerabilities
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy waqiti/api-gateway:latest
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy waqiti/payment-service:latest

# 3. Update Kubernetes security policies
kubectl apply -f /security/network-policies.yaml
kubectl apply -f /security/pod-security-policies.yaml

# 4. Rotate secrets (quarterly)
if [ "$(date +%d)" -eq 01 ] && [ $(($(date +%m) % 3)) -eq 1 ]; then
    echo "Rotating secrets..."
    ./scripts/rotate-secrets.sh
fi

# 5. Update certificates (if expiring within 30 days)
./scripts/check-certificate-expiry.sh

echo "Security updates completed."
```

#### Certificate Management
```bash
#!/bin/bash
# Certificate management

echo "Checking certificate expiry..."

# Check TLS certificates
kubectl get secrets -n waqiti -o json | jq -r '.items[] | select(.type=="kubernetes.io/tls") | .metadata.name' | while read secret; do
    EXPIRY=$(kubectl get secret $secret -n waqiti -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -enddate | cut -d= -f2)
    EXPIRY_EPOCH=$(date -d "$EXPIRY" +%s)
    NOW_EPOCH=$(date +%s)
    DAYS_LEFT=$(( ($EXPIRY_EPOCH - $NOW_EPOCH) / 86400 ))
    
    if [ $DAYS_LEFT -lt 30 ]; then
        echo "Certificate $secret expires in $DAYS_LEFT days - renewal required"
        # Trigger certificate renewal
        ./scripts/renew-certificate.sh $secret
    fi
done
```

### 4. Performance Optimization

#### Performance Analysis Script
```bash
#!/bin/bash
# Weekly performance analysis

echo "Starting performance analysis..."

# 1. Analyze database performance
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT query, calls, total_time/1000 as total_time_seconds, 
       mean_time/1000 as mean_time_seconds, 
       100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements 
WHERE calls > 100
ORDER BY total_time DESC 
LIMIT 20;"

# 2. Check slow queries
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query 
FROM pg_stat_activity 
WHERE (now() - pg_stat_activity.query_start) > interval '1 minute';"

# 3. Analyze application performance
kubectl exec -n waqiti deployment/payment-service -- curl -s http://localhost:9090/actuator/prometheus | grep -E "(http_server_requests_seconds|jvm_memory_used_bytes|jvm_gc_pause_seconds)"

# 4. Check resource utilization trends
kubectl top pods -n waqiti --sort-by=memory
kubectl top pods -n waqiti --sort-by=cpu

echo "Performance analysis completed."
```

### 5. Backup and Recovery Maintenance

#### Backup Verification
```bash
#!/bin/bash
# Daily backup verification

echo "Verifying daily backups..."

# 1. Check backup completion
LATEST_BACKUP=$(aws s3 ls s3://waqiti-backups-prod/ --recursive | grep $(date +%Y%m%d) | tail -1)
if [ -z "$LATEST_BACKUP" ]; then
    echo "ERROR: No backup found for today"
    exit 1
fi

# 2. Test backup restoration (weekly on Sunday)
if [ "$(date +%u)" -eq 7 ]; then
    echo "Testing backup restoration..."
    
    # Create test database
    kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "CREATE DATABASE waqiti_restore_test;"
    
    # Download and restore backup
    BACKUP_FILE=$(echo $LATEST_BACKUP | awk '{print $4}')
    aws s3 cp "s3://waqiti-backups-prod/$BACKUP_FILE" /tmp/
    
    # Decrypt and restore
    openssl enc -aes-256-cbc -d -in "/tmp/$(basename $BACKUP_FILE)" -out /tmp/restore-test.sql.gz -pass file:/etc/waqiti/backup-encryption.key
    gunzip /tmp/restore-test.sql.gz
    
    kubectl exec -i postgres-0 -n waqiti -- psql -U postgres -d waqiti_restore_test < /tmp/restore-test.sql
    
    # Verify restoration
    RECORD_COUNT=$(kubectl exec postgres-0 -n waqiti -- psql -U postgres -d waqiti_restore_test -t -c "SELECT COUNT(*) FROM users;")
    if [ $RECORD_COUNT -gt 0 ]; then
        echo "Backup restoration test successful - $RECORD_COUNT records restored"
    else
        echo "ERROR: Backup restoration test failed"
        exit 1
    fi
    
    # Cleanup test database
    kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "DROP DATABASE waqiti_restore_test;"
    rm -f /tmp/restore-test.sql /tmp/$(basename $BACKUP_FILE)
fi

echo "Backup verification completed."
```

### 6. Monitoring and Alerting Maintenance

#### Monitoring Health Check
```bash
#!/bin/bash
# Weekly monitoring system check

echo "Checking monitoring systems..."

# 1. Verify Prometheus is collecting metrics
PROMETHEUS_TARGETS=$(kubectl exec -n monitoring prometheus-0 -- wget -qO- http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length')
if [ $PROMETHEUS_TARGETS -lt 10 ]; then
    echo "WARNING: Prometheus has only $PROMETHEUS_TARGETS active targets"
fi

# 2. Check Grafana dashboards
curl -f https://grafana.example.com/api/health

# 3. Verify alerting rules
kubectl exec -n monitoring prometheus-0 -- wget -qO- http://localhost:9090/api/v1/rules | jq '.data.groups[].rules[] | select(.alerts != null) | .alerts | length' | awk '{sum+=$1} END {print "Active alerts: " sum}'

# 4. Test alert delivery
curl -X POST -H 'Content-type: application/json' \
    --data '{"text":"Test alert from monitoring maintenance"}' \
    $SLACK_WEBHOOK_URL

echo "Monitoring system check completed."
```

## Maintenance Windows

### Scheduled Maintenance Communication
```bash
#!/bin/bash
# Maintenance window communication

MAINTENANCE_START="2024-08-25 01:00 UTC"
MAINTENANCE_END="2024-08-25 05:00 UTC"
MAINTENANCE_REASON="Monthly security updates and performance optimization"

# Update status page
curl -X POST https://api.statuspage.io/v1/pages/PAGE_ID/incidents \
  -H "Authorization: OAuth TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"incident\": {
      \"name\": \"Scheduled Maintenance\",
      \"status\": \"scheduled\",
      \"impact_override\": \"maintenance\",
      \"scheduled_for\": \"$MAINTENANCE_START\",
      \"scheduled_until\": \"$MAINTENANCE_END\",
      \"body\": \"$MAINTENANCE_REASON\"
    }
  }"

# Send customer notifications
cat << EOF | mail -s "Scheduled Maintenance - Waqiti Platform" customers@example.com
Dear Waqiti Users,

We will be performing scheduled maintenance on the Waqiti platform:

Start Time: $MAINTENANCE_START
End Time: $MAINTENANCE_END
Expected Impact: Service may be temporarily unavailable

Reason: $MAINTENANCE_REASON

We apologize for any inconvenience and appreciate your understanding.

Best regards,
The Waqiti Team
EOF
```

### Emergency Maintenance
```bash
#!/bin/bash
# Emergency maintenance procedure

echo "EMERGENCY MAINTENANCE INITIATED"

# 1. Immediate notification
curl -X POST -H 'Content-type: application/json' \
    --data '{"text":"🚨 EMERGENCY MAINTENANCE: Waqiti platform - immediate action required"}' \
    $EMERGENCY_SLACK_WEBHOOK

# 2. Enable maintenance mode
kubectl patch ingress waqiti-ingress -n waqiti --patch '
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: maintenance-page
            port:
              number: 80
'

# 3. Scale down non-critical services
kubectl scale deployment notification-service --replicas=0 -n waqiti
kubectl scale deployment analytics-service --replicas=0 -n waqiti

# 4. Preserve critical logs
kubectl logs -n waqiti deployment/payment-service > /emergency-logs/payment-service-$(date +%Y%m%d-%H%M%S).log
kubectl logs -n waqiti deployment/wallet-service > /emergency-logs/wallet-service-$(date +%Y%m%d-%H%M%S).log

echo "Emergency maintenance mode activated"
```

## Quality Assurance

### Pre-Maintenance Testing
- [ ] Backup and recovery procedures tested
- [ ] Rollback procedures validated
- [ ] Communication plans reviewed
- [ ] Team coordination confirmed
- [ ] Emergency contacts updated

### Post-Maintenance Verification
- [ ] All services running normally
- [ ] Performance metrics within normal ranges
- [ ] No error rate increase
- [ ] Customer-facing functionality verified
- [ ] Monitoring and alerting operational

### Maintenance Metrics
- **MTTR** (Mean Time To Recovery): < 30 minutes
- **Maintenance Window Adherence**: 95%
- **Failed Maintenance Rate**: < 5%
- **Customer Impact Duration**: < 10 minutes

## Documentation and Reporting

### Maintenance Reports
```bash
#!/bin/bash
# Generate maintenance report

cat << EOF > maintenance-report-$(date +%Y%m%d).md
# Waqiti Platform Maintenance Report
Date: $(date +%Y-%m-%d)

## Summary
- Maintenance Duration: X hours
- Services Affected: [List services]
- Impact Level: [Low/Medium/High]
- Issues Encountered: [None/List issues]

## Activities Completed
- [ ] Application updates
- [ ] Database maintenance
- [ ] Security updates
- [ ] Performance optimization
- [ ] Backup verification

## Metrics
- Downtime: X minutes
- Error Rate During Maintenance: X%
- Recovery Time: X minutes

## Issues and Resolutions
[Document any issues encountered and how they were resolved]

## Recommendations
[Future improvements or actions needed]
EOF
```

### Change Log Maintenance
All maintenance activities should be logged in the change management system with:
- Date and time
- Personnel involved
- Changes made
- Testing performed
- Rollback plan
- Results and any issues

---
**Document Version**: 1.0  
**Last Updated**: 2024-08-21  
**Next Review**: 2024-11-21  
**Owner**: Platform Team