package com.waqiti.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for IDOR (Insecure Direct Object Reference) Protection
 *
 * CRITICAL SECURITY:
 * - Enables AspectJ proxy for @ValidateOwnership annotation
 * - Activates OwnershipValidationAspect for automatic ownership checks
 * - Required for OWASP Top 10 A01:2021 - Broken Access Control compliance
 *
 * USAGE:
 * <pre>
 * {@literal @}GetMapping("/wallets/{walletId}")
 * {@literal @}ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
 * public WalletResponse getWallet(@PathVariable UUID walletId) {
 *     // This method only executes if authenticated user owns the wallet
 * }
 * </pre>
 *
 * TECHNICAL DETAILS:
 * - Uses Spring AOP with @Around advice
 * - Executes BEFORE method invocation (fail-fast)
 * - Extracts user ID from JWT token in SecurityContext
 * - Performs database ownership lookup
 * - Logs all IDOR violation attempts
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class IdorProtectionConfig {
    // Configuration is declarative via annotations
    // OwnershipValidationAspect will be automatically discovered and registered
}
