package com.waqiti.transaction.service.impl;

import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.service.ReceiptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of receipt template service with advanced templating capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptTemplateServiceImpl implements ReceiptTemplateService {

    private final ResourceLoader resourceLoader;

    @Value("${waqiti.company.name:Waqiti Financial Services}")
    private String companyName;

    @Value("${waqiti.company.logo:/assets/waqiti-logo.png}")
    private String companyLogo;

    @Value("${waqiti.company.address:123 Financial District, New York, NY 10001}")
    private String companyAddress;

    @Value("${waqiti.company.phone:+1-800-WAQITI}")
    private String companyPhone;

    @Value("${waqiti.company.email:support@example.com}")
    private String companyEmail;

    @Value("${waqiti.company.website:https://example.com}")
    private String companyWebsite;

    @Value("${waqiti.branding.primary-color:#2196F3}")
    private String primaryColor;

    @Value("${waqiti.branding.secondary-color:#FFC107}")
    private String secondaryColor;

    @Value("${waqiti.branding.font-family:Helvetica}")
    private String fontFamily;

    private final Map<ReceiptGenerationOptions.ReceiptFormat, String> templates = new HashMap<>();
    private final Pattern variablePattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    @PostConstruct
    public void loadTemplates() {
        try {
            // Load default templates
            loadTemplate(ReceiptGenerationOptions.ReceiptFormat.STANDARD, "templates/receipt-standard.html");
            loadTemplate(ReceiptGenerationOptions.ReceiptFormat.DETAILED, "templates/receipt-detailed.html");
            loadTemplate(ReceiptGenerationOptions.ReceiptFormat.MINIMAL, "templates/receipt-minimal.html");
            loadTemplate(ReceiptGenerationOptions.ReceiptFormat.PROOF_OF_PAYMENT, "templates/receipt-proof.html");
            loadTemplate(ReceiptGenerationOptions.ReceiptFormat.TAX_DOCUMENT, "templates/receipt-tax.html");
            
            log.info("Successfully loaded {} receipt templates", templates.size());
        } catch (Exception e) {
            log.error("Error loading receipt templates", e);
            // Load fallback templates
            loadFallbackTemplates();
        }
    }

    @Override
    public String getTemplate(ReceiptGenerationOptions.ReceiptFormat format) {
        return templates.getOrDefault(format, templates.get(ReceiptGenerationOptions.ReceiptFormat.STANDARD));
    }

    @Override
    public String processTemplate(String template, Transaction transaction, ReceiptGenerationOptions options) {
        Map<String, Object> variables = buildVariableMap(transaction, options);
        
        String processed = template;
        Matcher matcher = variablePattern.matcher(template);
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            Object value = getVariableValue(variable, variables);
            
            if (value != null) {
                processed = processed.replace("{{" + variable + "}}", value.toString());
            }
        }
        
        return processed;
    }

    @Override
    public CompanyBrandingConfig getBrandingConfig() {
        return new CompanyBrandingConfigImpl();
    }

    @Override
    public boolean isValidTemplate(String template) {
        try {
            Matcher matcher = variablePattern.matcher(template);
            Set<String> availableVars = getAvailableVariables();
            
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (!availableVars.contains(variable)) {
                    log.warn("Unknown template variable: {}", variable);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error validating template", e);
            return false;
        }
    }

    @Override
    public Set<String> getAvailableVariables() {
        return Set.of(
            "transactionId", "amount", "currency", "status", "type", "description", "reference",
            "fromWallet", "toWallet", "createdAt", "updatedAt", "feeAmount",
            "companyName", "companyAddress", "companyPhone", "companyEmail", "companyWebsite",
            "receiptDate", "receiptTime", "receiptDateTime", "taxYear",
            "primaryColor", "secondaryColor", "fontFamily", "companyLogo"
        );
    }

    private void loadTemplate(ReceiptGenerationOptions.ReceiptFormat format, String resourcePath) {
        try {
            var resource = resourceLoader.getResource("classpath:" + resourcePath);
            if (resource.exists()) {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                templates.put(format, content);
                log.debug("Loaded template for format: {}", format);
            } else {
                log.warn("Template resource not found: {}", resourcePath);
                templates.put(format, getFallbackTemplate(format));
            }
        } catch (IOException e) {
            log.error("Error loading template for format: {}", format, e);
            templates.put(format, getFallbackTemplate(format));
        }
    }

    private void loadFallbackTemplates() {
        for (ReceiptGenerationOptions.ReceiptFormat format : ReceiptGenerationOptions.ReceiptFormat.values()) {
            templates.put(format, getFallbackTemplate(format));
        }
        log.info("Loaded fallback templates for all formats");
    }

    private String getFallbackTemplate(ReceiptGenerationOptions.ReceiptFormat format) {
        return switch (format) {
            case STANDARD -> getStandardTemplate();
            case DETAILED -> getDetailedTemplate();
            case MINIMAL -> getMinimalTemplate();
            case PROOF_OF_PAYMENT -> getProofTemplate();
            case TAX_DOCUMENT -> getTaxTemplate();
        };
    }

    private String getStandardTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Transaction Receipt</title>
                <style>
                    body { font-family: {{fontFamily}}, Arial, sans-serif; margin: 40px; }
                    .header { text-align: center; border-bottom: 2px solid {{primaryColor}}; padding-bottom: 20px; }
                    .company-name { color: {{primaryColor}}; font-size: 24px; font-weight: bold; }
                    .receipt-title { font-size: 20px; margin: 20px 0; text-align: center; }
                    .info-table { width: 100%; margin: 20px 0; }
                    .info-table td { padding: 8px 0; }
                    .label { font-weight: bold; width: 30%; }
                    .amount { font-size: 18px; font-weight: bold; color: {{primaryColor}}; }
                    .footer { margin-top: 40px; font-size: 12px; color: #666; text-align: center; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="company-name">{{companyName}}</div>
                    <div>{{companyAddress}}</div>
                    <div>Phone: {{companyPhone}} | Email: {{companyEmail}}</div>
                </div>
                
                <div class="receipt-title">TRANSACTION RECEIPT</div>
                
                <table class="info-table">
                    <tr><td class="label">Receipt Number:</td><td>{{transactionId}}</td></tr>
                    <tr><td class="label">Date:</td><td>{{receiptDateTime}}</td></tr>
                    <tr><td class="label">Status:</td><td>{{status}}</td></tr>
                    <tr><td class="label">Type:</td><td>{{type}}</td></tr>
                    <tr><td class="label">Amount:</td><td class="amount">{{currency}} {{amount}}</td></tr>
                    <tr><td class="label">Description:</td><td>{{description}}</td></tr>
                </table>
                
                <div class="footer">
                    This receipt is digitally generated and verified.<br>
                    For questions, contact {{companyEmail}}
                </div>
            </body>
            </html>
            """;
    }

    private String getDetailedTemplate() {
        return getStandardTemplate() + """
            <!-- Additional detailed sections would be added here -->
            <div class="detailed-section">
                <h3>Transaction Details</h3>
                <table class="info-table">
                    <tr><td class="label">From Wallet:</td><td>{{fromWallet}}</td></tr>
                    <tr><td class="label">To Wallet:</td><td>{{toWallet}}</td></tr>
                    <tr><td class="label">Reference:</td><td>{{reference}}</td></tr>
                    <tr><td class="label">Fee Amount:</td><td>{{currency}} {{feeAmount}}</td></tr>
                </table>
            </div>
            """;
    }

    private String getMinimalTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <head><title>Payment Confirmation</title></head>
            <body style="font-family: Arial, sans-serif; margin: 20px;">
                <h2>Payment Confirmation</h2>
                <p><strong>Transaction ID:</strong> {{transactionId}}</p>
                <p><strong>Amount:</strong> {{currency}} {{amount}}</p>
                <p><strong>Date:</strong> {{receiptDateTime}}</p>
                <p><strong>Status:</strong> {{status}}</p>
            </body>
            </html>
            """;
    }

    private String getProofTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Proof of Payment</title>
                <style>
                    body { font-family: {{fontFamily}}, Arial, sans-serif; margin: 40px; }
                    .header { text-align: center; border-bottom: 3px solid {{primaryColor}}; padding-bottom: 20px; }
                    .proof-title { font-size: 24px; font-weight: bold; color: {{primaryColor}}; text-align: center; margin: 30px 0; }
                    .certification { background: #f8f9fa; padding: 20px; margin: 20px 0; border-left: 5px solid {{primaryColor}}; }
                    .legal-disclaimer { font-size: 10px; color: #666; margin-top: 40px; border-top: 1px solid #ddd; padding-top: 20px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div style="font-size: 24px; font-weight: bold; color: {{primaryColor}};">{{companyName}}</div>
                    <div>{{companyAddress}}</div>
                </div>
                
                <div class="proof-title">PROOF OF PAYMENT</div>
                
                <div class="certification">
                    This document certifies that the payment detailed below has been successfully processed and completed.
                </div>
                
                <!-- Standard receipt content would be included here -->
                
                <div class="legal-disclaimer">
                    LEGAL DISCLAIMER: This proof of payment is valid for the transaction specified above. 
                    For disputes or inquiries, please contact customer support within 60 days of the transaction date.
                </div>
            </body>
            </html>
            """;
    }

    private String getTaxTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Tax Document - Transaction Record</title>
                <style>
                    body { font-family: {{fontFamily}}, Arial, sans-serif; margin: 40px; }
                    .tax-header { background: {{primaryColor}}; color: white; padding: 15px; text-align: center; }
                    .tax-year { font-size: 18px; font-weight: bold; margin: 20px 0; }
                    .tax-notice { background: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="tax-header">
                    <h1>TAX DOCUMENT - TRANSACTION RECORD</h1>
                </div>
                
                <div class="tax-year">Tax Year: {{taxYear}}</div>
                
                <!-- Standard receipt content -->
                
                <div class="tax-notice">
                    <strong>Tax Information:</strong> Please consult with a tax professional for specific 
                    tax implications of this transaction. This document may be used for tax reporting purposes.
                </div>
            </body>
            </html>
            """;
    }

    private Map<String, Object> buildVariableMap(Transaction transaction, ReceiptGenerationOptions options) {
        Map<String, Object> variables = new HashMap<>();
        
        // Transaction data
        variables.put("transactionId", transaction.getId());
        variables.put("amount", transaction.getAmount());
        variables.put("currency", transaction.getCurrency());
        variables.put("status", transaction.getStatus());
        variables.put("type", transaction.getType());
        variables.put("description", transaction.getDescription() != null ? transaction.getDescription() : "");
        variables.put("reference", transaction.getReference() != null ? transaction.getReference() : "");
        variables.put("fromWallet", transaction.getFromWalletId() != null ? transaction.getFromWalletId() : "N/A");
        variables.put("toWallet", transaction.getToWalletId() != null ? transaction.getToWalletId() : "N/A");
        variables.put("feeAmount", transaction.getFeeAmount() != null ? transaction.getFeeAmount() : java.math.BigDecimal.ZERO);
        
        // Date formatting
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        variables.put("createdAt", transaction.getCreatedAt());
        variables.put("updatedAt", transaction.getUpdatedAt());
        variables.put("receiptDateTime", transaction.getCreatedAt().format(dateTimeFormatter));
        variables.put("receiptDate", transaction.getCreatedAt().format(dateFormatter));
        variables.put("receiptTime", transaction.getCreatedAt().format(timeFormatter));
        variables.put("taxYear", transaction.getCreatedAt().getYear());
        
        // Company branding
        variables.put("companyName", companyName);
        variables.put("companyAddress", companyAddress);
        variables.put("companyPhone", companyPhone);
        variables.put("companyEmail", companyEmail);
        variables.put("companyWebsite", companyWebsite);
        variables.put("companyLogo", companyLogo);
        variables.put("primaryColor", primaryColor);
        variables.put("secondaryColor", secondaryColor);
        variables.put("fontFamily", fontFamily);
        
        return variables;
    }

    private Object getVariableValue(String variable, Map<String, Object> variables) {
        return variables.get(variable);
    }

    private class CompanyBrandingConfigImpl implements CompanyBrandingConfig {
        @Override
        public String getCompanyName() { return companyName; }
        
        @Override
        public String getCompanyLogo() { return companyLogo; }
        
        @Override
        public String getCompanyAddress() { return companyAddress; }
        
        @Override
        public String getCompanyPhone() { return companyPhone; }
        
        @Override
        public String getCompanyEmail() { return companyEmail; }
        
        @Override
        public String getCompanyWebsite() { return companyWebsite; }
        
        @Override
        public String getPrimaryColor() { return primaryColor; }
        
        @Override
        public String getSecondaryColor() { return secondaryColor; }
        
        @Override
        public String getFontFamily() { return fontFamily; }
    }
}