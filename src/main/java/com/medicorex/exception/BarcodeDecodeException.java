// Create new file: src/main/java/com/medicorex/exception/BarcodeDecodeException.java

package com.medicorex.exception;

/**
 * Exception thrown when barcode decoding fails
 */
public class BarcodeDecodeException extends RuntimeException {

    public BarcodeDecodeException(String message) {
        super(message);
    }

    public BarcodeDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}