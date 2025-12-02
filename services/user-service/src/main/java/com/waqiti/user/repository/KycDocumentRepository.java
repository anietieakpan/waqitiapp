package com.waqiti.user.repository;

import com.waqiti.user.model.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, String> {
    
    List<KycDocument> findByUserId(String userId);
    
    Optional<KycDocument> findByUserIdAndDocumentType(String userId, String documentType);
    
    List<KycDocument> findByUserIdAndVerificationStatus(String userId, String verificationStatus);
}