package com.waqiti.common.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map; /**
 * Behavior Profile Data Class
 */
@lombok.Data
public class BehaviorProfile implements java.io.Serializable {
    private String userId;
    private long eventCount = 0;
    private Instant firstActivity;
    private Instant lastActivity;
    private String maturityLevel = "NEW";
    private double averageRequestsPerMinute = 0;
    
    // Activity patterns
    private Map<Integer, Integer> hourlyActivity = new HashMap<>(); // Hour -> Count
    private Map<String, Integer> endpointFrequency = new HashMap<>(); // Endpoint -> Count
    private Map<String, Integer> endpointSequences = new HashMap<>(); // Sequence -> Count
    private List<String> recentEndpoints = new ArrayList<>();
    
    public BehaviorProfile() {}
    
    public BehaviorProfile(String userId) {
        this.userId = userId;
    }
}
