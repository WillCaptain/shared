package org.example.billing.client;

import org.example.billing.contract.BillingErrorCode;

public final class BillingClientException extends RuntimeException {
    private final int status;
    private final BillingErrorCode errorCode;
    private final boolean retryable;

    public BillingClientException(String message, int status, BillingErrorCode errorCode, boolean retryable,
                                  Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode == null ? BillingErrorCode.INTERNAL_ERROR : errorCode;
        this.retryable = retryable;
    }

    public BillingClientException(String message, int status, BillingErrorCode errorCode, boolean retryable) {
        this(message, status, errorCode, retryable, null);
    }

    public int status() { return status; }
    public BillingErrorCode errorCode() { return errorCode; }
    public boolean retryable() { return retryable; }
}
