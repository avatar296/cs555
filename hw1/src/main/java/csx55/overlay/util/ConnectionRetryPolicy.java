package csx55.overlay.util;

import java.io.IOException;
import java.net.Socket;

/**
 * Implements retry logic for network connections with exponential backoff.
 * Provides configurable retry attempts and delays to handle transient network failures.
 * Supports both direct socket connections and generic retryable operations.
 */
public class ConnectionRetryPolicy {
    
    /** Default maximum number of retry attempts */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    
    /** Default base delay in milliseconds (1 second) */
    private static final long DEFAULT_BASE_DELAY_MS = 1000;
    
    /** Default maximum delay in milliseconds (30 seconds) */
    private static final long DEFAULT_MAX_DELAY_MS = 30000;
    
    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    
    /**
     * Constructs a ConnectionRetryPolicy with default parameters.
     */
    public ConnectionRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }
    
    /**
     * Constructs a ConnectionRetryPolicy with custom parameters.
     * 
     * @param maxAttempts the maximum number of retry attempts
     * @param baseDelayMs the base delay in milliseconds for exponential backoff
     * @param maxDelayMs the maximum delay in milliseconds between retries
     */
    public ConnectionRetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }
    
    /**
     * Attempts to connect to the specified host and port with retry logic.
     * Uses exponential backoff between attempts.
     * 
     * @param host the host to connect to
     * @param port the port to connect to
     * @return established socket connection
     * @throws IOException if all retry attempts fail
     */
    public Socket connectWithRetry(String host, int port) throws IOException {
        IOException lastException = null;
        
        for (int e = 1; e <= maxAttempts; e++) {
            try {
                LoggerUtil.info("ConnectionRetry", 
                    String.format("Attempting connection to %s:%d (attempt %d/%d)", 
                    host, port, e, maxAttempts));
                    
                Socket socket = new Socket(host, port);
                
                if (e > 1) {
                    LoggerUtil.info("ConnectionRetry", 
                        String.format("Successfully connected to %s:%d after %d attempts", 
                        host, port, e));
                }
                
                return socket;
                
            } catch (IOException ex) {
                lastException = ex;
                LoggerUtil.warn("ConnectionRetry", 
                    String.format("Connection attempt %d failed: %s", e, ex.getMessage()));
                
                if (e < maxAttempts) {
                    long delay = calculateBackoffDelay(e);
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
     * Executes an operation with retry logic.
     * Only retries if the exception is determined to be retryable.
     * 
     * @param <T> the return type of the operation
     * @param operation the operation to execute
     * @param operationName name for logging purposes
     * @return result of the operation
     * @throws Exception if all retry attempts fail or exception is not retryable
     */
    public <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) throws Exception {
        Exception lastException = null;
        
        for (int e = 1; e <= maxAttempts; e++) {
            try {
                LoggerUtil.debug("ConnectionRetry", 
                    String.format("Executing %s (attempt %d/%d)", 
                    operationName, e, maxAttempts));
                    
                T result = operation.execute();
                
                if (e > 1) {
                    LoggerUtil.info("ConnectionRetry", 
                        String.format("%s succeeded after %d attempts", 
                        operationName, e));
                }
                
                return result;
                
            } catch (Exception ex) {
                lastException = ex;
                LoggerUtil.warn("ConnectionRetry", 
                    String.format("%s attempt %d failed: %s", 
                    operationName, e, ex.getMessage()));
                
                if (e < maxAttempts && isRetryable(ex)) {
                    long delay = calculateBackoffDelay(e);
                    
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
     * Calculates the exponential backoff delay for the given attempt.
     * Delay doubles with each attempt, capped at maxDelayMs.
     * 
     * @param attempt the current attempt number (1-based)
     * @return the delay in milliseconds
     */
    private long calculateBackoffDelay(int attempt) {
        long delay = baseDelayMs * (1L << (attempt - 1));
        return Math.min(delay, maxDelayMs);
    }
    
    /**
     * Determines if an exception is retryable.
     * Network timeouts, connection refused, and connection resets are considered retryable.
     * 
     * @param e the exception to check
     * @return true if the exception is retryable, false otherwise
     */
    private boolean isRetryable(Exception e) {
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
     * Interface for operations that can be retried.
     * 
     * @param <T> the return type of the operation
     */
    public interface RetryableOperation<T> {
        /**
         * Executes the operation.
         * 
         * @return the result of the operation
         * @throws Exception if the operation fails
         */
        T execute() throws Exception;
    }
}