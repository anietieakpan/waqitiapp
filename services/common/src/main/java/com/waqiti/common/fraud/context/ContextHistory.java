package com.waqiti.common.fraud.context;

import com.waqiti.common.fraud.FraudContext;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List; /**
 * Context history tracker
 */
@Data
public class ContextHistory {
    private final String userId;
    private final List<FraudContext> contexts = new ArrayList<>();
    private LocalDateTime firstContext;
    private LocalDateTime lastContext;
    
    public ContextHistory(String userId) {
        this.userId = userId;
    }
    
    public void addContext(FraudContext context) {
        contexts.add(context);
        if (firstContext == null) {
            firstContext = context.getTimestamp();
        }
        lastContext = context.getTimestamp();
        
        // Keep only last 100 contexts
        if (contexts.size() > 100) {
            contexts.remove(0);
        }
    }
}
