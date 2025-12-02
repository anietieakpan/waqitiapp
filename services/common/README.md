# Waqiti Common Module

**Version**: 1.0.0
**Status**: Production Ready
**Dependencies**: 80+ microservices

## Overview

The Waqiti Common Module is a shared library providing cross-cutting concerns, utilities, and frameworks used across all microservices in the Waqiti platform. This module serves as the foundational layer for security, observability, fraud detection, compliance, and infrastructure services.

⚠️ **CRITICAL**: This module is a platform-wide dependency. Changes affect 80+ microservices. Follow strict change management procedures.

## Table of Contents

- [Quick Start](#quick-start)
- [Module Structure](#module-structure)
- [Core Components](#core-components)
- [Usage Examples](#usage-examples)
- [API Documentation](#api-documentation)
- [Thread Safety](#thread-safety)
- [Version Compatibility](#version-compatibility)
- [Development Guidelines](#development-guidelines)
- [Testing Requirements](#testing-requirements)
- [Contributing](#contributing)
- [Support](#support)

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>common</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle Dependency

```gradle
implementation 'com.waqiti:common:1.0.0'
```

### Basic Usage

```java
import com.example.common.fraud.FraudContext;
import com.example.common.fraud.alert.FraudAlertService;
import com.example.common.security.EncryptionService;

@Service
public class PaymentService {

    @Autowired
    private FraudAlertService fraudAlertService;

    @Autowired
    private EncryptionService encryptionService;

    public void processPayment(PaymentRequest request) {
        // Fraud detection
        FraudContext context = FraudContext.builder()
            .contextId(UUID.randomUUID().toString())
            .transactionInfo(buildTransactionInfo(request))
            .build();

        if (context.shouldBlockTransaction()) {
            fraudAlertService.createAlert(context);
            throw new FraudDetectionException("Transaction blocked");
        }

        // Encrypt sensitive data
        String encryptedCard = encryptionService.encrypt(request.getCardNumber());

        // Process payment...
    }
}
```

## Module Structure

### Package Organization

The common module is organized into the following top-level packages:

```
com.example.common/
├── alert/                  - Alert management
├── alerting/               - PagerDuty, Slack integration (29 classes)
├── audit/                  - Audit logging framework (55 classes)
├── cache/                  - Caching abstractions (50 classes)
├── compliance/             - Regulatory compliance (23 classes)
├── config/                 - Spring configurations (59 classes)
├── database/               - Database utilities (52 classes)
├── encryption/             - Encryption services (34 classes)
├── events/                 - Event definitions (86 classes)
├── eventsourcing/          - Event sourcing framework (30 classes)
├── exception/              - Custom exceptions (55 classes)
├── fraud/                  - Fraud detection (210 classes) ⚠️
│   ├── alert/              - Alert management
│   ├── analytics/          - Fraud analytics
│   ├── context/            - Context management
│   ├── dto/                - Data transfer objects
│   ├── location/           - Geolocation analysis
│   ├── mapper/             - DTO/Model mapping
│   ├── ml/                 - Machine learning
│   ├── model/              - Domain models (78 classes)
│   ├── profiling/          - Risk profiling
│   ├── rules/              - Rule engine
│   ├── scoring/            - Fraud scoring
│   └── transaction/        - Transaction analysis
├── gdpr/                   - GDPR compliance (18 classes)
├── kafka/                  - Kafka integration (43 classes)
├── kyc/                    - KYC verification (15 classes)
├── notification/           - Notification models (37 classes)
├── observability/          - Metrics, tracing (43 classes)
├── resilience/             - Circuit breakers (33 classes)
├── saga/                   - Saga orchestration (28 classes)
├── security/               - Security services (130 classes)
├── servicemesh/            - Service mesh management (29 classes)
└── validation/             - Validators (61 classes)
```

### File Statistics

- **Production Java Files**: 2,314
- **Test Java Files**: 21+ (Growing)
- **Packages**: 280+
- **Lines of Code**: ~200,000+

## Core Components

### 1. Fraud Detection Framework

**Package**: `com.example.common.fraud.*` (210 classes)

The fraud detection framework provides real-time transaction fraud analysis with ML integration.

#### Key Classes

##### FraudContext

Comprehensive fraud analysis context containing all relevant information for fraud detection.

```java
FraudContext context = FraudContext.builder()
    .contextId(UUID.randomUUID().toString())
    .transactionInfo(transactionInfo)
    .userProfile(userProfile)
    .deviceSession(deviceSession)
    .locationInfo(locationInfo)
    .build();

BigDecimal riskScore = context.getOverallRiskScore();
FraudContext.RiskLevel riskLevel = context.getRiskLevel();
boolean shouldBlock = context.shouldBlockTransaction();
```

**Thread Safety**: ⚠️ NOT thread-safe. Create new instances per transaction.

##### FraudAlert

Fraud alert representation with workflow management.

```java
FraudAlert alert = FraudAlert.builder()
    .alertId(UUID.randomUUID().toString())
    .fraudContext(context)
    .alertType(AlertType.SUSPICIOUS_TRANSACTION)
    .alertSeverity(AlertSeverity.HIGH)
    .build();
```

**Thread Safety**: ⚠️ NOT thread-safe. Use defensive copies for concurrent access.

##### FraudAlertService

Service for managing fraud alerts.

```java
@Service
public class FraudAlertService {
    /**
     * Creates and processes a fraud alert.
     * Thread-safe method using internal ConcurrentHashMap.
     */
    public CompletableFuture<Void> createAlert(FraudAlert alert);

    /**
     * Retrieves pending alerts.
     * Returns defensive copy for thread safety.
     */
    public List<FraudAlert> getPendingAlerts();
}
```

**Thread Safety**: ✅ Thread-safe (uses ConcurrentHashMap internally)

##### FraudContextManager

Manages fraud context lifecycle.

```java
@Service
public class FraudContextManager {
    /**
     * Creates and stores fraud context.
     * Thread-safe.
     */
    public FraudContext createContext(String contextId);

    /**
     * Retrieves active context.
     * Thread-safe.
     */
    public Optional<FraudContext> getContext(String contextId);
}
```

**Thread Safety**: ✅ Thread-safe (uses ConcurrentHashMap for context storage)

### 2. Security Services

**Package**: `com.example.common.security.*` (130 classes)

#### Encryption Service

```java
@Service
public class EncryptionService {
    /**
     * Encrypts data using AES-256-GCM.
     * Thread-safe.
     */
    public String encrypt(String plaintext);

    /**
     * Decrypts data.
     * Thread-safe.
     */
    public String decrypt(String ciphertext);
}
```

#### PII Protection

```java
@Service
public class SensitiveDataMasker {
    /**
     * Masks PII data for logging.
     * Thread-safe - stateless service.
     */
    public String maskCardNumber(String cardNumber);
    public String maskSSN(String ssn);
    public String maskEmail(String email);
}
```

### 3. Validation Framework

**Package**: `com.example.common.validation.*` (61 classes)

Custom validators for business rules.

```java
@NotNull
@WalletOwnership // Custom validator
private String walletId;

@NoXSS // XSS prevention validator
private String description;

@ValidCardNumber // Luhn algorithm validator
private String cardNumber;
```

### 4. Audit Framework

**Package**: `com.example.common.audit.*` (55 classes)

Comprehensive audit logging.

```java
@Audited(action = AuditAction.PAYMENT_PROCESSED)
public void processPayment(Payment payment) {
    // Automatically logs audit trail
}
```

### 5. Event Framework

**Package**: `com.example.common.events.*` (86 classes)

Domain events for event-driven architecture.

```java
@Component
public class PaymentEventPublisher {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void publishPaymentEvent(Payment payment) {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
            this, payment.getId(), payment.getAmount()
        );
        eventPublisher.publishEvent(event);
    }
}
```

### 6. Resilience Framework

**Package**: `com.example.common.resilience.*` (33 classes)

Circuit breakers, rate limiting, bulkheads.

```java
@Service
public class ExternalApiService {

    @CircuitBreaker(name = "externalApi")
    @RateLimiter(name = "externalApi")
    @Retry(name = "externalApi")
    public Response callExternalApi(Request request) {
        // Protected call
    }
}
```

### 7. Observability

**Package**: `com.example.common.observability.*` (43 classes)

Metrics, tracing, logging.

```java
@Service
public class PaymentService {

    @Timed(value = "payment.processing.time")
    @Counted(value = "payment.processing.count")
    public void processPayment(Payment payment) {
        // Automatically tracked
    }
}
```

## API Documentation

### Public API Guarantee

All public classes and methods in `com.example.common.*` are part of the stable API and follow semantic versioning:

- **MAJOR version**: Breaking changes
- **MINOR version**: New features, backward compatible
- **PATCH version**: Bug fixes, backward compatible

### Deprecation Policy

- Deprecated APIs are marked with `@Deprecated`
- Deprecation notice period: **6 months** minimum
- Removal version specified in JavaDoc
- Replacement API documented

Example:

```java
/**
 * @deprecated As of version 1.2.0, replaced by {@link #newMethod()}
 * Scheduled for removal in version 2.0.0
 */
@Deprecated(since = "1.2.0", forRemoval = true)
public void oldMethod() {
    // Delegates to new method
    newMethod();
}
```

## Thread Safety

### Thread-Safe Components

The following components are **thread-safe** and can be used concurrently:

- ✅ `FraudAlertService` - Uses ConcurrentHashMap internally
- ✅ `FraudContextManager` - Uses ConcurrentHashMap for context storage
- ✅ `EncryptionService` - Stateless service
- ✅ `SensitiveDataMasker` - Stateless service
- ✅ `DistributedCacheManager` - Uses synchronization
- ✅ All `@Service` beans - Unless documented otherwise

### NOT Thread-Safe Components

The following components are **NOT thread-safe** and require external synchronization:

- ⚠️ `FraudContext` - Mutable DTO, create new instances per transaction
- ⚠️ `FraudAlert` - Mutable DTO, use defensive copies
- ⚠️ All DTOs with setters - Not designed for concurrent modification

### Thread Safety Guidelines

1. **DTOs**: Assume NOT thread-safe unless marked `@Immutable`
2. **Services**: Assume thread-safe (Spring beans are singletons)
3. **Utilities**: Check JavaDoc for thread safety guarantees
4. **Builders**: NOT thread-safe, use per-thread

## Version Compatibility

### Version Matrix

| Common Version | Spring Boot | Java | Supported Services |
|----------------|-------------|------|-------------------|
| 1.0.0          | 3.3.5       | 21   | All 80+ services  |

### Breaking Changes

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

### Migration Guides

- [Migrating to 1.0.0](docs/migration/1.0.0.md) (Future versions)

## Development Guidelines

### Code Quality Standards

- ✅ **Test Coverage**: Minimum 80% line coverage (enforced by JaCoCo)
- ✅ **Static Analysis**: Checkstyle, SpotBugs, OWASP Dependency-Check
- ✅ **Code Review**: Required for all changes
- ✅ **Documentation**: JavaDoc required for all public APIs
- ✅ **Thread Safety**: Document thread safety guarantees

### Making Changes

⚠️ **CRITICAL**: Changes to this module affect 80+ microservices.

**Before making changes**:

1. ✅ Create JIRA ticket with impact analysis
2. ✅ Get approval from Architecture Review Board
3. ✅ Write tests FIRST (TDD approach)
4. ✅ Ensure backward compatibility
5. ✅ Update JavaDoc with `@since` tags
6. ✅ Update CHANGELOG.md
7. ✅ Notify all consumer teams

**For breaking changes**:

1. ✅ Create deprecation PR first (mark old API as deprecated)
2. ✅ Wait 6 months deprecation period
3. ✅ Create migration guide
4. ✅ Coordinate with all 80+ service teams
5. ✅ Plan staged rollout
6. ✅ Major version bump

### Adding New Features

```java
/**
 * New feature description.
 *
 * @param param Parameter description
 * @return Return value description
 * @since 1.1.0
 */
public ReturnType newMethod(ParamType param) {
    // Implementation
}
```

### Build Commands

```bash
# Clean build
mvn clean install

# Run tests with coverage
mvn clean test jacoco:report

# Run all quality checks
mvn clean verify

# Skip tests (NOT recommended)
mvn clean install -DskipTests

# Security scan
mvn dependency-check:check
```

## Testing Requirements

### Unit Tests

- **Coverage**: Minimum 80% line coverage
- **Naming**: `*Test.java`
- **Location**: `src/test/java/`

```java
@ExtendWith(MockitoExtension.class)
class FraudContextTest {

    @Test
    void shouldCalculateRiskScore() {
        // Given
        FraudContext context = FraudContext.builder()
            .transactionAmount(new BigDecimal("1000.00"))
            .build();

        // When
        BigDecimal riskScore = context.getOverallRiskScore();

        // Then
        assertThat(riskScore).isGreaterThan(BigDecimal.ZERO);
    }
}
```

### Integration Tests

- **Naming**: `*IntegrationTest.java`
- **Use**: TestContainers for external dependencies

```java
@SpringBootTest
@Testcontainers
class FraudAlertServiceIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.8.0")
    );

    @Test
    void shouldPublishAlertToKafka() {
        // Test implementation
    }
}
```

### Test Base Classes

Extend base test classes for common setup:

- `BaseUnitTest` - Unit test setup
- `BaseIntegrationTest` - Integration test setup
- `BaseServiceTest` - Service layer test setup
- `BaseKafkaTest` - Kafka test setup

## Contributing

### Pull Request Process

1. Create feature branch: `feature/JIRA-123-description`
2. Write tests (TDD)
3. Ensure all tests pass: `mvn clean verify`
4. Update documentation
5. Submit PR with:
   - JIRA ticket reference
   - Impact analysis
   - Test coverage report
   - Breaking change notice (if applicable)

### Commit Message Format

```
type(scope): Brief description

Detailed description of changes.

BREAKING CHANGE: Description of breaking change (if applicable)

Refs: JIRA-123
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`

### Code Review Checklist

- [ ] Tests added/updated
- [ ] JavaDoc complete
- [ ] Thread safety documented
- [ ] Backward compatible OR migration guide provided
- [ ] CHANGELOG.md updated
- [ ] No security vulnerabilities
- [ ] Performance impact assessed

## Support

### Documentation

- **API Documentation**: [JavaDoc](https://docs.example.com/common/1.0.0/)
- **Architecture**: [Architecture Docs](docs/architecture/)
- **Guides**: [Implementation Guides](docs/guides/)

### Getting Help

- **Slack**: #waqiti-common-module
- **JIRA**: [Common Module Project](https://jira.example.com/projects/COMMON)
- **Email**: platform-team@example.com

### Reporting Issues

1. Check existing issues in JIRA
2. Create new issue with:
   - Version information
   - Steps to reproduce
   - Expected vs actual behavior
   - Stack trace (if applicable)
3. Label with severity (P0-P4)

### Emergency Contacts

- **Platform Team Lead**: platform-lead@example.com
- **Security Issues**: security@example.com (PGP key available)
- **PagerDuty**: On-call engineer (for production incidents)

## License

Copyright © 2024 Waqiti, Inc. All rights reserved.

Proprietary and confidential. Unauthorized copying or distribution prohibited.

---

**Last Updated**: 2025-11-18
**Maintainer**: Waqiti Platform Team
**Status**: ✅ Production Ready
