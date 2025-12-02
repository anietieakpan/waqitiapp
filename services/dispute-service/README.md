# Dispute Service

**Official Waqiti Dispute Management Service**

[![Status](https://img.shields.io/badge/status-active-success)]()
[![Production](https://img.shields.io/badge/production-deployed-blue)]()
[![Last Updated](https://img.shields.io/badge/updated-2025--11--20-brightgreen)]()

---

## 📋 Overview

The **Dispute Service** is Waqiti's comprehensive transaction dispute management and resolution service. It handles all aspects of dispute lifecycle management, from initial filing through investigation, evidence collection, resolution, and compliance reporting.

### Key Capabilities

- 🎯 **Transaction Dispute Management** - Create, track, and resolve transaction disputes
- 💳 **Chargeback Processing** - Handle card network chargebacks and representments
- 🔍 **Fraud Investigation** - Investigate potentially fraudulent transactions
- 📄 **Evidence Management** - Collect, store, and verify dispute evidence
- 🤖 **Automated Decision Making** - AI-ready auto-resolution capabilities
- 📊 **Resolution Tracking** - Monitor SLA compliance and resolution rates
- 🔗 **Payment Processor Integration** - Integrate with Stripe, PayPal, etc.
- 💬 **Customer Communication** - Multi-channel dispute communications
- 🔒 **Distributed Idempotency** - Redis-based duplicate prevention
- 📁 **Secure File Management** - Encrypted evidence storage
- ⚖️ **Regulatory Compliance** - Support for Reg E, Reg Z, card network rules

---

## 🏗️ Architecture

### Service Type
- **Pattern:** Domain Microservice
- **Database:** PostgreSQL (dedicated `waqiti_disputes` database)
- **Messaging:** Kafka (event-driven)
- **Cache:** Redis (idempotency, caching)
- **API:** REST + OpenAPI 3.0

### Technology Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.3.4 | Application framework |
| PostgreSQL | 42.7.4 | Primary data store |
| Redis | - | Caching & idempotency |
| Kafka | - | Event streaming |
| MapStruct | 1.5.5 | DTO mapping |
| Resilience4j | 2.2.0 | Circuit breakers & resilience |
| JWT (jjwt) | 0.12.6 | Authentication |
| Testcontainers | 1.19.8 | Integration testing |

---

## 📦 Service Components

### Controllers (1)
- **DisputeController** - REST API for dispute management (437 lines)
  - Comprehensive dispute lifecycle operations
  - JWT-based authentication (IDOR protected)
  - Evidence upload endpoints
  - Status tracking and updates

### Service Layers (13 Specialized Services)

| Service | Responsibility |
|---------|---------------|
| **ChargebackService** | Card network chargeback processing |
| **DisputeAnalysisService** | Analytics, reporting, and insights |
| **DisputeManagementService** | Core dispute lifecycle management |
| **DisputeNotificationService** | Dispute-specific notifications |
| **DisputeResolutionService** | Resolution workflows and decisions |
| **DistributedIdempotencyService** | Redis-based duplicate prevention |
| **FraudDetectionService** | Fraud scoring and investigation |
| **InvestigationService** | Investigation workflows |
| **NotificationService** | General notification handling |
| **SecureFileUploadService** | Encrypted evidence file handling |
| **TransactionDisputeService** | Transaction-specific dispute logic |
| **TransactionService** | Transaction data integration |
| **WalletService** | Wallet integration for disputes |

### Domain Entities (12)

| Entity | Description |
|--------|-------------|
| **Dispute** | Core dispute entity with full lifecycle |
| **DisputeEvidence** | Evidence files and metadata |
| **DisputeStatus** | Enum: SUBMITTED, UNDER_REVIEW, RESOLVED, etc. |
| **DisputeType** | Enum: CHARGEBACK, FRAUD, UNAUTHORIZED, etc. |
| **DisputePriority** | Enum: CRITICAL, HIGH, MEDIUM, LOW |
| **ResolutionDecision** | Enum: APPROVED, REJECTED, PARTIAL, etc. |
| **DLQEntry** | Dead letter queue tracking |
| **DLQStatus** | DLQ processing status |
| **EvidenceType** | Types of evidence (RECEIPT, INVOICE, etc.) |
| **RecoveryStrategy** | Retry strategies for failures |
| **VerificationStatus** | Evidence verification states |
| **+ more** | Additional supporting entities |

---

## 🔌 API Endpoints

### Base URL
```
/api/v1/disputes
```

### Key Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/v1/disputes` | Create new dispute | JWT |
| `GET` | `/api/v1/disputes/{id}` | Get dispute by ID | JWT |
| `GET` | `/api/v1/disputes/user/{userId}` | Get user's disputes | JWT |
| `PUT` | `/api/v1/disputes/{id}` | Update dispute | JWT |
| `POST` | `/api/v1/disputes/{id}/evidence` | Upload evidence | JWT |
| `GET` | `/api/v1/disputes/{id}/evidence` | List evidence | JWT |
| `POST` | `/api/v1/disputes/{id}/resolve` | Resolve dispute | JWT |
| `GET` | `/api/v1/disputes/stats` | Dispute statistics | JWT |

### Security Features
- ✅ **JWT Authentication** - All endpoints require valid JWT tokens
- ✅ **IDOR Protection** - User ID extracted from SecurityContext (not headers)
- ✅ **Role-Based Access** - @PreAuthorize guards on all operations
- ✅ **Input Validation** - Comprehensive @Valid annotations
- ✅ **Rate Limiting** - Resilience4j rate limiters configured
- ✅ **Circuit Breakers** - Protection for all external calls

---

## 📊 Database Schema

### Primary Tables

**disputes**
- Core dispute information
- Status tracking
- Financial details
- SLA tracking
- Auto-resolution flags

**dispute_evidence**
- Evidence files and metadata
- Verification status
- File storage references

**dispute_communication**
- Customer/merchant communications
- Internal notes
- Audit trail

**dispute_timeline**
- Event history
- Status changes
- Action logs

**processed_events**
- Kafka event deduplication
- Idempotency tracking

**dlq_entries**
- Failed message tracking
- Retry management

### Migrations

| Version | Description | Status |
|---------|-------------|--------|
| V001 | Initial schema | ✅ Applied |
| V002 | Foreign key indexes | ✅ Applied |
| V003 | DLQ table | ✅ Applied |
| V004 | Processed events update | ✅ Applied |
| V005 | Missing indexes | ✅ Applied |
| V2 | Missing FK indexes | ✅ Applied |

---

## 🔄 Event Integration

### Kafka Topics Consumed

| Topic | Event Type | Handler |
|-------|-----------|---------|
| `transaction-dispute-events` | Dispute lifecycle events | DisputeEventsConsumer |
| `payment-dispute-events` | Payment-specific disputes | PaymentDisputeConsumer |
| `fraud-detection-events` | Fraud alerts | FraudDisputeConsumer |
| `chargeback-events` | Card network chargebacks | ChargebackConsumer |

### Kafka Topics Published

| Topic | Event Type | When |
|-------|-----------|------|
| `dispute-created-events` | DisputeCreatedEvent | New dispute filed |
| `dispute-resolved-events` | DisputeResolvedEvent | Dispute resolved |
| `dispute-escalated-events` | DisputeEscalatedEvent | Escalation triggered |
| `evidence-uploaded-events` | EvidenceUploadedEvent | Evidence submitted |

---

## 🔒 Security & Compliance

### Authentication
- **JWT-based authentication** using Spring Security
- **SecurityContext integration** - User ID from JWT token
- **IDOR prevention** - No user-supplied IDs in sensitive operations

### Authorization
- **Role-based access control** using @PreAuthorize
- **Resource-level permissions** - Users can only access their own disputes
- **Admin/agent roles** - Support agents have elevated permissions

### Compliance

| Regulation | Status | Coverage |
|-----------|--------|----------|
| **Reg E (Electronic Funds)** | ✅ Supported | Unauthorized transaction disputes |
| **Reg Z (Credit Cards)** | ✅ Supported | Credit card dispute timelines |
| **NACHA Rules** | ✅ Supported | ACH dispute handling |
| **Card Network Rules** | ✅ Supported | Visa/Mastercard chargeback flows |
| **PCI-DSS** | ✅ Compliant | Secure evidence storage |
| **GDPR** | ✅ Compliant | Data privacy controls |

---

## 🧪 Testing

### Test Coverage
- **Unit Tests** - Service layer and business logic
- **Integration Tests** - Database interactions with Testcontainers
- **API Tests** - REST endpoint validation
- **Security Tests** - Authentication and authorization
- **Contract Tests** - Kafka event schemas

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify

# With coverage report
mvn clean test jacoco:report

# View coverage
open target/site/jacoco/index.html
```

---

## 🚀 Deployment

### Environment Variables

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `DB_URL` | ✅ Yes | PostgreSQL connection URL | - |
| `DB_USERNAME` | ✅ Yes | Database username | - |
| `DB_PASSWORD` | ✅ Yes | Database password | - |
| `KAFKA_BOOTSTRAP_SERVERS` | ✅ Yes | Kafka brokers | - |
| `REDIS_HOST` | ✅ Yes | Redis host | localhost |
| `REDIS_PORT` | No | Redis port | 6379 |
| `JWT_SECRET` | ✅ Yes | JWT signing key | - |

### Health Checks

```bash
# Actuator health endpoint
curl http://localhost:8080/actuator/health

# Readiness probe
curl http://localhost:8080/actuator/health/readiness

# Liveness probe
curl http://localhost:8080/actuator/health/liveness
```

### Metrics

Prometheus metrics available at:
```
http://localhost:8080/actuator/prometheus
```

---

## 📚 Related Documentation

### Architecture Decisions
- **[ADR-005](../../docs/architecture/decisions/ADR-005-remove-dispute-resolution-service.md)** - Removal of duplicate dispute-resolution-service
- **[ADR-004](../../docs/architecture/decisions/ADR-004-remove-chargeback-service.md)** - Chargeback functionality merged into this service

### API Documentation
- **OpenAPI/Swagger:** http://localhost:8080/swagger-ui.html
- **API Docs:** http://localhost:8080/v3/api-docs

### Related Services
- **payment-service** - Payment processing and transaction management
- **fraud-service** - Fraud detection and scoring
- **notification-service** - Customer communications
- **wallet-service** - Wallet balance management
- **transaction-service** - Transaction history and details

---

## 🛠️ Development

### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+
- Kafka 3.0+
- Docker (for integration tests)

### Local Setup

```bash
# Clone repository
git clone <repository-url>
cd services/dispute-service

# Start dependencies (requires Docker Compose)
docker-compose up -d postgres redis kafka

# Run database migrations
mvn flyway:migrate

# Start service
mvn spring-boot:run

# Or build and run
mvn clean package
java -jar target/dispute-service-1.0-SNAPSHOT.jar
```

### Configuration Profiles

| Profile | Purpose | Config File |
|---------|---------|-------------|
| `dev` | Local development | application-dev.yml |
| `test` | Integration testing | application-test.yml |
| `staging` | Staging environment | application-staging.yml |
| `production` | Production | application-production.yml |

---

## 📈 Monitoring & Observability

### Metrics Collected
- Dispute creation rate
- Resolution time (SLA compliance)
- Chargeback win/loss ratio
- Evidence upload success rate
- API response times
- Circuit breaker states
- Database connection pool stats

### Logging
- **Format:** Structured JSON logging
- **Level:** INFO (production), DEBUG (development)
- **Correlation:** Request correlation IDs
- **Sensitive Data:** Masked in logs

### Alerts
- SLA breaches
- High dispute volume
- Resolution backlog
- Circuit breaker open
- Database connection failures

---

## 🐛 Troubleshooting

### Common Issues

**Issue: Service won't start**
```bash
# Check database connection
psql -h localhost -U waqiti -d waqiti_disputes

# Check Kafka connectivity
kafka-topics.sh --list --bootstrap-server localhost:9092

# Check logs
tail -f logs/dispute-service.log
```

**Issue: Disputes not creating**
- Verify JWT token is valid
- Check user has correct role
- Verify transaction exists in transaction-service
- Check Kafka connectivity

**Issue: Evidence upload failing**
- Verify file size limits
- Check storage service connectivity
- Verify user permissions

---

## 👥 Ownership

**Team:** Financial Operations
**Primary Maintainer:** Engineering Team
**On-Call:** Financial Services Team
**Slack Channel:** #team-financial-ops

---

## 📝 Changelog

### [2025-11-20]
- ✅ Confirmed as official dispute service (ADR-005)
- ✅ Removed duplicate dispute-resolution-service
- 📚 Updated documentation

### [2025-11-18]
- ✨ Enhanced security features (IDOR protection)
- 🔒 SecurityContext integration for JWT
- 🐛 Bug fixes in evidence upload

### [2025-11-10]
- 🔄 Merged chargeback-service functionality (ADR-004)
- ✨ Added comprehensive chargeback handling
- 📊 Enhanced analytics and reporting

---

## 🎯 Roadmap

### Planned Features
- [ ] **AI-Based Auto-Resolution** - Intelligent dispute triage (Q1 2026)
- [ ] **State Machine Workflow** - Advanced workflow modeling (Q2 2026)
- [ ] **PDF Report Generation** - Automated dispute reports (Q1 2026)
- [ ] **Multi-Currency Support** - International dispute handling (Q2 2026)
- [ ] **Advanced Analytics** - ML-based dispute prediction (Q3 2026)

### Future Improvements
- Enhanced fraud detection integration
- Real-time dispute status dashboard
- Mobile app support for dispute filing
- Automated evidence collection

---

## 📞 Support

For questions or issues:
- **Documentation:** This README and [ADR-005](../../docs/architecture/decisions/ADR-005-remove-dispute-resolution-service.md)
- **Issues:** Create ticket in JIRA
- **Slack:** #team-financial-ops
- **Email:** financial-ops@example.com

---

**Version:** 1.0.0
**Last Updated:** 2025-11-20
**Status:** ✅ Production Active
