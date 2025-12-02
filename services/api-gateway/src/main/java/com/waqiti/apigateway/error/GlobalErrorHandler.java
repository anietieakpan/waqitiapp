/**
 * File: ./api-gateway/src/main/java/com/waqiti/gateway/error/GlobalErrorHandler.java
 */
package com.waqiti.apigateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(-2) // High precedence
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException) {
            status = HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
            message = ex.getMessage();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal Server Error";
        }

        // Log the error
        log.error("Gateway Error: {}", ex.getMessage(), ex);

        // Set the response status
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Create error response body
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("path", exchange.getRequest().getURI().getPath());

        // Convert to JSON
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        DataBuffer dataBuffer;
        try {
            dataBuffer = dataBufferFactory.wrap(objectMapper.writeValueAsBytes(errorResponse));
        } catch (JsonProcessingException e) {
            log.error("Error writing error response as JSON", e);
            dataBuffer = dataBufferFactory.wrap("Internal Server Error".getBytes());
        }

        return response.writeWith(Mono.just(dataBuffer));
    }
}