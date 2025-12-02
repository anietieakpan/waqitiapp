package com.waqiti.user.client;

import com.waqiti.user.client.dto.*;
import com.waqiti.user.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "integration-service", 
    url = "${integration-service.url}",
    fallback = IntegrationServiceClientFallback.class
)
public interface IntegrationServiceClient {
    
    @PostMapping("/api/v1/users/create")
    CreateUserResponse createUser(@RequestBody CreateUserRequest request);
    
    @PostMapping("/api/v1/users/update")
    UpdateUserResponse updateUser(@RequestBody UpdateUserRequest request);
    
    @PostMapping("/api/v1/users/status")
    UpdateUserStatusResponse updateUserStatus(@RequestBody UpdateUserStatusRequest request);
}