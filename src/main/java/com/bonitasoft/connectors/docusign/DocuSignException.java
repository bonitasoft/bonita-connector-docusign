package com.bonitasoft.connectors.docusign;

/**
 * Typed exception for DocuSign connector.
 * Carries HTTP status code and retryable flag for retry policy decisions.
 */
public class DocuSignException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public DocuSignException(String message) {
        super(message);
        this.statusCode = -1;
        this.retryable = false;
    }

    public DocuSignException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.retryable = false;
    }

    public DocuSignException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public DocuSignException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
