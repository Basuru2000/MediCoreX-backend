package com.medicorex.exception;

public class InsufficientBatchStockException extends RuntimeException {

    private final Long batchId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientBatchStockException(Long batchId, Integer requested, Integer available) {
        super(String.format("Insufficient stock in batch %d. Requested: %d, Available: %d",
                batchId, requested, available));
        this.batchId = batchId;
        this.requestedQuantity = requested;
        this.availableQuantity = available;
    }

    // Getters
    public Long getBatchId() { return batchId; }
    public Integer getRequestedQuantity() { return requestedQuantity; }
    public Integer getAvailableQuantity() { return availableQuantity; }
}