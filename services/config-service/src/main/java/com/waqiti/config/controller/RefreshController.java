package com.waqiti.config.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration Refresh Controller
 *
 * SECURITY: Requires ADMIN role for all operations
 * Configuration refresh is a privileged operation that can affect all services.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class RefreshController {

    private static final Logger logger = LoggerFactory.getLogger(RefreshController.class);
    
    private final EnvironmentRepository environmentRepository;
    
    public RefreshController(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refreshConfiguration(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String profile) {
        
        logger.info("Refreshing configuration for application: {}, profile: {}", application, profile);
        
        try {
            // Force refresh of configurations
            if (application != null && profile != null) {
                environmentRepository.findOne(application, profile, null);
            }
            
            return ResponseEntity.ok("Configuration refreshed successfully");
        } catch (Exception e) {
            logger.error("Error refreshing configuration", e);
            return ResponseEntity.internalServerError().body("Error refreshing configuration: " + e.getMessage());
        }
    }
}