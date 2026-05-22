package com.aegis.exception;

public class DemoQuotaExceededException extends RuntimeException {

    public DemoQuotaExceededException(String message) {
        super(message);
    }
}
