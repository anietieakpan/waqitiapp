package com.waqiti.payment.repository;

import com.waqiti.payment.entity.EncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, UUID> {
    
    Optional<EncryptionKey> findByKeyId(String keyId);
    
    Optional<EncryptionKey> findByAlias(String alias);
    
    List<EncryptionKey> findByStatus(String status);
    
    @Query("SELECT e FROM EncryptionKey e WHERE e.status = 'ACTIVE' AND e.rotationEnabled = true AND e.nextRotationAt <= :now")
    List<EncryptionKey> findKeysRequiringRotation(@Param("now") LocalDateTime now);
    
    @Query("SELECT e FROM EncryptionKey e WHERE e.status = 'ACTIVE' AND e.expiresAt IS NOT NULL AND e.expiresAt <= :now")
    List<EncryptionKey> findExpiredKeys(@Param("now") LocalDateTime now);
    
    List<EncryptionKey> findByPurpose(String purpose);
    
    @Query("SELECT COUNT(e) FROM EncryptionKey e WHERE e.status = 'ACTIVE'")
    long countActiveKeys();
}