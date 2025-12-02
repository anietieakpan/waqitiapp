package org.springframework.context.support;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility class to access Spring ApplicationContext statically
 * This is useful when you need to access Spring beans outside of Spring-managed components
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {
    
    private static ApplicationContext context;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        ApplicationContextHolder.context = applicationContext;
    }
    
    /**
     * Get the ApplicationContext instance
     * @return the ApplicationContext or null if not set
     */
    public static ApplicationContext getApplicationContext() {
        return context;
    }
    
    /**
     * Get a bean by name
     * @param name the bean name
     * @return the bean instance
     */
    public static Object getBean(String name) {
        if (context != null) {
            return context.getBean(name);
        }
        return null;
    }
    
    /**
     * Get a bean by type
     * @param clazz the bean class
     * @param <T> the bean type
     * @return the bean instance
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context != null) {
            return context.getBean(clazz);
        }
        return null;
    }
    
    /**
     * Check if a bean with the given name exists
     * @param name the bean name
     * @return true if the bean exists
     */
    public static boolean containsBean(String name) {
        return context != null && context.containsBean(name);
    }
}