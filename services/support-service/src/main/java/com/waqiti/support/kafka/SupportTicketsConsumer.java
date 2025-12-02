package com.waqiti.support.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Configuration
@EnableScheduling
public class SupportTicketsConsumer {
    
    private final SecureRandom secureRandom = new SecureRandom();

    public SupportTicketsConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry,
                                 ApplicationEventPublisher eventPublisher,
                                 RedisTemplate<String, String> redisTemplate,
                                 CacheManager cacheManager) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }
    private static final Logger logger = LoggerFactory.getLogger(SupportTicketsConsumer.class);
    
    private static final String TOPIC = "support-tickets";
    private static final String DLQ_TOPIC = "support-tickets-dlq";
    private static final String CONSUMER_GROUP = "support-tickets-consumer-group";
    private static final String TICKET_STATE_PREFIX = "ticket:state:";
    private static final String AGENT_ASSIGNMENT_PREFIX = "agent:assignment:";
    private static final String SLA_TRACKING_PREFIX = "sla:tracking:";
    private static final String KNOWLEDGE_BASE_PREFIX = "kb:article:";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    
    @Value("${support.ticket.priority.levels:5}")
    private int priorityLevels;
    
    @Value("${support.ticket.auto-assign.enabled:true}")
    private boolean autoAssignEnabled;
    
    @Value("${support.ticket.ai-suggestions.enabled:true}")
    private boolean aiSuggestionsEnabled;
    
    @Value("${support.ticket.escalation.enabled:true}")
    private boolean escalationEnabled;
    
    @Value("${support.ticket.sentiment-analysis.enabled:true}")
    private boolean sentimentAnalysisEnabled;
    
    @Value("${support.ticket.csat.survey.enabled:true}")
    private boolean csatSurveyEnabled;
    
    @Value("${support.ticket.max-attachments:10}")
    private int maxAttachments;
    
    @Value("${support.ticket.response-time.target.minutes:30}")
    private int responseTimeTargetMinutes;
    
    @Value("${support.ticket.resolution-time.target.hours:24}")
    private int resolutionTimeTargetHours;
    
    @Value("${support.ticket.auto-close.inactive.days:7}")
    private int autoCloseInactiveDays;
    
    @Value("${support.agent.max-concurrent-tickets:10}")
    private int maxConcurrentTicketsPerAgent;
    
    private final Map<String, TicketState> ticketStates = new ConcurrentHashMap<>();
    private final Map<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final Map<String, SlaTracking> slaTrackings = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeBaseCache> knowledgeBaseCache = new ConcurrentHashMap<>();
    private final Map<String, EscalationQueue> escalationQueues = new ConcurrentHashMap<>();
    private final Map<String, CustomerHistory> customerHistories = new ConcurrentHashMap<>();
    private final Map<String, TicketMetrics> ticketMetrics = new ConcurrentHashMap<>();
    private final PriorityQueue<TicketAssignment> assignmentQueue = new PriorityQueue<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(10);
    private final ExecutorService ticketProcessingExecutor = Executors.newFixedThreadPool(15);
    private final ExecutorService assignmentExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService escalationExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService analyticsExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService aiSuggestionExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService notificationExecutor = Executors.newFixedThreadPool(5);
    
    private Counter ticketsCreatedCounter;
    private Counter ticketsUpdatedCounter;
    private Counter ticketsResolvedCounter;
    private Counter ticketsEscalatedCounter;
    private Counter ticketsReopenedCounter;
    private Counter agentAssignmentsCounter;
    private Counter customerResponsesCounter;
    private Counter agentResponsesCounter;
    private Counter slaBreachesCounter;
    private Counter csatSurveysCounter;
    private Counter errorCounter;
    private Timer ticketProcessingTimer;
    private Timer responseTimeTimer;
    private Timer resolutionTimeTimer;
    private Timer assignmentTimeTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        initializeBackgroundTasks();
        loadTicketStates();
        loadAgentStates();
        initializeKnowledgeBase();
        logger.info("SupportTicketsConsumer initialized with comprehensive support capabilities");
    }
    
    private void initializeMetrics() {
        ticketsCreatedCounter = Counter.builder("support.tickets.created")
                .description("Total number of support tickets created")
                .register(meterRegistry);
                
        ticketsUpdatedCounter = Counter.builder("support.tickets.updated")
                .description("Total number of support tickets updated")
                .register(meterRegistry);
                
        ticketsResolvedCounter = Counter.builder("support.tickets.resolved")
                .description("Total number of support tickets resolved")
                .register(meterRegistry);
                
        ticketsEscalatedCounter = Counter.builder("support.tickets.escalated")
                .description("Total number of support tickets escalated")
                .register(meterRegistry);
                
        ticketsReopenedCounter = Counter.builder("support.tickets.reopened")
                .description("Total number of support tickets reopened")
                .register(meterRegistry);
                
        agentAssignmentsCounter = Counter.builder("support.agent.assignments")
                .description("Total number of agent assignments")
                .register(meterRegistry);
                
        customerResponsesCounter = Counter.builder("support.customer.responses")
                .description("Total number of customer responses")
                .register(meterRegistry);
                
        agentResponsesCounter = Counter.builder("support.agent.responses")
                .description("Total number of agent responses")
                .register(meterRegistry);
                
        slaBreachesCounter = Counter.builder("support.sla.breaches")
                .description("Total number of SLA breaches")
                .register(meterRegistry);
                
        csatSurveysCounter = Counter.builder("support.csat.surveys")
                .description("Total number of CSAT surveys completed")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("support.tickets.errors")
                .description("Total number of ticket processing errors")
                .register(meterRegistry);
                
        ticketProcessingTimer = Timer.builder("support.ticket.processing.time")
                .description("Time taken to process support tickets")
                .register(meterRegistry);
                
        responseTimeTimer = Timer.builder("support.response.time")
                .description("Time to first response")
                .register(meterRegistry);
                
        resolutionTimeTimer = Timer.builder("support.resolution.time")
                .description("Time to resolution")
                .register(meterRegistry);
                
        assignmentTimeTimer = Timer.builder("support.assignment.time")
                .description("Time to assign ticket to agent")
                .register(meterRegistry);
                
        Gauge.builder("support.tickets.open", ticketStates, map -> 
                (int) map.values().stream().filter(t -> "open".equals(t.status)).count())
                .description("Number of open tickets")
                .register(meterRegistry);
                
        Gauge.builder("support.tickets.pending", ticketStates, map -> 
                (int) map.values().stream().filter(t -> "pending".equals(t.status)).count())
                .description("Number of pending tickets")
                .register(meterRegistry);
                
        Gauge.builder("support.agents.available", agentStates, map -> 
                (int) map.values().stream().filter(a -> a.isAvailable()).count())
                .description("Number of available agents")
                .register(meterRegistry);
                
        Gauge.builder("support.assignment.queue.size", assignmentQueue, Queue::size)
                .description("Number of tickets in assignment queue")
                .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
                
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("support-tickets-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("support-tickets-retry");
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutor.scheduleWithFixedDelay(this::processAssignmentQueue, 0, 5, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::checkSlaCompliance, 0, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::processEscalations, 0, 2, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::updateAgentAvailability, 0, 30, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::autoCloseInactiveTickets, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::generateTicketAnalytics, 0, 15, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::refreshKnowledgeBase, 0, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::sendCsatSurveys, 0, 10, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::optimizeAgentRouting, 0, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::archiveResolvedTickets, 0, 24, TimeUnit.HOURS);
    }
    
    private void loadTicketStates() {
        try {
            Set<String> ticketKeys = redisTemplate.keys(TICKET_STATE_PREFIX + "*");
            if (ticketKeys != null && !ticketKeys.isEmpty()) {
                for (String key : ticketKeys) {
                    String stateJson = redisTemplate.opsForValue().get(key);
                    if (stateJson != null) {
                        TicketState state = objectMapper.readValue(stateJson, TicketState.class);
                        ticketStates.put(state.ticketId, state);
                    }
                }
                logger.info("Loaded {} ticket states from Redis", ticketStates.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load ticket states", e);
        }
    }
    
    private void loadAgentStates() {
        try {
            Set<String> agentKeys = redisTemplate.keys(AGENT_ASSIGNMENT_PREFIX + "*");
            if (agentKeys != null && !agentKeys.isEmpty()) {
                for (String key : agentKeys) {
                    String stateJson = redisTemplate.opsForValue().get(key);
                    if (stateJson != null) {
                        AgentState state = objectMapper.readValue(stateJson, AgentState.class);
                        agentStates.put(state.agentId, state);
                    }
                }
                logger.info("Loaded {} agent states from Redis", agentStates.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load agent states", e);
        }
    }
    
    private void initializeKnowledgeBase() {
        logger.info("Initializing knowledge base cache");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processSupportTicket(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                    Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        MDC.put("ticket.topic", topic);
        MDC.put("ticket.partition", String.valueOf(partition));
        MDC.put("ticket.offset", String.valueOf(offset));
        
        try {
            logger.debug("Processing support ticket from partition {} offset {}", partition, offset);
            
            Map<String, Object> ticketData = objectMapper.readValue(message, Map.class);
            String eventType = (String) ticketData.get("eventType");
            String ticketId = (String) ticketData.get("ticketId");
            
            MDC.put("ticket.id", ticketId);
            MDC.put("event.type", eventType);
            
            Supplier<Boolean> ticketProcessor = () -> {
                try {
                    switch (eventType) {
                        case "TICKET_CREATED":
                            return handleTicketCreated(ticketData);
                        case "TICKET_UPDATED":
                            return handleTicketUpdated(ticketData);
                        case "TICKET_ASSIGNED":
                            return handleTicketAssigned(ticketData);
                        case "TICKET_ESCALATED":
                            return handleTicketEscalated(ticketData);
                        case "TICKET_RESOLVED":
                            return handleTicketResolved(ticketData);
                        case "TICKET_REOPENED":
                            return handleTicketReopened(ticketData);
                        case "TICKET_CLOSED":
                            return handleTicketClosed(ticketData);
                        case "CUSTOMER_RESPONSE":
                            return handleCustomerResponse(ticketData);
                        case "AGENT_RESPONSE":
                            return handleAgentResponse(ticketData);
                        case "ATTACHMENT_ADDED":
                            return handleAttachmentAdded(ticketData);
                        case "PRIORITY_CHANGED":
                            return handlePriorityChanged(ticketData);
                        case "CATEGORY_CHANGED":
                            return handleCategoryChanged(ticketData);
                        case "INTERNAL_NOTE_ADDED":
                            return handleInternalNoteAdded(ticketData);
                        case "SLA_WARNING":
                            return handleSlaWarning(ticketData);
                        case "SLA_BREACH":
                            return handleSlaBreach(ticketData);
                        case "CSAT_SURVEY_COMPLETED":
                            return handleCsatSurveyCompleted(ticketData);
                        case "KNOWLEDGE_BASE_LINKED":
                            return handleKnowledgeBaseLinked(ticketData);
                        default:
                            logger.warn("Unknown event type: {}", eventType);
                            return false;
                    }
                } catch (Exception e) {
                    logger.error("Error processing ticket", e);
                    errorCounter.increment();
                    return false;
                }
            };
            
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, ticketProcessor)).get();
            
            if (result) {
                acknowledgment.acknowledge();
                logger.debug("Support ticket processed successfully");
            } else {
                sendToDlq(message, "Processing failed");
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            logger.error("Failed to process support ticket", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage());
            acknowledgment.acknowledge();
        } finally {
            sample.stop(ticketProcessingTimer);
            MDC.clear();
        }
    }
    
    private boolean handleTicketCreated(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String customerId = (String) ticketData.get("customerId");
        String subject = (String) ticketData.get("subject");
        String description = (String) ticketData.get("description");
        String category = (String) ticketData.get("category");
        int priority = ((Number) ticketData.get("priority")).intValue();
        String channel = (String) ticketData.get("channel");
        Map<String, Object> metadata = (Map<String, Object>) ticketData.get("metadata");
        
        TicketState ticket = new TicketState(
                ticketId,
                customerId,
                subject,
                description,
                category,
                priority,
                channel,
                metadata
        );
        
        ticketStates.put(ticketId, ticket);
        
        loadCustomerHistory(customerId);
        
        if (sentimentAnalysisEnabled) {
            SentimentScore sentiment = analyzeSentiment(description);
            ticket.setSentiment(sentiment);
            
            if (sentiment.isNegative() && sentiment.score < -0.7) {
                ticket.priority = Math.min(ticket.priority + 1, priorityLevels);
                logger.info("Increased priority due to negative sentiment for ticket: {}", ticketId);
            }
        }
        
        if (aiSuggestionsEnabled) {
            generateAiSuggestions(ticket);
        }
        
        findRelatedKnowledgeBaseArticles(ticket);
        
        initializeSlaTracking(ticket);
        
        if (autoAssignEnabled) {
            queueForAssignment(ticket);
        } else {
            notifyAgentsOfNewTicket(ticket);
        }
        
        persistTicketState(ticket);
        
        ticketsCreatedCounter.increment();
        return true;
    }
    
    private boolean handleTicketUpdated(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        Map<String, Object> updates = (Map<String, Object>) ticketData.get("updates");
        String updatedBy = (String) ticketData.get("updatedBy");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            logger.warn("Ticket not found: {}", ticketId);
            return false;
        }
        
        ticket.applyUpdates(updates);
        ticket.lastUpdatedBy = updatedBy;
        ticket.lastUpdatedAt = Instant.now();
        
        if (updates.containsKey("subject") || updates.containsKey("description")) {
            if (aiSuggestionsEnabled) {
                generateAiSuggestions(ticket);
            }
            findRelatedKnowledgeBaseArticles(ticket);
        }
        
        updateSlaTracking(ticket);
        persistTicketState(ticket);
        
        notifyTicketUpdate(ticket, updates);
        
        ticketsUpdatedCounter.increment();
        return true;
    }
    
    private boolean handleTicketAssigned(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String agentId = (String) ticketData.get("agentId");
        String assignedBy = (String) ticketData.get("assignedBy");
        boolean isReassignment = (boolean) ticketData.getOrDefault("isReassignment", false);
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        AgentState agent = agentStates.computeIfAbsent(agentId, k -> new AgentState(k));
        
        if (ticket.assignedAgentId != null && !isReassignment) {
            logger.warn("Ticket already assigned to agent: {}", ticket.assignedAgentId);
            return false;
        }
        
        if (agent.currentTickets.size() >= maxConcurrentTicketsPerAgent) {
            logger.warn("Agent {} has reached max concurrent tickets", agentId);
            return false;
        }
        
        if (ticket.assignedAgentId != null) {
            AgentState previousAgent = agentStates.get(ticket.assignedAgentId);
            if (previousAgent != null) {
                previousAgent.removeTicket(ticketId);
            }
        }
        
        ticket.assignedAgentId = agentId;
        ticket.assignedAt = Instant.now();
        ticket.status = "assigned";
        
        agent.addTicket(ticketId);
        
        persistTicketState(ticket);
        persistAgentState(agent);
        
        notifyAgentAssignment(ticket, agent);
        
        if (ticket.firstResponseAt == null) {
            scheduleFirstResponseReminder(ticket, agent);
        }
        
        agentAssignmentsCounter.increment();
        return true;
    }
    
    private boolean handleTicketEscalated(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String escalatedTo = (String) ticketData.get("escalatedTo");
        String reason = (String) ticketData.get("reason");
        int newPriority = ((Number) ticketData.get("newPriority")).intValue();
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.escalationLevel++;
        ticket.escalatedTo = escalatedTo;
        ticket.escalationReason = reason;
        ticket.priority = newPriority;
        ticket.status = "escalated";
        ticket.escalatedAt = Instant.now();
        
        EscalationQueue queue = escalationQueues.computeIfAbsent(escalatedTo, k -> new EscalationQueue());
        queue.addTicket(ticket);
        
        notifyEscalation(ticket, escalatedTo, reason);
        
        updateSlaTracking(ticket);
        persistTicketState(ticket);
        
        ticketsEscalatedCounter.increment();
        return true;
    }
    
    private boolean handleTicketResolved(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String resolvedBy = (String) ticketData.get("resolvedBy");
        String resolution = (String) ticketData.get("resolution");
        Map<String, Object> resolutionDetails = (Map<String, Object>) ticketData.get("resolutionDetails");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.status = "resolved";
        ticket.resolvedBy = resolvedBy;
        ticket.resolution = resolution;
        ticket.resolutionDetails = resolutionDetails;
        ticket.resolvedAt = Instant.now();
        
        Duration resolutionTime = Duration.between(ticket.createdAt, ticket.resolvedAt);
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(resolutionTimeTimer);
        
        if (ticket.assignedAgentId != null) {
            AgentState agent = agentStates.get(ticket.assignedAgentId);
            if (agent != null) {
                agent.removeTicket(ticketId);
                agent.incrementResolvedCount();
                agent.updateAverageResolutionTime(resolutionTime);
            }
        }
        
        updateTicketMetrics(ticket);
        
        if (csatSurveyEnabled) {
            scheduleCsatSurvey(ticket);
        }
        
        analyzeResolutionPatterns(ticket);
        
        persistTicketState(ticket);
        notifyTicketResolved(ticket);
        
        ticketsResolvedCounter.increment();
        return true;
    }
    
    private boolean handleTicketReopened(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String reopenedBy = (String) ticketData.get("reopenedBy");
        String reason = (String) ticketData.get("reason");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        if (!"resolved".equals(ticket.status) && !"closed".equals(ticket.status)) {
            logger.warn("Cannot reopen ticket in status: {}", ticket.status);
            return false;
        }
        
        ticket.status = "reopened";
        ticket.reopenedBy = reopenedBy;
        ticket.reopenReason = reason;
        ticket.reopenedAt = Instant.now();
        ticket.reopenCount++;
        
        ticket.priority = Math.min(ticket.priority + 1, priorityLevels);
        
        initializeSlaTracking(ticket);
        
        if (ticket.assignedAgentId != null) {
            AgentState agent = agentStates.get(ticket.assignedAgentId);
            if (agent != null && agent.isAvailable()) {
                agent.addTicket(ticketId);
                ticket.status = "assigned";
            } else {
                queueForAssignment(ticket);
            }
        } else {
            queueForAssignment(ticket);
        }
        
        analyzeReopenPatterns(ticket);
        
        persistTicketState(ticket);
        notifyTicketReopened(ticket);
        
        ticketsReopenedCounter.increment();
        return true;
    }
    
    private boolean handleTicketClosed(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String closedBy = (String) ticketData.get("closedBy");
        String closeReason = (String) ticketData.get("closeReason");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.status = "closed";
        ticket.closedBy = closedBy;
        ticket.closeReason = closeReason;
        ticket.closedAt = Instant.now();
        
        if (ticket.assignedAgentId != null) {
            AgentState agent = agentStates.get(ticket.assignedAgentId);
            if (agent != null) {
                agent.removeTicket(ticketId);
            }
        }
        
        finalizeTicketMetrics(ticket);
        archiveTicketConversation(ticket);
        
        persistTicketState(ticket);
        notifyTicketClosed(ticket);
        
        return true;
    }
    
    private boolean handleCustomerResponse(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String customerId = (String) ticketData.get("customerId");
        String message = (String) ticketData.get("message");
        List<String> attachments = (List<String>) ticketData.get("attachments");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.addCustomerResponse(new ResponseMessage(customerId, message, attachments));
        ticket.lastCustomerResponseAt = Instant.now();
        ticket.awaitingCustomerResponse = false;
        
        if (sentimentAnalysisEnabled) {
            SentimentScore sentiment = analyzeSentiment(message);
            updateTicketSentimentTrend(ticket, sentiment);
        }
        
        if (ticket.status.equals("pending_customer")) {
            ticket.status = "open";
        }
        
        if (ticket.assignedAgentId != null) {
            notifyAgentOfCustomerResponse(ticket, message);
        }
        
        updateSlaTracking(ticket);
        persistTicketState(ticket);
        
        customerResponsesCounter.increment();
        return true;
    }
    
    private boolean handleAgentResponse(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String agentId = (String) ticketData.get("agentId");
        String message = (String) ticketData.get("message");
        List<String> attachments = (List<String>) ticketData.get("attachments");
        boolean isPublic = (boolean) ticketData.getOrDefault("isPublic", true);
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.addAgentResponse(new ResponseMessage(agentId, message, attachments, isPublic));
        
        if (ticket.firstResponseAt == null) {
            ticket.firstResponseAt = Instant.now();
            Duration responseTime = Duration.between(ticket.createdAt, ticket.firstResponseAt);
            Timer.Sample sample = Timer.start(meterRegistry);
            sample.stop(responseTimeTimer);
        }
        
        ticket.lastAgentResponseAt = Instant.now();
        ticket.awaitingCustomerResponse = true;
        
        if (ticket.status.equals("open") || ticket.status.equals("assigned")) {
            ticket.status = "pending_customer";
        }
        
        AgentState agent = agentStates.get(agentId);
        if (agent != null) {
            agent.incrementResponseCount();
            agent.lastActivityAt = Instant.now();
        }
        
        evaluateResponseQuality(ticket, message);
        
        persistTicketState(ticket);
        notifyCustomerOfAgentResponse(ticket, message);
        
        agentResponsesCounter.increment();
        return true;
    }
    
    private boolean handleAttachmentAdded(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String attachmentId = (String) ticketData.get("attachmentId");
        String fileName = (String) ticketData.get("fileName");
        long fileSize = ((Number) ticketData.get("fileSize")).longValue();
        String fileType = (String) ticketData.get("fileType");
        String uploadedBy = (String) ticketData.get("uploadedBy");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        if (ticket.attachments.size() >= maxAttachments) {
            logger.warn("Max attachments reached for ticket: {}", ticketId);
            return false;
        }
        
        ticket.addAttachment(new TicketAttachment(attachmentId, fileName, fileSize, fileType, uploadedBy));
        
        if (shouldScanAttachment(fileType)) {
            scanAttachmentForIssues(attachmentId, ticketId);
        }
        
        persistTicketState(ticket);
        
        return true;
    }
    
    private boolean handlePriorityChanged(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        int oldPriority = ((Number) ticketData.get("oldPriority")).intValue();
        int newPriority = ((Number) ticketData.get("newPriority")).intValue();
        String changedBy = (String) ticketData.get("changedBy");
        String reason = (String) ticketData.get("reason");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.priority = newPriority;
        ticket.addPriorityChange(oldPriority, newPriority, changedBy, reason);
        
        if (newPriority > oldPriority) {
            reorderAssignmentQueue(ticket);
            updateSlaTargets(ticket);
        }
        
        persistTicketState(ticket);
        notifyPriorityChange(ticket, oldPriority, newPriority);
        
        return true;
    }
    
    private boolean handleCategoryChanged(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String oldCategory = (String) ticketData.get("oldCategory");
        String newCategory = (String) ticketData.get("newCategory");
        String changedBy = (String) ticketData.get("changedBy");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.category = newCategory;
        ticket.addCategoryChange(oldCategory, newCategory, changedBy);
        
        if (shouldReassignOnCategoryChange(oldCategory, newCategory)) {
            reassignToSpecialist(ticket, newCategory);
        }
        
        findRelatedKnowledgeBaseArticles(ticket);
        
        persistTicketState(ticket);
        
        return true;
    }
    
    private boolean handleInternalNoteAdded(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String agentId = (String) ticketData.get("agentId");
        String note = (String) ticketData.get("note");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.addInternalNote(new InternalNote(agentId, note));
        
        persistTicketState(ticket);
        notifyInternalNoteAdded(ticket, agentId, note);
        
        return true;
    }
    
    private boolean handleSlaWarning(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String slaType = (String) ticketData.get("slaType");
        long timeRemaining = ((Number) ticketData.get("timeRemaining")).longValue();
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.slaWarnings.add(slaType);
        
        if (ticket.assignedAgentId != null) {
            notifyAgentOfSlaWarning(ticket, slaType, timeRemaining);
        }
        
        if (escalationEnabled && shouldAutoEscalate(ticket, slaType)) {
            escalateTicket(ticket, "SLA warning: " + slaType);
        }
        
        return true;
    }
    
    private boolean handleSlaBreach(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String slaType = (String) ticketData.get("slaType");
        long breachDuration = ((Number) ticketData.get("breachDuration")).longValue();
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.slaBreaches.add(slaType);
        
        notifySlaBreachToManagement(ticket, slaType, breachDuration);
        
        if (escalationEnabled) {
            escalateTicket(ticket, "SLA breach: " + slaType);
        }
        
        updateTicketMetricsForSlaBreach(ticket, slaType);
        
        slaBreachesCounter.increment();
        return true;
    }
    
    private boolean handleCsatSurveyCompleted(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        int rating = ((Number) ticketData.get("rating")).intValue();
        String feedback = (String) ticketData.get("feedback");
        Map<String, Object> surveyDetails = (Map<String, Object>) ticketData.get("surveyDetails");
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.csatRating = rating;
        ticket.csatFeedback = feedback;
        ticket.csatDetails = surveyDetails;
        ticket.csatCompletedAt = Instant.now();
        
        if (ticket.assignedAgentId != null) {
            AgentState agent = agentStates.get(ticket.assignedAgentId);
            if (agent != null) {
                agent.updateCsatScore(rating);
            }
        }
        
        analyzeCsatFeedback(ticket, rating, feedback);
        
        persistTicketState(ticket);
        
        csatSurveysCounter.increment();
        return true;
    }
    
    private boolean handleKnowledgeBaseLinked(Map<String, Object> ticketData) {
        String ticketId = (String) ticketData.get("ticketId");
        String articleId = (String) ticketData.get("articleId");
        String linkedBy = (String) ticketData.get("linkedBy");
        boolean helpful = (boolean) ticketData.getOrDefault("helpful", false);
        
        TicketState ticket = ticketStates.get(ticketId);
        if (ticket == null) {
            return false;
        }
        
        ticket.linkedKbArticles.add(articleId);
        
        if (helpful) {
            updateKnowledgeBaseEffectiveness(articleId, ticket.category);
        }
        
        persistTicketState(ticket);
        
        return true;
    }
    
    private void processAssignmentQueue() {
        while (!assignmentQueue.isEmpty()) {
            TicketAssignment assignment = assignmentQueue.peek();
            if (assignment == null) break;
            
            AgentState bestAgent = findBestAvailableAgent(assignment.ticket);
            if (bestAgent != null) {
                assignmentQueue.poll();
                assignTicketToAgent(assignment.ticket, bestAgent);
            } else {
                break;
            }
        }
    }
    
    private void checkSlaCompliance() {
        Instant now = Instant.now();
        
        ticketStates.values().stream()
                .filter(t -> !t.status.equals("resolved") && !t.status.equals("closed"))
                .forEach(ticket -> {
                    SlaTracking sla = slaTrackings.get(ticket.ticketId);
                    if (sla != null) {
                        sla.checkCompliance(ticket, now);
                    }
                });
    }
    
    private void processEscalations() {
        escalationQueues.forEach((escalationLevel, queue) -> {
            List<TicketState> tickets = queue.getTicketsForProcessing(5);
            tickets.forEach(ticket -> {
                handleEscalatedTicket(ticket, escalationLevel);
            });
        });
    }
    
    private void updateAgentAvailability() {
        agentStates.values().forEach(agent -> {
            agent.updateAvailability();
            if (agent.shouldGoOffline()) {
                agent.setStatus("offline");
                redistributeTickets(agent);
            }
        });
    }
    
    private void autoCloseInactiveTickets() {
        Instant inactiveThreshold = Instant.now().minusSeconds(autoCloseInactiveDays * 24 * 60 * 60);
        
        ticketStates.values().stream()
                .filter(t -> t.status.equals("resolved"))
                .filter(t -> t.resolvedAt != null && t.resolvedAt.isBefore(inactiveThreshold))
                .forEach(ticket -> {
                    ticket.status = "closed";
                    ticket.closedAt = Instant.now();
                    ticket.closeReason = "Auto-closed due to inactivity";
                    persistTicketState(ticket);
                    logger.info("Auto-closed ticket {} due to inactivity", ticket.ticketId);
                });
    }
    
    private void generateTicketAnalytics() {
        TicketAnalytics analytics = new TicketAnalytics();
        
        analytics.totalTickets = ticketStates.size();
        analytics.openTickets = (int) ticketStates.values().stream()
                .filter(t -> "open".equals(t.status)).count();
        analytics.avgResolutionTime = calculateAverageResolutionTime();
        analytics.avgResponseTime = calculateAverageResponseTime();
        analytics.slaComplianceRate = calculateSlaComplianceRate();
        analytics.csatScore = calculateAverageCsatScore();
        
        publishAnalytics(analytics);
    }
    
    private void refreshKnowledgeBase() {
        logger.debug("Refreshing knowledge base cache");
    }
    
    private void sendCsatSurveys() {
        Instant surveyEligibleTime = Instant.now().minusMinutes(30);
        
        ticketStates.values().stream()
                .filter(t -> t.status.equals("resolved"))
                .filter(t -> t.resolvedAt != null && t.resolvedAt.isBefore(surveyEligibleTime))
                .filter(t -> t.csatRating == null)
                .limit(10)
                .forEach(this::sendCsatSurvey);
    }
    
    private void optimizeAgentRouting() {
        Map<String, Integer> categoryLoads = new HashMap<>();
        Map<String, List<AgentState>> categorySpecialists = new HashMap<>();
        
        agentStates.values().forEach(agent -> {
            agent.specialties.forEach(specialty -> {
                categorySpecialists.computeIfAbsent(specialty, k -> new ArrayList<>()).add(agent);
            });
        });
        
        ticketStates.values().stream()
                .filter(t -> t.status.equals("open"))
                .forEach(ticket -> {
                    String category = ticket.category;
                    categoryLoads.merge(category, 1, Integer::sum);
                });
        
        balanceAgentLoads(categoryLoads, categorySpecialists);
    }
    
    private void archiveResolvedTickets() {
        Instant archiveThreshold = Instant.now().minusSeconds(30 * 24 * 60 * 60);
        
        ticketStates.entrySet().removeIf(entry -> {
            TicketState ticket = entry.getValue();
            if (ticket.status.equals("closed") && ticket.closedAt != null && 
                ticket.closedAt.isBefore(archiveThreshold)) {
                archiveTicket(ticket);
                return true;
            }
            return false;
        });
    }
    
    private void sendToDlq(String message, String reason) {
        try {
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(DLQ_TOPIC, message);
            dlqRecord.headers().add("failure_reason", reason.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("original_topic", TOPIC.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("failed_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord);
            logger.warn("Message sent to DLQ with reason: {}", reason);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down SupportTicketsConsumer...");
        
        persistAllTicketStates();
        persistAllAgentStates();
        
        scheduledExecutor.shutdown();
        ticketProcessingExecutor.shutdown();
        assignmentExecutor.shutdown();
        escalationExecutor.shutdown();
        analyticsExecutor.shutdown();
        aiSuggestionExecutor.shutdown();
        notificationExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("SupportTicketsConsumer shutdown complete");
    }
    
    private static class TicketState {
        String ticketId;
        String customerId;
        String subject;
        String description;
        String category;
        int priority;
        String channel;
        String status;
        String assignedAgentId;
        String resolvedBy;
        String resolution;
        Map<String, Object> resolutionDetails;
        String escalatedTo;
        String escalationReason;
        int escalationLevel;
        String reopenedBy;
        String reopenReason;
        int reopenCount;
        String closedBy;
        String closeReason;
        Integer csatRating;
        String csatFeedback;
        Map<String, Object> csatDetails;
        SentimentScore sentiment;
        List<ResponseMessage> customerResponses;
        List<ResponseMessage> agentResponses;
        List<InternalNote> internalNotes;
        List<TicketAttachment> attachments;
        List<String> linkedKbArticles;
        Set<String> slaWarnings;
        Set<String> slaBreaches;
        List<PriorityChange> priorityChanges;
        List<CategoryChange> categoryChanges;
        Map<String, Object> metadata;
        Instant createdAt;
        Instant assignedAt;
        Instant firstResponseAt;
        Instant resolvedAt;
        Instant escalatedAt;
        Instant reopenedAt;
        Instant closedAt;
        Instant csatCompletedAt;
        Instant lastCustomerResponseAt;
        Instant lastAgentResponseAt;
        Instant lastUpdatedAt;
        String lastUpdatedBy;
        boolean awaitingCustomerResponse;
        
        TicketState(String ticketId, String customerId, String subject, String description, 
                   String category, int priority, String channel, Map<String, Object> metadata) {
            this.ticketId = ticketId;
            this.customerId = customerId;
            this.subject = subject;
            this.description = description;
            this.category = category;
            this.priority = priority;
            this.channel = channel;
            this.metadata = metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
            this.status = "open";
            this.createdAt = Instant.now();
            this.customerResponses = new CopyOnWriteArrayList<>();
            this.agentResponses = new CopyOnWriteArrayList<>();
            this.internalNotes = new CopyOnWriteArrayList<>();
            this.attachments = new CopyOnWriteArrayList<>();
            this.linkedKbArticles = new CopyOnWriteArrayList<>();
            this.slaWarnings = ConcurrentHashMap.newKeySet();
            this.slaBreaches = ConcurrentHashMap.newKeySet();
            this.priorityChanges = new CopyOnWriteArrayList<>();
            this.categoryChanges = new CopyOnWriteArrayList<>();
            this.escalationLevel = 0;
            this.reopenCount = 0;
        }
        
        void applyUpdates(Map<String, Object> updates) {
            updates.forEach((key, value) -> {
                switch (key) {
                    case "subject": this.subject = (String) value; break;
                    case "description": this.description = (String) value; break;
                    case "category": this.category = (String) value; break;
                    case "priority": this.priority = ((Number) value).intValue(); break;
                    case "status": this.status = (String) value; break;
                    default: this.metadata.put(key, value);
                }
            });
        }
        
        void setSentiment(SentimentScore sentiment) {
            this.sentiment = sentiment;
        }
        
        void addCustomerResponse(ResponseMessage response) {
            customerResponses.add(response);
        }
        
        void addAgentResponse(ResponseMessage response) {
            agentResponses.add(response);
        }
        
        void addInternalNote(InternalNote note) {
            internalNotes.add(note);
        }
        
        void addAttachment(TicketAttachment attachment) {
            attachments.add(attachment);
        }
        
        void addPriorityChange(int oldPriority, int newPriority, String changedBy, String reason) {
            priorityChanges.add(new PriorityChange(oldPriority, newPriority, changedBy, reason));
        }
        
        void addCategoryChange(String oldCategory, String newCategory, String changedBy) {
            categoryChanges.add(new CategoryChange(oldCategory, newCategory, changedBy));
        }
    }
    
    private static class AgentState {
        String agentId;
        String status;
        Set<String> currentTickets;
        List<String> specialties;
        int resolvedCount;
        int responseCount;
        double averageResolutionTime;
        double csatScore;
        int csatCount;
        Instant lastActivityAt;
        Instant shiftStartedAt;
        Map<String, Integer> categoryExpertise;
        
        AgentState(String agentId) {
            this.agentId = agentId;
            this.status = "available";
            this.currentTickets = ConcurrentHashMap.newKeySet();
            this.specialties = new CopyOnWriteArrayList<>();
            this.categoryExpertise = new ConcurrentHashMap<>();
            this.lastActivityAt = Instant.now();
            this.shiftStartedAt = Instant.now();
        }
        
        boolean isAvailable() {
            return "available".equals(status) && currentTickets.size() < 10;
        }
        
        void addTicket(String ticketId) {
            currentTickets.add(ticketId);
        }
        
        void removeTicket(String ticketId) {
            currentTickets.remove(ticketId);
        }
        
        void incrementResolvedCount() {
            resolvedCount++;
        }
        
        void incrementResponseCount() {
            responseCount++;
        }
        
        void updateAverageResolutionTime(Duration resolutionTime) {
            double newTime = resolutionTime.toMinutes();
            averageResolutionTime = (averageResolutionTime * resolvedCount + newTime) / (resolvedCount + 1);
        }
        
        void updateCsatScore(int rating) {
            csatScore = (csatScore * csatCount + rating) / (csatCount + 1);
            csatCount++;
        }
        
        void updateAvailability() {
            if (Duration.between(lastActivityAt, Instant.now()).toMinutes() > 15) {
                status = "away";
            }
        }
        
        boolean shouldGoOffline() {
            return Duration.between(shiftStartedAt, Instant.now()).toHours() > 8;
        }
        
        void setStatus(String status) {
            this.status = status;
        }
    }
    
    private static class SlaTracking {
        String ticketId;
        Instant targetFirstResponseTime;
        Instant targetResolutionTime;
        boolean firstResponseMet;
        boolean resolutionMet;
        List<SlaEvent> events;
        
        void checkCompliance(TicketState ticket, Instant now) {
            if (!firstResponseMet && ticket.firstResponseAt == null) {
                if (now.isAfter(targetFirstResponseTime)) {
                    events.add(new SlaEvent("first_response", "breach", now));
                } else if (Duration.between(now, targetFirstResponseTime).toMinutes() < 10) {
                    events.add(new SlaEvent("first_response", "warning", now));
                }
            }
            
            if (!resolutionMet && ticket.resolvedAt == null) {
                if (now.isAfter(targetResolutionTime)) {
                    events.add(new SlaEvent("resolution", "breach", now));
                } else if (Duration.between(now, targetResolutionTime).toHours() < 2) {
                    events.add(new SlaEvent("resolution", "warning", now));
                }
            }
        }
    }
    
    private static class SlaEvent {
        String type;
        String status;
        Instant timestamp;
        
        SlaEvent(String type, String status, Instant timestamp) {
            this.type = type;
            this.status = status;
            this.timestamp = timestamp;
        }
    }
    
    private static class TicketAssignment implements Comparable<TicketAssignment> {
        TicketState ticket;
        Instant queuedAt;
        
        TicketAssignment(TicketState ticket) {
            this.ticket = ticket;
            this.queuedAt = Instant.now();
        }
        
        @Override
        public int compareTo(TicketAssignment other) {
            int priorityCompare = Integer.compare(other.ticket.priority, this.ticket.priority);
            if (priorityCompare != 0) return priorityCompare;
            return this.queuedAt.compareTo(other.queuedAt);
        }
    }
    
    private static class EscalationQueue {
        private final Queue<TicketState> queue = new ConcurrentLinkedQueue<>();
        
        void addTicket(TicketState ticket) {
            queue.offer(ticket);
        }
        
        List<TicketState> getTicketsForProcessing(int limit) {
            List<TicketState> tickets = new ArrayList<>();
            for (int i = 0; i < limit && !queue.isEmpty(); i++) {
                TicketState ticket = queue.poll();
                if (ticket != null) {
                    tickets.add(ticket);
                }
            }
            return tickets;
        }
    }
    
    private static class ResponseMessage {
        String senderId;
        String message;
        List<String> attachments;
        boolean isPublic;
        Instant timestamp;
        
        ResponseMessage(String senderId, String message, List<String> attachments) {
            this(senderId, message, attachments, true);
        }
        
        ResponseMessage(String senderId, String message, List<String> attachments, boolean isPublic) {
            this.senderId = senderId;
            this.message = message;
            this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
            this.isPublic = isPublic;
            this.timestamp = Instant.now();
        }
    }
    
    private static class InternalNote {
        String agentId;
        String note;
        Instant timestamp;
        
        InternalNote(String agentId, String note) {
            this.agentId = agentId;
            this.note = note;
            this.timestamp = Instant.now();
        }
    }
    
    private static class TicketAttachment {
        String attachmentId;
        String fileName;
        long fileSize;
        String fileType;
        String uploadedBy;
        Instant uploadedAt;
        
        TicketAttachment(String attachmentId, String fileName, long fileSize, String fileType, String uploadedBy) {
            this.attachmentId = attachmentId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileType = fileType;
            this.uploadedBy = uploadedBy;
            this.uploadedAt = Instant.now();
        }
    }
    
    private static class PriorityChange {
        int oldPriority;
        int newPriority;
        String changedBy;
        String reason;
        Instant changedAt;
        
        PriorityChange(int oldPriority, int newPriority, String changedBy, String reason) {
            this.oldPriority = oldPriority;
            this.newPriority = newPriority;
            this.changedBy = changedBy;
            this.reason = reason;
            this.changedAt = Instant.now();
        }
    }
    
    private static class CategoryChange {
        String oldCategory;
        String newCategory;
        String changedBy;
        Instant changedAt;
        
        CategoryChange(String oldCategory, String newCategory, String changedBy) {
            this.oldCategory = oldCategory;
            this.newCategory = newCategory;
            this.changedBy = changedBy;
            this.changedAt = Instant.now();
        }
    }
    
    private static class KnowledgeBaseCache {
        Map<String, List<String>> categoryArticles = new ConcurrentHashMap<>();
        Map<String, Integer> articleEffectiveness = new ConcurrentHashMap<>();
        Instant lastRefreshed = Instant.now();
    }
    
    private static class CustomerHistory {
        String customerId;
        List<String> previousTickets;
        int totalTickets;
        double averageSatisfaction;
        Instant firstTicketDate;
        Instant lastTicketDate;
    }
    
    private static class TicketMetrics {
        int totalResponses;
        Duration totalResponseTime;
        Duration totalResolutionTime;
        int escalationCount;
        int reopenCount;
    }
    
    private static class SentimentScore {
        double score;
        String category;
        
        boolean isNegative() {
            return score < 0;
        }
    }
    
    private static class TicketAnalytics {
        int totalTickets;
        int openTickets;
        double avgResolutionTime;
        double avgResponseTime;
        double slaComplianceRate;
        double csatScore;
    }
    
    private void loadCustomerHistory(String customerId) {
        logger.debug("Loading customer history for: {}", customerId);
    }
    
    private SentimentScore analyzeSentiment(String text) {
        SentimentScore score = new SentimentScore();
        score.score = secureRandom.nextDouble() * 2 - 1;
        score.category = score.score > 0 ? "positive" : "negative";
        return score;
    }
    
    private void generateAiSuggestions(TicketState ticket) {
        aiSuggestionExecutor.submit(() -> {
            logger.debug("Generating AI suggestions for ticket: {}", ticket.ticketId);
        });
    }
    
    private void findRelatedKnowledgeBaseArticles(TicketState ticket) {
        logger.debug("Finding related KB articles for ticket: {}", ticket.ticketId);
    }
    
    private void initializeSlaTracking(TicketState ticket) {
        SlaTracking sla = new SlaTracking();
        sla.ticketId = ticket.ticketId;
        sla.targetFirstResponseTime = ticket.createdAt.plusSeconds(responseTimeTargetMinutes * 60);
        sla.targetResolutionTime = ticket.createdAt.plusSeconds(resolutionTimeTargetHours * 60 * 60);
        sla.events = new ArrayList<>();
        slaTrackings.put(ticket.ticketId, sla);
    }
    
    private void updateSlaTracking(TicketState ticket) {
        SlaTracking sla = slaTrackings.get(ticket.ticketId);
        if (sla != null) {
            if (ticket.firstResponseAt != null && !sla.firstResponseMet) {
                sla.firstResponseMet = ticket.firstResponseAt.isBefore(sla.targetFirstResponseTime);
            }
            if (ticket.resolvedAt != null && !sla.resolutionMet) {
                sla.resolutionMet = ticket.resolvedAt.isBefore(sla.targetResolutionTime);
            }
        }
    }
    
    private void updateSlaTargets(TicketState ticket) {
        SlaTracking sla = slaTrackings.get(ticket.ticketId);
        if (sla != null) {
            int reductionFactor = Math.max(1, 6 - ticket.priority);
            sla.targetFirstResponseTime = ticket.createdAt.plusSeconds(responseTimeTargetMinutes * 60 / reductionFactor);
            sla.targetResolutionTime = ticket.createdAt.plusSeconds(resolutionTimeTargetHours * 60 * 60 / reductionFactor);
        }
    }
    
    private void queueForAssignment(TicketState ticket) {
        assignmentQueue.offer(new TicketAssignment(ticket));
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(assignmentTimeTimer);
    }
    
    private void persistTicketState(TicketState ticket) {
        try {
            String key = TICKET_STATE_PREFIX + ticket.ticketId;
            String json = objectMapper.writeValueAsString(ticket);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
        } catch (Exception e) {
            logger.error("Failed to persist ticket state", e);
        }
    }
    
    private void persistAgentState(AgentState agent) {
        try {
            String key = AGENT_ASSIGNMENT_PREFIX + agent.agentId;
            String json = objectMapper.writeValueAsString(agent);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(1));
        } catch (Exception e) {
            logger.error("Failed to persist agent state", e);
        }
    }
    
    private void persistAllTicketStates() {
        ticketStates.values().forEach(this::persistTicketState);
    }
    
    private void persistAllAgentStates() {
        agentStates.values().forEach(this::persistAgentState);
    }
    
    private void notifyAgentsOfNewTicket(TicketState ticket) {
        logger.info("New ticket created: {} - {}", ticket.ticketId, ticket.subject);
    }
    
    private void notifyTicketUpdate(TicketState ticket, Map<String, Object> updates) {
        logger.debug("Ticket {} updated: {}", ticket.ticketId, updates.keySet());
    }
    
    private void notifyAgentAssignment(TicketState ticket, AgentState agent) {
        logger.info("Ticket {} assigned to agent {}", ticket.ticketId, agent.agentId);
    }
    
    private void notifyEscalation(TicketState ticket, String escalatedTo, String reason) {
        logger.warn("Ticket {} escalated to {}: {}", ticket.ticketId, escalatedTo, reason);
    }
    
    private void notifyTicketResolved(TicketState ticket) {
        logger.info("Ticket {} resolved", ticket.ticketId);
    }
    
    private void notifyTicketReopened(TicketState ticket) {
        logger.info("Ticket {} reopened: {}", ticket.ticketId, ticket.reopenReason);
    }
    
    private void notifyTicketClosed(TicketState ticket) {
        logger.info("Ticket {} closed", ticket.ticketId);
    }
    
    private void notifyAgentOfCustomerResponse(TicketState ticket, String message) {
        logger.debug("Customer responded to ticket {}", ticket.ticketId);
    }
    
    private void notifyCustomerOfAgentResponse(TicketState ticket, String message) {
        logger.debug("Agent responded to ticket {}", ticket.ticketId);
    }
    
    private void notifyPriorityChange(TicketState ticket, int oldPriority, int newPriority) {
        logger.info("Ticket {} priority changed from {} to {}", ticket.ticketId, oldPriority, newPriority);
    }
    
    private void notifyInternalNoteAdded(TicketState ticket, String agentId, String note) {
        logger.debug("Internal note added to ticket {} by agent {}", ticket.ticketId, agentId);
    }
    
    private void notifyAgentOfSlaWarning(TicketState ticket, String slaType, long timeRemaining) {
        logger.warn("SLA warning for ticket {}: {} - {} minutes remaining", 
                    ticket.ticketId, slaType, timeRemaining / 60);
    }
    
    private void notifySlaBreachToManagement(TicketState ticket, String slaType, long breachDuration) {
        logger.error("SLA breach for ticket {}: {} - breached by {} minutes", 
                     ticket.ticketId, slaType, breachDuration / 60);
    }
    
    private void scheduleFirstResponseReminder(TicketState ticket, AgentState agent) {
        logger.debug("Scheduling first response reminder for ticket {}", ticket.ticketId);
    }
    
    private void scheduleCsatSurvey(TicketState ticket) {
        logger.debug("Scheduling CSAT survey for ticket {}", ticket.ticketId);
    }
    
    private void sendCsatSurvey(TicketState ticket) {
        logger.debug("Sending CSAT survey for ticket {}", ticket.ticketId);
    }
    
    private void updateTicketSentimentTrend(TicketState ticket, SentimentScore sentiment) {
        logger.debug("Updating sentiment trend for ticket {}", ticket.ticketId);
    }
    
    private void evaluateResponseQuality(TicketState ticket, String response) {
        logger.debug("Evaluating response quality for ticket {}", ticket.ticketId);
    }
    
    private boolean shouldScanAttachment(String fileType) {
        return Arrays.asList("exe", "zip", "rar", "doc", "docx", "pdf").contains(fileType.toLowerCase());
    }
    
    private void scanAttachmentForIssues(String attachmentId, String ticketId) {
        logger.debug("Scanning attachment {} for ticket {}", attachmentId, ticketId);
    }
    
    private void reorderAssignmentQueue(TicketState ticket) {
        assignmentQueue.removeIf(a -> a.ticket.ticketId.equals(ticket.ticketId));
        assignmentQueue.offer(new TicketAssignment(ticket));
    }
    
    private boolean shouldReassignOnCategoryChange(String oldCategory, String newCategory) {
        return !oldCategory.equals(newCategory) && Arrays.asList("technical", "billing", "compliance")
                .contains(newCategory);
    }
    
    private void reassignToSpecialist(TicketState ticket, String category) {
        AgentState specialist = findSpecialistForCategory(category);
        if (specialist != null) {
            assignTicketToAgent(ticket, specialist);
        }
    }
    
    private boolean shouldAutoEscalate(TicketState ticket, String slaType) {
        return ticket.priority >= 3 || ticket.escalationLevel > 0;
    }
    
    private void escalateTicket(TicketState ticket, String reason) {
        ticket.escalationLevel++;
        ticket.escalationReason = reason;
        ticket.escalatedAt = Instant.now();
        ticketsEscalatedCounter.increment();
    }
    
    private void updateTicketMetrics(TicketState ticket) {
        TicketMetrics metrics = ticketMetrics.computeIfAbsent(ticket.ticketId, k -> new TicketMetrics());
        metrics.totalResponses = ticket.agentResponses.size() + ticket.customerResponses.size();
        if (ticket.resolvedAt != null) {
            metrics.totalResolutionTime = Duration.between(ticket.createdAt, ticket.resolvedAt);
        }
    }
    
    private void updateTicketMetricsForSlaBreach(TicketState ticket, String slaType) {
        logger.warn("Updating metrics for SLA breach on ticket {}: {}", ticket.ticketId, slaType);
    }
    
    private void finalizeTicketMetrics(TicketState ticket) {
        updateTicketMetrics(ticket);
    }
    
    private void analyzeCsatFeedback(TicketState ticket, int rating, String feedback) {
        logger.debug("Analyzing CSAT feedback for ticket {}: rating={}", ticket.ticketId, rating);
    }
    
    private void updateKnowledgeBaseEffectiveness(String articleId, String category) {
        KnowledgeBaseCache cache = knowledgeBaseCache.computeIfAbsent(category, k -> new KnowledgeBaseCache());
        cache.articleEffectiveness.merge(articleId, 1, Integer::sum);
    }
    
    private void analyzeResolutionPatterns(TicketState ticket) {
        logger.debug("Analyzing resolution patterns for ticket {}", ticket.ticketId);
    }
    
    private void analyzeReopenPatterns(TicketState ticket) {
        logger.debug("Analyzing reopen patterns for ticket {}", ticket.ticketId);
    }
    
    private void handleEscalatedTicket(TicketState ticket, String escalationLevel) {
        logger.info("Handling escalated ticket {} at level {}", ticket.ticketId, escalationLevel);
    }
    
    private void redistributeTickets(AgentState agent) {
        agent.currentTickets.forEach(ticketId -> {
            TicketState ticket = ticketStates.get(ticketId);
            if (ticket != null) {
                queueForAssignment(ticket);
            }
        });
        agent.currentTickets.clear();
    }
    
    private void balanceAgentLoads(Map<String, Integer> categoryLoads, Map<String, List<AgentState>> categorySpecialists) {
        logger.debug("Balancing agent loads across categories");
    }
    
    private void archiveTicket(TicketState ticket) {
        logger.info("Archiving ticket: {}", ticket.ticketId);
    }
    
    private void archiveTicketConversation(TicketState ticket) {
        logger.debug("Archiving conversation for ticket: {}", ticket.ticketId);
    }
    
    private void publishAnalytics(TicketAnalytics analytics) {
        logger.debug("Publishing ticket analytics: open={}, avg resolution={}h", 
                    analytics.openTickets, analytics.avgResolutionTime);
    }
    
    private AgentState findBestAvailableAgent(TicketAssignment assignment) {
        return agentStates.values().stream()
                .filter(AgentState::isAvailable)
                .filter(a -> a.specialties.contains(assignment.ticket.category) || a.specialties.isEmpty())
                .min(Comparator.comparing(a -> a.currentTickets.size()))
                .orElse(null);
    }
    
    private AgentState findSpecialistForCategory(String category) {
        return agentStates.values().stream()
                .filter(a -> a.specialties.contains(category))
                .filter(AgentState::isAvailable)
                .min(Comparator.comparing(a -> a.currentTickets.size()))
                .orElse(null);
    }
    
    private void assignTicketToAgent(TicketState ticket, AgentState agent) {
        ticket.assignedAgentId = agent.agentId;
        ticket.assignedAt = Instant.now();
        ticket.status = "assigned";
        agent.addTicket(ticket.ticketId);
        persistTicketState(ticket);
        persistAgentState(agent);
        agentAssignmentsCounter.increment();
    }
    
    private double calculateAverageResolutionTime() {
        return ticketStates.values().stream()
                .filter(t -> t.resolvedAt != null)
                .mapToDouble(t -> Duration.between(t.createdAt, t.resolvedAt).toHours())
                .average()
                .orElse(0);
    }
    
    private double calculateAverageResponseTime() {
        return ticketStates.values().stream()
                .filter(t -> t.firstResponseAt != null)
                .mapToDouble(t -> Duration.between(t.createdAt, t.firstResponseAt).toMinutes())
                .average()
                .orElse(0);
    }
    
    private double calculateSlaComplianceRate() {
        long total = slaTrackings.size();
        if (total == 0) return 100.0;
        
        long compliant = slaTrackings.values().stream()
                .filter(sla -> sla.firstResponseMet && sla.resolutionMet)
                .count();
        
        return (compliant * 100.0) / total;
    }
    
    private double calculateAverageCsatScore() {
        return ticketStates.values().stream()
                .filter(t -> t.csatRating != null)
                .mapToInt(t -> t.csatRating)
                .average()
                .orElse(0);
    }
}