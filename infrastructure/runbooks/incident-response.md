# Waqiti P2P Platform - Incident Response Runbook

## Overview
This runbook provides step-by-step procedures for responding to incidents in the Waqiti P2P payment platform. Follow these procedures to ensure rapid response, proper escalation, and effective resolution.

## Incident Classification

### Severity Levels

#### CRITICAL (P1) - Revenue Impact
- Payment system completely down
- Complete service outage
- Security breach detected
- Financial discrepancies
- **Response Time**: < 15 minutes
- **Resolution Target**: < 2 hours

#### HIGH (P2) - Service Degraded
- High error rates (>5%)
- Performance severely impacted
- Partial service outage
- Authentication issues
- **Response Time**: < 30 minutes
- **Resolution Target**: < 4 hours

#### MEDIUM (P3) - Service Issues
- Elevated error rates (2-5%)
- Performance degraded
- Non-critical service affected
- **Response Time**: < 1 hour
- **Resolution Target**: < 8 hours

#### LOW (P4) - Minor Issues
- Minor performance issues
- Cosmetic problems
- Documentation issues
- **Response Time**: < 4 hours
- **Resolution Target**: < 24 hours

## Incident Response Team

### Primary Contacts
- **Platform Lead**: platform-lead@example.com
- **Security Team**: security@example.com
- **Product Manager**: product@example.com
- **DevOps Engineer**: devops@example.com

### Escalation Matrix
1. **Level 1**: Platform Engineer
2. **Level 2**: Senior Platform Engineer + Product Manager
3. **Level 3**: Platform Lead + CTO
4. **Level 4**: CEO (for business-critical issues)

## Common Incident Scenarios

### 1. Payment System Down (CRITICAL)

#### Symptoms
- Alert: `WaqitiPaymentSystemDown`
- No payments processing
- API returning 503 errors
- High error rates in payment-service

#### Investigation Steps
```bash
# 1. Check service status
kubectl get pods -n waqiti -l component=payment-service

# 2. Check service logs
kubectl logs -n waqiti deployment/payment-service --tail=100 -f

# 3. Check database connectivity
kubectl exec -n waqiti deployment/payment-service -- nc -z postgres 5432

# 4. Check external dependencies
curl -I https://api.stripe.com/v1/charges
curl -I https://api.paystack.co/transaction
```

#### Resolution Steps
1. **Immediate Response**
   - Acknowledge incident in PagerDuty
   - Post in #incidents Slack channel
   - Start incident bridge call

2. **Service Recovery**
   ```bash
   # Scale up replicas if pods are failing
   kubectl scale deployment payment-service --replicas=10 -n waqiti
   
   # Restart deployment if needed
   kubectl rollout restart deployment/payment-service -n waqiti
   
   # Check autoscaler limits
   kubectl describe hpa payment-service-hpa -n waqiti
   ```

3. **Database Issues**
   ```bash
   # Check PostgreSQL status
   kubectl exec -n waqiti postgres-0 -- pg_isready
   
   # Check connection pool
   kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT count(*) FROM pg_stat_activity;"
   
   # Check for long-running queries
   kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT query, query_start FROM pg_stat_activity WHERE state = 'active' AND query_start < now() - interval '5 minutes';"
   ```

4. **External API Issues**
   - Check Stripe/PayStack status pages
   - Implement circuit breaker if needed
   - Switch to backup payment processor

#### Communication Template
```
🚨 INCIDENT: Payment System Down (P1)
Status: Investigating
Impact: All payments are currently failing
ETA: Under investigation
Lead: @platform-engineer
Bridge: [Conference Link]
```

### 2. High Transaction Failure Rate (HIGH)

#### Symptoms
- Alert: `WaqitiHighTransactionFailureRate`
- Transaction success rate < 95%
- Increased customer complaints

#### Investigation Steps
```bash
# 1. Check transaction metrics
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep waqiti_transactions_total

# 2. Analyze error patterns
kubectl logs -n waqiti deployment/payment-service --tail=1000 | grep ERROR | head -20

# 3. Check external API rates
kubectl logs -n waqiti deployment/payment-service | grep "stripe\|paystack" | tail -50
```

#### Resolution Steps
1. Identify failing transaction types
2. Check external API limits and quotas
3. Review recent deployments
4. Scale services if needed
5. Implement temporary workarounds

### 3. Database Connection Issues (HIGH)

#### Symptoms
- Alert: `WaqitiDatabaseConnectionPoolExhaustion`
- Services unable to connect to database
- Connection timeout errors

#### Investigation Steps
```bash
# 1. Check connection pool status
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# 2. Check for connection leaks
kubectl logs -n waqiti deployment/payment-service | grep "connection"

# 3. Check database performance
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT query, query_start, state FROM pg_stat_activity WHERE state = 'active';"
```

#### Resolution Steps
```bash
# 1. Kill long-running queries
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'active' AND query_start < now() - interval '10 minutes';"

# 2. Restart connection pools
kubectl rollout restart deployment/payment-service -n waqiti
kubectl rollout restart deployment/wallet-service -n waqiti

# 3. Scale database connections
kubectl patch configmap waqiti-config -n waqiti --patch '{"data":{"database.max-connections":"200"}}'
```

### 4. Security Breach Detected (CRITICAL)

#### Symptoms
- Alert: `WaqitiSecurityBreachDetected`
- Suspicious access patterns
- Unauthorized API calls

#### Immediate Response
1. **DO NOT** restart services immediately
2. Preserve logs and evidence
3. Isolate affected systems
4. Contact security team immediately

#### Investigation Steps
```bash
# 1. Preserve current logs
kubectl logs -n waqiti deployment/api-gateway > /tmp/security-incident-$(date +%Y%m%d-%H%M%S).log

# 2. Check access logs
kubectl logs -n waqiti deployment/api-gateway | grep -E "(401|403|419|429)" | tail -100

# 3. Analyze IP patterns
kubectl logs -n waqiti deployment/api-gateway | awk '{print $1}' | sort | uniq -c | sort -rn | head -20
```

#### Containment Steps
```bash
# 1. Block suspicious IPs
kubectl patch networkpolicy waqiti-network-policy -n waqiti --patch '{"spec":{"ingress":[{"from":[{"ipBlock":{"cidr":"0.0.0.0/0","except":["SUSPICIOUS_IP/32"]}}]}]}}'

# 2. Rotate API keys (coordinate with security team)
kubectl create secret generic waqiti-api-keys-new --from-literal=stripe-api-key="NEW_KEY" -n waqiti

# 3. Force user re-authentication
kubectl exec -n waqiti deployment/api-gateway -- curl -X POST http://localhost:8080/admin/invalidate-sessions
```

### 5. Memory/CPU Overload (MEDIUM)

#### Symptoms
- Alert: `WaqitiHighCPUUsage` or `WaqitiHighMemoryUsage`
- Slow response times
- Pod restarts due to OOMKilled

#### Investigation Steps
```bash
# 1. Check resource usage
kubectl top pods -n waqiti --sort-by=memory
kubectl top pods -n waqiti --sort-by=cpu

# 2. Check pod events
kubectl describe pods -n waqiti | grep -A 10 Events

# 3. Analyze memory usage patterns
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.memory.used
```

#### Resolution Steps
```bash
# 1. Immediate relief - scale horizontally
kubectl scale deployment payment-service --replicas=8 -n waqiti

# 2. Check for memory leaks
kubectl logs -n waqiti deployment/payment-service | grep -i "outofmemory\|heap"

# 3. Increase resource limits (temporary)
kubectl patch deployment payment-service -n waqiti --patch '{"spec":{"template":{"spec":{"containers":[{"name":"payment-service","resources":{"limits":{"memory":"4Gi","cpu":"2000m"}}}]}}}}'

# 4. Force garbage collection (Java apps)
kubectl exec -n waqiti deployment/payment-service -- jcmd 1 GC.run_finalization
```

## Post-Incident Procedures

### Immediate Post-Resolution (Within 1 hour)
1. **Confirm Full Service Restoration**
   ```bash
   # Run health checks
   curl -f https://api.example.com/health
   curl -f https://api.example.com/actuator/health
   
   # Check all metrics are green
   # Monitor error rates for 30 minutes
   ```

2. **Customer Communication**
   - Update status page
   - Send customer notifications if needed
   - Post resolution in #incidents channel

3. **Document Timeline**
   - Record incident start/end times
   - Document actions taken
   - Note root cause if identified

### Post-Incident Review (Within 48 hours)
1. **Conduct Blameless Post-Mortem**
   - Timeline of events
   - Root cause analysis
   - What went well
   - What could be improved

2. **Action Items**
   - Preventive measures
   - Monitoring improvements
   - Process improvements
   - Tool/infrastructure changes

3. **Knowledge Sharing**
   - Update runbooks based on learnings
   - Share lessons learned with team
   - Update monitoring and alerting

## Emergency Contacts

### Internal Team
- **Platform Team**: +1-555-0100 (24/7)
- **Security Team**: +1-555-0101 (24/7)
- **Product Team**: +1-555-0102 (Business hours)

### External Vendors
- **AWS Support**: Enterprise Support (Case priority: High)
- **Stripe Support**: +1-888-926-2961
- **PayStack Support**: support@paystack.com

### Communication Channels
- **Slack**: #incidents (P1-P2), #platform (P3-P4)
- **PagerDuty**: Auto-escalation enabled
- **Status Page**: https://status.example.com (auto-update via API)

## Monitoring Dashboards

### Primary Dashboard
- **Grafana**: https://grafana.example.com/d/waqiti-production-dashboard
- **Key Metrics**: Transaction success rate, response time, error rate, throughput

### Infrastructure Dashboard
- **Kubernetes**: Resource usage, pod health, node status
- **Database**: Connection pools, query performance, replication lag
- **External APIs**: Response times, error rates, quotas

## Useful Commands Reference

### Kubernetes Operations
```bash
# Get all resources in namespace
kubectl get all -n waqiti

# Check pod logs with timestamp
kubectl logs -n waqiti deployment/payment-service --timestamps=true --tail=100

# Port forward for local debugging
kubectl port-forward -n waqiti deployment/payment-service 8080:8080

# Execute commands in pod
kubectl exec -it -n waqiti deployment/payment-service -- /bin/bash

# Scale deployment
kubectl scale deployment payment-service --replicas=5 -n waqiti

# Check resource usage
kubectl top pods -n waqiti
kubectl top nodes

# Describe resources for events
kubectl describe pod -n waqiti payment-service-xxx
kubectl describe deployment -n waqiti payment-service
```

### Database Operations
```bash
# Connect to database
kubectl exec -it -n waqiti postgres-0 -- psql -U postgres -d waqiti

# Check active connections
SELECT count(*), state FROM pg_stat_activity GROUP BY state;

# Check slow queries
SELECT query, query_start FROM pg_stat_activity WHERE state = 'active' AND query_start < now() - interval '30 seconds';

# Check table sizes
SELECT schemaname,tablename,pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size FROM pg_tables ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

### Monitoring and Metrics
```bash
# Check Prometheus metrics
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep waqiti_

# Check application health
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:8080/actuator/health

# Check JVM metrics (Java apps)
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.memory.used
```

## Document Version
- **Version**: 1.0
- **Last Updated**: 2024-08-21
- **Next Review**: 2024-11-21
- **Owner**: Platform Team