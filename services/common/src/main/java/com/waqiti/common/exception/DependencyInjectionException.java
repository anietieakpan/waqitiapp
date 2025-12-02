package com.waqiti.common.exception;

/**
 * Exception thrown when dependency injection fails
 * Used for handling Spring Bean creation and wiring failures
 */
public class DependencyInjectionException extends WaqitiException {
    
    private final String beanName;
    private final String beanClass;
    private final String dependencyType;
    private final String contextPath;
    
    public DependencyInjectionException(String message) {
        super(ErrorCode.SYS_CONFIGURATION_ERROR, message);
        this.beanName = null;
        this.beanClass = null;
        this.dependencyType = null;
        this.contextPath = null;
    }
    
    public DependencyInjectionException(String message, Throwable cause) {
        super(ErrorCode.SYS_CONFIGURATION_ERROR, message, cause);
        this.beanName = null;
        this.beanClass = null;
        this.dependencyType = null;
        this.contextPath = null;
    }
    
    public DependencyInjectionException(String message, String beanName, String beanClass) {
        super(ErrorCode.SYS_CONFIGURATION_ERROR, message);
        this.beanName = beanName;
        this.beanClass = beanClass;
        this.dependencyType = null;
        this.contextPath = null;
    }
    
    public DependencyInjectionException(String message, String beanName, String beanClass,
                                      String dependencyType, String contextPath) {
        super(ErrorCode.SYS_CONFIGURATION_ERROR, message);
        this.beanName = beanName;
        this.beanClass = beanClass;
        this.dependencyType = dependencyType;
        this.contextPath = contextPath;
    }
    
    public DependencyInjectionException(String message, Throwable cause, String beanName,
                                      String beanClass, String dependencyType, String contextPath) {
        super(ErrorCode.SYS_CONFIGURATION_ERROR, message, cause);
        this.beanName = beanName;
        this.beanClass = beanClass;
        this.dependencyType = dependencyType;
        this.contextPath = contextPath;
    }
    
    public String getBeanName() {
        return beanName;
    }
    
    public String getBeanClass() {
        return beanClass;
    }
    
    public String getDependencyType() {
        return dependencyType;
    }
    
    public String getContextPath() {
        return contextPath;
    }
    
    public String getBeanContext() {
        StringBuilder context = new StringBuilder();
        if (beanName != null) context.append("bean=").append(beanName);
        if (beanClass != null) context.append(", class=").append(beanClass);
        if (dependencyType != null) context.append(", dependency=").append(dependencyType);
        if (contextPath != null) context.append(", context=").append(contextPath);
        return context.toString();
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        String context = getBeanContext();
        if (context.isEmpty()) {
            return baseMessage;
        }
        return baseMessage + " [" + context + "]";
    }
    
    // Static factory methods for common scenarios
    public static DependencyInjectionException beanNotFound(String beanName, String beanClass) {
        return new DependencyInjectionException(
            "Required bean not found in application context", 
            beanName, beanClass
        );
    }
    
    public static DependencyInjectionException circularDependency(String beanName, String beanClass) {
        return new DependencyInjectionException(
            "Circular dependency detected", 
            beanName, beanClass
        );
    }
    
    public static DependencyInjectionException beanCreationFailed(String beanName, String beanClass, Throwable cause) {
        return new DependencyInjectionException(
            "Failed to create bean instance", 
            cause, beanName, beanClass, null, null
        );
    }
    
    public static DependencyInjectionException unsatisfiedDependency(String beanName, String beanClass, 
                                                                   String dependencyType) {
        return new DependencyInjectionException(
            "Unsatisfied dependency", 
            beanName, beanClass, dependencyType, null
        );
    }
}