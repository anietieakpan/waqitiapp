package com.waqiti.messaging.repository;

import com.waqiti.messaging.domain.PreKey;
import com.waqiti.messaging.domain.UserKeyBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PreKeyRepository extends JpaRepository<PreKey, String> {
    
    Optional<PreKey> findByKeyBundleAndKeyId(UserKeyBundle keyBundle, Integer keyId);
    
    List<PreKey> findByKeyBundleAndUsedFalse(UserKeyBundle keyBundle);
    
    @Query("SELECT MAX(pk.keyId) FROM PreKey pk WHERE pk.keyBundle = :keyBundle")
    Optional<Integer> findMaxKeyIdByKeyBundle(UserKeyBundle keyBundle);
    
    @Modifying
    @Query("DELETE FROM PreKey pk WHERE pk.used = true AND pk.usedAt < :cutoff")
    int deleteByUsedTrueAndUsedAtBefore(LocalDateTime cutoff);
    
    long countByKeyBundleAndUsedFalse(UserKeyBundle keyBundle);
}