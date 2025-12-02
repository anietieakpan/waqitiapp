package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.model.*;
import com.waqiti.lending.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;
import com.waqiti.common.security.SecurityContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class P2PLendingMatchEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(P2PLendingMatchEventConsumer.class);
    
    private static final String TOPIC = "waqiti.lending.p2p-lending-match";
    private static final String CONSUMER_GROUP = "p2p-lending-match-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.lending.p2p-lending-match.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final P2PLendingService p2pLendingService;
    private final P2PLendingMatchingService matchingService;
    private final P2PLendingRiskService riskService;
    private final P2PLendingComplianceService complianceService;
    private final P2PLendingNotificationService notificationService;
    private final P2PLendingAuctionService auctionService;
    private final SecurityContextHolder securityContextHolder;
    
    @Value("${lending.p2p.max-loan-amount:100000.00}")
    private BigDecimal maxLoanAmount;
    
    @Value("${lending.p2p.min-loan-amount:1000.00}")
    private BigDecimal minLoanAmount;
    
    @Value("${lending.p2p.max-interest-rate:0.36}")
    private BigDecimal maxInterestRate;
    
    @Value("${lending.p2p.min-credit-score:600}")
    private int minCreditScore;
    
    @Value("${lending.p2p.matching-timeout-hours:72}")
    private int matchingTimeoutHours;
    
    @Value("${lending.p2p.rate-limit.global:2000}")
    private int globalRateLimit;
    
    @Value("${lending.p2p.batch-size:25}")
    private int batchSize;
    
    @Value("${lending.p2p.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService matchingExecutor = Executors.newFixedThreadPool(4);
    
    private CircuitBreaker circuitBreaker;
    private Retry retryConfig;
    
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter loansRequestedCounter;
    private Counter investmentOffersCounter;
    private Counter matchesCreatedCounter;
    private Counter matchesCompletedCounter;
    private Counter matchesExpiredCounter;
    private Counter auctionsStartedCounter;
    private Counter bidsReceivedCounter;
    private Counter complianceFailuresCounter;
    
    private Timer messageProcessingTimer;
    private Timer matchingProcessingTimer;
    private Timer riskAssessmentTimer;
    private Timer complianceCheckTimer;
    private Timer auctionProcessingTimer;
    
    private final AtomicLong totalLoanRequests = new AtomicLong(0);
    private final AtomicLong totalInvestmentOffers = new AtomicLong(0);
    private final AtomicLong totalMatches = new AtomicLong(0);
    private final AtomicLong totalLoanAmount = new AtomicLong(0);
    private final AtomicInteger currentGlobalRate = new AtomicInteger(0);
    
    private final ConcurrentHashMap<String, P2PLoanRequest> loanRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, P2PInvestmentOffer> investmentOffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, P2PLendingMatch> matches = new ConcurrentHashMap<>();
    private final BlockingQueue<P2PLendingMatchJob> matchingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<P2PLendingAuction> auctionQueue = new LinkedBlockingQueue<>();
    private final PriorityBlockingQueue<P2PLoanRequest> priorityLoanQueue = 
        new PriorityBlockingQueue<>(500, Comparator.comparing(P2PLoanRequest::getPriorityScore).reversed());
    
    public P2PLendingMatchEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            P2PLendingService p2pLendingService,
            P2PLendingMatchingService matchingService,
            P2PLendingRiskService riskService,
            P2PLendingComplianceService complianceService,
            P2PLendingNotificationService notificationService,
            P2PLendingAuctionService auctionService,
            SecurityContextHolder securityContextHolder) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.p2pLendingService = p2pLendingService;
        this.matchingService = matchingService;
        this.riskService = riskService;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
        this.auctionService = auctionService;
        this.securityContextHolder = securityContextHolder;
    }
    
    @PostConstruct
    public void init() {
        initializeCircuitBreaker();
        initializeRetry();
        initializeMetrics();
        startMatchingProcessor();
        startAuctionProcessor();
        startMatchTimeoutMonitor();
        startRateLimitReset();
        logger.info("P2PLendingMatchEventConsumer initialized successfully");
    }
    
    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        matchingExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
            if (!matchingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                matchingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            matchingExecutor.shutdownNow();
        }
        logger.info("P2PLendingMatchEventConsumer cleanup completed");
    }
    
    private void initializeCircuitBreaker() {
        circuitBreaker = CircuitBreaker.of("p2p-lending-match-circuit-breaker",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build());
    }
    
    private void initializeRetry() {
        retryConfig = Retry.of("p2p-lending-match-retry",
            RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(1000))
                .exponentialBackoffMultiplier(2.0)
                .build());
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("p2p_lending_match_messages_processed_total")
            .description("Total P2P lending match messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("p2p_lending_match_messages_failed_total")
            .description("Total P2P lending match messages failed")
            .register(meterRegistry);
            
        loansRequestedCounter = Counter.builder("p2p_lending_loans_requested_total")
            .description("Total P2P loan requests")
            .register(meterRegistry);
            
        investmentOffersCounter = Counter.builder("p2p_lending_investment_offers_total")
            .description("Total P2P investment offers")
            .register(meterRegistry);
            
        matchesCreatedCounter = Counter.builder("p2p_lending_matches_created_total")
            .description("Total P2P lending matches created")
            .register(meterRegistry);
            
        matchesCompletedCounter = Counter.builder("p2p_lending_matches_completed_total")
            .description("Total P2P lending matches completed")
            .register(meterRegistry);
            
        matchesExpiredCounter = Counter.builder("p2p_lending_matches_expired_total")
            .description("Total P2P lending matches expired")
            .register(meterRegistry);
            
        auctionsStartedCounter = Counter.builder("p2p_lending_auctions_started_total")
            .description("Total P2P lending auctions started")
            .register(meterRegistry);
            
        bidsReceivedCounter = Counter.builder("p2p_lending_bids_received_total")
            .description("Total P2P lending bids received")
            .register(meterRegistry);
            
        complianceFailuresCounter = Counter.builder("p2p_lending_compliance_failures_total")
            .description("Total P2P lending compliance failures")
            .register(meterRegistry);
        
        messageProcessingTimer = Timer.builder("p2p_lending_match_message_processing_duration")
            .description("P2P lending match message processing duration")
            .register(meterRegistry);
            
        matchingProcessingTimer = Timer.builder("p2p_lending_matching_processing_duration")
            .description("P2P lending matching processing duration")
            .register(meterRegistry);
            
        riskAssessmentTimer = Timer.builder("p2p_lending_risk_assessment_duration")
            .description("P2P lending risk assessment duration")
            .register(meterRegistry);
            
        complianceCheckTimer = Timer.builder("p2p_lending_compliance_check_duration")
            .description("P2P lending compliance check duration")
            .register(meterRegistry);
            
        auctionProcessingTimer = Timer.builder("p2p_lending_auction_processing_duration")
            .description("P2P lending auction processing duration")
            .register(meterRegistry);
        
        Gauge.builder("p2p_lending_total_loan_requests")
            .description("Total P2P loan requests")
            .register(meterRegistry, this, value -> totalLoanRequests.get());
            
        Gauge.builder("p2p_lending_total_investment_offers")
            .description("Total P2P investment offers")
            .register(meterRegistry, this, value -> totalInvestmentOffers.get());
            
        Gauge.builder("p2p_lending_total_matches")
            .description("Total P2P lending matches")
            .register(meterRegistry, this, value -> totalMatches.get());
            
        Gauge.builder("p2p_lending_total_loan_amount")
            .description("Total P2P loan amount")
            .register(meterRegistry, this, value -> totalLoanAmount.get());
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processP2PLendingMatch(@Payload String message,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                     Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            
            logger.info("Processing P2P lending match message: topic={}, partition={}, offset={}", 
                topic, partition, offset);
            
            if (!isWithinRateLimit()) {
                logger.warn("Rate limit exceeded, requeueing message");
                dlqService.sendToDlq(DLQ_TOPIC, message, "Rate limit exceeded", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = circuitBreaker.executeSupplier(() ->
                retryConfig.executeSupplier(() -> {
                    return executeProcessingStep(eventType, messageNode, requestId);
                })
            );
            
            if (processed) {
                messagesProcessedCounter.increment();
                metricsService.recordCustomMetric("p2p_lending_match_processed", 1.0, 
                    Map.of("eventType", eventType, "requestId", requestId));
                acknowledgment.acknowledge();
                logger.info("Successfully processed P2P lending match message: eventType={}, requestId={}", 
                    eventType, requestId);
            } else {
                throw new RuntimeException("Failed to process P2P lending match message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing P2P lending match message", e);
            messagesFailedCounter.increment();
            
            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("Failed to send message to DLQ", dlqException);
            }
        } finally {
            sample.stop(messageProcessingTimer);
            MDC.clear();
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "LOAN_REQUEST_SUBMITTED":
                return processLoanRequestSubmitted(messageNode, requestId);
            case "INVESTMENT_OFFER_CREATED":
                return processInvestmentOfferCreated(messageNode, requestId);
            case "MATCHING_REQUEST":
                return processMatchingRequest(messageNode, requestId);
            case "MATCH_ACCEPTANCE":
                return processMatchAcceptance(messageNode, requestId);
            case "MATCH_REJECTION":
                return processMatchRejection(messageNode, requestId);
            case "AUCTION_START_REQUEST":
                return processAuctionStartRequest(messageNode, requestId);
            case "BID_SUBMISSION":
                return processBidSubmission(messageNode, requestId);
            case "AUCTION_COMPLETION":
                return processAuctionCompletion(messageNode, requestId);
            case "RISK_ASSESSMENT_REQUEST":
                return processRiskAssessmentRequest(messageNode, requestId);
            case "COMPLIANCE_VERIFICATION":
                return processComplianceVerification(messageNode, requestId);
            case "LOAN_FUNDING_INITIATION":
                return processLoanFundingInitiation(messageNode, requestId);
            case "REPAYMENT_SCHEDULE_CREATION":
                return processRepaymentScheduleCreation(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processLoanRequestSubmitted(JsonNode messageNode, String requestId) {
        try {
            totalLoanRequests.incrementAndGet();
            
            String borrowerId = messageNode.path("borrowerId").asText();
            String loanAmount = messageNode.path("loanAmount").asText();
            String interestRate = messageNode.path("interestRate").asText();
            String loanTerm = messageNode.path("loanTerm").asText();
            String purpose = messageNode.path("purpose").asText();
            String creditScore = messageNode.path("creditScore").asText();
            String collateralType = messageNode.path("collateralType").asText();
            String urgencyLevel = messageNode.path("urgencyLevel").asText();
            
            BigDecimal amount = new BigDecimal(loanAmount);
            BigDecimal rate = new BigDecimal(interestRate);
            int termMonths = Integer.parseInt(loanTerm);
            int score = Integer.parseInt(creditScore);
            
            if (!validateLoanRequest(amount, rate, score)) {
                logger.warn("Invalid loan request parameters");
                return false;
            }
            
            P2PLoanRequest loanRequest = P2PLoanRequest.builder()
                .id(UUID.randomUUID().toString())
                .borrowerId(borrowerId)
                .loanAmount(amount)
                .interestRate(rate)
                .loanTermMonths(termMonths)
                .purpose(purpose)
                .creditScore(score)
                .collateralType(collateralType)
                .urgencyLevel(urgencyLevel)
                .status("PENDING_REVIEW")
                .priorityScore(calculateLoanPriorityScore(amount, urgencyLevel, score))
                .submittedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(matchingTimeoutHours))
                .requestId(requestId)
                .build();
            
            loanRequests.put(loanRequest.getId(), loanRequest);
            
            if ("HIGH".equals(urgencyLevel) || "URGENT".equals(urgencyLevel)) {
                priorityLoanQueue.offer(loanRequest);
            }
            
            loansRequestedCounter.increment();
            totalLoanAmount.addAndGet(amount.longValue());
            
            logger.info("Processed loan request: id={}, borrowerId={}, amount={}, rate={}, urgency={}", 
                loanRequest.getId(), borrowerId, amount, rate, urgencyLevel);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing loan request", e);
            return false;
        }
    }
    
    private boolean processInvestmentOfferCreated(JsonNode messageNode, String requestId) {
        try {
            totalInvestmentOffers.incrementAndGet();
            
            String investorId = messageNode.path("investorId").asText();
            String investmentAmount = messageNode.path("investmentAmount").asText();
            String minInterestRate = messageNode.path("minInterestRate").asText();
            String maxRiskLevel = messageNode.path("maxRiskLevel").asText();
            String preferredLoanTerms = messageNode.path("preferredLoanTerms").asText();
            String minCreditScore = messageNode.path("minCreditScore").asText();
            JsonNode preferredCategories = messageNode.path("preferredCategories");
            String autoInvest = messageNode.path("autoInvest").asText();
            
            BigDecimal amount = new BigDecimal(investmentAmount);
            BigDecimal minRate = new BigDecimal(minInterestRate);
            int minScore = Integer.parseInt(minCreditScore);
            
            P2PInvestmentOffer investmentOffer = P2PInvestmentOffer.builder()
                .id(UUID.randomUUID().toString())
                .investorId(investorId)
                .investmentAmount(amount)
                .minInterestRate(minRate)
                .maxRiskLevel(maxRiskLevel)
                .preferredLoanTerms(preferredLoanTerms)
                .minCreditScore(minScore)
                .preferredCategories(extractStringList(preferredCategories))
                .autoInvest(Boolean.parseBoolean(autoInvest))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(matchingTimeoutHours * 2))
                .requestId(requestId)
                .build();
            
            investmentOffers.put(investmentOffer.getId(), investmentOffer);
            investmentOffersCounter.increment();
            
            if (investmentOffer.isAutoInvest()) {
                P2PLendingMatchJob matchJob = P2PLendingMatchJob.builder()
                    .id(UUID.randomUUID().toString())
                    .investmentOfferId(investmentOffer.getId())
                    .jobType("AUTO_MATCH")
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
                
                matchingQueue.offer(matchJob);
            }
            
            logger.info("Processed investment offer: id={}, investorId={}, amount={}, minRate={}, autoInvest={}", 
                investmentOffer.getId(), investorId, amount, minRate, investmentOffer.isAutoInvest());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing investment offer", e);
            return false;
        }
    }
    
    private boolean processMatchingRequest(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String loanRequestId = messageNode.path("loanRequestId").asText();
            String matchingStrategy = messageNode.path("matchingStrategy").asText();
            JsonNode matchingCriteria = messageNode.path("matchingCriteria");
            
            P2PLoanRequest loanRequest = loanRequests.get(loanRequestId);
            if (loanRequest == null) {
                loanRequest = p2pLendingService.getLoanRequest(loanRequestId);
            }
            
            if (loanRequest == null) {
                logger.warn("Loan request not found: {}", loanRequestId);
                return false;
            }
            
            List<P2PInvestmentOffer> matchingOffers = matchingService.findMatchingInvestmentOffers(
                loanRequest, matchingStrategy, matchingCriteria);
            
            for (P2PInvestmentOffer offer : matchingOffers) {
                P2PLendingMatch match = P2PLendingMatch.builder()
                    .id(UUID.randomUUID().toString())
                    .loanRequestId(loanRequestId)
                    .investmentOfferId(offer.getId())
                    .borrowerId(loanRequest.getBorrowerId())
                    .investorId(offer.getInvestorId())
                    .matchScore(calculateMatchScore(loanRequest, offer))
                    .status("PENDING_ACCEPTANCE")
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .requestId(requestId)
                    .build();
                
                matches.put(match.getId(), match);
                totalMatches.incrementAndGet();
                matchesCreatedCounter.increment();
                
                notificationService.sendMatchNotification(match);
            }
            
            logger.info("Processed matching request: loanRequestId={}, strategy={}, matchCount={}", 
                loanRequestId, matchingStrategy, matchingOffers.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing matching request", e);
            return false;
        } finally {
            sample.stop(matchingProcessingTimer);
        }
    }
    
    private boolean processMatchAcceptance(JsonNode messageNode, String requestId) {
        try {
            String matchId = messageNode.path("matchId").asText();
            String acceptedBy = messageNode.path("acceptedBy").asText();
            String acceptanceTerms = messageNode.path("acceptanceTerms").asText();
            
            P2PLendingMatch match = matches.get(matchId);
            if (match == null) {
                match = p2pLendingService.getMatch(matchId);
            }
            
            if (match == null) {
                logger.warn("Match not found: {}", matchId);
                return false;
            }
            
            match.setStatus("ACCEPTED");
            match.setAcceptedBy(acceptedBy);
            match.setAcceptedAt(LocalDateTime.now());
            match.setAcceptanceTerms(acceptanceTerms);
            
            p2pLendingService.updateMatch(match);
            matchesCompletedCounter.increment();
            
            P2PLoanContract contract = p2pLendingService.createLoanContract(match);
            
            notificationService.sendMatchAcceptanceNotification(match, contract);
            
            logger.info("Processed match acceptance: matchId={}, acceptedBy={}, contractId={}", 
                matchId, acceptedBy, contract.getId());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing match acceptance", e);
            return false;
        }
    }
    
    private boolean processMatchRejection(JsonNode messageNode, String requestId) {
        try {
            String matchId = messageNode.path("matchId").asText();
            String rejectedBy = messageNode.path("rejectedBy").asText();
            String rejectionReason = messageNode.path("rejectionReason").asText();
            
            P2PLendingMatch match = matches.get(matchId);
            if (match != null) {
                match.setStatus("REJECTED");
                match.setRejectedBy(rejectedBy);
                match.setRejectedAt(LocalDateTime.now());
                match.setRejectionReason(rejectionReason);
                
                p2pLendingService.updateMatch(match);
                matches.remove(matchId);
                
                notificationService.sendMatchRejectionNotification(match);
            }
            
            logger.info("Processed match rejection: matchId={}, rejectedBy={}, reason={}", 
                matchId, rejectedBy, rejectionReason);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing match rejection", e);
            return false;
        }
    }
    
    private boolean processAuctionStartRequest(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String loanRequestId = messageNode.path("loanRequestId").asText();
            String auctionDuration = messageNode.path("auctionDuration").asText();
            String reserveRate = messageNode.path("reserveRate").asText();
            String minBidIncrement = messageNode.path("minBidIncrement").asText();
            
            P2PLoanRequest loanRequest = loanRequests.get(loanRequestId);
            if (loanRequest == null) {
                logger.warn("Loan request not found for auction: {}", loanRequestId);
                return false;
            }
            
            int durationHours = Integer.parseInt(auctionDuration);
            BigDecimal reserve = new BigDecimal(reserveRate);
            BigDecimal increment = new BigDecimal(minBidIncrement);
            
            P2PLendingAuction auction = P2PLendingAuction.builder()
                .id(UUID.randomUUID().toString())
                .loanRequestId(loanRequestId)
                .borrowerId(loanRequest.getBorrowerId())
                .loanAmount(loanRequest.getLoanAmount())
                .reserveRate(reserve)
                .minBidIncrement(increment)
                .currentLowestRate(loanRequest.getInterestRate())
                .auctionDurationHours(durationHours)
                .status("ACTIVE")
                .startedAt(LocalDateTime.now())
                .endsAt(LocalDateTime.now().plusHours(durationHours))
                .requestId(requestId)
                .build();
            
            auctionQueue.offer(auction);
            auctionsStartedCounter.increment();
            
            logger.info("Started P2P lending auction: id={}, loanRequestId={}, duration={}h, reserveRate={}", 
                auction.getId(), loanRequestId, durationHours, reserve);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error starting auction", e);
            return false;
        } finally {
            sample.stop(auctionProcessingTimer);
        }
    }
    
    private boolean processBidSubmission(JsonNode messageNode, String requestId) {
        try {
            String auctionId = messageNode.path("auctionId").asText();
            String investorId = messageNode.path("investorId").asText();
            String bidRate = messageNode.path("bidRate").asText();
            String bidAmount = messageNode.path("bidAmount").asText();
            
            BigDecimal rate = new BigDecimal(bidRate);
            BigDecimal amount = new BigDecimal(bidAmount);
            
            P2PAuctionBid bid = P2PAuctionBid.builder()
                .id(UUID.randomUUID().toString())
                .auctionId(auctionId)
                .investorId(investorId)
                .bidRate(rate)
                .bidAmount(amount)
                .bidTime(LocalDateTime.now())
                .status("ACTIVE")
                .requestId(requestId)
                .build();
            
            boolean bidAccepted = auctionService.submitBid(bid);
            if (bidAccepted) {
                bidsReceivedCounter.increment();
                notificationService.sendBidNotification(bid);
            }
            
            logger.info("Processed bid submission: auctionId={}, investorId={}, rate={}, amount={}, accepted={}", 
                auctionId, investorId, rate, amount, bidAccepted);
            
            return bidAccepted;
            
        } catch (Exception e) {
            logger.error("Error processing bid submission", e);
            return false;
        }
    }
    
    private boolean processAuctionCompletion(JsonNode messageNode, String requestId) {
        try {
            String auctionId = messageNode.path("auctionId").asText();
            String completionReason = messageNode.path("completionReason").asText();
            
            P2PLendingAuction auction = auctionService.getAuction(auctionId);
            if (auction == null) {
                logger.warn("Auction not found: {}", auctionId);
                return false;
            }
            
            P2PAuctionResult result = auctionService.completeAuction(auction, completionReason);
            
            if (result.isSuccessful()) {
                P2PLendingMatch match = createMatchFromAuctionResult(result, requestId);
                matches.put(match.getId(), match);
                totalMatches.incrementAndGet();
                matchesCreatedCounter.increment();
                
                notificationService.sendAuctionCompletionNotification(result);
            }
            
            logger.info("Completed auction: id={}, successful={}, winningRate={}", 
                auctionId, result.isSuccessful(), result.getWinningRate());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing auction completion", e);
            return false;
        }
    }
    
    private boolean processRiskAssessmentRequest(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String loanRequestId = messageNode.path("loanRequestId").asText();
            JsonNode riskFactors = messageNode.path("riskFactors");
            
            P2PLoanRequest loanRequest = loanRequests.get(loanRequestId);
            if (loanRequest == null) {
                loanRequest = p2pLendingService.getLoanRequest(loanRequestId);
            }
            
            if (loanRequest == null) {
                logger.warn("Loan request not found for risk assessment: {}", loanRequestId);
                return false;
            }
            
            P2PRiskAssessment riskAssessment = riskService.assessRisk(loanRequest, riskFactors);
            
            loanRequest.setRiskScore(riskAssessment.getRiskScore());
            loanRequest.setRiskLevel(riskAssessment.getRiskLevel());
            loanRequest.setRiskFactors(riskAssessment.getRiskFactors());
            loanRequest.setRiskAssessedAt(LocalDateTime.now());
            
            p2pLendingService.updateLoanRequest(loanRequest);
            
            logger.info("Processed risk assessment: loanRequestId={}, riskLevel={}, score={}", 
                loanRequestId, riskAssessment.getRiskLevel(), riskAssessment.getRiskScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing risk assessment", e);
            return false;
        } finally {
            sample.stop(riskAssessmentTimer);
        }
    }
    
    private boolean processComplianceVerification(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String loanRequestId = messageNode.path("loanRequestId").asText();
            String investmentOfferId = messageNode.path("investmentOfferId").asText();
            JsonNode complianceChecks = messageNode.path("complianceChecks");
            
            P2PComplianceResult result = complianceService.verifyCompliance(
                loanRequestId, investmentOfferId, complianceChecks);
            
            if (!result.isCompliant()) {
                complianceFailuresCounter.increment();
                logger.warn("Compliance failure: loanRequestId={}, investmentOfferId={}, violations={}", 
                    loanRequestId, investmentOfferId, result.getViolations());
            }
            
            logger.info("Processed compliance verification: loanRequestId={}, investmentOfferId={}, compliant={}", 
                loanRequestId, investmentOfferId, result.isCompliant());
            
            return result.isCompliant();
            
        } catch (Exception e) {
            logger.error("Error processing compliance verification", e);
            return false;
        } finally {
            sample.stop(complianceCheckTimer);
        }
    }
    
    private boolean processLoanFundingInitiation(JsonNode messageNode, String requestId) {
        try {
            String matchId = messageNode.path("matchId").asText();
            String fundingMethod = messageNode.path("fundingMethod").asText();
            String fundingAccountId = messageNode.path("fundingAccountId").asText();
            
            P2PLendingMatch match = matches.get(matchId);
            if (match == null) {
                match = p2pLendingService.getMatch(matchId);
            }
            
            if (match == null) {
                logger.warn("Match not found for funding: {}", matchId);
                return false;
            }
            
            P2PLoanFunding funding = p2pLendingService.initiateFunding(match, fundingMethod, fundingAccountId);
            
            match.setFundingId(funding.getId());
            match.setStatus("FUNDING_IN_PROGRESS");
            p2pLendingService.updateMatch(match);
            
            notificationService.sendFundingInitiationNotification(match, funding);
            
            logger.info("Initiated loan funding: matchId={}, fundingId={}, method={}", 
                matchId, funding.getId(), fundingMethod);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error initiating loan funding", e);
            return false;
        }
    }
    
    private boolean processRepaymentScheduleCreation(JsonNode messageNode, String requestId) {
        try {
            String matchId = messageNode.path("matchId").asText();
            String repaymentFrequency = messageNode.path("repaymentFrequency").asText();
            String firstPaymentDate = messageNode.path("firstPaymentDate").asText();
            
            P2PLendingMatch match = matches.get(matchId);
            if (match == null) {
                match = p2pLendingService.getMatch(matchId);
            }
            
            if (match == null) {
                logger.warn("Match not found for repayment schedule: {}", matchId);
                return false;
            }
            
            LocalDateTime firstPayment = LocalDateTime.parse(firstPaymentDate);
            
            P2PRepaymentSchedule schedule = p2pLendingService.createRepaymentSchedule(
                match, repaymentFrequency, firstPayment);
            
            match.setRepaymentScheduleId(schedule.getId());
            p2pLendingService.updateMatch(match);
            
            notificationService.sendRepaymentScheduleNotification(match, schedule);
            
            logger.info("Created repayment schedule: matchId={}, scheduleId={}, frequency={}, firstPayment={}", 
                matchId, schedule.getId(), repaymentFrequency, firstPayment);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error creating repayment schedule", e);
            return false;
        }
    }
    
    private void startMatchingProcessor() {
        matchingExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    P2PLendingMatchJob matchJob = matchingQueue.take();
                    processMatchingJob(matchJob);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in matching processor", e);
                }
            }
        });
    }
    
    private void processMatchingJob(P2PLendingMatchJob matchJob) {
        try {
            matchJob.setStatus("PROCESSING");
            matchJob.setProcessingStarted(LocalDateTime.now());
            
            matchingService.processMatchingJob(matchJob);
            
            matchJob.setStatus("COMPLETED");
            matchJob.setProcessingCompleted(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error processing matching job: {}", matchJob.getId(), e);
            matchJob.setStatus("FAILED");
            matchJob.setProcessingCompleted(LocalDateTime.now());
        }
    }
    
    private void startAuctionProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    P2PLendingAuction auction = auctionQueue.take();
                    auctionService.processAuction(auction);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in auction processor", e);
                }
            }
        });
    }
    
    private void startMatchTimeoutMonitor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                matches.values().stream()
                    .filter(match -> "PENDING_ACCEPTANCE".equals(match.getStatus()) && 
                                   match.getExpiresAt().isBefore(now))
                    .forEach(match -> {
                        match.setStatus("EXPIRED");
                        matches.remove(match.getId());
                        matchesExpiredCounter.increment();
                        logger.info("Expired match: {}", match.getId());
                    });
            } catch (Exception e) {
                logger.error("Error in match timeout monitor", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    private void startRateLimitReset() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            currentGlobalRate.set(0);
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private boolean isWithinRateLimit() {
        return currentGlobalRate.incrementAndGet() <= globalRateLimit;
    }
    
    private boolean validateLoanRequest(BigDecimal amount, BigDecimal rate, int creditScore) {
        return amount.compareTo(minLoanAmount) >= 0 && 
               amount.compareTo(maxLoanAmount) <= 0 &&
               rate.compareTo(maxInterestRate) <= 0 &&
               creditScore >= minCreditScore;
    }
    
    private int calculateLoanPriorityScore(BigDecimal amount, String urgencyLevel, int creditScore) {
        int score = 50;
        
        switch (urgencyLevel.toUpperCase()) {
            case "URGENT": score += 40; break;
            case "HIGH": score += 30; break;
            case "MEDIUM": score += 20; break;
            case "LOW": score += 10; break;
        }
        
        if (creditScore >= 750) score += 20;
        else if (creditScore >= 700) score += 15;
        else if (creditScore >= 650) score += 10;
        
        if (amount.compareTo(new BigDecimal("50000")) >= 0) score += 15;
        
        return score;
    }
    
    private int calculateMatchScore(P2PLoanRequest loanRequest, P2PInvestmentOffer offer) {
        int score = 0;
        
        if (loanRequest.getInterestRate().compareTo(offer.getMinInterestRate()) >= 0) score += 40;
        if (loanRequest.getCreditScore() >= offer.getMinCreditScore()) score += 30;
        if (offer.getPreferredCategories().contains(loanRequest.getPurpose())) score += 20;
        if (loanRequest.getLoanAmount().compareTo(offer.getInvestmentAmount()) <= 0) score += 10;
        
        return score;
    }
    
    private P2PLendingMatch createMatchFromAuctionResult(P2PAuctionResult result, String requestId) {
        return P2PLendingMatch.builder()
            .id(UUID.randomUUID().toString())
            .loanRequestId(result.getLoanRequestId())
            .investmentOfferId(result.getWinningBidderId())
            .borrowerId(result.getBorrowerId())
            .investorId(result.getWinningBidderId())
            .matchScore(100)
            .finalInterestRate(result.getWinningRate())
            .status("AUCTION_WON")
            .createdAt(LocalDateTime.now())
            .requestId(requestId)
            .build();
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}