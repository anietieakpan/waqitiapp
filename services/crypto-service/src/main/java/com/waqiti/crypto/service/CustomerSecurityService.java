package com.waqiti.crypto.service;

import com.waqiti.crypto.entity.CustomerSecurityBlock;
import com.waqiti.crypto.repository.CustomerSecurityBlockRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Customer Security Service
 * Manages customer account blocking/unblocking for regulatory compliance and security
 * Supports temporary and permanent blocks with full audit trail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSecurityService {

    private final CustomerSecurityBlockRepository customerSecurityBlockRepository;
    private final MeterRegistry meterRegistry;

    private Counter customersBlocked;
    private Counter customersUnblocked;
    private Counter permanentBlocks;
    private Counter temporaryBlocks;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        customersBlocked = Counter.builder("customers_blocked_total")
                .description("Total customers blocked")
                .register(meterRegistry);
        customersUnblocked = Counter.builder("customers_unblocked_total")
                .description("Total customers unblocked")
                .register(meterRegistry);
        permanentBlocks = Counter.builder("customers_permanent_blocks_total")
                .description("Total permanent customer blocks")
                .register(meterRegistry);
        temporaryBlocks = Counter.builder("customers_temporary_blocks_total")
                .description("Total temporary customer blocks")
                .register(meterRegistry);
    }

    /**
     * Block customer permanently for regulatory compliance violation
     */
    @Transactional
    public CustomerSecurityBlock blockCustomerPermanently(
            String customerId,
            String blockReason,
            String violationType,
            String correlationId) {

        log.error("PERMANENTLY BLOCKING CUSTOMER: {} reason: {} violation: {} correlationId: {}",
                customerId, blockReason, violationType, correlationId);

        UUID customerUuid = UUID.fromString(customerId);

        // Check if already blocked
        List<CustomerSecurityBlock> existingBlocks = customerSecurityBlockRepository
                .findByCustomerIdAndActiveTrue(customerUuid);

        if (!existingBlocks.isEmpty()) {
            CustomerSecurityBlock existingBlock = existingBlocks.get(0);
            log.warn("Customer already blocked: {} existing block: {} correlationId: {}",
                    customerId, existingBlock.getId(), correlationId);
            return existingBlock;
        }

        // Create permanent block
        CustomerSecurityBlock block = CustomerSecurityBlock.builder()
                .customerId(customerUuid)
                .blockType("PERMANENT")
                .blockReason(blockReason)
                .violationType(violationType)
                .correlationId(correlationId)
                .blockedAt(Instant.now())
                .blockedBy("system")
                .active(true)
                .isPermanent(true)
                .expiresAt(null)
                .build();

        block = customerSecurityBlockRepository.save(block);

        customersBlocked.increment();
        permanentBlocks.increment();

        log.error("Customer permanently blocked: {} block ID: {} correlationId: {}",
                customerId, block.getId(), correlationId);

        return block;
    }

    /**
     * Block customer temporarily for specified duration
     */
    @Transactional
    public CustomerSecurityBlock blockCustomerTemporarily(
            String customerId,
            String blockReason,
            String violationType,
            int durationDays,
            String correlationId) {

        log.warn("TEMPORARILY BLOCKING CUSTOMER: {} for {} days - reason: {} violation: {} correlationId: {}",
                customerId, durationDays, blockReason, violationType, correlationId);

        UUID customerUuid = UUID.fromString(customerId);

        // Check if already blocked
        List<CustomerSecurityBlock> existingBlocks = customerSecurityBlockRepository
                .findByCustomerIdAndActiveTrue(customerUuid);

        if (!existingBlocks.isEmpty()) {
            CustomerSecurityBlock existingBlock = existingBlocks.get(0);
            log.warn("Customer already blocked: {} existing block: {} correlationId: {}",
                    customerId, existingBlock.getId(), correlationId);
            return existingBlock;
        }

        // Calculate expiration time
        Instant expiresAt = Instant.now().plus(durationDays, ChronoUnit.DAYS);

        // Create temporary block
        CustomerSecurityBlock block = CustomerSecurityBlock.builder()
                .customerId(customerUuid)
                .blockType("TEMPORARY")
                .blockReason(blockReason)
                .violationType(violationType)
                .correlationId(correlationId)
                .blockedAt(Instant.now())
                .blockedBy("system")
                .active(true)
                .isPermanent(false)
                .expiresAt(expiresAt)
                .durationDays(durationDays)
                .build();

        block = customerSecurityBlockRepository.save(block);

        customersBlocked.increment();
        temporaryBlocks.increment();

        log.warn("Customer temporarily blocked: {} block ID: {} expires: {} correlationId: {}",
                customerId, block.getId(), expiresAt, correlationId);

        return block;
    }

    /**
     * Unblock customer after compliance review
     */
    @Transactional
    public void unblockCustomer(String customerId, String unblockReason, String correlationId) {
        log.info("UNBLOCKING CUSTOMER: {} reason: {} correlationId: {}",
                customerId, unblockReason, correlationId);

        UUID customerUuid = UUID.fromString(customerId);

        List<CustomerSecurityBlock> activeBlocks = customerSecurityBlockRepository
                .findByCustomerIdAndActiveTrue(customerUuid);

        if (activeBlocks.isEmpty()) {
            log.warn("No active blocks found for customer: {} correlationId: {}", customerId, correlationId);
            return;
        }

        for (CustomerSecurityBlock block : activeBlocks) {
            block.setActive(false);
            block.setUnblockedAt(Instant.now());
            block.setUnblockReason(unblockReason);
            block.setUnblockedBy(correlationId);

            customerSecurityBlockRepository.save(block);

            customersUnblocked.increment();

            log.info("Customer block removed: {} block ID: {} correlationId: {}",
                    customerId, block.getId(), correlationId);
        }
    }

    /**
     * Check if customer is currently blocked
     */
    @Transactional(readOnly = true)
    public boolean isCustomerBlocked(String customerId) {
        UUID customerUuid = UUID.fromString(customerId);

        List<CustomerSecurityBlock> activeBlocks = customerSecurityBlockRepository
                .findByCustomerIdAndActiveTrue(customerUuid);

        if (activeBlocks.isEmpty()) {
            return false;
        }

        // Check for expired temporary blocks
        Instant now = Instant.now();
        boolean hasValidBlock = false;

        for (CustomerSecurityBlock block : activeBlocks) {
            if (block.getIsPermanent()) {
                hasValidBlock = true;
                break;
            } else if (block.getExpiresAt() != null && block.getExpiresAt().isAfter(now)) {
                hasValidBlock = true;
                break;
            } else if (block.getExpiresAt() != null && block.getExpiresAt().isBefore(now)) {
                // Expire the block
                expireBlock(block);
            }
        }

        return hasValidBlock;
    }

    /**
     * Get customer block details
     */
    @Transactional(readOnly = true)
    public CustomerSecurityBlock getCustomerBlockDetails(String customerId) {
        UUID customerUuid = UUID.fromString(customerId);

        List<CustomerSecurityBlock> activeBlocks = customerSecurityBlockRepository
                .findByCustomerIdAndActiveTrue(customerUuid);

        if (activeBlocks.isEmpty()) {
            return null;
        }

        // Return the most recent active block
        return activeBlocks.get(0);
    }

    /**
     * Expire a temporary block
     */
    @Transactional
    protected void expireBlock(CustomerSecurityBlock block) {
        log.info("Expiring temporary customer block: {} customer: {}",
                block.getId(), block.getCustomerId());

        block.setActive(false);
        block.setUnblockedAt(Instant.now());
        block.setUnblockReason("Temporary block expired");
        block.setUnblockedBy("system");

        customerSecurityBlockRepository.save(block);

        customersUnblocked.increment();
    }

    /**
     * Get all active blocks (for admin dashboard)
     */
    @Transactional(readOnly = true)
    public List<CustomerSecurityBlock> getAllActiveBlocks() {
        return customerSecurityBlockRepository.findByActiveTrue();
    }

    /**
     * Get block history for customer (for audit)
     */
    @Transactional(readOnly = true)
    public List<CustomerSecurityBlock> getCustomerBlockHistory(String customerId) {
        UUID customerUuid = UUID.fromString(customerId);
        return customerSecurityBlockRepository.findByCustomerId(customerUuid);
    }

    /**
     * Block customer for sanctions hit (immediate permanent block)
     */
    @Transactional
    public CustomerSecurityBlock blockCustomerForSanctionsHit(
            String customerId,
            String walletAddress,
            String sanctionsList,
            String correlationId) {

        log.error("SANCTIONS HIT - IMMEDIATE PERMANENT BLOCK: customer: {} wallet: {} list: {} correlationId: {}",
                customerId, walletAddress, sanctionsList, correlationId);

        String blockReason = String.format("Sanctions screening hit - Wallet: %s - List: %s",
                walletAddress, sanctionsList);

        return blockCustomerPermanently(
                customerId,
                blockReason,
                "SANCTIONS_HIT",
                correlationId
        );
    }

    /**
     * Block customer for AML violation (temporary or permanent based on severity)
     */
    @Transactional
    public CustomerSecurityBlock blockCustomerForAMLViolation(
            String customerId,
            String amlViolation,
            boolean isPermanent,
            String correlationId) {

        log.error("AML VIOLATION - BLOCKING CUSTOMER: {} violation: {} permanent: {} correlationId: {}",
                customerId, amlViolation, isPermanent, correlationId);

        if (isPermanent) {
            return blockCustomerPermanently(
                    customerId,
                    "AML violation: " + amlViolation,
                    "AML_VIOLATION",
                    correlationId
            );
        } else {
            // Temporary 30-day block for review
            return blockCustomerTemporarily(
                    customerId,
                    "AML violation under review: " + amlViolation,
                    "AML_VIOLATION_REVIEW",
                    30,
                    correlationId
            );
        }
    }
}
