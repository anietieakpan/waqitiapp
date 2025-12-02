package com.waqiti.notification.service;

import com.waqiti.notification.domain.NotificationTemplate;
import com.waqiti.notification.dto.NotificationTemplateRequest;
import com.waqiti.notification.dto.NotificationTemplateResponse;
import com.waqiti.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTemplateService {
    private final NotificationTemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationProviderService notificationProviderService;
    
    // Cache for compiled templates
    private final Map<String, Object> compiledTemplateCache = new ConcurrentHashMap<>();
    
    // Pattern for complex template expressions
    private final Pattern complexExpressionPattern = Pattern.compile("\\$\\{[^}]+\\}|\\{\\{[^}]+\\}\\}");
    
    // Provider failover configuration
    private final List<String> providerPriorityOrder = Arrays.asList("primary", "secondary", "tertiary");

    /**
     * Creates a new notification template
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public NotificationTemplateResponse createTemplate(NotificationTemplateRequest request) {
        log.info("Creating notification template with code: {}", request.getCode());

        // Check if code already exists
        if (templateRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Template with code already exists: " + request.getCode());
        }

        // Create the template
        NotificationTemplate template = NotificationTemplate.create(
                request.getCode(),
                request.getName(),
                request.getCategory(),
                request.getTitleTemplate(),
                request.getMessageTemplate()
        );

        // Set optional fields
        if (request.getEmailSubjectTemplate() != null) {
            template.setEmailTemplates(
                    request.getEmailSubjectTemplate(),
                    request.getEmailBodyTemplate()
            );
        }

        if (request.getSmsTemplate() != null) {
            template.setSmsTemplate(request.getSmsTemplate());
        }

        if (request.getActionUrlTemplate() != null) {
            template.setActionUrlTemplate(request.getActionUrlTemplate());
        }

        template.setEnabled(request.isEnabled());

        template = templateRepository.save(template);

        return mapToTemplateResponse(template);
    }

    /**
     * Updates an existing notification template
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public NotificationTemplateResponse updateTemplate(UUID id, NotificationTemplateRequest request) {
        log.info("Updating notification template with ID: {}", id);

        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with ID: " + id));

        // Update basic fields
        template.updateContent(request.getTitleTemplate(), request.getMessageTemplate());

        // Update optional fields
        if (request.getEmailSubjectTemplate() != null) {
            template.setEmailTemplates(
                    request.getEmailSubjectTemplate(),
                    request.getEmailBodyTemplate()
            );
        }

        if (request.getSmsTemplate() != null) {
            template.setSmsTemplate(request.getSmsTemplate());
        }

        if (request.getActionUrlTemplate() != null) {
            template.setActionUrlTemplate(request.getActionUrlTemplate());
        }

        template.setEnabled(request.isEnabled());

        template = templateRepository.save(template);

        return mapToTemplateResponse(template);
    }

    /**
     * Gets a template by ID
     */
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplateById(UUID id) {
        log.info("Getting notification template with ID: {}", id);

        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with ID: " + id));

        return mapToTemplateResponse(template);
    }

    /**
     * Gets a template by code
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "templates", key = "#code")
    public NotificationTemplate getTemplateByCode(String code) {
        log.info("Getting notification template with code: {}", code);

        return templateRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with code: " + code));
    }

    /**
     * Gets all templates
     */
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> getAllTemplates() {
        log.info("Getting all notification templates");

        List<NotificationTemplate> templates = templateRepository.findAll();

        return templates.stream()
                .map(this::mapToTemplateResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets templates by category
     */
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> getTemplatesByCategory(String category) {
        log.info("Getting notification templates for category: {}", category);

        List<NotificationTemplate> templates = templateRepository.findByCategory(category);

        return templates.stream()
                .map(this::mapToTemplateResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all templates with pagination and filtering - PERFORMANCE OPTIMIZED
     */
    @Transactional(readOnly = true)
    public Page<NotificationTemplateResponse> getAllTemplatesPaginated(String category, Boolean enabled, Pageable pageable) {
        log.info("Getting notification templates with pagination - category: {}, enabled: {}, page: {}, size: {}",
                 category, enabled, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<NotificationTemplate> templates;
        if (category != null && enabled != null) {
            templates = templateRepository.findByCategoryAndEnabled(category, enabled, pageable);
        } else if (category != null) {
            templates = templateRepository.findByCategory(category, pageable);
        } else if (enabled != null) {
            templates = templateRepository.findByEnabled(enabled, pageable);
        } else {
            templates = templateRepository.findAll(pageable);
        }
        
        return templates.map(this::mapToTemplateResponse);
    }
    
    /**
     * Gets templates by category with pagination and filtering - PERFORMANCE OPTIMIZED
     */
    @Transactional(readOnly = true)
    public Page<NotificationTemplateResponse> getTemplatesByCategory(String category, Boolean enabled, Pageable pageable) {
        log.info("Getting notification templates for category: {}, enabled: {}, page: {}, size: {}",
                 category, enabled, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<NotificationTemplate> templates;
        if (enabled != null) {
            templates = templateRepository.findByCategoryAndEnabled(category, enabled, pageable);
        } else {
            templates = templateRepository.findByCategory(category, pageable);
        }
        
        return templates.map(this::mapToTemplateResponse);
    }

    /**
     * Enables or disables a template
     */
    @Transactional
    @CacheEvict(value = "templates", allEntries = true)
    public NotificationTemplateResponse setTemplateEnabled(UUID id, boolean enabled) {
        log.info("Setting template {} to enabled={}", id, enabled);

        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found with ID: " + id));

        template.setEnabled(enabled);
        template = templateRepository.save(template);

        return mapToTemplateResponse(template);
    }

    /**
     * Renders a template with the given parameters
     */
    public String renderTemplate(String template, Object model) {
        return templateRenderer.renderTemplate(template, model);
    }

    // GROUP 2: Notification Template Methods Implementation

    /**
     * 1. Advanced template rendering
     * Renders complex templates with nested expressions, conditional logic, and loops
     */
    public ComplexTemplateRenderResult renderComplexTemplate(ComplexTemplateRequest request) {
        try {
            log.info("Rendering complex template: templateCode={}, variables={}", 
                    request.getTemplateCode(), request.getVariables().keySet());

            // Get template
            NotificationTemplate template = getTemplateByCode(request.getTemplateCode());
            
            // Validate template complexity
            validateComplexTemplate(template, request);
            
            // Prepare rendering context with enhanced capabilities
            Map<String, Object> enhancedContext = prepareEnhancedContext(request.getVariables(), request.getUser());
            
            // Render different template parts
            String renderedTitle = renderWithComplexLogic(template.getTitleTemplate(), enhancedContext);
            String renderedMessage = renderWithComplexLogic(template.getMessageTemplate(), enhancedContext);
            
            Map<String, String> renderedParts = new HashMap<>();
            renderedParts.put("title", renderedTitle);
            renderedParts.put("message", renderedMessage);
            
            // Render optional parts
            if (template.getEmailSubjectTemplate() != null) {
                renderedParts.put("emailSubject", renderWithComplexLogic(template.getEmailSubjectTemplate(), enhancedContext));
            }
            
            if (template.getEmailBodyTemplate() != null) {
                renderedParts.put("emailBody", renderWithComplexLogic(template.getEmailBodyTemplate(), enhancedContext));
            }
            
            if (template.getSmsTemplate() != null) {
                renderedParts.put("sms", renderWithComplexLogic(template.getSmsTemplate(), enhancedContext));
            }
            
            if (template.getActionUrlTemplate() != null) {
                renderedParts.put("actionUrl", renderWithComplexLogic(template.getActionUrlTemplate(), enhancedContext));
            }

            // Perform post-rendering validation
            validateRenderedContent(renderedParts, request);
            
            // Track rendering metrics
            recordRenderingMetrics(request.getTemplateCode(), enhancedContext.size(), 
                    System.currentTimeMillis() - request.getStartTime());

            log.info("Complex template rendering completed: templateCode={}, renderedParts={}", 
                    request.getTemplateCode(), renderedParts.keySet());

            return ComplexTemplateRenderResult.builder()
                .templateCode(request.getTemplateCode())
                .renderedParts(renderedParts)
                .renderingSuccessful(true)
                .usedVariables(extractUsedVariables(template, enhancedContext))
                .renderingTimeMs(System.currentTimeMillis() - request.getStartTime())
                .complexityScore(calculateComplexityScore(template))
                .renderedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Complex template rendering failed: templateCode={}", request.getTemplateCode(), e);
            return ComplexTemplateRenderResult.builder()
                .templateCode(request.getTemplateCode())
                .renderingSuccessful(false)
                .errorMessage("Rendering error: " + e.getMessage())
                .renderedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 2. Multi-language template
     * Renders templates in multiple languages based on user locale
     */
    public LocalizedTemplateRenderResult renderLocalizedTemplate(LocalizedTemplateRequest request) {
        try {
            log.info("Rendering localized template: templateCode={}, locale={}, fallbackLocale={}", 
                    request.getTemplateCode(), request.getLocale(), request.getFallbackLocale());

            // Get localized template (try requested locale first, then fallback)
            NotificationTemplate template = getLocalizedTemplate(request.getTemplateCode(), 
                    request.getLocale(), request.getFallbackLocale());
            
            // Prepare localized context
            Map<String, Object> localizedContext = prepareLocalizedContext(
                    request.getVariables(), request.getLocale(), request.getUser());
            
            // Add locale-specific helpers
            addLocaleHelpers(localizedContext, request.getLocale());
            
            // Render template parts with localization
            Map<String, String> renderedParts = renderAllTemplateParts(template, localizedContext);
            
            // Apply locale-specific formatting
            applyLocaleFormatting(renderedParts, request.getLocale());
            
            // Validate localized content
            validateLocalizedContent(renderedParts, request.getLocale());

            log.info("Localized template rendering completed: templateCode={}, locale={}, parts={}", 
                    request.getTemplateCode(), request.getLocale(), renderedParts.keySet());

            return LocalizedTemplateRenderResult.builder()
                .templateCode(request.getTemplateCode())
                .requestedLocale(request.getLocale())
                .actualLocale(template.getLocale())
                .fallbackUsed(!request.getLocale().equals(template.getLocale()))
                .renderedParts(renderedParts)
                .renderingSuccessful(true)
                .localizationMetadata(buildLocalizationMetadata(template, request.getLocale()))
                .renderedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Localized template rendering failed: templateCode={}, locale={}", 
                    request.getTemplateCode(), request.getLocale(), e);
            return LocalizedTemplateRenderResult.builder()
                .templateCode(request.getTemplateCode())
                .requestedLocale(request.getLocale())
                .renderingSuccessful(false)
                .errorMessage("Localized rendering error: " + e.getMessage())
                .renderedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 3. Dynamic content injection
     * Injects dynamic content into templates based on user context and real-time data
     */
    public DynamicContentResult injectDynamicContent(DynamicContentRequest request) {
        try {
            log.info("Injecting dynamic content: templateCode={}, contentSources={}, userId={}", 
                    request.getTemplateCode(), request.getContentSources(), request.getUserId());

            // Get base template
            NotificationTemplate template = getTemplateByCode(request.getTemplateCode());
            
            // Fetch dynamic content from various sources
            Map<String, Object> dynamicContent = fetchDynamicContent(request.getContentSources(), request.getUserId());
            
            // Merge with static variables
            Map<String, Object> mergedContext = new HashMap<>(request.getStaticVariables());
            mergedContext.putAll(dynamicContent);
            
            // Apply real-time data enrichment
            enrichWithRealTimeData(mergedContext, request.getUserId(), request.getContextType());
            
            // Inject personalization data
            injectPersonalizationData(mergedContext, request.getUserId(), request.getPersonalizationLevel());
            
            // Apply content filtering based on user preferences
            applyContentFiltering(mergedContext, request.getUserId(), request.getContentFilters());
            
            // Render template with dynamic content
            Map<String, String> renderedParts = renderAllTemplateParts(template, mergedContext);
            
            // Post-process dynamic content
            postProcessDynamicContent(renderedParts, request.getPostProcessingRules());
            
            // Track content injection metrics
            trackDynamicContentMetrics(request.getTemplateCode(), request.getContentSources(), 
                    dynamicContent.size(), request.getUserId());

            log.info("Dynamic content injection completed: templateCode={}, injectedSources={}, userId={}", 
                    request.getTemplateCode(), dynamicContent.keySet(), request.getUserId());

            return DynamicContentResult.builder()
                .templateCode(request.getTemplateCode())
                .userId(request.getUserId())
                .renderedParts(renderedParts)
                .injectedContent(dynamicContent)
                .contentSources(request.getContentSources())
                .injectionSuccessful(true)
                .personalizationLevel(request.getPersonalizationLevel())
                .realTimeDataIncluded(containsRealTimeData(dynamicContent))
                .injectedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Dynamic content injection failed: templateCode={}, userId={}", 
                    request.getTemplateCode(), request.getUserId(), e);
            return DynamicContentResult.builder()
                .templateCode(request.getTemplateCode())
                .userId(request.getUserId())
                .injectionSuccessful(false)
                .errorMessage("Dynamic content injection error: " + e.getMessage())
                .injectedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 4. Template caching
     * Caches compiled templates for improved performance
     */
    @CachePut(value = "compiled_templates", key = "#templateCode + '_' + #version")
    public CompiledTemplate cacheCompiledTemplate(String templateCode, String version, 
                                                 CompilationOptions options) {
        try {
            log.info("Compiling and caching template: templateCode={}, version={}, options={}", 
                    templateCode, version, options);

            // Get template
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Validate template before compilation
            validateTemplateForCompilation(template, options);
            
            // Compile template parts
            Map<String, Object> compiledParts = compileTemplateParts(template, options);
            
            // Create compilation metadata
            CompilationMetadata metadata = buildCompilationMetadata(template, options);
            
            // Create compiled template
            CompiledTemplate compiledTemplate = CompiledTemplate.builder()
                .templateCode(templateCode)
                .version(version)
                .originalTemplate(template)
                .compiledParts(compiledParts)
                .compilationOptions(options)
                .metadata(metadata)
                .compiledAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(options.getCacheDuration()))
                .compilationHash(calculateCompilationHash(template, options))
                .build();
            
            // Store in local cache for fast access
            compiledTemplateCache.put(templateCode + "_" + version, compiledTemplate);
            
            // Store compilation statistics
            updateCompilationStatistics(templateCode, metadata);
            
            // Schedule cache cleanup if needed
            scheduleCompiledTemplateCacheCleanup();

            log.info("Template compiled and cached successfully: templateCode={}, version={}, size={}", 
                    templateCode, version, compiledParts.size());

            return compiledTemplate;

        } catch (Exception e) {
            log.error("Template compilation failed: templateCode={}, version={}", templateCode, version, e);
            throw new TemplateCompilationException("Failed to compile template: " + e.getMessage(), e);
        }
    }

    /**
     * 5. Provider failover
     * Implements failover mechanism when primary notification providers fail
     */
    public ProviderFailoverResult failoverToBackupProvider(FailoverRequest request) {
        try {
            log.info("Starting provider failover: templateCode={}, failedProvider={}, availableProviders={}", 
                    request.getTemplateCode(), request.getFailedProvider(), request.getAvailableProviders());

            // Determine next best provider
            String nextProvider = determineNextProvider(request.getFailedProvider(), 
                    request.getAvailableProviders(), request.getProviderPreferences());
            
            if (nextProvider == null) {
                log.error("No backup providers available for failover: templateCode={}", request.getTemplateCode());
                return ProviderFailoverResult.builder()
                    .originalProvider(request.getFailedProvider())
                    .failoverSuccessful(false)
                    .errorMessage("No backup providers available")
                    .attemptedAt(LocalDateTime.now())
                    .build();
            }
            
            // Adapt template for backup provider
            ProviderAdaptation adaptation = adaptTemplateForProvider(request.getTemplateCode(), 
                    nextProvider, request.getNotificationContent());
            
            // Test provider connectivity
            if (!testProviderConnectivity(nextProvider)) {
                log.warn("Backup provider connectivity test failed, trying next: {}", nextProvider);
                // Recursively try next provider
                request.getAvailableProviders().remove(nextProvider);
                return failoverToBackupProvider(request);
            }
            
            // Send test notification to validate provider
            boolean testResult = sendTestNotification(nextProvider, adaptation.getAdaptedContent());
            
            if (!testResult) {
                log.warn("Test notification failed on backup provider, trying next: {}", nextProvider);
                request.getAvailableProviders().remove(nextProvider);
                return failoverToBackupProvider(request);
            }
            
            // Update provider status
            updateProviderStatus(request.getFailedProvider(), "FAILED");
            updateProviderStatus(nextProvider, "ACTIVE");
            
            // Log failover event
            logFailoverEvent(request.getFailedProvider(), nextProvider, request.getTemplateCode());
            
            // Update monitoring metrics
            updateFailoverMetrics(request.getFailedProvider(), nextProvider, true);

            log.info("Provider failover successful: failed={}, backup={}, templateCode={}", 
                    request.getFailedProvider(), nextProvider, request.getTemplateCode());

            return ProviderFailoverResult.builder()
                .originalProvider(request.getFailedProvider())
                .backupProvider(nextProvider)
                .adaptation(adaptation)
                .failoverSuccessful(true)
                .testNotificationSent(testResult)
                .failoverTimeMs(System.currentTimeMillis() - request.getStartTime())
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Provider failover failed: failedProvider={}, templateCode={}", 
                    request.getFailedProvider(), request.getTemplateCode(), e);
            updateFailoverMetrics(request.getFailedProvider(), null, false);
            return ProviderFailoverResult.builder()
                .originalProvider(request.getFailedProvider())
                .failoverSuccessful(false)
                .errorMessage("Failover error: " + e.getMessage())
                .attemptedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 6. Batch notification sending
     * Sends notifications in batches with optimization and error handling
     */
    public BatchNotificationResult sendBatchNotifications(BatchNotificationRequest request) {
        try {
            log.info("Starting batch notification sending: templateCode={}, batchSize={}, recipientCount={}", 
                    request.getTemplateCode(), request.getBatchSize(), request.getRecipients().size());

            // Validate batch request
            validateBatchRequest(request);
            
            // Get and compile template
            NotificationTemplate template = getTemplateByCode(request.getTemplateCode());
            CompiledTemplate compiledTemplate = getOrCompileTemplate(template, request.getCompilationOptions());
            
            // Split recipients into batches
            List<List<NotificationRecipient>> batches = partitionRecipients(request.getRecipients(), 
                    request.getBatchSize());
            
            // Initialize results tracking
            List<BatchNotificationResult.BatchResult> batchResults = new ArrayList<>();
            int totalSuccessful = 0;
            int totalFailed = 0;
            
            // Process batches
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<NotificationRecipient> batch = batches.get(batchIndex);
                
                try {
                    log.debug("Processing batch {}/{}: size={}", batchIndex + 1, batches.size(), batch.size());
                    
                    // Process batch with rate limiting
                    BatchResult batchResult = processBatch(batch, compiledTemplate, request.getSendingOptions());
                    
                    batchResults.add(batchResult);
                    totalSuccessful += batchResult.getSuccessfulCount();
                    totalFailed += batchResult.getFailedCount();
                    
                    // Apply rate limiting between batches
                    if (request.getBatchDelayMs() > 0 && batchIndex < batches.size() - 1) {
                        Thread.sleep(request.getBatchDelayMs());
                    }
                    
                } catch (Exception e) {
                    log.error("Batch processing failed: batchIndex={}, size={}", batchIndex, batch.size(), e);
                    BatchResult failedBatch = BatchResult.builder()
                        .batchIndex(batchIndex)
                        .batchSize(batch.size())
                        .successfulCount(0)
                        .failedCount(batch.size())
                        .errorMessage("Batch processing error: " + e.getMessage())
                        .processedAt(LocalDateTime.now())
                        .build();
                    batchResults.add(failedBatch);
                    totalFailed += batch.size();
                }
            }
            
            // Generate final report
            BatchNotificationReport report = generateBatchReport(request, batchResults, 
                    totalSuccessful, totalFailed);
            
            // Update metrics
            updateBatchMetrics(request.getTemplateCode(), request.getRecipients().size(), 
                    totalSuccessful, totalFailed);

            log.info("Batch notification sending completed: templateCode={}, total={}, successful={}, failed={}", 
                    request.getTemplateCode(), request.getRecipients().size(), totalSuccessful, totalFailed);

            return BatchNotificationResult.builder()
                .templateCode(request.getTemplateCode())
                .totalRecipients(request.getRecipients().size())
                .totalBatches(batches.size())
                .successfulNotifications(totalSuccessful)
                .failedNotifications(totalFailed)
                .batchResults(batchResults)
                .batchingSuccessful(true)
                .report(report)
                .processingTimeMs(System.currentTimeMillis() - request.getStartTime())
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Batch notification sending failed: templateCode={}", request.getTemplateCode(), e);
            return BatchNotificationResult.builder()
                .templateCode(request.getTemplateCode())
                .totalRecipients(request.getRecipients() != null ? request.getRecipients().size() : 0)
                .batchingSuccessful(false)
                .errorMessage("Batch sending error: " + e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * 7. Template versioning
     * Manages template versions and retrieves specific versions
     */
    public TemplateVersionInfo getTemplateVersion(String templateCode, String version) {
        try {
            log.info("Getting template version: templateCode={}, version={}", templateCode, version);

            // Get template version from repository or cache
            NotificationTemplate template = getTemplateVersion(templateCode, version, true);
            
            // Get version history
            List<TemplateVersionHistory> versionHistory = getTemplateVersionHistory(templateCode);
            
            // Get current active version
            String activeVersion = getCurrentActiveVersion(templateCode);
            
            // Calculate version differences if previous version exists
            VersionComparison comparison = null;
            if (versionHistory.size() > 1) {
                comparison = compareWithPreviousVersion(template, templateCode, version);
            }
            
            // Get version usage statistics
            VersionUsageStats usageStats = getVersionUsageStats(templateCode, version);
            
            // Check if version is deprecated
            boolean isDeprecated = isVersionDeprecated(templateCode, version);
            
            // Get version metadata
            VersionMetadata metadata = getVersionMetadata(templateCode, version);

            log.info("Template version retrieved: templateCode={}, version={}, active={}, deprecated={}", 
                    templateCode, version, version.equals(activeVersion), isDeprecated);

            return TemplateVersionInfo.builder()
                .templateCode(templateCode)
                .version(version)
                .template(template)
                .isActiveVersion(version.equals(activeVersion))
                .isDeprecated(isDeprecated)
                .versionHistory(versionHistory)
                .comparison(comparison)
                .usageStats(usageStats)
                .metadata(metadata)
                .retrievedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to get template version: templateCode={}, version={}", templateCode, version, e);
            return TemplateVersionInfo.builder()
                .templateCode(templateCode)
                .version(version)
                .errorMessage("Version retrieval error: " + e.getMessage())
                .retrievedAt(LocalDateTime.now())
                .build();
        }
    }

    // Helper methods for the implemented functions

    private void validateComplexTemplate(NotificationTemplate template, ComplexTemplateRequest request) {
        // Validate template complexity and requirements
        if (template.getTitleTemplate() == null || template.getMessageTemplate() == null) {
            throw new IllegalArgumentException("Template must have title and message parts");
        }
        
        // Check for circular references in template variables
        detectCircularReferences(template, request.getVariables());
    }

    private Map<String, Object> prepareEnhancedContext(Map<String, Object> variables, UserContext user) {
        Map<String, Object> context = new HashMap<>(variables);
        
        // Add user-specific helpers
        context.put("user", user);
        context.put("currentTime", LocalDateTime.now());
        context.put("formatters", createFormatterHelpers());
        context.put("conditionals", createConditionalHelpers());
        context.put("loops", createLoopHelpers());
        
        return context;
    }

    private String renderWithComplexLogic(String templateText, Map<String, Object> context) {
        // Enhanced template renderer that supports complex expressions
        return templateRenderer.renderAdvanced(templateText, context);
    }

    private void validateRenderedContent(Map<String, String> renderedParts, ComplexTemplateRequest request) {
        for (Map.Entry<String, String> entry : renderedParts.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                throw new TemplateRenderingException("Rendered content is empty for part: " + entry.getKey());
            }
        }
    }

    private void recordRenderingMetrics(String templateCode, int variableCount, long renderingTimeMs) {
        // Record metrics for monitoring and optimization
        log.debug("Recording rendering metrics: template={}, variables={}, time={}ms", 
                templateCode, variableCount, renderingTimeMs);
    }

    private Set<String> extractUsedVariables(NotificationTemplate template, Map<String, Object> context) {
        Set<String> usedVars = new HashSet<>();
        String allTemplateText = template.getTitleTemplate() + " " + template.getMessageTemplate();
        
        for (String varName : context.keySet()) {
            if (allTemplateText.contains("${" + varName + "}") || 
                allTemplateText.contains("{{" + varName + "}}")) {
                usedVars.add(varName);
            }
        }
        
        return usedVars;
    }

    private int calculateComplexityScore(NotificationTemplate template) {
        // Calculate complexity based on template features used
        int score = 0;
        String allText = (template.getTitleTemplate() + " " + template.getMessageTemplate()).toLowerCase();
        
        if (allText.contains("if")) score += 2;
        if (allText.contains("for") || allText.contains("each")) score += 3;
        if (allText.contains("switch") || allText.contains("case")) score += 2;
        if (complexExpressionPattern.matcher(allText).find()) score += 1;
        
        return score;
    }

    private NotificationTemplate getLocalizedTemplate(String templateCode, Locale locale, Locale fallbackLocale) {
        // Try to get localized version first
        Optional<NotificationTemplate> localizedTemplate = templateRepository.findByCodeAndLocale(templateCode, locale);
        
        if (localizedTemplate.isPresent()) {
            return localizedTemplate.get();
        }
        
        // Try fallback locale
        if (fallbackLocale != null) {
            localizedTemplate = templateRepository.findByCodeAndLocale(templateCode, fallbackLocale);
            if (localizedTemplate.isPresent()) {
                return localizedTemplate.get();
            }
        }
        
        // Fall back to default template
        return getTemplateByCode(templateCode);
    }

    private void detectCircularReferences(NotificationTemplate template, Map<String, Object> variables) {
        // Implementation to detect circular variable references
        // This is a simplified version - real implementation would be more sophisticated
        Set<String> visited = new HashSet<>();
        String templateText = template.getTitleTemplate() + " " + template.getMessageTemplate();
        
        for (String varName : variables.keySet()) {
            if (visited.contains(varName)) {
                continue;
            }
            checkCircularReference(varName, variables, templateText, visited, new HashSet<>());
        }
    }

    private void checkCircularReference(String varName, Map<String, Object> variables, 
                                      String templateText, Set<String> visited, Set<String> currentPath) {
        if (currentPath.contains(varName)) {
            throw new TemplateRenderingException("Circular reference detected for variable: " + varName);
        }
        
        visited.add(varName);
        currentPath.add(varName);
        
        // Check if this variable references other variables
        Object value = variables.get(varName);
        if (value instanceof String) {
            String strValue = (String) value;
            for (String otherVar : variables.keySet()) {
                if (!otherVar.equals(varName) && strValue.contains("${" + otherVar + "}")) {
                    checkCircularReference(otherVar, variables, templateText, visited, new HashSet<>(currentPath));
                }
            }
        }
        
        currentPath.remove(varName);
    }

    private Object createFormatterHelpers() {
        return new Object(); // Placeholder for formatter helpers
    }

    private Object createConditionalHelpers() {
        return new Object(); // Placeholder for conditional helpers
    }

    private Object createLoopHelpers() {
        return new Object(); // Placeholder for loop helpers
    }

    // GROUP 3: Transaction Rollback Template Methods
    
    /**
     * Get rollback email template for transaction recipients
     */
    public RollbackEmailTemplate getRollbackEmailTemplate(Object transaction, String recipientType) {
        try {
            log.debug("Getting rollback email template for recipient type: {}", recipientType);
            
            // Get the appropriate template based on recipient type
            String templateCode = switch (recipientType.toLowerCase()) {
                case "sender" -> "TRANSACTION_ROLLBACK_SENDER_EMAIL";
                case "recipient" -> "TRANSACTION_ROLLBACK_RECIPIENT_EMAIL";
                case "beneficiary" -> "TRANSACTION_ROLLBACK_BENEFICIARY_EMAIL";
                default -> "TRANSACTION_ROLLBACK_GENERIC_EMAIL";
            };
            
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Prepare rollback-specific context
            Map<String, Object> rollbackContext = buildRollbackContext(transaction, recipientType);
            
            // Render email components
            String subject = renderTemplate(template.getEmailSubjectTemplate(), rollbackContext);
            String body = renderTemplate(template.getEmailBodyTemplate(), rollbackContext);
            String actionUrl = template.getActionUrlTemplate() != null ? 
                renderTemplate(template.getActionUrlTemplate(), rollbackContext) : null;
            
            return RollbackEmailTemplate.builder()
                .templateCode(templateCode)
                .recipientType(recipientType)
                .subject(subject)
                .body(body)
                .actionUrl(actionUrl)
                .priority(determinePriority(transaction, "EMAIL"))
                .deliveryOptions(Map.of(
                    "urgent", true,
                    "trackOpens", true,
                    "trackClicks", true
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get rollback email template for recipient type: {}", recipientType, e);
            return getDefaultRollbackEmailTemplate(recipientType);
        }
    }
    
    /**
     * Get rollback SMS template for transaction participants
     */
    public RollbackSMSTemplate getRollbackSMSTemplate(Object transaction, String recipientType) {
        try {
            log.debug("Getting rollback SMS template for recipient type: {}", recipientType);
            
            // Get the appropriate template based on recipient type
            String templateCode = switch (recipientType.toLowerCase()) {
                case "sender" -> "TRANSACTION_ROLLBACK_SENDER_SMS";
                case "recipient" -> "TRANSACTION_ROLLBACK_RECIPIENT_SMS";
                case "beneficiary" -> "TRANSACTION_ROLLBACK_BENEFICIARY_SMS";
                default -> "TRANSACTION_ROLLBACK_GENERIC_SMS";
            };
            
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Prepare rollback-specific context
            Map<String, Object> rollbackContext = buildRollbackContext(transaction, recipientType);
            
            // Render SMS message (must be concise for SMS limits)
            String message = renderTemplate(template.getSmsTemplate(), rollbackContext);
            
            // Ensure SMS length compliance
            message = truncateForSMS(message);
            
            return RollbackSMSTemplate.builder()
                .templateCode(templateCode)
                .recipientType(recipientType)
                .message(message)
                .priority(determinePriority(transaction, "SMS"))
                .deliveryOptions(Map.of(
                    "urgent", true,
                    "unicode", containsUnicode(message),
                    "shortCode", getShortCodeForRollback()
                ))
                .characterCount(message.length())
                .segmentCount(calculateSMSSegments(message))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get rollback SMS template for recipient type: {}", recipientType, e);
            return getDefaultRollbackSMSTemplate(recipientType);
        }
    }
    
    /**
     * Get merchant rollback email template for business notifications
     */
    public MerchantRollbackEmailTemplate getMerchantRollbackEmailTemplate(Object transaction) {
        try {
            log.debug("Getting merchant rollback email template for transaction");
            
            String templateCode = "MERCHANT_TRANSACTION_ROLLBACK_EMAIL";
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Prepare merchant-specific context
            Map<String, Object> merchantContext = buildMerchantRollbackContext(transaction);
            
            // Render merchant email components
            String subject = renderTemplate(template.getEmailSubjectTemplate(), merchantContext);
            String body = renderTemplate(template.getEmailBodyTemplate(), merchantContext);
            String actionUrl = template.getActionUrlTemplate() != null ? 
                renderTemplate(template.getActionUrlTemplate(), merchantContext) : null;
            
            // Get merchant-specific metadata
            MerchantNotificationMetadata metadata = buildMerchantMetadata(transaction);
            
            return MerchantRollbackEmailTemplate.builder()
                .templateCode(templateCode)
                .subject(subject)
                .body(body)
                .actionUrl(actionUrl)
                .merchantId(extractMerchantId(transaction))
                .priority(NotificationPriority.HIGH)
                .metadata(metadata)
                .complianceInfo(buildComplianceInfo(transaction))
                .deliveryOptions(Map.of(
                    "urgent", true,
                    "requireDeliveryConfirmation", true,
                    "businessHoursOnly", false,
                    "includeAttachments", true
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get merchant rollback email template", e);
            return getDefaultMerchantRollbackEmailTemplate();
        }
    }
    
    /**
     * Get push notification template for rollback alerts
     */
    public RollbackPushTemplate getRollbackPushTemplate(Object transaction, String recipientType) {
        try {
            log.debug("Getting rollback push template for recipient type: {}", recipientType);
            
            String templateCode = "TRANSACTION_ROLLBACK_PUSH_" + recipientType.toUpperCase();
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Prepare push notification context
            Map<String, Object> pushContext = buildRollbackContext(transaction, recipientType);
            
            // Render push notification components
            String title = renderTemplate(template.getTitleTemplate(), pushContext);
            String body = renderTemplate(template.getMessageTemplate(), pushContext);
            
            return RollbackPushTemplate.builder()
                .templateCode(templateCode)
                .title(title)
                .body(body)
                .recipientType(recipientType)
                .badge(1)
                .sound("urgent_alert.wav")
                .category("TRANSACTION_ROLLBACK")
                .priority(NotificationPriority.CRITICAL)
                .customData(Map.of(
                    "transactionId", extractTransactionId(transaction),
                    "rollbackReason", extractRollbackReason(transaction),
                    "actionRequired", determineActionRequired(transaction, recipientType)
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get rollback push template for recipient type: {}", recipientType, e);
            return getDefaultRollbackPushTemplate(recipientType);
        }
    }
    
    /**
     * Get in-app notification template for rollback updates
     */
    public RollbackInAppTemplate getRollbackInAppTemplate(Object transaction, String recipientType) {
        try {
            log.debug("Getting rollback in-app template for recipient type: {}", recipientType);
            
            String templateCode = "TRANSACTION_ROLLBACK_INAPP_" + recipientType.toUpperCase();
            NotificationTemplate template = getTemplateByCode(templateCode);
            
            // Prepare in-app notification context
            Map<String, Object> inAppContext = buildRollbackContext(transaction, recipientType);
            
            // Render in-app notification components
            String title = renderTemplate(template.getTitleTemplate(), inAppContext);
            String message = renderTemplate(template.getMessageTemplate(), inAppContext);
            String actionUrl = template.getActionUrlTemplate() != null ? 
                renderTemplate(template.getActionUrlTemplate(), inAppContext) : null;
            
            return RollbackInAppTemplate.builder()
                .templateCode(templateCode)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .recipientType(recipientType)
                .displayDuration(30000) // 30 seconds
                .persistent(true)
                .priority(NotificationPriority.HIGH)
                .styling(Map.of(
                    "theme", "urgent",
                    "iconType", "warning",
                    "backgroundColor", "#FF6B6B",
                    "textColor", "#FFFFFF"
                ))
                .actions(buildInAppActions(transaction, recipientType))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get rollback in-app template for recipient type: {}", recipientType, e);
            return getDefaultRollbackInAppTemplate(recipientType);
        }
    }
    
    // Helper methods for rollback templates
    
    private Map<String, Object> buildRollbackContext(Object transaction, String recipientType) {
        Map<String, Object> context = new HashMap<>();
        
        // Extract transaction details using reflection or casting
        context.put("transactionId", extractTransactionId(transaction));
        context.put("amount", extractAmount(transaction));
        context.put("currency", extractCurrency(transaction));
        context.put("recipientType", recipientType);
        context.put("rollbackReason", extractRollbackReason(transaction));
        context.put("rollbackTime", LocalDateTime.now());
        context.put("supportContact", getSupportContactForRollback());
        context.put("refundTimeline", getRefundTimeline(transaction));
        
        // Add recipient-specific context
        if ("sender".equals(recipientType)) {
            context.put("refundAccount", extractSenderAccount(transaction));
            context.put("originalPaymentMethod", extractPaymentMethod(transaction));
        } else if ("recipient".equals(recipientType)) {
            context.put("expectedAmount", extractAmount(transaction));
            context.put("alternativeOptions", getAlternativeOptions(transaction));
        }
        
        return context;
    }
    
    private Map<String, Object> buildMerchantRollbackContext(Object transaction) {
        Map<String, Object> context = buildRollbackContext(transaction, "merchant");
        
        // Add merchant-specific information
        context.put("merchantId", extractMerchantId(transaction));
        context.put("merchantName", extractMerchantName(transaction));
        context.put("settlementImpact", calculateSettlementImpact(transaction));
        context.put("reportingPeriod", getCurrentReportingPeriod());
        context.put("complianceRequirements", getComplianceRequirements(transaction));
        
        return context;
    }
    
    private String determinePriority(Object transaction, String channel) {
        // Determine notification priority based on transaction attributes
        BigDecimal amount = extractAmount(transaction);
        String rollbackReason = extractRollbackReason(transaction);
        
        if (amount.compareTo(new BigDecimal("10000")) > 0 || 
            "FRAUD_DETECTED".equals(rollbackReason)) {
            return "CRITICAL";
        } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
            return "HIGH";
        } else {
            return "NORMAL";
        }
    }
    
    private String truncateForSMS(String message) {
        // SMS limit is 160 characters for single segment
        if (message.length() <= 160) {
            return message;
        }
        
        // Truncate and add ellipsis
        return message.substring(0, 157) + "...";
    }
    
    private boolean containsUnicode(String message) {
        return message.chars().anyMatch(c -> c > 127);
    }
    
    private String getShortCodeForRollback() {
        return "WAQITI"; // 6-character short code for urgent notifications
    }
    
    private int calculateSMSSegments(String message) {
        if (containsUnicode(message)) {
            return (int) Math.ceil(message.length() / 70.0); // Unicode SMS limit
        } else {
            return (int) Math.ceil(message.length() / 160.0); // Standard SMS limit
        }
    }
    
    private MerchantNotificationMetadata buildMerchantMetadata(Object transaction) {
        return MerchantNotificationMetadata.builder()
            .merchantId(extractMerchantId(transaction))
            .transactionType(extractTransactionType(transaction))
            .riskLevel(extractRiskLevel(transaction))
            .complianceFlags(extractComplianceFlags(transaction))
            .reportingRequired(true)
            .build();
    }
    
    private ComplianceInfo buildComplianceInfo(Object transaction) {
        return ComplianceInfo.builder()
            .regulatoryRequirements(List.of("PCI-DSS", "GDPR", "AML"))
            .retentionPeriod("7_YEARS")
            .dataClassification("CONFIDENTIAL")
            .auditRequired(true)
            .build();
    }
    
    private List<InAppAction> buildInAppActions(Object transaction, String recipientType) {
        List<InAppAction> actions = new ArrayList<>();
        
        actions.add(InAppAction.builder()
            .actionId("view_details")
            .label("View Details")
            .actionType("NAVIGATE")
            .destination("/transactions/" + extractTransactionId(transaction))
            .build());
            
        actions.add(InAppAction.builder()
            .actionId("contact_support")
            .label("Contact Support")
            .actionType("EXTERNAL")
            .destination("mailto:support@example.com")
            .build());
            
        if ("sender".equals(recipientType)) {
            actions.add(InAppAction.builder()
                .actionId("track_refund")
                .label("Track Refund")
                .actionType("NAVIGATE")
                .destination("/refunds/track")
                .build());
        }
        
        return actions;
    }
    
    // Default template methods
    
    private RollbackEmailTemplate getDefaultRollbackEmailTemplate(String recipientType) {
        return RollbackEmailTemplate.builder()
            .templateCode("DEFAULT_ROLLBACK_EMAIL")
            .recipientType(recipientType)
            .subject("Transaction Update - Action Required")
            .body("Your recent transaction has been updated. Please check your account for details.")
            .priority("NORMAL")
            .deliveryOptions(Map.of("urgent", false))
            .build();
    }
    
    private RollbackSMSTemplate getDefaultRollbackSMSTemplate(String recipientType) {
        return RollbackSMSTemplate.builder()
            .templateCode("DEFAULT_ROLLBACK_SMS")
            .recipientType(recipientType)
            .message("Transaction update: Please check your Waqiti app for details.")
            .priority("NORMAL")
            .characterCount(62)
            .segmentCount(1)
            .build();
    }
    
    private MerchantRollbackEmailTemplate getDefaultMerchantRollbackEmailTemplate() {
        return MerchantRollbackEmailTemplate.builder()
            .templateCode("DEFAULT_MERCHANT_ROLLBACK")
            .subject("Transaction Rollback Notification")
            .body("A transaction rollback has occurred. Please review your merchant dashboard.")
            .priority(NotificationPriority.NORMAL)
            .deliveryOptions(Map.of("urgent", false))
            .build();
    }
    
    private RollbackPushTemplate getDefaultRollbackPushTemplate(String recipientType) {
        return RollbackPushTemplate.builder()
            .templateCode("DEFAULT_ROLLBACK_PUSH")
            .title("Transaction Update")
            .body("Your transaction status has changed")
            .recipientType(recipientType)
            .priority(NotificationPriority.NORMAL)
            .build();
    }
    
    private RollbackInAppTemplate getDefaultRollbackInAppTemplate(String recipientType) {
        return RollbackInAppTemplate.builder()
            .templateCode("DEFAULT_ROLLBACK_INAPP")
            .title("Transaction Update")
            .message("Your transaction status has been updated")
            .recipientType(recipientType)
            .priority(NotificationPriority.NORMAL)
            .build();
    }
    
    // Extraction methods (would use proper transaction object in production)
    
    private String extractTransactionId(Object transaction) {
        // In production, would properly cast and extract
        return "TXN_" + System.currentTimeMillis();
    }
    
    private BigDecimal extractAmount(Object transaction) {
        // In production, would properly cast and extract
        return new BigDecimal("100.00");
    }
    
    private String extractCurrency(Object transaction) {
        return "USD";
    }
    
    private String extractRollbackReason(Object transaction) {
        return "USER_REQUESTED";
    }
    
    private String extractMerchantId(Object transaction) {
        return "MERCHANT_123";
    }
    
    private String extractMerchantName(Object transaction) {
        return "Example Merchant";
    }
    
    private String extractSenderAccount(Object transaction) {
        return "****1234";
    }
    
    private String extractPaymentMethod(Object transaction) {
        return "Credit Card";
    }
    
    private String getSupportContactForRollback() {
        return "support@example.com";
    }
    
    private String getRefundTimeline(Object transaction) {
        return "3-5 business days";
    }
    
    private List<String> getAlternativeOptions(Object transaction) {
        return List.of("Retry payment", "Contact merchant", "Use different payment method");
    }
    
    private String calculateSettlementImpact(Object transaction) {
        return "Settlement will be adjusted in next cycle";
    }
    
    private String getCurrentReportingPeriod() {
        return LocalDateTime.now().getMonth() + " " + LocalDateTime.now().getYear();
    }
    
    private List<String> getComplianceRequirements(Object transaction) {
        return List.of("Transaction must be reported to regulatory authorities");
    }
    
    private String extractTransactionType(Object transaction) {
        return "PAYMENT";
    }
    
    private String extractRiskLevel(Object transaction) {
        return "MEDIUM";
    }
    
    private List<String> extractComplianceFlags(Object transaction) {
        return List.of("HIGH_VALUE");
    }
    
    private boolean determineActionRequired(Object transaction, String recipientType) {
        return "sender".equals(recipientType);
    }

    /**
     * Maps a NotificationTemplate entity to a NotificationTemplateResponse DTO
     */
    private NotificationTemplateResponse mapToTemplateResponse(NotificationTemplate template) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .code(template.getCode())
                .name(template.getName())
                .category(template.getCategory())
                .titleTemplate(template.getTitleTemplate())
                .messageTemplate(template.getMessageTemplate())
                .emailSubjectTemplate(template.getEmailSubjectTemplate())
                .emailBodyTemplate(template.getEmailBodyTemplate())
                .smsTemplate(template.getSmsTemplate())
                .actionUrlTemplate(template.getActionUrlTemplate())
                .enabled(template.isEnabled())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
    
    // Supporting DTOs for rollback templates
    
    public enum NotificationPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
    
    @lombok.Builder
    @lombok.Data
    public static class RollbackEmailTemplate {
        private String templateCode;
        private String recipientType;
        private String subject;
        private String body;
        private String actionUrl;
        private String priority;
        private Map<String, Object> deliveryOptions;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class RollbackSMSTemplate {
        private String templateCode;
        private String recipientType;
        private String message;
        private String priority;
        private Map<String, Object> deliveryOptions;
        private int characterCount;
        private int segmentCount;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class MerchantRollbackEmailTemplate {
        private String templateCode;
        private String subject;
        private String body;
        private String actionUrl;
        private String merchantId;
        private NotificationPriority priority;
        private MerchantNotificationMetadata metadata;
        private ComplianceInfo complianceInfo;
        private Map<String, Object> deliveryOptions;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class RollbackPushTemplate {
        private String templateCode;
        private String title;
        private String body;
        private String recipientType;
        private int badge;
        private String sound;
        private String category;
        private NotificationPriority priority;
        private Map<String, Object> customData;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class RollbackInAppTemplate {
        private String templateCode;
        private String title;
        private String message;
        private String actionUrl;
        private String recipientType;
        private int displayDuration;
        private boolean persistent;
        private NotificationPriority priority;
        private Map<String, Object> styling;
        private List<InAppAction> actions;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class MerchantNotificationMetadata {
        private String merchantId;
        private String transactionType;
        private String riskLevel;
        private List<String> complianceFlags;
        private boolean reportingRequired;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class ComplianceInfo {
        private List<String> regulatoryRequirements;
        private String retentionPeriod;
        private String dataClassification;
        private boolean auditRequired;
    }
    
    @lombok.Builder
    @lombok.Data
    public static class InAppAction {
        private String actionId;
        private String label;
        private String actionType; // NAVIGATE, EXTERNAL, CALLBACK
        private String destination;
    }
}