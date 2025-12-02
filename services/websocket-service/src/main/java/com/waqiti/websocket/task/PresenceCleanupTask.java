package com.waqiti.websocket.task;

import com.waqiti.websocket.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceCleanupTask {

    private final PresenceService presenceService;

    @Scheduled(fixedDelayString = "${presence.cleanup-interval:PT5M}")
    public void cleanupStalePresence() {
        log.debug("Running presence cleanup task");
        try {
            presenceService.cleanupStalePresence();
        } catch (Exception e) {
            log.error("Error during presence cleanup", e);
        }
    }
}