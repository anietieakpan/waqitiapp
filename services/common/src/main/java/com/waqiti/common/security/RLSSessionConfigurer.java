package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * CRITICAL SECURITY COMPONENT: PostgreSQL Session Parameter Configuration for RLS
 *
 * PURPOSE:
 * Configures PostgreSQL session parameters (current_setting) that RLS policies depend on.
 * Ensures database-level security policies have correct user context.
 *
 * SECURITY ARCHITECTURE:
 * 1. Application sets RLSContext (via RLSContextValidator)
 * 2. RLSSessionConfigurer translates context to PostgreSQL session parameters
 * 3. RLS policies in database use current_setting('app.current_user_id')
 * 4. Database enforces row-level security based on session parameters
 *
 * CRITICAL PATH:
 * This component MUST be called before EVERY database transaction that accesses
 * RLS-protected tables (wallets, transactions, payment_methods, etc.)
 *
 * @author Waqiti Security Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RLSSessionConfigurer {

    private final RLSContextValidator contextValidator;
    private final DataSource dataSource;

    /**
     * Configures PostgreSQL session parameters for RLS based on current context.
     *
     * SECURITY CRITICAL:
     * - Validates RLS context is set
     * - Sets PostgreSQL session variables for RLS policies
     * - Logs configuration for audit trail
     * - Handles emergency access mode
     *
     * PostgreSQL session parameters set:
     * - app.current_user_id: UUID of authenticated user
     * - app.user_role: Role for RBAC policies
     * - app.session_id: Session identifier for audit
     * - app.emergency_access: Emergency override flag
     *
     * @param connection JDBC connection to configure
     * @throws SQLException if session configuration fails
     * @throws RLSContextValidator.RLSContextValidationException if context invalid
     */
    public void configureSessionForRLS(Connection connection) throws SQLException {
        // CRITICAL: Validate context before proceeding
        contextValidator.validateContext();

        RLSContextValidator.RLSContext context = contextValidator.getCurrentContext();

        try {
            // Set user ID for RLS user-scoped policies
            setSessionParameter(connection, "app.current_user_id", context.getUserId().toString());

            // Set user role for RLS role-based policies
            setSessionParameter(connection, "app.user_role", context.getUserRole());

            // Set session ID for audit trail correlation
            if (context.getSessionId() != null) {
                setSessionParameter(connection, "app.session_id", context.getSessionId());
            }

            // Set emergency access flag (for emergency override policies)
            setSessionParameter(connection, "app.emergency_access",
                String.valueOf(context.isEmergencyAccess()));

            if (context.isEmergencyAccess()) {
                // Additional emergency access metadata for audit
                setSessionParameter(connection, "app.incident_ticket",
                    context.getIncidentTicket());
                setSessionParameter(connection, "app.approver_chain",
                    context.getApproverChain());

                log.warn("RLS session configured for EMERGENCY ACCESS. User: {}, Ticket: {}",
                    context.getUserId(), context.getIncidentTicket());
            } else {
                log.debug("RLS session configured for user: {}, role: {}",
                    context.getUserId(), context.getUserRole());
            }

        } catch (SQLException e) {
            log.error("CRITICAL: Failed to configure RLS session parameters. User: {}, Error: {}",
                context.getUserId(), e.getMessage(), e);
            throw new SQLException(
                "Failed to configure Row Level Security session parameters. " +
                "Database security cannot be enforced.", e
            );
        }
    }

    /**
     * Sets a PostgreSQL session parameter using SET LOCAL.
     *
     * SET LOCAL ensures the parameter is transaction-scoped and automatically
     * cleared at transaction end, preventing parameter leakage across requests.
     *
     * @param connection JDBC connection
     * @param paramName parameter name (e.g., "app.current_user_id")
     * @param paramValue parameter value
     * @throws SQLException if parameter set fails
     */
    private void setSessionParameter(Connection connection, String paramName, String paramValue)
            throws SQLException {

        // Use SET LOCAL for transaction-scoped parameters
        // This ensures parameters don't leak across connections in a pool
        String sql = "SET LOCAL " + paramName + " = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, paramValue);
            stmt.execute();

            log.trace("Set PostgreSQL session parameter: {} = {}", paramName,
                // Mask user ID in logs for privacy
                paramName.contains("user_id") ? "***masked***" : paramValue
            );
        }
    }

    /**
     * Validates that RLS session parameters are correctly set.
     *
     * USE CASE:
     * Call after configureSessionForRLS() to verify configuration succeeded.
     * Useful in high-security scenarios or during debugging.
     *
     * @param connection JDBC connection to validate
     * @return true if all required parameters are set correctly
     */
    public boolean validateSessionConfiguration(Connection connection) {
        try {
            RLSContextValidator.RLSContext context = contextValidator.getCurrentContext();

            // Verify user ID parameter matches context
            String dbUserId = getSessionParameter(connection, "app.current_user_id");
            if (!context.getUserId().toString().equals(dbUserId)) {
                log.error("RLS session parameter mismatch. Expected user ID: {}, Database has: {}",
                    context.getUserId(), dbUserId);
                return false;
            }

            // Verify user role parameter matches context
            String dbRole = getSessionParameter(connection, "app.user_role");
            if (!context.getUserRole().equals(dbRole)) {
                log.error("RLS session parameter mismatch. Expected role: {}, Database has: {}",
                    context.getUserRole(), dbRole);
                return false;
            }

            log.debug("RLS session configuration validated successfully");
            return true;

        } catch (Exception e) {
            log.error("Failed to validate RLS session configuration", e);
            return false;
        }
    }

    /**
     * Retrieves a PostgreSQL session parameter value.
     *
     * @param connection JDBC connection
     * @param paramName parameter name
     * @return parameter value or null if not set
     * @throws SQLException if query fails
     */
    private String getSessionParameter(Connection connection, String paramName) throws SQLException {
        String sql = "SELECT current_setting(?, true)"; // true = missing_ok

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, paramName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    /**
     * Clears all RLS session parameters.
     *
     * CAUTION:
     * Normally not needed because SET LOCAL parameters auto-clear at transaction end.
     * Only use if explicit clearing is required for security reasons.
     *
     * @param connection JDBC connection
     */
    public void clearSessionParameters(Connection connection) {
        try {
            setSessionParameter(connection, "app.current_user_id", "");
            setSessionParameter(connection, "app.user_role", "");
            setSessionParameter(connection, "app.session_id", "");
            setSessionParameter(connection, "app.emergency_access", "false");

            log.debug("RLS session parameters cleared");

        } catch (SQLException e) {
            log.warn("Failed to clear RLS session parameters (not critical)", e);
        }
    }
}
