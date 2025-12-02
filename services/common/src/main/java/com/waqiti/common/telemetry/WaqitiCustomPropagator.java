package com.waqiti.common.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;

/**
 * Custom Context Propagator for Waqiti-specific trace context
 * 
 * Propagates additional context information:
 * - User ID for user-centric tracing
 * - Transaction ID for business transaction correlation
 * - Session ID for session tracking
 * - Compliance flags for regulatory requirements
 * - Priority level for sampling decisions
 * 
 * Headers:
 * - X-Waqiti-User-Id: User identifier
 * - X-Waqiti-Transaction-Id: Business transaction ID
 * - X-Waqiti-Session-Id: User session ID
 * - X-Waqiti-Compliance: Compliance requirements
 * - X-Waqiti-Priority: Trace priority level
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
public class WaqitiCustomPropagator implements TextMapPropagator {
    
    private static final String USER_ID_HEADER = "X-Waqiti-User-Id";
    private static final String TRANSACTION_ID_HEADER = "X-Waqiti-Transaction-Id";
    private static final String SESSION_ID_HEADER = "X-Waqiti-Session-Id";
    private static final String COMPLIANCE_HEADER = "X-Waqiti-Compliance";
    private static final String PRIORITY_HEADER = "X-Waqiti-Priority";
    
    private static final ContextKey<String> USER_ID_KEY = ContextKey.named("waqiti.user.id");
    private static final ContextKey<String> TRANSACTION_ID_KEY = ContextKey.named("waqiti.transaction.id");
    private static final ContextKey<String> SESSION_ID_KEY = ContextKey.named("waqiti.session.id");
    private static final ContextKey<String> COMPLIANCE_KEY = ContextKey.named("waqiti.compliance");
    private static final ContextKey<String> PRIORITY_KEY = ContextKey.named("waqiti.priority");
    
    private static final List<String> FIELDS = List.of(
        USER_ID_HEADER,
        TRANSACTION_ID_HEADER,
        SESSION_ID_HEADER,
        COMPLIANCE_HEADER,
        PRIORITY_HEADER
    );
    
    @Override
    public Collection<String> fields() {
        return FIELDS;
    }
    
    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        if (context == null || setter == null) {
            return;
        }
        
        // Inject User ID
        String userId = context.get(USER_ID_KEY);
        if (userId != null) {
            setter.set(carrier, USER_ID_HEADER, userId);
            log.trace("Injected user ID: {}", userId);
        }
        
        // Inject Transaction ID
        String transactionId = context.get(TRANSACTION_ID_KEY);
        if (transactionId != null) {
            setter.set(carrier, TRANSACTION_ID_HEADER, transactionId);
            log.trace("Injected transaction ID: {}", transactionId);
        }
        
        // Inject Session ID
        String sessionId = context.get(SESSION_ID_KEY);
        if (sessionId != null) {
            setter.set(carrier, SESSION_ID_HEADER, sessionId);
            log.trace("Injected session ID: {}", sessionId);
        }
        
        // Inject Compliance Requirements
        String compliance = context.get(COMPLIANCE_KEY);
        if (compliance != null) {
            setter.set(carrier, COMPLIANCE_HEADER, compliance);
            log.trace("Injected compliance: {}", compliance);
        }
        
        // Inject Priority
        String priority = context.get(PRIORITY_KEY);
        if (priority != null) {
            setter.set(carrier, PRIORITY_HEADER, priority);
            log.trace("Injected priority: {}", priority);
        }
    }
    
    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        if (context == null || getter == null) {
            return context;
        }
        
        Context result = context;
        
        // Extract User ID
        String userId = getter.get(carrier, USER_ID_HEADER);
        if (userId != null) {
            result = result.with(USER_ID_KEY, userId);
            log.trace("Extracted user ID: {}", userId);
        }
        
        // Extract Transaction ID
        String transactionId = getter.get(carrier, TRANSACTION_ID_HEADER);
        if (transactionId != null) {
            result = result.with(TRANSACTION_ID_KEY, transactionId);
            log.trace("Extracted transaction ID: {}", transactionId);
        }
        
        // Extract Session ID
        String sessionId = getter.get(carrier, SESSION_ID_HEADER);
        if (sessionId != null) {
            result = result.with(SESSION_ID_KEY, sessionId);
            log.trace("Extracted session ID: {}", sessionId);
        }
        
        // Extract Compliance Requirements
        String compliance = getter.get(carrier, COMPLIANCE_HEADER);
        if (compliance != null) {
            result = result.with(COMPLIANCE_KEY, compliance);
            log.trace("Extracted compliance: {}", compliance);
        }
        
        // Extract Priority
        String priority = getter.get(carrier, PRIORITY_HEADER);
        if (priority != null) {
            result = result.with(PRIORITY_KEY, priority);
            log.trace("Extracted priority: {}", priority);
        }
        
        return result;
    }
    
    /**
     * Helper method to set user context
     */
    public static Context withUser(Context context, String userId) {
        return context.with(USER_ID_KEY, userId);
    }
    
    /**
     * Helper method to set transaction context
     */
    public static Context withTransaction(Context context, String transactionId) {
        return context.with(TRANSACTION_ID_KEY, transactionId);
    }
    
    /**
     * Helper method to set session context
     */
    public static Context withSession(Context context, String sessionId) {
        return context.with(SESSION_ID_KEY, sessionId);
    }
    
    /**
     * Helper method to set compliance context
     */
    public static Context withCompliance(Context context, String compliance) {
        return context.with(COMPLIANCE_KEY, compliance);
    }
    
    /**
     * Helper method to set priority context
     */
    public static Context withPriority(Context context, String priority) {
        return context.with(PRIORITY_KEY, priority);
    }
    
    /**
     * Helper method to get user from context
     */
    public static String getUser(Context context) {
        return context.get(USER_ID_KEY);
    }
    
    /**
     * Helper method to get transaction from context
     */
    public static String getTransaction(Context context) {
        return context.get(TRANSACTION_ID_KEY);
    }
    
    /**
     * Helper method to get session from context
     */
    public static String getSession(Context context) {
        return context.get(SESSION_ID_KEY);
    }
    
    /**
     * Helper method to get compliance from context
     */
    public static String getCompliance(Context context) {
        return context.get(COMPLIANCE_KEY);
    }
    
    /**
     * Helper method to get priority from context
     */
    public static String getPriority(Context context) {
        return context.get(PRIORITY_KEY);
    }
}