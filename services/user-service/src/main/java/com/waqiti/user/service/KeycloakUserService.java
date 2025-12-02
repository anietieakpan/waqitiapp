package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing users in Keycloak
 * Handles user creation, updates, and synchronization between local DB and Keycloak
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakUserService {

    private final UserRepository userRepository;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String adminClientId;

    private Keycloak keycloak;
    private RealmResource realmResource;

    @PostConstruct
    public void init() {
        try {
            this.keycloak = KeycloakBuilder.builder()
                    .serverUrl(authServerUrl)
                    .realm("master")
                    .username(adminUsername)
                    .password(adminPassword)
                    .clientId(adminClientId)
                    .build();
            
            this.realmResource = keycloak.realm(realm);
            log.info("Keycloak admin client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Keycloak admin client", e);
        }
    }

    /**
     * Create a new user in Keycloak
     */
    @Transactional
    public String createUser(User user, String password) {
        try {
            UsersResource usersResource = realmResource.users();
            
            // Create user representation
            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(user.getUsername());
            keycloakUser.setEmail(user.getEmail());
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEnabled(true);
            keycloakUser.setEmailVerified(user.isEmailVerified());
            
            // Set custom attributes
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("phoneNumber", Collections.singletonList(user.getPhoneNumber()));
            attributes.put("userId", Collections.singletonList(user.getId().toString()));
            attributes.put("createdAt", Collections.singletonList(user.getCreatedAt().toString()));
            keycloakUser.setAttributes(attributes);
            
            // Create user in Keycloak
            Response response = usersResource.create(keycloakUser);
            
            if (response.getStatus() == 201) {
                // Extract user ID from location header
                String locationHeader = response.getHeaderString("Location");
                String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
                
                // Set password
                setUserPassword(keycloakUserId, password);
                
                // Assign default role
                assignRoleToUser(keycloakUserId, "user");
                
                // Update local user with Keycloak ID
                user.setKeycloakId(keycloakUserId);
                userRepository.save(user);
                
                log.info("User created successfully in Keycloak: {}", user.getUsername());
                return keycloakUserId;
            } else {
                log.error("Failed to create user in Keycloak. Status: {}", response.getStatus());
                throw new RuntimeException("Failed to create user in Keycloak");
            }
        } catch (Exception e) {
            log.error("Error creating user in Keycloak", e);
            throw new RuntimeException("Failed to create user in Keycloak", e);
        }
    }

    /**
     * Update existing user in Keycloak
     */
    @Transactional
    public void updateUser(User user) {
        try {
            if (user.getKeycloakId() == null) {
                log.warn("User {} has no Keycloak ID, skipping update", user.getUsername());
                return;
            }
            
            UserResource userResource = realmResource.users().get(user.getKeycloakId());
            UserRepresentation keycloakUser = userResource.toRepresentation();
            
            // Update user properties
            keycloakUser.setEmail(user.getEmail());
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEmailVerified(user.isEmailVerified());
            keycloakUser.setEnabled(user.isActive());
            
            // Update custom attributes
            Map<String, List<String>> attributes = keycloakUser.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put("phoneNumber", Collections.singletonList(user.getPhoneNumber()));
            attributes.put("updatedAt", Collections.singletonList(new Date().toString()));
            keycloakUser.setAttributes(attributes);
            
            // Update in Keycloak
            userResource.update(keycloakUser);
            
            log.info("User updated successfully in Keycloak: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Error updating user in Keycloak", e);
            throw new RuntimeException("Failed to update user in Keycloak", e);
        }
    }

    /**
     * Delete user from Keycloak
     */
    @Transactional
    public void deleteUser(String keycloakUserId) {
        try {
            realmResource.users().delete(keycloakUserId);
            log.info("User deleted from Keycloak: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error deleting user from Keycloak", e);
            throw new RuntimeException("Failed to delete user from Keycloak", e);
        }
    }

    /**
     * Set user password in Keycloak
     */
    public void setUserPassword(String keycloakUserId, String password) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            
            userResource.resetPassword(credential);
            log.debug("Password set for user: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error setting user password in Keycloak", e);
            throw new RuntimeException("Failed to set user password", e);
        }
    }

    /**
     * Assign role to user in Keycloak
     */
    public void assignRoleToUser(String keycloakUserId, String roleName) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            
            // Get realm role
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            
            // Assign role to user
            userResource.roles().realmLevel().add(Collections.singletonList(role));
            
            log.debug("Role {} assigned to user: {}", roleName, keycloakUserId);
        } catch (Exception e) {
            log.error("Error assigning role to user in Keycloak", e);
            throw new RuntimeException("Failed to assign role to user", e);
        }
    }

    /**
     * Remove role from user in Keycloak
     */
    public void removeRoleFromUser(String keycloakUserId, String roleName) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            
            // Get realm role
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            
            // Remove role from user
            userResource.roles().realmLevel().remove(Collections.singletonList(role));
            
            log.debug("Role {} removed from user: {}", roleName, keycloakUserId);
        } catch (Exception e) {
            log.error("Error removing role from user in Keycloak", e);
            throw new RuntimeException("Failed to remove role from user", e);
        }
    }

    /**
     * Get user roles from Keycloak
     */
    public Set<String> getUserRoles(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            
            return userResource.roles().realmLevel().listEffective().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting user roles from Keycloak", e);
            return Collections.emptySet();
        }
    }

    /**
     * Sync user from Keycloak to local database
     */
    @Transactional
    public void syncUserFromKeycloak(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            UserRepresentation keycloakUser = userResource.toRepresentation();
            
            // Find or create local user
            Optional<User> existingUser = userRepository.findByKeycloakId(keycloakUserId);
            User user = existingUser.orElseGet(User::new);
            
            // Update user properties from Keycloak
            user.setKeycloakId(keycloakUserId);
            user.setUsername(keycloakUser.getUsername());
            user.setEmail(keycloakUser.getEmail());
            user.setFirstName(keycloakUser.getFirstName());
            user.setLastName(keycloakUser.getLastName());
            user.setEmailVerified(keycloakUser.isEmailVerified());
            user.setActive(keycloakUser.isEnabled());
            
            // Update custom attributes
            Map<String, List<String>> attributes = keycloakUser.getAttributes();
            if (attributes != null) {
                if (attributes.containsKey("phoneNumber")) {
                    user.setPhoneNumber(attributes.get("phoneNumber").get(0));
                }
            }
            
            // Save to local database
            userRepository.save(user);
            
            log.info("User synced from Keycloak: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Error syncing user from Keycloak", e);
            throw new RuntimeException("Failed to sync user from Keycloak", e);
        }
    }

    /**
     * Enable user in Keycloak
     */
    public void enableUser(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(true);
            userResource.update(user);
            log.info("User enabled in Keycloak: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error enabling user in Keycloak", e);
            throw new RuntimeException("Failed to enable user", e);
        }
    }

    /**
     * Disable user in Keycloak
     */
    public void disableUser(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(false);
            userResource.update(user);
            log.info("User disabled in Keycloak: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error disabling user in Keycloak", e);
            throw new RuntimeException("Failed to disable user", e);
        }
    }

    /**
     * Send email verification to user
     */
    public void sendVerificationEmail(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            userResource.sendVerifyEmail();
            log.info("Verification email sent to user: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error sending verification email", e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    /**
     * Initiate password reset for user
     */
    public void initiatePasswordReset(String keycloakUserId) {
        try {
            UserResource userResource = realmResource.users().get(keycloakUserId);
            userResource.executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));
            log.info("Password reset initiated for user: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Error initiating password reset", e);
            throw new RuntimeException("Failed to initiate password reset", e);
        }
    }
}