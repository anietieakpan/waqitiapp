package com.waqiti.user.repository;

import com.waqiti.user.model.AccountVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountVerificationRepository extends JpaRepository<AccountVerification, String> {
    
    List<AccountVerification> findByUserIdOrderByCreatedAtDesc(String userId);
    
    List<AccountVerification> findByUserId(String userId);
    
    List<AccountVerification> findByUserIdAndVerificationType(String userId, String verificationType);
}