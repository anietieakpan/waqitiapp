package com.waqiti.common.async;

/**
 * Callback interface for progress reporting
 */
@FunctionalInterface
public interface ProgressCallback {
    void onProgress(int percentage, String message);
}