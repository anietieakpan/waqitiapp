package com.waqiti.support.client;

import com.waqiti.support.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {
    
    @Override
    public Optional<UserDTO> getUser(String userId) {
        log.warn("User service unavailable, returning fallback user for ID: {}", userId);
        
        return Optional.of(UserDTO.builder()
            .id(userId)
            .name("Unknown User")
            .email("unknown@example.com")
            .isActive(true)
            .customerTier("BASIC")
            .roles(List.of("USER"))
            .createdAt(LocalDateTime.now())
            .build());
    }
    
    @Override
    public Optional<UserProfile> getUserProfile(String userId) {
        log.warn("User service unavailable, returning empty profile for user: {}", userId);
        return Optional.empty();
    }
    
    @Override
    public List<UserDTO> getUsers(List<String> userIds) {
        log.warn("User service unavailable, returning empty user list");
        return Collections.emptyList();
    }
    
    @Override
    public List<UserDTO> getSupportAgents() {
        log.warn("User service unavailable, returning empty agent list");
        return Collections.emptyList();
    }
    
    @Override
    public List<UserDTO> getAvailableSupportAgents() {
        log.warn("User service unavailable, returning empty available agent list");
        return Collections.emptyList();
    }
    
    @Override
    public UserPermissions getUserPermissions(String userId) {
        log.warn("User service unavailable, returning basic permissions for user: {}", userId);
        return new UserPermissions(userId, List.of("TICKET_CREATE", "TICKET_VIEW"), Collections.emptyMap());
    }
    
    @Override
    public UserPreferences getUserPreferences(String userId) {
        log.warn("User service unavailable, returning default preferences for user: {}", userId);
        return new UserPreferences(userId, "en", "UTC", "EMAIL", Collections.emptyMap());
    }
    
    @Override
    public Optional<SupportAgentDTO> getSupportAgent(String agentId) {
        log.warn("User service unavailable, returning empty agent for ID: {}", agentId);
        return Optional.empty();
    }
    
    @Override
    public AgentWorkload getAgentWorkload(String agentId) {
        log.warn("User service unavailable, returning zero workload for agent: {}", agentId);
        return new AgentWorkload(agentId, 0, 10, Collections.emptyList());
    }
    
    @Override
    public List<SupportAgentDTO> getAgentsByDepartment(String department) {
        log.warn("User service unavailable, returning empty agent list for department: {}", department);
        return Collections.emptyList();
    }
    
    @Override
    public List<SupportAgentDTO> getAgentsBySkills(List<String> skills) {
        log.warn("User service unavailable, returning empty agent list for skills: {}", skills);
        return Collections.emptyList();
    }
    
    @Override
    public List<SupportAgentDTO> getAgentsByLanguage(String language) {
        log.warn("User service unavailable, returning empty agent list for language: {}", language);
        return Collections.emptyList();
    }
    
    // Supporting record classes
    public record UserProfile(
        String userId,
        String firstName,
        String lastName,
        String fullName,
        String avatarUrl,
        String bio,
        String website,
        String location,
        LocalDateTime birthDate
    ) {}
    
    public record UserPermissions(
        String userId,
        List<String> permissions,
        java.util.Map<String, Object> roleData
    ) {}
    
    public record UserPreferences(
        String userId,
        String language,
        String timezone,
        String contactMethod,
        java.util.Map<String, Object> settings
    ) {}
    
    public record SupportAgentDTO(
        String id,
        String userId,
        String displayName,
        String email,
        List<String> departments,
        List<String> languages,
        List<String> skills,
        String status,
        int maxConcurrentTickets,
        int currentTicketCount,
        double averageRating,
        boolean autoAssignEnabled
    ) {}
    
    public record AgentWorkload(
        String agentId,
        int currentTickets,
        int maxTickets,
        List<String> currentTicketIds
    ) {}
}