package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.AssetFreeze;
import com.waqiti.compliance.repository.AssetFreezeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing asset freezes
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AssetFreezeService {

    private final AssetFreezeRepository freezeRepository;

    /**
     * Apply asset freeze to user account
     */
    @Transactional
    public void applyAssetFreeze(AssetFreeze freeze, String correlationId) {
        log.warn("Applying asset freeze - userId: {}, freezeId: {}, reason: {}, correlationId: {}",
            freeze.getUserId(), freeze.getFreezeId(), freeze.getReason(), correlationId);

        freeze.setIsActive(true);
        freeze.setFrozenAt(LocalDateTime.now());
        freeze.setCreatedAt(LocalDateTime.now());
        freeze.setCreatedBy("SYSTEM");

        freezeRepository.save(freeze);

        log.error("CRITICAL: Asset freeze applied - userId: {}, freezeId: {}, correlationId: {}",
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
    }

    /**
     * Release asset freeze
     */
    @Transactional
    public void releaseAssetFreeze(String freezeId, String reason, String releasedBy) {
        log.info("Releasing asset freeze - freezeId: {}, reason: {}, releasedBy: {}",
            freezeId, reason, releasedBy);

        freezeRepository.findByFreezeId(freezeId).ifPresent(freeze -> {
            freeze.setIsActive(false);
            freeze.setReleasedAt(LocalDateTime.now());
            freeze.setReleasedBy(releasedBy);
            freeze.setReleaseReason(reason);
            freeze.setUpdatedAt(LocalDateTime.now());
            freeze.setUpdatedBy(releasedBy);

            freezeRepository.save(freeze);

            log.info("Asset freeze released - freezeId: {}, userId: {}",
                freezeId, freeze.getUserId());
        });
    }

    /**
     * Check if user has active freeze
     */
    public boolean hasActiveFreeze(String userId) {
        return freezeRepository.existsByUserIdAndIsActive(userId, true);
    }
}
