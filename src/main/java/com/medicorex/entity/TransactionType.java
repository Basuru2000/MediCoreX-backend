package com.medicorex.entity;

public enum TransactionType {
    PURCHASE("Purchase", "Stock purchased from supplier"),
    SALE("Sale", "Stock sold to customer"),
    ADJUSTMENT("Adjustment", "Manual stock adjustment"),
    DAMAGE("Damage", "Stock damaged/expired"),
    RETURN("Return", "Stock returned"),
    TRANSFER("Transfer", "Stock transferred between locations"),
    INITIAL("Initial", "Initial stock entry"),
    CORRECTION("Correction", "Stock correction");

    private final String displayName;
    private final String description;

    TransactionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    // Helper method to convert string to enum
    public static TransactionType fromString(String text) {
        for (TransactionType type : TransactionType.values()) {
            if (type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        // Default to ADJUSTMENT if not found
        return ADJUSTMENT;
    }
}