package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * HSM key cache statistics
 */
@Data
@Builder
public class HsmCacheStats {
    private int totalKeys;
    private int expiredKeys;
    private boolean cacheEnabled;
}