package com.waqiti.user.repository;

import com.waqiti.user.model.DocumentVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DocumentVerificationRepository extends JpaRepository<DocumentVerification, String> {
    
    List<DocumentVerification> findByUserId(String userId);
    
    List<DocumentVerification> findRecentVerificationsByUserId(String userId, Instant since);
}