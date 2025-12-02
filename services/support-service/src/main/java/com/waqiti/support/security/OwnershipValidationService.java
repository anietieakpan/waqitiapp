package com.waqiti.support.security;

import com.waqiti.support.domain.Ticket;
import com.waqiti.support.repository.TicketRepository;
import com.waqiti.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Production-grade ownership validation service.
 * Addresses SEC-003: Missing ownership validation in controller endpoints.
 *
 * Security principle: Users can only access their own resources unless they have
 * elevated privileges (SUPPORT_AGENT, SUPPORT_MANAGER, ADMIN).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OwnershipValidationService {

    private final TicketRepository ticketRepository;

    /**
     * Validates that the current user owns the ticket or has permission to access it.
     *
     * @param ticketId The ticket ID to validate
     * @param userId The current user's ID
     * @param authentication The Spring Security authentication object
     * @throws ForbiddenAccessException if user doesn't have access
     */
    public void validateTicketAccess(String ticketId, String userId, Authentication authentication) {
        // Check if user has elevated role first (more efficient)
        if (hasElevatedRole(authentication)) {
            log.debug("User {} has elevated role - granting access to ticket {}", userId, ticketId);
            return;
        }

        // Fetch ticket and validate ownership
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            log.warn("SECURITY: User {} attempted unauthorized access to ticket {} owned by {}",
                    userId, ticketId, ticket.getUserId());
            throw new ForbiddenAccessException(
                String.format("User %s is not authorized to access ticket %s", userId, ticketId)
            );
        }

        log.debug("Ownership validated: User {} owns ticket {}", userId, ticketId);
    }

    /**
     * Validates ticket ownership without authentication object (fallback).
     */
    public void validateTicketOwnership(String ticketId, String userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            log.warn("SECURITY: User {} attempted unauthorized access to ticket {} owned by {}",
                    userId, ticketId, ticket.getUserId());
            throw new ForbiddenAccessException(
                String.format("Access denied to ticket %s", ticketId)
            );
        }
    }

    /**
     * Validates that user is either the ticket owner or an assigned agent.
     */
    public void validateTicketModificationAccess(String ticketId, String userId,
                                                 Authentication authentication) {
        if (hasElevatedRole(authentication)) {
            return;
        }

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        boolean isOwner = ticket.getUserId().equals(userId);
        boolean isAssignedAgent = userId.equals(ticket.getAssignedTo());

        if (!isOwner && !isAssignedAgent) {
            log.warn("SECURITY: User {} attempted unauthorized modification of ticket {}",
                    userId, ticketId);
            throw new ForbiddenAccessException("Not authorized to modify this ticket");
        }

        log.debug("Modification access validated for user {} on ticket {}", userId, ticketId);
    }

    /**
     * Validates agent-only access.
     */
    public void validateAgentAccess(Authentication authentication) {
        if (!hasRole(authentication, "SUPPORT_AGENT") &&
            !hasRole(authentication, "SUPPORT_MANAGER") &&
            !hasRole(authentication, "ADMIN")) {
            throw new ForbiddenAccessException("Agent privileges required");
        }
    }

    /**
     * Validates manager-only access.
     */
    public void validateManagerAccess(Authentication authentication) {
        if (!hasRole(authentication, "SUPPORT_MANAGER") &&
            !hasRole(authentication, "ADMIN")) {
            throw new ForbiddenAccessException("Manager privileges required");
        }
    }

    /**
     * Checks if user has elevated role (agent, manager, or admin).
     */
    public boolean hasElevatedRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }

        return hasRole(authentication, "SUPPORT_AGENT") ||
               hasRole(authentication, "SUPPORT_MANAGER") ||
               hasRole(authentication, "ADMIN");
    }

    /**
     * Checks if authentication has a specific role.
     */
    public boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }

        String roleToCheck = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        return authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals(roleToCheck) ||
                             auth.getAuthority().equals(role));
    }

    /**
     * Exception thrown when user attempts to access a resource they don't own.
     */
    public static class ForbiddenAccessException extends BusinessException {
        public ForbiddenAccessException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when resource is not found.
     */
    public static class ResourceNotFoundException extends BusinessException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
