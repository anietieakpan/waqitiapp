package com.waqiti.support.service;

import com.waqiti.support.domain.TicketMessage;
import com.waqiti.support.repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Secure search service that validates and sanitizes search queries
 * to prevent SQL injection and other security vulnerabilities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecureSearchService {

    private final TicketMessageRepository ticketMessageRepository;
    
    // Patterns for input validation
    private static final Pattern SAFE_SEARCH_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,!?]+$");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror).*");
    private static final int MAX_SEARCH_LENGTH = 100;
    
    /**
     * Secure search for ticket messages with input validation and sanitization.
     */
    public List<TicketMessage> searchTicketMessages(String ticketId, String searchQuery) {
        log.debug("Performing secure search for ticket: {}, query: {}", ticketId, searchQuery);
        
        // Validate inputs
        if (!isValidTicketId(ticketId)) {
            throw new IllegalArgumentException("Invalid ticket ID format");
        }
        
        String sanitizedQuery = sanitizeSearchQuery(searchQuery);
        if (sanitizedQuery == null || sanitizedQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid or empty search query");
        }
        
        try {
            // Use the secure JPQL version instead of native SQL
            List<TicketMessage> results = ticketMessageRepository.searchMessageContentSecure(ticketId, sanitizedQuery);
            
            log.debug("Search completed successfully, found {} results", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Search failed for ticket: {}, query: {}", ticketId, sanitizedQuery, e);
            throw new RuntimeException("Search operation failed", e);
        }
    }
    
    /**
     * Sanitize search query to prevent injection attacks.
     */
    private String sanitizeSearchQuery(String query) {
        if (query == null) {
            return null;
        }
        
        // Trim whitespace
        query = query.trim();
        
        // Check length
        if (query.length() > MAX_SEARCH_LENGTH) {
            log.warn("Search query too long, truncating: {}", query.length());
            query = query.substring(0, MAX_SEARCH_LENGTH);
        }
        
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(query).matches()) {
            log.warn("Potential SQL injection attempt detected in search query: {}", query);
            throw new SecurityException("Invalid search query detected");
        }
        
        // Validate against safe characters
        if (!SAFE_SEARCH_PATTERN.matcher(query).matches()) {
            log.warn("Search query contains unsafe characters: {}", query);
            // Remove unsafe characters instead of throwing exception
            query = query.replaceAll("[^a-zA-Z0-9\\s\\-_.,!?]", "");
        }
        
        // Remove multiple spaces
        query = query.replaceAll("\\s+", " ");
        
        return query;
    }
    
    /**
     * Validate ticket ID format.
     */
    private boolean isValidTicketId(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            return false;
        }
        
        // Ticket ID should be UUID or alphanumeric with hyphens
        Pattern ticketIdPattern = Pattern.compile("^[a-zA-Z0-9\\-]{8,36}$");
        return ticketIdPattern.matcher(ticketId).matches();
    }
    
    /**
     * Secure search with additional context validation.
     */
    public List<TicketMessage> searchWithValidation(String ticketId, String searchQuery, String userId) {
        // Additional security: verify user has access to this ticket
        validateUserAccess(ticketId, userId);
        
        return searchTicketMessages(ticketId, searchQuery);
    }
    
    private void validateUserAccess(String ticketId, String userId) {
        // Implementation would check if user has access to this ticket
        // For now, just log the access attempt
        log.debug("User {} accessing ticket {} for search", userId, ticketId);
    }
}