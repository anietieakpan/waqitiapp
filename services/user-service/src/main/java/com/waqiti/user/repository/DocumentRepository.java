package com.waqiti.user.repository;

import com.waqiti.user.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    
    List<Document> findByUserId(String userId);
    
    List<Document> findByUserIdAndType(String userId, String documentType);
    
    Optional<Document> findByUserIdAndDocumentType(String userId, String documentType);
}