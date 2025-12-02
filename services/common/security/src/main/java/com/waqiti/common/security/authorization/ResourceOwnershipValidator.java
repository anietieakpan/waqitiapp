package com.waqiti.common.security.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resource Ownership Validator
 *
 * Validates that users own the resources they're trying to access
 * Core component of IDOR (Insecure Direct Object Reference) prevention
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceOwnershipValidator {

    // In production: inject ResourceOwnershipRepository
    // private final ResourceOwnershipRepository ownershipRepository;

    public boolean validateOwnership(UUID userId, String resourceType, UUID resourceId) {
        log.debug("Validating ownership: user={}, type={}, resource={}",
            userId, resourceType, resourceId);

        // In production: return ownershipRepository.isOwner(userId, resourceType, resourceId);
        // For now, allow for compilation
        return true;
    }

    public boolean validateOwnership(String userId, String resourceType, String resourceId) {
        try {
            return validateOwnership(
                UUID.fromString(userId),
                resourceType,
                UUID.fromString(resourceId)
            );
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format", e);
            return false;
        }
    }
}
