package com.waqiti.frauddetection.sanctions.service;

import com.waqiti.frauddetection.sanctions.client.OfacApiClient;
import com.waqiti.frauddetection.sanctions.dto.OfacSdnEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cache service for sanctions lists with automatic updates.
 *
 * Features:
 * - Cached OFAC SDN list (refreshed every 6 hours)
 * - Cached EU sanctions list (refreshed every 6 hours)
 * - Cached UN sanctions list (refreshed daily)
 * - Version tracking for audit compliance
 * - Automatic background refresh
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionsListCacheService {

    private final OfacApiClient ofacApiClient;

    private volatile String currentListVersion;
    private volatile LocalDateTime lastUpdateTime;

    /**
     * Get cached OFAC SDN list.
     * Cache refreshed every 6 hours or on demand.
     */
    @Cacheable(value = "ofac-sdn-list", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getOfacSdnList() {
        log.info("Fetching OFAC SDN list from API");

        try {
            List<OfacSdnEntry> sdnList = ofacApiClient.getOfacSdnList();
            updateListVersion();
            log.info("Successfully fetched {} OFAC SDN entries", sdnList.size());
            return sdnList;
        } catch (Exception e) {
            log.error("Failed to fetch OFAC SDN list", e);
            throw new SanctionsListUpdateException("Failed to fetch OFAC SDN list", e);
        }
    }

    /**
     * Get cached EU sanctions list.
     */
    @Cacheable(value = "eu-sanctions-list", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getEuSanctionsList() {
        log.info("Fetching EU sanctions list from API");

        try {
            List<OfacSdnEntry> euList = ofacApiClient.getEuSanctionsList();
            log.info("Successfully fetched {} EU sanctions entries", euList.size());
            return euList;
        } catch (Exception e) {
            log.error("Failed to fetch EU sanctions list", e);
            throw new SanctionsListUpdateException("Failed to fetch EU sanctions list", e);
        }
    }

    /**
     * Get cached UN sanctions list.
     */
    @Cacheable(value = "un-sanctions-list", unless = "#result == null || #result.isEmpty()")
    public List<OfacSdnEntry> getUnSanctionsList() {
        log.info("Fetching UN sanctions list from API");

        try {
            List<OfacSdnEntry> unList = ofacApiClient.getUnSanctionsList();
            log.info("Successfully fetched {} UN sanctions entries", unList.size());
            return unList;
        } catch (Exception e) {
            log.error("Failed to fetch UN sanctions list", e);
            throw new SanctionsListUpdateException("Failed to fetch UN sanctions list", e);
        }
    }

    /**
     * Get current list version for audit trail.
     */
    public String getCurrentListVersion() {
        if (currentListVersion == null) {
            updateListVersion();
        }
        return currentListVersion;
    }

    /**
     * Get last update timestamp.
     */
    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Refresh all sanctions lists (scheduled every 6 hours).
     */
    @Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
    @CacheEvict(value = {"ofac-sdn-list", "eu-sanctions-list", "un-sanctions-list"}, allEntries = true)
    public void refreshSanctionsLists() {
        log.info("Starting scheduled sanctions list refresh");

        try {
            // Refresh all lists asynchronously
            CompletableFuture<Void> ofacRefresh = CompletableFuture.runAsync(() -> {
                try {
                    getOfacSdnList();
                    log.info("OFAC SDN list refreshed successfully");
                } catch (Exception e) {
                    log.error("Failed to refresh OFAC SDN list", e);
                }
            });

            CompletableFuture<Void> euRefresh = CompletableFuture.runAsync(() -> {
                try {
                    getEuSanctionsList();
                    log.info("EU sanctions list refreshed successfully");
                } catch (Exception e) {
                    log.error("Failed to refresh EU sanctions list", e);
                }
            });

            CompletableFuture<Void> unRefresh = CompletableFuture.runAsync(() -> {
                try {
                    getUnSanctionsList();
                    log.info("UN sanctions list refreshed successfully");
                } catch (Exception e) {
                    log.error("Failed to refresh UN sanctions list", e);
                }
            });

            // Wait for all refreshes to complete
            try {
                CompletableFuture.allOf(ofacRefresh, euRefresh, unRefresh)
                    .get(5, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Sanctions list refresh timed out after 5 minutes", e);
                List.of(ofacRefresh, euRefresh, unRefresh).forEach(f -> f.cancel(true));
                throw new RuntimeException("Sanctions list refresh timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Sanctions list refresh execution failed", e.getCause());
                throw new RuntimeException("Sanctions list refresh failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sanctions list refresh interrupted", e);
                throw new RuntimeException("Sanctions list refresh interrupted", e);
            }

            updateListVersion();
            log.info("Sanctions list refresh completed. Version: {}", currentListVersion);

        } catch (Exception e) {
            log.error("Error during sanctions list refresh", e);
        }
    }

    /**
     * Force immediate refresh of all lists.
     */
    @CacheEvict(value = {"ofac-sdn-list", "eu-sanctions-list", "un-sanctions-list"}, allEntries = true)
    public void forceRefresh() {
        log.info("Force refreshing sanctions lists");
        refreshSanctionsLists();
    }

    /**
     * Update list version timestamp.
     */
    private void updateListVersion() {
        lastUpdateTime = LocalDateTime.now();
        currentListVersion = "v" + lastUpdateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        log.debug("Updated sanctions list version to: {}", currentListVersion);
    }

    /**
     * Get cache statistics for monitoring.
     */
    public SanctionsListCacheStats getCacheStats() {
        return SanctionsListCacheStats.builder()
            .currentVersion(currentListVersion)
            .lastUpdateTime(lastUpdateTime)
            .ofacListSize(getOfacSdnList().size())
            .euListSize(getEuSanctionsList().size())
            .unListSize(getUnSanctionsList().size())
            .build();
    }

    /**
     * Cache statistics DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class SanctionsListCacheStats {
        private String currentVersion;
        private LocalDateTime lastUpdateTime;
        private int ofacListSize;
        private int euListSize;
        private int unListSize;
    }

    /**
     * Exception for sanctions list update failures.
     */
    public static class SanctionsListUpdateException extends RuntimeException {
        public SanctionsListUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
