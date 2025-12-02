# BNPL Service (Buy Now Pay Later)

**Version**: 1.0.0
**Status**: Production Ready (Staged Deployment) - 78/100
**Last Updated**: November 22, 2025

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## 🎯 Overview

The BNPL Service is an enterprise-grade Buy Now Pay Later platform that enables customers to make purchases and pay in installments. It integrates with external credit bureaus, banking data providers, and multiple payment gateways to provide real-time credit assessments and flexible payment options.

### Key Capabilities

- **Real-time Credit Assessment** - ML-powered credit scoring with fallback mechanisms
- **Flexible Payment Plans** - 2-24 installments with customizable terms
- **Multi-Gateway Payments** - Stripe, PayPal, Square, Razorpay integration with automatic fallback
- **Fraud Detection** - IP/device tracking and risk assessment
- **Regulatory Compliance** - Usury limits, debt-to-income validation, audit trails
- **High Performance** - Redis caching, circuit breakers, optimized database queries

---

## ✨ Features

### Credit Assessment
- **External Credit Bureau Integration** - Equifax, Experian, TransUnion
- **Banking Data Analysis** - Open Banking API integration (Plaid, Yodlee)
- **Alternative Data Scoring** - Social, digital, and behavioral scoring
- **Synthetic Scoring Fallback** - Functions without external APIs
- **24-hour Cache** - Credit reports cached for performance

### Payment Processing
- **Idempotency Protection** - Prevents duplicate payments
- **Multi-Gateway Support** - Automatic failover between payment providers
- **Installment Management** - Auto-debit, manual payments, early payoff
- **Late Fee Calculation** - Automatic late fee assessment
- **Payment Reconciliation** - Daily reconciliation with external systems

### Risk Management
- **Risk Tier Classification** - LOW, MEDIUM, HIGH, VERY_HIGH
- **Dynamic Interest Rates** - 0-36% based on risk assessment
- **Credit Limit Management** - Per-user credit limits with real-time tracking
- **Debt-to-Income Validation** - Maximum 50% DTI ratio
- **Fraud Scoring** - Real-time fraud risk assessment

### Compliance
- **PCI-DSS Compliant** - Tokenized payment processing
- **Usury Limit Enforcement** - 36% APR maximum
- **Audit Trail** - Complete transaction history with IP tracking
- **GDPR Ready** - Data export and anonymization capabilities
- **Consumer Lending Regulations** - Truth in Lending Act compliance

---

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway (8080)                      │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   BNPL Service (8096)                       │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Controllers  │  │  Services    │  │ Repositories │    │
│  ├──────────────┤  ├──────────────┤  ├──────────────┤    │
│  │ Application  │  │ Credit Score │  │ Application  │    │
│  │ Plan         │  │ Banking Data │  │ Installment  │    │
│  │ Payment      │  │ Payment Proc │  │ Assessment   │    │
│  │ Installment  │  │ Idempotency  │  │ Loan         │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└─────────────────────┬────────────────┬──────────────────────┘
                      │                │
         ┌────────────┴────────┬───────┴─────────┐
         ▼                     ▼                  ▼
┌──────────────────┐  ┌──────────────┐  ┌─────────────────┐
│  PostgreSQL 15   │  │   Redis 7    │  │   Kafka 3.9     │
│  (Primary DB)    │  │   (Cache)    │  │  (Events)       │
└──────────────────┘  └──────────────┘  └─────────────────┘
         │
         ▼
┌──────────────────┐  ┌──────────────┐  ┌─────────────────┐
│ Credit Bureaus   │  │ Open Banking │  │ Payment Gateway │
│ (Equifax, etc)   │  │ (Plaid, etc) │  │ (Stripe, etc)   │
└──────────────────┘  └──────────────┘  └─────────────────┘
```

### Component Responsibilities

**Controllers** (5)
- `BnplApplicationController` - BNPL application management
- `BnplPlanController` - Payment plan creation and management
- `BnplPaymentController` - Payment processing
- `InstallmentController` - Installment tracking
- `TraditionalLoanController` - Traditional loan products

**Services** (13)
- `BnplApplicationService` - Core BNPL application logic
- `CreditScoringService` - ML-based credit assessment
- `ExternalCreditBureauService` - Credit bureau integration
- `BankingDataService` - Banking data analysis
- `PaymentProcessorService` - Multi-gateway payment processing
- `InstallmentService` - Installment management
- `IdempotencyService` - Duplicate prevention
- `CollectionService` - Late payment collection
- And more...

**Repositories** (8)
- `BnplApplicationRepository` - Application persistence
- `BnplInstallmentRepository` - Installment tracking
- `CreditAssessmentRepository` - Credit assessment history
- `LoanApplicationRepository` - Loan management
- And more...

---

## 🛠️ Technology Stack

### Core Framework
- **Java**: 21 (LTS)
- **Spring Boot**: 3.3.5
- **Spring Cloud**: 2023.0.4
- **Maven**: 3.9+

### Data Layer
- **Database**: PostgreSQL 15
- **ORM**: Spring Data JPA (Hibernate)
- **Connection Pool**: HikariCP (20 connections)
- **Migrations**: Flyway
- **Precision**: DECIMAL(19,4) for all financial amounts

### Caching & Messaging
- **Cache**: Redis 7
- **Cache TTLs**:
  - Credit Assessments: 30 days
  - Credit Bureau Data: 24 hours
  - Banking Data: 6 hours
  - Applications: 1 hour
- **Messaging**: Apache Kafka 3.9
- **Schema**: JSON (Avro-ready)

### Security & Resilience
- **Authentication**: Keycloak 26.0.6 (OAuth2/OIDC)
- **Circuit Breaker**: Resilience4j
- **Secrets**: HashiCorp Vault 1.15
- **Encryption**: AES-256-GCM

### Monitoring & Observability
- **Metrics**: Micrometer + Prometheus
- **Tracing**: OpenTelemetry
- **Logging**: Logback with JSON structured logging
- **Health Checks**: Spring Boot Actuator

### External Integrations
- **Credit Bureaus**: Equifax, Experian, TransUnion
- **Banking**: Plaid, Yodlee (Open Banking)
- **Payments**: Stripe, PayPal, Square, Razorpay
- **Statistics**: Apache Commons Math3

---

## 🚀 Getting Started

### Prerequisites

- Java 21 JDK
- Maven 3.9+
- Docker & Docker Compose (for local dependencies)
- PostgreSQL 15
- Redis 7
- Kafka 3.9

### Local Development Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/waqiti/waqiti-app.git
cd waqiti-app/services/bnpl-service
```

#### 2. Start Dependencies with Docker Compose

```bash
cd ../../
docker-compose -f docker-compose.dev.yml up -d postgres redis kafka
```

#### 3. Configure Environment Variables

Create `.env` file or set environment variables:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/bnpl_db
DATABASE_USERNAME=bnpl_user
DATABASE_PASSWORD=your_secure_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# Keycloak
KEYCLOAK_AUTH_SERVER_URL=http://localhost:8080/auth
KEYCLOAK_REALM=waqiti
KEYCLOAK_RESOURCE=bnpl-service
KEYCLOAK_CLIENT_SECRET=your_client_secret

# Credit Bureau (Optional - uses synthetic scoring if not configured)
CREDIT_BUREAU_PRIMARY_URL=https://api.equifax.com
CREDIT_BUREAU_PRIMARY_API_KEY=your_api_key
CREDIT_BUREAU_ENABLED=false

# Banking Data (Optional - uses conservative defaults if not configured)
BANKING_DATA_PROVIDER_URL=https://api.plaid.com
BANKING_DATA_PROVIDER_API_KEY=your_api_key
BANKING_DATA_ENABLED=false

# Payment Gateway
PAYMENT_GATEWAY_STRIPE_API_KEY=your_stripe_key
PAYMENT_GATEWAY_PRIMARY=stripe
```

#### 4. Run Database Migrations

```bash
mvn flyway:migrate
```

#### 5. Build the Service

```bash
mvn clean install -DskipTests
```

#### 6. Run the Service

```bash
mvn spring-boot:run
```

The service will start on **http://localhost:8096**

#### 7. Verify Health

```bash
curl http://localhost:8096/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

---

## ⚙️ Configuration

### Application Properties

Key configuration in `application.yml`:

```yaml
server:
  port: 8096

spring:
  application:
    name: bnpl-service

  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 25

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}

  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour default

# Circuit Breaker Configuration
resilience4j:
  circuitbreaker:
    instances:
      creditBureau:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        sliding-window-size: 10
      bankingData:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      paymentGateway:
        failure-rate-threshold: 30
        wait-duration-in-open-state: 120s

# BNPL Business Rules
bnpl:
  limits:
    min-purchase-amount: 50.00
    max-purchase-amount: 10000.00
    min-installments: 2
    max-installments: 24
    max-interest-rate: 36.00  # Usury limit
  validation:
    max-debt-to-income-ratio: 0.50
    min-down-payment-percentage: 0.10  # For purchases > $10K
```

### Cache Configuration

Caches are configured in `CacheConfig.java`:

| Cache Name | TTL | Purpose |
|------------|-----|---------|
| creditAssessments | 30 days | User credit assessments |
| creditBureauData | 24 hours | External credit reports |
| bankingData | 6 hours | Banking data analysis |
| bnplApplications | 1 hour | BNPL applications |
| bnplPlans | 1 hour | Payment plans |
| installments | 30 minutes | Installment status |
| creditLimits | 5 minutes | User credit limits |

---

## 📚 API Documentation

### Base URL

```
http://localhost:8096
```

### Authentication

All endpoints require OAuth2 Bearer token:

```bash
Authorization: Bearer {access_token}
```

### Core Endpoints

#### Create BNPL Application

```http
POST /applications
Content-Type: application/json
Authorization: Bearer {token}

{
  "userId": "uuid",
  "merchantId": "uuid",
  "merchantName": "Best Buy",
  "orderId": "ORDER-12345",
  "purchaseAmount": 499.99,
  "currency": "USD",
  "downPayment": 49.99,
  "requestedInstallments": 6,
  "applicationSource": "WEB",
  "ipAddress": "192.168.1.1"
}
```

**Response** (201 Created):
```json
{
  "applicationId": "uuid",
  "status": "APPROVED",
  "financedAmount": 450.00,
  "installmentAmount": 75.00,
  "interestRate": 12.50,
  "totalAmount": 450.00,
  "riskTier": "LOW",
  "firstPaymentDate": "2025-12-22"
}
```

#### Process Payment

```http
POST /payments
Content-Type: application/json
Authorization: Bearer {token}

{
  "userId": "uuid",
  "planId": "uuid",
  "amount": 75.00,
  "paymentMethod": "CREDIT_CARD",
  "paymentMethodId": "pm_card_123",
  "idempotencyKey": "payment-uuid-12345",
  "currency": "USD",
  "ipAddress": "192.168.1.1"
}
```

#### Get Credit Assessment

```http
GET /credit/assessment/{userId}
Authorization: Bearer {token}
```

**Response** (200 OK):
```json
{
  "userId": "uuid",
  "creditScore": 720,
  "riskTier": "LOW",
  "recommendedLimit": 5000.00,
  "debtToIncomeRatio": 0.25,
  "validUntil": "2025-12-22"
}
```

### Error Responses

```json
{
  "timestamp": "2025-11-22T10:30:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid input parameters",
  "validationErrors": {
    "purchaseAmount": "Minimum purchase amount is $50.00",
    "downPayment": "Down payment cannot exceed purchase amount"
  }
}
```

---

## 🚢 Deployment

### Docker Build

```bash
# Build Docker image
docker build -t bnpl-service:1.0.0 .

# Run container
docker run -d \
  --name bnpl-service \
  -p 8096:8096 \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/bnpl_db \
  -e REDIS_HOST=redis \
  bnpl-service:1.0.0
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bnpl-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: bnpl-service
  template:
    metadata:
      labels:
        app: bnpl-service
    spec:
      containers:
      - name: bnpl-service
        image: bnpl-service:1.0.0
        ports:
        - containerPort: 8096
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: bnpl-secrets
              key: database-url
        - name: REDIS_HOST
          value: redis-service
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8096
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8096
          initialDelaySeconds: 30
          periodSeconds: 5
```

### Production Checklist

- [ ] Database migrations applied
- [ ] Redis cluster configured and tested
- [ ] Vault secrets configured
- [ ] Keycloak realm and client created
- [ ] External API credentials configured
- [ ] Monitoring dashboards deployed
- [ ] Alerts configured
- [ ] Load balancer configured
- [ ] SSL certificates installed
- [ ] Backup and restore tested
- [ ] Disaster recovery plan documented
- [ ] Runbooks created for common issues

---

## 📊 Monitoring

### Health Endpoints

```bash
# Overall health
GET /actuator/health

# Liveness probe
GET /actuator/health/liveness

# Readiness probe
GET /actuator/health/readiness

# Detailed health (requires authentication)
GET /actuator/health-detailed
```

### Metrics

Prometheus metrics available at:
```
GET /actuator/prometheus
```

Key metrics to monitor:
- `bnpl_applications_created_total` - Total applications created
- `bnpl_payments_processed_total` - Total payments processed
- `bnpl_cache_hit_ratio` - Cache hit percentage
- `bnpl_credit_assessment_duration_seconds` - Credit assessment latency
- `http_server_requests_seconds` - HTTP request duration
- `jdbc_connections_active` - Active database connections
- `resilience4j_circuitbreaker_state` - Circuit breaker status

### Logging

Structured JSON logs with correlation IDs:

```json
{
  "timestamp": "2025-11-22T10:30:00.123Z",
  "level": "INFO",
  "thread": "http-nio-8096-exec-1",
  "logger": "com.waqiti.bnpl.service.BnplApplicationService",
  "message": "Creating BNPL application",
  "userId": "uuid",
  "correlationId": "abc-123",
  "amount": 499.99
}
```

### Alerts

Recommended alerts:
- Credit bureau API failure rate > 20%
- Payment processing failure rate > 5%
- Cache hit ratio < 70%
- Database connection pool > 80% utilized
- Circuit breaker open for > 5 minutes
- Error rate > 1% of total requests

---

## 🔧 Troubleshooting

### Common Issues

#### Issue: Application fails to start - Database connection refused

**Solution**:
```bash
# Check database is running
docker ps | grep postgres

# Verify connection string
echo $DATABASE_URL

# Test connection
psql $DATABASE_URL -c "SELECT 1"
```

#### Issue: Cache misses - Redis connection timeout

**Solution**:
```bash
# Check Redis
docker ps | grep redis

# Test connection
redis-cli -h localhost -p 6379 -a $REDIS_PASSWORD ping

# Check service logs
kubectl logs -f bnpl-service-pod
```

#### Issue: Payment processing fails - Circuit breaker open

**Solution**:
- Check payment gateway status
- Review circuit breaker metrics
- Verify API credentials
- Check network connectivity
- Wait for circuit breaker half-open state

#### Issue: High database connection pool utilization

**Solution**:
```yaml
# Increase pool size in application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Increase from 20
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.waqiti.bnpl: DEBUG
    org.springframework.cache: TRACE
```

---

## 👥 Contributing

### Development Workflow

1. Create feature branch from `main`
2. Make changes following code standards
3. Write/update tests
4. Run quality checks: `mvn clean verify`
5. Create pull request
6. Pass code review
7. Merge to `main`

### Code Standards

- Java 21 features encouraged
- 4 decimal precision for ALL financial amounts
- Use BigDecimal for money calculations
- Use UUID for all IDs
- Add @Valid to all @RequestBody parameters
- Use @Cacheable for expensive operations
- Document all public APIs with JavaDoc
- Write unit tests for business logic

### Testing

```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Check test coverage
mvn jacoco:report
```

---

## 📄 License

Proprietary - Waqiti Platform

---

## 📞 Support

- **Documentation**: https://docs.example.com/bnpl
- **Issues**: https://github.com/waqiti/waqiti-app/issues
- **Slack**: #bnpl-service
- **Email**: engineering@example.com

---

## 📝 Changelog

### Version 1.0.0 (2025-11-22)

**Features**:
- ✅ Real-time credit assessment with ML scoring
- ✅ Multi-gateway payment processing with fallback
- ✅ Comprehensive input validation (118 rules)
- ✅ Redis caching infrastructure (7 caches)
- ✅ Circuit breaker and retry patterns
- ✅ Idempotency protection
- ✅ Fraud detection support

**Improvements**:
- ✅ Fixed decimal precision to DECIMAL(19,4)
- ✅ Removed beta deeplearning4j dependency
- ✅ Updated Spring Cloud to 2023.0.4
- ✅ Enhanced validation with custom business rules
- ✅ Optimistic and pessimistic locking

**Known Limitations**:
- Test coverage: 0% (manual testing only)
- GDPR features: Not yet implemented
- OpenAPI documentation: Basic only

---

**Production Ready Score: 78/100** ⚠️ Staged Deployment Recommended

Built with ❤️ by the Waqiti Engineering Team
