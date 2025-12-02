package com.waqiti.user.repository;

import com.waqiti.user.domain.AccountClosure;
import com.waqiti.user.domain.ClosureStatus;
import com.waqiti.user.domain.ClosureReason;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountClosureRepository extends MongoRepository<AccountClosure, String> {
    
    boolean existsByUserIdAndEventId(String userId, String eventId);
    
    Optional<AccountClosure> findByUserId(String userId);
    
    List<AccountClosure> findByStatus(ClosureStatus status);
    
    List<AccountClosure> findByReasonAndInitiatedAtBetween(ClosureReason reason, LocalDateTime start, LocalDateTime end);
    
    @Query("{'scheduledClosureDate': {'$lte': ?0}, 'status': {'$in': ['INITIATED', 'PROCESSING']}}")
    List<AccountClosure> findPendingClosures(LocalDateTime date);
    
    @Query("{'balanceResolutionRequired': true, 'status': 'PROCESSING'}")
    List<AccountClosure> findClosuresAwaitingBalanceResolution();
    
    long countByReasonAndInitiatedAtBetween(ClosureReason reason, LocalDateTime start, LocalDateTime end);
    
    @Query("{'dataArchived': false, 'completedAt': {'$lte': ?0}}")
    List<AccountClosure> findCompletedButNotArchived(LocalDateTime cutoff);
    
    @Query("{'dataRetentionUntil': {'$lte': ?0}}")
    List<AccountClosure> findEligibleForDeletion(LocalDateTime date);
}