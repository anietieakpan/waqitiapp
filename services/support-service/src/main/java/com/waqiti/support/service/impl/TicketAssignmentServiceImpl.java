package com.waqiti.support.service.impl;

import com.waqiti.support.client.UserServiceClient;
import com.waqiti.support.client.UserServiceClientFallback.AgentWorkload;
import com.waqiti.support.client.UserServiceClientFallback.SupportAgentDTO;
import com.waqiti.support.domain.Ticket;
import com.waqiti.support.repository.TicketRepository;
import com.waqiti.support.service.TicketAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketAssignmentServiceImpl implements TicketAssignmentService {
    
    private final UserServiceClient userServiceClient;
    private final TicketRepository ticketRepository;
    
    @Value("${support.ticket.auto-assignment.enabled:true}")
    private boolean autoAssignmentEnabled;
    
    @Value("${support.ticket.auto-assignment.max-tickets-per-agent:10}")
    private int maxTicketsPerAgent;
    
    // Assignment criteria weights
    private static final double WORKLOAD_WEIGHT = 0.4;
    private static final double SKILL_MATCH_WEIGHT = 0.3;
    private static final double PERFORMANCE_WEIGHT = 0.2;
    private static final double AVAILABILITY_WEIGHT = 0.1;
    
    @Override
    public String findBestAgent(Ticket ticket) {
        log.debug("Finding best agent for ticket: {}", ticket.getId());
        
        if (!autoAssignmentEnabled) {
            log.debug("Auto-assignment is disabled");
            return null;
        }
        
        List<SupportAgentDTO> availableAgents = getAvailableAgents();
        if (availableAgents.isEmpty()) {
            log.warn("No available agents found for ticket assignment");
            return null;
        }
        
        // Filter agents based on ticket requirements
        List<SupportAgentDTO> suitableAgents = filterSuitableAgents(availableAgents, ticket);
        
        if (suitableAgents.isEmpty()) {
            log.warn("No suitable agents found for ticket: {}", ticket.getId());
            // Fallback to any available agent
            suitableAgents = availableAgents;
        }
        
        // Calculate assignment scores and select best agent
        Optional<SupportAgentDTO> bestAgent = suitableAgents.stream()
            .max(Comparator.comparingDouble(agent -> calculateAssignmentScore(agent.id(), ticket)));
        
        if (bestAgent.isPresent()) {
            String agentId = bestAgent.get().id();
            log.info("Selected agent {} for ticket {}", agentId, ticket.getId());
            return agentId;
        }
        
        log.warn("Could not find suitable agent for ticket: {}", ticket.getId());
        return null;
    }
    
    @Override
    public String findBestAgentInDepartment(Ticket ticket, String department) {
        log.debug("Finding best agent in department {} for ticket: {}", department, ticket.getId());
        
        List<SupportAgentDTO> departmentAgents = userServiceClient.getAgentsByDepartment(department);
        List<SupportAgentDTO> availableAgents = departmentAgents.stream()
            .filter(agent -> isAgentAvailable(agent.id()))
            .collect(Collectors.toList());
        
        if (availableAgents.isEmpty()) {
            return null;
        }
        
        return availableAgents.stream()
            .max(Comparator.comparingDouble(agent -> calculateAssignmentScore(agent.id(), ticket)))
            .map(SupportAgentDTO::id)
            .orElse(null);
    }
    
    @Override
    public List<String> findAgentsWithSkills(List<String> requiredSkills) {
        return userServiceClient.getAgentsBySkills(requiredSkills)
            .stream()
            .map(SupportAgentDTO::id)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<String> findAgentsWithLanguage(String language) {
        return userServiceClient.getAgentsByLanguage(language)
            .stream()
            .map(SupportAgentDTO::id)
            .collect(Collectors.toList());
    }
    
    @Override
    @Cacheable(value = "agentAvailability", key = "#agentId", unless = "#result == false")
    public boolean isAgentAvailable(String agentId) {
        Optional<SupportAgentDTO> agent = userServiceClient.getSupportAgent(agentId);
        if (agent.isEmpty()) {
            return false;
        }
        
        SupportAgentDTO agentDTO = agent.get();
        
        // Check if agent is online/available
        if (!"ONLINE".equals(agentDTO.status()) && !"AVAILABLE".equals(agentDTO.status())) {
            return false;
        }
        
        // Check if agent has capacity
        if (agentDTO.currentTicketCount() >= agentDTO.maxConcurrentTickets()) {
            return false;
        }
        
        return agentDTO.autoAssignEnabled();
    }
    
    @Override
    public int getAgentCurrentWorkload(String agentId) {
        AgentWorkload workload = userServiceClient.getAgentWorkload(agentId);
        return workload.currentTickets();
    }
    
    @Override
    public void assignTicketToAgent(String ticketId, String agentId) {
        log.info("Assigning ticket {} to agent {}", ticketId, agentId);
        
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            log.error("Ticket not found: {}", ticketId);
            return;
        }
        
        Ticket ticket = ticketOpt.get();
        String previousAgent = ticket.getAssignedToAgentId();
        
        ticket.setAssignedToAgentId(agentId);
        ticket.setAssignedAt(LocalDateTime.now());
        
        // Set agent name for display
        userServiceClient.getSupportAgent(agentId).ifPresent(agent -> {
            ticket.setAssignedToAgentName(agent.displayName());
        });
        
        ticketRepository.save(ticket);
        
        // Log assignment activity
        logAssignmentActivity(ticket, previousAgent, agentId);
        
        log.info("Successfully assigned ticket {} to agent {}", ticketId, agentId);
    }
    
    @Override
    public void reassignTicket(String ticketId, String fromAgentId, String toAgentId) {
        log.info("Reassigning ticket {} from agent {} to agent {}", ticketId, fromAgentId, toAgentId);
        
        if (!isAgentAvailable(toAgentId)) {
            log.warn("Target agent {} is not available for reassignment", toAgentId);
            throw new IllegalStateException("Target agent is not available");
        }
        
        assignTicketToAgent(ticketId, toAgentId);
        
        // Additional reassignment logic (notifications, etc.)
        handleReassignmentNotifications(ticketId, fromAgentId, toAgentId);
    }
    
    @Override
    public void autoAssignTickets() {
        log.info("Starting auto-assignment of unassigned tickets");
        
        if (!autoAssignmentEnabled) {
            log.debug("Auto-assignment is disabled, skipping");
            return;
        }
        
        // Find unassigned tickets
        List<Ticket> unassignedTickets = ticketRepository.findUnassignedTickets();
        log.info("Found {} unassigned tickets", unassignedTickets.size());
        
        int assignedCount = 0;
        for (Ticket ticket : unassignedTickets) {
            try {
                String agentId = findBestAgent(ticket);
                if (agentId != null) {
                    assignTicketToAgent(ticket.getId(), agentId);
                    assignedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to auto-assign ticket: {}", ticket.getId(), e);
            }
        }
        
        log.info("Auto-assigned {} out of {} tickets", assignedCount, unassignedTickets.size());
    }
    
    @Override
    public void balanceWorkload() {
        log.info("Starting workload balancing");
        
        List<SupportAgentDTO> agents = userServiceClient.getSupportAgents();
        
        // Find agents with high workload
        List<SupportAgentDTO> overloadedAgents = agents.stream()
            .filter(agent -> agent.currentTicketCount() > maxTicketsPerAgent)
            .collect(Collectors.toList());
        
        // Find agents with low workload
        List<SupportAgentDTO> underloadedAgents = agents.stream()
            .filter(agent -> agent.currentTicketCount() < maxTicketsPerAgent / 2)
            .filter(agent -> isAgentAvailable(agent.id()))
            .collect(Collectors.toList());
        
        if (overloadedAgents.isEmpty() || underloadedAgents.isEmpty()) {
            log.info("No workload balancing needed");
            return;
        }
        
        // Redistribute tickets from overloaded to underloaded agents
        redistributeTickets(overloadedAgents, underloadedAgents);
    }
    
    @Override
    public AssignmentStatistics getAssignmentStatistics() {
        // Calculate assignment statistics
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        
        int totalAssignments = ticketRepository.countAssignmentsSince(since);
        int autoAssignments = ticketRepository.countAutoAssignmentsSince(since);
        int manualAssignments = totalAssignments - autoAssignments;
        int reassignments = ticketRepository.countReassignmentsSince(since);
        
        double averageAssignmentTime = ticketRepository.getAverageAssignmentTimeMinutes(since);
        
        Map<String, Integer> agentWorkloads = userServiceClient.getSupportAgents()
            .stream()
            .collect(Collectors.toMap(
                SupportAgentDTO::id,
                SupportAgentDTO::currentTicketCount
            ));
        
        return new AssignmentStatistics(
            totalAssignments,
            autoAssignments,
            manualAssignments,
            reassignments,
            averageAssignmentTime,
            agentWorkloads
        );
    }
    
    @Override
    public boolean shouldAutoAssign(Ticket ticket) {
        if (!autoAssignmentEnabled) {
            return false;
        }
        
        // Don't auto-assign if already assigned
        if (ticket.getAssignedToAgentId() != null) {
            return false;
        }
        
        // Don't auto-assign VIP tickets (may need special handling)
        if (ticket.isVip() && ticket.getPriority().ordinal() >= 3) { // HIGH or above
            return false;
        }
        
        // Check if there are available agents
        return !getAvailableAgents().isEmpty();
    }
    
    @Override
    public List<String> findBackupAgents(Ticket ticket) {
        List<SupportAgentDTO> allAgents = userServiceClient.getSupportAgents();
        
        return allAgents.stream()
            .filter(agent -> agent.currentTicketCount() < agent.maxConcurrentTickets())
            .sorted(Comparator.comparingInt(SupportAgentDTO::currentTicketCount))
            .limit(3)
            .map(SupportAgentDTO::id)
            .collect(Collectors.toList());
    }
    
    @Override
    public double calculateAssignmentScore(String agentId, Ticket ticket) {
        Optional<SupportAgentDTO> agentOpt = userServiceClient.getSupportAgent(agentId);
        if (agentOpt.isEmpty()) {
            return 0.0;
        }
        
        SupportAgentDTO agent = agentOpt.get();
        double score = 0.0;
        
        // Workload score (lower workload = higher score)
        double workloadScore = Math.max(0, 1.0 - (double) agent.currentTicketCount() / agent.maxConcurrentTickets());
        score += workloadScore * WORKLOAD_WEIGHT;
        
        // Skill match score
        double skillScore = calculateSkillMatchScore(agent, ticket);
        score += skillScore * SKILL_MATCH_WEIGHT;
        
        // Performance score (based on agent rating)
        double performanceScore = agent.averageRating() / 5.0; // Normalize to 0-1
        score += performanceScore * PERFORMANCE_WEIGHT;
        
        // Availability score
        double availabilityScore = isAgentAvailable(agentId) ? 1.0 : 0.0;
        score += availabilityScore * AVAILABILITY_WEIGHT;
        
        return score;
    }
    
    // Helper methods
    private List<SupportAgentDTO> getAvailableAgents() {
        return userServiceClient.getAvailableSupportAgents()
            .stream()
            .map(userDto -> userServiceClient.getSupportAgent(userDto.getId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(agent -> isAgentAvailable(agent.id()))
            .collect(Collectors.toList());
    }
    
    private List<SupportAgentDTO> filterSuitableAgents(List<SupportAgentDTO> agents, Ticket ticket) {
        return agents.stream()
            .filter(agent -> {
                // Language requirement
                if (ticket.getLanguageCode() != null) {
                    return agent.languages().contains(ticket.getLanguageCode());
                }
                
                // Department requirement based on category
                List<String> requiredDepartments = mapCategoryToDepartments(ticket.getCategory());
                if (!requiredDepartments.isEmpty()) {
                    return agent.departments().stream().anyMatch(requiredDepartments::contains);
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }
    
    private double calculateSkillMatchScore(SupportAgentDTO agent, Ticket ticket) {
        List<String> requiredSkills = extractRequiredSkills(ticket);
        if (requiredSkills.isEmpty()) {
            return 1.0; // No specific skills required
        }
        
        long matchingSkills = agent.skills().stream()
            .mapToLong(skill -> requiredSkills.contains(skill) ? 1 : 0)
            .sum();
        
        return (double) matchingSkills / requiredSkills.size();
    }
    
    private List<String> mapCategoryToDepartments(String category) {
        return switch (category.toLowerCase()) {
            case "payment", "billing" -> List.of("BILLING", "PAYMENTS");
            case "technical", "account" -> List.of("TECHNICAL", "SUPPORT");
            case "security" -> List.of("SECURITY", "FRAUD");
            default -> Collections.emptyList();
        };
    }
    
    private List<String> extractRequiredSkills(Ticket ticket) {
        List<String> skills = new ArrayList<>();
        
        String category = ticket.getCategory().toLowerCase();
        switch (category) {
            case "payment", "billing":
                skills.add("PAYMENTS");
                break;
            case "technical":
                skills.add("TECHNICAL_SUPPORT");
                break;
            case "security":
                skills.add("SECURITY");
                break;
            case "account":
                skills.add("ACCOUNT_MANAGEMENT");
                break;
        }
        
        // Add priority-based skills
        if (ticket.getPriority().ordinal() >= 3) { // HIGH or above
            skills.add("ESCALATION_HANDLING");
        }
        
        return skills;
    }
    
    private void logAssignmentActivity(Ticket ticket, String previousAgent, String newAgent) {
        // Implementation would log to ticket_activities table
        log.info("Assignment activity: Ticket {} assigned from {} to {}", 
                ticket.getId(), previousAgent, newAgent);
    }
    
    private void handleReassignmentNotifications(String ticketId, String fromAgentId, String toAgentId) {
        // Implementation would send notifications to both agents
        log.info("Handling reassignment notifications for ticket {}", ticketId);
    }
    
    private void redistributeTickets(List<SupportAgentDTO> overloadedAgents, List<SupportAgentDTO> underloadedAgents) {
        log.info("Redistributing tickets from {} overloaded agents to {} underloaded agents", 
                overloadedAgents.size(), underloadedAgents.size());
        
        for (SupportAgentDTO overloadedAgent : overloadedAgents) {
            // Find tickets that can be reassigned (newest, non-priority tickets)
            List<Ticket> reassignableTickets = ticketRepository
                .findReassignableTicketsForAgent(overloadedAgent.id(), 2); // Limit to 2 tickets
            
            for (Ticket ticket : reassignableTickets) {
                Optional<SupportAgentDTO> targetAgent = underloadedAgents.stream()
                    .filter(agent -> isAgentAvailable(agent.id()))
                    .filter(agent -> agent.currentTicketCount() < maxTicketsPerAgent / 2)
                    .findFirst();
                
                if (targetAgent.isPresent()) {
                    try {
                        reassignTicket(ticket.getId(), overloadedAgent.id(), targetAgent.get().id());
                        log.info("Redistributed ticket {} from {} to {}", 
                                ticket.getId(), overloadedAgent.id(), targetAgent.get().id());
                        break; // One ticket per iteration
                    } catch (Exception e) {
                        log.error("Failed to redistribute ticket: {}", ticket.getId(), e);
                    }
                }
            }
        }
    }
}