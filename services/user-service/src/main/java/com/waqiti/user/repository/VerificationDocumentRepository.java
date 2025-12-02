package com.waqiti.user.repository;

import com.waqiti.user.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, String> {
    
    List<VerificationDocument> findByUserId(String userId);
    
    List<VerificationDocument> findByUserIdAndDocumentType(String userId, String documentType);
}