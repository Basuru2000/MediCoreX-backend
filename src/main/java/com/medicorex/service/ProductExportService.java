package com.medicorex.service;

import com.medicorex.entity.Product;
import com.medicorex.repository.ProductRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductExportService {

    private final ProductRepository productRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Export all products to CSV format
     */
    public byte[] exportProductsToCSV() throws IOException {
        List<Product> products = productRepository.findAll();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos);
             CSVWriter csvWriter = new CSVWriter(osw)) {

            // Write header
            String[] header = {
                    "ID", "Product Code", "Product Name", "Description",
                    "Category", "Quantity", "Min Stock Level", "Unit",
                    "Unit Price", "Expiry Date", "Batch Number",
                    "Manufacturer", "Created Date", "Last Updated"
            };
            csvWriter.writeNext(header);

            // Write data
            for (Product product : products) {
                String[] data = {
                        product.getId().toString(),
                        product.getCode() != null ? product.getCode() : "",
                        product.getName(),
                        product.getDescription() != null ? product.getDescription() : "",
                        product.getCategory().getName(),
                        product.getQuantity().toString(),
                        product.getMinStockLevel().toString(),
                        product.getUnit(),
                        product.getUnitPrice().toString(),
                        product.getExpiryDate() != null ? product.getExpiryDate().format(DATE_FORMATTER) : "",
                        product.getBatchNumber() != null ? product.getBatchNumber() : "",
                        product.getManufacturer() != null ? product.getManufacturer() : "",
                        product.getCreatedAt().format(DATETIME_FORMATTER),
                        product.getUpdatedAt() != null ? product.getUpdatedAt().format(DATETIME_FORMATTER) : ""
                };
                csvWriter.writeNext(data);
            }

            csvWriter.flush();
            return baos.toByteArray();
        }
    }

    /**
     * Export all products to Excel format
     */
    public byte[] exportProductsToExcel() throws IOException {
        List<Product> products = productRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Products");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);

            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            // Create currency style
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(createHelper.createDataFormat().getFormat("$#,##0.00"));

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "ID", "Product Code", "Product Name", "Description",
                    "Category", "Quantity", "Min Stock Level", "Unit",
                    "Unit Price", "Expiry Date", "Batch Number",
                    "Manufacturer", "Created Date", "Last Updated"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (Product product : products) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(product.getId());
                row.createCell(1).setCellValue(product.getCode() != null ? product.getCode() : "");
                row.createCell(2).setCellValue(product.getName());
                row.createCell(3).setCellValue(product.getDescription() != null ? product.getDescription() : "");
                row.createCell(4).setCellValue(product.getCategory().getName());
                row.createCell(5).setCellValue(product.getQuantity());
                row.createCell(6).setCellValue(product.getMinStockLevel());
                row.createCell(7).setCellValue(product.getUnit());

                Cell priceCell = row.createCell(8);
                priceCell.setCellValue(product.getUnitPrice().doubleValue());
                priceCell.setCellStyle(currencyStyle);

                if (product.getExpiryDate() != null) {
                    Cell dateCell = row.createCell(9);
                    dateCell.setCellValue(java.sql.Date.valueOf(product.getExpiryDate()));
                    dateCell.setCellStyle(dateStyle);
                } else {
                    row.createCell(9).setCellValue("");
                }

                row.createCell(10).setCellValue(product.getBatchNumber() != null ? product.getBatchNumber() : "");
                row.createCell(11).setCellValue(product.getManufacturer() != null ? product.getManufacturer() : "");
                row.createCell(12).setCellValue(product.getCreatedAt().format(DATETIME_FORMATTER));
                row.createCell(13).setCellValue(product.getUpdatedAt() != null ? product.getUpdatedAt().format(DATETIME_FORMATTER) : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Add summary information
            Row summaryRow = sheet.createRow(rowNum + 2);
            summaryRow.createCell(0).setCellValue("Total Products:");
            summaryRow.createCell(1).setCellValue(products.size());

            Row exportDateRow = sheet.createRow(rowNum + 3);
            exportDateRow.createCell(0).setCellValue("Exported on:");
            exportDateRow.createCell(1).setCellValue(LocalDateTime.now().format(DATETIME_FORMATTER));

            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Generate template for bulk import
     */
    public byte[] generateImportTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Product Import Template");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create instruction style
            CellStyle instructionStyle = workbook.createCellStyle();
            Font instructionFont = workbook.createFont();
            instructionFont.setItalic(true);
            instructionFont.setColor(IndexedColors.DARK_RED.getIndex());
            instructionStyle.setFont(instructionFont);

            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Product Code*", "Product Name*", "Description",
                    "Category Name*", "Quantity*", "Min Stock Level*",
                    "Unit*", "Unit Price*", "Expiry Date",
                    "Batch Number", "Manufacturer"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create instruction row
            Row instructionRow = sheet.createRow(1);
            String[] instructions = {
                    "Optional (auto-generated if empty)",
                    "Required",
                    "Optional",
                    "Must match existing category",
                    "Required (number)",
                    "Required (number)",
                    "e.g., Tablets, Bottles",
                    "Required (decimal)",
                    "Format: YYYY-MM-DD",
                    "Optional",
                    "Optional"
            };

            for (int i = 0; i < instructions.length; i++) {
                Cell cell = instructionRow.createCell(i);
                cell.setCellValue(instructions[i]);
                cell.setCellStyle(instructionStyle);
            }

            // Create sample data
            Row sampleRow = sheet.createRow(2);
            sampleRow.createCell(0).setCellValue("MED001");
            sampleRow.createCell(1).setCellValue("Paracetamol 500mg");
            sampleRow.createCell(2).setCellValue("Pain relief medication");
            sampleRow.createCell(3).setCellValue("Medications");
            sampleRow.createCell(4).setCellValue(100);
            sampleRow.createCell(5).setCellValue(20);
            sampleRow.createCell(6).setCellValue("Tablets");
            sampleRow.createCell(7).setCellValue(5.99);
            sampleRow.createCell(8).setCellValue("2025-12-31");
            sampleRow.createCell(9).setCellValue("BATCH001");
            sampleRow.createCell(10).setCellValue("Generic Pharma");

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Add notes sheet
            Sheet notesSheet = workbook.createSheet("Instructions");
            Row noteRow1 = notesSheet.createRow(0);
            noteRow1.createCell(0).setCellValue("IMPORT INSTRUCTIONS:");

            Row noteRow2 = notesSheet.createRow(2);
            noteRow2.createCell(0).setCellValue("1. Fields marked with * are required");

            Row noteRow3 = notesSheet.createRow(3);
            noteRow3.createCell(0).setCellValue("2. Category Name must match an existing category in the system");

            Row noteRow4 = notesSheet.createRow(4);
            noteRow4.createCell(0).setCellValue("3. Product Code will be auto-generated if left empty");

            Row noteRow5 = notesSheet.createRow(5);
            noteRow5.createCell(0).setCellValue("4. Dates should be in YYYY-MM-DD format");

            Row noteRow6 = notesSheet.createRow(6);
            noteRow6.createCell(0).setCellValue("5. Do not modify the header row");

            Row noteRow7 = notesSheet.createRow(7);
            noteRow7.createCell(0).setCellValue("6. Delete the instruction row (row 2) before importing");

            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}