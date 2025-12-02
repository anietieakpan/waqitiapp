package com.waqiti.apigateway.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Map;

/**
 * Custom user principal for Waqiti application.
 * Represents authenticated user information from both Keycloak and legacy JWT tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaqitiUserPrincipal implements UserDetails {
    
    private String userId;
    private String username;
    private String email;
    private boolean emailVerified;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;
    private boolean enabled;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        // Password is not stored in the principal
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired || true; // Default to true if not set
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked || true; // Default to true if not set
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired || true; // Default to true if not set
    }

    @Override
    public boolean isEnabled() {
        return enabled || true; // Default to true if not set
    }
    
    /**
     * Get the full name of the user
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return username;
    }
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (authorities == null) {
            return false;
        }
        String rolePrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equalsIgnoreCase(rolePrefix));
    }
    
    /**
     * Get attribute value by key
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        if (attributes == null || !attributes.containsKey(key)) {
            return null;
        }
        return (T) attributes.get(key);
    }
}