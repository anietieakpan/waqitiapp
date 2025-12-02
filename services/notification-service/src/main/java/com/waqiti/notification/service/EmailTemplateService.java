package com.waqiti.notification.service;

import com.waqiti.common.notification.model.NotificationTemplate;
import com.waqiti.common.notification.model.TemplateRegistrationResult;
import com.waqiti.common.notification.model.TemplateFilter;
import com.waqiti.notification.entity.EmailTemplate;
import com.waqiti.notification.repository.EmailTemplateRepository;
import com.waqiti.common.exception.NotificationException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "email:template:";
    private static final String LIST_CACHE_PREFIX = "email:templates:list";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    @Traced(operation = "create_email_template")
    @Transactional
    public TemplateRegistrationResult createTemplate(NotificationTemplate template) {
        try {
            log.debug("Creating email template: {}", template.getName());

            validateTemplate(template);

            EmailTemplate emailTemplate = new EmailTemplate();
            emailTemplate.setName(template.getName());
            emailTemplate.setDescription(template.getDescription());
            emailTemplate.setSubject(template.getSubject());
            emailTemplate.setHtmlContent(template.getHtmlContent());
            emailTemplate.setTextContent(template.getTextContent());
            emailTemplate.setCategory(template.getCategory());
            emailTemplate.setLocale(template.getLocale());
            emailTemplate.setVariables(extractVariables(template));
            emailTemplate.setActive(true);
            emailTemplate.setCreatedAt(LocalDateTime.now());
            emailTemplate.setUpdatedAt(LocalDateTime.now());

            EmailTemplate saved = emailTemplateRepository.save(emailTemplate);

            invalidateCache();

            return TemplateRegistrationResult.builder()
                .templateId(saved.getId())
                .success(true)
                .message("Email template created successfully")
                .build();

        } catch (Exception e) {
            log.error("Error creating email template: {}", template.getName(), e);
            return TemplateRegistrationResult.builder()
                .success(false)
                .message("Failed to create email template: " + e.getMessage())
                .build();
        }
    }

    @Traced(operation = "update_email_template")
    @Transactional
    public TemplateRegistrationResult updateTemplate(String templateId, NotificationTemplate template) {
        try {
            log.debug("Updating email template: {}", templateId);

            EmailTemplate existing = emailTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NotificationException("Template not found: " + templateId));

            validateTemplate(template);

            existing.setName(template.getName());
            existing.setDescription(template.getDescription());
            existing.setSubject(template.getSubject());
            existing.setHtmlContent(template.getHtmlContent());
            existing.setTextContent(template.getTextContent());
            existing.setCategory(template.getCategory());
            existing.setLocale(template.getLocale());
            existing.setVariables(extractVariables(template));
            existing.setUpdatedAt(LocalDateTime.now());

            EmailTemplate saved = emailTemplateRepository.save(existing);

            invalidateCache();

            return TemplateRegistrationResult.builder()
                .templateId(saved.getId())
                .success(true)
                .message("Email template updated successfully")
                .build();

        } catch (Exception e) {
            log.error("Error updating email template: {}", templateId, e);
            return TemplateRegistrationResult.builder()
                .success(false)
                .message("Failed to update email template: " + e.getMessage())
                .build();
        }
    }

    @Traced(operation = "delete_email_template")
    @Transactional
    public void deleteTemplate(String templateId) {
        try {
            log.debug("Deleting email template: {}", templateId);

            EmailTemplate template = emailTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NotificationException("Template not found: " + templateId));

            template.setActive(false);
            template.setUpdatedAt(LocalDateTime.now());
            emailTemplateRepository.save(template);

            invalidateCache();

        } catch (Exception e) {
            log.error("Error deleting email template: {}", templateId, e);
            throw new NotificationException("Failed to delete email template", e);
        }
    }

    @Cacheable(value = "emailTemplates", key = "#templateId")
    public NotificationTemplate getTemplate(String templateId) {
        try {
            log.debug("Getting email template: {}", templateId);

            EmailTemplate template = emailTemplateRepository.findByIdAndActive(templateId, true)
                .orElseThrow(() -> new NotificationException("Template not found: " + templateId));

            return convertToNotificationTemplate(template);

        } catch (Exception e) {
            log.error("Error getting email template: {}", templateId, e);
            throw new NotificationException("Failed to get email template", e);
        }
    }

    @Cacheable(value = "emailTemplates", key = "#templateId + '_' + #locale + '_' + #variant")
    public NotificationTemplate getTemplate(String templateId, String locale, String variant) {
        try {
            log.debug("Getting email template: {} for locale: {} variant: {}", templateId, locale, variant);
            // For now, delegate to single-parameter method
            // In production, this would query by locale/variant
            return getTemplate(templateId);
        } catch (Exception e) {
            log.error("Error getting email template: {} locale: {}", templateId, locale, e);
            throw new NotificationException("Failed to get email template", e);
        }
    }

    @Cacheable(value = "emailTemplatesList", key = "#filter.hashCode()")
    public List<NotificationTemplate> listTemplates(TemplateFilter filter) {
        try {
            log.debug("Listing email templates with filter: {}", filter);

            List<EmailTemplate> templates = emailTemplateRepository.findByActiveTrue();

            return templates.stream()
                .filter(template -> matchesFilter(template, filter))
                .map(this::convertToNotificationTemplate)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error listing email templates", e);
            throw new NotificationException("Failed to list email templates", e);
        }
    }

    @Traced(operation = "render_email_template")
    public String renderTemplate(String templateId, Map<String, Object> variables) {
        try {
            log.debug("Rendering email template: {} with variables", templateId);

            NotificationTemplate template = getTemplate(templateId);
            return processTemplate(template.getHtmlContent(), variables);

        } catch (Exception e) {
            log.error("Error rendering email template: {}", templateId, e);
            throw new NotificationException("Failed to render email template", e);
        }
    }

    @Traced(operation = "render_email_subject")
    public String renderSubject(String templateId, Map<String, Object> variables) {
        try {
            log.debug("Rendering email subject for template: {}", templateId);

            NotificationTemplate template = getTemplate(templateId);
            return processTemplate(template.getSubject(), variables);

        } catch (Exception e) {
            log.error("Error rendering email subject: {}", templateId, e);
            throw new NotificationException("Failed to render email subject", e);
        }
    }

    public boolean templateExists(String templateId) {
        try {
            return emailTemplateRepository.existsByIdAndActive(templateId, true);
        } catch (Exception e) {
            log.error("Error checking template existence: {}", templateId, e);
            return false;
        }
    }

    public List<String> getTemplateVariables(String templateId) {
        try {
            NotificationTemplate template = getTemplate(templateId);
            return extractVariableNames(template.getHtmlContent());
        } catch (Exception e) {
            log.error("Error getting template variables: {}", templateId, e);
            return Collections.emptyList();
        }
    }

    @Traced(operation = "validate_template_variables")
    public boolean validateTemplateVariables(String templateId, Map<String, Object> variables) {
        try {
            List<String> requiredVariables = getTemplateVariables(templateId);
            return requiredVariables.stream().allMatch(variables::containsKey);
        } catch (Exception e) {
            log.error("Error validating template variables: {}", templateId, e);
            return false;
        }
    }

    private void validateTemplate(NotificationTemplate template) {
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new NotificationException("Template name is required");
        }

        if (template.getSubject() == null || template.getSubject().trim().isEmpty()) {
            throw new NotificationException("Template subject is required");
        }

        if (template.getHtmlContent() == null || template.getHtmlContent().trim().isEmpty()) {
            throw new NotificationException("Template HTML content is required");
        }

        // Check for template name uniqueness
        if (emailTemplateRepository.existsByNameAndActive(template.getName(), true)) {
            throw new NotificationException("Template with name already exists: " + template.getName());
        }

        // Validate HTML content (basic validation)
        if (!isValidHtml(template.getHtmlContent())) {
            throw new NotificationException("Invalid HTML content in template");
        }

        // Validate variables syntax
        if (!areVariablesValid(template.getHtmlContent()) || !areVariablesValid(template.getSubject())) {
            throw new NotificationException("Invalid variable syntax in template");
        }
    }

    private List<String> extractVariables(NotificationTemplate template) {
        Set<String> variables = new HashSet<>();
        
        variables.addAll(extractVariableNames(template.getSubject()));
        variables.addAll(extractVariableNames(template.getHtmlContent()));
        
        if (template.getTextContent() != null) {
            variables.addAll(extractVariableNames(template.getTextContent()));
        }

        return new ArrayList<>(variables);
    }

    private List<String> extractVariableNames(String content) {
        List<String> variables = new ArrayList<>();
        
        if (content != null) {
            var matcher = VARIABLE_PATTERN.matcher(content);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
        }

        return variables;
    }

    public String processTemplate(String template, Map<String, Object> variables) {
        if (template == null) return "";

        String processed = template;
        
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            processed = processed.replace(placeholder, value);
        }

        return processed;
    }

    private NotificationTemplate convertToNotificationTemplate(EmailTemplate emailTemplate) {
        return NotificationTemplate.builder()
            .id(emailTemplate.getId())
            .name(emailTemplate.getName())
            .description(emailTemplate.getDescription())
            .subject(emailTemplate.getSubject())
            .htmlContent(emailTemplate.getHtmlContent())
            .textContent(emailTemplate.getTextContent())
            .category(emailTemplate.getCategory())
            .locale(emailTemplate.getLocale())
            .variables(emailTemplate.getVariables())
            .active(emailTemplate.isActive())
            .createdAt(emailTemplate.getCreatedAt())
            .updatedAt(emailTemplate.getUpdatedAt())
            .build();
    }

    private boolean matchesFilter(EmailTemplate template, TemplateFilter filter) {
        if (filter == null) return true;

        if (filter.getCategory() != null && !filter.getCategory().equals(template.getCategory())) {
            return false;
        }

        if (filter.getLocale() != null && !filter.getLocale().equals(template.getLocale())) {
            return false;
        }

        if (filter.getName() != null && !template.getName().toLowerCase().contains(filter.getName().toLowerCase())) {
            return false;
        }

        return true;
    }

    private boolean isValidHtml(String html) {
        try {
            // Basic HTML validation - in production, use a proper HTML parser
            return html.contains("<") && html.contains(">") && 
                   !html.contains("<script") && !html.contains("javascript:");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean areVariablesValid(String content) {
        if (content == null) return true;

        var matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!isValidVariableName(variable)) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidVariableName(String name) {
        return name != null && name.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }

    @CacheEvict(value = {"emailTemplates", "emailTemplatesList"}, allEntries = true)
    private void invalidateCache() {
        log.debug("Invalidating email template cache");
    }
}