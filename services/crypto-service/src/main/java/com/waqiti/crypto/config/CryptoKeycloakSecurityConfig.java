package com.waqiti.crypto.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Crypto Service
 * Manages authentication and authorization for cryptocurrency operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class CryptoKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain cryptoKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Crypto Service");
        
        return createKeycloakSecurityFilterChain(http, "crypto-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/crypto/public/**").permitAll()
                .requestMatchers("/api/v1/crypto/rates/**").permitAll() // Public price rates
                
                // Wallet Management
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/wallets/create").hasAuthority("SCOPE_crypto:wallet-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/wallets").hasAuthority("SCOPE_crypto:wallet-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/wallets/*").hasAuthority("SCOPE_crypto:wallet-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/wallets/*/balance").hasAuthority("SCOPE_crypto:balance-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/wallets/*/activate").hasAuthority("SCOPE_crypto:wallet-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/wallets/*/deactivate").hasAuthority("SCOPE_crypto:wallet-deactivate")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/crypto/wallets/*").hasAuthority("SCOPE_crypto:wallet-delete")
                
                // Address Management
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/addresses/generate").hasAuthority("SCOPE_crypto:address-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/addresses").hasAuthority("SCOPE_crypto:address-list")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/addresses/validate").hasAuthority("SCOPE_crypto:address-validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/addresses/*/transactions").hasAuthority("SCOPE_crypto:address-transactions")
                
                // Transaction Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/transactions/send").hasAuthority("SCOPE_crypto:transaction-send")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/transactions/estimate-fee").hasAuthority("SCOPE_crypto:fee-estimate")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/transactions/*").hasAuthority("SCOPE_crypto:transaction-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/transactions").hasAuthority("SCOPE_crypto:transaction-list")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/transactions/*/accelerate").hasAuthority("SCOPE_crypto:transaction-accelerate")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/transactions/*/cancel").hasAuthority("SCOPE_crypto:transaction-cancel")
                
                // Exchange Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/exchange/quote").hasAuthority("SCOPE_crypto:exchange-quote")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/exchange/swap").hasAuthority("SCOPE_crypto:exchange-swap")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/exchange/history").hasAuthority("SCOPE_crypto:exchange-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/exchange/limits").hasAuthority("SCOPE_crypto:exchange-limits")
                
                // Buy/Sell Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/buy").hasAuthority("SCOPE_crypto:buy")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/sell").hasAuthority("SCOPE_crypto:sell")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/orders").hasAuthority("SCOPE_crypto:orders-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/orders/*").hasAuthority("SCOPE_crypto:order-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/orders/*/cancel").hasAuthority("SCOPE_crypto:order-cancel")
                
                // Staking Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/staking/stake").hasAuthority("SCOPE_crypto:stake")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/staking/unstake").hasAuthority("SCOPE_crypto:unstake")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/staking/positions").hasAuthority("SCOPE_crypto:staking-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/staking/rewards").hasAuthority("SCOPE_crypto:rewards-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/staking/claim-rewards").hasAuthority("SCOPE_crypto:rewards-claim")
                
                // Mining Pool Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/mining/join").hasAuthority("SCOPE_crypto:mining-join")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/mining/stats").hasAuthority("SCOPE_crypto:mining-stats")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/mining/payouts").hasAuthority("SCOPE_crypto:mining-payouts")
                
                // DeFi Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/defi/lend").hasAuthority("SCOPE_crypto:defi-lend")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/defi/borrow").hasAuthority("SCOPE_crypto:defi-borrow")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/defi/repay").hasAuthority("SCOPE_crypto:defi-repay")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/defi/positions").hasAuthority("SCOPE_crypto:defi-positions")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/defi/yields").hasAuthority("SCOPE_crypto:defi-yields")
                
                // Portfolio Management
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/portfolio").hasAuthority("SCOPE_crypto:portfolio-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/portfolio/performance").hasAuthority("SCOPE_crypto:portfolio-performance")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/portfolio/allocation").hasAuthority("SCOPE_crypto:portfolio-allocation")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/portfolio/rebalance").hasAuthority("SCOPE_crypto:portfolio-rebalance")
                
                // Security Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/security/whitelist/add").hasAuthority("SCOPE_crypto:whitelist-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/crypto/security/whitelist/remove").hasAuthority("SCOPE_crypto:whitelist-remove")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/security/whitelist").hasAuthority("SCOPE_crypto:whitelist-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/security/2fa/enable").hasAuthority("SCOPE_crypto:2fa-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/security/limits/set").hasAuthority("SCOPE_crypto:limits-set")
                
                // Backup and Recovery
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/backup/create").hasAuthority("SCOPE_crypto:backup-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/backup/download").hasAuthority("SCOPE_crypto:backup-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/recovery/initiate").hasAuthority("SCOPE_crypto:recovery-initiate")
                
                // Compliance and Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/compliance/aml-check").hasAuthority("SCOPE_crypto:aml-check")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/reports/tax").hasAuthority("SCOPE_crypto:tax-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/reports/transactions").hasAuthority("SCOPE_crypto:transaction-report")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/compliance/freeze").hasAuthority("SCOPE_crypto:account-freeze")
                
                // Admin Operations
                .requestMatchers("/api/v1/crypto/admin/**").hasRole("CRYPTO_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/admin/hot-wallet/refill").hasRole("CRYPTO_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/admin/cold-storage/transfer").hasRole("CRYPTO_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/crypto/admin/master-wallet").hasRole("CRYPTO_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/crypto/admin/emergency-shutdown").hasRole("CRYPTO_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/crypto/**").hasRole("SERVICE")
                .requestMatchers("/internal/blockchain/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}