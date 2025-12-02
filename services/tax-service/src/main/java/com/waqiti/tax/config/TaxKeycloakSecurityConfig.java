package com.waqiti.tax.config;

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
 * Keycloak security configuration for Tax Service
 * Manages authentication and authorization for tax filing and reporting operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class TaxKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain taxKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Tax Service");
        
        return createKeycloakSecurityFilterChain(http, "tax-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/tax/public/**").permitAll()
                .requestMatchers("/api/v1/tax/calculators/**").permitAll() // Public tax calculators
                .requestMatchers("/api/v1/tax/rates/**").permitAll() // Public tax rates
                
                // Tax Profile Management
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/profile/create").hasAuthority("SCOPE_tax:profile-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/profile").hasAuthority("SCOPE_tax:profile-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tax/profile").hasAuthority("SCOPE_tax:profile-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tax/profile").hasAuthority("SCOPE_tax:profile-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/profile/status").hasAuthority("SCOPE_tax:profile-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/profile/verify").hasAuthority("SCOPE_tax:profile-verify")
                
                // Tax Return Filing
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/returns/start").hasAuthority("SCOPE_tax:return-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/returns").hasAuthority("SCOPE_tax:returns-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/returns/*").hasAuthority("SCOPE_tax:return-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tax/returns/*").hasAuthority("SCOPE_tax:return-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tax/returns/*").hasAuthority("SCOPE_tax:return-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/returns/*/submit").hasAuthority("SCOPE_tax:return-submit")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/returns/*/amend").hasAuthority("SCOPE_tax:return-amend")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/returns/*/status").hasAuthority("SCOPE_tax:return-status")
                
                // Income Management
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/income/add").hasAuthority("SCOPE_tax:income-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/income").hasAuthority("SCOPE_tax:income-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tax/income/*").hasAuthority("SCOPE_tax:income-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tax/income/*").hasAuthority("SCOPE_tax:income-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/income/import").hasAuthority("SCOPE_tax:income-import")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/income/summary").hasAuthority("SCOPE_tax:income-summary")
                
                // Deductions & Credits
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/deductions/add").hasAuthority("SCOPE_tax:deduction-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/deductions").hasAuthority("SCOPE_tax:deductions-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tax/deductions/*").hasAuthority("SCOPE_tax:deduction-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tax/deductions/*").hasAuthority("SCOPE_tax:deduction-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/deductions/eligible").hasAuthority("SCOPE_tax:deductions-eligible")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/deductions/optimize").hasAuthority("SCOPE_tax:deductions-optimize")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/credits").hasAuthority("SCOPE_tax:credits-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/credits/claim").hasAuthority("SCOPE_tax:credits-claim")
                
                // Tax Documents
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/documents/upload").hasAuthority("SCOPE_tax:document-upload")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/documents").hasAuthority("SCOPE_tax:documents-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/documents/*").hasAuthority("SCOPE_tax:document-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/tax/documents/*").hasAuthority("SCOPE_tax:document-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/documents/forms/w2").hasAuthority("SCOPE_tax:w2-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/documents/forms/1099").hasAuthority("SCOPE_tax:1099-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/documents/forms/1098").hasAuthority("SCOPE_tax:1098-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/documents/ocr").hasAuthority("SCOPE_tax:document-ocr")
                
                // Tax Calculations
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/calculate/estimate").hasAuthority("SCOPE_tax:calculate-estimate")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/calculate/refund").hasAuthority("SCOPE_tax:calculate-refund")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/calculate/liability").hasAuthority("SCOPE_tax:calculate-liability")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/calculate/withholding").hasAuthority("SCOPE_tax:calculate-withholding")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/calculate/effective-rate").hasAuthority("SCOPE_tax:calculate-rate")
                
                // Crypto Tax
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/crypto/import").hasAuthority("SCOPE_tax:crypto-import")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/crypto/transactions").hasAuthority("SCOPE_tax:crypto-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/crypto/calculate").hasAuthority("SCOPE_tax:crypto-calculate")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/crypto/forms/8949").hasAuthority("SCOPE_tax:crypto-8949")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/crypto/cost-basis").hasAuthority("SCOPE_tax:crypto-cost-basis")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/crypto/harvest-losses").hasAuthority("SCOPE_tax:crypto-harvest")
                
                // Investment Tax
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/investments/summary").hasAuthority("SCOPE_tax:investment-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/investments/dividends").hasAuthority("SCOPE_tax:investment-dividends")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/investments/capital-gains").hasAuthority("SCOPE_tax:investment-gains")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/investments/import").hasAuthority("SCOPE_tax:investment-import")
                
                // State & Local Tax
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/state").hasAuthority("SCOPE_tax:state-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/state/file").hasAuthority("SCOPE_tax:state-file")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/state/*/requirements").hasAuthority("SCOPE_tax:state-requirements")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/local").hasAuthority("SCOPE_tax:local-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/local/file").hasAuthority("SCOPE_tax:local-file")
                
                // Quarterly Taxes
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/quarterly/estimates").hasAuthority("SCOPE_tax:quarterly-estimates")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/quarterly/calculate").hasAuthority("SCOPE_tax:quarterly-calculate")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/quarterly/pay").hasAuthority("SCOPE_tax:quarterly-pay")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/quarterly/schedule").hasAuthority("SCOPE_tax:quarterly-schedule")
                
                // Tax Planning
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/planning/recommendations").hasAuthority("SCOPE_tax:planning-recommendations")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/planning/optimize").hasAuthority("SCOPE_tax:planning-optimize")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/planning/scenarios").hasAuthority("SCOPE_tax:planning-scenarios")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/planning/simulate").hasAuthority("SCOPE_tax:planning-simulate")
                
                // IRS Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/irs/transcript").hasAuthority("SCOPE_tax:irs-transcript")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/irs/refund-status").hasAuthority("SCOPE_tax:irs-refund-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/irs/payment").hasAuthority("SCOPE_tax:irs-payment")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/irs/notices").hasAuthority("SCOPE_tax:irs-notices")
                
                // Audit Support
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/audit/risk").hasAuthority("SCOPE_tax:audit-risk")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/audit/documentation").hasAuthority("SCOPE_tax:audit-documentation")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/audit/prepare").hasAuthority("SCOPE_tax:audit-prepare")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/audit/checklist").hasAuthority("SCOPE_tax:audit-checklist")
                
                // Reports & Analytics
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/reports/summary").hasAuthority("SCOPE_tax:reports-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/reports/history").hasAuthority("SCOPE_tax:reports-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/reports/projections").hasAuthority("SCOPE_tax:reports-projections")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/reports/generate").hasAuthority("SCOPE_tax:reports-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/analytics").hasAuthority("SCOPE_tax:analytics-view")
                
                // Professional Services
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/professional/connect").hasAuthority("SCOPE_tax:professional-connect")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/professional/status").hasAuthority("SCOPE_tax:professional-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/professional/review").hasAuthority("SCOPE_tax:professional-review")
                
                // Admin Operations
                .requestMatchers("/api/v1/tax/admin/**").hasRole("TAX_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tax/admin/rates").hasRole("TAX_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/admin/forms/update").hasRole("TAX_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/admin/users/*/returns").hasRole("TAX_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/admin/bulk-process").hasRole("TAX_ADMIN")
                
                // Tax Professional Role
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/clients").hasRole("TAX_PROFESSIONAL")
                .requestMatchers(HttpMethod.GET, "/api/v1/tax/clients/*/returns").hasRole("TAX_PROFESSIONAL")
                .requestMatchers(HttpMethod.POST, "/api/v1/tax/clients/*/review").hasRole("TAX_PROFESSIONAL")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/tax/**").hasRole("SERVICE")
                .requestMatchers("/internal/calculations/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}