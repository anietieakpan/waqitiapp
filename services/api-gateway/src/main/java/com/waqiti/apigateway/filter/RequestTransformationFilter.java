package com.waqiti.apigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Request Transformation Filter
 * Transforms request payloads, headers, and parameters based on configuration
 */
@Slf4j
@Component("requestTransformationFilter")
public class RequestTransformationFilter extends AbstractGatewayFilterFactory<RequestTransformationFilter.Config> {

    private final ObjectMapper objectMapper;
    private final ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter;

    public RequestTransformationFilter(ObjectMapper objectMapper, 
                                     ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter) {
        super(Config.class);
        this.objectMapper = objectMapper;
        this.modifyRequestBodyFilter = modifyRequestBodyFilter;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // Transform headers
            ServerHttpRequest.Builder requestBuilder = request.mutate();
            transformHeaders(requestBuilder, request.getHeaders(), config);
            
            // Transform query parameters
            transformQueryParams(requestBuilder, request, config);
            
            // Transform request body if needed
            if (shouldTransformBody(request, config)) {
                return transformRequestBody(exchange, chain, config);
            }
            
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        };
    }

    private void transformHeaders(ServerHttpRequest.Builder builder, HttpHeaders headers, Config config) {
        // Add new headers
        if (config.getAddHeaders() != null) {
            config.getAddHeaders().forEach(builder::header);
        }
        
        // Remove headers
        if (config.getRemoveHeaders() != null) {
            for (String headerName : config.getRemoveHeaders()) {
                builder.headers(h -> h.remove(headerName));
            }
        }
        
        // Rename headers
        if (config.getRenameHeaders() != null) {
            config.getRenameHeaders().forEach((oldName, newName) -> {
                String value = headers.getFirst(oldName);
                if (value != null) {
                    builder.header(newName, value);
                    builder.headers(h -> h.remove(oldName));
                }
            });
        }
        
        // Transform header values
        if (config.getTransformHeaders() != null) {
            config.getTransformHeaders().forEach((headerName, transformation) -> {
                String value = headers.getFirst(headerName);
                if (value != null) {
                    String transformedValue = applyTransformation(value, transformation);
                    builder.header(headerName, transformedValue);
                }
            });
        }
    }

    private void transformQueryParams(ServerHttpRequest.Builder builder, ServerHttpRequest request, Config config) {
        if (config.getTransformQueryParams() == null) {
            return;
        }
        
        Map<String, String> queryParams = request.getQueryParams().toSingleValueMap();
        StringBuilder newQuery = new StringBuilder();
        
        queryParams.forEach((key, value) -> {
            String newKey = key;
            String newValue = value;
            
            // Apply transformations
            if (config.getTransformQueryParams().containsKey(key)) {
                Map<String, String> transformation = config.getTransformQueryParams().get(key);
                if (transformation.containsKey("rename")) {
                    newKey = transformation.get("rename");
                }
                if (transformation.containsKey("transform")) {
                    newValue = applyTransformation(value, transformation.get("transform"));
                }
            }
            
            if (newQuery.length() > 0) {
                newQuery.append("&");
            }
            newQuery.append(newKey).append("=").append(newValue);
        });
        
        if (newQuery.length() > 0) {
            builder.uri(request.getURI().resolve("?" + newQuery.toString()));
        }
    }

    private boolean shouldTransformBody(ServerHttpRequest request, Config config) {
        return config.getTransformBody() != null && 
               request.getHeaders().getContentType() != null &&
               request.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON);
    }

    private Mono<Void> transformRequestBody(org.springframework.web.server.ServerWebExchange exchange, 
                                           org.springframework.cloud.gateway.filter.GatewayFilterChain chain, 
                                           Config config) {
        
        ModifyRequestBodyGatewayFilterFactory.Config modifyConfig = new ModifyRequestBodyGatewayFilterFactory.Config()
            .setRewriteFunction(String.class, String.class, (exchange1, body) -> {
                try {
                    if (body == null || body.isEmpty()) {
                        return Mono.just(body);
                    }
                    
                    JsonNode jsonNode = objectMapper.readTree(body);
                    JsonNode transformedNode = transformJson(jsonNode, config.getTransformBody());
                    return Mono.just(objectMapper.writeValueAsString(transformedNode));
                } catch (Exception e) {
                    log.error("Error transforming request body", e);
                    return Mono.just(body);
                }
            });
        
        return modifyRequestBodyFilter.apply(modifyConfig).filter(exchange, chain);
    }

    private JsonNode transformJson(JsonNode node, Map<String, Object> transformations) {
        if (!node.isObject()) {
            return node;
        }
        
        ObjectNode objectNode = (ObjectNode) node.deepCopy();
        
        transformations.forEach((path, transformation) -> {
            if (transformation instanceof String) {
                // Simple field rename
                String[] parts = path.split("\\.");
                JsonNode value = getNodeByPath(objectNode, parts);
                if (value != null) {
                    removeNodeByPath(objectNode, parts);
                    setNodeByPath(objectNode, ((String) transformation).split("\\."), value);
                }
            } else if (transformation instanceof Map) {
                // Complex transformation
                Map<String, Object> transformMap = (Map<String, Object>) transformation;
                applyComplexTransformation(objectNode, path, transformMap);
            }
        });
        
        return objectNode;
    }

    private void applyComplexTransformation(ObjectNode node, String path, Map<String, Object> transformation) {
        String[] parts = path.split("\\.");
        JsonNode targetNode = getNodeByPath(node, parts);
        
        if (targetNode == null) {
            return;
        }
        
        // Apply transformation based on type
        if (transformation.containsKey("default") && targetNode.isNull()) {
            setNodeByPath(node, parts, objectMapper.valueToTree(transformation.get("default")));
        }
        
        if (transformation.containsKey("format") && targetNode.isTextual()) {
            String format = (String) transformation.get("format");
            String value = targetNode.asText();
            String formattedValue = formatValue(value, format);
            setNodeByPath(node, parts, objectMapper.valueToTree(formattedValue));
        }
        
        if (transformation.containsKey("map") && transformation.get("map") instanceof Map) {
            Map<String, Object> mapping = (Map<String, Object>) transformation.get("map");
            String currentValue = targetNode.asText();
            if (mapping.containsKey(currentValue)) {
                setNodeByPath(node, parts, objectMapper.valueToTree(mapping.get(currentValue)));
            }
        }
    }

    private String applyTransformation(String value, String transformation) {
        switch (transformation.toLowerCase()) {
            case "uppercase":
                return value.toUpperCase();
            case "lowercase":
                return value.toLowerCase();
            case "trim":
                return value.trim();
            case "base64encode":
                return java.util.Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            case "base64decode":
                return new String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            default:
                // Check if it's a regex replacement
                if (transformation.startsWith("regex:")) {
                    String[] parts = transformation.substring(6).split(":", 2);
                    if (parts.length == 2) {
                        return value.replaceAll(parts[0], parts[1]);
                    }
                }
                return value;
        }
    }

    private String formatValue(String value, String format) {
        switch (format.toLowerCase()) {
            case "phone":
                // Format phone number
                return value.replaceAll("[^0-9]", "")
                    .replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "($1) $2-$3");
            case "currency":
                // Format as currency
                try {
                    double amount = Double.parseDouble(value);
                    return String.format("%.2f", amount);
                } catch (NumberFormatException e) {
                    return value;
                }
            case "date":
                // Format date - simplified example
                return value; // Would use proper date formatting in production
            default:
                return value;
        }
    }

    private JsonNode getNodeByPath(JsonNode root, String[] path) {
        JsonNode current = root;
        for (String part : path) {
            if (current == null || !current.has(part)) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private void removeNodeByPath(ObjectNode root, String[] path) {
        if (path.length == 1) {
            root.remove(path[0]);
        } else {
            JsonNode parent = getNodeByPath(root, java.util.Arrays.copyOf(path, path.length - 1));
            if (parent instanceof ObjectNode) {
                ((ObjectNode) parent).remove(path[path.length - 1]);
            }
        }
    }

    private void setNodeByPath(ObjectNode root, String[] path, JsonNode value) {
        ObjectNode current = root;
        for (int i = 0; i < path.length - 1; i++) {
            if (!current.has(path[i])) {
                current.set(path[i], objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(path[i]);
        }
        current.set(path[path.length - 1], value);
    }

    public static class Config {
        private Map<String, String> addHeaders;
        private String[] removeHeaders;
        private Map<String, String> renameHeaders;
        private Map<String, String> transformHeaders;
        private Map<String, Map<String, String>> transformQueryParams;
        private Map<String, Object> transformBody;

        // Getters and setters
        public Map<String, String> getAddHeaders() {
            return addHeaders;
        }

        public void setAddHeaders(Map<String, String> addHeaders) {
            this.addHeaders = addHeaders;
        }

        public String[] getRemoveHeaders() {
            return removeHeaders;
        }

        public void setRemoveHeaders(String[] removeHeaders) {
            this.removeHeaders = removeHeaders;
        }

        public Map<String, String> getRenameHeaders() {
            return renameHeaders;
        }

        public void setRenameHeaders(Map<String, String> renameHeaders) {
            this.renameHeaders = renameHeaders;
        }

        public Map<String, String> getTransformHeaders() {
            return transformHeaders;
        }

        public void setTransformHeaders(Map<String, String> transformHeaders) {
            this.transformHeaders = transformHeaders;
        }

        public Map<String, Map<String, String>> getTransformQueryParams() {
            return transformQueryParams;
        }

        public void setTransformQueryParams(Map<String, Map<String, String>> transformQueryParams) {
            this.transformQueryParams = transformQueryParams;
        }

        public Map<String, Object> getTransformBody() {
            return transformBody;
        }

        public void setTransformBody(Map<String, Object> transformBody) {
            this.transformBody = transformBody;
        }
    }
}