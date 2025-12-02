package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.AssetFreeze;
import com.waqiti.compliance.domain.FreezeReason;
import com.waqiti.compliance.domain.FreezeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Asset Freeze entities
 */
@Repository
public interface AssetFreezeRepository extends MongoRepository<AssetFreeze, String> {

    Optional<AssetFreeze> findByFreezeId(String freezeId);

    List<AssetFreeze> findByUserId(String userId);

    List<AssetFreeze> findByUserIdAndIsActive(String userId, boolean isActive);

    List<AssetFreeze> findByReason(FreezeReason reason);

    List<AssetFreeze> findByStatus(FreezeStatus status);

    List<AssetFreeze> findByIsActiveTrue();

    List<AssetFreeze> findByFrozenAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByUserIdAndIsActive(String userId, boolean isActive);

    long countByReason(FreezeReason reason);

    long countByStatus(FreezeStatus status);
}
