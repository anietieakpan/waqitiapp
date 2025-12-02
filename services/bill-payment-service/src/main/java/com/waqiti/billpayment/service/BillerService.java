package com.waqiti.billpayment.service;

import com.waqiti.billpayment.entity.*;
import com.waqiti.billpayment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing billers and biller connections
 * Handles biller CRUD, search, and user connections
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillerService {

    private final BillerRepository billerRepository;
    private final BillerConnectionRepository billerConnectionRepository;
    private final BillPaymentAuditLogRepository auditLogRepository;

    // ========== BILLER MANAGEMENT ==========

    /**
     * Get biller by ID
     */
    @Transactional(readOnly = true)
    public Biller getBillerById(UUID billerId) {
        return billerRepository.findById(billerId)
                .orElseThrow(() -> new IllegalArgumentException("Biller not found: " + billerId));
    }

    /**
     * Search billers by name
     */
    @Transactional(readOnly = true)
    public Page<Biller> searchBillers(String searchTerm, Pageable pageable) {
        return billerRepository.searchByName(searchTerm, pageable);
    }

    /**
     * Get active billers
     */
    @Transactional(readOnly = true)
    public Page<Biller> getActiveBillers(Pageable pageable) {
        return billerRepository.findActiveBillers(pageable);
    }

    /**
     * Get billers by category
     */
    @Transactional(readOnly = true)
    public Page<Biller> getBillersByCategory(BillCategory category, Pageable pageable) {
        return billerRepository.findByCategory(category, pageable);
    }

    /**
     * Get billers supporting specific feature
     */
    @Transactional(readOnly = true)
    public List<Biller> getBillersBySupportedFeature(String feature) {
        return billerRepository.findBySupportedFeature(feature);
    }

    /**
     * Get most popular billers
     */
    @Transactional(readOnly = true)
    public List<Biller> getMostPopularBillers(int limit) {
        return billerRepository.findMostPopularBillers(Pageable.ofSize(limit));
    }

    // ========== BILLER CONNECTIONS ==========

    /**
     * Create biller connection
     */
    @Transactional
    public BillerConnection createBillerConnection(String userId, UUID billerId, String accountNumber,
                                                    String accountHolderName, String nickname) {
        log.info("Creating biller connection for user: {}, biller: {}", userId, billerId);

        // Validate biller exists and is active
        Biller biller = getBillerById(billerId);
        if (!biller.isActive()) {
            throw new IllegalStateException("Biller is not active: " + biller.getName());
        }

        // Check for duplicate connection
        boolean exists = billerConnectionRepository.existsByUserIdAndBillerIdAndAccountNumber(
                userId, billerId, accountNumber
        );

        if (exists) {
            throw new IllegalStateException("Connection already exists for this biller and account");
        }

        BillerConnection connection = BillerConnection.builder()
                .userId(userId)
                .billerId(billerId)
                .accountNumber(accountNumber)
                .accountHolderName(accountHolderName)
                .nickname(nickname)
                .status(ConnectionStatus.PENDING_VERIFICATION)
                .isVerified(false)
                .autoImportEnabled(false)
                .build();

        BillerConnection savedConnection = billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", savedConnection.getId(), "CONNECTION_CREATED", userId);

        log.info("Biller connection created: {}", savedConnection.getId());
        return savedConnection;
    }

    /**
     * Verify biller connection
     */
    @Transactional
    public void verifyBillerConnection(UUID connectionId, String userId) {
        log.info("Verifying biller connection: {}", connectionId);

        BillerConnection connection = getBillerConnection(connectionId, userId);

        if (connection.getIsVerified()) {
            throw new IllegalStateException("Connection already verified");
        }

        connection.verify();
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "CONNECTION_VERIFIED", userId);

        log.info("Biller connection verified: {}", connectionId);
    }

    /**
     * Enable auto-import for connection
     */
    @Transactional
    public void enableAutoImport(UUID connectionId, String userId, int frequencyDays) {
        log.info("Enabling auto-import for connection: {}, frequency: {} days", connectionId, frequencyDays);

        BillerConnection connection = getBillerConnection(connectionId, userId);

        if (!connection.isActive()) {
            throw new IllegalStateException("Connection must be active and verified");
        }

        connection.setAutoImportEnabled(true);
        connection.setImportFrequencyDays(frequencyDays);
        connection.setNextImportAt(LocalDateTime.now().plusDays(frequencyDays));
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "AUTO_IMPORT_ENABLED", userId);

        log.info("Auto-import enabled for connection: {}", connectionId);
    }

    /**
     * Disable auto-import for connection
     */
    @Transactional
    public void disableAutoImport(UUID connectionId, String userId) {
        log.info("Disabling auto-import for connection: {}", connectionId);

        BillerConnection connection = getBillerConnection(connectionId, userId);

        connection.setAutoImportEnabled(false);
        connection.setNextImportAt(null);
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "AUTO_IMPORT_DISABLED", userId);

        log.info("Auto-import disabled for connection: {}", connectionId);
    }

    /**
     * Get biller connection
     */
    @Transactional(readOnly = true)
    public BillerConnection getBillerConnection(UUID connectionId, String userId) {
        return billerConnectionRepository.findById(connectionId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
    }

    /**
     * Get all connections for user
     */
    @Transactional(readOnly = true)
    public List<BillerConnection> getBillerConnectionsByUser(String userId) {
        return billerConnectionRepository.findByUserId(userId);
    }

    /**
     * Get active connections for user
     */
    @Transactional(readOnly = true)
    public List<BillerConnection> getActiveConnections(String userId) {
        return billerConnectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.ACTIVE);
    }

    /**
     * Get connections due for import
     */
    @Transactional(readOnly = true)
    public List<BillerConnection> getConnectionsDueForImport() {
        return billerConnectionRepository.findConnectionsDueForImport(LocalDateTime.now());
    }

    /**
     * Update connection import status
     */
    @Transactional
    public void updateImportSuccess(UUID connectionId) {
        log.info("Updating import success for connection: {}", connectionId);

        BillerConnection connection = billerConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        connection.markImportSuccess();
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "IMPORT_SUCCESS", connection.getUserId());
    }

    /**
     * Update connection import failure
     */
    @Transactional
    public void updateImportFailure(UUID connectionId, String error) {
        log.info("Updating import failure for connection: {}, error: {}", connectionId, error);

        BillerConnection connection = billerConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));

        connection.markImportFailed(error);
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "IMPORT_FAILED", connection.getUserId());
    }

    /**
     * Disconnect biller connection
     */
    @Transactional
    public void disconnectBiller(UUID connectionId, String userId) {
        log.info("Disconnecting biller connection: {}", connectionId);

        BillerConnection connection = getBillerConnection(connectionId, userId);
        connection.softDelete(userId);
        billerConnectionRepository.save(connection);

        auditLog("BILLER_CONNECTION", connectionId, "CONNECTION_DISCONNECTED", userId);

        log.info("Biller connection disconnected: {}", connectionId);
    }

    // Helper methods

    private void auditLog(String entityType, UUID entityId, String action, String userId) {
        try {
            BillPaymentAuditLog auditLog = BillPaymentAuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }
}
