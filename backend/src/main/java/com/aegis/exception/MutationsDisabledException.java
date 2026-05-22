package com.aegis.exception;

public class MutationsDisabledException extends RuntimeException {

    public MutationsDisabledException(String message) {
        super(message);
    }
}
