package com.waqiti.support.service;

import com.waqiti.support.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ITicketService {
    
    TicketDTO createTicket(CreateTicketRequest request);
    
    TicketDTO getTicket(String ticketId);
    
    TicketDTO getTicketByNumber(String ticketNumber);
    
    Page<TicketDTO> getUserTickets(String userId, Pageable pageable);
    
    Page<TicketDTO> getAgentTickets(String agentId, Pageable pageable);
    
    Page<TicketDTO> searchTickets(TicketSearchRequest request, Pageable pageable);
    
    TicketDTO updateTicket(String ticketId, UpdateTicketRequest request);
    
    TicketDTO assignTicket(String ticketId, String agentId);
    
    MessageDTO addMessage(String ticketId, AddMessageRequest request);
    
    TicketDTO resolveTicket(String ticketId, ResolveTicketRequest request);
    
    TicketDTO closeTicket(String ticketId, String closedBy, String closedByName);
    
    TicketDTO reopenTicket(String ticketId, ReopenTicketRequest request);
    
    TicketDTO escalateTicket(String ticketId, EscalateTicketRequest request);
    
    FeedbackDTO submitFeedback(String ticketId, SubmitFeedbackRequest request);
    
    TicketStatisticsDTO getTicketStatistics(LocalDateTime since);
    
    List<TicketDTO> getSlaBreachedTickets();
    
    Map<String, Integer> getAgentWorkload();
    
    // Auto-categorization methods
    CategorizationResult previewCategorization(String subject, String description);
    
    CategorizationResult recategorizeTicket(String ticketId);
    
    List<CategorizationConfidence> getCategoryConfidences(String subject, String description);
    
    List<TicketDTO> findSimilarTickets(String ticketId, int limit);
}