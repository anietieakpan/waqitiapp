package com.waqiti.common.encryption.repository;

import com.waqiti.common.encryption.model.EncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for EncryptionKey entity management
 * Supports PCI DSS key rotation and management operations
 */
@Repository
public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, Long> {

    /**
     * Find encryption key by key ID
     * @param keyId The unique key identifier
     * @return Optional containing the encryption key if found
     */
    Optional<EncryptionKey> findByKeyId(String keyId);

    /**
     * Find all encryption keys by purpose
     * @param purpose The key purpose (e.g., "DATA_ENCRYPTION", "TOKEN_ENCRYPTION")
     * @return List of encryption keys matching the purpose
     */
    List<EncryptionKey> findByPurpose(String purpose);

    /**
     * Find all active encryption keys
     * @return List of active encryption keys
     */
    @Query("SELECT k FROM EncryptionKey k WHERE k.isActive = true AND k.status = 'ACTIVE'")
    List<EncryptionKey> findActiveKeys();

    /**
     * Count active encryption keys
     * @return Number of active keys
     */
    @Query("SELECT COUNT(k) FROM EncryptionKey k WHERE k.isActive = true AND k.status = 'ACTIVE'")
    long countActiveKeys();

    /**
     * Find encryption keys by status
     * @param status The key status
     * @return List of keys with the specified status
     */
    List<EncryptionKey> findByStatus(EncryptionKey.KeyStatus status);

    /**
     * Find encryption keys that are expired or close to expiration
     * @return List of keys requiring rotation
     */
    @Query("SELECT k FROM EncryptionKey k WHERE k.expiresAt < CURRENT_TIMESTAMP OR k.expiresAt < DATEADD('DAY', 7, CURRENT_TIMESTAMP)")
    List<EncryptionKey> findKeysRequiringRotation();
}
