package com.waqiti.support.service;

import com.waqiti.support.domain.Ticket;
import com.waqiti.support.domain.TicketMessage;
import com.waqiti.support.dto.LiveChatSession;

public interface NotificationService {
    
    // Ticket notifications
    void notifyTicketCreated(Ticket ticket);
    void notifyTicketAssigned(Ticket ticket, String agentId);
    void notifyTicketStatusChanged(Ticket ticket, String oldStatus, String newStatus);
    void notifyTicketEscalated(Ticket ticket, String escalatedTo);
    void notifyTicketResolved(Ticket ticket);
    void notifyTicketClosed(Ticket ticket);
    void notifyTicketReopened(Ticket ticket);
    void notifySLABreach(Ticket ticket, String breachType);
    
    // Message notifications
    void notifyNewMessage(TicketMessage message);
    void notifyMessageToCustomer(TicketMessage message);
    void notifyMessageToAgent(TicketMessage message, String agentId);
    void notifyInternalNote(TicketMessage message, String[] recipientIds);
    
    // Chat notifications
    void notifyChatSessionStarted(LiveChatSession session);
    void notifyChatSessionAssigned(LiveChatSession session, String agentId);
    void notifyChatSessionEnded(LiveChatSession session);
    void notifyChatMessageReceived(String sessionId, String message, String senderId);
    
    // Agent notifications
    void notifyAgentNewAssignment(String agentId, String ticketId);
    void notifyAgentTicketUpdate(String agentId, String ticketId);
    void notifyAgentChatRequest(String agentId, String sessionId);
    void notifyAgentWorkloadAlert(String agentId, int currentLoad, int maxLoad);
    
    // System notifications
    void notifySystemAlert(String alertType, String message, String[] recipientIds);
    void notifyMaintenanceScheduled(String maintenanceMessage);
    void notifyServiceOutage(String outageMessage);
    
    // Bulk notifications
    void notifyBulkTicketUpdate(String[] ticketIds, String updateType, String message);
    void notifyTeamAlert(String teamId, String alertType, String message);
    
    // Customer notifications
    void notifyCustomerTicketUpdate(String customerId, String ticketId, String updateType);
    void notifyCustomerSatisfactionRequest(String customerId, String ticketId);
    void notifyCustomerAutoResponse(String customerId, String ticketId, String response);
}