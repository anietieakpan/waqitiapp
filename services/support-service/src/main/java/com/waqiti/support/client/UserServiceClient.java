package com.waqiti.support.client;

import com.waqiti.support.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@FeignClient(
    name = "user-service",
    url = "${feign.client.config.user-service.url}",
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {
    
    @GetMapping("/api/v1/users/{userId}")
    Optional<UserDTO> getUser(@PathVariable("userId") String userId);
    
    @GetMapping("/api/v1/users/{userId}/profile")
    Optional<UserProfile> getUserProfile(@PathVariable("userId") String userId);
    
    @GetMapping("/api/v1/users")
    List<UserDTO> getUsers(@RequestParam("userIds") List<String> userIds);
    
    @GetMapping("/api/v1/users/agents")
    List<UserDTO> getSupportAgents();
    
    @GetMapping("/api/v1/users/agents/available")
    List<UserDTO> getAvailableSupportAgents();
    
    @GetMapping("/api/v1/users/{userId}/permissions")
    UserPermissions getUserPermissions(@PathVariable("userId") String userId);
    
    @GetMapping("/api/v1/users/{userId}/preferences")
    UserPreferences getUserPreferences(@PathVariable("userId") String userId);
    
    // Support agent specific endpoints
    @GetMapping("/api/v1/users/agents/{agentId}")
    Optional<SupportAgentDTO> getSupportAgent(@PathVariable("agentId") String agentId);
    
    @GetMapping("/api/v1/users/agents/{agentId}/workload")
    AgentWorkload getAgentWorkload(@PathVariable("agentId") String agentId);
    
    @GetMapping("/api/v1/users/agents/department/{department}")
    List<SupportAgentDTO> getAgentsByDepartment(@PathVariable("department") String department);
    
    @GetMapping("/api/v1/users/agents/skills")
    List<SupportAgentDTO> getAgentsBySkills(@RequestParam("skills") List<String> skills);
    
    @GetMapping("/api/v1/users/agents/language/{language}")
    List<SupportAgentDTO> getAgentsByLanguage(@PathVariable("language") String language);
}