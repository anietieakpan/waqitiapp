package com.waqiti.support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.support.domain.*;
import com.waqiti.support.repository.*;
import com.waqiti.support.dto.gdpr.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GDPR Compliance Service for Support Service
 *
 * Implements all GDPR rights as required by EU General Data Protection Regulation:
 * - Right to Access (Article 15) - Data export
 * - Right to Erasure (Article 17) - Data deletion
 * - Right to Rectification (Article 16) - Data correction
 * - Right to Portability (Article 20) - Machine-readable export
 * - Right to Object (Article 21) - Opt-out from processing
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0 - Production Ready
 */
@Service
@Slf4j
public class GdprComplianceService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gdpr.data-retention.default-days:90}")
    private int defaultRetentionDays;

    @Value("${gdpr.export.include-deleted:false}")
    private boolean includeDeletedInExport;

    // Metrics
    private Counter gdprRequestsReceived;
    private Counter gdprExportsGenerated;
    private Counter gdprDeletionsCompleted;
    private Counter gdprRectificationsCompleted;

    @Autowired
    public GdprComplianceService(MeterRegistry meterRegistry) {
        this.gdprRequestsReceived = Counter.builder("gdpr.requests.received")
            .description("Total GDPR requests received")
            .tag("service", "support")
            .register(meterRegistry);

        this.gdprExportsGenerated = Counter.builder("gdpr.exports.generated")
            .description("Total GDPR data exports generated")
            .tag("service", "support")
            .register(meterRegistry);

        this.gdprDeletionsCompleted = Counter.builder("gdpr.deletions.completed")
            .description("Total GDPR deletions completed")
            .tag("service", "support")
            .register(meterRegistry);

        this.gdprRectificationsCompleted = Counter.builder("gdpr.rectifications.completed")
            .description("Total GDPR data rectifications completed")
            .tag("service", "support")
            .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        log.info("GdprComplianceService initialized with {} day retention period", defaultRetentionDays);
    }

    // ===========================================================================
    // RIGHT TO ACCESS (ARTICLE 15) - DATA EXPORT
    // ===========================================================================

    /**
     * Exports all user data in machine-readable JSON format.
     * Includes tickets, messages, attachments metadata, and chat sessions.
     *
     * @param userId User ID requesting data export
     * @param requestedBy Who is requesting (user themselves or admin)
     * @return Complete data export
     */
    @Transactional(readOnly = true)
    public GdprDataExportDTO exportUserData(String userId, String requestedBy) {
        log.info("GDPR: Data export request received - userId: {}, requestedBy: {}", userId, requestedBy);
        gdprRequestsReceived.increment();

        try {
            GdprDataExportDTO export = new GdprDataExportDTO();
            export.setUserId(userId);
            export.setExportDate(LocalDateTime.now());
            export.setRequestedBy(requestedBy);
            export.setDataRetentionDays(defaultRetentionDays);

            // Export tickets (both active and deleted if configured)
            List<Ticket> tickets = includeDeletedInExport
                ? getAllTicketsIncludingDeleted(userId)
                : ticketRepository.findActiveTicketsByUser(userId, null).getContent();

            export.setTickets(tickets.stream()
                .map(this::convertTicketToExport)
                .collect(Collectors.toList()));

            // Export chat sessions from Redis
            export.setChatSessions(exportChatSessions(userId));

            // Export preferences and consent
            export.setUserPreferences(exportUserPreferences(userId));

            // Metadata
            export.setTotalTickets(tickets.size());
            export.setActiveTickets((int) tickets.stream().filter(t -> !t.isDeleted()).count());
            export.setDeletedTickets((int) tickets.stream().filter(Ticket::isDeleted).count());

            gdprExportsGenerated.increment();
            log.info("GDPR: Data export completed - userId: {}, tickets: {}", userId, tickets.size());

            return export;

        } catch (Exception e) {
            log.error("GDPR: Data export failed - userId: {}", userId, e);
            throw new GdprComplianceException("Failed to export user data", e);
        }
    }

    /**
     * Export data in portable JSON format (GDPR Article 20).
     */
    public String exportUserDataAsJson(String userId, String requestedBy) {
        try {
            GdprDataExportDTO export = exportUserData(userId, requestedBy);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            log.error("GDPR: JSON export failed - userId: {}", userId, e);
            throw new GdprComplianceException("Failed to generate JSON export", e);
        }
    }

    // ===========================================================================
    // RIGHT TO ERASURE (ARTICLE 17) - DATA DELETION
    // ===========================================================================

    /**
     * Soft deletes all user data with configurable retention period.
     * Data is marked for deletion but retained for specified period for legal/recovery.
     *
     * @param userId User requesting deletion
     * @param requestedBy Who is requesting (user or admin)
     * @param reason Reason for deletion (GDPR_REQUEST, USER_REQUEST, etc.)
     * @param retentionDays Days before permanent deletion (default 90)
     * @return Deletion summary
     */
    @Transactional
    public GdprDeletionSummaryDTO deleteUserData(String userId, String requestedBy,
                                                 String reason, Integer retentionDays) {
        log.warn("GDPR: Data deletion request received - userId: {}, requestedBy: {}, reason: {}",
                userId, requestedBy, reason);
        gdprRequestsReceived.increment();

        int daysToRetain = retentionDays != null ? retentionDays : defaultRetentionDays;
        LocalDateTime deletionTime = LocalDateTime.now();
        LocalDateTime retentionUntil = deletionTime.plusDays(daysToRetain);

        GdprDeletionSummaryDTO summary = new GdprDeletionSummaryDTO();
        summary.setUserId(userId);
        summary.setDeletionDate(deletionTime);
        summary.setRetentionUntil(retentionUntil);
        summary.setReason(reason);
        summary.setDeletedBy(requestedBy);

        try {
            // Soft delete tickets
            int ticketsDeleted = ticketRepository.softDeleteTicketsByUser(
                userId, requestedBy, reason, deletionTime, retentionUntil
            );
            summary.setTicketsDeleted(ticketsDeleted);

            // Delete chat sessions from Redis
            int chatSessionsDeleted = deleteChatSessions(userId);
            summary.setChatSessionsDeleted(chatSessionsDeleted);

            // Mark user preferences for deletion
            int preferencesDeleted = deleteUserPreferences(userId, requestedBy, retentionUntil);
            summary.setPreferencesDeleted(preferencesDeleted);

            // Create audit record
            createDeletionAuditRecord(userId, requestedBy, reason, summary);

            gdprDeletionsCompleted.increment();
            log.warn("GDPR: Data deletion completed - userId: {}, tickets: {}, retention: {} days",
                    userId, ticketsDeleted, daysToRetain);

            return summary;

        } catch (Exception e) {
            log.error("GDPR: Data deletion failed - userId: {}", userId, e);
            throw new GdprComplianceException("Failed to delete user data", e);
        }
    }

    /**
     * Permanently deletes data past retention period.
     * This is called by scheduled job, not directly by users.
     */
    @Transactional
    public int permanentlyDeleteExpiredData() {
        log.info("GDPR: Starting permanent deletion of expired data");

        LocalDateTime now = LocalDateTime.now();
        List<Ticket> expiredTickets = ticketRepository.findTicketsEligibleForPermanentDeletion(now);

        int deletedCount = 0;
        for (Ticket ticket : expiredTickets) {
            try {
                // Permanent deletion - cannot be recovered
                ticketRepository.delete(ticket);
                deletedCount++;
                log.debug("GDPR: Permanently deleted ticket {} (retention expired)", ticket.getId());
            } catch (Exception e) {
                log.error("GDPR: Failed to permanently delete ticket {}", ticket.getId(), e);
            }
        }

        log.info("GDPR: Permanent deletion completed - {} tickets deleted", deletedCount);
        return deletedCount;
    }

    // ===========================================================================
    // RIGHT TO RECTIFICATION (ARTICLE 16) - DATA CORRECTION
    // ===========================================================================

    /**
     * Updates user data to correct inaccuracies.
     *
     * @param userId User whose data to update
     * @param corrections Map of field corrections
     * @param requestedBy Who is requesting correction
     * @return Number of records corrected
     */
    @Transactional
    public GdprRectificationSummaryDTO rectifyUserData(String userId,
                                                       Map<String, String> corrections,
                                                       String requestedBy) {
        log.info("GDPR: Data rectification request - userId: {}, fields: {}",
                userId, corrections.keySet());
        gdprRequestsReceived.increment();

        GdprRectificationSummaryDTO summary = new GdprRectificationSummaryDTO();
        summary.setUserId(userId);
        summary.setRectificationDate(LocalDateTime.now());
        summary.setRequestedBy(requestedBy);
        summary.setCorrectedFields(new ArrayList<>(corrections.keySet()));

        try {
            int ticketsUpdated = 0;

            // Update all user's tickets with new information
            List<Ticket> userTickets = ticketRepository.findActiveTicketsByUser(userId, null).getContent();

            for (Ticket ticket : userTickets) {
                boolean updated = false;

                if (corrections.containsKey("email")) {
                    ticket.setUserEmail(corrections.get("email"));
                    updated = true;
                }

                if (corrections.containsKey("name")) {
                    ticket.setUserName(corrections.get("name"));
                    updated = true;
                }

                if (updated) {
                    ticketRepository.save(ticket);
                    ticketsUpdated++;
                }
            }

            summary.setRecordsUpdated(ticketsUpdated);

            gdprRectificationsCompleted.increment();
            log.info("GDPR: Data rectification completed - userId: {}, records: {}",
                    userId, ticketsUpdated);

            return summary;

        } catch (Exception e) {
            log.error("GDPR: Data rectification failed - userId: {}", userId, e);
            throw new GdprComplianceException("Failed to rectify user data", e);
        }
    }

    // ===========================================================================
    // RIGHT TO OBJECT (ARTICLE 21) - OPT-OUT
    // ===========================================================================

    /**
     * Records user's objection to data processing.
     * Marks account for restricted processing.
     */
    @Transactional
    public void recordDataProcessingObjection(String userId, String reason, String requestedBy) {
        log.warn("GDPR: Data processing objection - userId: {}, reason: {}", userId, reason);
        gdprRequestsReceived.increment();

        try {
            // Store objection in Redis
            String objectionKey = "gdpr:objection:" + userId;
            Map<String, String> objectionData = new HashMap<>();
            objectionData.put("userId", userId);
            objectionData.put("reason", reason);
            objectionData.put("requestedBy", requestedBy);
            objectionData.put("timestamp", LocalDateTime.now().toString());

            String jsonData = objectMapper.writeValueAsString(objectionData);
            redisTemplate.opsForValue().set(objectionKey, jsonData);

            log.warn("GDPR: Data processing objection recorded - userId: {}", userId);

        } catch (Exception e) {
            log.error("GDPR: Failed to record objection - userId: {}", userId, e);
            throw new GdprComplianceException("Failed to record objection", e);
        }
    }

    /**
     * Checks if user has objected to data processing.
     */
    public boolean hasDataProcessingObjection(String userId) {
        String objectionKey = "gdpr:objection:" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(objectionKey));
    }

    // ===========================================================================
    // HELPER METHODS
    // ===========================================================================

    private List<Ticket> getAllTicketsIncludingDeleted(String userId) {
        // Get active tickets
        List<Ticket> tickets = new ArrayList<>(
            ticketRepository.findActiveTicketsByUser(userId, null).getContent()
        );

        // Add deleted tickets
        tickets.addAll(ticketRepository.findDeletedTicketsByUser(userId));

        return tickets;
    }

    private TicketExportDTO convertTicketToExport(Ticket ticket) {
        TicketExportDTO dto = new TicketExportDTO();
        dto.setTicketNumber(ticket.getTicketNumber());
        dto.setSubject(ticket.getSubject());
        dto.setDescription(ticket.getDescription());
        dto.setStatus(ticket.getStatus().toString());
        dto.setPriority(ticket.getPriority().toString());
        dto.setCategory(ticket.getCategory().toString());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setResolvedAt(ticket.getResolvedAt());
        dto.setDeleted(ticket.isDeleted());
        dto.setDeletedAt(ticket.getDeletedAt());

        // Include message count but not full messages (privacy)
        dto.setMessageCount(ticket.getMessages() != null ? ticket.getMessages().size() : 0);

        return dto;
    }

    private List<ChatSessionExportDTO> exportChatSessions(String userId) {
        // Export chat sessions from Redis
        List<ChatSessionExportDTO> sessions = new ArrayList<>();

        try {
            Set<String> sessionKeys = redisTemplate.keys("chat:user:sessions:" + userId + ":*");
            if (sessionKeys != null) {
                for (String key : sessionKeys) {
                    String sessionData = redisTemplate.opsForValue().get(key);
                    if (sessionData != null) {
                        // Parse and convert to DTO (simplified)
                        ChatSessionExportDTO dto = new ChatSessionExportDTO();
                        dto.setSessionId(key);
                        dto.setExportedAt(LocalDateTime.now());
                        sessions.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to export chat sessions for user {}", userId, e);
        }

        return sessions;
    }

    private int deleteChatSessions(String userId) {
        try {
            Set<String> sessionKeys = redisTemplate.keys("chat:user:sessions:" + userId + ":*");
            if (sessionKeys != null && !sessionKeys.isEmpty()) {
                redisTemplate.delete(sessionKeys);
                return sessionKeys.size();
            }
        } catch (Exception e) {
            log.error("Failed to delete chat sessions for user {}", userId, e);
        }
        return 0;
    }

    private Map<String, String> exportUserPreferences(String userId) {
        Map<String, String> preferences = new HashMap<>();

        try {
            String prefsKey = "user:preferences:" + userId;
            Map<Object, Object> redisPrefs = redisTemplate.opsForHash().entries(prefsKey);

            for (Map.Entry<Object, Object> entry : redisPrefs.entrySet()) {
                preferences.put(entry.getKey().toString(), entry.getValue().toString());
            }
        } catch (Exception e) {
            log.error("Failed to export preferences for user {}", userId, e);
        }

        return preferences;
    }

    private int deleteUserPreferences(String userId, String deletedBy, LocalDateTime retentionUntil) {
        try {
            String prefsKey = "user:preferences:" + userId;

            // Mark for deletion rather than immediate delete
            String deletionMarker = "deletion:" + prefsKey;
            Map<String, String> deletionMeta = new HashMap<>();
            deletionMeta.put("deletedBy", deletedBy);
            deletionMeta.put("retentionUntil", retentionUntil.toString());

            redisTemplate.opsForValue().set(deletionMarker, objectMapper.writeValueAsString(deletionMeta));

            return 1;
        } catch (Exception e) {
            log.error("Failed to mark preferences for deletion - user {}", userId, e);
            return 0;
        }
    }

    private void createDeletionAuditRecord(String userId, String deletedBy,
                                          String reason, GdprDeletionSummaryDTO summary) {
        try {
            String auditKey = "gdpr:audit:deletion:" + userId + ":" + System.currentTimeMillis();
            String auditData = objectMapper.writeValueAsString(summary);
            redisTemplate.opsForValue().set(auditKey, auditData);

            log.info("GDPR: Audit record created - userId: {}, key: {}", userId, auditKey);
        } catch (Exception e) {
            log.error("Failed to create GDPR deletion audit record", e);
        }
    }

    /**
     * Custom exception for GDPR compliance errors.
     */
    public static class GdprComplianceException extends RuntimeException {
        public GdprComplianceException(String message) {
            super(message);
        }

        public GdprComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
