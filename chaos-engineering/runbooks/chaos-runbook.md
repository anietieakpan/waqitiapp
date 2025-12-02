# Chaos Engineering Runbook for Waqiti P2P Payment Application

## Overview
This runbook provides guidance for conducting chaos engineering experiments on the Waqiti platform. It includes pre-flight checks, experiment execution procedures, monitoring guidelines, and rollback procedures.

## Table of Contents
1. [Pre-Flight Checklist](#pre-flight-checklist)
2. [Experiment Categories](#experiment-categories)
3. [Execution Procedures](#execution-procedures)
4. [Monitoring During Chaos](#monitoring-during-chaos)
5. [Rollback Procedures](#rollback-procedures)
6. [Post-Experiment Analysis](#post-experiment-analysis)
7. [Common Issues and Solutions](#common-issues-and-solutions)

## Pre-Flight Checklist

Before conducting any chaos experiment:

- [ ] Verify all services are healthy
  ```bash
  kubectl get pods -n waqiti | grep -v Running
  ```

- [ ] Check current error rates are below baseline
  ```bash
  kubectl exec -n monitoring prometheus-0 -- promtool query instant \
    'sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))'
  ```

- [ ] Ensure monitoring and alerting are functional
  ```bash
  kubectl get pods -n monitoring
  kubectl get pods -n litmus
  ```

- [ ] Verify backups are recent
  ```bash
  kubectl exec -n waqiti postgres-primary-0 -- pg_dump -U postgres waqiti_db | head -n 20
  ```

- [ ] Confirm rollback procedures are ready
- [ ] Notify relevant teams about the chaos experiment
- [ ] Ensure incident response team is available

## Experiment Categories

### 1. Pod-Level Chaos
Experiments that affect individual pods or containers.

#### Pod Delete
```bash
kubectl apply -f experiments/payment-service-chaos.yaml
kubectl get chaosengine -n waqiti
```

#### CPU Stress
```bash
kubectl apply -f - <<EOF
apiVersion: litmuschaos.io/v1alpha1
kind: ChaosEngine
metadata:
  name: payment-cpu-chaos
  namespace: waqiti
spec:
  appinfo:
    appns: waqiti
    applabel: 'app=payment-service'
    appkind: 'deployment'
  chaosServiceAccount: payment-service-chaos-sa
  experiments:
    - name: pod-cpu-hog
      spec:
        components:
          env:
            - name: CPU_CORES
              value: '1'
            - name: CPU_LOAD
              value: '80'
            - name: TOTAL_CHAOS_DURATION
              value: '180'
EOF
```

### 2. Network Chaos
Experiments that affect network communication.

#### Network Latency
```bash
kubectl apply -f - <<EOF
apiVersion: litmuschaos.io/v1alpha1
kind: ChaosEngine
metadata:
  name: network-latency-chaos
  namespace: waqiti
spec:
  appinfo:
    appns: waqiti
    applabel: 'app=transaction-service'
    appkind: 'deployment'
  chaosServiceAccount: litmus-admin
  experiments:
    - name: pod-network-latency
      spec:
        components:
          env:
            - name: NETWORK_LATENCY
              value: '2000'
            - name: JITTER
              value: '500'
            - name: TOTAL_CHAOS_DURATION
              value: '300'
EOF
```

### 3. Infrastructure Chaos
Experiments that affect infrastructure components.

#### Node CPU Hog
```bash
kubectl apply -f experiments/infrastructure-chaos.yaml
kubectl get chaosengine -n litmus
```

## Execution Procedures

### Starting an Experiment

1. **Select the experiment**
   ```bash
   EXPERIMENT_NAME="payment-service-pod-delete"
   NAMESPACE="waqiti"
   ```

2. **Apply the chaos experiment**
   ```bash
   kubectl apply -f experiments/${EXPERIMENT_NAME}.yaml
   ```

3. **Monitor the experiment status**
   ```bash
   watch kubectl get chaosengine,chaosresult -n ${NAMESPACE}
   ```

4. **Check service health**
   ```bash
   kubectl get pods -n ${NAMESPACE} -l app=payment-service
   ```

### Monitoring During Chaos

Open multiple terminal windows to monitor:

**Terminal 1 - Chaos Status:**
```bash
watch -n 2 'kubectl get chaosengine,chaosresult -n waqiti'
```

**Terminal 2 - Service Health:**
```bash
watch -n 2 'kubectl get pods -n waqiti | grep -E "(payment|transaction|user|wallet)"'
```

**Terminal 3 - Error Rates:**
```bash
while true; do
  kubectl exec -n monitoring prometheus-0 -- promtool query instant \
    'sum(rate(http_requests_total{status=~"5.."}[1m])) by (service)'
  sleep 5
done
```

**Terminal 4 - Payment Success Rate:**
```bash
while true; do
  kubectl exec -n monitoring prometheus-0 -- promtool query instant \
    '(sum(rate(payment_service_payments_successful_total[1m])) / sum(rate(payment_service_payments_total[1m]))) * 100'
  sleep 5
done
```

### Grafana Dashboard
Open Grafana dashboard: http://grafana.waqiti.local/d/chaos-overview

## Rollback Procedures

### Immediate Rollback
If system degradation exceeds acceptable levels:

1. **Stop the chaos experiment**
   ```bash
   kubectl delete chaosengine ${EXPERIMENT_NAME}-chaos -n ${NAMESPACE}
   ```

2. **Verify experiment has stopped**
   ```bash
   kubectl get chaosresult -n ${NAMESPACE}
   ```

3. **Check pod recovery**
   ```bash
   kubectl get pods -n ${NAMESPACE} -w
   ```

4. **Scale up if needed**
   ```bash
   kubectl scale deployment payment-service -n waqiti --replicas=5
   ```

### Emergency Recovery

If services don't recover automatically:

1. **Restart affected deployments**
   ```bash
   kubectl rollout restart deployment payment-service -n waqiti
   kubectl rollout restart deployment transaction-service -n waqiti
   ```

2. **Clear corrupted cache**
   ```bash
   kubectl exec -n waqiti redis-master-0 -- redis-cli FLUSHALL
   ```

3. **Reset circuit breakers**
   ```bash
   kubectl exec -n waqiti deployment/payment-service -- curl -X POST http://localhost:8080/actuator/circuitbreakers/reset
   ```

## Post-Experiment Analysis

### 1. Collect Metrics
```bash
# Export chaos results
kubectl get chaosresult ${EXPERIMENT_NAME}-${EXPERIMENT_TYPE} -n ${NAMESPACE} -o yaml > results/${EXPERIMENT_NAME}-$(date +%Y%m%d-%H%M%S).yaml

# Export Prometheus metrics
curl -G http://prometheus.monitoring.svc.cluster.local:9090/api/v1/query_range \
  --data-urlencode 'query=rate(http_requests_total[5m])' \
  --data-urlencode 'start='$(date -d '1 hour ago' +%s) \
  --data-urlencode 'end='$(date +%s) \
  --data-urlencode 'step=15s' > metrics/${EXPERIMENT_NAME}-$(date +%Y%m%d-%H%M%S).json
```

### 2. Generate Report
```bash
cat > reports/${EXPERIMENT_NAME}-$(date +%Y%m%d).md << EOF
# Chaos Experiment Report

**Experiment:** ${EXPERIMENT_NAME}
**Date:** $(date)
**Duration:** ${DURATION}

## Observations
- Payment success rate: ${SUCCESS_RATE}%
- Peak error rate: ${ERROR_RATE}%
- Recovery time: ${RECOVERY_TIME}s

## Findings
[Document any issues discovered]

## Recommendations
[List improvements needed]
EOF
```

### 3. Update Runbooks
Based on findings, update:
- Service runbooks
- Alert thresholds
- Scaling policies
- Circuit breaker settings

## Common Issues and Solutions

### Issue: Chaos experiment stuck in "Running" state
**Solution:**
```bash
# Force delete the chaos engine
kubectl delete chaosengine ${EXPERIMENT_NAME}-chaos -n ${NAMESPACE} --force --grace-period=0

# Clean up chaos pods
kubectl delete pods -n ${NAMESPACE} -l chaosUID
```

### Issue: Service not recovering after chaos
**Solution:**
```bash
# Check for stuck pods
kubectl get pods -n ${NAMESPACE} | grep -E "(Terminating|CrashLoopBackOff)"

# Force delete stuck pods
kubectl delete pod ${POD_NAME} -n ${NAMESPACE} --force --grace-period=0

# Check PVC status
kubectl get pvc -n ${NAMESPACE}
```

### Issue: Metrics not available during chaos
**Solution:**
```bash
# Check Prometheus targets
kubectl port-forward -n monitoring prometheus-0 9090:9090
# Open http://localhost:9090/targets

# Restart Prometheus if needed
kubectl delete pod prometheus-0 -n monitoring
```

### Issue: Kafka not recovering
**Solution:**
```bash
# Check Kafka cluster status
kubectl exec -n waqiti kafka-0 -- kafka-broker-api-versions.sh --bootstrap-server kafka-0.kafka-headless:9092

# Restart Kafka brokers in sequence
for i in 0 1 2; do
  kubectl delete pod kafka-$i -n waqiti
  sleep 30
done
```

## Best Practices

1. **Start Small**: Begin with less critical services and short durations
2. **Gradual Escalation**: Increase chaos intensity gradually
3. **Time Windows**: Run experiments during low-traffic periods initially
4. **Documentation**: Document all findings and share with the team
5. **Automation**: Automate common chaos experiments for regular testing
6. **Gamedays**: Schedule regular chaos gamedays with all teams

## Chaos Experiment Schedule

| Day | Time | Experiment | Duration | Team |
|-----|------|-----------|----------|------|
| Mon | 10:00 AM | payment-service-pod-kill | 5 min | Platform |
| Tue | 02:00 PM | network-latency | 10 min | Network |
| Wed | 11:00 AM | redis-cache-flush | 2 min | Backend |
| Thu | 03:00 PM | kafka-broker-failure | 5 min | Data |
| Fri | 10:00 AM | Game Day | 2 hours | All |

## Contact Information

**Chaos Engineering Team:**
- Lead: chaos-lead@example.com
- Slack: #chaos-engineering
- PagerDuty: chaos-oncall

**Incident Response:**
- Hotline: +1-555-CHAOS-01
- Slack: #incident-response
- Wiki: https://wiki.example.com/chaos-engineering