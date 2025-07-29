package com.medicorex.exception;

public class DuplicateAlertConfigException extends RuntimeException {
    public DuplicateAlertConfigException(String message) {
        super(message);
    }
}