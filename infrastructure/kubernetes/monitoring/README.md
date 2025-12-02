# Waqiti P2P Platform - Comprehensive Monitoring & Observability Stack

## Overview

This directory contains the complete monitoring and observability infrastructure for the Waqiti P2P payment platform. The stack provides comprehensive visibility into business metrics, technical performance, security events, and operational health.

## Architecture

### Core Components

1. **Prometheus** - Metrics collection and storage
2. **Grafana** - Visualization and dashboards
3. **AlertManager** - Alert routing and notification management
4. **OpenTelemetry Collector** - Unified observability data collection
5. **Business Metrics Service** - Custom business metrics collection

### Observability Stack

1. **ELK Stack** (Elasticsearch, Logstash, Kibana) - Log aggregation and analysis
2. **Jaeger** - Distributed tracing
3. **OpenTelemetry** - Unified telemetry (metrics, logs, traces)

### Security Integration

- **HashiCorp Vault** integration via External Secrets Operator
- No hardcoded secrets or credentials
- Secure secret management across all monitoring components

## Features

### Business Monitoring
- Payment transaction metrics and success rates
- BNPL application and approval tracking
- Cryptocurrency trading volume and performance
- Investment portfolio tracking
- Physical card transaction monitoring
- Rewards system analytics
- User engagement metrics

### Technical Monitoring
- API response times and error rates
- Database connection pool monitoring
- JVM memory and garbage collection metrics
- Kafka consumer lag tracking
- Redis performance metrics
- Kubernetes resource utilization
- Network and disk I/O monitoring

### Security Monitoring
- Fraud detection events and scores
- Authentication failure analysis
- KYC verification status
- AML compliance monitoring
- Suspicious device activity
- Geographic risk analysis
- Data breach indicators

### Real-time Analytics
- Event streaming monitoring
- Analytics processing lag tracking
- Machine learning model performance
- Real-time threat level assessment

## Deployment

### Quick Start

```bash
# Deploy the complete monitoring stack
./deploy-monitoring.sh
```

### Custom Deployment

```bash
# Deploy only core monitoring (Prometheus, Grafana, AlertManager)
DEPLOY_ELK=false DEPLOY_JAEGER=false ./deploy-monitoring.sh

# Deploy with ingress for external access
DEPLOY_INGRESS=true ./deploy-monitoring.sh

# Deploy without business metrics service
DEPLOY_BUSINESS_METRICS=false ./deploy-monitoring.sh
```

### Prerequisites

1. **Kubernetes Cluster** - Version 1.20+
2. **Vault Integration** - External Secrets Operator configured
3. **Storage Classes** - For persistent volumes (Prometheus, Elasticsearch)
4. **Ingress Controller** - For external access (optional)

### Vault Secret Configuration

The monitoring stack requires the following secrets in Vault:

```bash
# Grafana secrets
vault kv put secret/monitoring/grafana \
  admin-password="your-secure-password" \
  secret-key="your-session-secret" \
  smtp-password="your-smtp-password"

# Grafana database credentials
vault kv put secret/monitoring/grafana-db \
  username="grafana_user" \
  password="secure-db-password"

# AlertManager notification credentials
vault kv put secret/monitoring/alertmanager \
  sendgrid-api-key="your-sendgrid-key" \
  slack-webhook-url="your-slack-webhook"

# Business metrics database
vault kv put secret/monitoring/metrics-db \
  username="metrics_user" \
  password="secure-metrics-password"
```

## Access & Usage

### Grafana Dashboards

1. **Business Metrics Dashboard**
   - Transaction volume and success rates
   - Active user analytics
   - Payment method distribution
   - Revenue tracking

2. **Technical Metrics Dashboard**
   - API performance and error rates
   - Infrastructure resource utilization
   - Database and cache performance
   - Service health overview

3. **Security Dashboard**
   - Fraud detection events
   - Authentication analytics
   - Risk score distributions
   - Compliance monitoring

### Prometheus Metrics

Access Prometheus at `http://localhost:9090` for:
- Raw metrics exploration
- Query testing and development
- Alert rule validation
- Target health monitoring

### AlertManager

Configure alert routing at `http://localhost:9093`:
- Review active alerts
- Manage alert routing rules
- Test notification channels
- Silence alerts during maintenance

## Alert Configuration

### Critical Alerts
- Service downtime
- Payment processing failures
- Security violations
- Infrastructure failures

### Warning Alerts
- High error rates
- Performance degradation
- Resource utilization
- Business metric anomalies

### Team-Specific Routing
- **Payment Team** - Payment processing issues
- **Security Team** - Fraud and security events
- **Infrastructure Team** - System and platform issues
- **DevOps Team** - General monitoring alerts

## Customization

### Adding Custom Metrics

1. **Application Metrics** - Add Prometheus client to your services
2. **Business Metrics** - Use the Business Metrics Service API
3. **Custom Dashboards** - Import or create in Grafana
4. **Alert Rules** - Add to Prometheus configuration

### Example Application Integration

```java
@RestController
public class MetricsController {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @PostMapping("/payment")
    public ResponseEntity<?> processPayment(@RequestBody PaymentRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Process payment
            PaymentResult result = paymentService.process(request);
            
            // Record business metrics
            meterRegistry.counter("waqiti.payment.total",
                "method", request.getPaymentMethod(),
                "status", result.getStatus()).increment();
                
            meterRegistry.counter("waqiti.payment.amount.total").increment(result.getAmount());
            
            return ResponseEntity.ok(result);
        } finally {
            sample.stop(Timer.builder("waqiti.payment.duration")
                .register(meterRegistry));
        }
    }
}
```

## Troubleshooting

### Common Issues

1. **Pod Not Starting**
   ```bash
   kubectl describe pod <pod-name> -n waqiti-monitoring
   kubectl logs <pod-name> -n waqiti-monitoring
   ```

2. **Vault Secrets Not Available**
   ```bash
   kubectl get externalsecrets -n waqiti-monitoring
   kubectl describe externalsecret <secret-name> -n waqiti-monitoring
   ```

3. **Prometheus Targets Down**
   - Check service annotations for `prometheus.io/scrape: "true"`
   - Verify network policies allow Prometheus access
   - Check service discovery configuration

4. **Grafana Dashboard Not Loading**
   - Verify Prometheus data source configuration
   - Check metric names and labels
   - Review dashboard JSON for syntax errors

### Health Checks

```bash
# Check all monitoring components
kubectl get pods -n waqiti-monitoring

# Check Prometheus targets
kubectl port-forward -n waqiti-monitoring svc/prometheus 9090:9090
# Visit http://localhost:9090/targets

# Check Grafana datasources
kubectl port-forward -n waqiti-monitoring svc/grafana 3000:3000
# Visit http://localhost:3000/datasources

# Check AlertManager status
kubectl port-forward -n waqiti-monitoring svc/alertmanager 9093:9093
# Visit http://localhost:9093
```

## Maintenance

### Regular Tasks

1. **Update Dashboards** - Keep business metrics current
2. **Review Alerts** - Tune thresholds and reduce noise
3. **Clean Up Metrics** - Archive old data
4. **Update Components** - Security patches and features

### Backup Strategy

1. **Grafana Dashboards** - Export and version control
2. **Prometheus Data** - Persistent volume snapshots
3. **Configuration** - Git repository backup
4. **Secrets** - Vault backup procedures

## Security Considerations

1. **Network Policies** - Restrict inter-service communication
2. **RBAC** - Limit monitoring service permissions
3. **TLS** - Enable encryption for external endpoints
4. **Secret Rotation** - Regular credential updates

## Performance Tuning

### Prometheus
- Adjust retention settings based on storage capacity
- Tune scrape intervals for high-cardinality metrics
- Configure federation for large deployments

### Grafana
- Optimize dashboard queries for performance
- Use template variables to reduce query load
- Configure caching for frequently accessed dashboards

### OpenTelemetry
- Adjust batch sizes for optimal throughput
- Configure sampling for high-volume traces
- Monitor collector resource usage

## Contributing

1. **New Dashboards** - Follow naming conventions
2. **Alert Rules** - Include comprehensive testing
3. **Documentation** - Update README for changes
4. **Testing** - Validate in development environment

## Support

For issues with the monitoring stack:

1. Check this documentation
2. Review logs and health checks
3. Create issue with detailed information
4. Contact the DevOps team

---

**Waqiti P2P Platform Monitoring Stack v1.0**
*Built for production-ready observability and security monitoring*