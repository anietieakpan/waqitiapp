package com.waqiti.payment.repository;

import com.waqiti.payment.domain.AccountControl;
import com.waqiti.payment.domain.AccountControlAction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AccountControl entities
 */
@Repository
public interface AccountControlRepository extends MongoRepository<AccountControl, String> {
    
    List<AccountControl> findByUserId(String userId);
    
    List<AccountControl> findByAccountId(String accountId);
    
    List<AccountControl> findByUserIdAndIsActive(String userId, boolean isActive);
    
    Optional<AccountControl> findByReferenceNumber(String referenceNumber);
    
    List<AccountControl> findByAction(AccountControlAction action);
    
    @Query("{'userId': ?0, 'action': ?1, 'isActive': true}")
    List<AccountControl> findActiveControlsByUserIdAndAction(String userId, AccountControlAction action);
    
    @Query("{'expiresAt': {'$lte': ?0}, 'isActive': true}")
    List<AccountControl> findExpiredActiveControls(LocalDateTime now);
    
    @Query("{'reason': ?0, 'isActive': true}")
    List<AccountControl> findActiveControlsByReason(String reason);
    
    @Query("{'severity': ?0, 'isActive': true}")
    List<AccountControl> findActiveControlsBySeverity(String severity);
    
    boolean existsByUserIdAndReferenceNumber(String userId, String referenceNumber);
    
    long countByUserIdAndIsActive(String userId, boolean isActive);
    
    @Query("{'correlationId': ?0}")
    Optional<AccountControl> findByCorrelationId(String correlationId);
}