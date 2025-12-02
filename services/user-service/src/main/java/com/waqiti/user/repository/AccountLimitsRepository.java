package com.waqiti.user.repository;

import com.waqiti.user.model.AccountLimits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountLimitsRepository extends JpaRepository<AccountLimits, String> {
    
    Optional<AccountLimits> findByUserId(String userId);
    
    void deleteByUserId(String userId);
}