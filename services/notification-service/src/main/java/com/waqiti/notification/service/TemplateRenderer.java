package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateRenderer {
    private final ITemplateEngine templateEngine;

    /**
     * Renders a template string with the given model
     */
    public String renderTemplate(String template, Object model) {
        try {
            Context context = new Context(Locale.getDefault());

            if (model instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> modelMap = (Map<String, Object>) model;
                modelMap.forEach(context::setVariable);
            } else {
                context.setVariable("model", model);
            }

            // Wrap the template in Thymeleaf processing tags
            String wrappedTemplate = "<div th:remove=\"tag\">" + template + "</div>";

            return templateEngine.process(wrappedTemplate, context);
        } catch (Exception e) {
            log.error("Error rendering template: {}", template, e);
            // Return the original template in case of an error
            return template;
        }
    }
}