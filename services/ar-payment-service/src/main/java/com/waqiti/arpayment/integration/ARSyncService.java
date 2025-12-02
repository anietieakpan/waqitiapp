package com.waqiti.arpayment.integration;

import com.waqiti.arpayment.domain.ARSession;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AR Synchronization Service
 * Multi-user AR session synchronization for collaborative payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ARSyncService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Set<UUID>> multiUserSessions = new ConcurrentHashMap<>();

    public void synchronizeSession(ARSession hostSession, List<UUID> participantIds) {
        log.info("Synchronizing multi-user AR session: {} with {} participants",
                hostSession.getSessionToken(), participantIds.size());

        String sessionId = hostSession.getSessionToken();
        Set<UUID> participants = multiUserSessions.computeIfAbsent(sessionId, k -> new HashSet<>());
        participants.addAll(participantIds);

        meterRegistry.counter("ar.sync.sessions",
                "participants", String.valueOf(participantIds.size())).increment();
    }

    public Set<UUID> getParticipants(String sessionToken) {
        return multiUserSessions.getOrDefault(sessionToken, Collections.emptySet());
    }

    public void removeParticipant(String sessionToken, UUID participantId) {
        Set<UUID> participants = multiUserSessions.get(sessionToken);
        if (participants != null) {
            participants.remove(participantId);
            log.info("Removed participant {} from session {}", participantId, sessionToken);

            if (participants.isEmpty()) {
                multiUserSessions.remove(sessionToken);
                log.info("All participants removed - cleaned up session {}", sessionToken);
            }
        }
    }

    public float calculateSyncQuality(String sessionToken) {
        Set<UUID> participants = getParticipants(sessionToken);
        // Quality degrades with more participants
        return Math.max(0.5f, 1.0f - (participants.size() * 0.05f));
    }
}
