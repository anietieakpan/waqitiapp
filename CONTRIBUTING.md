# Contributing to Waqiti

Thank you for your interest in contributing to Waqiti! This document provides comprehensive guidelines and instructions for contributing to this enterprise fintech platform.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Types of Contributions](#types-of-contributions)
3. [Development Setup](#development-setup)
4. [Architecture Guidelines](#architecture-guidelines)
5. [Coding Standards](#coding-standards)
6. [Testing Requirements](#testing-requirements)
7. [Security Requirements](#security-requirements)
8. [Pull Request Process](#pull-request-process)
9. [Documentation](#documentation)
10. [Getting Help](#getting-help)

---

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for everyone.

### Our Standards

**Positive behavior includes:**
- Using welcoming and inclusive language
- Being respectful of differing viewpoints and experiences
- Gracefully accepting constructive criticism
- Focusing on what is best for the community

**Unacceptable behavior includes:**
- Trolling, insulting/derogatory comments, and personal attacks
- Public or private harassment
- Publishing others' private information without permission

---

## Types of Contributions

| Type | Description | Labels |
|------|-------------|--------|
| Bug Reports | Report issues you've found | `bug` |
| Documentation | Improve docs, fix typos | `documentation` |
| Bug Fixes | Fix reported issues | `bug`, `help wanted` |
| New Features | Add new functionality | `enhancement` |
| Performance | Optimize existing code | `performance` |
| Security | Security improvements | `security` |

### First-Time Contributors

Look for issues labeled `good first issue` - these are ideal starting points.

## How to Contribute

### Reporting Bugs

1. **Check existing issues** to avoid duplicates
2. **Use the bug report template** when creating a new issue
3. **Include**:
   - Clear description of the bug
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (OS, Java version, etc.)
   - Relevant logs or screenshots

### Suggesting Features

1. **Check existing feature requests** first
2. **Describe the problem** your feature would solve
3. **Propose a solution** with as much detail as possible
4. **Consider the scope** - does it fit the project's goals?

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Follow the coding standards** outlined below
3. **Write tests** for any new functionality
4. **Update documentation** as needed
5. **Ensure all tests pass** before submitting
6. **Reference related issues** in your PR description

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 18+ (for frontend)
- Docker and Docker Compose
- PostgreSQL 15+ (or use Docker)

### Getting Started

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/waqiti-app.git
cd waqiti-app

# Copy environment template
cp .env.template .env
# Edit .env with your local configuration

# Start infrastructure services
docker-compose up -d postgres redis kafka zookeeper keycloak

# Wait for services to be healthy
docker-compose ps

# Build all services
mvn clean install -DskipTests

# Run a specific service
cd services/user-service
mvn spring-boot:run
```

### IDE Setup

#### IntelliJ IDEA (Recommended)

1. Open the project root directory
2. Import as Maven project
3. Enable annotation processing:
   - Settings → Build → Compiler → Annotation Processors
   - Check "Enable annotation processing"
4. Install plugins:
   - Lombok
   - MapStruct Support
   - Spring Boot Assistant

#### VS Code

1. Install extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Lombok Annotations Support
2. Open the project folder
3. Allow Java extension to import the project

### Local Development URLs

| Service | URL | Default Credentials |
|---------|-----|---------------------|
| API Gateway | http://localhost:8080 | - |
| Keycloak Admin | http://localhost:8180 | admin/admin |
| Kafka UI | http://localhost:8081 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin/admin |
| PostgreSQL | localhost:5432 | See .env file |
| Redis | localhost:6379 | See .env file |

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for a specific service
mvn test -pl services/user-service

# Run integration tests
mvn verify -P integration-tests

# Run with coverage
mvn test jacoco:report

# Run a single test class
mvn test -Dtest=PaymentServiceTest

# Run tests with specific Spring profile
mvn test -Dspring.profiles.active=test
```

---

## Architecture Guidelines

### Service Design Principles

1. **Single Responsibility**: Each service handles one bounded context
2. **Database Per Service**: Never share databases between services
3. **API-First Design**: Define OpenAPI spec before implementation
4. **Event-Driven Communication**: Prefer Kafka over REST for service-to-service communication

### When to Use Sync vs Async Communication

| Use Synchronous (Feign/REST) | Use Asynchronous (Kafka) |
|------------------------------|--------------------------|
| Real-time queries | State changes |
| Validation checks | Notifications |
| User-facing requests | Analytics events |
| When response is needed immediately | When eventual consistency is acceptable |

### Resilience Patterns

All external service calls must implement:

```java
@CircuitBreaker(name = "serviceName", fallbackMethod = "fallbackMethod")
@Retry(name = "serviceName")
@Bulkhead(name = "serviceName")
public Response callExternalService() {
    // Implementation
}
```

---

## Coding Standards

### Java

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful variable and method names
- Write Javadoc for public APIs
- Keep methods small and focused (< 30 lines preferred)
- Use dependency injection (constructor injection preferred)

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Services | `*Service` | `PaymentService` |
| Controllers | `*Controller` | `PaymentController` |
| Repositories | `*Repository` | `PaymentRepository` |
| DTOs | `*Request`, `*Response` | `PaymentRequest` |
| Entities | Singular noun | `Payment`, `User` |
| Events | `*Event` | `PaymentCompletedEvent` |
| Clients | `*Client` | `WalletServiceClient` |

### Code Organization

```
services/service-name/
├── src/main/java/com/waqiti/servicename/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── service/         # Business logic
│   │   └── impl/        # Service implementations
│   ├── repository/      # Data access
│   ├── domain/          # Domain entities
│   ├── dto/             # Data transfer objects
│   ├── mapper/          # MapStruct mappers
│   ├── event/           # Kafka producers/consumers
│   ├── client/          # Feign clients
│   └── exception/       # Custom exceptions
└── src/test/java/       # Tests mirror main structure
```

### Dependency Injection

```java
// GOOD: Constructor injection
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final WalletClient walletClient;

    public PaymentService(PaymentRepository paymentRepository,
                          WalletClient walletClient) {
        this.paymentRepository = paymentRepository;
        this.walletClient = walletClient;
    }
}

// BAD: Field injection - avoid this
@Service
public class PaymentService {
    @Autowired  // Don't do this
    private PaymentRepository paymentRepository;
}
```

### Exception Handling

```java
// Define domain-specific exceptions
public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(UUID userId, BigDecimal requested) {
        super(ErrorCode.INSUFFICIENT_FUNDS,
              String.format("User %s has insufficient funds for %s", userId, requested));
    }
}

// Use global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ex.getErrorCode().name());
        problem.setDetail(ex.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }
}
```

### TypeScript/React (Frontend)

- Follow [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)
- Use TypeScript strict mode
- Prefer functional components with hooks
- Write unit tests with React Testing Library

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): description

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

Examples:
```
feat(payment): add support for recurring payments
fix(auth): resolve token refresh race condition
docs(api): update OpenAPI specification for v2
```

## Security Requirements

**CRITICAL**: This is a financial application. Security is paramount.

### Before Submitting Code

1. **No secrets in code**: Never commit API keys, passwords, or tokens
2. **No hardcoded credentials**: Use environment variables or Vault
3. **Input validation**: Validate all user input
4. **SQL injection prevention**: Use parameterized queries only
5. **XSS prevention**: Sanitize output, use Content Security Policy
6. **Authentication checks**: Ensure proper auth on all endpoints
7. **Authorization checks**: Verify user permissions

### Security Checklist

- [ ] No secrets or credentials in the code
- [ ] All inputs are validated
- [ ] SQL queries use parameterization
- [ ] Sensitive data is encrypted
- [ ] Proper authentication is implemented
- [ ] Authorization checks are in place
- [ ] Error messages don't leak sensitive info
- [ ] Logs don't contain sensitive data

### Prohibited in Pull Requests

- Hardcoded passwords, API keys, or tokens
- Disabled security features
- SQL string concatenation
- eval() or similar dynamic code execution
- Insecure random number generation for security contexts
- Disabled SSL/TLS verification
- Overly permissive CORS configuration

---

## Testing Requirements

### Coverage Requirements

| Category | Minimum Coverage |
|----------|------------------|
| Overall | 80% |
| Service classes | 90% |
| Controllers | 85% |
| Repositories | 70% |
| Utility classes | 95% |

### Unit Tests

- Test both happy path and error cases
- Mock external dependencies
- Use meaningful test names (describe behavior, not method names)
- Follow Given-When-Then structure

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletClient walletClient;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("Should complete payment when sender has sufficient funds")
    void processPayment_WhenSufficientFunds_ShouldComplete() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .senderId(UUID.randomUUID())
            .recipientId(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .build();

        when(walletClient.getBalance(request.getSenderId()))
            .thenReturn(new Balance(new BigDecimal("500.00")));

        // When
        Payment result = paymentService.processPayment(request);

        // Then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(walletClient).debit(eq(request.getSenderId()), any());
        verify(walletClient).credit(eq(request.getRecipientId()), any());
    }

    @Test
    @DisplayName("Should throw exception when insufficient funds")
    void processPayment_WhenInsufficientFunds_ShouldThrow() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .senderId(UUID.randomUUID())
            .amount(new BigDecimal("1000.00"))
            .build();

        when(walletClient.getBalance(request.getSenderId()))
            .thenReturn(new Balance(new BigDecimal("50.00")));

        // When/Then
        assertThatThrownBy(() -> paymentService.processPayment(request))
            .isInstanceOf(InsufficientFundsException.class);
    }
}
```

### Integration Tests

Use TestContainers for realistic database and messaging tests:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test_payments");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_payment:write")
    void createPayment_ShouldReturnCreated() throws Exception {
        String requestBody = """
            {
                "recipientId": "550e8400-e29b-41d4-a716-446655440000",
                "amount": "100.00",
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }
}
```

### Contract Tests

Use Pact for consumer-driven contract testing between services:

```java
@Provider("payment-service")
@PactBroker
class PaymentServiceContractTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("a payment with ID 123 exists")
    void setupPaymentExists() {
        // Setup test data
    }
}
```

## Documentation

### Code Documentation

- Write Javadoc for all public classes and methods
- Document complex algorithms with inline comments
- Keep README files updated

### API Documentation

- Use OpenAPI/Swagger annotations
- Document request/response schemas
- Include example requests and responses
- Document error codes and their meanings

---

## Pull Request Process

### Before Submitting

1. **Create an issue first** for significant changes
2. **Fork and branch** from `main` using naming convention:
   - `feature/description` for new features
   - `fix/description` for bug fixes
   - `docs/description` for documentation
3. **Ensure all tests pass**: `mvn test`
4. **Check coverage**: Minimum 80%
5. **Run linting**: `mvn checkstyle:check`
6. **Update documentation** if needed

### PR Template

When opening a PR, include:

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] All tests pass locally

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-reviewed
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Security checklist completed
```

### Review Process

1. All PRs require at least one approving review
2. CI checks must pass (tests, linting, security scans)
3. All review comments must be addressed
4. Squash and merge for clean history

---

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue with the `bug` label
- **Feature Requests**: Open an issue with the `enhancement` label
- **Security Issues**: See [SECURITY.md](SECURITY.md) - do NOT open public issues

### Response Times

| Type | Expected Response |
|------|-------------------|
| Security issues | 24 hours |
| Bug reports | 48 hours |
| Feature requests | 1 week |
| PRs | 3-5 business days |

---

## License

By contributing, you agree that your contributions will be licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).

---

## Recognition

Contributors are recognized in:
- CONTRIBUTORS.md file
- Release notes
- Project README

---

Thank you for contributing to Waqiti! Your contributions help build a better fintech platform for everyone.
