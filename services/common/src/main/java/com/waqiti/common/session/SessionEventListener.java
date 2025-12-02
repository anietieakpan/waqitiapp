package com.waqiti.common.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.session.events.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Session event listener for monitoring and analytics
 */
@Component
@Slf4j
public class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {
    
    private final AtomicLong sessionsCreated = new AtomicLong(0);
    private final AtomicLong sessionsDestroyed = new AtomicLong(0);
    private final AtomicLong sessionsExpired = new AtomicLong(0);
    
    @Override
    public void onApplicationEvent(AbstractSessionEvent event) {
        if (event instanceof SessionCreatedEvent) {
            handleSessionCreated((SessionCreatedEvent) event);
        } else if (event instanceof SessionDeletedEvent) {
            handleSessionDeleted((SessionDeletedEvent) event);
        } else if (event instanceof SessionExpiredEvent) {
            handleSessionExpired((SessionExpiredEvent) event);
        }
    }
    
    private void handleSessionCreated(SessionCreatedEvent event) {
        String sessionId = event.getSessionId();
        long count = sessionsCreated.incrementAndGet();
        
        log.info("Session created: {} (Total created: {})", sessionId, count);
        
        // Extract user information if available
        Object userId = event.getSession().getAttribute("userId");
        if (userId != null) {
            log.debug("Session {} created for user: {}", sessionId, userId);
        }
        
        // Send metrics
        sendSessionMetric("session.created", 1);
    }
    
    private void handleSessionDeleted(SessionDeletedEvent event) {
        String sessionId = event.getSessionId();
        long count = sessionsDestroyed.incrementAndGet();
        
        log.info("Session deleted: {} (Total deleted: {})", sessionId, count);
        
        // Send metrics
        sendSessionMetric("session.deleted", 1);
    }
    
    private void handleSessionExpired(SessionExpiredEvent event) {
        String sessionId = event.getSessionId();
        long count = sessionsExpired.incrementAndGet();
        
        log.info("Session expired: {} (Total expired: {})", sessionId, count);
        
        // Send metrics
        sendSessionMetric("session.expired", 1);
    }
    
    private void sendSessionMetric(String metricName, long value) {
        // Integration with metrics system (Micrometer, Prometheus, etc.)
        log.debug("Metric: {} = {}", metricName, value);
    }
    
    // Getter methods for monitoring
    public long getSessionsCreated() {
        return sessionsCreated.get();
    }
    
    public long getSessionsDestroyed() {
        return sessionsDestroyed.get();
    }
    
    public long getSessionsExpired() {
        return sessionsExpired.get();
    }
    
    public long getActiveSessions() {
        return sessionsCreated.get() - sessionsDestroyed.get() - sessionsExpired.get();
    }
}