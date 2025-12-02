package com.waqiti.user.repository;

import com.waqiti.user.model.VerificationStatusRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationStatusRepository extends JpaRepository<VerificationStatusRecord, String> {
    
    Optional<VerificationStatusRecord> findByUserId(String userId);
    
    List<VerificationStatusRecord> findByStatus(String status);
    
    List<VerificationStatusRecord> findByUserIdAndVerificationType(String userId, String verificationType);
}