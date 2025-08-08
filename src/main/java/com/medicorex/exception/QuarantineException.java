package com.medicorex.exception;

public class QuarantineException extends RuntimeException {

    public QuarantineException(String message) {
        super(message);
    }

    public QuarantineException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class InvalidTransitionException extends QuarantineException {
        public InvalidTransitionException(String currentStatus, String targetAction) {
            super(String.format("Invalid transition from status '%s' with action '%s'",
                    currentStatus, targetAction));
        }
    }

    public static class AlreadyQuarantinedException extends QuarantineException {
        public AlreadyQuarantinedException(Long batchId) {
            super(String.format("Batch %d is already quarantined", batchId));
        }
    }
}