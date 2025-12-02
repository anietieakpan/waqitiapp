package com.waqiti.user.repository;

import com.waqiti.user.model.UserCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserComplianceRepository extends JpaRepository<UserCompliance, String> {
    
    Optional<UserCompliance> findByUserId(String userId);
    
    boolean existsByUserId(String userId);
}