# FRAUD DETECTION SERVICE - COMPLETE IMPLEMENTATION ARCHITECTURE

**Document Version:** 1.0
**Last Updated:** October 16, 2025
**Status:** Production Implementation Guide
**Scope:** Enterprise-scale, industrial-grade fraud detection system

---

## I. COMPLETED IMPLEMENTATIONS ✅

### A. Customer Profile Management (100% Complete)

**Files Created:**
1. `service/CustomerProfileService.java` (236 lines)
2. `entity/CustomerProfile.java` (368 lines)
3. `repository/CustomerProfileRepository.java` (57 lines)

**Production Features:**
- Thread-safe profile updates with optimistic locking
- Exponential moving average risk scoring (α = 0.3)
- Behavioral pattern tracking (transaction hours, days, velocity)
- Location history management (last 20 countries)
- Device fingerprint tracking (last 10 devices)
- Enhanced Due Diligence (EDD) determination
- Account age calculation and risk profiling
- Cache-enabled queries for performance
- Graceful error handling with non-blocking failures

### B. Merchant Profile Management (100% Complete)

**Files Created:**
1. `service/MerchantProfileService.java` (332 lines)

**Production Features:**
- Chargeback rate monitoring and alerting
- Refund rate tracking with thresholds
- Fraud transaction counting
- Merchant Category Code (MCC) risk assessment
- Transaction statistics and velocity analysis
- Enhanced monitoring determination
- Real-time rate calculations
- New merchant identification
- Risk level assessment (LOW/MEDIUM/HIGH)

---

## II. REMAINING IMPLEMENTATIONS (Priority Order)

### PHASE 1: CRITICAL SERVICE DEPENDENCIES (Immediate)

#### 1. Device Profile Service

**File:** `service/DeviceProfileService.java`

**Required Features:**
```java
@Service
public class DeviceProfileService {

    /**
     * Track device fingerprint and behavioral patterns
     */
    public void updateDeviceProfile(
        String deviceFingerprint,
        FraudCheckRequest request,
        double riskScore
    );

    /**
     * Detect device fingerprint anomalies
     */
    public DeviceRiskAssessment getDeviceRiskAssessment(String deviceFingerprint);

    /**
     * Identify device sharing patterns (multiple users, one device)
     */
    public boolean detectDeviceSharing(String deviceFingerprint);

    /**
     * Identify device farms (automated fraud)
     */
    public boolean detectDeviceFarm(String deviceFingerprint);

    /**
     * Track device location changes
     */
    public boolean detectImpossibleTravel(
        String deviceFingerprint,
        String currentLocation
    );
}
```

**Entity Schema:**
```java
@Entity
@Table(name = "device_profiles")
public class DeviceProfile {
    @Id @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(unique = true, nullable = false)
    private String deviceFingerprint;

    private Long totalTransactions;
    private Set<UUID> associatedUserIds; // JSONB in PostgreSQL
    private String lastKnownLocation;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private Double averageRiskScore;
    private String currentRiskLevel;
    private Integer fraudCount;
}
```

#### 2. Location Profile Service

**File:** `service/LocationProfileService.java`

**Required Features:**
```java
@Service
public class LocationProfileService {

    /**
     * Track transaction locations and patterns
     */
    public void updateLocationProfile(
        String countryCode,
        String city,
        String ipAddress,
        FraudCheckRequest request
    );

    /**
     * Assess location-based risk
     */
    public LocationRiskAssessment getLocationRisk(
        String countryCode,
        String ipAddress
    );

    /**
     * Detect VPN/Proxy usage
     */
    public boolean detectVPNProxy(String ipAddress);

    /**
     * Detect geolocation spoofing
     */
    public boolean detectLocationSpoofing(
        String declaredLocation,
        String ipLocation
    );

    /**
     * Check for high-risk jurisdictions
     */
    public boolean isHighRiskJurisdiction(String countryCode);
}
```

**Integration:**
- MaxMind GeoIP2 database
- IP reputation APIs
- VPN/Proxy detection services

#### 3. Velocity Profile Service

**File:** `service/VelocityProfileService.java`

**Required Features:**
```java
@Service
public class VelocityProfileService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Check transaction velocity (count-based)
     */
    public VelocityCheckResult checkTransactionVelocity(
        UUID userId,
        TimeWindow window
    );

    /**
     * Check amount velocity (volume-based)
     */
    public VelocityCheckResult checkAmountVelocity(
        UUID userId,
        BigDecimal amount,
        TimeWindow window
    );

    /**
     * Check recipient velocity (new recipients)
     */
    public VelocityCheckResult checkRecipientVelocity(
        UUID userId,
        UUID recipientId
    );

    /**
     * Update velocity counters (Redis sliding window)
     */
    public void updateVelocityCounters(
        UUID userId,
        BigDecimal amount,
        UUID recipientId
    );
}
```

**Implementation:**
- Redis sorted sets for sliding windows
- Configurable thresholds per user tier
- Real-time velocity calculation
- Automatic expiry and cleanup

---

### PHASE 2: ML MODEL INFRASTRUCTURE (Critical)

#### 1. ML Model Loader Framework

**File:** `ml/MLModelLoader.java`

```java
@Component
public class MLModelLoader {

    /**
     * Load XGBoost model from ONNX format
     */
    public XGBoostModel loadXGBoostModel(String modelPath) throws IOException;

    /**
     * Load Random Forest ensemble
     */
    public RandomForestModel loadRandomForestModel(String modelPath) throws IOException;

    /**
     * Load TensorFlow SavedModel
     */
    public TensorFlowModel loadTensorFlowModel(String modelPath) throws IOException;

    /**
     * Model versioning and A/B testing
     */
    public ModelVersion registerModelVersion(
        String modelName,
        String version,
        byte[] modelBytes
    );

    /**
     * Model performance tracking
     */
    public void recordModelPrediction(
        String modelName,
        double prediction,
        boolean actualFraud
    );
}
```

#### 2. XGBoost Fraud Model

**File:** `ml/XGBoostFraudModel.java`

```java
@Component
public class XGBoostFraudModel implements FraudMLModel {

    private OrtSession session; // ONNX Runtime

    @PostConstruct
    public void initialize() {
        // Load XGBoost model exported to ONNX
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath, options);
    }

    @Override
    public double predict(Map<String, Object> features) {
        // Feature extraction
        float[] featureVector = extractFeatures(features);

        // ONNX inference
        OnnxTensor tensor = OnnxTensor.createTensor(env, featureVector);
        OrtSession.Result result = session.run(inputs);

        // Extract fraud probability
        float[][] predictions = result.get(0).getValue();
        return predictions[0][1]; // Probability of fraud
    }
}
```

#### 3. TensorFlow Fraud Model

**File:** `ml/TensorFlowFraudModel.java`

```java
@Component
public class TensorFlowFraudModel implements FraudMLModel {

    private SavedModelBundle model;

    @PostConstruct
    public void initialize() {
        this.model = SavedModelBundle.load(
            modelPath,
            "serve"
        );
    }

    @Override
    public double predict(Map<String, Object> features) {
        // Create TensorFlow tensors
        Tensor<?> inputTensor = createInputTensor(features);

        // Run inference
        Session session = model.session();
        List<Tensor<?>> outputs = session.runner()
            .feed("input_layer", inputTensor)
            .fetch("fraud_probability")
            .run();

        // Extract prediction
        float[][] predictions = outputs.get(0).copyTo(new float[1][1]);
        return predictions[0][0];
    }
}
```

#### 4. Model Ensemble with Fallback Chain

**File:** `ml/FraudMLEnsemble.java`

```java
@Service
public class FraudMLEnsemble {

    private final XGBoostFraudModel xgboostModel;
    private final RandomForestModel randomForestModel;
    private final TensorFlowFraudModel tensorFlowModel;
    private final RuleBasedFallbackModel fallbackModel;

    /**
     * Ensemble prediction with fallback chain
     */
    public FraudPredictionResult predict(Map<String, Object> features) {

        // Try primary model (XGBoost)
        try {
            double xgboostScore = xgboostModel.predict(features);
            if (isValidPrediction(xgboostScore)) {
                return buildResult(xgboostScore, "XGBOOST", 0.95);
            }
        } catch (Exception e) {
            log.warn("XGBoost model failed", e);
        }

        // Try secondary model (Random Forest)
        try {
            double rfScore = randomForestModel.predict(features);
            if (isValidPrediction(rfScore)) {
                return buildResult(rfScore, "RANDOM_FOREST", 0.90);
            }
        } catch (Exception e) {
            log.warn("Random Forest model failed", e);
        }

        // Try tertiary model (TensorFlow)
        try {
            double tfScore = tensorFlowModel.predict(features);
            if (isValidPrediction(tfScore)) {
                return buildResult(tfScore, "TENSORFLOW", 0.85);
            }
        } catch (Exception e) {
            log.error("All ML models failed, using fallback", e);
        }

        // Fallback to rule-based scoring (FAIL SECURE)
        double fallbackScore = fallbackModel.predict(features);
        alertService.sendCriticalAlert("ML_SYSTEM_FAILURE");
        return buildResult(fallbackScore, "FALLBACK", 0.50);
    }
}
```

---

### PHASE 3: GRAPH-BASED FRAUD DETECTION (Critical - Replaces Placeholders)

#### 1. Neo4j Transaction Graph Service

**File:** `graph/Neo4jTransactionGraphService.java`

```java
@Service
public class Neo4jTransactionGraphService {

    private final Driver neo4jDriver;

    /**
     * Create transaction edge in graph
     */
    public void createTransactionEdge(
        UUID senderId,
        UUID recipientId,
        BigDecimal amount,
        LocalDateTime timestamp
    ) {
        String cypher = """
            MERGE (sender:User {id: $senderId})
            MERGE (recipient:User {id: $recipientId})
            CREATE (sender)-[tx:TRANSACTION {
                amount: $amount,
                timestamp: $timestamp,
                id: randomUUID()
            }]->(recipient)
            """;

        try (Session session = neo4jDriver.session()) {
            session.run(cypher, parameters);
        }
    }

    /**
     * Calculate PageRank centrality
     */
    public double calculatePageRank(UUID userId) {
        String cypher = """
            CALL gds.pageRank.stream('transaction-graph')
            YIELD nodeId, score
            WHERE nodeId = $userId
            RETURN score
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, parameters);
            return result.single().get("score").asDouble();
        }
    }

    /**
     * Detect fraud rings using community detection
     */
    public Set<UUID> detectFraudRing(UUID suspiciousUserId) {
        String cypher = """
            CALL gds.louvain.stream('transaction-graph')
            YIELD nodeId, communityId
            WITH communityId, collect(nodeId) as community
            WHERE $userId IN community
            RETURN community
            """;

        // Returns all users in same community
    }
}
```

#### 2. Fraud Ring Detection Service

**File:** `service/FraudRingDetectionService.java`

```java
@Service
public class FraudRingDetectionService {

    private final Neo4jTransactionGraphService graphService;

    /**
     * Detect if user is part of a fraud ring
     */
    public FraudRingAnalysis analyzeFraudRing(UUID userId) {

        // Calculate network metrics
        double centrality = graphService.calculatePageRank(userId);
        double betweenness = graphService.calculateBetweenness(userId);
        double clustering = graphService.calculateClusteringCoefficient(userId);

        // Detect community
        Set<UUID> community = graphService.detectCommunity(userId);

        // Count fraudsters in community
        long fraudsterCount = community.stream()
            .filter(this::isKnownFraudster)
            .count();

        // Calculate fraud ring score
        double fraudRingScore = calculateFraudRingScore(
            centrality,
            betweenness,
            clustering,
            fraudsterCount,
            community.size()
        );

        return FraudRingAnalysis.builder()
            .userId(userId)
            .fraudRingScore(fraudRingScore)
            .communitySize(community.size())
            .fraudsterCount(fraudsterCount)
            .centrality(centrality)
            .isSuspicious(fraudRingScore > 0.7)
            .build();
    }
}
```

#### 3. Replace Placeholder Methods in FraudDetectionMLService

**File:** `ml/FraudDetectionMLService.java` (modifications)

```java
// REMOVE PLACEHOLDER:
// private double calculateNetworkCentrality(String userId) {
//     return 0.1; // Placeholder
// }

// REPLACE WITH:
private double calculateNetworkCentrality(UUID userId) {
    try {
        return fraudRingDetectionService.analyzeFraudRing(userId)
            .getCentrality();
    } catch (Exception e) {
        log.error("Failed to calculate network centrality", e);
        return 0.5; // Conservative default
    }
}

// REMOVE PLACEHOLDER:
// private double calculateConnectionDiversity(String userId) {
//     return 0.5; // Placeholder
// }

// REPLACE WITH:
private double calculateConnectionDiversity(UUID userId) {
    try {
        Set<UUID> connections = graphService.getDirectConnections(userId);
        Set<String> countries = connections.stream()
            .map(this::getUserCountry)
            .collect(Collectors.toSet());

        // Diversity score based on unique countries
        return Math.min(1.0, countries.size() / 10.0);
    } catch (Exception e) {
        log.error("Failed to calculate connection diversity", e);
        return 0.5;
    }
}

// REMOVE PLACEHOLDER:
// private double countSuspiciousConnections(String userId) {
//     return 0.0; // Placeholder
// }

// REPLACE WITH:
private double countSuspiciousConnections(UUID userId) {
    try {
        FraudRingAnalysis analysis = fraudRingDetectionService
            .analyzeFraudRing(userId);

        long total = analysis.getCommunitySize();
        long suspicious = analysis.getFraudsterCount();

        return total > 0 ? (double) suspicious / total : 0.0;
    } catch (Exception e) {
        log.error("Failed to count suspicious connections", e);
        return 0.0;
    }
}
```

---

### PHASE 4: DATA PRECISION FIXES

#### 1. Fix Double/Float Money Issues (16 files)

**Create Utility Class:**

**File:** `util/MoneyUtils.java`

```java
@UtilityClass
public class MoneyUtils {

    /**
     * Convert BigDecimal to double safely for ML features
     * with explicit rounding
     */
    public static double toMLFeature(BigDecimal amount) {
        if (amount == null) {
            return 0.0;
        }
        return amount
            .setScale(2, RoundingMode.HALF_EVEN)
            .doubleValue();
    }

    /**
     * Convert double to BigDecimal safely
     */
    public static BigDecimal fromDouble(double value) {
        return BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_EVEN);
    }

    /**
     * Safe logarithm for ML features
     */
    public static double logTransform(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return Math.log(toMLFeature(amount) + 1.0);
    }
}
```

**Fix Pattern (apply to all 16 files):**

```java
// WRONG:
features.put("amount", request.getAmount().doubleValue());

// CORRECT:
features.put("amount", MoneyUtils.toMLFeature(request.getAmount()));

// WRONG:
features.put("amount_log", Math.log(request.getAmount().doubleValue() + 1));

// CORRECT:
features.put("amount_log", MoneyUtils.logTransform(request.getAmount()));
```

#### 2. Add Optimistic Locking to All Entities

**Pattern to Apply:**

```java
@Entity
public class [EntityName] {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ADD THIS:
    @Version
    private Long version;

    // ... rest of entity
}
```

**Entities Requiring @Version:**
1. FraudModel
2. FraudPrediction
3. TransactionHistory
4. FraudAlert
5. UserBehaviorProfile
6. DeviceProfile
7. LocationProfile
8. MerchantProfile (already has it)
9. CustomerProfile (already has it)
10. FraudRuleDefinition

---

### PHASE 5: IDEMPOTENCY FOR ALL KAFKA CONSUMERS

#### 1. Create Idempotency Service

**File:** `service/IdempotencyService.java`

```java
@Service
public class IdempotencyService {

    private final RedisTemplate<String, Boolean> redisTemplate;

    private static final String KEY_PREFIX = "fraud:idempotency:";
    private static final long TTL_HOURS = 24;

    /**
     * Check if event has been processed
     */
    public boolean isProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(key));
    }

    /**
     * Mark event as processed
     */
    public void markProcessed(String eventId) {
        String key = KEY_PREFIX + eventId;
        redisTemplate.opsForValue().set(key, true, TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Check and mark atomically (if not exists)
     */
    public boolean checkAndMark(String eventId) {
        String key = KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue()
                .setIfAbsent(key, true, TTL_HOURS, TimeUnit.HOURS)
        );
    }
}
```

#### 2. Apply to All Consumers (20+ consumers)

**Pattern:**

```java
@KafkaListener(topics = "fraud-events")
@Transactional(isolation = Isolation.SERIALIZABLE)
public void processFraudEvent(FraudEvent event) {

    // ADD IDEMPOTENCY CHECK:
    String idempotencyKey = event.getEventId() + ":" + event.getTransactionId();

    if (!idempotencyService.checkAndMark(idempotencyKey)) {
        log.debug("Duplicate event detected, skipping: {}", idempotencyKey);
        return;
    }

    // Process event...
}
```

**Consumers Requiring Idempotency:**
1. TransactionFraudEventsConsumer (CRITICAL)
2. CardFraudDetectionConsumer (verify if missing)
3. WalletWithdrawalFraudConsumer
4. ATMWithdrawalRequestedConsumer
5. DeviceFingerprintEventsConsumer
6. BehavioralBiometricEventsConsumer
7. SuspiciousPatternEventsConsumer
8. ... (all 63 consumers - audit each)

---

### PHASE 6: EXTERNAL INTEGRATIONS

#### 1. TensorFlow Serving Client

**File:** `integration/TensorFlowServingClientImpl.java`

```java
@Component
public class TensorFlowServingClientImpl {

    private final WebClient webClient;

    @Value("${fraud.integrations.tensorflow.endpoint}")
    private String tensorFlowEndpoint;

    /**
     * Make prediction request to TF Serving
     */
    @CircuitBreaker(name = "tensorflow", fallbackMethod = "fallbackPredict")
    @TimeLimiter(name = "tensorflow")
    public Mono<TensorFlowPrediction> predict(Map<String, Object> features) {

        TensorFlowRequest request = TensorFlowRequest.builder()
            .signature_name("serving_default")
            .instances(List.of(features))
            .build();

        return webClient.post()
            .uri(tensorFlowEndpoint + "/v1/models/fraud_detection:predict")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TensorFlowResponse.class)
            .map(this::extractPrediction)
            .timeout(Duration.ofSeconds(3));
    }
}
```

#### 2. MaxMind GeoIP Service

**File:** `integration/MaxMindGeoIPService.java`

```java
@Service
public class MaxMindGeoIPService {

    private DatabaseReader geoIpReader;

    @PostConstruct
    public void initialize() throws IOException {
        File database = new File(maxMindDbPath);
        this.geoIpReader = new DatabaseReader.Builder(database).build();
    }

    /**
     * Get country from IP address
     */
    public GeoLocation getLocation(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            CityResponse response = geoIpReader.city(inet);

            return GeoLocation.builder()
                .countryCode(response.getCountry().getIsoCode())
                .city(response.getCity().getName())
                .latitude(response.getLocation().getLatitude())
                .longitude(response.getLocation().getLongitude())
                .build();
        } catch (Exception e) {
            log.error("Failed to lookup IP: {}", ipAddress, e);
            return GeoLocation.unknown();
        }
    }

    /**
     * Detect VPN/Proxy
     */
    public boolean isVPNOrProxy(String ipAddress) {
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            AnonymousIpResponse response = geoIpReader.anonymousIp(inet);
            return response.isAnonymousVpn() ||
                   response.isPublicProxy() ||
                   response.isTorExitNode();
        } catch (Exception e) {
            return false;
        }
    }
}
```

#### 3. Threat Intelligence Integration

**File:** `integration/ThreatIntelligenceService.java`

```java
@Service
public class ThreatIntelligenceService {

    private final WebClient threatIntelClient;

    /**
     * Check IP reputation
     */
    @Cacheable(value = "ipReputation", key = "#ipAddress")
    public IPReputation checkIPReputation(String ipAddress) {
        // Integration with threat intelligence provider
        // (VirusTotal, AbuseIPDB, etc.)
    }

    /**
     * Check if IP is from known botnet
     */
    public boolean isKnownBotnet(String ipAddress) {
        IPReputation reputation = checkIPReputation(ipAddress);
        return reputation.getThreatScore() > 80;
    }
}
```

---

### PHASE 7: PRODUCTION CONFIGURATION

**File:** `src/main/resources/application.yml`

```yaml
# Complete Production Configuration

spring:
  application:
    name: fraud-detection-service

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/fraud_detection}
    username: ${DB_USERNAME:fraud_user}
    password: ${DB_PASSWORD:${vault.database.password}}
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true

  cache:
    type: redis
    redis:
      time-to-live: 3600000 # 1 hour

  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    consumer:
      group-id: fraud-detection-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed

# Fraud Detection Configuration
fraud:
  ml:
    enabled: ${ML_ENABLED:true}
    models:
      xgboost:
        path: ${XGBOOST_MODEL_PATH:/models/fraud-detection/xgboost_v3.onnx}
        version: 3.0
      random-forest:
        path: ${RF_MODEL_PATH:/models/fraud-detection/random_forest_v2.onnx}
        version: 2.0
      tensorflow:
        path: ${TF_MODEL_PATH:/models/fraud-detection/deep_neural_network}
        version: 1.5
    fallback:
      mode: CONSERVATIVE
      default-risk-score: 0.7

  integrations:
    sagemaker:
      enabled: ${SAGEMAKER_ENABLED:false}
      endpoint-name: ${AWS_SAGEMAKER_ENDPOINT:fraud-detection-prod}
      region: ${AWS_REGION:us-east-1}
      timeout: 3s

    tensorflow-serving:
      enabled: ${TF_SERVING_ENABLED:true}
      endpoint: ${TF_SERVING_URL:http://tensorflow-serving:8501}
      model-name: fraud_detection
      timeout: 3s

    maxmind:
      enabled: ${MAXMIND_ENABLED:true}
      license-key: ${MAXMIND_LICENSE_KEY}
      database-path: ${MAXMIND_DB_PATH:/data/GeoLite2-City.mmdb}
      update-interval: 7d

    threat-intelligence:
      enabled: ${THREAT_INTEL_ENABLED:true}
      provider: ${THREAT_INTEL_PROVIDER:virustotal}
      api-key: ${THREAT_INTEL_API_KEY}
      endpoint: ${THREAT_INTEL_URL:https://api.virustotal.com/v3}

    neo4j:
      enabled: ${NEO4J_ENABLED:true}
      uri: ${NEO4J_URI:bolt://localhost:7687}
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD}

  thresholds:
    high-risk-score: 0.75
    medium-risk-score: 0.50
    chargeback-rate: 0.01 # 1%
    refund-rate: 0.05 # 5%

  sanctions:
    ofac:
      enabled: true
      api-endpoint: ${OFAC_API_URL:https://api.trade.gov/consolidated_screening_list}
      api-key: ${OFAC_API_KEY}
      cache-ttl: 24h
      screening-timeout: 3s

# Resilience4j Configuration
resilience4j:
  circuitbreaker:
    instances:
      ml-service:
        sliding-window-size: 100
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
      tensorflow:
        sliding-window-size: 50
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
      ofac-screening:
        sliding-window-size: 100
        failure-rate-threshold: 30
        wait-duration-in-open-state: 10s

  timelimiter:
    instances:
      tensorflow:
        timeout-duration: 3s
      ofac-screening:
        timeout-duration: 3s

# Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## III. TESTING STRATEGY

### Unit Testing (50+ test classes)

**Example:** `CustomerProfileServiceTest.java`

```java
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerProfileServiceTest {

    @Mock
    private CustomerProfileRepository repository;

    @InjectMocks
    private CustomerProfileService service;

    @Test
    void testUpdateProfileAfterTransaction_NewCustomer() {
        // Test new customer profile creation
    }

    @Test
    void testUpdateProfileAfterTransaction_ExistingCustomer() {
        // Test profile update logic
    }

    @Test
    void testRiskLevelDetermination_HighRisk() {
        // Test high risk detection
    }
}
```

### Integration Testing (20+ test classes)

**Example:** `FraudDetectionIntegrationTest.java`

```java
@SpringBootTest
@Testcontainers
class FraudDetectionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Test
    void testEndToEndFraudDetection() {
        // Full workflow test
    }
}
```

### Performance Testing

**Gatling Scenario:**

```scala
class FraudDetectionLoadTest extends Simulation {

  val httpProtocol = http.baseUrl("http://localhost:8080")

  val fraudCheckScenario = scenario("Fraud Check")
    .exec(
      http("Check Transaction")
        .post("/api/fraud-detection/check")
        .body(StringBody("""{"transactionId": "..."}"""))
        .check(status.is(200))
        .check(responseTime.lt(100)) // <100ms requirement
    )

  setUp(
    fraudCheckScenario.inject(
      constantUsersPerSec(1000) during (60 minutes)
    )
  ).protocols(httpProtocol)
}
```

---

## IV. DEPLOYMENT CHECKLIST

### Pre-Deployment
- [ ] All 74 tasks completed
- [ ] 70%+ test coverage achieved
- [ ] Load testing passed (1000 TPS)
- [ ] Security scan passed (OWASP ZAP)
- [ ] ML models trained and validated
- [ ] Database migrations tested
- [ ] Configuration externalized
- [ ] Secrets encrypted in Vault

### Staging Deployment
- [ ] Deploy to staging environment
- [ ] Run smoke tests
- [ ] Run integration test suite
- [ ] Performance validation
- [ ] Security penetration testing
- [ ] 48-hour soak test

### Production Deployment
- [ ] Blue-green deployment setup
- [ ] Canary release (5% traffic)
- [ ] Monitor for 24 hours
- [ ] Gradually increase to 50%
- [ ] Monitor for 24 hours
- [ ] Full rollout to 100%
- [ ] 24/7 monitoring active
- [ ] On-call rotation staffed

---

**Document Owner:** Waqiti Fraud Detection Engineering Team
**Last Updated:** October 16, 2025
**Status:** Living Document - Updated During Implementation
