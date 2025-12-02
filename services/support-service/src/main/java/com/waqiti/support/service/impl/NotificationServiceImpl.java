package com.waqiti.support.service.impl;

import com.waqiti.support.domain.Ticket;
import com.waqiti.support.domain.TicketMessage;
import com.waqiti.support.dto.LiveChatSession;
import com.waqiti.support.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final JavaMailSender mailSender;
    
    private static final String TICKET_NOTIFICATIONS_TOPIC = "support.ticket.notifications";
    private static final String AGENT_NOTIFICATIONS_TOPIC = "support.agent.notifications";
    private static final String CUSTOMER_NOTIFICATIONS_TOPIC = "support.customer.notifications";
    
    @Override
    public void notifyTicketCreated(Ticket ticket) {
        log.info("Sending ticket created notification for ticket: {}", ticket.getId());
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_CREATED", ticket);
            notification.put("message", "New support ticket created: " + ticket.getTicketNumber());
            
            // Send to Kafka for processing
            kafkaTemplate.send(TICKET_NOTIFICATIONS_TOPIC, ticket.getId(), notification);
            
            // Send real-time notification if customer is online
            sendWebSocketNotification("/topic/tickets/" + ticket.getUserId(), notification);
            
            // Send email to customer
            sendEmailNotification(ticket.getUserEmail(), 
                "Support Ticket Created - " + ticket.getTicketNumber(),
                buildTicketCreatedEmailContent(ticket));
                
        } catch (Exception e) {
            log.error("Failed to send ticket created notification for ticket: {}", ticket.getId(), e);
        }
    }
    
    @Override
    public void notifyTicketAssigned(Ticket ticket, String agentId) {
        log.info("Sending ticket assigned notification for ticket: {} to agent: {}", ticket.getId(), agentId);
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_ASSIGNED", ticket);
            notification.put("agentId", agentId);
            notification.put("message", "Ticket " + ticket.getTicketNumber() + " has been assigned to you");
            
            kafkaTemplate.send(AGENT_NOTIFICATIONS_TOPIC, agentId, notification);
            sendWebSocketNotification("/topic/agents/" + agentId, notification);
            
        } catch (Exception e) {
            log.error("Failed to send ticket assigned notification", e);
        }
    }
    
    @Override
    public void notifyTicketStatusChanged(Ticket ticket, String oldStatus, String newStatus) {
        log.info("Sending status change notification for ticket: {} from {} to {}", 
                ticket.getId(), oldStatus, newStatus);
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_STATUS_CHANGED", ticket);
            notification.put("oldStatus", oldStatus);
            notification.put("newStatus", newStatus);
            notification.put("message", "Ticket " + ticket.getTicketNumber() + " status changed to " + newStatus);
            
            kafkaTemplate.send(CUSTOMER_NOTIFICATIONS_TOPIC, ticket.getUserId(), notification);
            sendWebSocketNotification("/topic/tickets/" + ticket.getUserId(), notification);
            
            // Notify assigned agent if exists
            if (ticket.getAssignedToAgentId() != null) {
                sendWebSocketNotification("/topic/agents/" + ticket.getAssignedToAgentId(), notification);
            }
            
        } catch (Exception e) {
            log.error("Failed to send status change notification", e);
        }
    }
    
    @Override
    public void notifyTicketEscalated(Ticket ticket, String escalatedTo) {
        log.info("Sending escalation notification for ticket: {} to: {}", ticket.getId(), escalatedTo);
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_ESCALATED", ticket);
            notification.put("escalatedTo", escalatedTo);
            notification.put("message", "Ticket " + ticket.getTicketNumber() + " has been escalated");
            
            kafkaTemplate.send(AGENT_NOTIFICATIONS_TOPIC, escalatedTo, notification);
            sendWebSocketNotification("/topic/agents/" + escalatedTo, notification);
            
        } catch (Exception e) {
            log.error("Failed to send escalation notification", e);
        }
    }
    
    @Override
    public void notifyTicketResolved(Ticket ticket) {
        log.info("Sending ticket resolved notification for ticket: {}", ticket.getId());
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_RESOLVED", ticket);
            notification.put("message", "Your support ticket " + ticket.getTicketNumber() + " has been resolved");
            
            kafkaTemplate.send(CUSTOMER_NOTIFICATIONS_TOPIC, ticket.getUserId(), notification);
            sendWebSocketNotification("/topic/tickets/" + ticket.getUserId(), notification);
            
            // Send satisfaction survey request
            notifyCustomerSatisfactionRequest(ticket.getUserId(), ticket.getId());
            
        } catch (Exception e) {
            log.error("Failed to send ticket resolved notification", e);
        }
    }
    
    @Override
    public void notifyTicketClosed(Ticket ticket) {
        log.info("Sending ticket closed notification for ticket: {}", ticket.getId());
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_CLOSED", ticket);
            notification.put("message", "Support ticket " + ticket.getTicketNumber() + " has been closed");
            
            kafkaTemplate.send(CUSTOMER_NOTIFICATIONS_TOPIC, ticket.getUserId(), notification);
            sendWebSocketNotification("/topic/tickets/" + ticket.getUserId(), notification);
            
        } catch (Exception e) {
            log.error("Failed to send ticket closed notification", e);
        }
    }
    
    @Override
    public void notifyTicketReopened(Ticket ticket) {
        log.info("Sending ticket reopened notification for ticket: {}", ticket.getId());
        
        try {
            Map<String, Object> notification = createBaseNotification("TICKET_REOPENED", ticket);
            notification.put("message", "Ticket " + ticket.getTicketNumber() + " has been reopened");
            
            kafkaTemplate.send(TICKET_NOTIFICATIONS_TOPIC, ticket.getId(), notification);
            
            // Notify customer and agent
            sendWebSocketNotification("/topic/tickets/" + ticket.getUserId(), notification);
            if (ticket.getAssignedToAgentId() != null) {
                sendWebSocketNotification("/topic/agents/" + ticket.getAssignedToAgentId(), notification);
            }
            
        } catch (Exception e) {
            log.error("Failed to send ticket reopened notification", e);
        }
    }
    
    @Override
    public void notifySLABreach(Ticket ticket, String breachType) {
        log.warn("SLA breach notification for ticket: {} - breach type: {}", ticket.getId(), breachType);
        
        try {
            Map<String, Object> notification = createBaseNotification("SLA_BREACH", ticket);
            notification.put("breachType", breachType);
            notification.put("message", "SLA breach detected for ticket " + ticket.getTicketNumber());
            notification.put("priority", "HIGH");
            
            kafkaTemplate.send(AGENT_NOTIFICATIONS_TOPIC, "sla-breach", notification);
            
            // Notify manager or escalation team
            sendWebSocketNotification("/topic/alerts/sla-breach", notification);
            
        } catch (Exception e) {
            log.error("Failed to send SLA breach notification", e);
        }
    }
    
    @Override
    public void notifyNewMessage(TicketMessage message) {
        log.info("Sending new message notification for ticket: {}", message.getTicket().getId());
        
        try {
            Map<String, Object> notification = createBaseMessageNotification("NEW_MESSAGE", message);
            
            kafkaTemplate.send(TICKET_NOTIFICATIONS_TOPIC, message.getTicket().getId(), notification);
            
            // Notify based on sender type
            if (message.getSenderType().name().equals("CUSTOMER")) {
                notifyMessageToAgent(message, message.getTicket().getAssignedToAgentId());
            } else {
                notifyMessageToCustomer(message);
            }
            
        } catch (Exception e) {
            log.error("Failed to send new message notification", e);
        }
    }
    
    @Override
    public void notifyMessageToCustomer(TicketMessage message) {
        try {
            Map<String, Object> notification = createBaseMessageNotification("AGENT_RESPONSE", message);
            
            String userId = message.getTicket().getUserId();
            sendWebSocketNotification("/topic/tickets/" + userId, notification);
            
            // Send email if customer is not online
            if (!isCustomerOnline(userId)) {
                sendEmailNotification(message.getTicket().getUserEmail(),
                    "New Response - " + message.getTicket().getTicketNumber(),
                    buildMessageEmailContent(message));
            }
            
        } catch (Exception e) {
            log.error("Failed to send message notification to customer", e);
        }
    }
    
    @Override
    public void notifyMessageToAgent(TicketMessage message, String agentId) {
        if (agentId == null) return;
        
        try {
            Map<String, Object> notification = createBaseMessageNotification("CUSTOMER_MESSAGE", message);
            
            sendWebSocketNotification("/topic/agents/" + agentId, notification);
            kafkaTemplate.send(AGENT_NOTIFICATIONS_TOPIC, agentId, notification);
            
        } catch (Exception e) {
            log.error("Failed to send message notification to agent", e);
        }
    }
    
    @Override
    public void notifyInternalNote(TicketMessage message, String[] recipientIds) {
        try {
            Map<String, Object> notification = createBaseMessageNotification("INTERNAL_NOTE", message);
            
            for (String recipientId : recipientIds) {
                sendWebSocketNotification("/topic/agents/" + recipientId, notification);
            }
            
        } catch (Exception e) {
            log.error("Failed to send internal note notification", e);
        }
    }
    
    @Override
    public void notifyChatSessionStarted(LiveChatSession session) {
        log.info("Sending chat session started notification for session: {}", session.getSessionId());
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "CHAT_SESSION_STARTED",
                "sessionId", session.getSessionId(),
                "userId", session.getUserId(),
                "timestamp", LocalDateTime.now(),
                "message", "New chat session started"
            );
            
            sendWebSocketNotification("/topic/chat/" + session.getSessionId(), notification);
            
        } catch (Exception e) {
            log.error("Failed to send chat session started notification", e);
        }
    }
    
    @Override
    public void notifyChatSessionAssigned(LiveChatSession session, String agentId) {
        try {
            Map<String, Object> notification = Map.of(
                "type", "CHAT_ASSIGNED",
                "sessionId", session.getSessionId(),
                "agentId", agentId,
                "agentName", session.getAgentName(),
                "timestamp", LocalDateTime.now(),
                "message", "Chat assigned to " + session.getAgentName()
            );
            
            sendWebSocketNotification("/topic/chat/" + session.getSessionId(), notification);
            sendWebSocketNotification("/topic/agents/" + agentId, notification);
            
        } catch (Exception e) {
            log.error("Failed to send chat assignment notification", e);
        }
    }
    
    @Override
    public void notifyChatSessionEnded(LiveChatSession session) {
        try {
            Map<String, Object> notification = Map.of(
                "type", "CHAT_SESSION_ENDED",
                "sessionId", session.getSessionId(),
                "timestamp", LocalDateTime.now(),
                "message", "Chat session ended"
            );
            
            sendWebSocketNotification("/topic/chat/" + session.getSessionId(), notification);
            
        } catch (Exception e) {
            log.error("Failed to send chat session ended notification", e);
        }
    }
    
    @Override
    public void notifyChatMessageReceived(String sessionId, String message, String senderId) {
        try {
            Map<String, Object> notification = Map.of(
                "type", "CHAT_MESSAGE_RECEIVED",
                "sessionId", sessionId,
                "senderId", senderId,
                "message", message,
                "timestamp", LocalDateTime.now()
            );
            
            sendWebSocketNotification("/topic/chat/" + sessionId, notification);
            
        } catch (Exception e) {
            log.error("Failed to send chat message notification", e);
        }
    }
    
    // Additional methods implementation...
    @Override
    public void notifyAgentNewAssignment(String agentId, String ticketId) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyAgentTicketUpdate(String agentId, String ticketId) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyAgentChatRequest(String agentId, String sessionId) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyAgentWorkloadAlert(String agentId, int currentLoad, int maxLoad) {
        // Implementation similar to above
    }
    
    @Override
    public void notifySystemAlert(String alertType, String message, String[] recipientIds) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyMaintenanceScheduled(String maintenanceMessage) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyServiceOutage(String outageMessage) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyBulkTicketUpdate(String[] ticketIds, String updateType, String message) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyTeamAlert(String teamId, String alertType, String message) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyCustomerTicketUpdate(String customerId, String ticketId, String updateType) {
        // Implementation similar to above
    }
    
    @Override
    public void notifyCustomerSatisfactionRequest(String customerId, String ticketId) {
        log.info("Sending satisfaction survey request for customer: {} ticket: {}", customerId, ticketId);
        
        try {
            Map<String, Object> notification = Map.of(
                "type", "SATISFACTION_SURVEY",
                "customerId", customerId,
                "ticketId", ticketId,
                "timestamp", LocalDateTime.now(),
                "message", "Please rate your support experience"
            );
            
            kafkaTemplate.send(CUSTOMER_NOTIFICATIONS_TOPIC, customerId, notification);
            sendWebSocketNotification("/topic/tickets/" + customerId, notification);
            
        } catch (Exception e) {
            log.error("Failed to send satisfaction survey request", e);
        }
    }
    
    @Override
    public void notifyCustomerAutoResponse(String customerId, String ticketId, String response) {
        // Implementation similar to above
    }
    
    // Helper methods
    private Map<String, Object> createBaseNotification(String type, Ticket ticket) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("ticketId", ticket.getId());
        notification.put("ticketNumber", ticket.getTicketNumber());
        notification.put("subject", ticket.getSubject());
        notification.put("priority", ticket.getPriority().toString());
        notification.put("status", ticket.getStatus().toString());
        notification.put("timestamp", LocalDateTime.now());
        return notification;
    }
    
    private Map<String, Object> createBaseMessageNotification(String type, TicketMessage message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("messageId", message.getId());
        notification.put("ticketId", message.getTicket().getId());
        notification.put("ticketNumber", message.getTicket().getTicketNumber());
        notification.put("senderName", message.getSenderName());
        notification.put("content", message.getContent());
        notification.put("timestamp", LocalDateTime.now());
        return notification;
    }
    
    private void sendWebSocketNotification(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket notification to {}: {}", destination, e.getMessage());
        }
    }
    
    private void sendEmailNotification(String toEmail, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);
            message.setFrom("noreply@example.com");
            
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email notification to {}: {}", toEmail, e.getMessage());
        }
    }
    
    private boolean isCustomerOnline(String userId) {
        // Implementation to check if customer is currently online
        // This could check Redis or WebSocket session store
        return false; // Simplified implementation
    }
    
    private String buildTicketCreatedEmailContent(Ticket ticket) {
        return String.format("""
            Dear %s,
            
            Thank you for contacting Waqiti Support. Your ticket has been created with the following details:
            
            Ticket Number: %s
            Subject: %s
            Priority: %s
            Status: %s
            
            Our team will respond to your inquiry within our standard response time.
            
            You can track the progress of your ticket by visiting our support portal.
            
            Best regards,
            Waqiti Support Team
            """, 
            ticket.getUserName(), 
            ticket.getTicketNumber(), 
            ticket.getSubject(), 
            ticket.getPriority(), 
            ticket.getStatus());
    }
    
    private String buildMessageEmailContent(TicketMessage message) {
        return String.format("""
            You have received a new response for ticket %s:
            
            From: %s
            Message: %s
            
            Please log in to your support portal to view the full conversation.
            
            Best regards,
            Waqiti Support Team
            """, 
            message.getTicket().getTicketNumber(), 
            message.getSenderName(), 
            message.getContent());
    }
}