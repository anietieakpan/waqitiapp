package com.waqiti.support.service.impl;

import com.waqiti.support.domain.*;
import com.waqiti.support.dto.*;
import com.waqiti.support.repository.*;
import com.waqiti.support.exception.*;
import com.waqiti.support.event.*;
import com.waqiti.support.client.*;
import com.waqiti.support.service.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {
    
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketActivityRepository activityRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AIAssistantService aiAssistantService;
    private final UserServiceClient userServiceClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TicketAssignmentService assignmentService;
    private final TicketEscalationService escalationService;
    private final TicketCategorizationService categorizationService;
    
    @Override
    public TicketDTO createTicket(CreateTicketRequest request) {
        log.info("Creating new ticket for user: {}", request.getUserId());
        
        // Validate user
        UserDTO user = userServiceClient.getUser(request.getUserId())
            .orElseThrow(() -> new BusinessException("User not found"));
        
        // Auto-categorize the ticket using ML and rules
        CategorizationResult categorization = categorizationService.categorizeTicket(request);
        
        // Check for spam using the categorization service
        if (categorizationService.isSpamTicket(request.getSubject(), request.getDescription())) {
            throw new SpamTicketException("Ticket appears to be spam");
        }
        
        if (isDuplicateTicket(request)) {
            throw new DuplicateTicketException("Similar ticket already exists");
        }
        
        // Create ticket with auto-categorization results
        Ticket ticket = Ticket.builder()
            .userId(request.getUserId())
            .userEmail(user.getEmail())
            .userName(user.getName())
            .subject(request.getSubject())
            .description(request.getDescription())
            .category(categorization.getSuggestedCategory() != null ? 
                     categorization.getSuggestedCategory() : request.getCategory())
            .subCategory(categorization.getSuggestedSubCategory() != null ? 
                        categorization.getSuggestedSubCategory() : request.getSubCategory())
            .priority(categorization.getSuggestedPriority() != null ? 
                     categorization.getSuggestedPriority() : determinePriority(request))
            .channel(request.getChannel())
            .relatedTransactionId(request.getRelatedTransactionId())
            .relatedPaymentId(request.getRelatedPaymentId())
            .tags(categorization.getExtractedTags() != null ? 
                  categorization.getExtractedTags() : request.getTags())
            .isVip(user.isVip())
            .languageCode(request.getLanguageCode())
            .build();
        
        // Analyze sentiment
        ticket.setSentimentScore(aiAssistantService.analyzeSentiment(request.getDescription()));
        
        ticket = ticketRepository.save(ticket);
        
        // Add initial message
        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .content(request.getDescription())
            .senderId(request.getUserId())
            .senderName(user.getName())
            .senderType(MessageSenderType.CUSTOMER)
            .build();
        
        ticket.addMessage(message);
        messageRepository.save(message);
        
        // Add creation activity
        addActivity(ticket, ActivityType.CREATED, request.getUserId(), user.getName());
        
        // Handle attachments
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            handleAttachments(ticket, message, request.getAttachments(), request.getUserId());
        }
        
        // Auto-assign if enabled
        if (shouldAutoAssign(ticket)) {
            String agentId = assignmentService.findBestAgent(ticket);
            if (agentId != null) {
                assignTicket(ticket.getId(), agentId);
            }
        }
        
        // Send notifications
        notificationService.notifyTicketCreated(ticket);
        
        // Publish event
        eventPublisher.publishEvent(new TicketCreatedEvent(ticket));
        
        // Check for auto-response
        String autoResponse = aiAssistantService.generateAutoResponse(ticket);
        if (autoResponse != null) {
            addSystemMessage(ticket, autoResponse);
        }
        
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO getTicket(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO getTicketByNumber(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return mapToDTO(ticket);
    }
    
    @Override
    public Page<TicketDTO> getUserTickets(String userId, Pageable pageable) {
        return ticketRepository.findByUserId(userId, pageable)
            .map(this::mapToDTO);
    }
    
    @Override
    public Page<TicketDTO> getAgentTickets(String agentId, Pageable pageable) {
        return ticketRepository.findByAssignedToAgentId(agentId, pageable)
            .map(this::mapToDTO);
    }
    
    @Override
    public Page<TicketDTO> searchTickets(TicketSearchRequest request, Pageable pageable) {
        Specification<Ticket> spec = buildSearchSpecification(request);
        return ticketRepository.findAll(spec, pageable)
            .map(this::mapToDTO);
    }
    
    @Override
    public TicketDTO updateTicket(String ticketId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        String performedBy = request.getPerformedBy();
        String performedByName = request.getPerformedByName();
        
        // Update status
        if (request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(request.getStatus());
            addActivity(ticket, ActivityType.STATUS_CHANGED, performedBy, performedByName, 
                       oldStatus.toString(), request.getStatus().toString());
            
            // Handle status-specific actions
            handleStatusChange(ticket, oldStatus, request.getStatus());
        }
        
        // Update priority
        if (request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            TicketPriority oldPriority = ticket.getPriority();
            ticket.setPriority(request.getPriority());
            ticket.calculateSlaBreachTime();
            addActivity(ticket, ActivityType.PRIORITY_CHANGED, performedBy, performedByName,
                       oldPriority.toString(), request.getPriority().toString());
        }
        
        // Update category
        if (request.getCategory() != null) {
            ticket.setCategory(request.getCategory());
            ticket.setSubCategory(request.getSubCategory());
            addActivity(ticket, ActivityType.CATEGORY_CHANGED, performedBy, performedByName);
        }
        
        // Update tags
        if (request.getTags() != null) {
            Set<String> oldTags = new HashSet<>(ticket.getTags());
            ticket.setTags(request.getTags());
            
            // Log tag changes
            Set<String> addedTags = new HashSet<>(request.getTags());
            addedTags.removeAll(oldTags);
            for (String tag : addedTags) {
                addActivity(ticket, ActivityType.TAGGED, performedBy, performedByName, null, tag);
            }
            
            Set<String> removedTags = new HashSet<>(oldTags);
            removedTags.removeAll(request.getTags());
            for (String tag : removedTags) {
                addActivity(ticket, ActivityType.UNTAGGED, performedBy, performedByName, tag, null);
            }
        }
        
        ticket = ticketRepository.save(ticket);
        
        // Send notifications
        notificationService.notifyTicketUpdated(ticket);
        
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO assignTicket(String ticketId, String agentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        AgentDTO agent = userServiceClient.getAgent(agentId)
            .orElseThrow(() -> new BusinessException("Agent not found"));
        
        String oldAgentId = ticket.getAssignedToAgentId();
        ticket.setAssignedToAgentId(agentId);
        ticket.setAssignedToAgentName(agent.getName());
        ticket.setAssignedAt(LocalDateTime.now());
        
        if (ticket.getStatus() == TicketStatus.NEW) {
            ticket.setStatus(TicketStatus.OPEN);
        }
        
        ticket = ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.ASSIGNED, agentId, agent.getName(), 
                   oldAgentId, agentId);
        
        // Notify agents
        if (oldAgentId != null) {
            notificationService.notifyAgentUnassigned(oldAgentId, ticket);
        }
        notificationService.notifyAgentAssigned(agentId, ticket);
        
        eventPublisher.publishEvent(new TicketAssignedEvent(ticket, agentId));
        
        return mapToDTO(ticket);
    }
    
    @Override
    public MessageDTO addMessage(String ticketId, AddMessageRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .content(request.getContent())
            .senderId(request.getSenderId())
            .senderName(request.getSenderName())
            .senderType(request.getSenderType())
            .isInternalNote(request.isInternalNote())
            .build();
        
        // Analyze sentiment
        message.setSentimentScore(aiAssistantService.analyzeSentiment(request.getContent()));
        
        // Generate AI suggestion if agent message
        if (message.getSenderType() == MessageSenderType.CUSTOMER && !request.isInternalNote()) {
            String suggestion = aiAssistantService.generateResponseSuggestion(ticket, message);
            message.setAiSuggestedResponse(suggestion);
        }
        
        ticket.addMessage(message);
        message = messageRepository.save(message);
        
        // Handle attachments
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            handleAttachments(ticket, message, request.getAttachments(), request.getSenderId());
        }
        
        // Update ticket status if needed
        if (message.getSenderType() == MessageSenderType.CUSTOMER && 
            ticket.getStatus() == TicketStatus.WAITING_FOR_CUSTOMER) {
            ticket.setStatus(TicketStatus.OPEN);
            ticketRepository.save(ticket);
        } else if (message.getSenderType() == MessageSenderType.AGENT && 
                   ticket.getStatus() == TicketStatus.WAITING_FOR_AGENT) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        }
        
        // Add activity
        ActivityType activityType = request.isInternalNote() ? 
            ActivityType.INTERNAL_NOTE_ADDED : ActivityType.MESSAGE_ADDED;
        addActivity(ticket, activityType, request.getSenderId(), request.getSenderName());
        
        // Send notifications
        if (!request.isInternalNote()) {
            notificationService.notifyNewMessage(ticket, message);
        }
        
        eventPublisher.publishEvent(new MessageAddedEvent(ticket, message));
        
        return mapMessageToDTO(message);
    }
    
    @Override
    public TicketDTO resolveTicket(String ticketId, ResolveTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException("Ticket is already resolved or closed");
        }
        
        ticket.resolve();
        
        // Add resolution message
        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .content(request.getResolutionNotes())
            .senderId(request.getResolvedBy())
            .senderName(request.getResolvedByName())
            .senderType(MessageSenderType.AGENT)
            .isInternalNote(false)
            .build();
        
        ticket.addMessage(message);
        messageRepository.save(message);
        
        ticket = ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.RESOLVED, request.getResolvedBy(), request.getResolvedByName());
        
        // Send resolution notification
        notificationService.notifyTicketResolved(ticket);
        
        // Request feedback
        notificationService.requestFeedback(ticket);
        
        eventPublisher.publishEvent(new TicketResolvedEvent(ticket));
        
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO closeTicket(String ticketId, String closedBy, String closedByName) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        ticket.close();
        ticket = ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.CLOSED, closedBy, closedByName);
        
        eventPublisher.publishEvent(new TicketClosedEvent(ticket));
        
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO reopenTicket(String ticketId, ReopenTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new BusinessException("Only resolved or closed tickets can be reopened");
        }
        
        ticket.reopen();
        
        // Add reopen message
        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .content(request.getReopenReason())
            .senderId(request.getReopenedBy())
            .senderName(request.getReopenedByName())
            .senderType(MessageSenderType.CUSTOMER)
            .build();
        
        ticket.addMessage(message);
        messageRepository.save(message);
        
        ticket = ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.REOPENED, request.getReopenedBy(), request.getReopenedByName());
        
        // Notify assigned agent
        if (ticket.getAssignedToAgentId() != null) {
            notificationService.notifyTicketReopened(ticket);
        }
        
        eventPublisher.publishEvent(new TicketReopenedEvent(ticket));
        
        return mapToDTO(ticket);
    }
    
    @Override
    public TicketDTO escalateTicket(String ticketId, EscalateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        ticket.escalate(request.getEscalationReason());
        
        // Find escalation manager
        String escalationManagerId = escalationService.findEscalationManager(ticket);
        if (escalationManagerId != null) {
            assignTicket(ticketId, escalationManagerId);
        }
        
        ticket = ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.ESCALATED, request.getEscalatedBy(), 
                   request.getEscalatedByName(), null, request.getEscalationReason());
        
        // Notify escalation team
        notificationService.notifyEscalation(ticket);
        
        eventPublisher.publishEvent(new TicketEscalatedEvent(ticket));
        
        return mapToDTO(ticket);
    }
    
    @Override
    public FeedbackDTO submitFeedback(String ticketId, SubmitFeedbackRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        
        if (ticket.getFeedback() != null) {
            throw new BusinessException("Feedback already submitted for this ticket");
        }
        
        TicketFeedback feedback = TicketFeedback.builder()
            .ticket(ticket)
            .satisfactionRating(request.getSatisfactionRating())
            .resolutionRating(request.getResolutionRating())
            .responseTimeRating(request.getResponseTimeRating())
            .agentRating(request.getAgentRating())
            .comment(request.getComment())
            .wouldRecommend(request.getWouldRecommend())
            .issueResolved(request.getIssueResolved())
            .easyToContact(request.getEasyToContact())
            .professionalService(request.getProfessionalService())
            .clearCommunication(request.getClearCommunication())
            .feedbackSource(request.getFeedbackSource())
            .build();
        
        // Determine if followup is required
        if (!feedback.isPositiveFeedback() || !Boolean.TRUE.equals(request.getIssueResolved())) {
            feedback.setFollowupRequired(true);
        }
        
        ticket.setFeedback(feedback);
        ticket.setSatisfactionScore(feedback.getSatisfactionRating());
        ticketRepository.save(ticket);
        
        addActivity(ticket, ActivityType.FEEDBACK_RECEIVED, ticket.getUserId(), ticket.getUserName());
        
        // Handle negative feedback
        if (!feedback.isPositiveFeedback()) {
            escalationService.handleNegativeFeedback(ticket, feedback);
        }
        
        eventPublisher.publishEvent(new FeedbackReceivedEvent(ticket, feedback));
        
        return mapFeedbackToDTO(feedback);
    }
    
    @Override
    public TicketStatisticsDTO getTicketStatistics(LocalDateTime since) {
        return TicketStatisticsDTO.builder()
            .averageResolutionTime(ticketRepository.getAverageResolutionTime(since))
            .averageSatisfactionScore(ticketRepository.getAverageSatisfactionScore(since))
            .averageFirstResponseTime(ticketRepository.getAverageFirstResponseTime(since))
            .reopenRate(ticketRepository.getReopenRate(since))
            .ticketsByCategory(convertToMap(ticketRepository.getTicketCountByCategory(since)))
            .openTicketsByPriority(convertToMap(ticketRepository.getOpenTicketCountByPriority(
                Arrays.asList(TicketStatus.CLOSED, TicketStatus.CANCELLED))))
            .ticketTrend(ticketRepository.getTicketTrendData(since))
            .build();
    }
    
    @Override
    public List<TicketDTO> getSlaBreachedTickets() {
        List<TicketStatus> closedStatuses = Arrays.asList(TicketStatus.CLOSED, TicketStatus.CANCELLED);
        return ticketRepository.findSlaBreachedTickets(LocalDateTime.now(), closedStatuses)
            .stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    @Override
    public Map<String, Integer> getAgentWorkload() {
        List<TicketStatus> closedStatuses = Arrays.asList(TicketStatus.CLOSED, TicketStatus.CANCELLED);
        List<Object[]> workloadData = ticketRepository.getAgentWorkload(closedStatuses);
        
        Map<String, Integer> workload = new HashMap<>();
        for (Object[] data : workloadData) {
            workload.put((String) data[0], ((Long) data[1]).intValue());
        }
        
        return workload;
    }
    
    @Override
    public CategorizationResult previewCategorization(String subject, String description) {
        CreateTicketRequest previewRequest = CreateTicketRequest.builder()
            .subject(subject)
            .description(description)
            .build();
            
        return categorizationService.categorizeTicket(previewRequest);
    }
    
    @Override
    public CategorizationResult recategorizeTicket(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
            
        CategorizationResult result = categorizationService.recategorizeTicket(ticket);
        
        // Optionally update the ticket with new categorization
        boolean updated = false;
        
        if (!ticket.getCategory().equals(result.getSuggestedCategory())) {
            ticket.setCategory(result.getSuggestedCategory());
            updated = true;
        }
        
        if (!ticket.getSubCategory().equals(result.getSuggestedSubCategory())) {
            ticket.setSubCategory(result.getSuggestedSubCategory());
            updated = true;
        }
        
        if (!ticket.getPriority().equals(result.getSuggestedPriority())) {
            ticket.setPriority(result.getSuggestedPriority());
            ticket.calculateSlaBreachTime();
            updated = true;
        }
        
        if (result.getExtractedTags() != null && !result.getExtractedTags().equals(ticket.getTags())) {
            ticket.setTags(result.getExtractedTags());
            updated = true;
        }
        
        if (updated) {
            ticketRepository.save(ticket);
            addActivity(ticket, ActivityType.RECATEGORIZED, "SYSTEM", "Auto-categorization System");
            
            // Send notification about recategorization
            notificationService.notifyTicketRecategorized(ticket, result);
        }
        
        return result;
    }
    
    @Override
    public List<CategorizationConfidence> getCategoryConfidences(String subject, String description) {
        return categorizationService.getCategoryConfidences(subject, description);
    }
    
    @Override
    public List<TicketDTO> findSimilarTickets(String ticketId, int limit) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
            
        String content = ticket.getSubject() + " " + ticket.getDescription();
        List<Ticket> similarTickets = categorizationService.findSimilarTickets(content, limit);
        
        return similarTickets.stream()
            .filter(t -> !t.getId().equals(ticketId)) // Exclude the original ticket
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    // Helper methods
    
    private void addActivity(Ticket ticket, ActivityType type, String performedBy, String performedByName) {
        addActivity(ticket, type, performedBy, performedByName, null, null);
    }
    
    private void addActivity(Ticket ticket, ActivityType type, String performedBy, String performedByName,
                           String oldValue, String newValue) {
        TicketActivity activity = TicketActivity.builder()
            .ticket(ticket)
            .activityType(type)
            .performedBy(performedBy)
            .performedByName(performedByName)
            .oldValue(oldValue)
            .newValue(newValue)
            .build();
        
        ticket.addActivity(activity);
        activityRepository.save(activity);
    }
    
    private void handleAttachments(Ticket ticket, TicketMessage message, List<MultipartFile> files, String uploadedBy) {
        for (MultipartFile file : files) {
            try {
                StorageResult result = storageService.uploadFile(file, "support-attachments");
                
                TicketAttachment attachment = TicketAttachment.builder()
                    .ticket(ticket)
                    .message(message)
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .s3Key(result.getKey())
                    .s3Bucket(result.getBucket())
                    .contentType(file.getContentType())
                    .uploadedBy(uploadedBy)
                    .build();
                
                attachmentRepository.save(attachment);
                
                if (message != null) {
                    message.addAttachment(attachment);
                }
                
                addActivity(ticket, ActivityType.ATTACHMENT_ADDED, uploadedBy, null);
            } catch (Exception e) {
                log.error("Failed to upload attachment", e);
            }
        }
    }
    
    private void handleStatusChange(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus) {
        switch (newStatus) {
            case RESOLVED:
                ticket.resolve();
                break;
            case CLOSED:
                ticket.close();
                break;
            case OPEN:
                if (oldStatus == TicketStatus.RESOLVED || oldStatus == TicketStatus.CLOSED) {
                    ticket.reopen();
                }
                break;
        }
    }
    
    private TicketPriority determinePriority(CreateTicketRequest request) {
        // Use the advanced categorization service for priority determination
        return categorizationService.determinePriority(request);
    }
    
    private boolean isSpamTicket(CreateTicketRequest request) {
        return categorizationService.isSpamTicket(request.getSubject(), request.getDescription());
    }
    
    private boolean isDuplicateTicket(CreateTicketRequest request) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Long recentTickets = ticketRepository.countUserTicketsSince(request.getUserId(), since);
        
        if (recentTickets > 5) {
            // Check for similar content
            List<Ticket> userTickets = ticketRepository.findByUserId(request.getUserId(), 
                Pageable.ofSize(10)).getContent();
            
            for (Ticket ticket : userTickets) {
                if (aiAssistantService.isSimilar(request.getDescription(), ticket.getDescription())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean shouldAutoAssign(Ticket ticket) {
        return ticket.getPriority() == TicketPriority.CRITICAL || 
               ticket.getPriority() == TicketPriority.HIGH ||
               ticket.isVip();
    }
    
    private void addSystemMessage(Ticket ticket, String content) {
        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .content(content)
            .senderId("SYSTEM")
            .senderName("System")
            .senderType(MessageSenderType.SYSTEM)
            .isAutomated(true)
            .build();
        
        ticket.addMessage(message);
        messageRepository.save(message);
    }
    
    private Specification<Ticket> buildSearchSpecification(TicketSearchRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (request.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), request.getStatus()));
            }
            
            if (request.getPriority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), request.getPriority()));
            }
            
            if (request.getCategory() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), request.getCategory()));
            }
            
            if (request.getAssignedToAgentId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("assignedToAgentId"), request.getAssignedToAgentId()));
            }
            
            if (request.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), request.getUserId()));
            }
            
            if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
                String searchPattern = "%" + request.getSearchTerm().toLowerCase() + "%";
                Predicate subjectPredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("subject")), searchPattern);
                Predicate descriptionPredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")), searchPattern);
                predicates.add(criteriaBuilder.or(subjectPredicate, descriptionPredicate));
            }
            
            if (request.getCreatedAfter() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"), request.getCreatedAfter()));
            }
            
            if (request.getCreatedBefore() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"), request.getCreatedBefore()));
            }
            
            if (request.getIsEscalated() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isEscalated"), request.getIsEscalated()));
            }
            
            if (request.getIsVip() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isVip"), request.getIsVip()));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    private Map<String, Long> convertToMap(List<Object[]> data) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : data) {
            map.put(row[0].toString(), (Long) row[1]);
        }
        return map;
    }
    
    private TicketDTO mapToDTO(Ticket ticket) {
        return TicketDTO.builder()
            .id(ticket.getId())
            .ticketNumber(ticket.getTicketNumber())
            .userId(ticket.getUserId())
            .userEmail(ticket.getUserEmail())
            .userName(ticket.getUserName())
            .subject(ticket.getSubject())
            .description(ticket.getDescription())
            .status(ticket.getStatus())
            .priority(ticket.getPriority())
            .category(ticket.getCategory())
            .subCategory(ticket.getSubCategory())
            .assignedToAgentId(ticket.getAssignedToAgentId())
            .assignedToAgentName(ticket.getAssignedToAgentName())
            .assignedAt(ticket.getAssignedAt())
            .resolvedAt(ticket.getResolvedAt())
            .closedAt(ticket.getClosedAt())
            .firstResponseAt(ticket.getFirstResponseAt())
            .slaBreachAt(ticket.getSlaBreachAt())
            .isEscalated(ticket.isEscalated())
            .escalatedAt(ticket.getEscalatedAt())
            .tags(ticket.getTags())
            .messageCount(ticket.getMessages().size())
            .attachmentCount(ticket.getAttachments().size())
            .satisfactionScore(ticket.getSatisfactionScore())
            .resolutionTimeMinutes(ticket.getResolutionTimeMinutes())
            .isVip(ticket.isVip())
            .channel(ticket.getChannel())
            .sentimentScore(ticket.getSentimentScore())
            .createdAt(ticket.getCreatedAt())
            .updatedAt(ticket.getUpdatedAt())
            .build();
    }
    
    private MessageDTO mapMessageToDTO(TicketMessage message) {
        return MessageDTO.builder()
            .id(message.getId())
            .ticketId(message.getTicket().getId())
            .content(message.getContent())
            .senderId(message.getSenderId())
            .senderName(message.getSenderName())
            .senderType(message.getSenderType())
            .isInternalNote(message.isInternalNote())
            .isAutomated(message.isAutomated())
            .attachmentCount(message.getAttachments().size())
            .deliveryStatus(message.getDeliveryStatus())
            .sentimentScore(message.getSentimentScore())
            .aiSuggestedResponse(message.getAiSuggestedResponse())
            .createdAt(message.getCreatedAt())
            .build();
    }
    
    private FeedbackDTO mapFeedbackToDTO(TicketFeedback feedback) {
        return FeedbackDTO.builder()
            .id(feedback.getId())
            .ticketId(feedback.getTicket().getId())
            .satisfactionRating(feedback.getSatisfactionRating())
            .resolutionRating(feedback.getResolutionRating())
            .responseTimeRating(feedback.getResponseTimeRating())
            .agentRating(feedback.getAgentRating())
            .averageRating(feedback.getAverageRating())
            .comment(feedback.getComment())
            .wouldRecommend(feedback.getWouldRecommend())
            .issueResolved(feedback.getIssueResolved())
            .feedbackSubmittedAt(feedback.getFeedbackSubmittedAt())
            .build();
    }
}