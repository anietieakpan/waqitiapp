package com.waqiti.common.domain.valueobjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UserId Value Object - Immutable representation of user identifiers
 * Encapsulates user ID validation and formatting rules
 */
@EqualsAndHashCode
public class UserId {
    
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^user_[a-zA-Z0-9]{16}$");
    private static final String PREFIX = "user_";
    
    private final String value;
    
    @JsonCreator
    public UserId(String userId) {
        this.value = validateAndNormalize(userId);
    }
    
    public static UserId of(String userId) {
        return new UserId(userId);
    }
    
    public static UserId generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return new UserId(PREFIX + uuid.substring(0, 16));
    }
    
    public static UserId fromUUID(UUID uuid) {
        String uuidString = uuid.toString().replace("-", "");
        return new UserId(PREFIX + uuidString.substring(0, 16));
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    public String getShortId() {
        return value.substring(PREFIX.length());
    }
    
    public boolean isValid() {
        return USER_ID_PATTERN.matcher(value).matches();
    }
    
    public UserId maskForLogging() {
        if (value.length() <= PREFIX.length() + 4) {
            return new UserId(PREFIX + "****");
        }
        
        String shortId = getShortId();
        String masked = shortId.substring(0, 2) + "*".repeat(shortId.length() - 4) + shortId.substring(shortId.length() - 2);
        return new UserId(PREFIX + masked);
    }
    
    private String validateAndNormalize(String userId) {
        Objects.requireNonNull(userId, "UserId cannot be null");
        
        String trimmed = userId.trim();
        
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("UserId cannot be empty");
        }
        
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("UserId must start with 'user_' prefix");
        }
        
        if (!USER_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid UserId format. Expected: user_[16 alphanumeric chars]");
        }
        
        return trimmed;
    }
    
    @Override
    public String toString() {
        return value;
    }
}