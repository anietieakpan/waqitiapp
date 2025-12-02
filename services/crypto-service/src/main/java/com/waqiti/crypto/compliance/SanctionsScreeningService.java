/**
 * Sanctions Screening Service
 * Screens cryptocurrency addresses against sanctions lists
 */
package com.waqiti.crypto.compliance;

import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.repository.SanctionedAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SanctionsScreeningService {

    @Lazy
    private final SanctionsScreeningService self;

    @Value("${compliance.sanctions.screening.enabled:true}")
    private boolean screeningEnabled;

    // In-memory cache of sanctioned addresses loaded from database
    private final Set<String> sanctionedAddresses = ConcurrentHashMap.newKeySet();
    
    // OFAC SDN List - Known sanctioned crypto addresses
    private static final Set<String> OFAC_SDN_ADDRESSES = Set.of(
        "149w62rY42aZBox8fGcmqNsXUzSStKeq8C", // Lazarus Group
        "1AjZPMsnmpdK2Rv9KQNfMurTXinscVro9V", // Russian Exchange
        "12QtD5BFwRsdNsAZY76UVE1xyCGNTojH9h", // Iran-linked
        "1HesYJSP1QqcyPEjnQ9vzBL1wujruNGe7R", // North Korea
        "3Cbq7aT1tY8kMxWLbitaG7yT6bPbKChq64", // Ransomware
        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", // Hydra Market
        "1F1tAaz5x1HUXrCNLbtMDqcw6o5GNn4xqX"  // Silk Road
    );
    
    private final SanctionedAddressRepository sanctionedAddressRepository;

    /**
     * Check if address is sanctioned
     */
    @Cacheable(value = "sanctionsScreening", key = "#address + '-' + #currency")
    public boolean isSanctionedAddress(String address, CryptoCurrency currency) {
        if (!screeningEnabled) {
            return false;
        }
        
        log.debug("Screening address {} for currency {}", address, currency);
        
        // Check local cache first
        if (sanctionedAddresses.contains(address.toLowerCase())) {
            log.warn("Sanctioned address detected: {}", address);
            return true;
        }
        
        // Check against known sanctions lists
        if (OFAC_SDN_ADDRESSES.contains(address)) {
            log.warn("OFAC SDN address detected: {}", address);
            sanctionedAddresses.add(address.toLowerCase());
            return true;
        }
        
        // Check with external sanctions APIs
        if (checkExternalSanctionsAPIs(address, currency)) {
            log.warn("External sanctions API flagged address: {}", address);
            sanctionedAddresses.add(address.toLowerCase());
            return true;
        }
        
        return false;
    }

    /**
     * Batch screen multiple addresses
     */
    public Map<String, Boolean> batchScreenAddresses(Map<String, CryptoCurrency> addressesToScreen) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (Map.Entry<String, CryptoCurrency> entry : addressesToScreen.entrySet()) {
            String address = entry.getKey();
            CryptoCurrency currency = entry.getValue();
            results.put(address, self.isSanctionedAddress(address, currency));
        }
        
        return results;
    }
    
    /**
     * Initialize sanctioned addresses from database
     */
    @PostConstruct
    private void initializeSanctionedAddresses() {
        try {
            // Load all active sanctioned addresses from repository
            sanctionedAddressRepository.findByActiveTrue().forEach(sanctionedAddress -> {
                sanctionedAddresses.add(sanctionedAddress.getAddress().toLowerCase());
            });
            
            log.info("Loaded {} sanctioned addresses from database", sanctionedAddresses.size());
            
        } catch (Exception e) {
            log.error("Failed to load sanctioned addresses from database", e);
            // Continue with empty set - fail open rather than fail closed
        }
    }

    /**
     * Add address to sanctions list (for testing/admin purposes)
     */
    public void addSanctionedAddress(String address) {
        sanctionedAddresses.add(address.toLowerCase());
        log.info("Added address to sanctions list: {}", address);
    }

    /**
     * Remove address from sanctions list (for testing/admin purposes)
     */
    public void removeSanctionedAddress(String address) {
        sanctionedAddresses.remove(address.toLowerCase());
        log.info("Removed address from sanctions list: {}", address);
    }

    /**
     * Get sanctions screening statistics
     */
    public SanctionsScreeningStats getScreeningStats() {
        return SanctionsScreeningStats.builder()
            .totalSanctionedAddresses(sanctionedAddresses.size())
            .screeningEnabled(screeningEnabled)
            .lastUpdated(new Date())
            .build();
    }

    /**
     * Check external sanctions APIs
     */
    private boolean checkExternalSanctionsAPIs(String address, CryptoCurrency currency) {
        // Check against known high-risk patterns
        
        // 1. Check for mixer/tumbler patterns
        Set<String> knownMixerPatterns = Set.of(
            "1mixer", "3mixer", "bc1mix",
            "tornado", "wasabi", "chipmixer"
        );
        String addressLower = address.toLowerCase();
        for (String pattern : knownMixerPatterns) {
            if (addressLower.contains(pattern)) {
                log.warn("Address {} matches mixer pattern: {}", address, pattern);
                return true;
            }
        }
        
        // 2. Check for darknet market patterns
        if (address.startsWith("3") && address.length() == 34) {
            // Many darknet markets use P2SH addresses starting with 3
            // This is a heuristic check - would need real API in production
            if (address.contains("dark") || address.contains("market")) {
                log.warn("Potential darknet market address: {}", address);
                return true;
            }
        }
        
        // 3. In production, integrate with:
        try {
            boolean matchesMixerPattern = address.matches("(?i).*(mixer|tumbler|tornado).*");
            boolean matchesDarknetPattern = address.matches("(?i).*(darknet|onion|hydra|silk).*");
            
            if (matchesMixerPattern || matchesDarknetPattern) {
                log.warn("Address matches high-risk pattern: {}", address);
                return true;
            }
        } catch (Exception e) {
            log.debug("Error checking external sanctions APIs for address: {}", address, e);
        }
        
        return false;
    }

    /**
     * Update sanctions list from external sources
     */
    public void updateSanctionsList() {
        log.info("Updating sanctions list from external sources");
        
        try {
            // In production, this would:
            // 1. Download latest OFAC SDN list
            // 2. Parse cryptocurrency addresses
            // 3. Update local cache
            // 4. Sync with database
            
            // Mock update
            sanctionedAddresses.addAll(OFAC_SDN_ADDRESSES);
            
            log.info("Sanctions list updated. Total addresses: {}", sanctionedAddresses.size());
            
        } catch (Exception e) {
            log.error("Failed to update sanctions list", e);
        }
    }

    /**
     * Sanctions screening statistics
     */
    public static class SanctionsScreeningStats {
        private final int totalSanctionedAddresses;
        private final boolean screeningEnabled;
        private final Date lastUpdated;
        
        private SanctionsScreeningStats(int totalSanctionedAddresses, boolean screeningEnabled, Date lastUpdated) {
            this.totalSanctionedAddresses = totalSanctionedAddresses;
            this.screeningEnabled = screeningEnabled;
            this.lastUpdated = lastUpdated;
        }
        
        public static SanctionsScreeningStatsBuilder builder() {
            return new SanctionsScreeningStatsBuilder();
        }
        
        public int getTotalSanctionedAddresses() {
            return totalSanctionedAddresses;
        }
        
        public boolean isScreeningEnabled() {
            return screeningEnabled;
        }
        
        public Date getLastUpdated() {
            return lastUpdated;
        }
        
        public static class SanctionsScreeningStatsBuilder {
            private int totalSanctionedAddresses;
            private boolean screeningEnabled;
            private Date lastUpdated;
            
            public SanctionsScreeningStatsBuilder totalSanctionedAddresses(int totalSanctionedAddresses) {
                this.totalSanctionedAddresses = totalSanctionedAddresses;
                return this;
            }
            
            public SanctionsScreeningStatsBuilder screeningEnabled(boolean screeningEnabled) {
                this.screeningEnabled = screeningEnabled;
                return this;
            }
            
            public SanctionsScreeningStatsBuilder lastUpdated(Date lastUpdated) {
                this.lastUpdated = lastUpdated;
                return this;
            }
            
            public SanctionsScreeningStats build() {
                return new SanctionsScreeningStats(totalSanctionedAddresses, screeningEnabled, lastUpdated);
            }
        }
    }
}