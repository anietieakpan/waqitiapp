package com.waqiti.payment.repository;

import com.waqiti.payment.entity.SecretRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretRecordRepository extends JpaRepository<SecretRecord, UUID> {
    
    Optional<SecretRecord> findBySecretName(String secretName);
    
    List<SecretRecord> findByStatus(String status);
    
    @Query("SELECT s FROM SecretRecord s WHERE s.status = 'ACTIVE' AND s.rotationEnabled = true AND s.nextRotationAt <= :now")
    List<SecretRecord> findSecretsRequiringRotation(@Param("now") LocalDateTime now);
    
    @Modifying
    @Query("UPDATE SecretRecord s SET s.lastAccessedAt = :accessTime, s.accessCount = s.accessCount + 1 WHERE s.secretName = :secretName")
    void updateAccessMetrics(@Param("secretName") String secretName, @Param("accessTime") LocalDateTime accessTime);
    
    @Query("SELECT COUNT(s) FROM SecretRecord s WHERE s.status = 'ACTIVE'")
    long countActiveSecrets();
    
    List<SecretRecord> findBySecretType(String secretType);
}