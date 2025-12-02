package com.waqiti.support.service;

import com.waqiti.support.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TicketService {
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final AgentService agentService;
    private final SLAService slaService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final AIAssistantService aiAssistantService;
    private final TicketNumberGenerator ticketNumberGenerator;

    @Transactional
    public TicketDTO createTicket(CreateTicketRequest request) {
        log.info("Creating ticket for user: {}", request.getUserId());

        // Check if AI can handle this automatically
        if (request.isAllowAiAssist()) {
            var aiResponse = aiAssistantService.tryAutoResolve(request);
            if (aiResponse.isResolved()) {
                return createResolvedTicket(request, aiResponse);
            }
        }

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumberGenerator.generateTicketNumber())
                .userId(request.getUserId())
                .subject(request.getSubject())
                .description(request.getDescription())
                .status(Ticket.TicketStatus.NEW)
                .priority(determinePriority(request))
                .category(request.getCategory())
                .channel(request.getChannel())
                .tags(extractTags(request))
                .metadata(request.getMetadata())
                .build();

        // Calculate SLA breach time
        LocalDateTime slaBreachTime = slaService.calculateSLABreachTime(
                ticket.getPriority(), 
                ticket.getCategory()
        );
        ticket.setSlaBreachAt(slaBreachTime);

        // Auto-assign if possible
        Optional<String> assignedAgent = agentService.autoAssignAgent(ticket);
        assignedAgent.ifPresent(ticket::setAssignedAgentId);

        ticket = ticketRepository.save(ticket);

        // Create initial message
        TicketMessage initialMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderId(request.getUserId())
                .senderName(getUserName(request.getUserId()))
                .senderType(TicketMessage.SenderType.CUSTOMER)
                .content(request.getDescription())
                .messageType(TicketMessage.MessageType.REPLY)
                .build();
        messageRepository.save(initialMessage);

        // Send notifications
        notificationService.notifyTicketCreated(ticket);

        // Get AI suggestions for agent
        if (ticket.getAssignedAgentId() != null) {
            aiAssistantService.generateAgentSuggestions(ticket);
        }

        return mapToDTO(ticket);
    }

    @Transactional
    public TicketDTO updateTicket(String ticketId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        boolean statusChanged = false;
        Ticket.TicketStatus oldStatus = ticket.getStatus();

        if (request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            statusChanged = true;
            ticket.setStatus(request.getStatus());
            
            // Update timestamps based on status
            switch (request.getStatus()) {
                case RESOLVED:
                    ticket.setResolvedAt(LocalDateTime.now());
                    break;
                case CLOSED:
                    ticket.setClosedAt(LocalDateTime.now());
                    break;
                case REOPENED:
                    ticket.setClosedAt(null);
                    ticket.setResolvedAt(null);
                    break;
            }
        }

        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
            // Recalculate SLA if priority changed
            LocalDateTime newSlaBreachTime = slaService.calculateSLABreachTime(
                    request.getPriority(), 
                    ticket.getCategory()
            );
            ticket.setSlaBreachAt(newSlaBreachTime);
        }

        if (request.getAssignedAgentId() != null) {
            ticket.setAssignedAgentId(request.getAssignedAgentId());
        }

        if (request.getTags() != null) {
            ticket.setTags(request.getTags());
        }

        ticket = ticketRepository.save(ticket);

        // Send notifications for status changes
        if (statusChanged) {
            notificationService.notifyTicketStatusChanged(ticket, oldStatus);
        }

        return mapToDTO(ticket);
    }

    @Transactional
    public TicketMessageDTO addMessage(String ticketId, AddMessageRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .senderType(request.getSenderType())
                .content(request.getContent())
                .htmlContent(request.getHtmlContent())
                .isInternal(request.isInternal())
                .messageType(request.getMessageType())
                .metadata(request.getMetadata())
                .build();

        message = messageRepository.save(message);

        // Update ticket status if needed
        if (message.getSenderType() == TicketMessage.SenderType.CUSTOMER 
                && ticket.getStatus() == Ticket.TicketStatus.PENDING_CUSTOMER) {
            ticket.setStatus(Ticket.TicketStatus.OPEN);
            ticketRepository.save(ticket);
        } else if (message.getSenderType() == TicketMessage.SenderType.AGENT 
                && ticket.getStatus() == Ticket.TicketStatus.NEW) {
            ticket.setStatus(Ticket.TicketStatus.IN_PROGRESS);
            if (ticket.getFirstResponseAt() == null) {
                ticket.setFirstResponseAt(LocalDateTime.now());
            }
            ticketRepository.save(ticket);
        }

        // Send notifications
        if (!message.isInternal()) {
            notificationService.notifyNewMessage(ticket, message);
        }

        return mapToMessageDTO(message);
    }

    public Page<TicketDTO> getUserTickets(String userId, TicketFilterRequest filter, Pageable pageable) {
        Specification<Ticket> spec = Specification.where(TicketSpecifications.byUserId(userId));

        if (filter.getStatus() != null) {
            spec = spec.and(TicketSpecifications.byStatus(filter.getStatus()));
        }
        if (filter.getCategory() != null) {
            spec = spec.and(TicketSpecifications.byCategory(filter.getCategory()));
        }
        if (filter.getPriority() != null) {
            spec = spec.and(TicketSpecifications.byPriority(filter.getPriority()));
        }
        if (filter.getSearchTerm() != null && !filter.getSearchTerm().isEmpty()) {
            spec = spec.and(TicketSpecifications.search(filter.getSearchTerm()));
        }

        return ticketRepository.findAll(spec, pageable).map(this::mapToDTO);
    }

    public Page<TicketDTO> getAgentTickets(String agentId, TicketFilterRequest filter, Pageable pageable) {
        Specification<Ticket> spec = Specification.where(TicketSpecifications.byAgentId(agentId));

        // Apply filters
        if (filter.getStatus() != null) {
            spec = spec.and(TicketSpecifications.byStatus(filter.getStatus()));
        }
        if (filter.getCategory() != null) {
            spec = spec.and(TicketSpecifications.byCategory(filter.getCategory()));
        }
        if (filter.getPriority() != null) {
            spec = spec.and(TicketSpecifications.byPriority(filter.getPriority()));
        }

        return ticketRepository.findAll(spec, pageable).map(this::mapToDTO);
    }

    @Transactional
    public void escalateTicket(String ticketId, EscalateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.setEscalated(true);
        ticket.setEscalationReason(request.getReason());
        
        // Increase priority if not already critical
        if (ticket.getPriority() != Ticket.TicketPriority.CRITICAL) {
            ticket.setPriority(getNextPriority(ticket.getPriority()));
        }

        // Reassign to senior agent
        Optional<String> seniorAgent = agentService.assignToSeniorAgent(ticket);
        seniorAgent.ifPresent(ticket::setAssignedAgentId);

        ticketRepository.save(ticket);

        // Add escalation message
        TicketMessage escalationMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderId("SYSTEM")
                .senderName("System")
                .senderType(TicketMessage.SenderType.SYSTEM)
                .content("Ticket escalated: " + request.getReason())
                .messageType(TicketMessage.MessageType.ESCALATION)
                .isInternal(true)
                .build();
        messageRepository.save(escalationMessage);

        notificationService.notifyTicketEscalated(ticket);
    }

    @Transactional
    public void submitSatisfactionRating(String ticketId, SatisfactionRatingRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.setSatisfactionRating(request.getRating());
        ticket.setSatisfactionComment(request.getComment());
        ticketRepository.save(ticket);

        // Add satisfaction survey message
        TicketMessage surveyMessage = TicketMessage.builder()
                .ticket(ticket)
                .senderId(ticket.getUserId())
                .senderName(getUserName(ticket.getUserId()))
                .senderType(TicketMessage.SenderType.CUSTOMER)
                .content(String.format("Satisfaction Rating: %d/5\nComment: %s", 
                        request.getRating(), request.getComment()))
                .messageType(TicketMessage.MessageType.SATISFACTION_SURVEY)
                .build();
        messageRepository.save(surveyMessage);
    }

    public TicketAnalyticsDTO getTicketAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        List<Ticket> tickets = ticketRepository.findByCreatedAtBetween(startDate, endDate);

        return TicketAnalyticsDTO.builder()
                .totalTickets(tickets.size())
                .resolvedTickets(tickets.stream()
                        .filter(t -> t.getStatus() == Ticket.TicketStatus.RESOLVED 
                                || t.getStatus() == Ticket.TicketStatus.CLOSED)
                        .count())
                .averageResolutionTime(calculateAverageResolutionTime(tickets))
                .averageFirstResponseTime(calculateAverageFirstResponseTime(tickets))
                .ticketsByCategory(groupByCategory(tickets))
                .ticketsByPriority(groupByPriority(tickets))
                .satisfactionScore(calculateAverageSatisfaction(tickets))
                .slaBreachRate(calculateSLABreachRate(tickets))
                .build();
    }

    private Ticket.TicketPriority determinePriority(CreateTicketRequest request) {
        // Use AI to determine priority based on content
        if (request.getDescription().toLowerCase().contains("urgent") ||
            request.getDescription().toLowerCase().contains("critical") ||
            request.getDescription().toLowerCase().contains("immediately")) {
            return Ticket.TicketPriority.HIGH;
        }

        // Category-based priority
        return switch (request.getCategory()) {
            case SECURITY, PAYMENT -> Ticket.TicketPriority.HIGH;
            case TECHNICAL, BILLING -> Ticket.TicketPriority.MEDIUM;
            default -> Ticket.TicketPriority.LOW;
        };
    }

    private List<String> extractTags(CreateTicketRequest request) {
        List<String> tags = new ArrayList<>();
        
        // Extract from subject and description
        String content = request.getSubject() + " " + request.getDescription();
        
        // Common tag patterns
        if (content.toLowerCase().contains("refund")) tags.add("refund");
        if (content.toLowerCase().contains("transfer")) tags.add("transfer");
        if (content.toLowerCase().contains("card")) tags.add("card");
        if (content.toLowerCase().contains("account")) tags.add("account");
        if (content.toLowerCase().contains("security")) tags.add("security");
        
        return tags;
    }

    private String getUserName(String userId) {
        return userService.getUserById(userId)
                .map(user -> user.getFirstName() + " " + user.getLastName())
                .orElse("Unknown User");
    }

    private Ticket.TicketPriority getNextPriority(Ticket.TicketPriority current) {
        return switch (current) {
            case LOW -> Ticket.TicketPriority.MEDIUM;
            case MEDIUM -> Ticket.TicketPriority.HIGH;
            case HIGH -> Ticket.TicketPriority.URGENT;
            case URGENT, CRITICAL -> Ticket.TicketPriority.CRITICAL;
        };
    }

    private TicketDTO mapToDTO(Ticket ticket) {
        return TicketDTO.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .userId(ticket.getUserId())
                .assignedAgentId(ticket.getAssignedAgentId())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .category(ticket.getCategory())
                .channel(ticket.getChannel())
                .tags(ticket.getTags())
                .messageCount(ticket.getMessages().size())
                .lastMessageAt(ticket.getMessages().isEmpty() ? null : 
                        ticket.getMessages().get(ticket.getMessages().size() - 1).getCreatedAt())
                .satisfactionRating(ticket.getSatisfactionRating())
                .isEscalated(ticket.isEscalated())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .build();
    }

    private TicketMessageDTO mapToMessageDTO(TicketMessage message) {
        return TicketMessageDTO.builder()
                .id(message.getId())
                .ticketId(message.getTicket().getId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderType(message.getSenderType())
                .content(message.getContent())
                .htmlContent(message.getHtmlContent())
                .isInternal(message.isInternal())
                .messageType(message.getMessageType())
                .createdAt(message.getCreatedAt())
                .build();
    }
}