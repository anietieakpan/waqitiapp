# 🔌 PART 4: API INTEGRATION FIXES
## Timeline: Week 4 (EXTERNAL SYSTEM CONNECTIVITY)
## Priority: CRITICAL - Core Operations Depend on These
## Team Size: 4 Developers

---

## 💳 DAY 1-2: PAYMENT PROCESSOR INTEGRATIONS

### 1. STRIPE INTEGRATION (8 hours)
**File**: `services/payment-service/src/main/java/com/waqiti/payment/integration/stripe/StripePaymentProcessor.java`

#### Task 1.1: Configure Stripe API
**File**: `services/payment-service/src/main/resources/application.yml`
```yaml
stripe:
  api:
    key: ${STRIPE_SECRET_KEY}
    publishable-key: ${STRIPE_PUBLISHABLE_KEY}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET}
    api-version: "2023-10-16"
  endpoints:
    base-url: https://api.stripe.com
    timeout: 30000
  connect:
    enabled: true
    client-id: ${STRIPE_CONNECT_CLIENT_ID}
```

#### Task 1.2: Implement Stripe Payment Service
```java
@Service
@Slf4j
public class StripePaymentProcessor implements PaymentProcessor {
    
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        Stripe.setApiVersion("2023-10-16");
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        try {
            // 1. Create payment intent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmount().multiply(new BigDecimal(100)).longValue())
                .setCurrency(request.getCurrency().toLowerCase())
                .setCustomer(getOrCreateCustomer(request.getUserId()))
                .setPaymentMethod(request.getPaymentMethodId())
                .setConfirm(true)
                .setReturnUrl(buildReturnUrl(request))
                .addPaymentMethodType("card")
                .setMetadata(buildMetadata(request))
                .build();
            
            PaymentIntent intent = PaymentIntent.create(params);
            
            // 2. Handle 3DS if required
            if (intent.getStatus().equals("requires_action")) {
                return PaymentResult.builder()
                    .status(PaymentStatus.REQUIRES_AUTHENTICATION)
                    .authenticationUrl(intent.getNextAction().getRedirectToUrl().getUrl())
                    .paymentIntentId(intent.getId())
                    .build();
            }
            
            // 3. Process result
            return mapStripeResult(intent);
            
        } catch (StripeException e) {
            log.error("Stripe payment failed", e);
            return handleStripeError(e);
        }
    }
    
    @Override
    public RefundResult processRefund(RefundRequest request) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(request.getOriginalPaymentId())
                .setAmount(request.getAmount().multiply(new BigDecimal(100)).longValue())
                .setReason(mapRefundReason(request.getReason()))
                .setMetadata(buildRefundMetadata(request))
                .build();
            
            Refund refund = Refund.create(params);
            return mapRefundResult(refund);
            
        } catch (StripeException e) {
            log.error("Stripe refund failed", e);
            return handleRefundError(e);
        }
    }
}
```

#### Task 1.3: Stripe Webhook Handler
**File**: Create `services/payment-service/src/main/java/com/waqiti/payment/webhook/StripeWebhookController.java`
```java
@RestController
@RequestMapping("/api/webhooks/stripe")
@Slf4j
public class StripeWebhookController {
    
    @Value("${stripe.api.webhook-secret}")
    private String webhookSecret;
    
    @PostMapping
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        
        try {
            // 1. Verify signature
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            
            // 2. Handle event
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentSuccess(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentFailure(event);
                    break;
                case "charge.dispute.created":
                    handleDispute(event);
                    break;
                case "refund.created":
                    handleRefund(event);
                    break;
                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
            
            return ResponseEntity.ok().build();
            
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.status(400).build();
        }
    }
}
```

---

### 2. PAYPAL INTEGRATION (8 hours)
**File**: `services/payment-service/src/main/java/com/waqiti/payment/integration/paypal/PayPalPaymentProcessor.java`

#### Task 2.1: Configure PayPal API
```yaml
paypal:
  api:
    client-id: ${PAYPAL_CLIENT_ID}
    client-secret: ${PAYPAL_CLIENT_SECRET}
    mode: ${PAYPAL_MODE:live} # sandbox or live
    webhook-id: ${PAYPAL_WEBHOOK_ID}
  endpoints:
    base-url: ${PAYPAL_BASE_URL:https://api-m.paypal.com}
    sandbox-url: https://api-m.sandbox.paypal.com
```

#### Task 2.2: Implement PayPal Service
```java
@Service
@Slf4j
public class PayPalPaymentProcessor implements PaymentProcessor {
    
    private APIContext apiContext;
    
    @PostConstruct
    public void init() {
        apiContext = new APIContext(clientId, clientSecret, mode);
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        try {
            // 1. Create payment
            Payment payment = new Payment();
            payment.setIntent("sale");
            payment.setPayer(createPayer(request));
            payment.setTransactions(Arrays.asList(createTransaction(request)));
            payment.setRedirectUrls(createRedirectUrls(request));
            
            // 2. Execute payment
            Payment createdPayment = payment.create(apiContext);
            
            // 3. Get approval URL
            String approvalUrl = createdPayment.getLinks().stream()
                .filter(link -> link.getRel().equals("approval_url"))
                .findFirst()
                .map(Links::getHref)
                .orElseThrow(() -> new PaymentException("No approval URL"));
            
            return PaymentResult.builder()
                .status(PaymentStatus.PENDING_APPROVAL)
                .approvalUrl(approvalUrl)
                .paymentId(createdPayment.getId())
                .build();
                
        } catch (PayPalRESTException e) {
            log.error("PayPal payment failed", e);
            return handlePayPalError(e);
        }
    }
}
```

---

### 3. PAYMENT PROCESSOR FALLBACK (8 hours)

#### Task 3.1: Payment Router Service
**File**: Create `services/payment-service/src/main/java/com/waqiti/payment/service/PaymentRouterService.java`
```java
@Service
@Slf4j
public class PaymentRouterService {
    
    @Autowired
    private List<PaymentProcessor> processors;
    
    @Autowired
    private ProcessorHealthService healthService;
    
    public PaymentResult routePayment(PaymentRequest request) {
        // 1. Select primary processor
        PaymentProcessor primary = selectProcessor(request);
        
        // 2. Try primary
        try {
            if (healthService.isHealthy(primary)) {
                return primary.processPayment(request);
            }
        } catch (Exception e) {
            log.error("Primary processor failed", e);
        }
        
        // 3. Try fallback processors
        for (PaymentProcessor fallback : getFallbackProcessors(primary)) {
            try {
                if (healthService.isHealthy(fallback)) {
                    log.info("Using fallback processor: {}", fallback.getName());
                    return fallback.processPayment(request);
                }
            } catch (Exception e) {
                log.error("Fallback processor {} failed", fallback.getName(), e);
            }
        }
        
        throw new PaymentException("All payment processors unavailable");
    }
}
```

---

## 🏦 DAY 3-4: BANKING INTEGRATIONS

### 4. FINERACT CORE BANKING (12 hours)
**File**: `services/core-banking-service/src/main/java/com/waqiti/corebanking/client/FineractApiClient.java`

#### Task 4.1: Fix Missing Configuration
```yaml
fineract:
  api:
    url: ${FINERACT_API_URL:https://demo.mifos.io/fineract-provider/api/v1}
    username: ${FINERACT_USERNAME}
    password: ${FINERACT_PASSWORD}
    tenant: ${FINERACT_TENANT:default}
  connection:
    timeout: 30000
    max-connections: 50
```

#### Task 4.2: Implement Fineract Client
```java
@Component
@Slf4j
public class FineractApiClient {
    
    @Value("${fineract.api.url}")
    private String apiUrl;
    
    @Value("${fineract.api.username}")
    private String username;
    
    @Value("${fineract.api.password}")
    private String password;
    
    @Value("${fineract.api.tenant}")
    private String tenant;
    
    private RestTemplate restTemplate;
    
    @PostConstruct
    public void init() {
        this.restTemplate = createRestTemplate();
    }
    
    public AccountResponse createAccount(AccountRequest request) {
        HttpHeaders headers = createHeaders();
        HttpEntity<AccountRequest> entity = new HttpEntity<>(request, headers);
        
        return restTemplate.postForObject(
            apiUrl + "/savingsaccounts",
            entity,
            AccountResponse.class
        );
    }
    
    public TransferResponse transferFunds(TransferRequest request) {
        HttpHeaders headers = createHeaders();
        HttpEntity<TransferRequest> entity = new HttpEntity<>(request, headers);
        
        return restTemplate.postForObject(
            apiUrl + "/accounttransfers",
            entity,
            TransferResponse.class
        );
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.set("Fineract-Platform-TenantId", tenant);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
```

---

### 5. ACH PROCESSING (12 hours)
**File**: Create `services/payment-service/src/main/java/com/waqiti/payment/ach/ACHProcessor.java`

#### Task 5.1: NACHA File Generation
```java
@Service
@Slf4j
public class ACHProcessor {
    
    public String generateNACHAFile(List<ACHTransaction> transactions) {
        NACHAFile file = new NACHAFile();
        
        // 1. File header
        file.addFileHeader(FileHeader.builder()
            .immediateDestination(routingNumber)
            .immediateOrigin(companyId)
            .fileCreationDate(LocalDate.now())
            .fileIdModifier("A")
            .build());
        
        // 2. Batch header
        BatchHeader batch = BatchHeader.builder()
            .serviceClassCode(ServiceClassCode.MIXED)
            .companyName(companyName)
            .companyId(companyId)
            .standardEntryClass("PPD")
            .companyEntryDescription("PAYMENT")
            .effectiveEntryDate(LocalDate.now().plusDays(1))
            .build();
        
        file.addBatch(batch);
        
        // 3. Entry details
        for (ACHTransaction tx : transactions) {
            file.addEntry(EntryDetail.builder()
                .transactionCode(getTransactionCode(tx))
                .receivingDFI(tx.getRoutingNumber())
                .checkDigit(calculateCheckDigit(tx.getRoutingNumber()))
                .accountNumber(tx.getAccountNumber())
                .amount(tx.getAmount())
                .individualId(tx.getIndividualId())
                .individualName(tx.getIndividualName())
                .traceNumber(generateTraceNumber())
                .build());
        }
        
        // 4. Batch control
        file.addBatchControl();
        
        // 5. File control
        file.addFileControl();
        
        return file.toString();
    }
}
```

#### Task 5.2: ACH Status Tracking
```java
@Service
public class ACHStatusService {
    
    @Scheduled(cron = "0 0 6,14,22 * * *") // 3 times daily
    public void checkACHStatus() {
        List<ACHTransaction> pending = achRepository.findPendingTransactions();
        
        for (ACHTransaction tx : pending) {
            ACHStatus status = achProvider.getStatus(tx.getTraceNumber());
            
            switch (status) {
                case SETTLED:
                    handleSettlement(tx);
                    break;
                case RETURNED:
                    handleReturn(tx);
                    break;
                case REJECTED:
                    handleRejection(tx);
                    break;
            }
        }
    }
}
```

---

### 6. CHECK PROCESSING (8 hours)
**File**: Fix `services/payment-service/src/main/java/com/waqiti/payment/client/CheckProcessingClient.java`

#### Task 6.1: Configure Check Processor
```yaml
check:
  processor:
    url: ${CHECK_PROCESSOR_URL:https://api.checkprocessor.com/v1}
    api-key: ${CHECK_PROCESSOR_API_KEY}
    merchant-id: ${CHECK_PROCESSOR_MERCHANT_ID}
```

#### Task 6.2: Implement Check Processing
```java
@FeignClient(
    name = "check-processor",
    url = "${check.processor.url}",
    configuration = CheckProcessorConfig.class,
    fallbackFactory = CheckProcessorFallbackFactory.class
)
public interface CheckProcessingClient {
    
    @PostMapping("/deposits")
    CheckDepositResponse depositCheck(
        @RequestHeader("X-API-Key") String apiKey,
        @RequestBody CheckDepositRequest request
    );
    
    @GetMapping("/deposits/{depositId}/status")
    CheckStatus getDepositStatus(
        @RequestHeader("X-API-Key") String apiKey,
        @PathVariable String depositId
    );
    
    @PostMapping("/deposits/{depositId}/images")
    ImageUploadResponse uploadCheckImages(
        @RequestHeader("X-API-Key") String apiKey,
        @PathVariable String depositId,
        @RequestPart("front") MultipartFile front,
        @RequestPart("back") MultipartFile back
    );
}
```

---

## 🔄 DAY 5: CIRCUIT BREAKERS & MONITORING

### 7. CIRCUIT BREAKER CONFIGURATION (8 hours)

#### Task 7.1: Resilience4j Configuration
**File**: `services/payment-service/src/main/resources/application.yml`
```yaml
resilience4j:
  circuitbreaker:
    instances:
      stripe:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        eventConsumerBufferSize: 10
      paypal:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureRateThreshold: 60
        waitDurationInOpenState: 45s
      fineract:
        registerHealthIndicator: true
        slidingWindowSize: 20
        failureRateThreshold: 40
        waitDurationInOpenState: 60s
  
  retry:
    instances:
      payment-api:
        maxAttempts: 3
        waitDuration: 1000
        retryExceptions:
          - java.net.SocketTimeoutException
          - java.io.IOException
  
  timelimiter:
    instances:
      payment-api:
        timeoutDuration: 30s
        cancelRunningFuture: true
```

#### Task 7.2: Fallback Implementations
**File**: Create fallback factories for each Feign client
```java
@Component
@Slf4j
public class CheckProcessorFallbackFactory implements FallbackFactory<CheckProcessingClient> {
    
    @Override
    public CheckProcessingClient create(Throwable cause) {
        log.error("Check processor circuit breaker opened", cause);
        
        return new CheckProcessingClient() {
            @Override
            public CheckDepositResponse depositCheck(String apiKey, CheckDepositRequest request) {
                // Queue for later processing
                queueService.queueCheckDeposit(request);
                
                return CheckDepositResponse.builder()
                    .status("QUEUED")
                    .message("Check queued for processing")
                    .queueId(UUID.randomUUID().toString())
                    .build();
            }
            
            @Override
            public CheckStatus getDepositStatus(String apiKey, String depositId) {
                // Return cached status if available
                return cacheService.getCachedStatus(depositId)
                    .orElse(CheckStatus.UNKNOWN);
            }
        };
    }
}
```

---

### 8. HEALTH CHECKS (8 hours)

#### Task 8.1: External Service Health Indicators
**File**: Create `services/payment-service/src/main/java/com/waqiti/payment/health/ExternalServiceHealthIndicator.java`
```java
@Component
public class StripeHealthIndicator implements HealthIndicator {
    
    @Autowired
    private StripePaymentProcessor stripeProcessor;
    
    @Override
    public Health health() {
        try {
            // Ping Stripe API
            Balance balance = Balance.retrieve();
            
            return Health.up()
                .withDetail("service", "Stripe")
                .withDetail("status", "Connected")
                .withDetail("livemode", balance.getLivemode())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("service", "Stripe")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

#### Task 8.2: Monitoring Dashboard
**File**: Create `services/payment-service/src/main/java/com/waqiti/payment/api/IntegrationMonitorController.java`
```java
@RestController
@RequestMapping("/api/admin/integrations")
public class IntegrationMonitorController {
    
    @Autowired
    private List<HealthIndicator> healthIndicators;
    
    @GetMapping("/health")
    public Map<String, Health> getIntegrationHealth() {
        return healthIndicators.stream()
            .collect(Collectors.toMap(
                indicator -> indicator.getClass().getSimpleName(),
                indicator -> indicator.health()
            ));
    }
    
    @GetMapping("/metrics")
    public IntegrationMetrics getMetrics() {
        return IntegrationMetrics.builder()
            .stripeSuccessRate(getSuccessRate("stripe"))
            .paypalSuccessRate(getSuccessRate("paypal"))
            .achProcessingTime(getAvgProcessingTime("ach"))
            .checkClearanceRate(getClearanceRate("check"))
            .build();
    }
}
```

---

## 📋 VERIFICATION CHECKLIST

### API Configuration
- [ ] All API keys in environment variables
- [ ] All endpoints configured correctly
- [ ] Timeout settings appropriate
- [ ] Retry policies configured

### Integration Testing
- [ ] Stripe payment flow tested
- [ ] PayPal payment flow tested
- [ ] ACH processing tested
- [ ] Check deposit tested
- [ ] Fineract operations tested

### Circuit Breakers
- [ ] All external APIs have circuit breakers
- [ ] Fallback methods implemented
- [ ] Queue mechanism for failed requests
- [ ] Health checks working

### Monitoring
- [ ] All integrations have health checks
- [ ] Metrics being collected
- [ ] Alerts configured
- [ ] Dashboard accessible

---

## 🚀 DELIVERABLES

### Week 4 Deliverables
1. **Working Payment Processors** - Stripe and PayPal integrated
2. **Core Banking Connected** - Fineract API working
3. **ACH Processing** - NACHA file generation and tracking
4. **Check Processing** - Check deposit API integrated
5. **Resilience Patterns** - Circuit breakers and fallbacks

### Documentation
1. **Integration Guide** - How to add new providers
2. **Configuration Guide** - All settings documented
3. **Troubleshooting Guide** - Common issues and fixes
4. **API Documentation** - All endpoints documented

---

## 👥 TEAM ASSIGNMENTS

### Developer 1: Payment Processors
- Primary: Tasks 1.1-1.3 (Stripe)
- Secondary: Tasks 2.1-2.2 (PayPal)

### Developer 2: Banking Integration
- Primary: Tasks 4.1-4.2 (Fineract)
- Secondary: Task 5.1-5.2 (ACH)

### Developer 3: Check & Fallbacks
- Primary: Task 6.1-6.2 (Check processing)
- Secondary: Task 3.1 (Payment router)

### Developer 4: Resilience & Monitoring
- Primary: Tasks 7.1-7.2 (Circuit breakers)
- Secondary: Tasks 8.1-8.2 (Health checks)

---

**Last Updated**: September 10, 2025  
**Next Review**: End of Day 2  
**Integration Team Lead Sign-off**: Required