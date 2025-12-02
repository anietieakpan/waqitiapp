# Waqiti P2P Platform - Troubleshooting Guide

## Overview
This guide provides systematic troubleshooting procedures for common issues in the Waqiti P2P payment platform. Use this guide to diagnose and resolve problems efficiently.

## General Troubleshooting Methodology

### Step-by-Step Approach
1. **Identify symptoms** - What is the observed behavior?
2. **Gather information** - Collect logs, metrics, and system state
3. **Analyze patterns** - Look for correlations and timing
4. **Form hypothesis** - What could be causing the issue?
5. **Test hypothesis** - Implement targeted fixes
6. **Verify solution** - Confirm the issue is resolved
7. **Document findings** - Update knowledge base

### Information Gathering Checklist
- [ ] Recent deployments or changes
- [ ] Error messages and stack traces
- [ ] System metrics and performance data
- [ ] External service status
- [ ] Network connectivity
- [ ] Resource utilization

## Common Issues and Solutions

### 1. Application Won't Start

#### Symptoms
- Pods stuck in CrashLoopBackOff
- Container exits immediately
- Init containers failing
- Health checks failing

#### Diagnostic Commands
```bash
# Check pod status and events
kubectl get pods -n waqiti
kubectl describe pod <pod-name> -n waqiti

# Check container logs
kubectl logs <pod-name> -n waqiti -c <container-name>
kubectl logs <pod-name> -n waqiti --previous  # Previous container instance

# Check resource constraints
kubectl top pod <pod-name> -n waqiti
kubectl describe node <node-name>
```

#### Common Causes and Solutions

##### Configuration Issues
```bash
# Check ConfigMaps and Secrets
kubectl get configmap waqiti-config -n waqiti -o yaml
kubectl get secret waqiti-database-secrets -n waqiti -o yaml

# Validate environment variables
kubectl exec -n waqiti deployment/payment-service -- env | grep SPRING_

# Test configuration locally
kubectl exec -it deployment/payment-service -n waqiti -- /bin/bash
```

##### Resource Constraints
```bash
# Check resource requests vs limits
kubectl describe deployment payment-service -n waqiti | grep -A 10 "Requests\|Limits"

# Check node resources
kubectl describe nodes | grep -A 10 "Allocated resources"

# Adjust resources if needed
kubectl patch deployment payment-service -n waqiti --patch '
spec:
  template:
    spec:
      containers:
      - name: payment-service
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
'
```

##### Dependency Issues
```bash
# Check if dependencies are ready
kubectl get pods -n waqiti | grep -E "(postgres|redis|kafka)"

# Test connectivity to dependencies
kubectl exec -n waqiti deployment/payment-service -- nc -zv postgres 5432
kubectl exec -n waqiti deployment/payment-service -- nc -zv redis 6379
kubectl exec -n waqiti deployment/payment-service -- nc -zv kafka 9092

# Check DNS resolution
kubectl exec -n waqiti deployment/payment-service -- nslookup postgres.waqiti.svc.cluster.local
```

### 2. High Response Times

#### Symptoms
- API responses taking >2 seconds
- Timeout errors
- Poor user experience
- High P95/P99 response times

#### Diagnostic Commands
```bash
# Check application metrics
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep http_request_duration

# Check resource utilization
kubectl top pods -n waqiti --sort-by=cpu
kubectl top pods -n waqiti --sort-by=memory

# Check database performance
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT query, query_start, now() - query_start as runtime
FROM pg_stat_activity
WHERE state = 'active'
AND now() - query_start > interval '1 second'
ORDER BY runtime DESC;"
```

#### Common Causes and Solutions

##### Database Performance Issues
```bash
# Check slow queries
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT query, mean_time, calls, total_time
FROM pg_stat_statements
WHERE mean_time > 100
ORDER BY mean_time DESC
LIMIT 10;"

# Check missing indexes
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT schemaname, tablename, seq_scan, seq_tup_read, seq_tup_read/seq_scan as avg_tup_read
FROM pg_stat_user_tables
WHERE seq_scan > 0
ORDER BY seq_tup_read DESC
LIMIT 10;"

# Add missing indexes (example)
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
CREATE INDEX CONCURRENTLY idx_transactions_user_id_created_at ON transactions(user_id, created_at);"
```

##### Connection Pool Issues
```bash
# Check connection pool status
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/hikari.connections.active
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/hikari.connections.pending

# Increase connection pool size
kubectl patch configmap waqiti-config -n waqiti --patch '
data:
  spring.datasource.hikari.maximum-pool-size: "50"
  spring.datasource.hikari.minimum-idle: "10"
'

# Restart services to apply changes
kubectl rollout restart deployment/payment-service -n waqiti
```

##### JVM Performance Issues (Java Services)
```bash
# Check heap usage
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.memory.used | grep heap

# Check GC performance
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.gc.pause

# Tune JVM parameters
kubectl patch deployment payment-service -n waqiti --patch '
spec:
  template:
    spec:
      containers:
      - name: payment-service
        env:
        - name: JAVA_OPTS
          value: "-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
'
```

### 3. Database Connection Issues

#### Symptoms
- "Connection refused" errors
- "Too many connections" errors
- Services unable to start
- Intermittent database failures

#### Diagnostic Commands
```bash
# Check database pod status
kubectl get pods -n waqiti -l app=postgres

# Check database logs
kubectl logs -n waqiti postgres-0 --tail=100

# Check current connections
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "
SELECT count(*), state, usename, application_name
FROM pg_stat_activity
GROUP BY state, usename, application_name
ORDER BY count DESC;"

# Check connection limits
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SHOW max_connections;"
```

#### Common Solutions

##### Too Many Connections
```bash
# Kill idle connections
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE state = 'idle'
AND state_change < now() - interval '1 hour';"

# Increase max connections (temporary)
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "ALTER SYSTEM SET max_connections = 200;"
kubectl exec -n waqiti postgres-0 -- psql -U postgres -c "SELECT pg_reload_conf();"

# Review application connection pooling
kubectl describe configmap waqiti-config -n waqiti | grep -i pool
```

##### Connection Refused
```bash
# Check if PostgreSQL is running
kubectl exec -n waqiti postgres-0 -- ps aux | grep postgres

# Check PostgreSQL configuration
kubectl exec -n waqiti postgres-0 -- cat /var/lib/postgresql/data/postgresql.conf | grep listen_addresses

# Restart PostgreSQL if needed
kubectl delete pod postgres-0 -n waqiti
kubectl wait --for=condition=ready pod/postgres-0 --timeout=300s -n waqiti
```

### 4. External API Integration Issues

#### Symptoms
- Payment processing failures
- API timeout errors
- Authentication failures with external services
- Rate limiting errors

#### Diagnostic Commands
```bash
# Check external API connectivity
kubectl exec -n waqiti deployment/payment-service -- curl -I https://api.stripe.com/v1/charges
kubectl exec -n waqiti deployment/payment-service -- curl -I https://api.paystack.co/transaction

# Check API response times
kubectl exec -n waqiti deployment/payment-service -- time curl -s https://api.stripe.com/v1/charges -H "Authorization: Bearer sk_test_..."

# Check application logs for API errors
kubectl logs -n waqiti deployment/payment-service | grep -i "stripe\|paystack\|api" | tail -20
```

#### Common Solutions

##### API Authentication Issues
```bash
# Verify API keys
kubectl get secret waqiti-api-keys -n waqiti -o jsonpath='{.data.stripe-api-key}' | base64 -d | head -c 10

# Test API keys manually
kubectl exec -n waqiti deployment/payment-service -- curl -u sk_test_...: https://api.stripe.com/v1/charges

# Rotate API keys if needed
kubectl patch secret waqiti-api-keys -n waqiti --patch '{"data":{"stripe-api-key":"'$(echo -n "new_key" | base64)'"}}'
kubectl rollout restart deployment/payment-service -n waqiti
```

##### Rate Limiting Issues
```bash
# Check rate limit headers in logs
kubectl logs -n waqiti deployment/payment-service | grep -i "rate\|limit\|429"

# Implement circuit breaker pattern
# (This would be a code change, documented for dev team)

# Add retry logic with exponential backoff
# (This would be a code change, documented for dev team)

# Scale down request rate temporarily
kubectl patch deployment payment-service -n waqiti --patch '{"spec":{"replicas":2}}'
```

### 5. Memory and Resource Issues

#### Symptoms
- OutOfMemoryError exceptions
- Pods being OOMKilled
- High CPU usage
- Slow performance

#### Diagnostic Commands
```bash
# Check resource usage
kubectl top pods -n waqiti --sort-by=memory
kubectl top nodes

# Check pod resource limits
kubectl describe pod <pod-name> -n waqiti | grep -A 10 "Limits\|Requests"

# Check memory metrics
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.memory.used
```

#### Common Solutions

##### Memory Leaks (Java Applications)
```bash
# Generate heap dump for analysis
kubectl exec -n waqiti deployment/payment-service -- jcmd 1 GC.run_finalization
kubectl exec -n waqiti deployment/payment-service -- jcmd 1 VM.memory_summary

# Check for memory leaks in logs
kubectl logs -n waqiti deployment/payment-service | grep -i "memory\|heap\|gc"

# Increase heap size temporarily
kubectl patch deployment payment-service -n waqiti --patch '
spec:
  template:
    spec:
      containers:
      - name: payment-service
        env:
        - name: JAVA_OPTS
          value: "-Xmx4g -Xms2g"
        resources:
          limits:
            memory: "6Gi"
'
```

##### High CPU Usage
```bash
# Check CPU usage patterns
kubectl exec -n waqiti deployment/payment-service -- top -p 1

# Check for infinite loops or heavy processing
kubectl logs -n waqiti deployment/payment-service | grep -i "processing\|loop\|thread"

# Scale horizontally to distribute load
kubectl scale deployment payment-service --replicas=8 -n waqiti

# Check for CPU-intensive queries
kubectl exec -n waqiti postgres-0 -- psql -U postgres -d waqiti -c "
SELECT query, calls, total_time, mean_time
FROM pg_stat_statements
WHERE calls > 100
ORDER BY total_time DESC
LIMIT 10;"
```

### 6. Network and Connectivity Issues

#### Symptoms
- Services can't communicate
- DNS resolution failures
- Timeout errors
- Connection refused errors

#### Diagnostic Commands
```bash
# Test service connectivity
kubectl exec -n waqiti deployment/api-gateway -- curl -v http://payment-service:8080/actuator/health
kubectl exec -n waqiti deployment/api-gateway -- nslookup payment-service

# Check network policies
kubectl get networkpolicy -n waqiti

# Check service endpoints
kubectl get endpoints -n waqiti
```

#### Common Solutions

##### DNS Issues
```bash
# Check DNS configuration
kubectl exec -n waqiti deployment/api-gateway -- cat /etc/resolv.conf

# Test DNS resolution
kubectl exec -n waqiti deployment/api-gateway -- nslookup kubernetes.default.svc.cluster.local

# Restart DNS if needed
kubectl delete pods -n kube-system -l k8s-app=kube-dns
```

##### Service Mesh Issues (if using Istio)
```bash
# Check Envoy proxy logs
kubectl logs -n waqiti deployment/payment-service -c istio-proxy

# Check service mesh configuration
kubectl get virtualservices -n waqiti
kubectl get destinationrules -n waqiti

# Disable service mesh temporarily
kubectl label namespace waqiti istio-injection-
kubectl rollout restart deployment/payment-service -n waqiti
```

## Performance Optimization

### Database Optimization
```sql
-- Find missing indexes
SELECT schemaname, tablename, attname, n_distinct, correlation
FROM pg_stats
WHERE schemaname = 'public'
AND n_distinct > 100
AND correlation < 0.1;

-- Optimize frequently used queries
EXPLAIN ANALYZE SELECT * FROM transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT 10;

-- Update table statistics
ANALYZE transactions;
ANALYZE wallets;
ANALYZE users;
```

### Application Optimization
```bash
# Enable application profiling
kubectl patch deployment payment-service -n waqiti --patch '
spec:
  template:
    spec:
      containers:
      - name: payment-service
        env:
        - name: JAVA_OPTS
          value: "-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=profile.jfr"
'

# Check connection pooling efficiency
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/hikari.connections

# Monitor garbage collection
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/metrics/jvm.gc.pause
```

## Monitoring and Alerting

### Key Metrics to Monitor
- Response time (P50, P95, P99)
- Error rate
- Throughput (requests per second)
- Resource utilization (CPU, Memory)
- Database performance
- External API response times

### Useful Monitoring Queries
```bash
# Error rate
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep http_server_requests_seconds_count

# Response time percentiles
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep http_server_requests_seconds

# JVM metrics
kubectl exec -n waqiti deployment/payment-service -- curl http://localhost:9090/actuator/prometheus | grep jvm_
```

## Useful Commands Reference

### Kubernetes Troubleshooting
```bash
# Get all resources
kubectl get all -n waqiti

# Describe resource for events
kubectl describe <resource-type> <resource-name> -n waqiti

# Check resource usage
kubectl top pods -n waqiti
kubectl top nodes

# Port forward for debugging
kubectl port-forward -n waqiti deployment/payment-service 8080:8080

# Execute commands in pod
kubectl exec -it -n waqiti deployment/payment-service -- /bin/bash

# Copy files to/from pod
kubectl cp local-file waqiti/pod-name:/tmp/remote-file
```

### Log Analysis
```bash
# Search logs for errors
kubectl logs -n waqiti deployment/payment-service | grep -i error

# Follow logs in real-time
kubectl logs -f -n waqiti deployment/payment-service

# Get logs from multiple pods
kubectl logs -n waqiti -l component=payment-service

# Search logs with timestamp
kubectl logs -n waqiti deployment/payment-service --timestamps=true | grep "2024-08-21T10:"
```

### Database Troubleshooting
```sql
-- Check connection status
SELECT count(*), state FROM pg_stat_activity GROUP BY state;

-- Find long running queries
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';

-- Check table sizes
SELECT schemaname,tablename,pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check index usage
SELECT schemaname,tablename,indexname,idx_scan,idx_tup_read,idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

## Escalation Guidelines

### When to Escalate
- System-wide outage (>15 minutes)
- Data integrity issues
- Security incidents
- Unable to resolve within SLA timeframe

### Escalation Contacts
1. **Level 1**: Senior Engineer (+1-555-0100)
2. **Level 2**: Platform Lead (+1-555-0101)
3. **Level 3**: CTO (+1-555-0102)
4. **Security**: security@example.com (for security issues)

### Information to Include
- Incident timeline
- Steps taken so far
- Current system status
- Business impact assessment
- Logs and error messages

---
**Document Version**: 1.0  
**Last Updated**: 2024-08-21  
**Next Review**: 2024-11-21  
**Owner**: Platform Team