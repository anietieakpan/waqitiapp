package com.waqiti.common.compliance.provider;

import com.waqiti.common.compliance.model.OFACScreeningRequest;
import com.waqiti.common.compliance.model.ProviderScreeningResult;

/**
 * Base interface for sanctions screening providers
 */
public interface SanctionsProvider {

    /**
     * Screen an entity against sanctions lists
     */
    ProviderScreeningResult screen(OFACScreeningRequest request);

    /**
     * Screen an entity against sanctions lists (alias)
     */
    default ProviderScreeningResult screenEntity(OFACScreeningRequest request) {
        return screen(request);
    }

    /**
     * Get provider name
     */
    String getProviderName();

    /**
     * Check if provider is available
     */
    boolean isAvailable();

    /**
     * Get provider priority (lower is higher priority)
     */
    int getPriority();

    /**
     * Update the sanctions list from the provider
     */
    default void updateSanctionsList() {
        // Default implementation does nothing - providers can override
    }
}