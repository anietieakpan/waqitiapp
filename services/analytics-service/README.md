# Analytics Service

**Advanced Analytics, Machine Learning, and Business Intelligence Service for Waqiti P2P Platform**

[![Production Ready](https://img.shields.io/badge/Production%20Ready-75%25-yellow)]()
[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green)]()
[![Test Coverage](https://img.shields.io/badge/Coverage-10%25%20(Target%2080%25)-red)]()

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Production Readiness](#production-readiness)

---

## 🎯 Overview

The Analytics Service is a critical component of the Waqiti platform, providing:
- **Real-time analytics** processing for transactions, user behavior, and fraud detection
- **Machine learning** models for predictive insights and anomaly detection
- **Business intelligence** dashboards and reporting
- **Big data processing** with Apache Spark and Elasticsearch
- **Dead Letter Queue** management with automatic recovery

**Service Type:** Tier 3 (Analytics & Reporting)
**Port:** 8087
**Context Path:** `/api/analytics`
**Database:** PostgreSQL (waqiti_analytics)

---

## ✨ Features

### Core Analytics
- Transaction analytics (volume, value, success rates, trends)
- User behavior analytics (session tracking, engagement metrics)
- Merchant analytics (performance, revenue, fraud patterns)
- Real-time metrics collection and aggregation

### Machine Learning
- Fraud detection with TensorFlow/PyTorch models
- Anomaly detection for unusual transaction patterns
- Predictive analytics for cash flow forecasting
- Recommendation engine for personalized insights

### Data Processing
- Batch processing with Apache Spark
- Stream processing with Kafka
- Time-series analytics with InfluxDB
- Search and aggregation with Elasticsearch

### Operational Features
- **DLQ (Dead Letter Queue)** management with automatic retry
- Multi-channel notifications (Email, SMS, Slack, PagerDuty)
- Comprehensive audit trails
- GDPR-compliant data retention

---

## 🏗 Architecture

### Microservices Pattern
```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway                              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                Analytics Service (Port 8087)                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Controllers  │  │   Services    │  │ Repositories │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│          │                 │                  │              │
│          ▼                 ▼                  ▼              │
│  ┌──────────────────────────────────────────────────┐      │
│  │           Spring Boot Application                 │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
         │            │             │              │
         ▼            ▼             ▼              ▼
    PostgreSQL    Elasticsearch  InfluxDB      Redis
    (Analytics)    (Search)    (TimeSeries)  (Cache)
```

### Event-Driven Architecture
```
Kafka Topics → Kafka Consumers → Business Logic → Database
                      │                              │
                      └──── DLQ Handler ─────────────┘
                              │
                              ├─ Automatic Retry
                              ├─ Manual Review Queue
                              └─ Operations Alerting
```

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Spring Cloud | Spring Cloud | 2023.0.4 |
| Database | PostgreSQL | 15 |
| Cache | Redis | 7 |
| Message Queue | Apache Kafka | 3.9.0 |
| Search | Elasticsearch | 8.11.0 |
| Big Data | Apache Spark | 3.5.0 |
| ML Framework | TensorFlow, PyTorch | Latest |
| Time Series | InfluxDB | Latest |

---

## 📦 Prerequisites

### Required Software
- **Java 21** (OpenJDK or Oracle JDK)
- **Maven 3.9+**
- **Docker 24+** and Docker Compose
- **PostgreSQL 15+**
- **Redis 7+**
- **Kafka 3.9+**
- **Elasticsearch 8.11+** (optional, for search features)

### Optional (for ML features)
- **Python 3.10+** (for JEP integration)
- **CUDA** (for GPU-accelerated ML)

### Environment Variables
```bash
# Database
export DB_USERNAME=waqiti_user
export ANALYTICS_DB_PASSWORD=your_secure_password

# Kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis
export REDIS_PASSWORD=your_redis_password

# Keycloak Authentication
export KEYCLOAK_URL=https://keycloak:8180
export KEYCLOAK_REALM=waqiti-fintech
export KEYCLOAK_CLIENT_SECRET=your_client_secret

# External Services (Optional)
export PAGERDUTY_ROUTING_KEY=your_pagerduty_key
export PAGERDUTY_ENABLED=false
export SLACK_BOT_TOKEN=xoxb-your-slack-token
export SLACK_ENABLED=false

# Vault (for secrets management)
export VAULT_ADDR=https://vault:8200
export VAULT_TOKEN=your_vault_token
```

---

## 🚀 Quick Start

### 1. Clone and Build

```bash
# Navigate to service directory
cd services/analytics-service

# Build the service
mvn clean package -DskipTests

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=development
```

### 2. Using Docker

```bash
# Build Docker image
docker build -t waqiti/analytics-service:latest .

# Run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f analytics-service

# Stop services
docker-compose down
```

### 3. Database Setup

```bash
# Run Flyway migrations
mvn flyway:migrate

# Validate migrations
mvn flyway:validate

# Check migration status
mvn flyway:info
```

### 4. Health Check

```bash
# Check service health
curl http://localhost:8087/actuator/health

# Check detailed health
curl http://localhost:8087/actuator/health/readiness

# View metrics
curl http://localhost:8087/actuator/prometheus
```

---

## ⚙️ Configuration

### Application Profiles

- **development** - Local development with debug logging
- **staging** - Pre-production environment
- **production** - Production with optimized settings

### Key Configuration Files

```
src/main/resources/
├── application.yml                    # Main configuration
├── application-development.yml        # Dev overrides
├── application-production.yml         # Prod overrides
└── application-keycloak.yml          # Keycloak settings
```

### Resilience4j Configuration

Circuit breakers, retries, and rate limiting are configured for all external dependencies:

- **notification-service**: 3 retries, 60s circuit breaker wait
- **pagerduty**: 2 retries, 30s circuit breaker wait
- **slack**: 2 retries, 30s circuit breaker wait, 50 calls/sec rate limit

See `application.yml` for complete configuration.

### Feature Flags

```yaml
pagerduty:
  enabled: ${PAGERDUTY_ENABLED:false}

slack:
  enabled: ${SLACK_ENABLED:false}

analytics:
  ml:
    enabled: true
    fraud-detection:
      threshold: 0.75
```

---

## 📚 API Documentation

### Swagger UI
```
http://localhost:8087/swagger-ui.html
```

### OpenAPI Specification
```
http://localhost:8087/api-docs
```

### Key Endpoints

#### Transaction Analytics
```http
GET /api/v1/analytics/transactions/metrics
GET /api/v1/analytics/transactions/trends
GET /api/v1/analytics/transactions/patterns
```

#### User Analytics
```http
GET /api/v1/analytics/users/{userId}/summary
GET /api/v1/analytics/users/{userId}/behavior
```

#### Real-time Analytics
```http
GET /api/v1/analytics/realtime/stats
GET /api/v1/analytics/realtime/alerts
```

#### Dashboard
```http
GET /api/v1/analytics/dashboard/kpis
POST /api/v1/analytics/dashboard/refresh
```

### Authentication

All endpoints require JWT authentication via Keycloak:

```http
Authorization: Bearer <jwt_token>
```

Required roles: `ADMIN`, `ANALYST`

---

## 🛠 Development

### Project Structure

```
src/
├── main/
│   ├── java/com/waqiti/analytics/
│   │   ├── api/                    # REST controllers
│   │   ├── client/                 # Feign clients
│   │   ├── config/                 # Configuration classes
│   │   ├── dto/                    # Data Transfer Objects
│   │   ├── entity/                 # JPA entities
│   │   ├── kafka/                  # Kafka consumers & DLQ handlers
│   │   ├── ml/                     # Machine learning models
│   │   ├── repository/             # Data repositories
│   │   ├── service/                # Business logic
│   │   └── AnalyticsServiceApplication.java
│   └── resources/
│       ├── application.yml
│       └── db/migration/           # Flyway migrations
└── test/
    ├── java/com/waqiti/analytics/
    │   ├── entity/                 # Entity tests
    │   ├── repository/             # Repository integration tests
    │   └── service/                # Service unit tests
    └── resources/
        └── application-test.yml
```

### Code Style

- **Java:** Follow Google Java Style Guide
- **Line Length:** 120 characters
- **Indentation:** 4 spaces
- **JavaDoc:** Required for all public classes and methods

### Git Workflow

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes and commit
git add .
git commit -m "feat: add your feature description"

# Push and create PR
git push origin feature/your-feature-name
```

---

## 🧪 Testing

### Test Categories

1. **Unit Tests** - Service and entity logic
2. **Integration Tests** - Database, Kafka, external APIs (TestContainers)
3. **Contract Tests** - API contracts (Pact)

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=DlqMessageTest

# Integration tests only
mvn verify -P integration-tests

# With coverage report
mvn clean test jacoco:report
```

### Test Coverage

**Current:** ~10%
**Target:** 80%+

View coverage report:
```bash
open target/site/jacoco/index.html
```

### TestContainers

Integration tests use TestContainers for:
- PostgreSQL database
- Kafka broker
- Redis cache
- Elasticsearch cluster

Example:
```java
@Testcontainers
@DataJpaTest
class DlqMessageRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine");
}
```

---

## 🚀 Deployment

### Docker Deployment

```bash
# Build image
docker build -t waqiti/analytics-service:1.0.0 .

# Tag for registry
docker tag waqiti/analytics-service:1.0.0 registry.example.com/analytics-service:1.0.0

# Push to registry
docker push registry.example.com/analytics-service:1.0.0
```

### Kubernetes Deployment

```bash
# Apply configurations
kubectl apply -f k8s/production/analytics-service-deployment.yaml
kubectl apply -f k8s/production/analytics-service-service.yaml
kubectl apply -f k8s/configmaps/analytics-service-config.yaml

# Check deployment status
kubectl rollout status deployment/analytics-service

# View logs
kubectl logs -f deployment/analytics-service

# Scale replicas
kubectl scale deployment/analytics-service --replicas=3
```

### Environment-Specific Deployments

```bash
# Development
mvn spring-boot:run -Dspring-boot.run.profiles=development

# Staging
kubectl apply -f k8s/staging/

# Production
kubectl apply -f k8s/production/
```

---

## 📊 Monitoring

### Health Checks

```bash
# Liveness probe
curl http://localhost:8087/actuator/health/liveness

# Readiness probe
curl http://localhost:8087/actuator/health/readiness
```

### Metrics

**Prometheus metrics endpoint:**
```
http://localhost:8087/actuator/prometheus
```

**Key metrics:**
- `analytics_dlq_messages_total` - DLQ message count by status
- `analytics_processing_duration_seconds` - Processing time
- `analytics_kafka_consumer_lag` - Consumer lag
- `analytics_circuit_breaker_state` - Circuit breaker states

### Grafana Dashboards

Import dashboards from:
```
k8s/monitoring/grafana-dashboards/analytics-service.json
```

### Distributed Tracing

**Jaeger UI:**
```
http://jaeger:16686
```

All requests include correlation IDs for end-to-end tracing.

### Logging

**Log Levels:**
- `com.waqiti.analytics`: DEBUG (development), INFO (production)
- `org.springframework`: INFO
- `org.hibernate.SQL`: WARN

**Log Format:** JSON (Logstash encoder)

**Correlation IDs:** Included in all log statements for distributed tracing

---

## 🔧 Troubleshooting

### Common Issues

#### Service Won't Start

**Symptom:** Application fails to start

**Solutions:**
1. Check database connectivity:
   ```bash
   psql -h localhost -U waqiti_user -d waqiti_analytics
   ```

2. Verify Kafka is running:
   ```bash
   docker-compose ps kafka
   ```

3. Check port availability:
   ```bash
   lsof -i :8087
   ```

#### DLQ Messages Not Being Processed

**Symptom:** Failed messages accumulating

**Solutions:**
1. Check DLQ table:
   ```sql
   SELECT status, COUNT(*) FROM dlq_messages GROUP BY status;
   ```

2. Review DLQ handler logs:
   ```bash
   kubectl logs -f analytics-service | grep DLQ
   ```

3. Manually retry eligible messages:
   ```sql
   SELECT * FROM dlq_messages WHERE status = 'PENDING_REVIEW' AND retry_count < max_retry_attempts;
   ```

#### High Memory Usage

**Symptom:** OOMKilled or high heap usage

**Solutions:**
1. Check JVM settings in Dockerfile
2. Review Spark executor memory configuration
3. Increase pod memory limits in Kubernetes

#### Circuit Breaker Open

**Symptom:** External service calls failing

**Solutions:**
1. Check circuit breaker health:
   ```bash
   curl http://localhost:8087/actuator/health/circuitbreakers
   ```

2. Review service dependencies
3. Check external service health (notification-service, PagerDuty, Slack)

---

## ✅ Production Readiness

### Current Status: 75% Complete

#### ✅ Completed (Phase 1-3)

- [x] Spring Cloud version updated to 2023.0.4
- [x] DJL version property defined
- [x] JPA Auditing configured (AuditorAware bean)
- [x] DLQ infrastructure complete (entity, repository, migration, base handler)
- [x] Complete DLQ handler example (AnomalyAlertsConsumerDlqHandler)
- [x] NotificationService fully implemented (notification-service, PagerDuty, Slack)
- [x] Resilience4j configuration (circuit breakers, retries, timeouts)
- [x] Feign client configurations (PagerDuty, Slack)
- [x] Comprehensive test examples (entity, repository, service)
- [x] Production-grade error handling
- [x] Correlation ID tracking throughout

#### 🚧 In Progress / Remaining

**High Priority (Week 1-2):**
- [ ] Complete remaining 26 DLQ handlers (use AnomalyAlertsConsumerDlqHandler as template)
- [ ] Implement ManualReviewQueueService (Redis + PostgreSQL)
- [ ] Implement EscalationService (Jira + ServiceNow)
- [ ] Complete AnalyticsDashboardService (KPI calculations)

**Medium Priority (Week 3-4):**
- [ ] Comprehensive unit tests (target 80% coverage)
- [ ] Integration tests for all repositories
- [ ] Controller API tests with security
- [ ] Kafka consumer integration tests

**Low Priority (Week 5-6):**
- [ ] Performance testing and optimization
- [ ] Security scanning (OWASP, SonarQube)
- [ ] Complete API documentation (Swagger)
- [ ] Operational runbooks

### Production Checklist

Before deploying to production:

- [ ] All TODOs resolved
- [ ] Test coverage ≥ 80%
- [ ] Security scan passed (no critical/high vulnerabilities)
- [ ] SonarQube quality gate passed
- [ ] Load testing completed
- [ ] Database migrations tested
- [ ] Rollback plan documented
- [ ] Monitoring dashboards configured
- [ ] Alerts configured
- [ ] Runbooks created

---

## 📞 Support

### Team Contacts

- **Tech Lead:** [Your Name]
- **DevOps:** [DevOps Team]
- **On-Call:** [PagerDuty rotation]

### Resources

- **Confluence:** [Wiki Pages]
- **Jira:** [ANALYTICS project]
- **Slack:** #analytics-service

---

## 📝 License

Copyright © 2025 Waqiti. All rights reserved.

---

## 🙏 Acknowledgments

Built with:
- Spring Boot
- PostgreSQL
- Apache Kafka
- Elasticsearch
- Apache Spark
- TensorFlow

---

**Last Updated:** 2025-11-15
**Version:** 1.0.0-SNAPSHOT
**Production Ready:** 75%
