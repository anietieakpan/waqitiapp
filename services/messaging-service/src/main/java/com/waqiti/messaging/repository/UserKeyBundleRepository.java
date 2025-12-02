package com.waqiti.messaging.repository;

import com.waqiti.messaging.domain.UserKeyBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserKeyBundleRepository extends JpaRepository<UserKeyBundle, String> {
    
    Optional<UserKeyBundle> findByUserId(String userId);
    
    Optional<UserKeyBundle> findByUserIdAndDeviceId(String userId, String deviceId);
    
    List<UserKeyBundle> findBySignedPreKeyCreatedAtBefore(LocalDateTime date);
    
    @Query("SELECT ukb FROM UserKeyBundle ukb WHERE ukb.lastActiveAt < :date")
    List<UserKeyBundle> findInactiveKeyBundles(LocalDateTime date);
    
    boolean existsByUserId(String userId);
}