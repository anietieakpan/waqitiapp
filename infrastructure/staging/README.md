# Waqiti Platform - Staging Environment

Production-ready staging environment deployment for comprehensive testing and validation.

## Overview

This staging environment replicates production infrastructure with:
- Full microservices architecture (4 core services)
- Production-grade databases and caching
- SSL/TLS encryption and security headers
- Comprehensive monitoring and alerting
- Load balancing and reverse proxy
- Container orchestration with health checks

## Architecture

### Services Deployed
- **Payment Service** (port 8081): Payment processing and transactions
- **Wallet Service** (port 8082): Digital wallet and balance management
- **Fraud Detection Service** (port 8083): Real-time fraud analysis
- **Reconciliation Service** (port 8084): Settlement and reconciliation

### Infrastructure
- **PostgreSQL 15**: Primary database with optimizations
- **Redis 7**: Caching and session storage
- **Apache Kafka**: Event streaming and messaging
- **Nginx**: Load balancer and SSL termination
- **Prometheus**: Metrics collection
- **Grafana**: Monitoring dashboards

### Domains
- `payment-staging.example.com` - Payment Service
- `wallet-staging.example.com` - Wallet Service
- `fraud-staging.example.com` - Fraud Detection Service
- `reconciliation-staging.example.com` - Reconciliation Service
- `grafana-staging.example.com` - Monitoring Dashboard
- `prometheus-staging.example.com` - Metrics (internal)

## Deployment

### Prerequisites

```bash
# Docker and Docker Compose
docker --version  # 20.10+
docker-compose --version  # 1.29+

# Available disk space (minimum 10GB)
df -h

# Network connectivity
curl -I https://hub.docker.com
```

### Configuration

1. **Environment Setup**:
```bash
cd infrastructure/staging
cp .env.staging.template .env.staging
```

2. **Configure Environment Variables**:
```bash
# Edit .env.staging with your values
vim .env.staging

# Required variables:
POSTGRES_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_jwt_secret_64_chars
ENCRYPTION_KEY=your_encryption_key_32_chars
STRIPE_SECRET_KEY=sk_test_your_stripe_key
GRAFANA_ADMIN_PASSWORD=your_grafana_password
```

3. **SSL Certificates**:
```bash
# Generate self-signed certificates (for staging)
# Production should use Let's Encrypt or commercial certificates
```

### Deploy Staging Environment

```bash
cd infrastructure/staging/scripts
./deploy-staging.sh
```

**Deployment Process**:
1. Prerequisites validation
2. Docker image building
3. SSL certificate generation
4. Database and Redis configuration
5. Monitoring setup
6. Service deployment
7. Health check validation
8. Summary report generation

## Operations

### Service Management

```bash
cd infrastructure/staging

# View all services status
docker-compose -f docker-compose.staging.yml ps

# View logs for specific service
docker-compose -f docker-compose.staging.yml logs -f payment-service

# Restart a service
docker-compose -f docker-compose.staging.yml restart wallet-service

# Scale a service
docker-compose -f docker-compose.staging.yml up -d --scale payment-service=2

# Stop all services
docker-compose -f docker-compose.staging.yml down

# Stop and remove volumes (data loss!)
docker-compose -f docker-compose.staging.yml down -v
```

### Health Monitoring

```bash
# Service health endpoints
curl https://payment-staging.example.com/actuator/health
curl https://wallet-staging.example.com/actuator/health
curl https://fraud-staging.example.com/actuator/health
curl https://reconciliation-staging.example.com/actuator/health

# Infrastructure health
curl http://localhost:5432  # PostgreSQL
curl http://localhost:6379  # Redis
curl http://localhost:29092  # Kafka

# Monitoring
curl http://localhost:9090/api/v1/query?query=up  # Prometheus
curl https://grafana-staging.example.com/api/health  # Grafana
```

### Log Management

```bash
# Application logs
docker logs waqiti-payment-service-staging
docker logs waqiti-wallet-service-staging
docker logs waqiti-fraud-detection-service-staging

# Infrastructure logs
docker logs waqiti-postgres-staging
docker logs waqiti-redis-staging
docker logs waqiti-kafka-staging

# Nginx access logs
docker exec waqiti-nginx-staging tail -f /var/log/nginx/access.log

# Follow all logs
docker-compose -f docker-compose.staging.yml logs -f
```

## Configuration Files

### Core Configuration
- `docker-compose.staging.yml` - Container orchestration
- `.env.staging` - Environment variables
- `nginx/nginx.conf` - Load balancer configuration

### Database Configuration
- `postgres/postgresql.conf` - PostgreSQL settings
- `postgres/init-scripts/` - Database initialization
- `redis/redis.conf` - Redis configuration

### Monitoring Configuration
- `prometheus/prometheus-staging.yml` - Metrics collection
- `grafana/datasources/` - Grafana data sources
- `grafana/dashboards/` - Pre-configured dashboards

### Security Configuration
- `ssl/` - SSL certificates and keys
- `nginx/` - Security headers and rate limiting

## Performance Tuning

### Database Optimization
- Connection pooling: 20 connections per service
- Shared buffers: 256MB
- Effective cache size: 1GB
- WAL optimization for high throughput

### Redis Configuration
- Memory limit: 512MB with LRU eviction
- Persistence: RDB snapshots + AOF
- Threading: 4 I/O threads for performance

### Application Tuning
- JVM settings: -Xms1g -Xmx2g with G1GC
- Tomcat: 200 threads, 8192 connections
- Connection pools: Optimized per service load

### Nginx Optimization
- Worker processes: Auto-scaled
- Keepalive connections: 4096 per worker
- Gzip compression enabled
- SSL session caching

## Security Features

### Network Security
- Internal Docker network isolation
- Rate limiting (100 req/min API, 10 req/min auth)
- Connection limits per IP
- Fail2ban equivalent protection

### SSL/TLS Configuration
- TLS 1.2/1.3 only
- Strong cipher suites
- HSTS headers
- Perfect Forward Secrecy

### Application Security
- JWT token authentication
- CORS configuration
- Security headers (CSP, XSS protection)
- Input validation and sanitization

### Data Protection
- Database encryption at rest
- Redis AUTH password protection
- Sensitive data masking in logs
- PCI DSS compliance configurations

## Monitoring & Alerting

### Grafana Dashboards
- **Service Metrics**: Response times, throughput, error rates
- **Infrastructure**: CPU, memory, disk, network usage
- **Business Metrics**: Payment volumes, fraud detection rates
- **Security**: Failed authentication attempts, rate limiting

### Prometheus Metrics
- Application metrics (Spring Boot Actuator)
- Infrastructure metrics (node, postgres, redis exporters)
- Custom business metrics
- SLA monitoring (99.9% uptime target)

### Alerting Rules
- Service down alerts
- High error rate (>5%)
- Database connection exhaustion
- High memory usage (>80%)
- Disk space low (<10%)
- SSL certificate expiration

## Backup & Recovery

### Automated Backups
- PostgreSQL: Daily full backup + WAL shipping
- Redis: RDB snapshots every 15 minutes
- Application logs: 30-day retention
- Configuration files: Version controlled

### Disaster Recovery
- RTO: 15 minutes (Recovery Time Objective)
- RPO: 5 minutes (Recovery Point Objective)
- Hot standby database configuration
- Multi-AZ deployment ready

## Testing

### Smoke Tests
```bash
# API connectivity tests
curl -X POST https://payment-staging.example.com/api/v1/health
curl -X GET https://wallet-staging.example.com/api/v1/health
curl -X POST https://fraud-staging.example.com/api/v1/health

# Database connectivity
docker exec waqiti-postgres-staging pg_isready

# Cache connectivity
docker exec waqiti-redis-staging redis-cli ping
```

### Load Testing
- JMeter test suites available in `../../performance-testing/`
- Gatling scenarios for sustained load
- Expected throughput: 1000+ TPS per service
- Target response time: p95 < 500ms

### Security Testing
- OWASP ZAP automated scans
- SSL Labs grade A+ target
- Penetration testing scenarios
- Vulnerability scanning integration

## Troubleshooting

### Common Issues

**Services not starting**:
```bash
# Check resource availability
docker system df
docker system prune

# Check logs
docker-compose logs service-name

# Verify environment variables
docker-compose config
```

**Database connection issues**:
```bash
# Check PostgreSQL status
docker exec waqiti-postgres-staging pg_isready
docker logs waqiti-postgres-staging

# Connection test
docker exec waqiti-postgres-staging psql -U waqiti_staging_user -d waqiti_staging -c "SELECT 1"
```

**SSL certificate problems**:
```bash
# Verify certificate validity
openssl x509 -in ssl/payment-staging.example.com.crt -text -noout

# Check certificate expiration
openssl x509 -in ssl/payment-staging.example.com.crt -enddate -noout
```

**Performance issues**:
```bash
# Check resource usage
docker stats

# Monitor application metrics
curl http://localhost:9090/metrics | grep jvm_memory

# Database performance
docker exec waqiti-postgres-staging psql -U waqiti_staging_user -c "SELECT * FROM pg_stat_activity;"
```

### Log Analysis
```bash
# Parse nginx access logs
docker exec waqiti-nginx-staging tail -f /var/log/nginx/access.log | jq .

# Application error logs
docker logs waqiti-payment-service-staging 2>&1 | grep ERROR

# Database slow queries
docker exec waqiti-postgres-staging tail -f /var/lib/postgresql/data/pg_log/postgresql-*.log
```

## Development Workflow

### Continuous Deployment
1. Code commit triggers CI/CD pipeline
2. Automated testing (unit, integration, security)
3. Docker image building and tagging
4. Staging deployment with health checks
5. Automated smoke tests
6. Production deployment approval

### Environment Promotion
- **Development** → **Staging** → **Production**
- Configuration management via environment files
- Database migration scripts
- Feature flag management
- Rollback procedures

## Maintenance

### Regular Tasks
- **Weekly**: Security updates, log rotation
- **Monthly**: Performance review, capacity planning
- **Quarterly**: Disaster recovery testing, security audit

### Updates & Patches
```bash
# Update base images
docker-compose pull
docker-compose up -d --force-recreate

# Application updates
# (Build new images with updated code)
./deploy-staging.sh

# Security patches
apt update && apt upgrade  # Host system
# Container updates via image rebuilding
```

## Compliance

### PCI DSS Readiness
- Network segmentation
- Access controls and authentication
- Encryption of cardholder data
- Secure development practices
- Regular security testing
- Audit logging and monitoring

### SOC 2 Type II
- Security controls documentation
- Availability monitoring and reporting
- Processing integrity validation
- Confidentiality protection measures
- Privacy controls implementation

## Support

### Documentation
- Architecture diagrams: `docs/architecture/`
- API documentation: `docs/api/`
- Runbooks: `docs/operations/`
- Security procedures: `docs/security/`

### Contact Information
- **DevOps Team**: devops@example.com
- **Security Team**: security@example.com
- **Emergency Escalation**: +1-XXX-XXX-XXXX

### External Resources
- [Docker Documentation](https://docs.docker.com/)
- [Nginx Configuration Guide](https://nginx.org/en/docs/)
- [PostgreSQL Performance](https://www.postgresql.org/docs/current/performance-tips.html)
- [Redis Best Practices](https://redis.io/docs/manual/admin/)

---

**Last Updated**: $(date)  
**Environment Version**: Staging v1.0  
**Deployment Guide Version**: 1.0