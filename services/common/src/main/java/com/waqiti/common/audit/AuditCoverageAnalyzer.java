package com.waqiti.common.audit;

import com.waqiti.common.audit.annotation.Auditable;
import com.waqiti.common.audit.annotation.AuditLogged;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Audit Coverage Analyzer for Waqiti Platform
 * 
 * Analyzes the current audit coverage across all services to identify gaps
 * and ensure compliance with regulatory requirements.
 * 
 * ANALYSIS FEATURES:
 * - Service-level audit coverage metrics
 * - Method-level audit status identification
 * - Compliance framework coverage mapping
 * - Gap analysis and recommendations
 * - Risk-based prioritization
 * - Automated coverage reporting
 * 
 * COMPLIANCE COVERAGE:
 * - PCI DSS: Payment card data operations
 * - SOX: Financial reporting and controls
 * - GDPR: Personal data processing
 * - SOC 2: Security and operational controls
 * - ISO 27001: Information security management
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-09-28
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditCoverageAnalyzer {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // Critical method patterns that require auditing
    private static final Set<String> CRITICAL_METHOD_PATTERNS = Set.of(
        "create", "update", "delete", "transfer", "withdraw", "deposit",
        "authenticate", "login", "logout", "authorize", "process", "execute",
        "approve", "reject", "cancel", "refund", "settlement", "charge"
    );
    
    // Financial operation patterns (high priority)
    private static final Set<String> FINANCIAL_PATTERNS = Set.of(
        "payment", "transaction", "transfer", "withdraw", "deposit", "refund",
        "charge", "settlement", "balance", "fee", "commission", "exchange"
    );
    
    // Security operation patterns (high priority)
    private static final Set<String> SECURITY_PATTERNS = Set.of(
        "authenticate", "login", "logout", "authorize", "permission", "access",
        "encrypt", "decrypt", "sign", "verify", "token", "credential"
    );
    
    // Data operation patterns (medium priority)
    private static final Set<String> DATA_PATTERNS = Set.of(
        "create", "read", "update", "delete", "export", "import", "backup",
        "restore", "archive", "purge", "migrate", "sync"
    );
    
    /**
     * Generate comprehensive audit coverage report
     */
    public AuditCoverageReport generateCoverageReport() {
        log.info("Starting audit coverage analysis...");
        
        List<ServiceAnalysis> serviceAnalyses = new ArrayList<>();
        Map<String, Object> serviceBeans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class);
        
        int totalCriticalMethods = 0;
        int totalAuditedMethods = 0;
        int totalGaps = 0;
        
        for (Map.Entry<String, Object> entry : serviceBeans.entrySet()) {
            String serviceName = entry.getKey();
            Object serviceBean = entry.getValue();
            
            ServiceAnalysis analysis = analyzeService(serviceName, serviceBean);
            serviceAnalyses.add(analysis);
            
            totalCriticalMethods += analysis.getCriticalMethods().size();
            totalAuditedMethods += analysis.getAuditedMethods().size();
            totalGaps += analysis.getGaps().size();
        }
        
        // Calculate overall metrics
        double overallCoveragePercentage = totalCriticalMethods > 0 ? 
            (double) totalAuditedMethods / totalCriticalMethods * 100 : 100.0;
        
        AuditCoverageReport report = AuditCoverageReport.builder()
            .analysisTimestamp(new Date())
            .serviceAnalyses(serviceAnalyses)
            .totalServices(serviceAnalyses.size())
            .totalCriticalMethods(totalCriticalMethods)
            .totalAuditedMethods(totalAuditedMethods)
            .totalGaps(totalGaps)
            .overallCoveragePercentage(overallCoveragePercentage)
            .complianceStatus(determineComplianceStatus(overallCoveragePercentage))
            .recommendations(generateRecommendations(serviceAnalyses))
            .highPriorityGaps(identifyHighPriorityGaps(serviceAnalyses))
            .build();
        
        log.info("Audit coverage analysis completed. Overall coverage: {:.2f}%", overallCoveragePercentage);
        
        return report;
    }
    
    /**
     * Analyze audit coverage for a specific service
     */
    public ServiceAnalysis analyzeService(String serviceName, Object serviceBean) {
        Class<?> serviceClass = serviceBean.getClass();
        Method[] methods = serviceClass.getMethods();
        
        List<MethodAnalysis> criticalMethods = new ArrayList<>();
        List<MethodAnalysis> auditedMethods = new ArrayList<>();
        List<AuditGap> gaps = new ArrayList<>();
        
        for (Method method : methods) {
            // Skip methods from Object class and Spring proxies
            if (method.getDeclaringClass().equals(Object.class) || 
                method.getName().startsWith("$") ||
                method.isSynthetic()) {
                continue;
            }
            
            MethodAnalysis methodAnalysis = analyzeMethod(method);
            
            if (methodAnalysis.isCritical()) {
                criticalMethods.add(methodAnalysis);
                
                if (methodAnalysis.isAudited()) {
                    auditedMethods.add(methodAnalysis);
                } else {
                    AuditGap gap = AuditGap.builder()
                        .serviceName(serviceName)
                        .methodName(method.getName())
                        .methodSignature(getMethodSignature(method))
                        .riskLevel(methodAnalysis.getRiskLevel())
                        .businessArea(methodAnalysis.getBusinessArea())
                        .complianceRequirements(methodAnalysis.getComplianceRequirements())
                        .recommendedAuditLevel(methodAnalysis.getRecommendedAuditLevel())
                        .justification(methodAnalysis.getGapJustification())
                        .build();
                    gaps.add(gap);
                }
            }
        }
        
        double coveragePercentage = criticalMethods.size() > 0 ? 
            (double) auditedMethods.size() / criticalMethods.size() * 100 : 100.0;
        
        return ServiceAnalysis.builder()
            .serviceName(serviceName)
            .serviceClass(serviceClass.getSimpleName())
            .criticalMethods(criticalMethods)
            .auditedMethods(auditedMethods)
            .gaps(gaps)
            .coveragePercentage(coveragePercentage)
            .riskLevel(determineServiceRiskLevel(serviceName, gaps))
            .complianceFrameworks(identifyComplianceFrameworks(criticalMethods))
            .build();
    }
    
    /**
     * Analyze a specific method for audit requirements
     */
    private MethodAnalysis analyzeMethod(Method method) {
        String methodName = method.getName().toLowerCase();
        String className = method.getDeclaringClass().getSimpleName().toLowerCase();
        
        // Check if method is already audited
        boolean isAudited = AnnotationUtils.findAnnotation(method, Auditable.class) != null ||
                           AnnotationUtils.findAnnotation(method, AuditLogged.class) != null;
        
        // Determine if method is critical for auditing
        boolean isCritical = isCriticalMethod(methodName, className);
        
        // Determine business area
        BusinessArea businessArea = determineBusinessArea(methodName, className);
        
        // Determine risk level
        RiskLevel riskLevel = determineRiskLevel(methodName, className, businessArea);
        
        // Identify compliance requirements
        Set<String> complianceRequirements = identifyComplianceRequirements(methodName, className, businessArea);
        
        // Recommend audit level
        AuditLevel recommendedAuditLevel = recommendAuditLevel(riskLevel, businessArea);
        
        // Generate gap justification if not audited
        String gapJustification = !isAudited && isCritical ? 
            generateGapJustification(methodName, businessArea, riskLevel) : null;
        
        return MethodAnalysis.builder()
            .methodName(method.getName())
            .methodSignature(getMethodSignature(method))
            .critical(isCritical)
            .audited(isAudited)
            .businessArea(businessArea)
            .riskLevel(riskLevel)
            .complianceRequirements(complianceRequirements)
            .recommendedAuditLevel(recommendedAuditLevel)
            .gapJustification(gapJustification)
            .build();
    }
    
    /**
     * Determine if method is critical for auditing
     */
    private boolean isCriticalMethod(String methodName, String className) {
        // Check if method name contains critical patterns
        boolean hasCriticalPattern = CRITICAL_METHOD_PATTERNS.stream()
            .anyMatch(pattern -> methodName.contains(pattern));
        
        // Check if class is in critical business areas
        boolean isCriticalService = className.contains("payment") ||
                                   className.contains("wallet") ||
                                   className.contains("auth") ||
                                   className.contains("user") ||
                                   className.contains("transaction") ||
                                   className.contains("compliance") ||
                                   className.contains("security");
        
        return hasCriticalPattern && isCriticalService;
    }
    
    /**
     * Determine business area for method
     */
    private BusinessArea determineBusinessArea(String methodName, String className) {
        if (FINANCIAL_PATTERNS.stream().anyMatch(pattern -> 
            methodName.contains(pattern) || className.contains(pattern))) {
            return BusinessArea.FINANCIAL;
        }
        
        if (SECURITY_PATTERNS.stream().anyMatch(pattern -> 
            methodName.contains(pattern) || className.contains(pattern))) {
            return BusinessArea.SECURITY;
        }
        
        if (DATA_PATTERNS.stream().anyMatch(pattern -> 
            methodName.contains(pattern) || className.contains(pattern))) {
            return BusinessArea.DATA_MANAGEMENT;
        }
        
        if (className.contains("user") || className.contains("profile") || 
            className.contains("kyc") || className.contains("identity")) {
            return BusinessArea.USER_MANAGEMENT;
        }
        
        if (className.contains("compliance") || className.contains("regulatory") ||
            className.contains("risk") || className.contains("aml")) {
            return BusinessArea.COMPLIANCE;
        }
        
        return BusinessArea.GENERAL;
    }
    
    /**
     * Determine risk level for method
     */
    private RiskLevel determineRiskLevel(String methodName, String className, BusinessArea businessArea) {
        // High risk patterns
        if (methodName.contains("transfer") && methodName.contains("high") ||
            methodName.contains("withdraw") && businessArea == BusinessArea.FINANCIAL ||
            methodName.contains("authenticate") && className.contains("admin") ||
            methodName.contains("authorize") && className.contains("privilege")) {
            return RiskLevel.HIGH;
        }
        
        // Medium risk for financial and security operations
        if (businessArea == BusinessArea.FINANCIAL || businessArea == BusinessArea.SECURITY) {
            return RiskLevel.MEDIUM;
        }
        
        // Medium risk for compliance operations
        if (businessArea == BusinessArea.COMPLIANCE) {
            return RiskLevel.MEDIUM;
        }
        
        return RiskLevel.LOW;
    }
    
    /**
     * Identify compliance requirements for method
     */
    private Set<String> identifyComplianceRequirements(String methodName, String className, BusinessArea businessArea) {
        Set<String> requirements = new HashSet<>();
        
        // PCI DSS requirements
        if (businessArea == BusinessArea.FINANCIAL && 
            (methodName.contains("payment") || methodName.contains("card") || 
             methodName.contains("transaction") || className.contains("payment"))) {
            requirements.add("PCI_DSS");
        }
        
        // SOX requirements
        if (businessArea == BusinessArea.FINANCIAL || businessArea == BusinessArea.COMPLIANCE) {
            requirements.add("SOX");
        }
        
        // GDPR requirements
        if (businessArea == BusinessArea.USER_MANAGEMENT || 
            (businessArea == BusinessArea.DATA_MANAGEMENT && 
             (className.contains("user") || className.contains("profile") || 
              className.contains("personal") || className.contains("pii")))) {
            requirements.add("GDPR");
        }
        
        // SOC 2 requirements
        if (businessArea == BusinessArea.SECURITY || businessArea == BusinessArea.COMPLIANCE) {
            requirements.add("SOC2");
        }
        
        // ISO 27001 requirements
        if (businessArea == BusinessArea.SECURITY) {
            requirements.add("ISO27001");
        }
        
        return requirements;
    }
    
    /**
     * Recommend audit level based on risk and business area
     */
    private AuditLevel recommendAuditLevel(RiskLevel riskLevel, BusinessArea businessArea) {
        if (riskLevel == RiskLevel.HIGH) {
            return AuditLevel.COMPREHENSIVE;
        }
        
        if (riskLevel == RiskLevel.MEDIUM && 
            (businessArea == BusinessArea.FINANCIAL || businessArea == BusinessArea.SECURITY)) {
            return AuditLevel.COMPREHENSIVE;
        }
        
        if (businessArea == BusinessArea.COMPLIANCE) {
            return AuditLevel.DETAILED;
        }
        
        return AuditLevel.BASIC;
    }
    
    /**
     * Generate gap justification
     */
    private String generateGapJustification(String methodName, BusinessArea businessArea, RiskLevel riskLevel) {
        StringBuilder justification = new StringBuilder();
        
        justification.append("Method '").append(methodName).append("' requires audit logging because: ");
        
        if (riskLevel == RiskLevel.HIGH) {
            justification.append("High risk operation in ").append(businessArea.name()).append(" domain. ");
        }
        
        if (businessArea == BusinessArea.FINANCIAL) {
            justification.append("Financial operations require audit trails for SOX and PCI DSS compliance. ");
        }
        
        if (businessArea == BusinessArea.SECURITY) {
            justification.append("Security operations require audit trails for SOC 2 and ISO 27001 compliance. ");
        }
        
        if (businessArea == BusinessArea.USER_MANAGEMENT) {
            justification.append("User data operations require audit trails for GDPR compliance. ");
        }
        
        justification.append("Lack of audit logging poses compliance and security risks.");
        
        return justification.toString();
    }
    
    /**
     * Determine service risk level
     */
    private RiskLevel determineServiceRiskLevel(String serviceName, List<AuditGap> gaps) {
        long highRiskGaps = gaps.stream()
            .mapToLong(gap -> gap.getRiskLevel() == RiskLevel.HIGH ? 1 : 0)
            .sum();
        
        if (highRiskGaps > 0) {
            return RiskLevel.HIGH;
        }
        
        if (gaps.size() > 5) {
            return RiskLevel.MEDIUM;
        }
        
        return RiskLevel.LOW;
    }
    
    /**
     * Identify compliance frameworks for service
     */
    private Set<String> identifyComplianceFrameworks(List<MethodAnalysis> methods) {
        return methods.stream()
            .flatMap(method -> method.getComplianceRequirements().stream())
            .collect(Collectors.toSet());
    }
    
    /**
     * Determine overall compliance status
     */
    private ComplianceStatus determineComplianceStatus(double coveragePercentage) {
        if (coveragePercentage >= 95.0) {
            return ComplianceStatus.COMPLIANT;
        } else if (coveragePercentage >= 80.0) {
            return ComplianceStatus.MOSTLY_COMPLIANT;
        } else if (coveragePercentage >= 60.0) {
            return ComplianceStatus.PARTIALLY_COMPLIANT;
        } else {
            return ComplianceStatus.NON_COMPLIANT;
        }
    }
    
    /**
     * Generate recommendations based on analysis
     */
    private List<String> generateRecommendations(List<ServiceAnalysis> serviceAnalyses) {
        List<String> recommendations = new ArrayList<>();
        
        // Check for services with low coverage
        serviceAnalyses.stream()
            .filter(service -> service.getCoveragePercentage() < 80.0)
            .forEach(service -> {
                recommendations.add(
                    String.format("Service '%s' has low audit coverage (%.1f%%). " +
                                 "Review and add @Auditable annotations to critical methods.",
                                 service.getServiceName(), service.getCoveragePercentage())
                );
            });
        
        // Check for high-risk gaps
        long totalHighRiskGaps = serviceAnalyses.stream()
            .flatMap(service -> service.getGaps().stream())
            .mapToLong(gap -> gap.getRiskLevel() == RiskLevel.HIGH ? 1 : 0)
            .sum();
        
        if (totalHighRiskGaps > 0) {
            recommendations.add(
                String.format("Found %d high-risk audit gaps. " +
                             "Prioritize adding audit logging to high-risk financial and security operations.",
                             totalHighRiskGaps)
            );
        }
        
        // Check compliance framework coverage
        Set<String> allFrameworks = serviceAnalyses.stream()
            .flatMap(service -> service.getComplianceFrameworks().stream())
            .collect(Collectors.toSet());
        
        if (allFrameworks.contains("PCI_DSS")) {
            recommendations.add("Ensure all payment card data operations have comprehensive audit logging for PCI DSS compliance.");
        }
        
        if (allFrameworks.contains("SOX")) {
            recommendations.add("Ensure all financial reporting operations have audit logging for SOX compliance.");
        }
        
        if (allFrameworks.contains("GDPR")) {
            recommendations.add("Ensure all personal data operations have audit logging for GDPR compliance.");
        }
        
        return recommendations;
    }
    
    /**
     * Identify high priority gaps
     */
    private List<AuditGap> identifyHighPriorityGaps(List<ServiceAnalysis> serviceAnalyses) {
        return serviceAnalyses.stream()
            .flatMap(service -> service.getGaps().stream())
            .filter(gap -> gap.getRiskLevel() == RiskLevel.HIGH || 
                          gap.getBusinessArea() == BusinessArea.FINANCIAL ||
                          gap.getBusinessArea() == BusinessArea.SECURITY)
            .sorted(Comparator.comparing(AuditGap::getRiskLevel).reversed()
                             .thenComparing(gap -> gap.getComplianceRequirements().size()).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Get method signature for display
     */
    private String getMethodSignature(Method method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.getName()).append("(");
        
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) signature.append(", ");
            signature.append(paramTypes[i].getSimpleName());
        }
        
        signature.append(")");
        return signature.toString();
    }
    
    // Data classes for analysis results
    
    @Data
    @lombok.Builder
    public static class AuditCoverageReport {
        private Date analysisTimestamp;
        private List<ServiceAnalysis> serviceAnalyses;
        private int totalServices;
        private int totalCriticalMethods;
        private int totalAuditedMethods;
        private int totalGaps;
        private double overallCoveragePercentage;
        private ComplianceStatus complianceStatus;
        private List<String> recommendations;
        private List<AuditGap> highPriorityGaps;
    }
    
    @Data
    @lombok.Builder
    public static class ServiceAnalysis {
        private String serviceName;
        private String serviceClass;
        private List<MethodAnalysis> criticalMethods;
        private List<MethodAnalysis> auditedMethods;
        private List<AuditGap> gaps;
        private double coveragePercentage;
        private RiskLevel riskLevel;
        private Set<String> complianceFrameworks;
    }
    
    @Data
    @lombok.Builder
    public static class MethodAnalysis {
        private String methodName;
        private String methodSignature;
        private boolean critical;
        private boolean audited;
        private BusinessArea businessArea;
        private RiskLevel riskLevel;
        private Set<String> complianceRequirements;
        private AuditLevel recommendedAuditLevel;
        private String gapJustification;
    }
    
    @Data
    @lombok.Builder
    public static class AuditGap {
        private String serviceName;
        private String methodName;
        private String methodSignature;
        private RiskLevel riskLevel;
        private BusinessArea businessArea;
        private Set<String> complianceRequirements;
        private AuditLevel recommendedAuditLevel;
        private String justification;
    }
    
    public enum BusinessArea {
        FINANCIAL, SECURITY, USER_MANAGEMENT, DATA_MANAGEMENT, COMPLIANCE, GENERAL
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
    
    public enum AuditLevel {
        BASIC, DETAILED, COMPREHENSIVE
    }
    
    public enum ComplianceStatus {
        COMPLIANT, MOSTLY_COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT
    }
}