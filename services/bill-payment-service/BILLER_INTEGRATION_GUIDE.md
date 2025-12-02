# 🔌 Biller Integration Guide
## Bill Payment Service - External Biller Integration

---

## Table of Contents
1. [Overview](#overview)
2. [What Was Fixed](#what-was-fixed)
3. [Current Implementation](#current-implementation)
4. [Architecture](#architecture)
5. [Production Implementation Guide](#production-implementation-guide)
6. [Real-World Examples](#real-world-examples)
7. [Testing Strategy](#testing-strategy)
8. [Deployment Roadmap](#deployment-roadmap)

---

## Overview

The Bill Payment Service integrates with external biller systems (electricity companies, water utilities, credit card companies, etc.) to enable bill inquiry, payment submission, and account management.

### The Problem
The `BillPaymentService` was injecting a `BillerIntegrationProvider` dependency that didn't exist, causing a compilation failure.

### The Solution
Created a complete provider interface and default mock implementation that:
- ✅ Allows service to compile successfully
- ✅ Provides realistic mock data for development
- ✅ Enables end-to-end testing without external dependencies
- ✅ Defines clear contract for production implementations

---

## What Was Fixed

### Files Created

#### 1. **BillerIntegrationProvider.java** (Interface)
**Location**: `src/main/java/com/waqiti/billpayment/provider/BillerIntegrationProvider.java`

**Methods** (11 total):
```java
public interface BillerIntegrationProvider {
    BillerConnectionResult establishConnection(String userId, UUID billerId, ...);
    BillInquiryResult inquireBill(String billerCode, String accountNumber, ...);
    PaymentSubmissionResult submitPayment(UUID billerId, UUID billId, ...);
    List<BillData> fetchBills(UUID connectionId);
    boolean supportsDirectPayment(UUID billerId);
    boolean supportsNegotiation(UUID billerId);
    NegotiationResult initiateBillNegotiation(UUID billerId, UUID billId, ...);
    String verifyPaymentStatus(UUID billerId, String externalPaymentId);
    boolean testConnection(UUID billerId);
}
```

**Inner Classes**:
- `BillData` - DTO for bill information from external biller
- `NegotiationResult` - DTO for negotiation responses

#### 2. **DefaultBillerIntegrationProvider.java** (Implementation)
**Location**: `src/main/java/com/waqiti/billpayment/provider/DefaultBillerIntegrationProvider.java`

**Purpose**: Working stub/mock implementation
- Returns realistic mock data
- Validates inputs
- Full logging
- Ready for development and testing
- **Not suitable for production** (no real API calls)

---

## Current Implementation

### How It Works

```
BillPaymentService
        ↓
    (injects)
        ↓
BillerIntegrationProvider (interface)
        ↓
    (implements)
        ↓
DefaultBillerIntegrationProvider (mock)
        ↓
    (returns)
        ↓
Mock Data (realistic responses)
```

### Example Mock Response

```java
// Bill Inquiry
BillInquiryResult result = BillInquiryResult.success(
    "John Doe",                    // Account name
    new BigDecimal("150.75"),      // Bill amount
    new BigDecimal("50.00"),       // Minimum due
    "USD",                         // Currency
    LocalDate.now().plusDays(15),  // Due date
    "BILL-ABC12345"                // Reference number
);

// Payment Submission
PaymentSubmissionResult result = PaymentSubmissionResult.success(
    "CONF-ELECTRICITY-XYZ789",     // Confirmation number
    "TXN-123456789ABC",            // Transaction reference
    new BigDecimal("150.75")       // Processed amount
);
```

---

## Architecture

### Option 1: Multiple Implementations (Recommended)

Create one provider per biller type:

```java
@Component("electricityBillerProvider")
public class ElectricityBillerProvider implements BillerIntegrationProvider {
    // Real API calls to electricity companies
}

@Component("waterBillerProvider")
public class WaterBillerProvider implements BillerIntegrationProvider {
    // Real API calls to water utilities
}

@Component("creditCardBillerProvider")
public class CreditCardBillerProvider implements BillerIntegrationProvider {
    // Real API calls to credit card companies
}
```

**Inject using qualifiers**:
```java
@Autowired
@Qualifier("electricityBillerProvider")
private BillerIntegrationProvider electricityProvider;
```

### Option 2: Factory Pattern

```java
@Component
public class BillerIntegrationFactory {

    @Autowired
    private Map<String, BillerIntegrationProvider> providers;

    public BillerIntegrationProvider getProvider(String billerType) {
        return providers.getOrDefault(
            billerType + "BillerProvider",
            defaultProvider
        );
    }
}
```

### Option 3: Dynamic Provider Selection

```java
@Service
public class BillerProviderService {

    @Autowired
    private BillerRepository billerRepository;

    @Autowired
    private Map<String, BillerIntegrationProvider> providers;

    public BillerIntegrationProvider getProviderForBiller(UUID billerId) {
        Biller biller = billerRepository.findById(billerId)
            .orElseThrow();

        String providerKey = biller.getProviderType() + "Provider";
        return providers.getOrDefault(providerKey, defaultProvider);
    }
}
```

---

## Production Implementation Guide

### Step 1: Research Biller API

Document for each biller:
```markdown
## Electricity Company XYZ

**API Type**: REST
**Base URL**: https://api.electricityxyz.com/v2
**Authentication**: OAuth 2.0
**Rate Limits**: 100 requests/minute
**Sandbox**: https://sandbox.electricityxyz.com/v2

### Endpoints
- **Bill Inquiry**: POST /bills/inquiry
- **Payment Submit**: POST /payments/submit
- **Payment Status**: GET /payments/{id}/status

### Field Mapping
| Their Field | Our Field |
|-------------|-----------|
| customer_account_no | accountNumber |
| current_charges | billAmount |
| minimum_payment | minimumDue |
| payment_due_date | dueDate |
```

### Step 2: Implement Provider

```java
@Slf4j
@Component("electricityBillerProvider")
@RequiredArgsConstructor
public class ElectricityCompanyBillerProvider implements BillerIntegrationProvider {

    private final RestTemplate restTemplate;
    private final BillerCredentialService credentialService;

    @Value("${electricity.api.url}")
    private String apiUrl;

    @Value("${electricity.api.key}")
    private String apiKey;

    @Retryable(
        value = {HttpServerErrorException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    @CircuitBreaker(name = "electricityBiller")
    @Override
    public BillInquiryResult inquireBill(
            String billerCode,
            String accountNumber,
            String accountName) {

        try {
            // Build request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ElectricityInquiryRequest request = ElectricityInquiryRequest.builder()
                .customerAccountNo(accountNumber)
                .customerName(accountName)
                .build();

            HttpEntity<ElectricityInquiryRequest> entity =
                new HttpEntity<>(request, headers);

            // Make API call
            ResponseEntity<ElectricityBillResponse> response =
                restTemplate.postForEntity(
                    apiUrl + "/bills/inquiry",
                    entity,
                    ElectricityBillResponse.class
                );

            // Map response
            ElectricityBillResponse billData = response.getBody();

            return BillInquiryResult.success(
                billData.getCustomerName(),
                billData.getCurrentCharges(),
                billData.getMinimumPayment(),
                "USD",
                billData.getPaymentDueDate(),
                billData.getBillNumber()
            );

        } catch (HttpClientErrorException.BadRequest e) {
            log.error("Invalid request to electricity API: {}",
                e.getResponseBodyAsString());
            return BillInquiryResult.failure(
                "INVALID_REQUEST",
                "Account number not found"
            );

        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Authentication failed with electricity API");
            return BillInquiryResult.failure(
                "AUTH_FAILED",
                "API authentication failed"
            );

        } catch (HttpServerErrorException e) {
            log.error("Electricity API server error: {}",
                e.getResponseBodyAsString());
            throw e; // Retry will handle this

        } catch (Exception e) {
            log.error("Electricity inquiry failed", e);
            return BillInquiryResult.failure(
                "INQUIRY_FAILED",
                e.getMessage()
            );
        }
    }

    @Override
    public PaymentSubmissionResult submitPayment(
            UUID billerId,
            UUID billId,
            String accountNumber,
            BigDecimal amount,
            String currency,
            String paymentReference) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ElectricityPaymentRequest request =
                ElectricityPaymentRequest.builder()
                    .accountNumber(accountNumber)
                    .paymentAmount(amount)
                    .paymentReference(paymentReference)
                    .build();

            HttpEntity<ElectricityPaymentRequest> entity =
                new HttpEntity<>(request, headers);

            ResponseEntity<ElectricityPaymentResponse> response =
                restTemplate.postForEntity(
                    apiUrl + "/payments/submit",
                    entity,
                    ElectricityPaymentResponse.class
                );

            ElectricityPaymentResponse paymentData = response.getBody();

            return PaymentSubmissionResult.success(
                paymentData.getConfirmationNumber(),
                paymentData.getTransactionId(),
                paymentData.getAmountProcessed()
            );

        } catch (Exception e) {
            log.error("Payment submission failed", e);
            return PaymentSubmissionResult.failure(
                "SUBMISSION_FAILED",
                e.getMessage()
            );
        }
    }
}
```

### Step 3: Configuration

**application.yml**:
```yaml
# Electricity Company Configuration
electricity:
  api:
    url: ${ELECTRICITY_API_URL:https://api.electricityxyz.com/v2}
    key: ${ELECTRICITY_API_KEY}
    timeout: 30000

# Resilience4j Configuration
resilience4j:
  circuitbreaker:
    instances:
      electricityBiller:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 60000

  retry:
    instances:
      electricityBiller:
        maxAttempts: 3
        waitDuration: 2000
```

### Step 4: Error Handling

Create biller-specific exceptions:
```java
public class BillerApiException extends RuntimeException {
    private final String billerName;
    private final String errorCode;
    private final int httpStatus;

    // Constructor and getters
}

public class BillerAuthenticationException extends BillerApiException {
    // Specific for auth failures
}

public class BillerRateLimitException extends BillerApiException {
    // Specific for rate limiting
}
```

---

## Real-World Examples

### Example 1: Electricity Bill Inquiry

**User Action**: User enters account number to check bill

**Flow**:
```
1. User submits: Account #123456
2. BillPaymentService calls: billerProvider.inquireBill()
3. Provider makes HTTP call: POST https://api.electric.com/bills/inquiry
4. API returns: {
    "account_number": "123456",
    "customer_name": "John Doe",
    "current_charges": 150.75,
    "minimum_payment": 50.00,
    "due_date": "2025-12-15",
    "bill_number": "ELEC-2025-11-123456"
   }
5. Provider maps to: BillInquiryResult
6. Service saves bill to database
7. User sees: Bill Amount: $150.75, Due: Dec 15, 2025
```

### Example 2: Credit Card Payment

**User Action**: User pays credit card bill

**Flow**:
```
1. User submits: Pay $500 on Card ending 1234
2. Service validates: Sufficient wallet balance
3. Service debits wallet: $500
4. Service calls: billerProvider.submitPayment()
5. Provider makes HTTP call: POST https://api.creditcard.com/payments
6. API returns: {
    "confirmation": "CC-CONF-XYZ789",
    "transaction_id": "TXN-123456789",
    "status": "SUBMITTED",
    "estimated_posting": "2025-11-21T00:00:00Z"
   }
7. Provider maps to: PaymentSubmissionResult
8. Service updates payment status: COMPLETED
9. Service sends notification: Payment confirmed
10. User sees: Payment successful, Confirmation: CC-CONF-XYZ789
```

### Example 3: Water Utility Auto-Import

**System Action**: Scheduler runs at midnight

**Flow**:
```
1. Scheduler triggers: processAutoImports()
2. Service finds connections due for import
3. For each connection:
   a. Service calls: billerProvider.fetchBills()
   b. Provider makes HTTP call: GET https://api.water.com/bills/list
   c. API returns: [
       {
         "bill_id": "WATER-2025-11-001",
         "amount": 45.50,
         "due_date": "2025-12-01",
         ...
       },
       {
         "bill_id": "WATER-2025-10-001",
         "amount": 42.00,
         "due_date": "2025-11-01",
         ...
       }
     ]
   d. Provider maps to: List<BillData>
   e. Service saves new bills
   f. Service sends notification: "2 new bills imported"
4. User wakes up: Sees notification of new water bills
```

---

## Testing Strategy

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class ElectricityBillerProviderTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private BillerCredentialService credentialService;

    @InjectMocks
    private ElectricityCompanyBillerProvider provider;

    @Test
    void shouldInquireBillSuccessfully() {
        // Given
        ElectricityBillResponse apiResponse = new ElectricityBillResponse();
        apiResponse.setCurrentCharges(new BigDecimal("150.75"));
        apiResponse.setCustomerName("John Doe");

        when(restTemplate.postForEntity(anyString(), any(), eq(ElectricityBillResponse.class)))
            .thenReturn(ResponseEntity.ok(apiResponse));

        // When
        BillInquiryResult result = provider.inquireBill("ELEC-001", "123456", "John Doe");

        // Then
        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("150.75"), result.getBillAmount());
        assertEquals("John Doe", result.getAccountName());
    }

    @Test
    void shouldHandleApiErrorGracefully() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), eq(ElectricityBillResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // When
        BillInquiryResult result = provider.inquireBill("ELEC-001", "999999", "John Doe");

        // Then
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
}
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class BillerIntegrationTest {

    @Container
    static MockServerContainer mockServer = new MockServerContainer(
        DockerImageName.parse("mockserver/mockserver:latest")
    );

    @Autowired
    private BillerIntegrationProvider provider;

    private MockServerClient mockServerClient;

    @BeforeEach
    void setup() {
        mockServerClient = new MockServerClient(
            mockServer.getHost(),
            mockServer.getServerPort()
        );
    }

    @Test
    void shouldIntegrateWithElectricityAPI() {
        // Setup mock server expectation
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath("/bills/inquiry")
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("""
                        {
                          "customer_name": "John Doe",
                          "current_charges": 150.75,
                          "minimum_payment": 50.00,
                          "payment_due_date": "2025-12-15"
                        }
                        """)
            );

        // Execute
        BillInquiryResult result = provider.inquireBill("ELEC-001", "123456", "John Doe");

        // Verify
        assertTrue(result.isSuccess());
        assertEquals("John Doe", result.getAccountName());
    }
}
```

### Contract Tests (Pact)

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ElectricityAPI", port = "8888")
class ElectricityBillerProviderContractTest {

    @Pact(consumer = "BillPaymentService")
    public RequestResponsePact billInquiryPact(PactDslWithProvider builder) {
        return builder
            .given("Account 123456 exists with unpaid bill")
            .uponReceiving("A bill inquiry request")
            .path("/bills/inquiry")
            .method("POST")
            .body(new PactDslJsonBody()
                .stringValue("customer_account_no", "123456")
            )
            .willRespondWith()
            .status(200)
            .body(new PactDslJsonBody()
                .decimalType("current_charges", 150.75)
                .stringType("customer_name", "John Doe")
            )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "billInquiryPact")
    void testBillInquiry() {
        BillInquiryResult result = provider.inquireBill("ELEC-001", "123456", "John Doe");

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("150.75"), result.getBillAmount());
    }
}
```

---

## Deployment Roadmap

### Phase 1: Development & Testing (Current)
**Status**: ✅ Complete
- Default mock provider implemented
- Service compiles and runs
- End-to-end flows work with mock data
- Ready for development and UAT

### Phase 2: Biller Research (1-2 weeks)
**Tasks**:
- [ ] Document top 10 billers APIs
- [ ] Collect API credentials for sandbox
- [ ] Map field structures
- [ ] Identify common patterns
- [ ] Document error codes

**Deliverable**: Biller API Documentation

### Phase 3: First Production Provider (2-4 weeks)
**Tasks**:
- [ ] Choose pilot biller (e.g., major electricity company)
- [ ] Implement provider class
- [ ] Add circuit breakers and retry logic
- [ ] Write comprehensive tests
- [ ] Test in sandbox environment
- [ ] Security review

**Deliverable**: Production-ready provider for 1 biller

### Phase 4: Monitoring & Observability (1 week)
**Tasks**:
- [ ] Add Prometheus metrics per biller
- [ ] Create Grafana dashboards
- [ ] Setup alerts for API failures
- [ ] Add detailed logging
- [ ] Implement correlation IDs

**Deliverable**: Full observability stack

### Phase 5: Gradual Rollout (2-3 weeks)
**Tasks**:
- [ ] Deploy to staging
- [ ] Test with real accounts (internal team)
- [ ] Soft launch to 100 users
- [ ] Monitor for 1 week
- [ ] Expand to 1,000 users
- [ ] Full production rollout

**Deliverable**: First biller live in production

### Phase 6: Scale to More Billers (Ongoing)
**Tasks**:
- [ ] Implement 2-3 providers per month
- [ ] Refactor common code into base class
- [ ] Build biller provider library
- [ ] Consider third-party aggregators (Plaid, Yodlee)

**Deliverable**: 10+ billers integrated

---

## Common Biller Integration Patterns

### Pattern 1: REST API (Most Common)
**Examples**: Modern utility companies, fintech billers
```java
ResponseEntity<BillResponse> response = restTemplate.postForEntity(
    billerApiUrl + "/bills/inquiry",
    request,
    BillResponse.class
);
```

### Pattern 2: SOAP/XML (Legacy Systems)
**Examples**: Older utility companies, government billers
```java
SoapMessage request = soapClient.createRequest();
request.setBody(billInquiryXml);
SoapMessage response = soapClient.send(request);
```

### Pattern 3: File-based (Batch)
**Examples**: Some credit bureaus, statement processors
```java
// Upload file
ftpClient.upload("/incoming/bill_inquiry_123.csv");
// Poll for response file
File response = ftpClient.download("/outgoing/bill_response_123.csv");
```

### Pattern 4: Message Queue
**Examples**: High-volume enterprise billers
```java
jmsTemplate.convertAndSend("bill.inquiry.queue", inquiryRequest);
BillResponse response = jmsTemplate.receiveAndConvert("bill.response.queue");
```

### Pattern 5: Third-Party Aggregators
**Examples**: Plaid, Yodlee, Finicity
```java
PlaidClient plaidClient = new PlaidClient(apiKey);
BillData[] bills = plaidClient.getBills(accessToken);
```

---

## Security Considerations

### 1. API Key Management
```java
@Configuration
public class BillerSecurityConfig {

    @Bean
    public BillerCredentialService credentialService(
            @Value("${encryption.key}") String encryptionKey) {
        return new BillerCredentialService(encryptionKey);
    }
}
```

### 2. PII Protection
```java
@Aspect
public class PIIMaskingAspect {

    @Around("@annotation(MaskPII)")
    public Object maskSensitiveData(ProceedingJoinPoint pjp) {
        // Mask account numbers in logs
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String accountNumber) {
                args[i] = maskAccountNumber(accountNumber);
            }
        }
        return pjp.proceed(args);
    }
}
```

### 3. Rate Limiting
```java
@RateLimiter(name = "electricityBiller")
public BillInquiryResult inquireBill(...) {
    // Protected by rate limiter
}
```

---

## Troubleshooting Guide

### Issue: Biller API Returns 401 Unauthorized
**Cause**: Expired or invalid API key
**Solution**:
1. Check API key in configuration
2. Verify key hasn't expired
3. Regenerate key from biller portal
4. Update in secrets manager

### Issue: Biller API Returns 429 Too Many Requests
**Cause**: Rate limit exceeded
**Solution**:
1. Implement exponential backoff
2. Add circuit breaker
3. Cache frequent requests
4. Contact biller to increase limits

### Issue: Payment Submitted but Status Unknown
**Cause**: Network timeout during response
**Solution**:
1. Implement idempotency keys
2. Add payment verification job
3. Call verifyPaymentStatus()
4. Reconcile with biller

### Issue: Bill Amounts Don't Match
**Cause**: Currency conversion or field mapping error
**Solution**:
1. Verify currency codes match
2. Check decimal precision (use BigDecimal)
3. Review field mapping documentation
4. Test with known values

---

## Contact & Support

**For Biller API Issues**:
- Check biller's developer portal
- Contact biller's API support team
- Review biller's status page

**For Internal Support**:
- Slack: #bill-payment-integration
- Email: fintech-team@company.com
- On-call: PagerDuty rotation

---

## Appendix: Method Reference

### BillerIntegrationProvider Methods

| Method | Purpose | Returns | Throws |
|--------|---------|---------|--------|
| `establishConnection()` | Link user account to biller | BillerConnectionResult | BillerIntegrationException |
| `inquireBill()` | Fetch current bill details | BillInquiryResult | BillerIntegrationException |
| `submitPayment()` | Submit payment to biller | PaymentSubmissionResult | BillerIntegrationException |
| `fetchBills()` | Import multiple bills | List<BillData> | BillerIntegrationException |
| `supportsDirectPayment()` | Check if direct payment supported | boolean | - |
| `supportsNegotiation()` | Check if negotiation supported | boolean | - |
| `initiateBillNegotiation()` | Start negotiation process | NegotiationResult | BillerIntegrationException |
| `verifyPaymentStatus()` | Check payment status | String | BillerIntegrationException |
| `testConnection()` | Health check | boolean | - |

---

**Last Updated**: November 2025
**Version**: 1.0
**Maintained By**: FinTech Engineering Team
