package com.waqiti.support.exception;

public class KnowledgeBaseException extends RuntimeException {
    
    public KnowledgeBaseException(String message) {
        super(message);
    }
    
    public KnowledgeBaseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static KnowledgeBaseException articleNotFound(Long articleId) {
        return new KnowledgeBaseException("Knowledge article not found: " + articleId);
    }
    
    public static KnowledgeBaseException faqNotFound(Long faqId) {
        return new KnowledgeBaseException("FAQ not found: " + faqId);
    }
    
    public static KnowledgeBaseException searchFailed(String query) {
        return new KnowledgeBaseException("Knowledge base search failed for query: " + query);
    }
}