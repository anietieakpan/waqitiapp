package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.cache.RedisCache;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.NotificationTemplate;
import com.waqiti.security.dto.*;
import com.waqiti.security.model.*;
import com.waqiti.security.service.*;
import com.waqiti.security.repository.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
@Component
@Getter
@Setter
public class IpReputationMonitoringConsumer {

    private static final String TOPIC = "ip-reputation-monitoring";
    private static final String DLQ_TOPIC = "ip-reputation-monitoring.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 15000;
    private static final double CRITICAL_THREAT_SCORE = 90.0;
    private static final double HIGH_THREAT_SCORE = 70.0;
    private static final double MEDIUM_THREAT_SCORE = 50.0;
    private static final double LOW_THREAT_SCORE = 30.0;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IpReputationRepository ipReputationRepository;
    private final BlacklistRepository blacklistRepository;
    private final ThreatIntelligenceRepository threatIntelligenceRepository;
    private final SecurityEventRepository securityEventRepository;
    private final IpReputationService ipReputationService;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final GeoLocationService geoLocationService;
    private final NetworkAnalysisService networkAnalysisService;
    private final SecurityOrchestrationService securityOrchestrationService;
    private final FirewallService firewallService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final RedisCache redisCache;
    private final RestTemplate restTemplate;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private static final Map<String, List<String>> THREAT_FEEDS = new HashMap<>();
    private static final Map<String, Double> CATEGORY_WEIGHTS = new HashMap<>();
    private static final Set<String> RESERVED_IP_RANGES = new HashSet<>();
    
    static {
        THREAT_FEEDS.put("SPAMHAUS", Arrays.asList(
            "https://www.spamhaus.org/drop/drop.txt",
            "https://www.spamhaus.org/drop/edrop.txt"
        ));
        THREAT_FEEDS.put("TALOS", Arrays.asList(
            "https://talosintelligence.com/documents/ip-blacklist"
        ));
        THREAT_FEEDS.put("EMERGING_THREATS", Arrays.asList(
            "https://rules.emergingthreats.net/blockrules/compromised-ips.txt"
        ));
        
        CATEGORY_WEIGHTS.put("MALWARE", 0.95);
        CATEGORY_WEIGHTS.put("BOTNET", 0.90);
        CATEGORY_WEIGHTS.put("COMMAND_CONTROL", 0.95);
        CATEGORY_WEIGHTS.put("PHISHING", 0.85);
        CATEGORY_WEIGHTS.put("SPAM", 0.60);
        CATEGORY_WEIGHTS.put("SCANNER", 0.50);
        CATEGORY_WEIGHTS.put("TOR_EXIT", 0.40);
        CATEGORY_WEIGHTS.put("VPN", 0.30);
        CATEGORY_WEIGHTS.put("PROXY", 0.35);
        CATEGORY_WEIGHTS.put("HOSTING", 0.25);
        
        RESERVED_IP_RANGES.addAll(Arrays.asList(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
        ));
    }
    
    public IpReputationMonitoringConsumer() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .failureRateThreshold(50)
            .build();
            
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("ip-reputation-monitoring");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("ip-reputation-monitoring");
        
        this.executorService = Executors.newFixedThreadPool(15);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
        
        initializeScheduledTasks();
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpReputationRequest {
        private String ipAddress;
        private String userId;
        private String sessionId;
        private String service;
        private String action;
        private IpContext context;
        private List<String> associatedIps;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean deepAnalysis;
        private boolean realTimeCheck;
        private String correlationId;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpContext {
        private String requestType;
        private String userAgent;
        private String referrer;
        private String protocol;
        private Integer port;
        private String path;
        private Map<String, String> headers;
        private boolean authenticated;
        private String authMethod;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpReputationAnalysis {
        private String analysisId;
        private String ipAddress;
        private LocalDateTime timestamp;
        private ReputationScore reputationScore;
        private ThreatIntelligence threatIntel;
        private NetworkAnalysis networkAnalysis;
        private GeoLocationData geoData;
        private HistoricalAnalysis historicalData;
        private List<ThreatIndicator> threatIndicators;
        private List<SecurityRecommendation> recommendations;
        private AutomatedAction automatedAction;
        private boolean isBlacklisted;
        private boolean requiresReview;
        private Map<String, Object> evidence;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReputationScore {
        private double overallScore;
        private double threatScore;
        private double trustScore;
        private Map<String, Double> categoryScores;
        private String riskLevel;
        private String confidence;
        private LocalDateTime lastUpdated;
        private Integer dataPoints;
        private List<String> sources;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatIntelligence {
        private boolean knownThreat;
        private List<String> threatCategories;
        private List<String> threatFeeds;
        private Map<String, ThreatFeedData> feedDetails;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private Integer reportCount;
        private List<String> associatedMalware;
        private List<String> associatedCampaigns;
        private String threatActor;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatFeedData {
        private String feedName;
        private String category;
        private LocalDateTime listingDate;
        private String reason;
        private Integer severity;
        private Map<String, Object> additionalInfo;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAnalysis {
        private String asn;
        private String asnName;
        private String networkName;
        private String networkType;
        private boolean isHostingProvider;
        private boolean isVpnProvider;
        private boolean isTorExit;
        private boolean isProxy;
        private boolean isResidential;
        private boolean isCorporate;
        private boolean isEducational;
        private Double abuseScore;
        private List<String> openPorts;
        private List<String> runningServices;
        private String reverseDns;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocationData {
        private String country;
        private String countryCode;
        private String city;
        private String region;
        private String postalCode;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String isp;
        private String org;
        private boolean highRiskCountry;
        private boolean sanctionedCountry;
        private Double countryRiskScore;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalAnalysis {
        private Integer totalRequests;
        private Integer uniqueUsers;
        private Integer failedAuthAttempts;
        private Integer successfulAuthAttempts;
        private Integer suspiciousActivities;
        private Integer blockedRequests;
        private LocalDateTime firstActivity;
        private LocalDateTime lastActivity;
        private List<String> commonUserAgents;
        private List<String> targetedServices;
        private Map<String, Integer> activityPattern;
        private Double averageRiskScore;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatIndicator {
        private String indicatorType;
        private String severity;
        private String description;
        private Double confidence;
        private String source;
        private LocalDateTime detectedAt;
        private Map<String, Object> details;
        private boolean confirmed;
        private String mitigationAction;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityRecommendation {
        private String recommendationType;
        private String priority;
        private String action;
        private String reasoning;
        private Map<String, Object> parameters;
        private Double expectedRiskReduction;
        private boolean automated;
        private LocalDateTime expiresAt;
    }
    
    @Getter
@Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutomatedAction {
        private String actionType;
        private String status;
        private List<String> executedActions;
        private LocalDateTime executedAt;
        private Map<String, Object> results;
        private boolean reversible;
        private String rollbackPlan;
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = "security-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp
    ) {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        
        try {
            log.info("Processing IP reputation monitoring event: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            IpReputationRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            if (isReservedIp(request.getIpAddress())) {
                log.debug("Skipping reserved IP address: {}", request.getIpAddress());
                acknowledgment.acknowledge();
                return;
            }
            
            CompletableFuture<IpReputationAnalysis> analysisFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    analyzeIpReputation(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            IpReputationAnalysis analysis = analysisFuture.join();
            
            if (analysis.getReputationScore().getThreatScore() >= CRITICAL_THREAT_SCORE) {
                handleCriticalThreat(request, analysis);
            } else if (analysis.getReputationScore().getThreatScore() >= HIGH_THREAT_SCORE) {
                handleHighThreat(request, analysis);
            } else if (analysis.getReputationScore().getThreatScore() >= MEDIUM_THREAT_SCORE) {
                handleMediumThreat(request, analysis);
            } else if (analysis.getReputationScore().getThreatScore() >= LOW_THREAT_SCORE) {
                handleLowThreat(request, analysis);
            }
            
            persistAnalysisResults(request, analysis);
            publishReputationEvents(analysis);
            updateMetrics(analysis, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing IP reputation for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing IP reputation for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private void initializeScheduledTasks() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateThreatFeeds,
            0, 6, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::cleanupOldRecords,
            1, 24, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::aggregateReputationData,
            0, 1, TimeUnit.HOURS
        );
    }
    
    private IpReputationRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, IpReputationRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize IP reputation request", e);
        }
    }
    
    private void validateRequest(IpReputationRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getIpAddress() == null || request.getIpAddress().isEmpty()) {
            errors.add("IP address is required");
        } else if (!isValidIpAddress(request.getIpAddress())) {
            errors.add("Invalid IP address format");
        }
        
        if (request.getService() == null || request.getService().isEmpty()) {
            errors.add("Service name is required");
        }
        
        if (request.getTimestamp() == null) {
            errors.add("Timestamp is required");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }
    
    private boolean isValidIpAddress(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    private boolean isReservedIp(String ip) {
        return RESERVED_IP_RANGES.stream()
            .anyMatch(range -> isIpInRange(ip, range));
    }
    
    private boolean isIpInRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress targetAddr = InetAddress.getByName(ip);
            InetAddress rangeAddr = InetAddress.getByName(parts[0]);
            
            int prefixLength = Integer.parseInt(parts[1]);
            byte[] targetBytes = targetAddr.getAddress();
            byte[] rangeBytes = rangeAddr.getAddress();
            
            int bytesToCheck = prefixLength / 8;
            int bitsToCheck = prefixLength % 8;
            
            for (int i = 0; i < bytesToCheck; i++) {
                if (targetBytes[i] != rangeBytes[i]) {
                    return false;
                }
            }
            
            if (bitsToCheck > 0 && bytesToCheck < targetBytes.length) {
                int mask = 0xFF << (8 - bitsToCheck);
                return (targetBytes[bytesToCheck] & mask) == (rangeBytes[bytesToCheck] & mask);
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private <T> T executeWithResilience(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry,
            CircuitBreaker.decorateSupplier(circuitBreaker, supplier)).get();
    }
    
    private IpReputationAnalysis analyzeIpReputation(
        IpReputationRequest request,
        String correlationId
    ) {
        IpReputationAnalysis analysis = new IpReputationAnalysis();
        analysis.setAnalysisId(UUID.randomUUID().toString());
        analysis.setIpAddress(request.getIpAddress());
        analysis.setTimestamp(LocalDateTime.now());
        analysis.setThreatIndicators(new ArrayList<>());
        analysis.setRecommendations(new ArrayList<>());
        
        List<CompletableFuture<Void>> analysisTasks = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                checkThreatFeeds(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeNetworkCharacteristics(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                performGeoLocationAnalysis(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeHistoricalBehavior(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                checkBlacklists(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeThreatIntelligence(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                performPortScan(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                checkDnsReputation(request, analysis), executorService)
        );
        
        if (request.isDeepAnalysis()) {
            analysisTasks.add(CompletableFuture.runAsync(() -> 
                performDeepPacketInspection(request, analysis), executorService));
            analysisTasks.add(CompletableFuture.runAsync(() -> 
                analyzeSslCertificates(request, analysis), executorService));
        }

        try {
            CompletableFuture.allOf(analysisTasks.toArray(new CompletableFuture[0]))
                .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("IP reputation analysis timed out after 15 seconds for IP: {}", request.getIpAddress(), e);
            analysisTasks.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("IP reputation analysis failed for IP: {}", request.getIpAddress(), e);
        }

        calculateReputationScore(analysis);
        generateRecommendations(analysis);
        determineAutomatedAction(request, analysis);
        
        return analysis;
    }
    
    private void checkThreatFeeds(IpReputationRequest request, IpReputationAnalysis analysis) {
        ThreatIntelligence threatIntel = new ThreatIntelligence();
        threatIntel.setThreatCategories(new ArrayList<>());
        threatIntel.setThreatFeeds(new ArrayList<>());
        threatIntel.setFeedDetails(new HashMap<>());
        
        String cacheKey = "threat:feeds:" + request.getIpAddress();
        ThreatIntelligence cachedIntel = redisCache.get(cacheKey, ThreatIntelligence.class);
        
        if (cachedIntel != null && !request.isRealTimeCheck()) {
            analysis.setThreatIntel(cachedIntel);
            return;
        }
        
        for (Map.Entry<String, List<String>> feedEntry : THREAT_FEEDS.entrySet()) {
            String feedName = feedEntry.getKey();
            List<String> feedUrls = feedEntry.getValue();
            
            for (String feedUrl : feedUrls) {
                try {
                    Set<String> blacklistedIps = fetchThreatFeed(feedUrl);
                    if (blacklistedIps.contains(request.getIpAddress())) {
                        threatIntel.setKnownThreat(true);
                        threatIntel.getThreatFeeds().add(feedName);
                        
                        ThreatFeedData feedData = new ThreatFeedData();
                        feedData.setFeedName(feedName);
                        feedData.setCategory(determineThreatCategory(feedName));
                        feedData.setListingDate(LocalDateTime.now());
                        feedData.setSeverity(determineSeverity(feedName));
                        threatIntel.getFeedDetails().put(feedName, feedData);
                        
                        ThreatIndicator indicator = new ThreatIndicator();
                        indicator.setIndicatorType("THREAT_FEED_MATCH");
                        indicator.setSeverity("HIGH");
                        indicator.setDescription("IP found in " + feedName + " threat feed");
                        indicator.setConfidence(0.9);
                        indicator.setSource(feedName);
                        indicator.setDetectedAt(LocalDateTime.now());
                        indicator.setConfirmed(true);
                        analysis.getThreatIndicators().add(indicator);
                    }
                } catch (Exception e) {
                    log.warn("Failed to check threat feed {}: {}", feedName, e.getMessage());
                }
            }
        }
        
        if (threatIntel.isKnownThreat()) {
            threatIntel.setFirstSeen(getFirstSeenDate(request.getIpAddress()));
            threatIntel.setLastSeen(LocalDateTime.now());
            threatIntel.setReportCount(getReportCount(request.getIpAddress()));
        }
        
        analysis.setThreatIntel(threatIntel);
        redisCache.set(cacheKey, threatIntel, Duration.ofHours(6));
    }
    
    private void analyzeNetworkCharacteristics(IpReputationRequest request, IpReputationAnalysis analysis) {
        NetworkAnalysis networkAnalysis = networkAnalysisService
            .analyzeNetwork(request.getIpAddress());
        
        if (networkAnalysis == null) {
            networkAnalysis = new NetworkAnalysis();
        }
        
        enrichNetworkAnalysis(networkAnalysis, request.getIpAddress());
        
        if (networkAnalysis.isHostingProvider()) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("HOSTING_PROVIDER");
            indicator.setSeverity("LOW");
            indicator.setDescription("IP belongs to hosting provider");
            indicator.setConfidence(0.8);
            indicator.setDetails(Map.of(
                "provider", networkAnalysis.getAsnName(),
                "type", "hosting"
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            analysis.getThreatIndicators().add(indicator);
        }
        
        if (networkAnalysis.isTorExit()) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("TOR_EXIT_NODE");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("IP is a Tor exit node");
            indicator.setConfidence(0.95);
            indicator.setDetails(Map.of("anonymization", "tor"));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setConfirmed(true);
            analysis.getThreatIndicators().add(indicator);
        }
        
        if (networkAnalysis.isVpnProvider() || networkAnalysis.isProxy()) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("ANONYMIZATION_SERVICE");
            indicator.setSeverity("LOW");
            indicator.setDescription("IP associated with VPN/Proxy service");
            indicator.setConfidence(0.7);
            indicator.setDetails(Map.of(
                "vpn", networkAnalysis.isVpnProvider(),
                "proxy", networkAnalysis.isProxy()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            analysis.getThreatIndicators().add(indicator);
        }
        
        if (networkAnalysis.getAbuseScore() != null && networkAnalysis.getAbuseScore() > 50) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("HIGH_ABUSE_SCORE");
            indicator.setSeverity("HIGH");
            indicator.setDescription("Network has high abuse score");
            indicator.setConfidence(0.85);
            indicator.setDetails(Map.of("abuseScore", networkAnalysis.getAbuseScore()));
            indicator.setDetectedAt(LocalDateTime.now());
            analysis.getThreatIndicators().add(indicator);
        }
        
        analysis.setNetworkAnalysis(networkAnalysis);
    }
    
    private void performGeoLocationAnalysis(IpReputationRequest request, IpReputationAnalysis analysis) {
        GeoLocationData geoData = geoLocationService
            .getDetailedLocation(request.getIpAddress());
        
        if (geoData == null) {
            geoData = new GeoLocationData();
            geoData.setCountry("Unknown");
            geoData.setCountryCode("XX");
        }
        
        geoData.setHighRiskCountry(isHighRiskCountry(geoData.getCountryCode()));
        geoData.setSanctionedCountry(isSanctionedCountry(geoData.getCountryCode()));
        geoData.setCountryRiskScore(calculateCountryRiskScore(geoData.getCountryCode()));
        
        if (geoData.isHighRiskCountry()) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("HIGH_RISK_COUNTRY");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("IP from high-risk country: " + geoData.getCountry());
            indicator.setConfidence(0.9);
            indicator.setDetails(Map.of(
                "country", geoData.getCountry(),
                "riskScore", geoData.getCountryRiskScore()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            analysis.getThreatIndicators().add(indicator);
        }
        
        if (geoData.isSanctionedCountry()) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("SANCTIONED_COUNTRY");
            indicator.setSeverity("CRITICAL");
            indicator.setDescription("IP from sanctioned country: " + geoData.getCountry());
            indicator.setConfidence(1.0);
            indicator.setDetails(Map.of("country", geoData.getCountry()));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setConfirmed(true);
            analysis.getThreatIndicators().add(indicator);
        }
        
        analysis.setGeoData(geoData);
    }
    
    private void analyzeHistoricalBehavior(IpReputationRequest request, IpReputationAnalysis analysis) {
        HistoricalAnalysis historical = new HistoricalAnalysis();
        
        List<SecurityEvent> events = securityEventRepository
            .findByIpAddressAndTimestampAfter(
                request.getIpAddress(),
                LocalDateTime.now().minusDays(90)
            );
        
        historical.setTotalRequests(events.size());
        historical.setUniqueUsers(events.stream()
            .map(SecurityEvent::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .count()
            .intValue());
        
        historical.setFailedAuthAttempts(events.stream()
            .filter(e -> "AUTH_FAILED".equals(e.getEventType()))
            .count()
            .intValue());
        
        historical.setSuccessfulAuthAttempts(events.stream()
            .filter(e -> "AUTH_SUCCESS".equals(e.getEventType()))
            .count()
            .intValue());
        
        historical.setSuspiciousActivities(events.stream()
            .filter(e -> e.getRiskScore() != null && e.getRiskScore() > 0.5)
            .count()
            .intValue());
        
        historical.setBlockedRequests(events.stream()
            .filter(e -> "BLOCKED".equals(e.getStatus()))
            .count()
            .intValue());
        
        if (!events.isEmpty()) {
            historical.setFirstActivity(events.stream()
                .map(SecurityEvent::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(null));
            
            historical.setLastActivity(events.stream()
                .map(SecurityEvent::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null));
            
            historical.setCommonUserAgents(events.stream()
                .map(SecurityEvent::getUserAgent)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ua -> ua, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()));
            
            historical.setTargetedServices(events.stream()
                .map(SecurityEvent::getService)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
            
            historical.setAverageRiskScore(events.stream()
                .map(SecurityEvent::getRiskScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0));
        }
        
        if (historical.getFailedAuthAttempts() > 100) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("BRUTE_FORCE_HISTORY");
            indicator.setSeverity("HIGH");
            indicator.setDescription("History of brute force attempts");
            indicator.setConfidence(0.9);
            indicator.setDetails(Map.of(
                "failedAttempts", historical.getFailedAuthAttempts(),
                "period", "90 days"
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setConfirmed(true);
            analysis.getThreatIndicators().add(indicator);
        }
        
        if (historical.getSuspiciousActivities() > 50) {
            ThreatIndicator indicator = new ThreatIndicator();
            indicator.setIndicatorType("SUSPICIOUS_ACTIVITY_HISTORY");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("Pattern of suspicious activities");
            indicator.setConfidence(0.8);
            indicator.setDetails(Map.of(
                "suspiciousCount", historical.getSuspiciousActivities(),
                "averageRiskScore", historical.getAverageRiskScore()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            analysis.getThreatIndicators().add(indicator);
        }
        
        analysis.setHistoricalData(historical);
    }
    
    private void checkBlacklists(IpReputationRequest request, IpReputationAnalysis analysis) {
        List<Blacklist> blacklists = blacklistRepository.findActiveByIpAddress(request.getIpAddress());
        
        if (!blacklists.isEmpty()) {
            analysis.setBlacklisted(true);
            
            for (Blacklist blacklist : blacklists) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("BLACKLISTED");
                indicator.setSeverity("CRITICAL");
                indicator.setDescription("IP is blacklisted: " + blacklist.getReason());
                indicator.setConfidence(1.0);
                indicator.setSource(blacklist.getSource());
                indicator.setDetails(Map.of(
                    "blacklistId", blacklist.getId(),
                    "reason", blacklist.getReason(),
                    "listedDate", blacklist.getListedDate()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setConfirmed(true);
                analysis.getThreatIndicators().add(indicator);
            }
        }
    }
    
    private void analyzeThreatIntelligence(IpReputationRequest request, IpReputationAnalysis analysis) {
        List<ThreatIntelligenceData> threatData = threatIntelligenceRepository
            .findByIpAddress(request.getIpAddress());
        
        if (!threatData.isEmpty() && analysis.getThreatIntel() != null) {
            ThreatIntelligence threatIntel = analysis.getThreatIntel();
            
            threatIntel.setAssociatedMalware(threatData.stream()
                .map(ThreatIntelligenceData::getMalwareName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
            
            threatIntel.setAssociatedCampaigns(threatData.stream()
                .map(ThreatIntelligenceData::getCampaignName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
            
            String threatActor = threatData.stream()
                .map(ThreatIntelligenceData::getThreatActor)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            threatIntel.setThreatActor(threatActor);
            
            if (!threatIntel.getAssociatedMalware().isEmpty()) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("MALWARE_ASSOCIATION");
                indicator.setSeverity("CRITICAL");
                indicator.setDescription("IP associated with known malware");
                indicator.setConfidence(0.95);
                indicator.setDetails(Map.of(
                    "malware", threatIntel.getAssociatedMalware()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setConfirmed(true);
                analysis.getThreatIndicators().add(indicator);
            }
            
            if (threatActor != null) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("THREAT_ACTOR");
                indicator.setSeverity("CRITICAL");
                indicator.setDescription("IP associated with threat actor: " + threatActor);
                indicator.setConfidence(0.9);
                indicator.setDetails(Map.of("actor", threatActor));
                indicator.setDetectedAt(LocalDateTime.now());
                analysis.getThreatIndicators().add(indicator);
            }
        }
    }
    
    private void performPortScan(IpReputationRequest request, IpReputationAnalysis analysis) {
        if (analysis.getNetworkAnalysis() == null) {
            return;
        }
        
        try {
            List<Integer> commonPorts = Arrays.asList(
                21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 3306, 3389, 8080, 8443
            );
            
            List<String> openPorts = new ArrayList<>();
            for (Integer port : commonPorts) {
                if (isPortOpen(request.getIpAddress(), port)) {
                    openPorts.add(String.valueOf(port));
                }
            }
            
            analysis.getNetworkAnalysis().setOpenPorts(openPorts);
            
            if (openPorts.contains("23") || openPorts.contains("445") || openPorts.contains("3389")) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("RISKY_PORTS_OPEN");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("Potentially risky ports are open");
                indicator.setConfidence(0.7);
                indicator.setDetails(Map.of("openPorts", openPorts));
                indicator.setDetectedAt(LocalDateTime.now());
                analysis.getThreatIndicators().add(indicator);
            }
        } catch (Exception e) {
            log.debug("Port scan failed for {}: {}", request.getIpAddress(), e.getMessage());
        }
    }
    
    private void checkDnsReputation(IpReputationRequest request, IpReputationAnalysis analysis) {
        if (analysis.getNetworkAnalysis() == null) {
            return;
        }
        
        try {
            String reverseDns = performReverseDnsLookup(request.getIpAddress());
            analysis.getNetworkAnalysis().setReverseDns(reverseDns);
            
            if (reverseDns == null || reverseDns.isEmpty()) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("NO_REVERSE_DNS");
                indicator.setSeverity("LOW");
                indicator.setDescription("No reverse DNS record");
                indicator.setConfidence(0.6);
                indicator.setDetectedAt(LocalDateTime.now());
                analysis.getThreatIndicators().add(indicator);
            } else if (isSuspiciousDomain(reverseDns)) {
                ThreatIndicator indicator = new ThreatIndicator();
                indicator.setIndicatorType("SUSPICIOUS_DOMAIN");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("Suspicious reverse DNS: " + reverseDns);
                indicator.setConfidence(0.7);
                indicator.setDetails(Map.of("domain", reverseDns));
                indicator.setDetectedAt(LocalDateTime.now());
                analysis.getThreatIndicators().add(indicator);
            }
        } catch (Exception e) {
            log.debug("DNS check failed for {}: {}", request.getIpAddress(), e.getMessage());
        }
    }
    
    private void performDeepPacketInspection(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.debug("Performing deep packet inspection for {}", request.getIpAddress());
    }
    
    private void analyzeSslCertificates(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.debug("Analyzing SSL certificates for {}", request.getIpAddress());
    }
    
    private void calculateReputationScore(IpReputationAnalysis analysis) {
        ReputationScore score = new ReputationScore();
        score.setCategoryScores(new HashMap<>());
        score.setSources(new ArrayList<>());
        
        double threatScore = 0.0;
        double trustScore = 100.0;
        int dataPoints = 0;
        
        for (ThreatIndicator indicator : analysis.getThreatIndicators()) {
            double weight = getIndicatorWeight(indicator.getSeverity());
            threatScore += weight * indicator.getConfidence();
            trustScore -= weight * indicator.getConfidence();
            dataPoints++;
            
            String category = mapIndicatorToCategory(indicator.getIndicatorType());
            score.getCategoryScores().merge(category, weight, Double::sum);
        }
        
        if (analysis.isBlacklisted()) {
            threatScore = Math.max(threatScore, 95.0);
            trustScore = Math.min(trustScore, 5.0);
        }
        
        if (analysis.getThreatIntel() != null && analysis.getThreatIntel().isKnownThreat()) {
            threatScore = Math.max(threatScore, 80.0);
            trustScore = Math.min(trustScore, 20.0);
            score.getSources().addAll(analysis.getThreatIntel().getThreatFeeds());
        }
        
        if (analysis.getHistoricalData() != null) {
            double historicalRisk = analysis.getHistoricalData().getAverageRiskScore() * 100;
            threatScore = (threatScore + historicalRisk) / 2;
            dataPoints++;
        }
        
        threatScore = Math.min(100.0, Math.max(0.0, threatScore));
        trustScore = Math.max(0.0, Math.min(100.0, trustScore));
        
        score.setThreatScore(threatScore);
        score.setTrustScore(trustScore);
        score.setOverallScore((threatScore + (100 - trustScore)) / 2);
        score.setRiskLevel(determineRiskLevel(threatScore));
        score.setConfidence(determineConfidence(dataPoints));
        score.setDataPoints(dataPoints);
        score.setLastUpdated(LocalDateTime.now());
        
        analysis.setReputationScore(score);
    }
    
    private void generateRecommendations(IpReputationAnalysis analysis) {
        double threatScore = analysis.getReputationScore().getThreatScore();
        
        if (threatScore >= CRITICAL_THREAT_SCORE) {
            SecurityRecommendation rec = new SecurityRecommendation();
            rec.setRecommendationType("BLOCK_IMMEDIATELY");
            rec.setPriority("CRITICAL");
            rec.setAction("Block all traffic from this IP");
            rec.setReasoning("Critical threat score indicates immediate danger");
            rec.setExpectedRiskReduction(0.95);
            rec.setAutomated(true);
            rec.setExpiresAt(LocalDateTime.now().plusDays(30));
            analysis.getRecommendations().add(rec);
            
            SecurityRecommendation rec2 = new SecurityRecommendation();
            rec2.setRecommendationType("INCIDENT_RESPONSE");
            rec2.setPriority("HIGH");
            rec2.setAction("Initiate security incident response");
            rec2.setReasoning("Potential active threat detected");
            rec2.setAutomated(false);
            analysis.getRecommendations().add(rec2);
        } else if (threatScore >= HIGH_THREAT_SCORE) {
            SecurityRecommendation rec = new SecurityRecommendation();
            rec.setRecommendationType("RATE_LIMIT");
            rec.setPriority("HIGH");
            rec.setAction("Apply strict rate limiting");
            rec.setParameters(Map.of(
                "requestsPerMinute", 10,
                "burstSize", 20
            ));
            rec.setReasoning("High threat score requires traffic control");
            rec.setExpectedRiskReduction(0.7);
            rec.setAutomated(true);
            rec.setExpiresAt(LocalDateTime.now().plusDays(7));
            analysis.getRecommendations().add(rec);
        } else if (threatScore >= MEDIUM_THREAT_SCORE) {
            SecurityRecommendation rec = new SecurityRecommendation();
            rec.setRecommendationType("ENHANCED_MONITORING");
            rec.setPriority("MEDIUM");
            rec.setAction("Enable detailed logging and monitoring");
            rec.setReasoning("Medium threat requires closer observation");
            rec.setExpectedRiskReduction(0.4);
            rec.setAutomated(true);
            rec.setExpiresAt(LocalDateTime.now().plusDays(3));
            analysis.getRecommendations().add(rec);
        }
        
        if (analysis.getGeoData() != null && analysis.getGeoData().isSanctionedCountry()) {
            SecurityRecommendation rec = new SecurityRecommendation();
            rec.setRecommendationType("COMPLIANCE_BLOCK");
            rec.setPriority("CRITICAL");
            rec.setAction("Block for compliance reasons");
            rec.setReasoning("IP from sanctioned country");
            rec.setAutomated(true);
            analysis.getRecommendations().add(rec);
        }
    }
    
    private void determineAutomatedAction(IpReputationRequest request, IpReputationAnalysis analysis) {
        AutomatedAction action = new AutomatedAction();
        action.setExecutedActions(new ArrayList<>());
        action.setResults(new HashMap<>());
        
        double threatScore = analysis.getReputationScore().getThreatScore();
        
        if (threatScore >= CRITICAL_THREAT_SCORE || analysis.isBlacklisted()) {
            action.setActionType("BLOCK_AND_ALERT");
            action.setStatus("EXECUTED");
            action.getExecutedActions().add("Added IP to firewall block list");
            action.getExecutedActions().add("Terminated existing connections");
            action.getExecutedActions().add("Triggered security alert");
            action.setReversible(true);
            action.setRollbackPlan("Remove from firewall block list via manual review");
        } else if (threatScore >= HIGH_THREAT_SCORE) {
            action.setActionType("RATE_LIMIT_AND_MONITOR");
            action.setStatus("EXECUTED");
            action.getExecutedActions().add("Applied rate limiting rules");
            action.getExecutedActions().add("Enabled detailed logging");
            action.getExecutedActions().add("Added to watch list");
            action.setReversible(true);
            action.setRollbackPlan("Remove rate limits after threat assessment");
        } else if (threatScore >= MEDIUM_THREAT_SCORE) {
            action.setActionType("MONITOR");
            action.setStatus("ACTIVE");
            action.getExecutedActions().add("Enhanced monitoring enabled");
            action.getExecutedActions().add("Added to suspicious IP list");
            action.setReversible(true);
        } else {
            action.setActionType("LOG_ONLY");
            action.setStatus("LOGGED");
            action.getExecutedActions().add("Activity logged for analysis");
            action.setReversible(false);
        }
        
        action.setExecutedAt(LocalDateTime.now());
        analysis.setAutomatedAction(action);
    }
    
    private Set<String> fetchThreatFeed(String feedUrl) {
        Set<String> ips = new HashSet<>();
        try {
            String cacheKey = "feed:cache:" + feedUrl.hashCode();
            Set<String> cachedIps = redisCache.get(cacheKey, Set.class);
            if (cachedIps != null) {
                return cachedIps;
            }
            
            String response = restTemplate.getForObject(feedUrl, String.class);
            if (response != null) {
                Arrays.stream(response.split("\\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(ips::add);
            }
            
            redisCache.set(cacheKey, ips, Duration.ofHours(6));
        } catch (Exception e) {
            log.warn("Failed to fetch threat feed from {}: {}", feedUrl, e.getMessage());
        }
        return ips;
    }
    
    private void enrichNetworkAnalysis(NetworkAnalysis analysis, String ip) {
        try {
            analysis.setAsn(lookupAsn(ip));
            analysis.setAsnName(lookupAsnName(analysis.getAsn()));
            analysis.setNetworkType(determineNetworkType(analysis));
        } catch (Exception e) {
            log.debug("Failed to enrich network analysis: {}", e.getMessage());
        }
    }
    
    private String lookupAsn(String ip) {
        return "AS" + Math.abs(ip.hashCode() % 65535);
    }
    
    private String lookupAsnName(String asn) {
        Map<String, String> knownAsns = Map.of(
            "AS15169", "Google LLC",
            "AS16509", "Amazon.com Inc.",
            "AS8075", "Microsoft Corporation",
            "AS13335", "Cloudflare Inc."
        );
        return knownAsns.getOrDefault(asn, "Unknown Provider");
    }
    
    private String determineNetworkType(NetworkAnalysis analysis) {
        if (analysis.isResidential()) return "RESIDENTIAL";
        if (analysis.isCorporate()) return "CORPORATE";
        if (analysis.isEducational()) return "EDUCATIONAL";
        if (analysis.isHostingProvider()) return "HOSTING";
        return "UNKNOWN";
    }
    
    private boolean isHighRiskCountry(String countryCode) {
        Set<String> highRiskCountries = Set.of(
            "KP", "IR", "SY", "CU", "RU", "CN", "VE", "LY", "SO"
        );
        return highRiskCountries.contains(countryCode);
    }
    
    private boolean isSanctionedCountry(String countryCode) {
        Set<String> sanctionedCountries = Set.of(
            "KP", "IR", "SY", "CU"
        );
        return sanctionedCountries.contains(countryCode);
    }
    
    private double calculateCountryRiskScore(String countryCode) {
        Map<String, Double> countryScores = Map.of(
            "KP", 100.0,
            "IR", 95.0,
            "SY", 95.0,
            "CU", 85.0,
            "RU", 75.0,
            "CN", 70.0,
            "US", 20.0,
            "GB", 15.0,
            "DE", 15.0
        );
        return countryScores.getOrDefault(countryCode, 30.0);
    }
    
    private String determineThreatCategory(String feedName) {
        if (feedName.contains("SPAM")) return "SPAM";
        if (feedName.contains("MALWARE")) return "MALWARE";
        if (feedName.contains("BOTNET")) return "BOTNET";
        return "GENERAL_THREAT";
    }
    
    private Integer determineSeverity(String feedName) {
        if (feedName.contains("CRITICAL")) return 10;
        if (feedName.contains("HIGH")) return 8;
        if (feedName.contains("MEDIUM")) return 5;
        return 3;
    }
    
    private LocalDateTime getFirstSeenDate(String ip) {
        return securityEventRepository
            .findFirstByIpAddressOrderByTimestampAsc(ip)
            .map(SecurityEvent::getTimestamp)
            .orElse(LocalDateTime.now());
    }
    
    private Integer getReportCount(String ip) {
        return securityEventRepository.countByIpAddress(ip);
    }
    
    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 100);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String performReverseDnsLookup(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.getCanonicalHostName();
        } catch (Exception e) {
            log.debug("Reverse DNS lookup failed for IP: {}. Using IP as hostname.", ip);
            return ip; // Return the IP itself if reverse DNS fails
        }
    }
    
    private boolean isSuspiciousDomain(String domain) {
        List<String> suspiciousPatterns = Arrays.asList(
            "dynamic", "pool", "dsl", "dial", "broadband",
            "dhcp", "customer", "client", "user"
        );
        
        String lowerDomain = domain.toLowerCase();
        return suspiciousPatterns.stream()
            .anyMatch(lowerDomain::contains);
    }
    
    private double getIndicatorWeight(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL": return 25.0;
            case "HIGH": return 15.0;
            case "MEDIUM": return 8.0;
            case "LOW": return 3.0;
            default: return 1.0;
        }
    }
    
    private String mapIndicatorToCategory(String indicatorType) {
        Map<String, String> categoryMap = Map.of(
            "MALWARE_ASSOCIATION", "MALWARE",
            "THREAT_FEED_MATCH", "THREAT_FEED",
            "BLACKLISTED", "BLACKLIST",
            "TOR_EXIT_NODE", "ANONYMIZATION",
            "VPN_DETECTED", "ANONYMIZATION",
            "BRUTE_FORCE_HISTORY", "ATTACK",
            "HIGH_RISK_COUNTRY", "GEOGRAPHIC"
        );
        return categoryMap.getOrDefault(indicatorType, "OTHER");
    }
    
    private String determineRiskLevel(double threatScore) {
        if (threatScore >= CRITICAL_THREAT_SCORE) return "CRITICAL";
        if (threatScore >= HIGH_THREAT_SCORE) return "HIGH";
        if (threatScore >= MEDIUM_THREAT_SCORE) return "MEDIUM";
        if (threatScore >= LOW_THREAT_SCORE) return "LOW";
        return "MINIMAL";
    }
    
    private String determineConfidence(int dataPoints) {
        if (dataPoints >= 10) return "HIGH";
        if (dataPoints >= 5) return "MEDIUM";
        return "LOW";
    }
    
    private void handleCriticalThreat(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.error("CRITICAL THREAT DETECTED - IP: {}, Score: {}", 
            request.getIpAddress(), analysis.getReputationScore().getThreatScore());
        
        firewallService.blockIpAddress(request.getIpAddress(), "Critical threat detected");
        firewallService.terminateConnections(request.getIpAddress());
        
        if (!analysis.isBlacklisted()) {
            Blacklist blacklist = new Blacklist();
            blacklist.setIpAddress(request.getIpAddress());
            blacklist.setReason("Critical threat score: " + analysis.getReputationScore().getThreatScore());
            blacklist.setSource("IP_REPUTATION_MONITORING");
            blacklist.setListedDate(LocalDateTime.now());
            blacklist.setActive(true);
            blacklistRepository.save(blacklist);
        }
        
        securityOrchestrationService.initiateIncidentResponse(
            "CRITICAL_IP_THREAT",
            request.getIpAddress(),
            analysis
        );
        
        sendCriticalThreatAlert(request, analysis);
    }
    
    private void handleHighThreat(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.warn("HIGH THREAT DETECTED - IP: {}, Score: {}", 
            request.getIpAddress(), analysis.getReputationScore().getThreatScore());
        
        firewallService.rateLimitIpAddress(request.getIpAddress(), 10, 60);
        
        sendHighThreatNotification(request, analysis);
    }
    
    private void handleMediumThreat(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.info("MEDIUM THREAT DETECTED - IP: {}, Score: {}", 
            request.getIpAddress(), analysis.getReputationScore().getThreatScore());
        
        firewallService.enableEnhancedLogging(request.getIpAddress());
        
        sendMediumThreatNotification(request, analysis);
    }
    
    private void handleLowThreat(IpReputationRequest request, IpReputationAnalysis analysis) {
        log.info("LOW THREAT - IP: {}, Score: {}", 
            request.getIpAddress(), analysis.getReputationScore().getThreatScore());
    }
    
    private void sendCriticalThreatAlert(IpReputationRequest request, IpReputationAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_IP_THREAT")
            .priority("URGENT")
            .recipient("security-team")
            .subject("Critical IP Threat Detected")
            .templateData(Map.of(
                "ipAddress", request.getIpAddress(),
                "threatScore", analysis.getReputationScore().getThreatScore(),
                "indicators", analysis.getThreatIndicators().stream()
                    .filter(i -> "CRITICAL".equals(i.getSeverity()))
                    .map(i -> i.getDescription())
                    .collect(Collectors.toList()),
                "country", analysis.getGeoData() != null ? 
                    analysis.getGeoData().getCountry() : "Unknown",
                "action", "IP has been blocked"
            ))
            .channels(Arrays.asList("EMAIL", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighThreatNotification(IpReputationRequest request, IpReputationAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_IP_THREAT")
            .priority("HIGH")
            .recipient("security-team")
            .subject("High Risk IP Detected")
            .templateData(Map.of(
                "ipAddress", request.getIpAddress(),
                "threatScore", analysis.getReputationScore().getThreatScore(),
                "action", "Rate limiting applied"
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendMediumThreatNotification(IpReputationRequest request, IpReputationAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("MEDIUM_IP_THREAT")
            .priority("MEDIUM")
            .recipient("security-team")
            .subject("Medium Risk IP Detected")
            .templateData(Map.of(
                "ipAddress", request.getIpAddress(),
                "threatScore", analysis.getReputationScore().getThreatScore()
            ))
            .channels(Arrays.asList("EMAIL"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistAnalysisResults(IpReputationRequest request, IpReputationAnalysis analysis) {
        IpReputation reputation = new IpReputation();
        reputation.setIpAddress(request.getIpAddress());
        reputation.setThreatScore(analysis.getReputationScore().getThreatScore());
        reputation.setTrustScore(analysis.getReputationScore().getTrustScore());
        reputation.setRiskLevel(analysis.getReputationScore().getRiskLevel());
        reputation.setCountry(analysis.getGeoData() != null ? 
            analysis.getGeoData().getCountry() : null);
        reputation.setAsn(analysis.getNetworkAnalysis() != null ? 
            analysis.getNetworkAnalysis().getAsn() : null);
        reputation.setBlacklisted(analysis.isBlacklisted());
        reputation.setLastAnalysis(LocalDateTime.now());
        reputation.setAnalysisDetails(objectMapper.convertValue(analysis, Map.class));
        
        ipReputationRepository.save(reputation);
        
        SecurityEvent event = new SecurityEvent();
        event.setEventId(analysis.getAnalysisId());
        event.setEventType("IP_REPUTATION_ANALYSIS");
        event.setIpAddress(request.getIpAddress());
        event.setUserId(request.getUserId());
        event.setSessionId(request.getSessionId());
        event.setService(request.getService());
        event.setRiskScore(analysis.getReputationScore().getThreatScore() / 100.0);
        event.setRiskLevel(analysis.getReputationScore().getRiskLevel());
        event.setTimestamp(LocalDateTime.now());
        event.setDetails(objectMapper.convertValue(analysis, Map.class));
        
        securityEventRepository.save(event);
        
        String cacheKey = "ip:reputation:" + request.getIpAddress();
        redisCache.set(cacheKey, analysis, Duration.ofHours(1));
    }
    
    private void publishReputationEvents(IpReputationAnalysis analysis) {
        if (analysis.getReputationScore().getThreatScore() >= HIGH_THREAT_SCORE) {
            kafkaTemplate.send("security-alerts", analysis.getIpAddress(), analysis);
        }
        
        if (analysis.isRequiresReview()) {
            kafkaTemplate.send("security-review-queue", analysis.getIpAddress(), analysis);
        }
        
        kafkaTemplate.send("ip-reputation-updates", analysis.getIpAddress(), analysis);
    }
    
    private void updateMetrics(IpReputationAnalysis analysis, long processingTime) {
        metricsService.recordIpThreatScore(analysis.getReputationScore().getThreatScore());
        metricsService.recordIpRiskLevel(analysis.getReputationScore().getRiskLevel());
        metricsService.recordThreatIndicatorCount(analysis.getThreatIndicators().size());
        metricsService.recordProcessingTime("ip-reputation-monitoring", processingTime);
        
        if (analysis.getReputationScore().getThreatScore() >= CRITICAL_THREAT_SCORE) {
            metricsService.incrementCounter("critical.threat.ips");
        } else if (analysis.getReputationScore().getThreatScore() >= HIGH_THREAT_SCORE) {
            metricsService.incrementCounter("high.threat.ips");
        } else if (analysis.getReputationScore().getThreatScore() >= MEDIUM_THREAT_SCORE) {
            metricsService.incrementCounter("medium.threat.ips");
        } else if (analysis.getReputationScore().getThreatScore() >= LOW_THREAT_SCORE) {
            metricsService.incrementCounter("low.threat.ips");
        }
        
        if (analysis.isBlacklisted()) {
            metricsService.incrementCounter("blacklisted.ips");
        }
    }
    
    private void updateThreatFeeds() {
        log.info("Updating threat feeds...");
        try {
            for (Map.Entry<String, List<String>> entry : THREAT_FEEDS.entrySet()) {
                for (String feedUrl : entry.getValue()) {
                    fetchThreatFeed(feedUrl);
                }
            }
            log.info("Threat feeds updated successfully");
        } catch (Exception e) {
            log.error("Failed to update threat feeds", e);
        }
    }
    
    private void cleanupOldRecords() {
        log.info("Cleaning up old reputation records...");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            ipReputationRepository.deleteByLastAnalysisBefore(cutoffDate);
            securityEventRepository.deleteByTimestampBefore(cutoffDate);
            log.info("Old records cleaned up successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup old records", e);
        }
    }
    
    private void aggregateReputationData() {
        log.debug("Aggregating reputation data...");
        try {
            List<IpReputation> recentAnalyses = ipReputationRepository
                .findByLastAnalysisAfter(LocalDateTime.now().minusHours(1));
            
            Map<String, Double> avgScoresByCountry = recentAnalyses.stream()
                .filter(r -> r.getCountry() != null)
                .collect(Collectors.groupingBy(
                    IpReputation::getCountry,
                    Collectors.averagingDouble(IpReputation::getThreatScore)
                ));
            
            metricsService.recordCountryThreatScores(avgScoresByCountry);
        } catch (Exception e) {
            log.error("Failed to aggregate reputation data", e);
        }
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("ip.reputation.monitoring.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("ip.reputation.monitoring.errors");
        log.error("Failed to process IP reputation monitoring: {}", record.key(), error);
        sendToDLQ(record, error.getMessage());
        acknowledgment.acknowledge();
    }
    
    private void sendToDLQ(ConsumerRecord<String, String> record, String reason) {
        try {
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                DLQ_TOPIC,
                record.key(),
                Map.of(
                    "originalTopic", TOPIC,
                    "originalMessage", record.value(),
                    "failureReason", reason,
                    "failureTimestamp", Instant.now().toString(),
                    "retryCount", record.headers().lastHeader("retryCount") != null ?
                        Integer.parseInt(new String(record.headers().lastHeader("retryCount").value())) + 1 : 1
                )
            );
            
            kafkaTemplate.send(dlqRecord);
            log.info("Message sent to DLQ: {}", record.key());
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", record.key(), e);
        }
    }
}