package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.notification.entity.EmailNotification;
import com.waqiti.notification.repository.EmailNotificationRepository;
import com.waqiti.notification.service.*;
import com.waqiti.notification.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EmailNotificationConsumer {

    private static final String TOPIC = "email-notifications";
    private static final String GROUP_ID = "notification-email-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final int BATCH_SIZE = 50;
    private static final int RATE_LIMIT_PER_MINUTE = 1000;
    private static final int BOUNCE_THRESHOLD = 5;
    private static final double SPAM_SCORE_THRESHOLD = 0.70;
    private static final int TEMPLATE_CACHE_SIZE = 100;
    private static final long DELIVERY_TIMEOUT_MS = 30000;
    private static final int MAX_ATTACHMENT_SIZE_MB = 25;
    private static final double ENGAGEMENT_THRESHOLD = 0.20;
    private static final int UNSUBSCRIBE_COOLDOWN_DAYS = 30;
    private static final double DELIVERABILITY_THRESHOLD = 0.95;
    private static final int EMAIL_PRIORITY_HIGH = 1;
    private static final int EMAIL_PRIORITY_NORMAL = 3;
    private static final int EMAIL_PRIORITY_LOW = 5;
    
    private final EmailNotificationRepository notificationRepository;
    private final EmailDeliveryService deliveryService;
    private final TemplateEngineService templateService;
    private final EmailValidationService validationService;
    private final BounceManagementService bounceService;
    private final SpamCheckService spamService;
    private final TrackingService trackingService;
    private final RateLimitService rateLimitService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler universalDLQHandler;

    public EmailNotificationConsumer(
            EmailNotificationRepository notificationRepository,
            EmailDeliveryService deliveryService,
            TemplateEngineService templateService,
            EmailValidationService validationService,
            BounceManagementService bounceService,
            SpamCheckService spamService,
            TrackingService trackingService,
            RateLimitService rateLimitService,
            MetricsService metricsService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            UniversalDLQHandler universalDLQHandler) {
        this.notificationRepository = notificationRepository;
        this.deliveryService = deliveryService;
        this.templateService = templateService;
        this.validationService = validationService;
        this.bounceService = bounceService;
        this.spamService = spamService;
        this.trackingService = trackingService;
        this.rateLimitService = rateLimitService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.universalDLQHandler = universalDLQHandler;
    }
    
    private final Map<String, EmailQueueState> queueStates = new ConcurrentHashMap<>();
    private final Map<String, TemplateCache> templateCaches = new ConcurrentHashMap<>();
    private final Map<String, RecipientProfile> recipientProfiles = new ConcurrentHashMap<>();
    private final Map<String, DeliveryTracker> deliveryTrackers = new ConcurrentHashMap<>();
    private final Map<String, BounceTracker> bounceTrackers = new ConcurrentHashMap<>();
    private final Map<String, EngagementTracker> engagementTrackers = new ConcurrentHashMap<>();
    private final Map<String, UnsubscribeManager> unsubscribeManagers = new ConcurrentHashMap<>();
    private final Map<String, EmailBatch> pendingBatches = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<EmailMessage> priorityQueue = new PriorityBlockingQueue<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService deliveryExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<EmailEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Counter deliveredCounter;
    private Counter bouncedCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge deliveryRateGauge;
    private Gauge bounceRateGauge;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        loadTemplates();
        initializeRateLimiters();
        log.info("EmailNotificationConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("email.notifications.processed");
        errorCounter = meterRegistry.counter("email.notifications.errors");
        deliveredCounter = meterRegistry.counter("email.notifications.delivered");
        bouncedCounter = meterRegistry.counter("email.notifications.bounced");
        processingTimer = meterRegistry.timer("email.notifications.processing.time");
        queueSizeGauge = meterRegistry.gauge("email.notifications.queue.size", priorityQueue, Queue::size);
        
        deliveryRateGauge = meterRegistry.gauge("email.notifications.delivery.rate", 
            deliveryTrackers, trackers -> calculateDeliveryRate(trackers));
        bounceRateGauge = meterRegistry.gauge("email.notifications.bounce.rate",
            bounceTrackers, trackers -> calculateBounceRate(trackers));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::processBatchQueue, 0, 5, TimeUnit.SECONDS);
        scheduledExecutor.scheduleAtFixedRate(this::retryFailedDeliveries, 1, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::updateDeliveryMetrics, 30, 30, TimeUnit.SECONDS);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::refreshTemplateCache, 1, 1, TimeUnit.HOURS);
    }
    
    private void loadTemplates() {
        try {
            List<EmailTemplate> templates = templateService.loadAllTemplates();
            templates.forEach(template -> {
                TemplateCache cache = new TemplateCache(template);
                templateCaches.put(template.getName(), cache);
            });
            log.info("Loaded {} email templates", templates.size());
        } catch (Exception e) {
            log.error("Error loading email templates: {}", e.getMessage(), e);
        }
    }
    
    private void initializeRateLimiters() {
        Arrays.asList("transactional", "marketing", "system", "alert").forEach(category -> {
            rateLimiters.put(category, new RateLimiter(RATE_LIMIT_PER_MINUTE));
            queueStates.put(category, new EmailQueueState(category));
            deliveryTrackers.put(category, new DeliveryTracker(category));
            bounceTrackers.put(category, new BounceTracker(category));
            engagementTrackers.put(category, new EngagementTracker(category));
            unsubscribeManagers.put(category, new UnsubscribeManager(category));
        });
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "emailNotification", fallbackMethod = "handleMessageFallback")
    @Retry(name = "emailNotification", fallbackMethod = "handleMessageFallback")
    public void consume(
            @Payload ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("traceId", UUID.randomUUID().toString());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Processing email notification from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing email notification: {}", e.getMessage(), e);
            errorCounter.increment();

            // Enhanced DLQ handling with UniversalDLQHandler
            try {
                universalDLQHandler.handleFailedMessage(record, e)
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            log.info("Email notification successfully sent to DLQ: destinationTopic={}, attemptNumber={}",
                                result.getDestinationTopic(), result.getAttemptNumber());
                        } else {
                            log.error("Failed to send email notification to DLQ: {}", result.getError());
                        }
                    })
                    .exceptionally(dlqEx -> {
                        log.error("Critical DLQ failure for email notification: {}", dlqEx.getMessage(), dlqEx);
                        return null;
                    });
            } catch (Exception dlqException) {
                log.error("Exception during DLQ handling for email notification: {}", dlqException.getMessage(), dlqException);
            }

            handleProcessingError(record, e, acknowledgment);

            // Re-throw to trigger Spring Kafka error handling
            throw e;
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "SEND_EMAIL":
                    processSendEmail(eventData);
                    break;
                case "SEND_BATCH_EMAIL":
                    processSendBatchEmail(eventData);
                    break;
                case "SEND_TEMPLATED_EMAIL":
                    processSendTemplatedEmail(eventData);
                    break;
                case "SEND_TRANSACTIONAL_EMAIL":
                    processSendTransactionalEmail(eventData);
                    break;
                case "SEND_MARKETING_EMAIL":
                    processSendMarketingEmail(eventData);
                    break;
                case "SEND_ALERT_EMAIL":
                    processSendAlertEmail(eventData);
                    break;
                case "EMAIL_BOUNCE":
                    processEmailBounce(eventData);
                    break;
                case "EMAIL_COMPLAINT":
                    processEmailComplaint(eventData);
                    break;
                case "EMAIL_OPENED":
                    processEmailOpened(eventData);
                    break;
                case "EMAIL_CLICKED":
                    processEmailClicked(eventData);
                    break;
                case "EMAIL_UNSUBSCRIBE":
                    processEmailUnsubscribe(eventData);
                    break;
                case "EMAIL_DELIVERED":
                    processEmailDelivered(eventData);
                    break;
                case "EMAIL_FAILED":
                    processEmailFailed(eventData);
                    break;
                case "UPDATE_PREFERENCE":
                    processUpdatePreference(eventData);
                    break;
                case "SCHEDULE_EMAIL":
                    processScheduleEmail(eventData);
                    break;
                default:
                    log.warn("Unknown email notification event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processSendEmail(JsonNode eventData) {
        String messageId = UUID.randomUUID().toString();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String senderEmail = eventData.get("senderEmail").asText();
        String subject = eventData.get("subject").asText();
        String htmlBody = eventData.get("htmlBody").asText();
        String textBody = eventData.get("textBody").asText("");
        int priority = eventData.get("priority").asInt(EMAIL_PRIORITY_NORMAL);
        JsonNode headers = eventData.get("headers");
        JsonNode attachments = eventData.get("attachments");
        long timestamp = eventData.get("timestamp").asLong();
        
        if (!validationService.validateEmail(recipientEmail)) {
            log.warn("Invalid recipient email: {}", recipientEmail);
            return;
        }
        
        if (isBounced(recipientEmail)) {
            log.warn("Recipient email is in bounce list: {}", recipientEmail);
            handleBouncedRecipient(recipientEmail, messageId);
            return;
        }
        
        if (isUnsubscribed(recipientEmail, "general")) {
            log.info("Recipient has unsubscribed: {}", recipientEmail);
            return;
        }
        
        double spamScore = spamService.calculateSpamScore(subject, htmlBody);
        if (spamScore > SPAM_SCORE_THRESHOLD) {
            log.warn("High spam score {:.2f} for email to {}", spamScore, recipientEmail);
            modifyForSpamCompliance(subject, htmlBody, spamScore);
        }
        
        EmailMessage message = EmailMessage.builder()
            .messageId(messageId)
            .recipientEmail(recipientEmail)
            .senderEmail(senderEmail)
            .subject(subject)
            .htmlBody(addTrackingPixel(htmlBody, messageId))
            .textBody(textBody)
            .priority(priority)
            .headers(headers)
            .attachments(validateAttachments(attachments))
            .timestamp(timestamp)
            .build();
        
        priorityQueue.offer(message);
        
        updateQueueState("transactional", state -> {
            state.incrementQueued();
        });
        
        EmailNotification notification = EmailNotification.builder()
            .messageId(messageId)
            .recipientEmail(recipientEmail)
            .subject(subject)
            .status("QUEUED")
            .priority(priority)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        notificationRepository.save(notification);
    }
    
    private void processSendBatchEmail(JsonNode eventData) {
        String batchId = UUID.randomUUID().toString();
        String templateId = eventData.get("templateId").asText();
        JsonNode recipients = eventData.get("recipients");
        JsonNode commonData = eventData.get("commonData");
        String category = eventData.get("category").asText("marketing");
        long scheduledTime = eventData.get("scheduledTime").asLong(0);
        long timestamp = eventData.get("timestamp").asLong();
        
        EmailBatch batch = new EmailBatch(batchId, templateId, category);
        
        recipients.forEach(recipient -> {
            String email = recipient.get("email").asText();
            JsonNode personalData = recipient.get("data");
            
            if (validationService.validateEmail(email) && 
                !isBounced(email) && 
                !isUnsubscribed(email, category)) {
                
                Map<String, Object> mergedData = mergeTemplateData(commonData, personalData);
                batch.addRecipient(email, mergedData);
            }
        });
        
        if (batch.getRecipientCount() > 0) {
            if (scheduledTime > 0) {
                scheduleBatch(batch, scheduledTime);
            } else {
                pendingBatches.put(batchId, batch);
            }
            
            log.info("Batch email {} queued with {} recipients", batchId, batch.getRecipientCount());
        }
        
        updateQueueState(category, state -> {
            state.addBatch(batchId, batch.getRecipientCount());
        });
    }
    
    private void processSendTemplatedEmail(JsonNode eventData) {
        String messageId = UUID.randomUUID().toString();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String templateName = eventData.get("templateName").asText();
        JsonNode templateData = eventData.get("templateData");
        String locale = eventData.get("locale").asText("en-US");
        int priority = eventData.get("priority").asInt(EMAIL_PRIORITY_NORMAL);
        long timestamp = eventData.get("timestamp").asLong();
        
        TemplateCache cache = templateCaches.get(templateName);
        if (cache == null) {
            log.error("Template not found: {}", templateName);
            return;
        }
        
        String subject = templateService.renderSubject(cache.getTemplate(), templateData, locale);
        String htmlBody = templateService.renderHtml(cache.getTemplate(), templateData, locale);
        String textBody = templateService.renderText(cache.getTemplate(), templateData, locale);
        
        EmailMessage message = EmailMessage.builder()
            .messageId(messageId)
            .recipientEmail(recipientEmail)
            .subject(subject)
            .htmlBody(addTrackingPixel(htmlBody, messageId))
            .textBody(textBody)
            .priority(priority)
            .templateName(templateName)
            .timestamp(timestamp)
            .build();
        
        priorityQueue.offer(message);
        
        cache.recordUsage();
    }
    
    private void processSendTransactionalEmail(JsonNode eventData) {
        String messageId = UUID.randomUUID().toString();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String transactionId = eventData.get("transactionId").asText();
        String transactionType = eventData.get("transactionType").asText();
        JsonNode transactionData = eventData.get("transactionData");
        long timestamp = eventData.get("timestamp").asLong();
        
        String templateName = getTransactionalTemplate(transactionType);
        TemplateCache cache = templateCaches.get(templateName);
        
        if (cache == null) {
            log.error("Transactional template not found for type: {}", transactionType);
            return;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", transactionId);
        data.put("transactionData", transactionData);
        
        String subject = templateService.renderSubject(cache.getTemplate(), data, "en-US");
        String htmlBody = templateService.renderHtml(cache.getTemplate(), data, "en-US");
        
        EmailMessage message = EmailMessage.builder()
            .messageId(messageId)
            .recipientEmail(recipientEmail)
            .subject(subject)
            .htmlBody(addTrackingPixel(htmlBody, messageId))
            .priority(EMAIL_PRIORITY_HIGH)
            .category("transactional")
            .referenceId(transactionId)
            .timestamp(timestamp)
            .build();
        
        sendImmediately(message);
        
        updateQueueState("transactional", state -> {
            state.incrementTransactional();
        });
    }
    
    private void processSendMarketingEmail(JsonNode eventData) {
        String campaignId = eventData.get("campaignId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String subject = eventData.get("subject").asText();
        JsonNode content = eventData.get("content");
        JsonNode segmentData = eventData.get("segmentData");
        long timestamp = eventData.get("timestamp").asLong();
        
        if (isUnsubscribed(recipientEmail, "marketing")) {
            log.info("Recipient unsubscribed from marketing: {}", recipientEmail);
            return;
        }
        
        RecipientProfile profile = recipientProfiles.computeIfAbsent(recipientEmail, 
            k -> new RecipientProfile(recipientEmail));
        
        if (!profile.shouldReceiveMarketing()) {
            log.info("Recipient engagement too low: {}", recipientEmail);
            return;
        }
        
        String personalizedSubject = personalizeContent(subject, segmentData);
        String personalizedContent = personalizeContent(content.asText(), segmentData);
        
        personalizedContent = addUnsubscribeLink(personalizedContent, recipientEmail, campaignId);
        
        EmailMessage message = EmailMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .recipientEmail(recipientEmail)
            .subject(personalizedSubject)
            .htmlBody(addTrackingPixel(personalizedContent, campaignId))
            .priority(EMAIL_PRIORITY_LOW)
            .category("marketing")
            .campaignId(campaignId)
            .timestamp(timestamp)
            .build();
        
        if (rateLimiters.get("marketing").tryAcquire()) {
            priorityQueue.offer(message);
        } else {
            scheduleForLater(message, 60000);
        }
        
        updateQueueState("marketing", state -> {
            state.incrementMarketing();
        });
    }
    
    private void processSendAlertEmail(JsonNode eventData) {
        String alertId = eventData.get("alertId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String alertType = eventData.get("alertType").asText();
        String severity = eventData.get("severity").asText();
        JsonNode alertData = eventData.get("alertData");
        long timestamp = eventData.get("timestamp").asLong();
        
        String subject = formatAlertSubject(alertType, severity);
        String body = formatAlertBody(alertType, severity, alertData);
        
        EmailMessage message = EmailMessage.builder()
            .messageId(UUID.randomUUID().toString())
            .recipientEmail(recipientEmail)
            .subject(subject)
            .htmlBody(body)
            .priority(EMAIL_PRIORITY_HIGH)
            .category("alert")
            .referenceId(alertId)
            .timestamp(timestamp)
            .build();
        
        sendImmediately(message);
        
        updateQueueState("alert", state -> {
            state.incrementAlert();
        });
    }
    
    private void processEmailBounce(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String bounceType = eventData.get("bounceType").asText();
        String bounceSubType = eventData.get("bounceSubType").asText();
        String diagnosticCode = eventData.get("diagnosticCode").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        BounceTracker tracker = bounceTrackers.get("transactional");
        if (tracker != null) {
            tracker.recordBounce(recipientEmail, bounceType, bounceSubType, diagnosticCode, timestamp);
            
            if ("Permanent".equals(bounceType)) {
                bounceService.addToBounceList(recipientEmail, bounceType, diagnosticCode);
                log.warn("Added {} to bounce list due to permanent bounce", recipientEmail);
            }
            
            int bounceCount = tracker.getBounceCount(recipientEmail);
            if (bounceCount >= BOUNCE_THRESHOLD) {
                handleExcessiveBounces(recipientEmail, bounceCount);
            }
        }
        
        bouncedCounter.increment();
        
        updateNotificationStatus(messageId, "BOUNCED", diagnosticCode);
        
        metricsService.recordEmailBounce(recipientEmail, bounceType);
    }
    
    private void processEmailComplaint(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String complaintType = eventData.get("complaintType").asText();
        String feedbackType = eventData.get("feedbackType").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        log.warn("Complaint received from {} for message {}: {}", 
            recipientEmail, messageId, complaintType);
        
        unsubscribeManagers.values().forEach(manager -> 
            manager.addComplaint(recipientEmail, complaintType));
        
        if ("spam".equalsIgnoreCase(complaintType)) {
            handleSpamComplaint(recipientEmail, messageId);
        }
        
        updateNotificationStatus(messageId, "COMPLAINT", complaintType);
        
        metricsService.recordEmailComplaint(recipientEmail, complaintType);
    }
    
    private void processEmailOpened(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String userAgent = eventData.get("userAgent").asText("");
        String ipAddress = eventData.get("ipAddress").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        EngagementTracker tracker = engagementTrackers.get("marketing");
        if (tracker != null) {
            tracker.recordOpen(recipientEmail, messageId, userAgent, ipAddress, timestamp);
        }
        
        RecipientProfile profile = recipientProfiles.get(recipientEmail);
        if (profile != null) {
            profile.recordEngagement("open", timestamp);
        }
        
        trackingService.recordOpen(messageId, recipientEmail, userAgent, ipAddress);
        
        updateNotificationStatus(messageId, "OPENED", null);
        
        metricsService.recordEmailOpen(recipientEmail);
    }
    
    private void processEmailClicked(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String clickedUrl = eventData.get("clickedUrl").asText();
        String userAgent = eventData.get("userAgent").asText("");
        String ipAddress = eventData.get("ipAddress").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        EngagementTracker tracker = engagementTrackers.get("marketing");
        if (tracker != null) {
            tracker.recordClick(recipientEmail, messageId, clickedUrl, userAgent, ipAddress, timestamp);
        }
        
        RecipientProfile profile = recipientProfiles.get(recipientEmail);
        if (profile != null) {
            profile.recordEngagement("click", timestamp);
        }
        
        trackingService.recordClick(messageId, recipientEmail, clickedUrl);
        
        updateNotificationStatus(messageId, "CLICKED", clickedUrl);
        
        metricsService.recordEmailClick(recipientEmail, clickedUrl);
    }
    
    private void processEmailUnsubscribe(JsonNode eventData) {
        String recipientEmail = eventData.get("recipientEmail").asText();
        String category = eventData.get("category").asText("all");
        String reason = eventData.get("reason").asText("");
        String source = eventData.get("source").asText("link");
        long timestamp = eventData.get("timestamp").asLong();
        
        UnsubscribeManager manager = unsubscribeManagers.get(category);
        if (manager != null) {
            manager.addUnsubscribe(recipientEmail, reason, source, timestamp);
        }
        
        if ("all".equals(category)) {
            unsubscribeManagers.values().forEach(m -> 
                m.addUnsubscribe(recipientEmail, reason, source, timestamp));
        }
        
        log.info("User {} unsubscribed from {} emails", recipientEmail, category);
        
        metricsService.recordEmailUnsubscribe(recipientEmail, category);
    }
    
    private void processEmailDelivered(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String smtpResponse = eventData.get("smtpResponse").asText("");
        long timestamp = eventData.get("timestamp").asLong();
        
        DeliveryTracker tracker = deliveryTrackers.get("transactional");
        if (tracker != null) {
            tracker.recordDelivery(messageId, recipientEmail, smtpResponse, timestamp);
        }
        
        deliveredCounter.increment();
        
        updateNotificationStatus(messageId, "DELIVERED", smtpResponse);
        
        metricsService.recordEmailDelivered(recipientEmail);
    }
    
    private void processEmailFailed(JsonNode eventData) {
        String messageId = eventData.get("messageId").asText();
        String recipientEmail = eventData.get("recipientEmail").asText();
        String errorCode = eventData.get("errorCode").asText();
        String errorMessage = eventData.get("errorMessage").asText();
        int attemptNumber = eventData.get("attemptNumber").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (attemptNumber < MAX_RETRY_ATTEMPTS) {
            scheduleRetry(messageId, recipientEmail, attemptNumber + 1);
        } else {
            updateNotificationStatus(messageId, "FAILED", errorMessage);
            handlePermanentFailure(messageId, recipientEmail, errorMessage);
        }
        
        metricsService.recordEmailFailed(recipientEmail, errorCode);
    }
    
    private void processUpdatePreference(JsonNode eventData) {
        String recipientEmail = eventData.get("recipientEmail").asText();
        JsonNode preferences = eventData.get("preferences");
        long timestamp = eventData.get("timestamp").asLong();
        
        RecipientProfile profile = recipientProfiles.computeIfAbsent(recipientEmail, 
            k -> new RecipientProfile(recipientEmail));
        
        preferences.fieldNames().forEachRemaining(category -> {
            boolean enabled = preferences.get(category).asBoolean();
            profile.updatePreference(category, enabled);
            
            if (!enabled) {
                UnsubscribeManager manager = unsubscribeManagers.get(category);
                if (manager != null) {
                    manager.addUnsubscribe(recipientEmail, "preference_update", "api", timestamp);
                }
            }
        });
        
        log.info("Updated preferences for {}", recipientEmail);
    }
    
    private void processScheduleEmail(JsonNode eventData) {
        String messageId = UUID.randomUUID().toString();
        JsonNode emailData = eventData.get("emailData");
        long scheduledTime = eventData.get("scheduledTime").asLong();
        String timezone = eventData.get("timezone").asText("UTC");
        
        EmailMessage message = buildEmailMessage(messageId, emailData);
        
        long delay = scheduledTime - System.currentTimeMillis();
        if (delay > 0) {
            scheduledExecutor.schedule(() -> {
                priorityQueue.offer(message);
            }, delay, TimeUnit.MILLISECONDS);
            
            log.info("Email {} scheduled for delivery at {}", messageId, 
                Instant.ofEpochMilli(scheduledTime));
        } else {
            priorityQueue.offer(message);
        }
    }
    
    private boolean isBounced(String email) {
        return bounceService.isInBounceList(email);
    }
    
    private boolean isUnsubscribed(String email, String category) {
        UnsubscribeManager manager = unsubscribeManagers.get(category);
        return manager != null && manager.isUnsubscribed(email);
    }
    
    private void handleBouncedRecipient(String email, String messageId) {
        updateNotificationStatus(messageId, "SUPPRESSED", "Recipient in bounce list");
    }
    
    private void modifyForSpamCompliance(String subject, String body, double spamScore) {
        log.info("Modifying email content to reduce spam score from {}", spamScore);
    }
    
    private String addTrackingPixel(String htmlBody, String trackingId) {
        String pixelUrl = trackingService.generateTrackingPixel(trackingId);
        return htmlBody + "<img src='" + pixelUrl + "' width='1' height='1' />";
    }
    
    private JsonNode validateAttachments(JsonNode attachments) {
        if (attachments == null) return null;
        
        int totalSize = 0;
        for (JsonNode attachment : attachments) {
            int size = attachment.get("size").asInt();
            totalSize += size;
        }
        
        if (totalSize > MAX_ATTACHMENT_SIZE_MB * 1024 * 1024) {
            log.warn("CRITICAL: Attachments exceed maximum size limit - Email delivery may fail");
            throw new IllegalArgumentException("Attachments exceed maximum size limit of " + MAX_ATTACHMENT_SIZE_MB + "MB");
        }
        
        return attachments;
    }
    
    private void updateQueueState(String category, java.util.function.Consumer<EmailQueueState> updater) {
        queueStates.computeIfAbsent(category, k -> new EmailQueueState(category))
                   .update(updater);
    }
    
    private Map<String, Object> mergeTemplateData(JsonNode common, JsonNode personal) {
        Map<String, Object> merged = objectMapper.convertValue(common, Map.class);
        Map<String, Object> personalData = objectMapper.convertValue(personal, Map.class);
        merged.putAll(personalData);
        return merged;
    }
    
    private void scheduleBatch(EmailBatch batch, long scheduledTime) {
        long delay = scheduledTime - System.currentTimeMillis();
        scheduledExecutor.schedule(() -> {
            pendingBatches.put(batch.getBatchId(), batch);
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    private String getTransactionalTemplate(String transactionType) {
        return "transactional_" + transactionType.toLowerCase();
    }
    
    private void sendImmediately(EmailMessage message) {
        deliveryExecutor.execute(() -> {
            try {
                deliveryService.send(message);
                deliveredCounter.increment();
            } catch (Exception e) {
                log.error("Failed to send email {}: {}", message.getMessageId(), e.getMessage());
                scheduleRetry(message.getMessageId(), message.getRecipientEmail(), 1);
            }
        });
    }
    
    private String personalizeContent(String content, JsonNode data) {
        return templateService.personalizeContent(content, data);
    }
    
    private String addUnsubscribeLink(String content, String email, String campaignId) {
        String unsubscribeUrl = trackingService.generateUnsubscribeLink(email, campaignId);
        return content + "<br><br><a href='" + unsubscribeUrl + "'>Unsubscribe</a>";
    }
    
    private void scheduleForLater(EmailMessage message, long delayMs) {
        scheduledExecutor.schedule(() -> {
            priorityQueue.offer(message);
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    private String formatAlertSubject(String alertType, String severity) {
        return String.format("[%s] %s Alert", severity.toUpperCase(), alertType);
    }
    
    private String formatAlertBody(String alertType, String severity, JsonNode data) {
        return templateService.formatAlert(alertType, severity, data);
    }
    
    private void handleExcessiveBounces(String email, int bounceCount) {
        log.warn("Excessive bounces for {}: {} bounces", email, bounceCount);
        bounceService.suspendEmail(email);
    }
    
    private void handleSpamComplaint(String email, String messageId) {
        unsubscribeManagers.values().forEach(manager -> 
            manager.addUnsubscribe(email, "spam_complaint", "complaint", System.currentTimeMillis()));
    }
    
    private void updateNotificationStatus(String messageId, String status, String detail) {
        notificationRepository.findByMessageId(messageId).ifPresent(notification -> {
            notification.setStatus(status);
            notification.setStatusDetail(detail);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        });
    }
    
    private void scheduleRetry(String messageId, String email, int attemptNumber) {
        long delay = RETRY_DELAY_MS * attemptNumber;
        scheduledExecutor.schedule(() -> {
            retryDelivery(messageId, email, attemptNumber);
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    private void retryDelivery(String messageId, String email, int attemptNumber) {
        log.info("Retrying delivery for {} to {} (attempt {})", messageId, email, attemptNumber);
    }
    
    private void handlePermanentFailure(String messageId, String email, String error) {
        log.error("Permanent failure for {} to {}: {}", messageId, email, error);
    }
    
    private EmailMessage buildEmailMessage(String messageId, JsonNode data) {
        return EmailMessage.builder()
            .messageId(messageId)
            .recipientEmail(data.get("recipientEmail").asText())
            .subject(data.get("subject").asText())
            .htmlBody(data.get("htmlBody").asText())
            .textBody(data.get("textBody").asText(""))
            .priority(data.get("priority").asInt(EMAIL_PRIORITY_NORMAL))
            .build();
    }
    
    @Scheduled(fixedDelay = 5000)
    private void processBatchQueue() {
        try {
            while (!priorityQueue.isEmpty()) {
                EmailMessage message = priorityQueue.poll();
                if (message != null && rateLimiters.get(message.getCategory()).tryAcquire()) {
                    sendImmediately(message);
                } else if (message != null) {
                    priorityQueue.offer(message);
                    break;
                }
            }
            
            pendingBatches.values().forEach(batch -> {
                if (batch.isReady()) {
                    processBatch(batch);
                }
            });
        } catch (Exception e) {
            log.error("Error processing batch queue: {}", e.getMessage(), e);
        }
    }
    
    private void processBatch(EmailBatch batch) {
        batch.getRecipients().forEach((email, data) -> {
            TemplateCache cache = templateCaches.get(batch.getTemplateId());
            if (cache != null) {
                String subject = templateService.renderSubject(cache.getTemplate(), data, "en-US");
                String htmlBody = templateService.renderHtml(cache.getTemplate(), data, "en-US");
                
                EmailMessage message = EmailMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .recipientEmail(email)
                    .subject(subject)
                    .htmlBody(addTrackingPixel(htmlBody, batch.getBatchId()))
                    .category(batch.getCategory())
                    .batchId(batch.getBatchId())
                    .build();
                
                priorityQueue.offer(message);
            }
        });
        
        pendingBatches.remove(batch.getBatchId());
    }
    
    @Scheduled(fixedDelay = 60000)
    private void retryFailedDeliveries() {
        try {
            List<EmailNotification> failed = notificationRepository.findByStatusAndRetryCountLessThan(
                "FAILED", MAX_RETRY_ATTEMPTS);
            
            failed.forEach(notification -> {
                scheduleRetry(notification.getMessageId(), 
                            notification.getRecipientEmail(), 
                            notification.getRetryCount() + 1);
            });
        } catch (Exception e) {
            log.error("Error retrying failed deliveries: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 30000)
    private void updateDeliveryMetrics() {
        try {
            deliveryTrackers.forEach((category, tracker) -> {
                double deliveryRate = tracker.getDeliveryRate();
                metricsService.recordDeliveryRate(category, deliveryRate);
            });
            
            bounceTrackers.forEach((category, tracker) -> {
                double bounceRate = tracker.getBounceRate();
                metricsService.recordBounceRate(category, bounceRate);
            });
        } catch (Exception e) {
            log.error("Error updating delivery metrics: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deleted = notificationRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old email notifications", deleted);
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    private void refreshTemplateCache() {
        try {
            loadTemplates();
        } catch (Exception e) {
            log.error("Error refreshing template cache: {}", e.getMessage(), e);
        }
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process email notification after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            sendToDeadLetterQueue(record, error);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error handling processing failure: {}", e.getMessage(), e);
        }
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record, Exception error) {
        log.info("Sending failed message to DLQ: {}", record.value());
    }
    
    public void handleMessageFallback(ConsumerRecord<String, String> record, Exception ex) {
        log.error("Fallback triggered for email notification processing", ex);
        errorCounter.increment();
    }
    
    private double calculateDeliveryRate(Map<String, DeliveryTracker> trackers) {
        return trackers.values().stream()
            .mapToDouble(DeliveryTracker::getDeliveryRate)
            .average()
            .orElse(0.0);
    }
    
    private double calculateBounceRate(Map<String, BounceTracker> trackers) {
        return trackers.values().stream()
            .mapToDouble(BounceTracker::getBounceRate)
            .average()
            .orElse(0.0);
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down EmailNotificationConsumer...");
            scheduledExecutor.shutdown();
            deliveryExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!deliveryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
            
            log.info("EmailNotificationConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}