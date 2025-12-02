package com.waqiti.dispute.config;

import com.waqiti.dispute.exception.ExternalServiceException;
import com.waqiti.dispute.exception.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Custom error handler for RestTemplate responses.
 * Differentiates between retriable and non-retriable errors.
 * Provides detailed error messages for debugging.
 *
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Slf4j
public class RestTemplateResponseErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        HttpStatus.Series series = response.getStatusCode().series();
        return series == HttpStatus.Series.CLIENT_ERROR ||
               series == HttpStatus.Series.SERVER_ERROR;
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String statusText = response.getStatusText();

        // Read response body for error details
        String responseBody = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            responseBody = reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Failed to read error response body", e);
        }

        String errorMessage = String.format(
            "External service call failed - Status: %s %s, Body: %s",
            statusCode.value(),
            statusText,
            responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody
        );

        log.error(errorMessage);

        // Determine if error is retriable
        if (isRetriable(statusCode)) {
            throw new RetryableException(errorMessage);
        } else {
            throw new ExternalServiceException(errorMessage, statusCode.value());
        }
    }

    /**
     * Determines if an HTTP status code indicates a retriable error.
     * Retriable errors:
     * - 408 Request Timeout
     * - 429 Too Many Requests
     * - 500 Internal Server Error
     * - 502 Bad Gateway
     * - 503 Service Unavailable
     * - 504 Gateway Timeout
     *
     * Non-retriable errors (permanent failures):
     * - 400 Bad Request
     * - 401 Unauthorized
     * - 403 Forbidden
     * - 404 Not Found
     * - 422 Unprocessable Entity
     *
     * @param statusCode HTTP status code
     * @return true if error is retriable, false otherwise
     */
    private boolean isRetriable(HttpStatus statusCode) {
        return statusCode == HttpStatus.REQUEST_TIMEOUT ||
               statusCode == HttpStatus.TOO_MANY_REQUESTS ||
               statusCode == HttpStatus.INTERNAL_SERVER_ERROR ||
               statusCode == HttpStatus.BAD_GATEWAY ||
               statusCode == HttpStatus.SERVICE_UNAVAILABLE ||
               statusCode == HttpStatus.GATEWAY_TIMEOUT;
    }
}
