package com.waqiti.common.security.awareness.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public class SecurityContextUtil {

    public static void validateEmployeeAccess(UUID employeeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Check if user is accessing their own data or is an admin
        String currentUserId = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_SECURITY_ADMIN"));

        if (!isAdmin && !currentUserId.equals(employeeId.toString())) {
            throw new SecurityException("Access denied: Cannot access other employee's data");
        }
    }
}