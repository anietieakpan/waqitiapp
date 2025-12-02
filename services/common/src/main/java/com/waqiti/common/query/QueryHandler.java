package com.waqiti.common.query;

/**
 * Interface for query handlers in CQRS pattern
 * @param <Q> The query type
 * @param <R> The result type
 */
public interface QueryHandler<Q extends Query<R>, R> {
    
    /**
     * Handle the query and return the result
     * @param query The query to handle
     * @return The query result
     */
    R handle(Q query);
    
    /**
     * Get the query class this handler supports
     * Uses reflection to determine the generic type parameter
     */
    @SuppressWarnings("unchecked")
    default Class<Q> getQueryClass() {
        try {
            // Use reflection to determine the generic type parameter
            java.lang.reflect.Type[] interfaces = getClass().getGenericInterfaces();
            for (java.lang.reflect.Type type : interfaces) {
                if (type instanceof java.lang.reflect.ParameterizedType paramType) {
                    if (paramType.getRawType().equals(QueryHandler.class)) {
                        java.lang.reflect.Type queryType = paramType.getActualTypeArguments()[0];
                        if (queryType instanceof Class) {
                            return (Class<Q>) queryType;
                        }
                    }
                }
            }
            
            // Fallback: check superclass for generic types
            java.lang.reflect.Type superclass = getClass().getGenericSuperclass();
            if (superclass instanceof java.lang.reflect.ParameterizedType paramType) {
                java.lang.reflect.Type queryType = paramType.getActualTypeArguments()[0];
                if (queryType instanceof Class) {
                    return (Class<Q>) queryType;
                }
            }
            
            // If reflection fails, implementations should override this method
            throw new IllegalStateException(
                "Unable to determine query class for " + getClass().getSimpleName() + 
                ". Please override getQueryClass() method."
            );
            
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to determine query class for " + getClass().getSimpleName() + 
                ": " + e.getMessage(), e
            );
        }
    }
    
    /**
     * Check if this handler can handle the given query
     */
    default boolean canHandle(Query<?> query) {
        try {
            return getQueryClass().isInstance(query);
        } catch (IllegalStateException e) {
            // If query class detection fails, cannot handle the query
            return false;
        } catch (Exception e) {
            // Any other exception means we cannot safely handle this query
            return false;
        }
    }
}