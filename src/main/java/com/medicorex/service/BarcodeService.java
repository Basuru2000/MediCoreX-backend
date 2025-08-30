package com.medicorex.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.oned.Code128Writer;
import com.medicorex.entity.Product;
import lombok.RequiredArgsConstructor;
import com.medicorex.exception.BarcodeDecodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeService {

    private static final int BARCODE_WIDTH = 300;
    private static final int BARCODE_HEIGHT = 100;
    private static final int QR_CODE_SIZE = 200;

    /**
     * Generate a unique barcode for a product
     */
    public String generateBarcode(Product product) {
        // Generate barcode based on product ID with prefix
        String prefix = "MED";
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(7);
        return prefix + String.format("%06d", product.getId()) + timestamp;
    }

    /**
     * Generate barcode image as Base64 string
     */
    public String generateBarcodeImage(String barcodeText) throws WriterException, IOException {
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.CODE_128, BARCODE_WIDTH, BARCODE_HEIGHT);

        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Generate QR code containing product information
     */
    public String generateQRCode(Product product) throws WriterException, IOException {
        // Create JSON-like string with product info
        StringBuilder qrContent = new StringBuilder();
        qrContent.append("MediCoreX Product\n");
        qrContent.append("ID: ").append(product.getId()).append("\n");
        qrContent.append("Name: ").append(product.getName()).append("\n");
        qrContent.append("Code: ").append(product.getCode() != null ? product.getCode() : "N/A").append("\n");
        qrContent.append("Barcode: ").append(product.getBarcode() != null ? product.getBarcode() : "N/A").append("\n");
        qrContent.append("Price: $").append(product.getUnitPrice()).append("\n");

        if (product.getExpiryDate() != null) {
            qrContent.append("Expiry: ").append(product.getExpiryDate()).append("\n");
        }

        if (product.getBatchNumber() != null) {
            qrContent.append("Batch: ").append(product.getBatchNumber()).append("\n");
        }

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrContent.toString(), BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Decode barcode from Base64 image with improved accuracy
     */
    public String decodeBarcode(String base64Image) throws IOException {
        log.info("Starting barcode decoding process");
        // Remove data URL prefix if present
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new BarcodeDecodeException("Failed to decode image from base64 data");
        }
        log.info("Image loaded successfully: width={}, height={}", image.getWidth(), image.getHeight());
        // Configure hints for better detection
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.QR_CODE
        ));
        // Try multiple readers
        MultiFormatReader multiReader = new MultiFormatReader();
        multiReader.setHints(hints);
        try {
            // Convert to luminance source
            LuminanceSource source = new BufferedImageLuminanceSource(image);

            // Try with GlobalHistogramBinarizer first
            BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            Result result = multiReader.decode(bitmap);

            if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                log.info("Barcode decoded successfully: {}", result.getText());
                return result.getText();
            }
        } catch (NotFoundException e) {
            log.debug("First attempt failed, trying HybridBinarizer");
        }
        try {
            // Try with HybridBinarizer
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = multiReader.decode(bitmap);

            if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                log.info("Barcode decoded successfully with HybridBinarizer: {}", result.getText());
                return result.getText();
            }
        } catch (NotFoundException e) {
            log.debug("Second attempt failed");
        }
        // If all attempts fail, throw exception with helpful message
        throw new BarcodeDecodeException("No barcode found in the image. Please ensure the image contains a clear, readable barcode and try again.");
    }

    /**
     * Try to decode barcode with different configurations
     */
    private String tryDecodeBarcode(BufferedImage image, String approach) {
        log.debug("Trying to decode barcode with approach: {}", approach);

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, true);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.ITF,
                BarcodeFormat.CODABAR
        ));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        try {
            // Try single barcode reader
            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(bitmap, hints);
            log.info("Successfully decoded barcode with approach '{}': {}", approach, result.getText());
            return result.getText();
        } catch (NotFoundException e) {
            // Try multiple barcode reader
            try {
                MultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(new MultiFormatReader());
                Result[] results = multiReader.decodeMultiple(bitmap, hints);
                if (results != null && results.length > 0) {
                    log.info("Successfully decoded multiple barcodes with approach '{}', returning first: {}",
                            approach, results[0].getText());
                    return results[0].getText();
                }
            } catch (Exception multiEx) {
                log.debug("Multiple barcode reader also failed");
            }
            return null;
        }
    }

    /**
     * Try decoding with different binarizers
     */
    private String tryDecodeWithDifferentBinarizers(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);

        // Try with GlobalHistogramBinarizer
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, true);

            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(bitmap, hints);
            log.info("Successfully decoded with GlobalHistogramBinarizer: {}", result.getText());
            return result.getText();
        } catch (NotFoundException e) {
            log.debug("GlobalHistogramBinarizer failed");
            return null;
        }
    }

    /**
     * Preprocess image to improve barcode detection
     */
    private BufferedImage preprocessImage(BufferedImage original) {
        // Convert to grayscale
        BufferedImage grayscale = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int rgb = original.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int grayRGB = (gray << 16) | (gray << 8) | gray;
                grayscale.setRGB(x, y, grayRGB);
            }
        }

        // Apply simple threshold to create high contrast
        BufferedImage binary = new BufferedImage(
                grayscale.getWidth(),
                grayscale.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY
        );

        for (int x = 0; x < grayscale.getWidth(); x++) {
            for (int y = 0; y < grayscale.getHeight(); y++) {
                int gray = grayscale.getRGB(x, y) & 0xFF;
                int binary_pixel = gray < 128 ? 0x000000 : 0xFFFFFF;
                binary.setRGB(x, y, binary_pixel);
            }
        }

        return binary;
    }

    /**
     * Rotate image by specified angle
     */
    private BufferedImage rotateImage(BufferedImage original, int angle) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage rotated;
        if (angle == 90 || angle == 270) {
            rotated = new BufferedImage(height, width, original.getType());
        } else {
            rotated = new BufferedImage(width, height, original.getType());
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int newX, newY;
                switch (angle) {
                    case 90:
                        newX = height - 1 - y;
                        newY = x;
                        break;
                    case 180:
                        newX = width - 1 - x;
                        newY = height - 1 - y;
                        break;
                    case 270:
                        newX = y;
                        newY = width - 1 - x;
                        break;
                    default:
                        newX = x;
                        newY = y;
                }
                rotated.setRGB(newX, newY, original.getRGB(x, y));
            }
        }

        return rotated;
    }

    /**
     * Validate barcode format
     */
    public boolean isValidBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        // Check if it matches our format or is a standard barcode
        // Allow alphanumeric characters, minimum 8 characters
        return barcode.matches("^[A-Za-z0-9]{8,}$");
    }
}