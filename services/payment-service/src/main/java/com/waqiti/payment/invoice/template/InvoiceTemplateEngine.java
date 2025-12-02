package com.waqiti.payment.invoice.template;

import com.waqiti.payment.invoice.Invoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for generating invoice HTML/PDF content
 */
@Slf4j
@Component
public class InvoiceTemplateEngine {
    
    private final Map<String, InvoiceTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, TemplateProcessor> processorCache = new ConcurrentHashMap<>();
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\{\\{#each\\s+([^}]+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
    private static final Pattern IF_PATTERN = Pattern.compile("\\{\\{#if\\s+([^}]+)\\}\\}(.*?)(?:\\{\\{#else\\}\\}(.*?))?\\{\\{/if\\}\\}", Pattern.DOTALL);
    
    /**
     * Generate invoice HTML from template
     */
    public String generateInvoiceHtml(Invoice invoice, String templateName) {
        log.debug("Generating invoice HTML using template: {}", templateName);
        
        InvoiceTemplate template = getTemplate(templateName);
        if (template == null) {
            template = getDefaultTemplate();
        }
        
        Map<String, Object> context = buildInvoiceContext(invoice);
        return processTemplate(template.getContent(), context);
    }
    
    /**
     * Generate invoice HTML with custom template
     */
    public String generateInvoiceHtml(Invoice invoice, InvoiceTemplate template) {
        log.debug("Generating invoice HTML with custom template");
        
        Map<String, Object> context = buildInvoiceContext(invoice);
        return processTemplate(template.getContent(), context);
    }
    
    /**
     * Process template with context
     */
    public String processTemplate(String template, Map<String, Object> context) {
        if (template == null || context == null) {
            return template;
        }
        
        String processed = template;
        
        // Process loops first
        processed = processLoops(processed, context);
        
        // Process conditionals
        processed = processConditionals(processed, context);
        
        // Process variables
        processed = processVariables(processed, context);
        
        // Process functions
        processed = processFunctions(processed, context);
        
        return processed;
    }
    
    /**
     * Register custom template
     */
    public void registerTemplate(String name, InvoiceTemplate template) {
        templateCache.put(name, template);
        log.info("Registered invoice template: {}", name);
    }
    
    /**
     * Register custom processor
     */
    public void registerProcessor(String name, TemplateProcessor processor) {
        processorCache.put(name, processor);
        log.info("Registered template processor: {}", name);
    }
    
    /**
     * Get available template names
     */
    public Set<String> getAvailableTemplates() {
        loadDefaultTemplates();
        return new HashSet<>(templateCache.keySet());
    }
    
    /**
     * Validate template syntax
     */
    public TemplateValidationResult validateTemplate(String template) {
        TemplateValidationResult result = new TemplateValidationResult();
        result.setValid(true);
        
        try {
            // Check for unclosed variables
            int openCount = countOccurrences(template, "{{");
            int closeCount = countOccurrences(template, "}}");
            if (openCount != closeCount) {
                result.setValid(false);
                result.addError("Mismatched variable brackets: " + openCount + " open, " + closeCount + " close");
            }
            
            // Check for unclosed loops
            Matcher loopMatcher = Pattern.compile("\\{\\{#each\\s+([^}]+)\\}\\}").matcher(template);
            int eachCount = 0;
            while (loopMatcher.find()) {
                eachCount++;
            }
            
            Matcher endLoopMatcher = Pattern.compile("\\{\\{/each\\}\\}").matcher(template);
            int endEachCount = 0;
            while (endLoopMatcher.find()) {
                endEachCount++;
            }
            
            if (eachCount != endEachCount) {
                result.setValid(false);
                result.addError("Mismatched loop tags: " + eachCount + " #each, " + endEachCount + " /each");
            }
            
            // Check for unclosed conditionals
            Matcher ifMatcher = Pattern.compile("\\{\\{#if\\s+([^}]+)\\}\\}").matcher(template);
            int ifCount = 0;
            while (ifMatcher.find()) {
                ifCount++;
            }
            
            Matcher endIfMatcher = Pattern.compile("\\{\\{/if\\}\\}").matcher(template);
            int endIfCount = 0;
            while (endIfMatcher.find()) {
                endIfCount++;
            }
            
            if (ifCount != endIfCount) {
                result.setValid(false);
                result.addError("Mismatched conditional tags: " + ifCount + " #if, " + endIfCount + " /if");
            }
            
        } catch (Exception e) {
            result.setValid(false);
            result.addError("Template validation error: " + e.getMessage());
        }
        
        return result;
    }
    
    // Private helper methods
    
    private InvoiceTemplate getTemplate(String name) {
        if (templateCache.isEmpty()) {
            loadDefaultTemplates();
        }
        return templateCache.get(name);
    }
    
    private InvoiceTemplate getDefaultTemplate() {
        if (!templateCache.containsKey("default")) {
            loadDefaultTemplates();
        }
        return templateCache.get("default");
    }
    
    private void loadDefaultTemplates() {
        // Default invoice template
        String defaultTemplate = 
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<style>\n" +
            "body { font-family: Arial, sans-serif; }\n" +
            ".invoice-header { background: #f8f9fa; padding: 20px; }\n" +
            ".invoice-title { font-size: 24px; font-weight: bold; }\n" +
            ".invoice-details { margin: 20px 0; }\n" +
            ".line-items { width: 100%; border-collapse: collapse; }\n" +
            ".line-items th, .line-items td { padding: 10px; border: 1px solid #ddd; }\n" +
            ".total-section { text-align: right; margin-top: 20px; }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class='invoice-header'>\n" +
            "<div class='invoice-title'>INVOICE {{invoiceNumber}}</div>\n" +
            "<div>Date: {{issueDate}}</div>\n" +
            "<div>Due Date: {{dueDate}}</div>\n" +
            "</div>\n" +
            "<div class='invoice-details'>\n" +
            "<div><strong>From:</strong><br>{{businessName}}<br>{{businessAddress}}</div>\n" +
            "<div><strong>To:</strong><br>{{customerName}}<br>{{customerAddress}}</div>\n" +
            "</div>\n" +
            "<table class='line-items'>\n" +
            "<thead>\n" +
            "<tr><th>Description</th><th>Quantity</th><th>Unit Price</th><th>Amount</th></tr>\n" +
            "</thead>\n" +
            "<tbody>\n" +
            "{{#each lineItems}}\n" +
            "<tr>\n" +
            "<td>{{description}}</td>\n" +
            "<td>{{quantity}}</td>\n" +
            "<td>{{formatCurrency unitPrice}}</td>\n" +
            "<td>{{formatCurrency amount}}</td>\n" +
            "</tr>\n" +
            "{{/each}}\n" +
            "</tbody>\n" +
            "</table>\n" +
            "<div class='total-section'>\n" +
            "<div>Subtotal: {{formatCurrency subtotal}}</div>\n" +
            "<div>Tax: {{formatCurrency taxAmount}}</div>\n" +
            "<div><strong>Total: {{formatCurrency totalAmount}}</strong></div>\n" +
            "</div>\n" +
            "{{#if notes}}\n" +
            "<div class='notes'>\n" +
            "<strong>Notes:</strong><br>{{notes}}\n" +
            "</div>\n" +
            "{{/if}}\n" +
            "</body>\n" +
            "</html>";
        
        templateCache.put("default", InvoiceTemplate.builder()
                .name("default")
                .content(defaultTemplate)
                .build());
        
        // Professional template
        templateCache.put("professional", InvoiceTemplate.builder()
                .name("professional")
                .content(getProfessionalTemplate())
                .build());
        
        // Simple template
        templateCache.put("simple", InvoiceTemplate.builder()
                .name("simple")
                .content(getSimpleTemplate())
                .build());
    }
    
    private String getProfessionalTemplate() {
        // Professional template with more styling
        return "<!DOCTYPE html>...";
    }
    
    private String getSimpleTemplate() {
        // Simple, minimal template
        return "<!DOCTYPE html>...";
    }
    
    private Map<String, Object> buildInvoiceContext(Invoice invoice) {
        Map<String, Object> context = new HashMap<>();
        
        // Basic invoice data
        context.put("invoiceNumber", invoice.getInvoiceNumber());
        context.put("issueDate", formatDate(invoice.getIssueDate()));
        context.put("dueDate", formatDate(invoice.getDueDate()));
        context.put("status", invoice.getStatus());
        context.put("currency", invoice.getCurrency());
        
        // Business data
        context.put("businessName", invoice.getBusinessName());
        context.put("businessAddress", invoice.getBusinessAddress());
        context.put("businessTaxId", invoice.getBusinessTaxId());
        
        // Customer data
        context.put("customerName", invoice.getCustomerName());
        context.put("customerAddress", invoice.getCustomerAddress());
        context.put("customerEmail", invoice.getCustomerEmail());
        context.put("customerTaxId", invoice.getCustomerTaxId());
        
        // Line items
        context.put("lineItems", invoice.getLineItems());
        
        // Amounts
        context.put("subtotal", invoice.getSubtotal());
        context.put("taxAmount", invoice.getTaxAmount());
        context.put("discountAmount", invoice.getDiscountAmount());
        context.put("totalAmount", invoice.getTotalAmount());
        
        // Additional data
        context.put("notes", invoice.getNotes());
        context.put("termsAndConditions", invoice.getTermsAndConditions());
        context.put("paymentTerms", invoice.getPaymentTerms());
        context.put("paymentMethod", invoice.getPaymentMethod());
        
        // Add helper functions
        context.put("formatCurrency", new CurrencyFormatter(invoice.getCurrency()));
        context.put("formatDate", new DateFormatter());
        
        return context;
    }
    
    private String processVariables(String template, Map<String, Object> context) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variable = matcher.group(1).trim();
            Object value = resolveVariable(variable, context);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String processLoops(String template, Map<String, Object> context) {
        Matcher matcher = LOOP_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String collectionName = matcher.group(1).trim();
            String loopContent = matcher.group(2);
            
            Object collection = context.get(collectionName);
            StringBuilder loopResult = new StringBuilder();
            
            if (collection instanceof List) {
                List<?> list = (List<?>) collection;
                for (Object item : list) {
                    Map<String, Object> loopContext = new HashMap<>(context);
                    loopContext.put("item", item);
                    
                    // Make item properties directly accessible
                    if (item instanceof Map) {
                        loopContext.putAll((Map<String, Object>) item);
                    }
                    
                    String processed = processTemplate(loopContent, loopContext);
                    loopResult.append(processed);
                }
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String processConditionals(String template, Map<String, Object> context) {
        Matcher matcher = IF_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String ifContent = matcher.group(2);
            String elseContent = matcher.group(3);
            
            boolean conditionResult = evaluateCondition(condition, context);
            String replacement = conditionResult ? ifContent : (elseContent != null ? elseContent : "");
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String processFunctions(String template, Map<String, Object> context) {
        Pattern functionPattern = Pattern.compile("\\{\\{([a-zA-Z]+)\\s+([^}]+)\\}\\}");
        Matcher matcher = functionPattern.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String functionName = matcher.group(1);
            String argument = matcher.group(2);
            
            Object function = context.get(functionName);
            String replacement = "";
            
            if (function instanceof TemplateFunction) {
                Object argValue = resolveVariable(argument, context);
                replacement = ((TemplateFunction) function).apply(argValue);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private Object resolveVariable(String variable, Map<String, Object> context) {
        if (variable.contains(".")) {
            String[] parts = variable.split("\\.");
            Object current = context.get(parts[0]);
            
            for (int i = 1; i < parts.length && current != null; i++) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(parts[i]);
                }
            }
            
            return current;
        }
        
        return context.get(variable);
    }
    
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        Object value = resolveVariable(condition, context);
        
        if (value == null) {
            return false;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        
        return true;
    }
    
    private String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
    
    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        
        return count;
    }
    
    // Inner classes
    
    public static class InvoiceTemplate {
        private String name;
        private String content;
        private Map<String, String> metadata;
        
        private InvoiceTemplate(Builder builder) {
            this.name = builder.name;
            this.content = builder.content;
            this.metadata = builder.metadata;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String name;
            private String content;
            private Map<String, String> metadata = new HashMap<>();
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder content(String content) {
                this.content = content;
                return this;
            }
            
            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }
            
            public InvoiceTemplate build() {
                return new InvoiceTemplate(this);
            }
        }
        
        // Getters
        public String getName() { return name; }
        public String getContent() { return content; }
        public Map<String, String> getMetadata() { return metadata; }
    }
    
    public interface TemplateProcessor {
        String process(String template, Map<String, Object> context);
    }
    
    public interface TemplateFunction {
        String apply(Object value);
    }
    
    public static class CurrencyFormatter implements TemplateFunction {
        private final NumberFormat formatter;
        
        public CurrencyFormatter(String currencyCode) {
            this.formatter = NumberFormat.getCurrencyInstance(Locale.US);
            this.formatter.setCurrency(Currency.getInstance(currencyCode != null ? currencyCode : "USD"));
        }
        
        @Override
        public String apply(Object value) {
            if (value == null) return "";
            
            if (value instanceof BigDecimal) {
                return formatter.format(value);
            }
            
            if (value instanceof Number) {
                return formatter.format(((Number) value).doubleValue());
            }
            
            return value.toString();
        }
    }
    
    public static class DateFormatter implements TemplateFunction {
        private final DateTimeFormatter formatter;
        
        public DateFormatter() {
            this.formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        }
        
        @Override
        public String apply(Object value) {
            if (value == null) return "";
            
            if (value instanceof LocalDate) {
                return ((LocalDate) value).format(formatter);
            }
            
            return value.toString();
        }
    }
    
    public static class TemplateValidationResult {
        private boolean valid;
        private List<String> errors = new ArrayList<>();
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
    }
}