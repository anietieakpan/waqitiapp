# Compliance Contracts Module

## Overview

The `compliance-contracts` module provides shared contracts (DTOs, client interfaces, events) for the compliance domain across the Waqiti platform. This module enables **service decoupling** by eliminating direct compile-time dependencies between services.

## Purpose

**Problem Solved:**
Previously, `security-service` had a compile-time dependency on `compliance-service`, violating microservice independence principles. This created:
- Services that couldn't deploy independently
- Circular dependency risks
- Shared fate (both services must deploy together)
- Build failures when one service changes

**Solution:**
This module provides:
1. **Shared DTOs** - Data transfer objects for compliance operations
2. **Feign Client Interface** - Type-safe REST client for compliance-service
3. **Event Schemas** - Event-driven communication contracts
4. **Circuit Breaker Fallbacks** - Graceful degradation when service unavailable

## Architecture Pattern

```
┌─────────────────────┐
│  security-service   │
│                     │
│  Uses:              │
│  - ComplianceService│
│    Client (Feign)   │
│  - Compliance DTOs  │
└──────────┬──────────┘
           │
           │ Depends on
           ▼
┌─────────────────────┐
│compliance-contracts │ ← Shared module (minimal dependencies)
│                     │
│  Contains:          │
│  - DTOs             │
│  - Client interface │
│  - Fallback factory │
└──────────┬──────────┘
           │
           │ Implements
           ▼
┌─────────────────────┐
│ compliance-service  │
│                     │
│  Implements:        │
│  - REST endpoints   │
│  - Business logic   │
└─────────────────────┘
```

## Key Features

### 1. Type-Safe Feign Client

```java
@Autowired
private ComplianceServiceClient complianceClient;

// Synchronous validation
ComplianceValidationRequest request = ComplianceValidationRequest.builder()
    .requestId(UUID.randomUUID().toString())
    .validationType(ComplianceValidationType.COMPREHENSIVE)
    .targetEntityId(userId)
    .entityType(EntityType.USER)
    .requestingService("security-service")
    .priority(ValidationPriority.HIGH)
    .build();

ResponseEntity<ComplianceValidationResponse> response =
    complianceClient.validateCompliance(request);
```

### 2. Circuit Breaker Support

Automatic fallback when compliance-service is unavailable:

```java
// If compliance-service is down, fallback returns:
ComplianceValidationResponse {
    overallStatus: ERROR,
    errorMessage: "Compliance service unavailable - circuit breaker activated",
    criticalIssues: [...],
    recommendations: [...]
}
```

### 3. AML Case Management

```java
// Create AML case
AMLCaseRequest caseRequest = AMLCaseRequest.builder()
    .caseId(UUID.randomUUID().toString())
    .caseType(AMLCaseType.SUSPICIOUS_ACTIVITY)
    .subjectId(userId)
    .priority(AMLPriority.HIGH)
    .description("Unusual transaction pattern detected")
    .build();

AMLCaseResponse response = complianceClient.createAMLCase(caseRequest);
```

### 4. Compliance Status Checking

```java
// Quick compliance status check
ComplianceStatusDTO status = complianceClient.getComplianceStatus(
    "USER",
    userId
).getBody();

if (!status.getCompliant()) {
    // Handle non-compliant user
}
```

## Module Structure

```
compliance-contracts/
├── src/main/java/com/waqiti/compliance/contracts/
│   ├── client/
│   │   ├── ComplianceServiceClient.java          # Feign client interface
│   │   └── ComplianceServiceClientFallbackFactory.java  # Circuit breaker fallback
│   ├── dto/
│   │   ├── ComplianceValidationRequest.java
│   │   ├── ComplianceValidationResponse.java
│   │   ├── ComplianceCheckResultDTO.java
│   │   ├── ComplianceFindingDTO.java
│   │   ├── ComplianceStatusDTO.java
│   │   ├── ComplianceReportDTO.java
│   │   ├── ComplianceMetricsDTO.java
│   │   ├── HealthCheckDTO.java
│   │   ├── RequirementResultDTO.java
│   │   ├── ComponentHealth.java
│   │   ├── ComplianceStatus.java              # Enum
│   │   ├── ComplianceValidationType.java      # Enum
│   │   ├── ComplianceFramework.java           # Enum
│   │   ├── EntityType.java                    # Enum
│   │   ├── ValidationPriority.java            # Enum
│   │   ├── FindingSeverity.java               # Enum
│   │   ├── FindingCategory.java               # Enum
│   │   ├── FindingStatus.java                 # Enum
│   │   ├── HealthStatus.java                  # Enum
│   │   ├── ReportStatus.java                  # Enum
│   │   └── aml/
│   │       ├── AMLCaseRequest.java
│   │       ├── AMLCaseResponse.java
│   │       ├── AMLCaseType.java               # Enum
│   │       ├── AMLCaseStatus.java             # Enum
│   │       └── AMLPriority.java               # Enum
│   └── event/
│       └── (Event schemas - to be implemented)
└── pom.xml
```

## Usage

### Adding Dependency

Add to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>compliance-contracts</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Enable Feign Clients

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.compliance.contracts.client")
public class SecurityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityServiceApplication.class, args);
    }
}
```

### Configure Circuit Breaker (Optional but Recommended)

`application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      compliance-service:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s
        slowCallRateThreshold: 50
```

## Dependency Philosophy

This module intentionally has **minimal dependencies**:

✅ **Included:**
- Lombok (reducing boilerplate)
- Jackson (JSON serialization)
- Jakarta Validation (field validation)
- Spring Cloud OpenFeign (client interface - provided scope)
- Spring Web (REST annotations - provided scope)

❌ **Excluded:**
- Spring Boot (no autoconfiguration)
- Spring Data (no database access)
- Kafka (event schemas separate)
- AWS/Azure/GCP SDKs (not needed for contracts)
- Common module (avoiding transitive dependency explosion)

## Migration Guide

### For security-service

**Before (Direct Dependency):**
```java
// security-service/pom.xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>compliance-service</artifactId>  <!-- WRONG -->
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>

// Java code
import com.example.compliance.service.CaseManagementService;  // Direct import
import com.example.compliance.service.ComplianceReportingService;

@Autowired
private CaseManagementService caseManagementService;  // Compile dependency!
```

**After (Decoupled):**
```java
// security-service/pom.xml
<dependency>
    <groupId>com.waqiti</groupId>
    <artifactId>compliance-contracts</artifactId>  <!-- CORRECT -->
    <version>1.0-SNAPSHOT</version>
</dependency>

// Java code
import com.example.compliance.contracts.client.ComplianceServiceClient;
import com.example.compliance.contracts.dto.*;

@Autowired
private ComplianceServiceClient complianceClient;  // Feign client - no compile dependency!
```

### For compliance-service

**Action Required:**
1. Implement REST endpoints matching the Feign client interface
2. Return DTOs from compliance-contracts module
3. No code changes in business logic (internal implementation unchanged)

**Example Controller:**
```java
@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {

    @Autowired
    private ComplianceService complianceService;  // Internal service

    @PostMapping("/validate")
    public ResponseEntity<ComplianceValidationResponse> validateCompliance(
        @Valid @RequestBody ComplianceValidationRequest request
    ) {
        // Convert request DTO → internal domain
        // Execute validation
        // Convert result → response DTO
        return ResponseEntity.ok(response);
    }
}
```

## Testing

### Unit Testing with Mock Feign Client

```java
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private ComplianceServiceClient complianceClient;

    @InjectMocks
    private SecurityService securityService;

    @Test
    void testComplianceValidation() {
        // Arrange
        ComplianceValidationResponse mockResponse = ComplianceValidationResponse.builder()
            .overallStatus(ComplianceStatus.COMPLIANT)
            .complianceScore(95.0)
            .build();

        when(complianceClient.validateCompliance(any()))
            .thenReturn(ResponseEntity.ok(mockResponse));

        // Act
        ComplianceValidationResponse result = securityService.checkCompliance(userId);

        // Assert
        assertThat(result.getCompliant()).isTrue();
    }
}
```

### Integration Testing

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class ComplianceClientIntegrationTest {

    @Autowired
    private ComplianceServiceClient complianceClient;

    @Test
    void testComplianceValidation() {
        // Stub WireMock to simulate compliance-service
        stubFor(post(urlEqualTo("/api/v1/compliance/validate"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(mockResponseJson)));

        // Test actual Feign client
        ResponseEntity<ComplianceValidationResponse> response =
            complianceClient.validateCompliance(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

## Benefits

### 1. Service Independence
- Services can be deployed independently
- No shared fate between security-service and compliance-service
- Build failures in one service don't block the other

### 2. Loose Coupling
- Services communicate via REST APIs (network boundary)
- Changes to internal implementation don't affect clients
- Easier to refactor and evolve services independently

### 3. Resilience
- Circuit breaker prevents cascading failures
- Graceful degradation when service unavailable
- Services continue operating with reduced functionality

### 4. Testability
- Easy to mock Feign client in unit tests
- Integration tests with WireMock
- Contract testing with Pact (future)

### 5. Type Safety
- Compile-time checking of API contracts
- IDE autocomplete and refactoring support
- Clear documentation through interfaces

## Troubleshooting

### Circuit Breaker Constantly Open

**Symptom:** All calls return fallback responses

**Diagnosis:**
```bash
# Check circuit breaker metrics
curl http://localhost:8080/actuator/circuitbreakers
```

**Solutions:**
1. Verify compliance-service is running
2. Check network connectivity
3. Review circuit breaker thresholds (may be too sensitive)
4. Check compliance-service response times

### Feign Client Not Found

**Symptom:** `NoSuchBeanDefinitionException: No qualifying bean of type ComplianceServiceClient`

**Solution:**
```java
// Add @EnableFeignClients with correct package
@EnableFeignClients(basePackages = "com.example.compliance.contracts.client")
```

### Deserialization Errors

**Symptom:** `Cannot deserialize instance of...`

**Solution:**
1. Ensure DTOs have no-args constructors (Lombok `@NoArgsConstructor`)
2. Check Jackson annotations on DTOs
3. Verify API response matches DTO structure

## Roadmap

### Phase 1 (Completed)
- ✅ Create shared DTOs
- ✅ Implement Feign client interface
- ✅ Add circuit breaker fallback
- ✅ AML case management contracts

### Phase 2 (In Progress)
- ⏳ Event-driven contracts (Kafka events)
- ⏳ Avro schema definitions
- ⏳ Contract testing with Pact

### Phase 3 (Planned)
- 📋 GraphQL schema (alternative to REST)
- 📋 gRPC contracts (high-performance alternative)
- 📋 WebSocket contracts (real-time compliance updates)

## Contributing

When adding new contracts:

1. **Keep DTOs simple** - No business logic, only data
2. **Use builder pattern** - Lombok `@Builder` for all DTOs
3. **Add validation** - Jakarta Validation annotations where appropriate
4. **Document fields** - JavaDoc for all public fields
5. **Maintain backward compatibility** - Never remove fields without deprecation period
6. **Version APIs** - Use `/api/v1/`, `/api/v2/` for breaking changes

## Related Documentation

- [Microservices Architecture Guide](../../docs/architecture/microservices.md)
- [Circuit Breaker Pattern](../../docs/patterns/circuit-breaker.md)
- [Service Communication Patterns](../../docs/patterns/service-communication.md)
- [API Versioning Strategy](../../docs/api/versioning.md)

## Support

For questions or issues:
- **Architecture:** @architecture-team
- **Compliance Domain:** @compliance-team
- **Feign/Circuit Breaker:** @platform-team

---

**Last Updated:** 2025-11-22
**Module Version:** 1.0-SNAPSHOT
**Status:** Production Ready
