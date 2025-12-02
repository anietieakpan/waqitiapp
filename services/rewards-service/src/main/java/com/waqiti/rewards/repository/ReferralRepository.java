package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.Referral;
import com.waqiti.rewards.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, String> {
    
    List<Referral> findByReferrerId(String referrerId);
    
    Optional<Referral> findByRefereeId(String refereeId);
    
    List<Referral> findByReferrerIdAndStatus(String referrerId, ReferralStatus status);
    
    boolean existsByRefereeId(String refereeId);
    
    @Query("SELECT COUNT(r) FROM Referral r WHERE r.referrerId = ?1 AND r.status = ?2")
    long countByReferrerIdAndStatus(String referrerId, ReferralStatus status);
}
