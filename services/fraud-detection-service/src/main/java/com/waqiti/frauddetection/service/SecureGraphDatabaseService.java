package com.waqiti.frauddetection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY SERVICE: Secure Neo4j Graph Database Operations
 * Prevents Cypher injection attacks and ensures data integrity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureGraphDatabaseService {
    
    private final Driver neo4jDriver;
    
    // Validation patterns
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,50}$");
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,50}$");
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_\\.@]{0,255}$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    
    // Query templates with proper parameterization
    private static final Map<String, String> SECURE_QUERIES = new ConcurrentHashMap<>();
    
    static {
        // Initialize secure query templates
        SECURE_QUERIES.put("CREATE_USER", 
            "MERGE (u:User {userId: $userId}) " +
            "ON CREATE SET u.created = datetime(), u.riskScore = 0.0 " +
            "ON MATCH SET u.lastSeen = datetime() " +
            "RETURN u");
        
        SECURE_QUERIES.put("CREATE_TRANSACTION",
            "MATCH (sender:User {userId: $senderId}) " +
            "MATCH (receiver:User {userId: $receiverId}) " +
            "CREATE (sender)-[t:TRANSACTION {" +
            "  transactionId: $transactionId, " +
            "  amount: $amount, " +
            "  currency: $currency, " +
            "  timestamp: datetime($timestamp), " +
            "  riskScore: $riskScore, " +
            "  status: $status" +
            "}]->(receiver) " +
            "RETURN t");
        
        SECURE_QUERIES.put("FIND_FRAUD_RINGS",
            "MATCH (u:User {userId: $userId}) " +
            "MATCH path = (u)-[t:TRANSACTION*1..3]-(connected:User) " +
            "WHERE ALL(rel IN relationships(path) WHERE rel.riskScore > $threshold) " +
            "WITH connected, COUNT(DISTINCT path) as pathCount, AVG([rel IN relationships(path) | rel.riskScore]) as avgRisk " +
            "WHERE pathCount >= $minPaths " +
            "RETURN connected.userId as connectedUser, pathCount, avgRisk " +
            "ORDER BY avgRisk DESC " +
            "LIMIT $limit");
        
        SECURE_QUERIES.put("UPDATE_USER_RISK",
            "MATCH (u:User {userId: $userId}) " +
            "SET u.riskScore = $riskScore, " +
            "    u.lastUpdated = datetime(), " +
            "    u.flagged = CASE WHEN $riskScore > $flagThreshold THEN true ELSE false END " +
            "RETURN u");
        
        SECURE_QUERIES.put("DETECT_LAYERING",
            "MATCH (origin:User {userId: $userId}) " +
            "MATCH path = (origin)-[t1:TRANSACTION]->(middle:User)-[t2:TRANSACTION]->(dest:User) " +
            "WHERE t1.timestamp < t2.timestamp " +
            "  AND duration.between(t1.timestamp, t2.timestamp).seconds < $maxSeconds " +
            "  AND abs(t1.amount - t2.amount) / t1.amount < $tolerance " +
            "RETURN origin, middle, dest, t1, t2, " +
            "  (t1.riskScore + t2.riskScore) / 2 as layeringRisk " +
            "ORDER BY layeringRisk DESC " +
            "LIMIT $limit");
    }
    
    /**
     * CRITICAL SECURITY: Execute query with comprehensive input validation
     */
    public Result executeSecureQuery(String queryName, Map<String, Object> parameters) {
        // Validate query name
        String query = SECURE_QUERIES.get(queryName);
        if (query == null) {
            log.error("SECURITY: Attempted to execute unknown query: {}", queryName);
            throw new SecurityException("Invalid query name");
        }
        
        // Validate and sanitize all parameters
        Map<String, Object> sanitizedParams = sanitizeParameters(parameters);
        
        // Log query execution for audit
        String queryId = UUID.randomUUID().toString();
        log.info("SECURITY AUDIT: Executing query {} with ID {} and params: {}", 
            queryName, queryId, sanitizedParams.keySet());
        
        try (Session session = neo4jDriver.session()) {
            // Execute with parameterized query (prevents injection)
            return session.run(query, sanitizedParams);
        } catch (Exception e) {
            log.error("SECURITY: Query execution failed for {}: {}", queryName, e.getMessage());
            throw new RuntimeException("Query execution failed", e);
        }
    }
    
    /**
     * Sanitize and validate all parameters
     */
    private Map<String, Object> sanitizeParameters(Map<String, Object> params) {
        Map<String, Object> sanitized = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Validate parameter name
            if (!isValidParameterName(key)) {
                log.error("SECURITY: Invalid parameter name detected: {}", key);
                throw new SecurityException("Invalid parameter name: " + key);
            }
            
            // Sanitize parameter value based on type
            Object sanitizedValue = sanitizeValue(key, value);
            sanitized.put(key, sanitizedValue);
        }
        
        return sanitized;
    }
    
    /**
     * Validate parameter names to prevent injection
     */
    private boolean isValidParameterName(String name) {
        return name != null && 
               name.matches("^[a-zA-Z][a-zA-Z0-9_]{0,49}$") &&
               !containsSuspiciousKeywords(name);
    }
    
    /**
     * Check for suspicious keywords that might indicate injection attempts
     */
    private boolean containsSuspiciousKeywords(String input) {
        String lower = input.toLowerCase();
        return lower.contains("delete") ||
               lower.contains("drop") ||
               lower.contains("create") ||
               lower.contains("merge") ||
               lower.contains("match") ||
               lower.contains("return") ||
               lower.contains("set") ||
               lower.contains("remove") ||
               lower.contains("detach") ||
               lower.contains("call") ||
               lower.contains("load") ||
               lower.contains("csv") ||
               lower.contains("periodic") ||
               lower.contains("apoc") ||
               lower.contains("db.") ||
               lower.contains("dbms.");
    }
    
    /**
     * Sanitize values based on expected type
     */
    private Object sanitizeValue(String paramName, Object value) {
        if (value == null) {
            return null;
        }
        
        // User IDs
        if (paramName.toLowerCase().contains("userid") || 
            paramName.toLowerCase().contains("senderid") || 
            paramName.toLowerCase().contains("receiverid")) {
            return sanitizeUserId(value.toString());
        }
        
        // Transaction IDs
        if (paramName.toLowerCase().contains("transactionid")) {
            return sanitizeTransactionId(value.toString());
        }
        
        // Numeric values
        if (paramName.toLowerCase().contains("amount") || 
            paramName.toLowerCase().contains("score") ||
            paramName.toLowerCase().contains("threshold")) {
            return sanitizeNumeric(value);
        }
        
        // Timestamps
        if (paramName.toLowerCase().contains("timestamp") || 
            paramName.toLowerCase().contains("date")) {
            return sanitizeTimestamp(value);
        }
        
        // Integer limits
        if (paramName.equals("limit") || 
            paramName.equals("depth") || 
            paramName.equals("maxSeconds")) {
            return sanitizeInteger(value);
        }
        
        // Currency codes
        if (paramName.equals("currency")) {
            return sanitizeCurrency(value.toString());
        }
        
        // Status values
        if (paramName.equals("status")) {
            return sanitizeStatus(value.toString());
        }
        
        // Default string sanitization
        return sanitizeString(value.toString());
    }
    
    /**
     * Sanitize user ID to prevent injection
     */
    private String sanitizeUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        // Remove any whitespace
        userId = userId.trim();
        
        // Validate format
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            log.error("SECURITY: Invalid user ID format: {}", userId);
            throw new SecurityException("Invalid user ID format");
        }
        
        // Check for Cypher keywords
        if (containsCypherKeywords(userId)) {
            log.error("SECURITY: Cypher keywords detected in user ID: {}", userId);
            throw new SecurityException("Invalid user ID - contains restricted keywords");
        }
        
        return userId;
    }
    
    /**
     * Sanitize transaction ID
     */
    private String sanitizeTransactionId(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        
        transactionId = transactionId.trim();
        
        if (!TRANSACTION_ID_PATTERN.matcher(transactionId).matches()) {
            log.error("SECURITY: Invalid transaction ID format: {}", transactionId);
            throw new SecurityException("Invalid transaction ID format");
        }
        
        return transactionId;
    }
    
    /**
     * Sanitize numeric values
     */
    private Double sanitizeNumeric(Object value) {
        String strValue = value.toString();
        
        if (!NUMERIC_PATTERN.matcher(strValue).matches()) {
            log.error("SECURITY: Invalid numeric value: {}", strValue);
            throw new SecurityException("Invalid numeric value");
        }
        
        double numValue = Double.parseDouble(strValue);
        
        // Range validation
        if (Double.isNaN(numValue) || Double.isInfinite(numValue)) {
            throw new SecurityException("Invalid numeric value - NaN or Infinite");
        }
        
        // Prevent extremely large values
        if (Math.abs(numValue) > 1_000_000_000) {
            throw new SecurityException("Numeric value exceeds maximum allowed");
        }
        
        return numValue;
    }
    
    /**
     * Sanitize integer values
     */
    private Integer sanitizeInteger(Object value) {
        Double numValue = sanitizeNumeric(value);
        
        // Additional validation for integers
        if (numValue < 0) {
            throw new SecurityException("Negative values not allowed for this parameter");
        }
        
        if (numValue > 1000) {
            log.warn("SECURITY: Large limit value capped at 1000: {}", numValue);
            return 1000; // Cap at reasonable limit
        }
        
        return numValue.intValue();
    }
    
    /**
     * Sanitize timestamp values
     */
    private String sanitizeTimestamp(Object value) {
        String timestamp = value.toString();
        
        // Validate ISO 8601 format
        if (!timestamp.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$")) {
            throw new SecurityException("Invalid timestamp format");
        }
        
        return timestamp;
    }
    
    /**
     * Sanitize currency codes
     */
    private String sanitizeCurrency(String currency) {
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new SecurityException("Invalid currency code");
        }
        return currency;
    }
    
    /**
     * Sanitize status values
     */
    private String sanitizeStatus(String status) {
        // Whitelist of allowed statuses
        if (!status.matches("^(PENDING|COMPLETED|FAILED|CANCELLED)$")) {
            throw new SecurityException("Invalid status value");
        }
        return status;
    }
    
    /**
     * Generic string sanitization
     */
    private String sanitizeString(String value) {
        if (value == null) {
            return null;
        }
        
        // Remove any control characters
        value = value.replaceAll("[\\p{Cntrl}]", "");
        
        // Validate against safe pattern
        if (!SAFE_STRING_PATTERN.matcher(value).matches()) {
            log.error("SECURITY: Unsafe string value detected");
            throw new SecurityException("Invalid string value");
        }
        
        // Check for Cypher keywords
        if (containsCypherKeywords(value)) {
            log.error("SECURITY: Cypher keywords detected in string value");
            throw new SecurityException("String contains restricted keywords");
        }
        
        return value;
    }
    
    /**
     * Check for Cypher keywords in input
     */
    private boolean containsCypherKeywords(String input) {
        String lower = input.toLowerCase();
        String[] keywords = {
            "match", "create", "merge", "delete", "remove", "set",
            "return", "with", "where", "order by", "skip", "limit",
            "union", "call", "yield", "detach", "foreach", "load",
            "using", "start", "relate", "optional", "case", "when",
            "db.", "dbms.", "apoc.", "algo.", "gds.", "system."
        };
        
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create a secure transaction with validation
     */
    public void createSecureTransaction(String senderId, String receiverId, String transactionId, 
                                       double amount, String currency, double riskScore) {
        Map<String, Object> params = Map.of(
            "senderId", senderId,
            "receiverId", receiverId,
            "transactionId", transactionId,
            "amount", amount,
            "currency", currency,
            "timestamp", java.time.Instant.now().toString(),
            "riskScore", riskScore,
            "status", "PENDING"
        );
        
        executeSecureQuery("CREATE_TRANSACTION", params);
    }
    
    /**
     * Detect fraud rings with secure parameters
     */
    public Result detectFraudRings(String userId, double threshold, int minPaths, int limit) {
        Map<String, Object> params = Map.of(
            "userId", userId,
            "threshold", threshold,
            "minPaths", minPaths,
            "limit", Math.min(limit, 100) // Cap limit
        );
        
        return executeSecureQuery("FIND_FRAUD_RINGS", params);
    }
    
    /**
     * Update user risk score securely
     */
    public void updateUserRisk(String userId, double riskScore, double flagThreshold) {
        Map<String, Object> params = Map.of(
            "userId", userId,
            "riskScore", Math.min(1.0, Math.max(0.0, riskScore)), // Clamp between 0 and 1
            "flagThreshold", flagThreshold
        );
        
        executeSecureQuery("UPDATE_USER_RISK", params);
    }
    
    /**
     * Detect money layering patterns securely
     */
    public Result detectLayering(String userId, int maxSeconds, double tolerance, int limit) {
        Map<String, Object> params = Map.of(
            "userId", userId,
            "maxSeconds", Math.min(maxSeconds, 86400), // Cap at 24 hours
            "tolerance", Math.min(tolerance, 1.0), // Cap at 100%
            "limit", Math.min(limit, 50) // Cap result size
        );
        
        return executeSecureQuery("DETECT_LAYERING", params);
    }
}