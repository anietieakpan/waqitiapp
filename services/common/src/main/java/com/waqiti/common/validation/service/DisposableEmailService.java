package com.waqiti.common.validation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disposable Email Service
 * Public service wrapper for disposable/temporary email detection
 */
@Service
@Slf4j
public class DisposableEmailService {
    
    private final Set<String> disposableDomains = ConcurrentHashMap.newKeySet();
    
    public DisposableEmailService() {
        initializeDisposableDomains();
    }
    
    private void initializeDisposableDomains() {
        // Common disposable email domains
        disposableDomains.addAll(Arrays.asList(
            "mailinator.com", "guerrillamail.com", "10minutemail.com",
            "tempmail.com", "throwaway.email", "yopmail.com",
            "fakeinbox.com", "trashmail.com", "maildrop.cc",
            "dispostable.com", "temp-mail.org", "temporaryemail.net",
            "sharklasers.com", "spam4.me", "jetable.org"
        ));
    }
    
    /**
     * Check if email domain is disposable
     */
    public boolean isDisposable(String domain) {
        if (domain == null) {
            return false;
        }
        
        String normalized = domain.toLowerCase().trim();
        return disposableDomains.contains(normalized);
    }
    
    /**
     * Get all known disposable domains
     */
    public Set<String> getAllDisposableDomains() {
        return new HashSet<>(disposableDomains);
    }
    
    /**
     * Add new disposable domain
     */
    public void addDisposableDomain(String domain) {
        if (domain != null) {
            String normalized = domain.toLowerCase().trim();
            disposableDomains.add(normalized);
            log.info("Added disposable domain: {}", normalized);
        }
    }
}