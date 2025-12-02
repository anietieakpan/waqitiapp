package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.UserRoleChangeEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.RoleManagementService;
import com.waqiti.user.service.PermissionService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Production-grade Kafka consumer for user role change events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRoleChangeConsumer {

    private final UserService userService;
    private final RoleManagementService roleService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-role-changes", groupId = "role-change-processor")
    public void processRoleChange(@Payload UserRoleChangeEvent event,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment acknowledgment) {
        try {
            log.info("Processing role change for user: {} action: {} roles: {}", 
                    event.getUserId(), event.getAction(), event.getRoles());
            
            // Validate event
            validateRoleChangeEvent(event);
            
            // Process based on action
            switch (event.getAction()) {
                case "ASSIGN" -> handleRoleAssignment(event);
                case "REVOKE" -> handleRoleRevocation(event);
                case "UPDATE" -> handleRoleUpdate(event);
                case "ELEVATE" -> handlePrivilegeElevation(event);
                case "DEMOTE" -> handlePrivilegeDemotion(event);
                default -> log.warn("Unknown role action: {}", event.getAction());
            }
            
            // Update effective permissions
            updateEffectivePermissions(event);
            
            // Apply role-based restrictions
            applyRoleBasedRestrictions(event);
            
            // Send notifications
            sendRoleChangeNotifications(event);
            
            // Log role change for audit
            auditService.logRoleChange(
                event.getUserId(),
                event.getPreviousRoles(),
                event.getNewRoles(),
                event.getAction(),
                event.getChangedBy(),
                event.getChangeReason(),
                event.getChangedAt()
            );
            
            // Schedule role expiry if applicable
            if (event.getExpiryDate() != null) {
                scheduleRoleExpiry(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed role change for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process role change for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Role change processing failed", e);
        }
    }

    private void validateRoleChangeEvent(UserRoleChangeEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for role change");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required for role change");
        }
        
        if (event.getRoles() == null || event.getRoles().isEmpty()) {
            throw new IllegalArgumentException("Roles are required for role change");
        }
    }

    private void handleRoleAssignment(UserRoleChangeEvent event) {
        // Validate roles exist
        for (String role : event.getRoles()) {
            if (!roleService.roleExists(role)) {
                log.error("Role does not exist: {}", role);
                throw new IllegalArgumentException("Invalid role: " + role);
            }
        }
        
        // Check for conflicting roles
        Set<String> conflicts = roleService.findConflictingRoles(event.getRoles());
        if (!conflicts.isEmpty()) {
            log.error("Conflicting roles detected: {}", conflicts);
            auditService.logRoleConflict(
                event.getUserId(),
                event.getRoles(),
                conflicts
            );
            return;
        }
        
        // Assign roles
        for (String role : event.getRoles()) {
            roleService.assignRole(
                event.getUserId(),
                role,
                event.getChangedBy(),
                event.getEffectiveDate(),
                event.getExpiryDate()
            );
            
            // Grant associated permissions
            Set<String> permissions = roleService.getRolePermissions(role);
            permissionService.grantPermissions(
                event.getUserId(),
                permissions,
                "ROLE_ASSIGNMENT"
            );
        }
        
        // Enable role-specific features
        userService.enableRoleFeatures(
            event.getUserId(),
            event.getRoles()
        );
        
        log.info("Assigned roles {} to user: {}", event.getRoles(), event.getUserId());
    }

    private void handleRoleRevocation(UserRoleChangeEvent event) {
        // Revoke each role
        for (String role : event.getRoles()) {
            // Check if role can be revoked
            if (!roleService.canRevokeRole(event.getUserId(), role)) {
                log.warn("Cannot revoke protected role {} from user: {}", 
                        role, event.getUserId());
                continue;
            }
            
            // Revoke role
            roleService.revokeRole(
                event.getUserId(),
                role,
                event.getChangedBy(),
                event.getChangeReason()
            );
            
            // Revoke associated permissions
            Set<String> permissions = roleService.getRolePermissions(role);
            permissionService.revokePermissions(
                event.getUserId(),
                permissions,
                "ROLE_REVOCATION"
            );
        }
        
        // Disable role-specific features
        userService.disableRoleFeatures(
            event.getUserId(),
            event.getRoles()
        );
        
        // Check if user still has required roles
        if (!roleService.hasMinimumRoles(event.getUserId())) {
            roleService.assignDefaultRole(event.getUserId());
        }
        
        log.info("Revoked roles {} from user: {}", event.getRoles(), event.getUserId());
    }

    private void handleRoleUpdate(UserRoleChangeEvent event) {
        // Get current roles
        Set<String> currentRoles = roleService.getUserRoles(event.getUserId());
        
        // Calculate changes
        Set<String> rolesToAdd = event.getNewRoles();
        rolesToAdd.removeAll(currentRoles);
        
        Set<String> rolesToRemove = currentRoles;
        rolesToRemove.removeAll(event.getNewRoles());
        
        // Apply additions
        for (String role : rolesToAdd) {
            roleService.assignRole(
                event.getUserId(),
                role,
                event.getChangedBy(),
                event.getEffectiveDate(),
                event.getExpiryDate()
            );
        }
        
        // Apply removals
        for (String role : rolesToRemove) {
            roleService.revokeRole(
                event.getUserId(),
                role,
                event.getChangedBy(),
                event.getChangeReason()
            );
        }
        
        // Update user profile
        userService.updateUserRoleProfile(
            event.getUserId(),
            event.getNewRoles()
        );
        
        log.info("Updated roles for user: {} added: {} removed: {}", 
                event.getUserId(), rolesToAdd, rolesToRemove);
    }

    private void handlePrivilegeElevation(UserRoleChangeEvent event) {
        // Validate elevation request
        if (!roleService.canElevatePrivileges(
                event.getUserId(),
                event.getElevationLevel(),
                event.getChangedBy())) {
            log.error("Unauthorized privilege elevation attempt for user: {}", 
                    event.getUserId());
            auditService.logUnauthorizedElevation(
                event.getUserId(),
                event.getElevationLevel(),
                event.getChangedBy()
            );
            return;
        }
        
        // Apply elevated privileges
        roleService.elevatePrivileges(
            event.getUserId(),
            event.getElevationLevel(),
            event.getElevationDuration(),
            event.getElevationReason()
        );
        
        // Enable elevated access
        permissionService.grantElevatedPermissions(
            event.getUserId(),
            event.getElevatedPermissions(),
            event.getElevationDuration()
        );
        
        // Set auto-expiry for elevated privileges
        LocalDateTime expiryTime = LocalDateTime.now().plus(event.getElevationDuration());
        roleService.schedulePrivilegeExpiry(
            event.getUserId(),
            expiryTime
        );
        
        // Send elevation notification
        notificationService.sendPrivilegeElevationNotice(
            event.getUserId(),
            event.getElevationLevel(),
            event.getElevationDuration(),
            expiryTime
        );
        
        log.info("Elevated privileges for user: {} to level: {} for duration: {}", 
                event.getUserId(), event.getElevationLevel(), event.getElevationDuration());
    }

    private void handlePrivilegeDemotion(UserRoleChangeEvent event) {
        // Remove elevated privileges
        roleService.demotePrivileges(
            event.getUserId(),
            event.getDemotionReason()
        );
        
        // Revoke elevated permissions
        permissionService.revokeElevatedPermissions(
            event.getUserId()
        );
        
        // Terminate elevated sessions
        userService.terminateElevatedSessions(event.getUserId());
        
        // Apply standard role set
        roleService.applyStandardRoles(
            event.getUserId(),
            event.getStandardRoles()
        );
        
        log.info("Demoted privileges for user: {} reason: {}", 
                event.getUserId(), event.getDemotionReason());
    }

    private void updateEffectivePermissions(UserRoleChangeEvent event) {
        // Calculate effective permissions from all roles
        Set<String> effectivePermissions = permissionService.calculateEffectivePermissions(
            event.getUserId(),
            event.getNewRoles()
        );
        
        // Update user's permission set
        permissionService.updateUserPermissions(
            event.getUserId(),
            effectivePermissions
        );
        
        // Clear permission cache
        permissionService.clearPermissionCache(event.getUserId());
        
        // Update access control lists
        permissionService.updateAccessControlLists(
            event.getUserId(),
            effectivePermissions
        );
    }

    private void applyRoleBasedRestrictions(UserRoleChangeEvent event) {
        // Get restrictions for new roles
        var restrictions = roleService.getRoleRestrictions(event.getNewRoles());
        
        // Apply IP restrictions
        if (restrictions.containsKey("ipRestrictions")) {
            userService.applyIpRestrictions(
                event.getUserId(),
                (List<String>) restrictions.get("ipRestrictions")
            );
        }
        
        // Apply time-based restrictions
        if (restrictions.containsKey("timeRestrictions")) {
            userService.applyTimeRestrictions(
                event.getUserId(),
                restrictions.get("timeRestrictions")
            );
        }
        
        // Apply feature restrictions
        if (restrictions.containsKey("featureRestrictions")) {
            userService.applyFeatureRestrictions(
                event.getUserId(),
                (List<String>) restrictions.get("featureRestrictions")
            );
        }
        
        // Apply transaction limits
        if (restrictions.containsKey("transactionLimits")) {
            userService.applyTransactionLimits(
                event.getUserId(),
                restrictions.get("transactionLimits")
            );
        }
    }

    private void sendRoleChangeNotifications(UserRoleChangeEvent event) {
        // Send notification to user
        notificationService.sendRoleChangeNotification(
            event.getUserId(),
            event.getPreviousRoles(),
            event.getNewRoles(),
            event.getChangeReason(),
            event.getEffectiveDate()
        );
        
        // Send notification to administrators for privileged roles
        if (containsPrivilegedRole(event.getNewRoles())) {
            notificationService.notifyAdministrators(
                event.getUserId(),
                event.getNewRoles(),
                event.getChangedBy(),
                "PRIVILEGED_ROLE_ASSIGNMENT"
            );
        }
        
        // Send compliance notification if required
        if (event.isComplianceNotificationRequired()) {
            notificationService.sendComplianceNotification(
                event.getUserId(),
                event.getNewRoles(),
                event.getComplianceReason()
            );
        }
    }

    private void scheduleRoleExpiry(UserRoleChangeEvent event) {
        roleService.scheduleRoleExpiry(
            event.getUserId(),
            event.getRoles(),
            event.getExpiryDate(),
            event.getExpiryAction()
        );
        
        // Schedule expiry notification
        notificationService.scheduleRoleExpiryReminder(
            event.getUserId(),
            event.getRoles(),
            event.getExpiryDate()
        );
    }

    private boolean containsPrivilegedRole(Set<String> roles) {
        Set<String> privilegedRoles = Set.of("ADMIN", "SUPER_ADMIN", "SYSTEM_ADMIN", 
                                             "SECURITY_ADMIN", "COMPLIANCE_OFFICER");
        return roles.stream().anyMatch(privilegedRoles::contains);
    }
}