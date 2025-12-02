package com.waqiti.user.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Custom CSRF Token Request Handler for Single Page Applications
 *
 * Implements the "Double Submit Cookie" pattern optimized for SPAs:
 * 1. Token is stored in a cookie (XSRF-TOKEN)
 * 2. SPA reads the cookie and sends the token in a header (X-XSRF-TOKEN)
 * 3. Server validates that header value matches cookie value
 *
 * Security Benefits:
 * - Protects against CSRF attacks from malicious sites
 * - XOR masking prevents BREACH-style attacks
 * - Compatible with modern SPA frameworks (React, Angular, Vue)
 * - Supports both header and parameter-based submission
 *
 * Integration with SPA:
 * ```javascript
 * // Axios automatically reads XSRF-TOKEN cookie and sends as X-XSRF-TOKEN header
 * axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
 * axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
 *
 * // Fetch API - manual handling
 * const csrfToken = document.cookie
 *   .split('; ')
 *   .find(row => row.startsWith('XSRF-TOKEN='))
 *   ?.split('=')[1];
 *
 * fetch('/api/v1/payments', {
 *   method: 'POST',
 *   headers: {
 *     'X-XSRF-TOKEN': csrfToken
 *   },
 *   credentials: 'include'
 * });
 * ```
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                      Supplier<CsrfToken> csrfTokenSupplier) {
        /*
         * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection
         * of the CsrfToken when it is rendered in the response body.
         */
        this.delegate.handle(request, response, csrfTokenSupplier);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        /*
         * If the request contains a request header, use CsrfTokenRequestAttributeHandler
         * to resolve the CsrfToken. This applies when a single-page application includes
         * the header value automatically, which was obtained via a cookie containing the
         * raw CsrfToken.
         */
        String headerValue = request.getHeader(csrfToken.getHeaderName());

        if (StringUtils.hasText(headerValue)) {
            // Token from header (SPA client sent X-XSRF-TOKEN)
            return super.resolveCsrfTokenValue(request, csrfToken);
        }

        /*
         * In all other cases (e.g. if the request contains a request parameter), use
         * XorCsrfTokenRequestAttributeHandler to resolve the CsrfToken. This applies
         * when a server-side rendered form includes the _csrf request parameter as a
         * hidden input.
         */
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
