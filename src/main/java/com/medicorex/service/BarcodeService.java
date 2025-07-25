package com.medicorex.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.oned.Code128Writer;
import com.medicorex.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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
     * Decode barcode from Base64 image
     */
    public String decodeBarcode(String base64Image) throws IOException, NotFoundException {
        // Remove data URL prefix if present
        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",")[1];
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));

        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, true);

        Result result = reader.decode(bitmap, hints);
        return result.getText();
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