# Waqiti Network Policies

Comprehensive network security policies for the Waqiti fintech platform, implementing defense-in-depth with default-deny baseline and service-specific allow rules.

## 🔒 Security Overview

**Security Posture:** Zero-trust network architecture
**Compliance:** PCI-DSS 1.2.1, 1.3.x | GDPR | SOX | AML/CTF
**Coverage:** 100% of services (60+ microservices)
**Default Policy:** DENY ALL

## 📁 Policy Files

### Core Policies
- **00-default-deny-all.yaml** - Default-deny baseline for all namespaces
- **01-api-gateway-network-policy.yaml** - API Gateway ingress/egress rules
- **02-payment-service-network-policy.yaml** - Payment, Wallet, Transaction services (PCI-DSS scope)
- **03-security-services-network-policy.yaml** - Fraud Detection, KYC, Compliance, User services
- **04-infrastructure-network-policy.yaml** - PostgreSQL, Redis, Kafka, Zookeeper, ClickHouse, Neo4j
- **05-monitoring-network-policy.yaml** - Prometheus, Grafana, Zipkin, Jaeger, ELK stack
- **06-support-services-network-policy.yaml** - Notification, Integration, Discovery, Config, Audit

### Deployment Tools
- **deploy-network-policies.sh** - Automated deployment script with validation and rollback
- **README.md** - This file

## 🚀 Quick Start

### Prerequisites
- kubectl installed and configured
- Access to Kubernetes cluster
- Appropriate RBAC permissions

### Deployment

#### Production Deployment
```bash
# Dry-run first (recommended)
./deploy-network-policies.sh production --dry-run

# Deploy to production
./deploy-network-policies.sh production
```

#### Staging Deployment
```bash
./deploy-network-policies.sh staging
```

#### Rollback
```bash
./deploy-network-policies.sh production --rollback
```

## 🏗️ Architecture

### Default-Deny Baseline
All pods in all namespaces are denied ALL ingress and egress traffic by default. Explicit allow rules must be defined for any communication.

### Service Tiers

#### **Edge Tier**
- API Gateway
- Load Balancers
- Receives external traffic

#### **Critical Tier** (PCI-DSS Scope)
- Payment Service
- Wallet Service
- Transaction Service
- Fraud Detection Service
- KYC Service
- Compliance Service
- User Service
- Audit Service

#### **Application Tier**
- Notification Service
- Integration Service
- Analytics Service
- Reporting Service

#### **Infrastructure Tier**
- PostgreSQL
- Redis
- Kafka
- Zookeeper
- ClickHouse
- Neo4j

#### **Monitoring Tier**
- Prometheus
- Grafana
- Zipkin/Jaeger
- Elasticsearch
- Kibana
- Logstash
- AlertManager

## 📊 Policy Matrix

### Payment Service Example

| Direction | Source/Destination | Port | Protocol | Purpose |
|-----------|-------------------|------|----------|---------|
| Ingress | API Gateway | 8083 | TCP | HTTP API |
| Ingress | Transaction Service | 8083 | TCP | Payment initiation |
| Ingress | Prometheus | 9090 | TCP | Metrics scraping |
| Egress | PostgreSQL | 5432 | TCP | Database access |
| Egress | Kafka | 9092 | TCP | Event publishing |
| Egress | Redis | 6379 | TCP | Caching/idempotency |
| Egress | Fraud Detection | 8088 | TCP | Fraud scoring |
| Egress | External APIs | 443 | TCP | Payment gateways |

## 🔍 Validation

### Verify Deployment
```bash
# Check deployed policies
kubectl get networkpolicies -n waqiti-production

# Verify default-deny is active
kubectl get networkpolicy default-deny-all-ingress -n waqiti-production

# View policy details
kubectl describe networkpolicy payment-service-policy -n waqiti-production
```

### Test Connectivity

#### Test 1: Verify API Gateway can reach Payment Service
```bash
kubectl run test-pod --rm -i --tty --image=busybox -n waqiti-production -- \
  wget -O- http://payment-service:8083/actuator/health
```

#### Test 2: Verify unauthorized access is blocked
```bash
# This should timeout/fail
kubectl run test-pod --rm -i --tty --image=busybox -n waqiti-production -- \
  wget -O- http://postgres:5432
```

#### Test 3: Verify DNS resolution works
```bash
kubectl run test-pod --rm -i --tty --image=busybox -n waqiti-production -- \
  nslookup payment-service
```

## 🛡️ Security Best Practices

### 1. Principle of Least Privilege
- Each service has minimal required access
- No wildcards in production policies
- Explicit port numbers specified

### 2. Defense in Depth
- Network policies (this layer)
- Pod Security Policies
- Service Mesh (Istio) mTLS
- Application-level authentication

### 3. Audit and Monitoring
```bash
# Monitor network policy violations
kubectl logs -n kube-system -l component=kube-network-policy --tail=100

# Prometheus alerts for denied connections
sum(rate(network_policy_denied_connections_total[5m])) by (pod, destination)
```

### 4. Change Management
- All policy changes require code review
- Dry-run before production deployment
- Maintain backups of previous policies
- Document policy exceptions

## 📝 Policy Customization

### Adding a New Service

1. **Create policy file**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: new-service-policy
  namespace: waqiti-production
spec:
  podSelector:
    matchLabels:
      app: new-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - protocol: TCP
          port: 8091
  egress:
    # Add required egress rules
```

2. **Add to deployment script**
```bash
# Edit deploy-network-policies.sh
POLICY_FILES=(
    ...
    "07-new-service-network-policy.yaml"
)
```

3. **Deploy and validate**
```bash
./deploy-network-policies.sh production --dry-run
./deploy-network-policies.sh production
```

### Modifying Existing Policies

1. Edit the relevant policy file
2. Test with dry-run: `./deploy-network-policies.sh production --dry-run`
3. Deploy: `./deploy-network-policies.sh production`
4. Validate connectivity
5. Rollback if issues: `./deploy-network-policies.sh production --rollback`

## 🔧 Troubleshooting

### Issue: Service cannot connect to database

**Symptoms:** Application logs show connection timeouts to PostgreSQL

**Diagnosis:**
```bash
# Check if network policy exists
kubectl get networkpolicy postgres-policy -n waqiti-production

# Check policy allows your service
kubectl describe networkpolicy postgres-policy -n waqiti-production
```

**Solution:** Add your service to the PostgreSQL policy ingress rules

### Issue: DNS resolution failing

**Symptoms:** Pods cannot resolve service names

**Diagnosis:**
```bash
# Verify DNS policy exists
kubectl get networkpolicy allow-dns-access -n waqiti-production

# Test DNS from pod
kubectl exec -it <pod-name> -n waqiti-production -- nslookup google.com
```

**Solution:** Ensure `allow-dns-access` policy is deployed

### Issue: External API calls failing

**Symptoms:** Services cannot reach external HTTPS endpoints

**Diagnosis:**
```bash
# Check egress rules for HTTPS
kubectl describe networkpolicy <service>-policy -n waqiti-production | grep "port: 443"
```

**Solution:** Add egress rule for port 443 to the service policy

## 📈 Monitoring and Alerts

### Key Metrics to Monitor

```promql
# Denied connections by service
sum(rate(network_policy_denied_connections[5m])) by (source_service, destination_service)

# Policy evaluation latency
histogram_quantile(0.99, network_policy_evaluation_duration_seconds_bucket)

# Services without network policies
count(kube_pod_info) by (pod, namespace)
  unless on(pod, namespace) count(kube_networkpolicy_spec_pod_selector) by (pod, namespace)
```

### Recommended Alerts

```yaml
# AlertManager configuration
groups:
  - name: network_policies
    rules:
      - alert: HighDeniedConnectionRate
        expr: rate(network_policy_denied_connections[5m]) > 10
        for: 5m
        annotations:
          summary: "High rate of denied network connections"

      - alert: ServiceWithoutNetworkPolicy
        expr: |
          count(kube_pod_info{namespace="waqiti-production"}) by (pod)
            unless on(pod) count(kube_networkpolicy_spec_pod_selector) by (pod)
        annotations:
          summary: "Service deployed without network policy"
```

## 🔄 Maintenance

### Regular Tasks

#### Weekly
- Review denied connection logs
- Validate all services have policies
- Check for policy violations

#### Monthly
- Audit policy compliance
- Review and update documentation
- Test rollback procedures

#### Quarterly
- Full security audit
- Penetration testing
- Policy optimization review

## 📚 References

- [Kubernetes Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/)
- [PCI-DSS Network Security Requirements](https://www.pcisecuritystandards.org/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [Zero Trust Architecture](https://www.nist.gov/publications/zero-trust-architecture)

## 🆘 Support

For issues or questions:
- **Security Team:** security@example.com
- **DevOps Team:** devops@example.com
- **Slack Channel:** #network-security
- **On-Call:** security-oncall@waqiti.pagerduty.com

## 📜 License

Copyright © 2025 Waqiti. All rights reserved.

## 🔖 Version History

- **v1.0.0** (2025-10-25) - Initial comprehensive network policy deployment
  - Default-deny baseline implemented
  - 60+ services covered
  - PCI-DSS compliant isolation
  - Automated deployment tooling
