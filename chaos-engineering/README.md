# Chaos Engineering for Waqiti P2P Payment Platform

## Overview

This directory contains the chaos engineering infrastructure for the Waqiti platform. We use [Litmus Chaos](https://litmuschaos.io/) to conduct controlled experiments that help us build confidence in the system's capability to withstand turbulent conditions in production.

## 🎯 Objectives

- **Discover weaknesses** before they manifest in production
- **Build confidence** in system resilience
- **Validate** monitoring and alerting effectiveness
- **Improve** incident response procedures
- **Ensure** SLA compliance under failure conditions

## 📁 Directory Structure

```
chaos-engineering/
├── README.md                 # This file
├── chaos-config.yaml        # Main chaos configuration
├── litmus-operator.yaml     # Litmus installation manifests
├── experiments/             # Chaos experiment definitions
│   ├── payment-service-chaos.yaml
│   ├── infrastructure-chaos.yaml
│   └── ...
├── monitoring/              # Monitoring and dashboards
│   └── chaos-dashboard.yaml
├── runbooks/               # Operational runbooks
│   └── chaos-runbook.md
├── scripts/                # Automation scripts
│   └── chaos-controller.sh
├── results/                # Experiment results (gitignored)
└── reports/                # Generated reports (gitignored)
```

## 🚀 Quick Start

### Prerequisites

- Kubernetes cluster (1.16+)
- kubectl configured
- Prometheus & Grafana installed
- Litmus Chaos installed

### Installation

1. **Install Litmus Chaos Operator:**
   ```bash
   kubectl apply -f litmus-operator.yaml
   ```

2. **Verify Installation:**
   ```bash
   kubectl get pods -n litmus
   kubectl get crds | grep litmus
   ```

3. **Deploy Chaos Experiments:**
   ```bash
   kubectl apply -f experiments/
   ```

### Running Your First Experiment

1. **Use the Chaos Controller:**
   ```bash
   ./scripts/chaos-controller.sh
   ```

2. **Or manually start an experiment:**
   ```bash
   kubectl apply -f experiments/payment-service-chaos.yaml
   ```

3. **Monitor the experiment:**
   ```bash
   kubectl get chaosengine -n waqiti -w
   ```

## 📊 Experiment Types

### Pod-Level Chaos
- **Pod Delete**: Randomly delete pods
- **Pod CPU Hog**: Stress CPU resources
- **Pod Memory Hog**: Stress memory resources
- **Pod Network Latency**: Add network delays
- **Pod Network Loss**: Simulate packet loss

### Application-Level Chaos
- **Kafka Broker Failure**: Kill Kafka brokers
- **Redis Cache Flush**: Clear cache data
- **Database Connection Drain**: Exhaust DB connections

### Infrastructure-Level Chaos
- **Node CPU Hog**: Stress node CPU
- **Node Memory Hog**: Stress node memory
- **Disk Fill**: Fill up disk space

## 📈 Monitoring

### Grafana Dashboard
Access the chaos engineering dashboard:
```
http://grafana.waqiti.local/d/chaos-overview
```

### Key Metrics to Monitor
- Payment success rate
- API latency (P50, P95, P99)
- Error rates by service
- Pod restart counts
- Circuit breaker status

### Prometheus Queries

**Payment Success Rate:**
```promql
(sum(rate(payment_service_payments_successful_total[5m])) / 
 sum(rate(payment_service_payments_total[5m]))) * 100
```

**Error Rate:**
```promql
(sum(rate(http_requests_total{status=~"5.."}[5m])) / 
 sum(rate(http_requests_total[5m]))) * 100
```

## 🛡️ Safety Mechanisms

### Automatic Rollback
Experiments automatically rollback when:
- Payment success rate drops below 90%
- Error rate exceeds 5%
- API latency P99 exceeds 2 seconds

### Manual Stop
```bash
# Stop specific experiment
kubectl delete chaosengine payment-service-pod-delete-chaos -n waqiti

# Stop all experiments
kubectl delete chaosengine --all -n waqiti
```

## 📅 Experiment Schedule

| Day | Time | Experiment | Duration | Impact |
|-----|------|-----------|----------|---------|
| Mon | 10:00 | Pod Delete | 5 min | Low |
| Tue | 14:00 | Network Latency | 10 min | Medium |
| Wed | 11:00 | CPU Stress | 3 min | Medium |
| Thu | 15:00 | Kafka Failure | 5 min | High |
| Fri | 10:00 | Game Day | 2 hours | Variable |

## 🎮 Game Days

Monthly game days simulate real-world failure scenarios:

### Black Friday Simulation
- Date: 3rd Friday of each month
- Duration: 4 hours
- Scenarios: High load + infrastructure failures

### Security Incident Simulation
- Date: Last Tuesday of each month
- Duration: 2 hours
- Scenarios: DDoS + service degradation

## 📝 Best Practices

1. **Start Small**: Begin with non-critical services
2. **Communicate**: Notify teams before experiments
3. **Monitor Actively**: Watch metrics during chaos
4. **Document Everything**: Record observations and findings
5. **Iterate**: Gradually increase chaos complexity
6. **Automate**: Use CI/CD for regular chaos testing

## 🔧 Troubleshooting

### Experiment Stuck
```bash
kubectl delete chaosengine <name> -n waqiti --force --grace-period=0
kubectl delete pods -l chaosUID -n waqiti
```

### Metrics Not Available
```bash
kubectl port-forward -n monitoring prometheus-0 9090:9090
# Check http://localhost:9090/targets
```

### Service Not Recovering
```bash
kubectl rollout restart deployment <service-name> -n waqiti
```

## 📚 Additional Resources

- [Litmus Documentation](https://docs.litmuschaos.io/)
- [Chaos Engineering Principles](https://principlesofchaos.org/)
- [Waqiti Runbooks](./runbooks/)
- [Incident Response Guide](https://wiki.example.com/incident-response)

## 🤝 Contributing

1. Add new experiments to `experiments/`
2. Update monitoring queries in `monitoring/`
3. Document findings in `reports/`
4. Share learnings with the team

## 📧 Contact

- **Chaos Engineering Team**: chaos-team@example.com
- **Slack Channel**: #chaos-engineering
- **On-Call**: chaos-oncall@example.com

---

*"The best way to avoid failure is to fail constantly." - Netflix*