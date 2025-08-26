package csx55.overlay.util;

import java.io.IOException;
import java.net.Socket;

/**
 * Implements retry logic for network connections with exponential backoff
 */
public class ConnectionRetryPolicy {
    
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 1000; // 1 second
    private static final long DEFAULT_MAX_DELAY_MS = 30000; // 30 seconds
    
    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    
    public ConnectionRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }
    
    public ConnectionRetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }
    
    /**
     * Attempt to connect with retry logic
     * @param host The host to connect to
     * @param port The port to connect to
     * @return Established socket connection
     * @throws IOException if all retry attempts fail
     */
    public Socket connectWithRetry(String host, int port) throws IOException {
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LoggerUtil.info("ConnectionRetry", 
                    String.format("Attempting connection to %s:%d (attempt %d/%d)", 
                    host, port, attempt, maxAttempts));
                    
                Socket socket = new Socket(host, port);
                
                if (attempt > 1) {
                    LoggerUtil.info("ConnectionRetry", 
                        String.format("Successfully connected to %s:%d after %d attempts", 
                        host, port, attempt));
                }
                
                return socket;
                
            } catch (IOException e) {
                lastException = e;
                LoggerUtil.warn("ConnectionRetry", 
                    String.format("Connection attempt %d failed: %s", attempt, e.getMessage()));
                
                if (attempt < maxAttempts) {
                    long delay = calculateBackoffDelay(attempt);
                    LoggerUtil.debug("ConnectionRetry", 
                        String.format("Waiting %dms before retry", delay));
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        throw new IOException(String.format(
            "Failed to connect to %s:%d after %d attempts", 
            host, port, maxAttempts), lastException);
    }
    
    /**
     * Execute an operation with retry logic
     * @param operation The operation to execute
     * @param operationName Name for logging
     * @return Result of the operation
     * @throws Exception if all retry attempts fail
     */
    public <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LoggerUtil.debug("ConnectionRetry", 
                    String.format("Executing %s (attempt %d/%d)", 
                    operationName, attempt, maxAttempts));
                    
                T result = operation.execute();
                
                if (attempt > 1) {
                    LoggerUtil.info("ConnectionRetry", 
                        String.format("%s succeeded after %d attempts", 
                        operationName, attempt));
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                LoggerUtil.warn("ConnectionRetry", 
                    String.format("%s attempt %d failed: %s", 
                    operationName, attempt, e.getMessage()));
                
                if (attempt < maxAttempts && isRetryable(e)) {
                    long delay = calculateBackoffDelay(attempt);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Retry interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        
        throw new Exception(String.format(
            "%s failed after %d attempts", 
            operationName, maxAttempts), lastException);
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoffDelay(int attempt) {
        long delay = baseDelayMs * (1L << (attempt - 1)); // Exponential backoff
        return Math.min(delay, maxDelayMs);
    }
    
    /**
     * Determine if an exception is retryable
     */
    private boolean isRetryable(Exception e) {
        // Network timeouts, connection refused, etc. are retryable
        if (e instanceof IOException) {
            String message = e.getMessage();
            if (message != null) {
                return message.contains("Connection refused") ||
                       message.contains("Connection reset") ||
                       message.contains("Timeout") ||
                       message.contains("timed out");
            }
        }
        return false;
    }
    
    /**
     * Interface for retryable operations
     */
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }
}