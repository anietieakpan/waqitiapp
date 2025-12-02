# CARD SERVICE - COMPLETE PRODUCTION IMPLEMENTATION PLAN

**Date:** November 9, 2025
**Status:** Schema Consolidated ✅ | Implementation In Progress ⏳
**Target:** 100% Production-Ready Enterprise Card Platform

---

## IMPLEMENTATION STATUS

### ✅ COMPLETED
1. **Phase 1:** card-processing-service archived
2. **Phase 2:** Database schemas consolidated (V2 migration created)
   - 15 tables total (6 enhanced + 9 new)
   - All foreign keys, indexes, triggers configured
   - Single source of truth established

### ⏳ IN PROGRESS
**Phase 3-12:** Full implementation (detailed below)

---

## PHASE 3: COMPLETE ENTITY LAYER (15 Entities)

### Implementation Order & Details

#### 1. **Card.java** (Master Entity)
```java
Location: src/main/java/com/waqiti/card/entity/Card.java

@Entity
@Table(name = "card")
public class Card extends BaseAuditEntity {
    // Primary identification
    @Id @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String cardId;

    // Card details (60+ fields from consolidated schema)
    @Column(nullable = false)
    private String cardNumberEncrypted;

    @Column(nullable = false, length = 4)
    private String cardNumberLastFour;

    @Enumerated(EnumType.STRING)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    private CardBrand cardBrand;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private CardProduct product;

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL)
    private List<CardTransaction> transactions;

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL)
    private List<CardAuthorization> authorizations;

    // Financial fields
    @Column(precision = 18, scale = 2)
    private BigDecimal creditLimit;

    @Column(precision = 18, scale = 2)
    private BigDecimal availableCredit;

    // All other fields from consolidated schema
    // ... (implement all 60+ fields)
}

**Enums to create:**
- CardType (DEBIT, CREDIT, PREPAID, VIRTUAL)
- CardBrand (VISA, MASTERCARD, AMEX, DISCOVER)
- CardStatus (ACTIVE, BLOCKED, EXPIRED, CANCELLED)
```

#### 2. **CardProduct.java**
```java
@Entity
@Table(name = "card_product")
public class CardProduct extends BaseAuditEntity {
    @Id @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String productId;

    // All fields from schema
    // Relationships to cards
    @OneToMany(mappedBy = "product")
    private List<Card> cards;
}
```

#### 3-15. **Remaining Entities**
```
CardTransaction.java       - Transaction master records
CardAuthorization.java     - Authorization records
CardSettlement.java        - Settlement records
CardDispute.java           - Dispute management
CardFraudRule.java         - Fraud detection rules
CardFraudAlert.java        - Fraud alerts
CardVelocityLimit.java     - Velocity limits
CardPinManagement.java     - PIN management
CardTokenManagement.java   - Tokenization
CardLimit.java             - Card limits
CardStatement.java         - Statement records
CardReplacementRequest.java- Replacement requests
CardProcessingAnalytics.java- Analytics
CardProcessingStatistics.java- Statistics
```

**Each entity must include:**
- JPA annotations (@Entity, @Table, @Column)
- Proper relationships (@OneToMany, @ManyToOne, @ManyToMany)
- Fetch strategies (LAZY for performance)
- Cascade types (appropriate for each relationship)
- Validation annotations (@NotNull, @Size, @Pattern)
- Lombok annotations (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)
- Audit fields (extends BaseAuditEntity)
- Indexes (via @Table(indexes = {...}))

---

## PHASE 4: COMPLETE REPOSITORY LAYER (15 Repositories)

### Spring Data JPA Repositories

#### 1. **CardRepository.java**
```java
Location: src/main/java/com/waqiti/card/repository/CardRepository.java

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByCardId(String cardId);

    Optional<Card> findByCardIdAndUserId(String cardId, UUID userId);

    List<Card> findByUserId(UUID userId);

    List<Card> findByUserIdAndCardStatus(UUID userId, CardStatus status);

    Optional<Card> findByPanToken(String panToken);

    @Query("SELECT c FROM Card c WHERE c.userId = :userId AND c.cardStatus = 'ACTIVE'")
    List<Card> findActiveCardsByUserId(@Param("userId") UUID userId);

    @Query("SELECT c FROM Card c WHERE c.expiryDate < :date AND c.cardStatus = 'ACTIVE'")
    List<Card> findExpiringCards(@Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE Card c SET c.cardStatus = 'BLOCKED' WHERE c.cardId = :cardId")
    int blockCard(@Param("cardId") String cardId);

    @Query(value = "SELECT * FROM card WHERE user_id = :userId AND card_status = :status " +
           "ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Card> findRecentCardsByUserAndStatus(
        @Param("userId") UUID userId,
        @Param("status") String status,
        @Param("limit") int limit
    );

    boolean existsByCardIdAndUserId(String cardId, UUID userId);

    long countByUserIdAndCardStatus(UUID userId, CardStatus status);

    // Add 20+ more custom query methods
}
```

#### 2-15. **Remaining Repositories**
```
CardProductRepository.java
CardTransactionRepository.java      - Complex queries for transactions
CardAuthorizationRepository.java    - Authorization lookups
CardSettlementRepository.java       - Settlement queries
CardDisputeRepository.java          - Dispute management
CardFraudRuleRepository.java        - Fraud rule queries
CardFraudAlertRepository.java       - Alert queries
CardVelocityLimitRepository.java    - Velocity checks
CardPinManagementRepository.java    - PIN lookups
CardTokenManagementRepository.java  - Token queries
CardLimitRepository.java            - Limit checks
CardStatementRepository.java        - Statement queries
CardReplacementRequestRepository.java
CardProcessingAnalyticsRepository.java
CardProcessingStatisticsRepository.java
```

**Each repository must include:**
- Standard CRUD operations (from JpaRepository)
- Custom finder methods (10-20 per repository)
- @Query annotations for complex queries
- Native SQL queries where needed (performance)
- @Modifying for update/delete operations
- @Lock for pessimistic locking (where needed)
- Pagination support (Pageable parameters)
- Projections for performance (DTO projections)

---

## PHASE 5: COMPLETE DTO LAYER (30+ DTOs)

### Request DTOs

```
CardIssuanceRequest.java
CardActivationRequest.java
CardBlockRequest.java
CardUnblockRequest.java
CardPinSetRequest.java
CardPinChangeRequest.java
CardLimitUpdateRequest.java
CardReplacementRequest.java
TransactionAuthorizationRequest.java
TransactionSettlementRequest.java
DisputeCreationRequest.java
FraudRuleCreationRequest.java
... (20+ request DTOs)
```

**Example: CardIssuanceRequest.java**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardIssuanceRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Product ID is required")
    private String productId;

    @NotNull(message = "Card type is required")
    private CardType cardType;

    @NotNull(message = "Credit limit is required")
    @DecimalMin(value = "0.00", message = "Credit limit must be positive")
    private BigDecimal creditLimit;

    @Pattern(regexp = "^(STANDARD|EXPRESS)$", message = "Invalid delivery method")
    private String deliveryMethod;

    @Valid
    private DeliveryAddress deliveryAddress;

    // Validation groups for different scenarios
    // Builder pattern for flexibility
    // Immutable after creation
}
```

### Response DTOs

```
CardResponse.java
CardDetailsResponse.java
CardListResponse.java
TransactionResponse.java
TransactionListResponse.java
AuthorizationResponse.java
SettlementResponse.java
DisputeResponse.java
FraudAlertResponse.java
StatementResponse.java
... (20+ response DTOs)
```

**Example: CardResponse.java**
```java
@Data
@Builder
public class CardResponse {
    private String cardId;
    private String cardNumberMasked;  // ****-****-****-1234
    private String cardType;
    private String cardBrand;
    private String cardStatus;
    private LocalDate expiryDate;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private BigDecimal outstandingBalance;
    private Boolean isContactless;
    private Boolean isVirtual;
    private LocalDateTime activatedAt;
    private LocalDateTime createdAt;

    // NEVER include sensitive data (full PAN, CVV, PIN)
    // Always mask card numbers
    // Include only necessary fields
}
```

**All DTOs must include:**
- Jakarta Validation annotations (@NotNull, @Size, @Pattern, @Email, etc.)
- Proper JSON annotations (@JsonProperty, @JsonIgnore)
- Custom validators where needed
- Swagger/OpenAPI annotations (@Schema)
- Lombok (@Data, @Builder)
- Immutability where appropriate

---

## PHASE 6: COMPLETE SERVICE LAYER (12 Services)

### Service Implementation Order

#### 1. **CardIssuanceService.java**
```java
Location: src/main/java/com/waqiti/card/service/CardIssuanceService.java

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CardIssuanceService {

    private final CardRepository cardRepository;
    private final CardProductRepository productRepository;
    private final CardEventPublisher eventPublisher;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    /**
     * Issues a new card to a user
     *
     * @param request Card issuance request
     * @param userId Requesting user ID
     * @return Issued card details
     * @throws CardIssuanceException if issuance fails
     */
    public CardResponse issueCard(CardIssuanceRequest request, UUID userId) {
        log.info("Issuing new card for user: {}, product: {}", userId, request.getProductId());

        // 1. Validate request
        validateIssuanceRequest(request, userId);

        // 2. Check user eligibility
        validateUserEligibility(userId);

        // 3. Get card product
        CardProduct product = productRepository.findByProductId(request.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        // 4. Generate card number (PCI-compliant)
        String cardNumber = generateCardNumber(product.getBinRange());
        String encryptedPan = encryptionService.encrypt(cardNumber);
        String panToken = tokenizationService.tokenize(cardNumber);

        // 5. Generate CVV (encrypted)
        String cvv = generateCVV();
        String encryptedCvv = encryptionService.encrypt(cvv);

        // 6. Calculate expiry date
        LocalDate expiryDate = calculateExpiryDate(product);

        // 7. Create card entity
        Card card = Card.builder()
            .cardId(generateCardId())
            .userId(userId)
            .accountId(request.getAccountId())
            .product(product)
            .cardType(request.getCardType())
            .cardBrand(product.getCardNetwork())
            .cardNumberEncrypted(encryptedPan)
            .cardNumberLastFour(cardNumber.substring(cardNumber.length() - 4))
            .panToken(panToken)
            .cvvEncrypted(encryptedCvv)
            .expiryDate(expiryDate)
            .creditLimit(request.getCreditLimit())
            .availableCredit(request.getCreditLimit())
            .cardStatus(CardStatus.PENDING_ACTIVATION)
            .isContactless(true)
            .isVirtual(request.getCardType() == CardType.VIRTUAL)
            .deliveryAddress(request.getDeliveryAddress())
            .deliveryStatus(DeliveryStatus.PENDING)
            .build();

        // 8. Save card
        card = cardRepository.save(card);

        // 9. Publish card issued event
        eventPublisher.publishCardIssuance(
            card.getCardId(),
            userId.toString(),
            card.getAccountId().toString(),
            card.getCardType().name(),
            card.getCreditLimit(),
            request.getDeliveryMethod(),
            expiryDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        // 10. Create default limits
        createDefaultLimits(card);

        // 11. Audit log
        auditService.auditCardEvent(
            "CARD_ISSUED",
            userId.toString(),
            "Card issued: " + card.getCardId(),
            Map.of("cardId", card.getCardId(), "productId", product.getProductId())
        );

        // 12. Return response
        log.info("Card issued successfully: {} for user: {}", card.getCardId(), userId);
        return mapToResponse(card);
    }

    // Add 20+ more methods:
    // - activateCard()
    // - validateIssuanceRequest()
    // - validateUserEligibility()
    // - generateCardNumber()
    // - generateCVV()
    // - calculateExpiryDate()
    // - createDefaultLimits()
    // - etc.
}
```

#### 2-12. **Remaining Services**

```java
CardLifecycleService.java          - Block, unblock, replace, cancel
CardTransactionService.java        - Transaction processing (CRITICAL!)
CardAuthorizationService.java      - Authorization logic (CRITICAL!)
CardSettlementService.java         - Settlement processing
CardDisputeService.java            - Dispute management
CardFraudDetectionService.java     - Fraud detection (CRITICAL!)
CardNotificationService.java       - User notifications
CardPinService.java                - PIN management
CardLimitService.java              - Limit management
CardStatementService.java          - Statement generation
CardReplacementService.java        - Card replacement
CardTokenizationService.java       - Tokenization
```

**Each service must include:**
- Comprehensive business logic (100-500 lines per service)
- Transaction management (@Transactional with proper isolation)
- Error handling (try-catch with specific exceptions)
- Validation (business rules validation)
- Logging (debug, info, warn, error)
- Audit trails (all financial operations)
- Event publishing (Kafka events for all state changes)
- Metrics collection (Micrometer counters/timers)
- Circuit breakers (@CircuitBreaker for external calls)
- Retry logic (@Retryable where appropriate)
- Security checks (authorization, PCI compliance)
- Idempotency (for critical operations)

---

## PHASE 7: COMPLETE CONTROLLER LAYER (REST API)

### API Endpoints (15-20 Controllers)

#### **CardController.java**
```java
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CardController {

    private final CardIssuanceService issuanceService;
    private final CardLifecycleService lifecycleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue new card", description = "Issues a new card to the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Card issued successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Card already exists")
    })
    public ResponseEntity<ApiResponse<CardResponse>> issueCard(
            @Valid @RequestBody CardIssuanceRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        log.info("POST /api/v1/cards - User: {}", user.getUserId());

        CardResponse response = issuanceService.issueCard(request, user.getUserId());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Card issued successfully"));
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get card details")
    public ResponseEntity<ApiResponse<CardResponse>> getCard(
            @PathVariable @NotBlank String cardId,
            @AuthenticationPrincipal UserPrincipal user) {

        CardResponse response = lifecycleService.getCardDetails(cardId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List user's cards")
    public ResponseEntity<ApiResponse<List<CardResponse>>> listCards(
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal user) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CardResponse> cards = lifecycleService.getUserCards(user.getUserId(), status, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(cards.getContent(), cards.getTotalElements()));
    }

    @PatchMapping("/{cardId}/activate")
    @Operation(summary = "Activate card")
    public ResponseEntity<ApiResponse<CardResponse>> activateCard(
            @PathVariable String cardId,
            @Valid @RequestBody CardActivationRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        CardResponse response = lifecycleService.activateCard(cardId, request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Card activated successfully"));
    }

    @PatchMapping("/{cardId}/block")
    @Operation(summary = "Block card")
    public ResponseEntity<ApiResponse<CardResponse>> blockCard(
            @PathVariable String cardId,
            @Valid @RequestBody CardBlockRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        CardResponse response = lifecycleService.blockCard(cardId, request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Card blocked successfully"));
    }

    // Add 15+ more endpoints
    // - PATCH /{cardId}/unblock
    // - DELETE /{cardId}
    // - POST /{cardId}/replacement
    // - POST /{cardId}/pin
    // - PATCH /{cardId}/limits
    // - GET /{cardId}/balance
    // - etc.
}
```

#### **Additional Controllers**
```
CardTransactionController.java     - Transaction APIs
CardAuthorizationController.java   - Authorization endpoints
CardDisputeController.java         - Dispute management
CardStatementController.java       - Statement APIs
CardLimitController.java           - Limit management
AdminCardController.java           - Admin operations
... (10+ more controllers)
```

**Each controller must include:**
- OpenAPI/Swagger annotations
- Request validation (@Valid, @Validated)
- Proper HTTP status codes
- Security annotations (@PreAuthorize)
- Rate limiting (if needed)
- CORS configuration
- Exception handlers (@ExceptionHandler)
- Response wrapping (ApiResponse)
- Logging (request/response logging)
- Pagination support (where applicable)

---

## PHASE 8: FIX ALL BROKEN KAFKA CONSUMERS (32 Consumers)

### Implementation Strategy

All 32 Kafka consumers need these services implemented:

**Required Services (Currently Missing):**
```java
CardIssuanceService        ✅ Implemented in Phase 6
CardLifecycleService       ✅ Implemented in Phase 6
CardTransactionService     ✅ Implemented in Phase 6
CardAuthorizationService   ✅ Implemented in Phase 6
CardNotificationService    ✅ Implemented in Phase 6
CardFraudDetectionService  ✅ Implemented in Phase 6
```

### Consumer Fix Pattern

**For each consumer (e.g., CardActivationEventConsumer.java):**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CardActivationEventConsumer {

    private final CardLifecycleService cardLifecycleService;  // NOW EXISTS!
    private final CardEventPublisher eventPublisher;
    private final AuditService auditService;

    @KafkaListener(
        topics = "card-activation-events",
        groupId = "card-service-activation-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCardActivationEvent(
            @Payload String eventJson,
            Acknowledgment acknowledgment) {

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String cardId = (String) event.get("cardId");
            UUID userId = UUID.fromString((String) event.get("userId"));
            String activationCode = (String) event.get("activationCode");

            // Call actual service (no longer throws "bean not found"!)
            cardLifecycleService.processActivationEvent(cardId, userId, activationCode);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Card activation event processing failed", e);
            throw new RuntimeException("Activation failed", e);
        }
    }
}
```

**Apply to all 32 consumers:**
```
✅ Card3DSecureEventConsumer
✅ CardActivationEventConsumer
✅ CardAuthorizationConsumer
✅ CardControlsEventsConsumer
✅ CardLifecycleEventsConsumer
✅ CardLimitAdjustmentEventsConsumer
✅ CardReplacementEventsConsumer
✅ CardShippingEventsConsumer
✅ CardTransactionEventConsumer (already complete!)
✅ CardUpgradeEventsConsumer
✅ PinChangeEventsConsumer
✅ VirtualCardEventsConsumer
... + 20 more DLQ handlers
```

---

## PHASE 9: EXCEPTION HANDLING FRAMEWORK

### Create Exception Hierarchy

```java
// Base exception
public class CardServiceException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
}

// Specific exceptions
public class CardNotFoundException extends CardServiceException {}
public class CardAlreadyActivatedException extends CardServiceException {}
public class InsufficientCreditException extends CardServiceException {}
public class CardBlockedException extends CardServiceException {}
public class FraudDetectedException extends CardServiceException {}
public class InvalidPinException extends CardServiceException {}
public class AuthorizationFailedException extends CardServiceException {}
... (20+ domain-specific exceptions)
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class CardServiceExceptionHandler {

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCardNotFound(CardNotFoundException ex) {
        log.warn("Card not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse.builder()
                .errorCode("CARD_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Handle all 20+ exception types
    // Include validation exceptions
    // Include constraint violations
    // Include method argument validation
}
```

---

## PHASE 10: SECURITY CONFIGURATION

### Create SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class CardSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Use JWT
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/cards/**").authenticated()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }

    // Add JWT configuration
    // Add method security
    // Add PCI-DSS compliant encryption
}
```

### PCI-DSS Compliance

```java
@Service
public class EncryptionService {

    /**
     * Encrypts PAN (Primary Account Number) using AES-256
     * PCI-DSS requirement: PAN must be encrypted at rest
     */
    public String encryptPAN(String pan) {
        // AES-256-GCM encryption
        // Key management via Vault
        // IV generation per encryption
    }

    /**
     * Encrypts CVV
     * PCI-DSS requirement: CVV must never be stored unencrypted
     */
    public String encryptCVV(String cvv) {
        // AES-256 encryption
        // Different key from PAN
    }

    // PIN encryption (different key again)
    // Key rotation support
    // HSM integration (for production)
}
```

---

## PHASE 11: COMPLETE TEST SUITE

### Unit Tests (80%+ Coverage Target)

```java
@SpringBootTest
@Transactional
class CardIssuanceServiceTest {

    @Autowired
    private CardIssuanceService service;

    @MockBean
    private CardRepository cardRepository;

    @Test
    @DisplayName("Should issue card successfully for valid request")
    void testIssueCard_Success() {
        // Given
        CardIssuanceRequest request = CardIssuanceRequest.builder()
            .userId(UUID.randomUUID())
            .productId("PRODUCT-001")
            .cardType(CardType.CREDIT)
            .creditLimit(new BigDecimal("5000.00"))
            .build();

        // When
        CardResponse response = service.issueCard(request, request.getUserId());

        // Then
        assertNotNull(response.getCardId());
        assertEquals(CardStatus.PENDING_ACTIVATION, response.getCardStatus());
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    @DisplayName("Should throw exception when user not eligible")
    void testIssueCard_UserNotEligible() {
        // Test negative scenarios
    }

    // Add 50+ test cases per service
}
```

### Integration Tests

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestDatabase
@Testcontainers
class CardControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testFullCardIssuanceFlow() {
        // Test complete end-to-end flow
        // Issue card -> Activate -> Make transaction -> Check balance
    }
}
```

### Contract Tests (Kafka)

```java
@SpringBootTest
@EmbeddedKafka
class CardEventPublisherContractTest {

    @Test
    void testCardIssuanceEventContract() {
        // Verify published events match expected schema
        // Ensure consumers can process events
    }
}
```

---

## PHASE 12: CONFIGURATION & DOCUMENTATION

### application.yml

```yaml
spring:
  application:
    name: card-service

  datasource:
    url: jdbc:postgresql://localhost:5432/card_service
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true

  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    consumer:
      group-id: card-service
      auto-offset-reset: earliest
      enable-auto-commit: false
    producer:
      acks: all
      retries: 3

# Security
security:
  jwt:
    public-key: ${JWT_PUBLIC_KEY}
  encryption:
    algorithm: AES-256-GCM
    key-source: vault

# PCI-DSS
pci:
  data-retention-days: 90
  pan-masking: true
  cvv-storage: false

# Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### API Documentation

Create comprehensive OpenAPI spec and README.

---

## IMPLEMENTATION TIMELINE

| Phase | Description | Effort | Status |
|-------|-------------|--------|--------|
| 1 | Archive card-processing-service | 30min | ✅ DONE |
| 2 | Consolidate schemas | 2hrs | ✅ DONE |
| 3 | Entity layer (15 entities) | 12hrs | ⏳ TODO |
| 4 | Repository layer (15 repos) | 8hrs | ⏳ TODO |
| 5 | DTO layer (30+ DTOs) | 10hrs | ⏳ TODO |
| 6 | Service layer (12 services) | 40hrs | ⏳ TODO |
| 7 | Controller layer (REST API) | 16hrs | ⏳ TODO |
| 8 | Fix Kafka consumers (32) | 12hrs | ⏳ TODO |
| 9 | Exception framework | 4hrs | ⏳ TODO |
| 10 | Security config | 6hrs | ⏳ TODO |
| 11 | Test suite | 24hrs | ⏳ TODO |
| 12 | Config & docs | 4hrs | ⏳ TODO |
| **TOTAL** | **Complete Implementation** | **138 hours** | **1.4% done** |

**Estimated Timeline:** 3.5 weeks (1 senior developer, full-time)

---

## PRODUCTION READINESS CHECKLIST

### Code Quality
- [ ] All entities implemented with proper JPA annotations
- [ ] All repositories with custom queries
- [ ] All DTOs with validation
- [ ] All services with business logic
- [ ] All controllers with proper API design
- [ ] All Kafka consumers fixed and working
- [ ] Exception handling complete
- [ ] Security fully configured
- [ ] 80%+ test coverage

### Database
- [ ] V2 migration applied successfully
- [ ] All indexes created
- [ ] All foreign keys configured
- [ ] Update triggers working
- [ ] Performance tested (1M+ transactions)

### Security
- [ ] JWT authentication working
- [ ] PAN encryption (AES-256)
- [ ] CVV never stored unencrypted
- [ ] PIN hashing (bcrypt/PBKDF2)
- [ ] Vault integration for keys
- [ ] PCI-DSS compliance verified
- [ ] Rate limiting configured
- [ ] CORS properly configured

### Operations
- [ ] Health checks configured
- [ ] Metrics exported (Prometheus)
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Logging properly configured
- [ ] Alerts defined
- [ ] Runbook created
- [ ] DR plan documented

### Documentation
- [ ] OpenAPI spec complete
- [ ] README with setup instructions
- [ ] Architecture diagrams
- [ ] API examples
- [ ] Troubleshooting guide

---

## NEXT STEPS

1. **Start with Phase 3:** Create entity layer
2. **Follow order:** Entities → Repositories → DTOs → Services → Controllers
3. **Test continuously:** Write tests as you implement
4. **Review frequently:** Code reviews after each phase
5. **Deploy incrementally:** Deploy to dev after each major phase

---

**Document Status:** IMPLEMENTATION BLUEPRINT ✅
**Ready for Development:** YES
**Estimated Completion:** 3.5 weeks (138 person-hours)
**Production Ready Target:** 100%
