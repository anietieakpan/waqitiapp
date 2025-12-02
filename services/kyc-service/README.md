# KYC Service

A comprehensive, industrial-grade Know Your Customer (KYC) microservice for the Waqiti P2P payment platform.

## Overview

The KYC Service provides centralized identity verification and compliance management across the Waqiti platform. It supports multiple verification providers, implements graduated verification levels, and ensures regulatory compliance while maintaining a seamless user experience.

## Architecture

### Core Components

1. **Domain Layer**
   - `KYCVerification`: Core verification entity
   - `VerificationDocument`: Document management
   - `VerificationLevel`: Basic, Intermediate, Advanced levels
   - `KYCStatus`: State management

2. **Service Layer**
   - `KYCService`: Main business logic
   - `DocumentService`: Document handling
   - `KYCProviderService`: Provider integrations
   - `CacheService`: Performance optimization
   - `KYCCompatibilityService`: Legacy system compatibility

3. **Integration Layer**
   - Multi-provider support (Onfido, Jumio, ComplyAdvantage)
   - Event-driven architecture with Kafka
   - RESTful APIs with OpenAPI documentation

## Features

### Verification Levels

| Level | Requirements | Capabilities |
|-------|-------------|--------------|
| **Basic** | Email & Phone verification | Send/receive up to $1,000/day |
| **Intermediate** | Government ID verification | International transfers, $10,000/day limit |
| **Advanced** | Enhanced due diligence | Crypto purchases, $100,000/day limit |

### Security & Compliance

- **GDPR Compliant**: Right to erasure, data retention policies
- **PCI DSS Ready**: Secure document storage and encryption
- **AML/KYC**: Sanctions screening, PEP checks
- **Audit Trail**: Complete verification history

### Performance Features

- **Redis Caching**: Reduced latency for status checks
- **Circuit Breakers**: Resilient provider integrations
- **Async Processing**: Non-blocking verification flows
- **Horizontal Scaling**: Kubernetes-ready deployment

## API Endpoints

### Verification Management

```bash
# Initiate verification
POST /api/v1/kyc/users/{userId}/verifications
{
  "verificationLevel": "INTERMEDIATE",
  "consentGiven": true,
  "preferredProvider": "ONFIDO"
}

# Get verification status
GET /api/v1/kyc/users/{userId}/status

# Check verification
GET /api/v1/kyc/users/{userId}/verified?level=BASIC
```

### Document Management

```bash
# Upload document
POST /api/v1/kyc/verifications/{verificationId}/documents
Content-Type: multipart/form-data

# Get documents
GET /api/v1/kyc/verifications/{verificationId}/documents
```

### Migration & Feature Flags

```bash
# Migrate user data
POST /api/v1/kyc/migration/users/{userId}

# Update feature flags
PUT /api/v1/kyc/feature-flags/USE_NEW_KYC_SERVICE
{
  "enabled": true,
  "percentage": 25
}
```

## Integration Guide

### 1. Add KYC Client Dependency

```xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>kyc-client</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Use KYC Annotations

```java
@Service
public class PaymentService {
    
    @RequireKYCVerification(level = VerificationLevel.INTERMEDIATE)
    public void processInternationalTransfer(TransferRequest request) {
        // Method only executes if user has intermediate KYC
    }
}
```

### 3. Check KYC Status Programmatically

```java
@Service
public class TransactionService {
    private final KYCClientService kycClient;
    
    public void validateTransaction(String userId, BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            if (!kycClient.isUserIntermediateVerified(userId)) {
                throw new InsufficientKYCException("Intermediate KYC required");
            }
        }
    }
}
```

## Migration Strategy

### Phase 1: Shadow Mode (Current)
- All traffic goes to legacy system
- New service runs in parallel for comparison
- No user impact

### Phase 2: Dual Write
- Writes to both systems
- Reads from legacy with gradual migration
- Data consistency guaranteed

### Phase 3: Gradual Rollout
- 5% → 25% → 50% → 100% traffic migration
- Feature flag controlled
- Instant rollback capability

### Phase 4: Cleanup
- Remove legacy code
- Decommission old tables
- Update all integrations

## Configuration

### Application Properties

```yaml
kyc:
  providers:
    onfido:
      api-key: ${ONFIDO_API_KEY}
      enabled: true
    default-provider: ONFIDO
  
  verification:
    max-attempts-per-user: 3
    document-expiry-days: 365
    auto-approve-threshold: 0.95
  
  security:
    encrypt-documents: true
    watermark-documents: true
    audit-log-enabled: true
  
  compliance:
    gdpr-enabled: true
    data-retention-days: 2555
    restricted-countries: [IR, KP, SY]
```

### Environment Variables

- `ONFIDO_API_KEY`: Onfido API credentials
- `JUMIO_API_KEY`: Jumio API credentials
- `KYC_ENCRYPTION_KEY`: Document encryption key
- `DATABASE_URL`: PostgreSQL connection string
- `REDIS_HOST`: Redis cache host

## Monitoring & Observability

### Metrics
- Verification success/failure rates
- Provider response times
- Document processing duration
- Cache hit rates

### Health Checks
- `/actuator/health`: Service health
- `/actuator/health/readiness`: Readiness probe
- `/actuator/health/liveness`: Liveness probe

### Logging
- Structured JSON logs
- Correlation IDs for request tracing
- Audit logs for compliance

## Development

### Prerequisites
- Java 17+
- PostgreSQL 14+
- Redis 6+
- Maven 3.8+

### Running Locally

```bash
# Start dependencies
docker-compose up -d postgres redis kafka

# Run migrations
mvn liquibase:update

# Start service
mvn spring-boot:run
```

### Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -P integration

# Load tests
mvn gatling:test
```

## Production Deployment

### Kubernetes

```bash
# Deploy to Kubernetes
kubectl apply -f infrastructure/kubernetes/base/kyc-service/

# Scale deployment
kubectl scale deployment kyc-service --replicas=5

# Check status
kubectl get pods -l app=kyc-service
```

### Database Migrations

```bash
# Preview changes
mvn liquibase:updateSQL

# Apply migrations
mvn liquibase:update

# Rollback if needed
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

## Troubleshooting

### Common Issues

1. **Provider Timeout**
   - Check circuit breaker status
   - Verify provider API status
   - Review timeout configuration

2. **Document Upload Failures**
   - Check file size limits (10MB default)
   - Verify allowed file types
   - Check S3 permissions

3. **Migration Errors**
   - Verify database connectivity
   - Check for data inconsistencies
   - Review migration logs

## Support

- Documentation: [/docs/kyc-service](https://docs.example.com/kyc-service)
- Issues: [GitHub Issues](https://github.com/waqiti/waqiti-app/issues)
- Slack: #kyc-service channel

## License

Copyright © 2024 Waqiti. All rights reserved.