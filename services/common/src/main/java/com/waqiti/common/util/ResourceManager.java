package com.waqiti.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Comprehensive resource management utility
 * Ensures proper cleanup of resources and prevents resource leaks
 */
@Slf4j
public class ResourceManager {

    private final List<AutoCloseable> resources = new ArrayList<>();
    private volatile boolean closed = false;

    /**
     * Registers a resource for automatic cleanup
     */
    public <T extends AutoCloseable> T register(T resource) {
        if (resource == null) {
            return null;
        }
        synchronized (resources) {
            if (closed) {
                throw new IllegalStateException("ResourceManager is already closed");
            }
            resources.add(resource);
        }
        return resource;
    }

    /**
     * Executes an action with a resource and ensures cleanup
     */
    public static <T extends AutoCloseable, R> R useResource(
            T resource, 
            Function<T, R> action) {
        try {
            return action.apply(resource);
        } finally {
            closeQuietly(resource);
        }
    }

    /**
     * Executes an action with a resource (void return)
     */
    public static <T extends AutoCloseable> void useResourceVoid(
            T resource, 
            Consumer<T> action) {
        try {
            action.accept(resource);
        } finally {
            closeQuietly(resource);
        }
    }

    /**
     * Executes an action with multiple resources
     */
    @SafeVarargs
    public static <R> R useResources(
            Function<ResourceContext, R> action,
            AutoCloseable... resources) {
        ResourceContext context = new ResourceContext();
        try {
            for (AutoCloseable resource : resources) {
                context.register(resource);
            }
            return action.apply(context);
        } finally {
            context.closeAll();
        }
    }

    /**
     * Closes a resource quietly without throwing exceptions
     */
    public static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                log.warn("Failed to close resource: {}", resource.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Closes multiple resources quietly
     */
    public static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            closeQuietly(resource);
        }
    }

    /**
     * Closes JDBC resources in proper order
     */
    public static void closeJDBC(ResultSet rs, Statement stmt, Connection conn) {
        closeQuietly(rs);
        closeQuietly(stmt);
        closeQuietly(conn);
    }

    /**
     * Shuts down an executor service gracefully
     */
    public static void shutdownExecutor(ExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null) {
            return;
        }
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeout, unit)) {
                log.warn("Executor did not terminate in {} {}", timeout, unit);
                executor.shutdownNow();
                if (!executor.awaitTermination(timeout / 2, unit)) {
                    log.error("Executor did not terminate after shutdownNow()");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * Handles CompletableFuture cancellation safely
     */
    public static void cancelFuture(CompletableFuture<?> future, boolean mayInterruptIfRunning) {
        if (future != null && !future.isDone()) {
            future.cancel(mayInterruptIfRunning);
        }
    }

    /**
     * Closes all registered resources
     */
    public void closeAll() {
        synchronized (resources) {
            if (closed) {
                return;
            }
            closed = true;
            
            // Close in reverse order of registration
            for (int i = resources.size() - 1; i >= 0; i--) {
                closeQuietly(resources.get(i));
            }
            resources.clear();
        }
    }

    /**
     * Resource context for managing multiple resources
     */
    public static class ResourceContext implements AutoCloseable {
        private final List<AutoCloseable> resources = new ArrayList<>();

        public <T extends AutoCloseable> T register(T resource) {
            if (resource != null) {
                resources.add(resource);
            }
            return resource;
        }

        @Override
        public void close() {
            closeAll();
        }

        private void closeAll() {
            for (int i = resources.size() - 1; i >= 0; i--) {
                closeQuietly(resources.get(i));
            }
            resources.clear();
        }
    }

    /**
     * Try-with-resources helper for multiple operations
     */
    public static class TryWithResources implements AutoCloseable {
        private final List<AutoCloseable> resources = new ArrayList<>();
        private final List<Runnable> cleanupTasks = new ArrayList<>();

        public <T extends AutoCloseable> T add(T resource) {
            if (resource != null) {
                resources.add(resource);
            }
            return resource;
        }

        public void addCleanupTask(Runnable task) {
            if (task != null) {
                cleanupTasks.add(task);
            }
        }

        @Override
        public void close() {
            // Run cleanup tasks first
            for (Runnable task : cleanupTasks) {
                try {
                    task.run();
                } catch (Exception e) {
                    log.warn("Cleanup task failed", e);
                }
            }
            
            // Then close resources in reverse order
            for (int i = resources.size() - 1; i >= 0; i--) {
                closeQuietly(resources.get(i));
            }
        }
    }

    /**
     * Flushes and closes an output stream safely
     */
    public static void flushAndClose(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        
        try {
            if (closeable instanceof java.io.Flushable) {
                ((java.io.Flushable) closeable).flush();
            }
        } catch (IOException e) {
            log.warn("Failed to flush stream", e);
        } finally {
            closeQuietly(closeable);
        }
    }

    /**
     * Ensures a database connection is valid and not closed
     */
    public static boolean isConnectionValid(Connection conn, int timeout) {
        if (conn == null) {
            return false;
        }
        
        try {
            return !conn.isClosed() && conn.isValid(timeout);
        } catch (SQLException e) {
            log.debug("Connection validation failed", e);
            return false;
        }
    }

    /**
     * Rollback a transaction safely
     */
    public static void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e) {
                log.warn("Failed to rollback transaction", e);
            }
        }
    }

    /**
     * Commit a transaction safely
     */
    public static boolean commitQuietly(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.commit();
                    return true;
                }
            } catch (SQLException e) {
                log.warn("Failed to commit transaction", e);
                rollbackQuietly(conn);
            }
        }
        return false;
    }
}