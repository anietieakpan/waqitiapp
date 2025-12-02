# Waqiti Kubernetes Auto-scaling Configuration

## Overview

Comprehensive auto-scaling setup for the Waqiti fintech platform ensuring optimal resource utilization, cost efficiency, and service reliability across all financial services.

## Architecture Components

### 1. Horizontal Pod Autoscaler (HPA)
- **CPU & Memory based scaling**: Standard resource-based scaling for all services
- **Custom metrics scaling**: Business-specific metrics like transaction volume, queue depth
- **Quality-based scaling**: Response time, error rate, and SLA-driven scaling
- **Multi-metric scaling**: Combines multiple metrics for intelligent scaling decisions

### 2. Vertical Pod Autoscaler (VPA)
- **Resource optimization**: Automatically adjusts CPU/memory requests and limits
- **Cost optimization**: Right-sizing containers based on actual usage patterns
- **Performance optimization**: Ensures adequate resources for peak performance

### 3. Cluster Autoscaler
- **Node scaling**: Automatically adds/removes nodes based on pod scheduling requirements
- **Cost optimization**: Scales down underutilized nodes
- **Multi-AZ support**: Distributes nodes across availability zones

### 4. Pod Disruption Budgets (PDB)
- **High availability**: Ensures minimum number of replicas during updates
- **Rolling updates**: Prevents service disruption during deployments
- **Maintenance windows**: Controlled scaling during maintenance

## Service-Specific Scaling Policies

### Critical Financial Services

#### Payment Service
```yaml
Min Replicas: 3
Max Replicas: 50
CPU Target: 70%
Memory Target: 80%
Custom Metrics:
  - Transactions per second: 100/pod
  - Queue depth: 1000 max
  - Response time P95: 500ms
```

#### Wallet Service  
```yaml
Min Replicas: 3
Max Replicas: 30
CPU Target: 70%
Memory Target: 75%
Custom Metrics:
  - Balance requests: 200/pod/second
  - DB connection pool: 70% max
```

#### Ledger Service
```yaml
Min Replicas: 2
Max Replicas: 20
CPU Target: 65%
Custom Metrics:
  - Audit entries per second
  - Storage utilization
```

### Support Services

#### User Service
```yaml
Min Replicas: 2
Max Replicas: 20
CPU Target: 65%
Custom Metrics:
  - Authentication requests: 150/pod/second
```

#### Notification Service
```yaml
Min Replicas: 2
Max Replicas: 25
Custom Metrics:
  - Queue depth: 500 max
  - Delivery success rate: 95% min
  - Provider response time: 2s P95
```

#### KYC Service
```yaml
Min Replicas: 2
Max Replicas: 10
Custom Metrics:
  - Document processing queue: 50 max
  - Processing time P95: 30s
```

## Advanced Scaling Features

### Quality-Based Scaling
Services automatically scale based on service quality metrics:
- **Response Time**: Scale up when P95 response time > 500ms
- **Error Rate**: Scale up when error rate > 5%
- **Availability**: Maintain 99.9% uptime SLA

### Resource-Aware Scaling
- **Database Connection Scaling**: Scale based on connection pool utilization
- **Memory Pressure Scaling**: Scale before memory exhaustion
- **Storage Scaling**: Scale based on disk utilization

### Business Logic Scaling
- **Transaction Volume**: Scale payment services during peak trading hours
- **Fraud Detection**: Scale ML services based on suspicious activity
- **Compliance Processing**: Scale AML/KYC services based on regulatory queues

## Scaling Behavior Configuration

### Scale-Up Policies
- **Aggressive scaling** for critical services (100% increase in 30s)
- **Conservative scaling** for stateful services (50% increase in 60s)
- **Immediate scaling** for queue-based services

### Scale-Down Policies
- **Stabilization windows**: 5-10 minutes to prevent thrashing
- **Gradual scale-down**: 25-50% reduction per cycle
- **Minimum replica protection**: Always maintain minimum service levels

## Priority-Based Scheduling

### Priority Classes
1. **waqiti-critical-priority** (1,000,000): Payment, Wallet, Ledger services
2. **waqiti-high-priority** (500,000): Fraud, Compliance, KYC services  
3. **waqiti-normal-priority** (100,000): User, Notification services
4. **waqiti-low-priority** (10,000): Analytics, Monitoring services
5. **waqiti-batch-priority** (1,000): Reporting, ETL jobs

### Resource Preemption
- Critical services can preempt lower priority pods
- Graceful shutdown for preempted services
- Anti-affinity rules for critical service distribution

## Custom Metrics Integration

### Prometheus Metrics Adapter
Custom business metrics exposed for HPA:
```yaml
payment_transactions_per_second
wallet_balance_requests_per_second
authentication_requests_per_second
queue_depth
notification_queue_depth
kyc_document_processing_queue
db_connection_pool_usage
http_request_duration_p95
ml_inference_duration_p95
fraud_analysis_queue_depth
```

### Grafana Dashboards
- Real-time scaling decisions visualization
- Resource utilization trending
- Cost optimization recommendations
- SLA compliance tracking

## Resource Management

### Resource Quotas
**Production Environment:**
- CPU: 100 cores request, 300 cores limit
- Memory: 200Gi request, 600Gi limit  
- Storage: 1Ti total
- Pods: 200 maximum

**Staging Environment:**
- CPU: 20 cores request, 60 cores limit
- Memory: 40Gi request, 120Gi limit
- Storage: 200Gi total
- Pods: 50 maximum

### Limit Ranges
Prevent resource abuse with container-level limits:
- **Production**: 50m-4000m CPU, 128Mi-16Gi memory
- **Staging**: 25m-2000m CPU, 64Mi-8Gi memory
- **Development**: 10m-1000m CPU, 32Mi-4Gi memory

## Deployment Instructions

### Prerequisites
```bash
# Install required components
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Install VPA (if not already installed)
git clone https://github.com/kubernetes/autoscaler.git
cd autoscaler/vertical-pod-autoscaler/
./hack/vpa-up.sh

# Install Custom Metrics API
kubectl apply -f custom-metrics-config.yaml
```

### Deploy Auto-scaling Configuration
```bash
# Apply in order
kubectl apply -f priority-classes.yaml
kubectl apply -f resource-quotas.yaml
kubectl apply -f pod-disruption-budgets.yaml
kubectl apply -f cluster-autoscaler.yaml
kubectl apply -f hpa-payment-service.yaml
kubectl apply -f vpa-config.yaml
kubectl apply -f quality-based-autoscaling.yaml
```

### Verification
```bash
# Verify HPA status
kubectl get hpa

# Verify VPA recommendations
kubectl get vpa

# Verify cluster autoscaler
kubectl logs -f deployment/cluster-autoscaler -n kube-system

# Check custom metrics
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" | jq .
```

## Monitoring and Alerting

### Key Metrics to Monitor
- **Scaling Events**: Track all scaling decisions
- **Resource Utilization**: CPU, memory, storage trends
- **SLA Compliance**: Response time, availability, error rates
- **Cost Optimization**: Resource efficiency ratios

### Alerts Configuration
```yaml
# Scale-up failures
- alert: HPAScaleUpFailed
  expr: increase(hpa_scale_up_errors_total[5m]) > 0
  
# Resource exhaustion
- alert: ResourceQuotaExceeded
  expr: resource_quota_used / resource_quota_hard > 0.9
  
# SLA violations
- alert: ResponseTimeSLAViolation
  expr: http_request_duration_p95 > 1.0
```

## Cost Optimization

### Strategies
1. **Right-sizing**: VPA automatically optimizes resource requests
2. **Spot instances**: Use cluster autoscaler with spot node groups
3. **Scheduled scaling**: Pre-scale for known traffic patterns
4. **Resource quotas**: Prevent runaway resource consumption

### Cost Monitoring
- **Hourly cost tracking** per service
- **Resource efficiency** metrics
- **Scaling cost impact** analysis
- **Optimization recommendations**

## Security Considerations

### Network Policies
- Restrict inter-service communication
- Isolate critical financial services
- Control egress to external services

### Pod Security Standards
- **Restricted** security context for all pods
- **Non-root** user execution
- **ReadOnlyRootFilesystem** where possible
- **Resource limits** enforcement

## Disaster Recovery

### Multi-Region Scaling
- **Cross-region failover**: Automatic scaling in secondary regions
- **Data replication**: Ensure data availability for scaling decisions
- **Circuit breakers**: Prevent cascade failures during scaling

### Backup Scaling Policies
- **Emergency scale-up**: Immediate resource allocation during incidents
- **Degraded mode scaling**: Maintain core services during outages
- **Recovery scaling**: Gradual restoration to normal capacity

## Troubleshooting

### Common Issues

#### HPA Not Scaling
```bash
# Check metrics availability
kubectl describe hpa payment-service-hpa

# Verify metrics server
kubectl top pods

# Check custom metrics
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/default/pods/*/payment_transactions_per_second"
```

#### Cluster Autoscaler Issues
```bash
# Check autoscaler logs
kubectl logs -f deployment/cluster-autoscaler -n kube-system

# Verify node group configuration
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names waqiti-worker-nodes-1
```

#### Resource Quota Issues
```bash
# Check quota usage
kubectl describe quota waqiti-production-quota

# Identify high resource consumers
kubectl top pods --sort-by=memory
kubectl top pods --sort-by=cpu
```

## Performance Tuning

### HPA Tuning
- **--horizontal-pod-autoscaler-downscale-stabilization**: 5m (default)
- **--horizontal-pod-autoscaler-upscale-delay**: 3m
- **--horizontal-pod-autoscaler-sync-period**: 15s

### VPA Tuning
- **--memory-savings-until-safe**: 0.95
- **--recommendation-margin-fraction**: 0.15
- **--pod-recommendation-min-memory-mb**: 250

### Cluster Autoscaler Tuning
- **--scale-down-delay-after-add**: 10m
- **--scale-down-unneeded-time**: 10m  
- **--scale-down-utilization-threshold**: 0.5

## Maintenance

### Regular Tasks
- **Weekly**: Review scaling patterns and adjust policies
- **Monthly**: Analyze cost optimization opportunities
- **Quarterly**: Update resource quotas based on growth
- **Annually**: Review and update priority classifications

### Updates
- **Monitor** Kubernetes autoscaling feature releases
- **Test** new scaling policies in staging environment
- **Gradual rollout** of policy changes to production

## Support and Contacts

- **Platform Team**: platform@example.com
- **On-call**: +1-555-WAQITI-1
- **Documentation**: https://docs.example.com/k8s/autoscaling
- **Runbook**: https://runbook.example.com/autoscaling

## License

Copyright © 2024 Waqiti. All rights reserved.

This autoscaling configuration is proprietary and confidential.