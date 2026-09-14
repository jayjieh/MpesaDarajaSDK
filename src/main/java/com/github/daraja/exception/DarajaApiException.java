package com.github.daraja.exception;

public class DarajaApiException extends RuntimeException {

    private final String errorCode;

    public DarajaApiException(String message) {
        super(message);
        this.errorCode = null;
    }

    public DarajaApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public DarajaApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
