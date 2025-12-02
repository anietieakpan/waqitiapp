package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import com.waqiti.transaction.entity.Transaction;

/**
 * Service for managing receipt templates and customization
 */
public interface ReceiptTemplateService {

    /**
     * Get template content for a specific format
     */
    String getTemplate(ReceiptGenerationOptions.ReceiptFormat format);

    /**
     * Process template with transaction data
     */
    String processTemplate(String template, Transaction transaction, ReceiptGenerationOptions options);

    /**
     * Get company branding configuration
     */
    CompanyBrandingConfig getBrandingConfig();

    /**
     * Validate template syntax
     */
    boolean isValidTemplate(String template);

    /**
     * Get available template variables
     */
    java.util.Set<String> getAvailableVariables();

    /**
     * Company branding configuration
     */
    interface CompanyBrandingConfig {
        String getCompanyName();
        String getCompanyLogo();
        String getCompanyAddress();
        String getCompanyPhone();
        String getCompanyEmail();
        String getCompanyWebsite();
        String getPrimaryColor();
        String getSecondaryColor();
        String getFontFamily();
    }
}