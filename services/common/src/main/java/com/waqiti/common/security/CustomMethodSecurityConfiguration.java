package com.waqiti.common.security;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import org.springframework.security.core.Authentication;

/**
 * Configuration for custom method security expressions.
 * Enables the use of custom security expressions in @PreAuthorize annotations.
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class CustomMethodSecurityConfiguration extends GlobalMethodSecurityConfiguration {
    
    private final PermissionMatrix permissionMatrix;
    
    public CustomMethodSecurityConfiguration(PermissionMatrix permissionMatrix) {
        this.permissionMatrix = permissionMatrix;
    }
    
    @Override
    protected MethodSecurityExpressionHandler createExpressionHandler() {
        return new CustomMethodSecurityExpressionHandler();
    }
    
    /**
     * Custom expression handler that creates our custom expression root
     */
    public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
        
        @Override
        protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
                Authentication authentication, MethodInvocation invocation) {
            
            CustomMethodSecurityExpressionRoot root = 
                new CustomMethodSecurityExpressionRoot(authentication, permissionMatrix);
            
            root.setPermissionEvaluator(getPermissionEvaluator());
            root.setTrustResolver(getTrustResolver());
            root.setRoleHierarchy(getRoleHierarchy());
            
            return root;
        }
    }
    
    /**
     * Bean to make PermissionMatrix available for injection
     */
    @Bean
    public PermissionMatrix permissionMatrix() {
        return new PermissionMatrix();
    }
}