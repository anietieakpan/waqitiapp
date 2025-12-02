package com.waqiti.payment.ach;

import com.waqiti.payment.entity.ACHTransfer;
import com.waqiti.payment.entity.ACHTransferStatus;
import com.waqiti.payment.repository.ACHTransactionRepository;
import com.waqiti.payment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Separate service for transactional ACH batch processing operations
 * This ensures proper Spring AOP proxy behavior for @Transactional methods
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ACHBatchProcessorService {

    private final ACHTransactionRepository achRepository;
    private final NotificationService notificationService;
    private final NACHAFileGenerator nachaFileGenerator;
    private final FederalHolidayService holidayService;

    @Transactional
    public void processBatch(String batchKey, List<ACHTransfer> transfers) {
        log.info("Processing ACH batch: {} with {} transfers", batchKey, transfers.size());
        
        try {
            // Update all transfers to processing status
            transfers.forEach(transfer -> {
                transfer.setStatus(ACHTransferStatus.PROCESSING);
                transfer.setBatchId(batchKey);
                transfer.setProcessedAt(java.time.LocalDateTime.now());
            });
            
            // Save updated statuses
            achRepository.saveAll(transfers);
            
            // Generate NACHA file
            NACHAFile nachaFile = nachaFileGenerator.generateFile(batchKey, transfers);
            
            if (!nachaFile.isValid()) {
                throw new RuntimeException("Generated NACHA file is invalid");
            }
            
            // Submit to ACH network (simulated)
            submitToACHNetwork(nachaFile, transfers);
            
            // Update transfers to submitted status
            transfers.forEach(transfer -> {
                transfer.setStatus(ACHTransferStatus.SUBMITTED);
                transfer.setSubmittedAt(java.time.LocalDateTime.now());
            });
            
            achRepository.saveAll(transfers);
            
            // Send batch notifications
            sendBatchNotifications(transfers);
            
            log.info("ACH batch processed successfully: {}", batchKey);
            
        } catch (Exception e) {
            log.error("Error processing ACH batch: {}", batchKey, e);
            
            // Update transfers to failed status
            transfers.forEach(transfer -> {
                transfer.setStatus(ACHTransferStatus.FAILED);
                transfer.setFailureReason(e.getMessage());
                transfer.setFailedAt(java.time.LocalDateTime.now());
            });
            
            achRepository.saveAll(transfers);
            throw e;
        }
    }

    private void submitToACHNetwork(NACHAFile nachaFile, List<ACHTransfer> transfers) {
        // In a real implementation, this would submit to the ACH network
        log.info("Submitting NACHA file to ACH network for {} transfers", transfers.size());
        
        // Simulate network submission delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ACH network submission interrupted", e);
        }
    }

    private void sendBatchNotifications(List<ACHTransfer> transfers) {
        transfers.forEach(transfer -> {
            try {
                notificationService.sendACHTransferNotification(
                    transfer.getUserId(),
                    transfer.getId(),
                    transfer.getStatus(),
                    transfer.getAmount()
                );
            } catch (Exception e) {
                log.warn("Failed to send notification for transfer: {}", transfer.getId(), e);
            }
        });
    }
}