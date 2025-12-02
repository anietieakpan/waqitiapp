package com.waqiti.support.event;

import com.waqiti.support.domain.Ticket;
import com.waqiti.support.domain.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SupportEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(SupportEventPublisher.class);
    
    private static final String TICKET_EVENTS_TOPIC = "support.ticket.events";
    private static final String CHAT_EVENTS_TOPIC = "support.chat.events";
    private static final String ESCALATION_EVENTS_TOPIC = "support.escalation.events";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public SupportEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void publishTicketCreatedEvent(Ticket ticket) {
        try {
            TicketEvent event = TicketEvent.builder()
                    .eventType(TicketEventType.CREATED)
                    .ticketId(ticket.getId())
                    .userId(ticket.getUserId())
                    .priority(ticket.getPriority())
                    .category(ticket.getCategory())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TICKET_EVENTS_TOPIC, ticket.getId(), eventJson);
            logger.info("Published ticket created event for ticket: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("Failed to publish ticket created event", e);
        }
    }
    
    public void publishTicketUpdatedEvent(Ticket ticket) {
        try {
            TicketEvent event = TicketEvent.builder()
                    .eventType(TicketEventType.UPDATED)
                    .ticketId(ticket.getId())
                    .userId(ticket.getUserId())
                    .status(ticket.getStatus())
                    .priority(ticket.getPriority())
                    .assignedAgent(ticket.getAssignedAgent())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TICKET_EVENTS_TOPIC, ticket.getId(), eventJson);
            logger.info("Published ticket updated event for ticket: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("Failed to publish ticket updated event", e);
        }
    }
    
    public void publishTicketClosedEvent(Ticket ticket) {
        try {
            TicketEvent event = TicketEvent.builder()
                    .eventType(TicketEventType.CLOSED)
                    .ticketId(ticket.getId())
                    .userId(ticket.getUserId())
                    .resolutionTime(ticket.getResolutionTime())
                    .satisfactionRating(ticket.getSatisfactionRating())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TICKET_EVENTS_TOPIC, ticket.getId(), eventJson);
            logger.info("Published ticket closed event for ticket: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("Failed to publish ticket closed event", e);
        }
    }
    
    public void publishTicketEscalatedEvent(Ticket ticket, String reason) {
        try {
            EscalationEvent event = EscalationEvent.builder()
                    .ticketId(ticket.getId())
                    .userId(ticket.getUserId())
                    .fromAgent(ticket.getAssignedAgent())
                    .escalationLevel(ticket.getEscalationLevel())
                    .reason(reason)
                    .priority(ticket.getPriority())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ESCALATION_EVENTS_TOPIC, ticket.getId(), eventJson);
            logger.info("Published ticket escalation event for ticket: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("Failed to publish ticket escalation event", e);
        }
    }
    
    public void publishChatSessionStartedEvent(ChatSession session) {
        try {
            ChatEvent event = ChatEvent.builder()
                    .eventType(ChatEventType.SESSION_STARTED)
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .agentId(session.getAgentId())
                    .channel(session.getChannel())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(CHAT_EVENTS_TOPIC, session.getId(), eventJson);
            logger.info("Published chat session started event for session: {}", session.getId());
        } catch (Exception e) {
            logger.error("Failed to publish chat session started event", e);
        }
    }
    
    public void publishChatSessionEndedEvent(ChatSession session) {
        try {
            ChatEvent event = ChatEvent.builder()
                    .eventType(ChatEventType.SESSION_ENDED)
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .agentId(session.getAgentId())
                    .duration(session.getDuration())
                    .messageCount(session.getMessageCount())
                    .satisfactionRating(session.getSatisfactionRating())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(CHAT_EVENTS_TOPIC, session.getId(), eventJson);
            logger.info("Published chat session ended event for session: {}", session.getId());
        } catch (Exception e) {
            logger.error("Failed to publish chat session ended event", e);
        }
    }
    
    public void publishAgentHandoffEvent(ChatSession session, String fromAgent, String toAgent) {
        try {
            ChatEvent event = ChatEvent.builder()
                    .eventType(ChatEventType.AGENT_HANDOFF)
                    .sessionId(session.getId())
                    .userId(session.getUserId())
                    .fromAgent(fromAgent)
                    .toAgent(toAgent)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(CHAT_EVENTS_TOPIC, session.getId(), eventJson);
            logger.info("Published agent handoff event for session: {}", session.getId());
        } catch (Exception e) {
            logger.error("Failed to publish agent handoff event", e);
        }
    }
}