package com.waqiti.support.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.support.service.SupportTicketService;
import com.waqiti.support.service.TicketRoutingService;
import com.waqiti.support.service.SupportNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportTicketConsumer {
    
    private final SupportTicketService supportTicketService;
    private final TicketRoutingService ticketRoutingService;
    private final SupportNotificationService supportNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"support-ticket-events", "support-tickets"},
        groupId = "support-service-ticket-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleSupportTicket(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("SUPPORT TICKET: Processing support ticket - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID ticketId = null;
        UUID userId = null;
        String ticketStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            ticketId = UUID.fromString((String) event.get("ticketId"));
            userId = UUID.fromString((String) event.get("userId"));
            String ticketType = (String) event.get("ticketType");
            ticketStatus = (String) event.get("ticketStatus");
            String priority = (String) event.get("priority");
            String category = (String) event.get("category");
            String subject = (String) event.get("subject");
            String description = (String) event.get("description");
            LocalDateTime createdAt = LocalDateTime.parse((String) event.get("createdAt"));
            UUID assignedToAgentId = event.containsKey("assignedToAgentId") ? 
                    UUID.fromString((String) event.get("assignedToAgentId")) : null;
            String assignedToTeam = (String) event.get("assignedToTeam");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) event.getOrDefault("tags", List.of());
            String channel = (String) event.get("channel");
            String language = (String) event.getOrDefault("language", "en");
            Boolean requiresEscalation = (Boolean) event.getOrDefault("requiresEscalation", false);
            UUID relatedTransactionId = event.containsKey("relatedTransactionId") ? 
                    UUID.fromString((String) event.get("relatedTransactionId")) : null;
            UUID relatedAccountId = event.containsKey("relatedAccountId") ? 
                    UUID.fromString((String) event.get("relatedAccountId")) : null;
            
            log.info("Support ticket - TicketId: {}, UserId: {}, Type: {}, Status: {}, Priority: {}, Category: {}, Channel: {}", 
                    ticketId, userId, ticketType, ticketStatus, priority, category, channel);
            
            validateSupportTicket(ticketId, userId, ticketType, ticketStatus, priority, category);
            
            processTicketByType(ticketId, userId, ticketType, ticketStatus, priority, category, 
                    subject, description, createdAt, assignedToAgentId, assignedToTeam, tags, 
                    channel, language, requiresEscalation, relatedTransactionId, relatedAccountId);
            
            if ("NEW".equals(ticketStatus)) {
                handleNewTicket(ticketId, userId, ticketType, priority, category, subject, 
                        description, tags, channel, requiresEscalation);
            } else if ("ASSIGNED".equals(ticketStatus)) {
                handleAssignedTicket(ticketId, userId, assignedToAgentId, assignedToTeam, priority);
            } else if ("RESOLVED".equals(ticketStatus)) {
                handleResolvedTicket(ticketId, userId, ticketType, category, assignedToAgentId);
            } else if ("ESCALATED".equals(ticketStatus)) {
                handleEscalatedTicket(ticketId, userId, ticketType, priority, category, 
                        assignedToTeam);
            }
            
            if (requiresEscalation || "URGENT".equals(priority) || "CRITICAL".equals(priority)) {
                escalateTicket(ticketId, userId, ticketType, priority, category, assignedToTeam);
            }
            
            routeTicket(ticketId, ticketType, category, priority, tags, language, channel);
            
            notifyUser(userId, ticketId, ticketType, ticketStatus, subject, channel);
            
            if (assignedToAgentId != null) {
                notifyAgent(assignedToAgentId, ticketId, userId, ticketType, priority, subject);
            }
            
            updateSupportMetrics(ticketType, ticketStatus, priority, category, channel);
            
            auditSupportTicket(ticketId, userId, ticketType, ticketStatus, priority, category, 
                    processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Support ticket processed - TicketId: {}, Type: {}, Status: {}, ProcessingTime: {}ms", 
                    ticketId, ticketType, ticketStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Support ticket processing failed - TicketId: {}, UserId: {}, Status: {}, Error: {}", 
                    ticketId, userId, ticketStatus, e.getMessage(), e);
            
            if (ticketId != null && userId != null) {
                handleTicketFailure(ticketId, userId, ticketStatus, e);
            }
            
            throw new RuntimeException("Support ticket processing failed", e);
        }
    }
    
    private void validateSupportTicket(UUID ticketId, UUID userId, String ticketType,
                                      String ticketStatus, String priority, String category) {
        if (ticketId == null || userId == null) {
            throw new IllegalArgumentException("Ticket ID and User ID are required");
        }
        
        if (ticketType == null || ticketType.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket type is required");
        }
        
        if (ticketStatus == null || ticketStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket status is required");
        }
        
        if (priority == null || priority.trim().isEmpty()) {
            throw new IllegalArgumentException("Priority is required");
        }
        
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category is required");
        }
        
        log.debug("Support ticket validation passed - TicketId: {}", ticketId);
    }
    
    private void processTicketByType(UUID ticketId, UUID userId, String ticketType, String ticketStatus,
                                    String priority, String category, String subject, String description,
                                    LocalDateTime createdAt, UUID assignedToAgentId, String assignedToTeam,
                                    List<String> tags, String channel, String language,
                                    Boolean requiresEscalation, UUID relatedTransactionId,
                                    UUID relatedAccountId) {
        try {
            switch (ticketType) {
                case "ACCOUNT_ISSUE" -> processAccountIssue(ticketId, userId, category, subject, 
                        description, relatedAccountId);
                
                case "TRANSACTION_ISSUE" -> processTransactionIssue(ticketId, userId, category, 
                        subject, description, relatedTransactionId);
                
                case "PAYMENT_DISPUTE" -> processPaymentDispute(ticketId, userId, subject, 
                        description, relatedTransactionId);
                
                case "TECHNICAL_ISSUE" -> processTechnicalIssue(ticketId, userId, category, 
                        subject, description, channel);
                
                case "FEATURE_REQUEST" -> processFeatureRequest(ticketId, userId, subject, 
                        description, tags);
                
                case "COMPLAINT" -> processComplaint(ticketId, userId, category, subject, 
                        description, priority);
                
                case "INQUIRY" -> processInquiry(ticketId, userId, category, subject, description);
                
                default -> {
                    log.warn("Unknown support ticket type: {}", ticketType);
                    processGenericTicket(ticketId, userId, ticketType);
                }
            }
            
            log.debug("Ticket type processing completed - TicketId: {}, Type: {}", ticketId, ticketType);
            
        } catch (Exception e) {
            log.error("Failed to process ticket by type - TicketId: {}, Type: {}", ticketId, ticketType, e);
            throw new RuntimeException("Ticket type processing failed", e);
        }
    }
    
    private void processAccountIssue(UUID ticketId, UUID userId, String category, String subject,
                                    String description, UUID relatedAccountId) {
        log.info("Processing ACCOUNT ISSUE ticket - TicketId: {}, Category: {}", ticketId, category);
        
        supportTicketService.processAccountIssue(ticketId, userId, category, subject, description, 
                relatedAccountId);
    }
    
    private void processTransactionIssue(UUID ticketId, UUID userId, String category, String subject,
                                        String description, UUID relatedTransactionId) {
        log.info("Processing TRANSACTION ISSUE ticket - TicketId: {}, Category: {}", ticketId, category);
        
        supportTicketService.processTransactionIssue(ticketId, userId, category, subject, 
                description, relatedTransactionId);
    }
    
    private void processPaymentDispute(UUID ticketId, UUID userId, String subject, String description,
                                      UUID relatedTransactionId) {
        log.warn("Processing PAYMENT DISPUTE ticket - TicketId: {}, TransactionId: {}", 
                ticketId, relatedTransactionId);
        
        supportTicketService.processPaymentDispute(ticketId, userId, subject, description, 
                relatedTransactionId);
    }
    
    private void processTechnicalIssue(UUID ticketId, UUID userId, String category, String subject,
                                      String description, String channel) {
        log.info("Processing TECHNICAL ISSUE ticket - TicketId: {}, Category: {}, Channel: {}", 
                ticketId, category, channel);
        
        supportTicketService.processTechnicalIssue(ticketId, userId, category, subject, 
                description, channel);
    }
    
    private void processFeatureRequest(UUID ticketId, UUID userId, String subject, String description,
                                      List<String> tags) {
        log.info("Processing FEATURE REQUEST ticket - TicketId: {}, Tags: {}", ticketId, tags.size());
        
        supportTicketService.processFeatureRequest(ticketId, userId, subject, description, tags);
    }
    
    private void processComplaint(UUID ticketId, UUID userId, String category, String subject,
                                 String description, String priority) {
        log.warn("Processing COMPLAINT ticket - TicketId: {}, Category: {}, Priority: {}", 
                ticketId, category, priority);
        
        supportTicketService.processComplaint(ticketId, userId, category, subject, description, 
                priority);
    }
    
    private void processInquiry(UUID ticketId, UUID userId, String category, String subject,
                               String description) {
        log.info("Processing INQUIRY ticket - TicketId: {}, Category: {}", ticketId, category);
        
        supportTicketService.processInquiry(ticketId, userId, category, subject, description);
    }
    
    private void processGenericTicket(UUID ticketId, UUID userId, String ticketType) {
        log.info("Processing generic support ticket - TicketId: {}, Type: {}", ticketId, ticketType);
        
        supportTicketService.processGeneric(ticketId, userId, ticketType);
    }
    
    private void handleNewTicket(UUID ticketId, UUID userId, String ticketType, String priority,
                                String category, String subject, String description, List<String> tags,
                                String channel, Boolean requiresEscalation) {
        try {
            log.info("Processing new support ticket - TicketId: {}, Priority: {}, Category: {}", 
                    ticketId, priority, category);
            
            supportTicketService.recordNewTicket(ticketId, userId, ticketType, priority, category, 
                    subject, description, tags, channel);
            
            if (requiresEscalation) {
                supportTicketService.flagForImmediateEscalation(ticketId, priority, category);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle new ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void handleAssignedTicket(UUID ticketId, UUID userId, UUID assignedToAgentId,
                                     String assignedToTeam, String priority) {
        try {
            log.info("Processing assigned ticket - TicketId: {}, AgentId: {}, Team: {}", 
                    ticketId, assignedToAgentId, assignedToTeam);
            
            supportTicketService.recordTicketAssignment(ticketId, userId, assignedToAgentId, 
                    assignedToTeam);
            
        } catch (Exception e) {
            log.error("Failed to handle assigned ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void handleResolvedTicket(UUID ticketId, UUID userId, String ticketType, String category,
                                     UUID assignedToAgentId) {
        try {
            log.info("Processing resolved ticket - TicketId: {}, Type: {}, Category: {}", 
                    ticketId, ticketType, category);
            
            supportTicketService.recordTicketResolution(ticketId, userId, ticketType, category, 
                    assignedToAgentId);
            
            supportTicketService.requestFeedback(ticketId, userId);
            
        } catch (Exception e) {
            log.error("Failed to handle resolved ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void handleEscalatedTicket(UUID ticketId, UUID userId, String ticketType, String priority,
                                      String category, String assignedToTeam) {
        try {
            log.warn("Processing escalated ticket - TicketId: {}, Type: {}, Priority: {}, Team: {}", 
                    ticketId, ticketType, priority, assignedToTeam);
            
            supportTicketService.recordTicketEscalation(ticketId, userId, ticketType, priority, 
                    category, assignedToTeam);
            
        } catch (Exception e) {
            log.error("Failed to handle escalated ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void escalateTicket(UUID ticketId, UUID userId, String ticketType, String priority,
                               String category, String assignedToTeam) {
        try {
            log.warn("Escalating ticket - TicketId: {}, Priority: {}, Category: {}", 
                    ticketId, priority, category);
            
            supportTicketService.escalateTicket(ticketId, userId, ticketType, priority, category, 
                    assignedToTeam);
            
        } catch (Exception e) {
            log.error("Failed to escalate ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void routeTicket(UUID ticketId, String ticketType, String category, String priority,
                            List<String> tags, String language, String channel) {
        try {
            ticketRoutingService.routeTicket(ticketId, ticketType, category, priority, tags, 
                    language, channel);
            
            log.debug("Ticket routed - TicketId: {}, Category: {}", ticketId, category);
            
        } catch (Exception e) {
            log.error("Failed to route ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID ticketId, String ticketType, String ticketStatus,
                           String subject, String channel) {
        try {
            supportNotificationService.sendTicketNotification(userId, ticketId, ticketType, 
                    ticketStatus, subject, channel);
            
            log.info("User notified - UserId: {}, TicketId: {}, Status: {}", userId, ticketId, 
                    ticketStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, TicketId: {}", userId, ticketId, e);
        }
    }
    
    private void notifyAgent(UUID agentId, UUID ticketId, UUID userId, String ticketType,
                            String priority, String subject) {
        try {
            supportNotificationService.sendAgentNotification(agentId, ticketId, userId, ticketType, 
                    priority, subject);
            
            log.info("Agent notified - AgentId: {}, TicketId: {}", agentId, ticketId);
            
        } catch (Exception e) {
            log.error("Failed to notify agent - AgentId: {}, TicketId: {}", agentId, ticketId, e);
        }
    }
    
    private void updateSupportMetrics(String ticketType, String ticketStatus, String priority,
                                     String category, String channel) {
        try {
            supportTicketService.updateTicketMetrics(ticketType, ticketStatus, priority, category, 
                    channel);
        } catch (Exception e) {
            log.error("Failed to update support metrics - Type: {}, Status: {}", ticketType, 
                    ticketStatus, e);
        }
    }
    
    private void auditSupportTicket(UUID ticketId, UUID userId, String ticketType, String ticketStatus,
                                   String priority, String category, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditEvent(
                    "SUPPORT_TICKET_PROCESSED",
                    userId.toString(),
                    String.format("Support ticket %s - Type: %s, Priority: %s, Category: %s", 
                            ticketStatus, ticketType, priority, category),
                    Map.of(
                            "ticketId", ticketId.toString(),
                            "userId", userId.toString(),
                            "ticketType", ticketType,
                            "ticketStatus", ticketStatus,
                            "priority", priority,
                            "category", category,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit support ticket - TicketId: {}", ticketId, e);
        }
    }
    
    private void handleTicketFailure(UUID ticketId, UUID userId, String ticketStatus, Exception error) {
        try {
            supportTicketService.handleTicketFailure(ticketId, userId, ticketStatus, error.getMessage());
            
            auditService.auditEvent(
                    "SUPPORT_TICKET_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process support ticket: " + error.getMessage(),
                    Map.of(
                            "ticketId", ticketId.toString(),
                            "userId", userId.toString(),
                            "ticketStatus", ticketStatus != null ? ticketStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle ticket failure - TicketId: {}", ticketId, e);
        }
    }
    
    @KafkaListener(
        topics = {"support-ticket-events.DLQ", "support-tickets.DLQ"},
        groupId = "support-service-ticket-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Support ticket event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID ticketId = event.containsKey("ticketId") ? 
                    UUID.fromString((String) event.get("ticketId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String ticketType = (String) event.get("ticketType");
            
            log.error("DLQ: Support ticket failed permanently - TicketId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    ticketId, userId, ticketType);
            
            if (ticketId != null && userId != null) {
                supportTicketService.markForManualReview(ticketId, userId, ticketType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse support ticket DLQ event: {}", eventJson, e);
        }
    }
}