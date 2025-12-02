package com.waqiti.support.service;

import com.waqiti.support.domain.Ticket;

import java.util.List;

public interface TicketAssignmentService {
    
    /**
     * Find the best agent to assign a ticket to based on various criteria
     */
    String findBestAgent(Ticket ticket);
    
    /**
     * Find the best agent from a specific department
     */
    String findBestAgentInDepartment(Ticket ticket, String department);
    
    /**
     * Find agents with specific skills
     */
    List<String> findAgentsWithSkills(List<String> requiredSkills);
    
    /**
     * Find agents who speak a specific language
     */
    List<String> findAgentsWithLanguage(String language);
    
    /**
     * Check if an agent is available for assignment
     */
    boolean isAgentAvailable(String agentId);
    
    /**
     * Get current workload for an agent
     */
    int getAgentCurrentWorkload(String agentId);
    
    /**
     * Assign ticket to specific agent
     */
    void assignTicketToAgent(String ticketId, String agentId);
    
    /**
     * Reassign ticket to a different agent
     */
    void reassignTicket(String ticketId, String fromAgentId, String toAgentId);
    
    /**
     * Auto-assign tickets based on rules
     */
    void autoAssignTickets();
    
    /**
     * Balance workload across agents
     */
    void balanceWorkload();
    
    /**
     * Get assignment statistics
     */
    AssignmentStatistics getAssignmentStatistics();
    
    /**
     * Check if auto-assignment should be triggered for a ticket
     */
    boolean shouldAutoAssign(Ticket ticket);
    
    /**
     * Find backup agents when primary assignment fails
     */
    List<String> findBackupAgents(Ticket ticket);
    
    /**
     * Calculate assignment score for agent-ticket pair
     */
    double calculateAssignmentScore(String agentId, Ticket ticket);
    
    // Supporting classes
    record AssignmentStatistics(
        int totalAssignments,
        int autoAssignments,
        int manualAssignments,
        int reassignments,
        double averageAssignmentTime,
        java.util.Map<String, Integer> agentWorkloads
    ) {}
}