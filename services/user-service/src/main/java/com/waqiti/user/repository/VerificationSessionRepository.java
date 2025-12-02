package com.waqiti.user.repository;

import com.waqiti.user.model.VerificationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationSessionRepository extends JpaRepository<VerificationSession, String> {
    
    Optional<VerificationSession> findBySessionId(String sessionId);
    
    List<VerificationSession> findByUserId(String userId);
    
    List<VerificationSession> findByUserIdAndActive(String userId, boolean active);
}