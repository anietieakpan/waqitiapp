# Waqiti Staging Environment Setup Guide

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Maven 3.8+
- Git
- 16GB RAM minimum
- 50GB available disk space

### One-Command Setup
```bash
# Clone the repository
git clone https://github.com/waqiti/waqiti-app.git
cd waqiti-app

# Run the staging setup script
./infrastructure/setup-staging.sh
```

This will automatically:
1. Check prerequisites
2. Create environment configuration
3. Set up all databases (PostgreSQL, MongoDB, Redis)
4. Deploy Kafka message broker
5. Build and deploy all microservices
6. Configure monitoring (Prometheus/Grafana)
7. Set up API gateway (Nginx)
8. Initialize all necessary data

## 📋 Manual Setup Steps

### 1. Environment Configuration
```bash
# Copy example environment file
cp .env.staging.example .env.staging

# Edit with your configuration
nano .env.staging
```

### 2. Build Microservices
```bash
# Build all services
mvn clean package -DskipTests

# Or build individual services
cd services/payment-service
mvn clean package
```

### 3. Start Infrastructure
```bash
# Start all services with Docker Compose
docker-compose -f docker-compose.staging.yml up -d

# Check service status
docker-compose -f docker-compose.staging.yml ps
```

### 4. Verify Services
```bash
# Check health endpoints
curl http://localhost:8080/actuator/health  # Payment Service
curl http://localhost:8081/actuator/health  # Wallet Service
curl http://localhost:8082/actuator/health  # Ledger Service
curl http://localhost:8083/actuator/health  # Compliance Service
```

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        NGINX (Port 80/443)                  │
│                         API Gateway                         │
└─────────────┬───────────────────────────────────────────────┘
              │
┌─────────────┴───────────────────────────────────────────────┐
│                      MICROSERVICES                          │
├──────────────┬──────────────┬──────────────┬───────────────┤
│   Payment    │   Wallet     │   Ledger     │  Compliance   │
│   Service    │   Service    │   Service    │   Service     │
│   (8080)     │   (8081)     │   (8082)     │   (8083)      │
├──────────────┼──────────────┼──────────────┼───────────────┤
│    Fraud     │      ML      │              │               │
│   Service    │   Service    │              │               │
│   (8084)     │   (8085)     │              │               │
└──────────────┴──────────────┴──────────────┴───────────────┘
              │
┌─────────────┴───────────────────────────────────────────────┐
│                     INFRASTRUCTURE                          │
├──────────────┬──────────────┬──────────────┬───────────────┤
│  PostgreSQL  │   MongoDB    │    Redis     │    Kafka      │
│    (5432)    │   (27017)    │   (6379)     │   (9092)      │
├──────────────┼──────────────┼──────────────┼───────────────┤
│Elasticsearch │  Prometheus  │   Grafana    │   Consul      │
│    (9200)    │   (9090)     │   (3000)     │   (8500)      │
└──────────────┴──────────────┴──────────────┴───────────────┘
```

## 🌐 Service Endpoints

### API Endpoints
- **API Gateway**: http://localhost
- **Payment API**: http://localhost/api/v1/payments
- **Wallet API**: http://localhost/api/v1/wallets
- **Ledger API**: http://localhost/api/v1/ledger
- **Compliance API**: http://localhost/api/v1/compliance
- **Fraud API**: http://localhost/api/v1/fraud
- **ML API**: http://localhost/api/v1/ml

### Direct Service Endpoints (Development)
- **Payment Service**: http://localhost:8080
- **Wallet Service**: http://localhost:8081
- **Ledger Service**: http://localhost:8082
- **Compliance Service**: http://localhost:8083
- **Fraud Service**: http://localhost:8084
- **ML Service**: http://localhost:8085

### Monitoring & Tools
- **Grafana**: http://localhost:3000 (admin/admin_staging_2024)
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8088
- **Consul**: http://localhost:8500
- **Elasticsearch**: http://localhost:9200

## 🔧 Common Operations

### View Logs
```bash
# All services
docker-compose -f docker-compose.staging.yml logs -f

# Specific service
docker-compose -f docker-compose.staging.yml logs -f payment-service

# Last 100 lines
docker-compose -f docker-compose.staging.yml logs --tail=100 wallet-service
```

### Restart Services
```bash
# Restart all services
docker-compose -f docker-compose.staging.yml restart

# Restart specific service
docker-compose -f docker-compose.staging.yml restart payment-service
```

### Scale Services
```bash
# Scale payment service to 3 instances
docker-compose -f docker-compose.staging.yml up -d --scale payment-service=3
```

### Database Access
```bash
# PostgreSQL
docker exec -it waqiti-postgres psql -U waqiti_user -d waqiti_db

# MongoDB
docker exec -it waqiti-mongodb mongosh -u admin -p mongo_staging_2024

# Redis
docker exec -it waqiti-redis redis-cli -a redis_staging_2024
```

### Kafka Operations
```bash
# List topics
docker exec -it waqiti-kafka kafka-topics --list --bootstrap-server localhost:9092

# Create topic
docker exec -it waqiti-kafka kafka-topics --create \
  --topic new-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# View messages
docker exec -it waqiti-kafka kafka-console-consumer \
  --topic payment-events \
  --from-beginning \
  --bootstrap-server localhost:9092
```

## 🔒 Security Testing

After setting up the staging environment, run security tests:

```bash
# Run complete security validation
./security-testing/security-test-runner.sh staging

# Run specific tests
python3 security-testing/penetration-testing/owasp-security-scan.py
python3 security-testing/vulnerability-assessment/security-audit-framework.py
python3 security-testing/compliance-validation/pci-dss-compliance-tester.py
```

## 📊 Load Testing

```bash
# Run k6 load tests
k6 run load-testing/k6-load-tests/payment-load-test.js

# Run with custom VUs and duration
k6 run --vus 100 --duration 5m load-testing/k6-load-tests/payment-load-test.js
```

## 🐛 Troubleshooting

### Services Not Starting
```bash
# Check Docker resources
docker system df
docker system prune -a  # Clean up unused resources

# Check service logs
docker-compose -f docker-compose.staging.yml logs [service-name]

# Check container status
docker ps -a
```

### Database Connection Issues
```bash
# Test PostgreSQL connection
pg_isready -h localhost -p 5432

# Test MongoDB connection
mongosh --host localhost:27017 -u admin -p mongo_staging_2024

# Test Redis connection
redis-cli -h localhost -p 6379 -a redis_staging_2024 ping
```

### Kafka Issues
```bash
# Check Kafka broker status
docker exec -it waqiti-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check Zookeeper status
docker exec -it waqiti-zookeeper zkServer.sh status
```

### Memory Issues
```bash
# Increase Docker memory allocation
# Docker Desktop: Preferences → Resources → Memory: 8GB minimum

# Check container memory usage
docker stats
```

## 📝 Configuration Files

### Key Configuration Files
- `docker-compose.staging.yml` - Docker Compose orchestration
- `.env.staging` - Environment variables
- `infrastructure/postgres/init.sql` - Database initialization
- `infrastructure/nginx/nginx.conf` - API Gateway configuration
- `infrastructure/prometheus/prometheus.yml` - Metrics collection

### Service Configuration
Each microservice can be configured via environment variables in `docker-compose.staging.yml` or through their respective `application-staging.yml` files.

## 🔄 CI/CD Integration

### GitHub Actions
```yaml
name: Deploy to Staging
on:
  push:
    branches: [develop]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup staging environment
        run: ./infrastructure/setup-staging.sh
```

### Jenkins Pipeline
```groovy
pipeline {
    agent any
    stages {
        stage('Setup Staging') {
            steps {
                sh './infrastructure/setup-staging.sh'
            }
        }
        stage('Run Tests') {
            steps {
                sh './security-testing/security-test-runner.sh staging'
            }
        }
    }
}
```

## 📚 Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [MongoDB Documentation](https://docs.mongodb.com/)
- [Grafana Documentation](https://grafana.com/docs/)

## 🆘 Support

For issues or questions:
- Check logs: `docker-compose -f docker-compose.staging.yml logs`
- Review configuration: `.env.staging`
- Contact: devops@example.com

---

## ⚡ Quick Commands Reference

```bash
# Start everything
./infrastructure/setup-staging.sh

# Stop everything
docker-compose -f docker-compose.staging.yml down

# View all logs
docker-compose -f docker-compose.staging.yml logs -f

# Run security tests
./security-testing/security-test-runner.sh staging

# Access databases
docker exec -it waqiti-postgres psql -U waqiti_user -d waqiti_db
docker exec -it waqiti-mongodb mongosh -u admin -p mongo_staging_2024
docker exec -it waqiti-redis redis-cli -a redis_staging_2024

# Monitor services
open http://localhost:3000  # Grafana
open http://localhost:8088  # Kafka UI
open http://localhost:8500  # Consul
```