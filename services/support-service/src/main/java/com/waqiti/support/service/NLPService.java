package com.waqiti.support.service;

import com.waqiti.support.dto.NLPResult;

import java.util.List;
import java.util.Map;

public interface NLPService {
    
    /**
     * Analyze text to extract intent, entities, and generate embeddings
     */
    NLPResult analyze(String text);
    
    /**
     * Extract named entities from text
     */
    Map<String, String> extractEntities(String text);
    
    /**
     * Classify the intent of the text
     */
    String classifyIntent(String text);
    
    /**
     * Calculate confidence score for intent classification
     */
    double calculateConfidence(String text, String intent);
    
    /**
     * Generate text embedding vector
     */
    float[] generateEmbedding(String text);
    
    /**
     * Extract keywords and tags from text
     */
    List<String> extractTags(String text);
    
    /**
     * Detect sentiment of the text
     */
    String detectSentiment(String text);
    
    /**
     * Calculate similarity between two texts
     */
    double calculateSimilarity(String text1, String text2);
    
    /**
     * Preprocess text for analysis
     */
    String preprocessText(String text);
}