package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, String> {
    
    Optional<ReferralCode> findByCode(String code);
    
    Optional<ReferralCode> findByCodeAndActiveTrue(String code);
    
    List<ReferralCode> findByUserId(String userId);
    
    @Query("SELECT rc FROM ReferralCode rc WHERE rc.userId = ?1 AND rc.active = true")
    List<ReferralCode> findActiveCodesByUserId(String userId);
    
    boolean existsByCode(String code);
}
