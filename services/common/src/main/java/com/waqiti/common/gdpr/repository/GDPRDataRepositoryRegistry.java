package com.waqiti.common.gdpr.repository;

import com.waqiti.common.gdpr.GDPRDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for GDPR data repositories across all microservices
 */
@Slf4j
@Component
public class GDPRDataRepositoryRegistry {

    private final Map<String, GDPRDataRepository> repositories = new ConcurrentHashMap<>();

    public void registerRepository(String entityType, GDPRDataRepository repository) {
        repositories.put(entityType, repository);
        log.info("Registered GDPR repository for entity type: {}", entityType);
    }

    /**
     * Alias for registerRepository for compatibility
     */
    public void register(String entityType, GDPRDataRepository repository) {
        registerRepository(entityType, repository);
    }

    public GDPRDataRepository getRepository(String entityType) {
        return repositories.get(entityType);
    }

    public Set<String> getAllEntityTypes() {
        return repositories.keySet();
    }

    public Collection<GDPRDataRepository> getAllRepositories() {
        return repositories.values();
    }
}
