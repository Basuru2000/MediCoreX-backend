package com.medicorex.service;

import com.medicorex.dto.ImportResultDTO;
import com.medicorex.dto.ImportErrorDTO;
import com.medicorex.entity.Category;
import com.medicorex.entity.Product;
import com.medicorex.repository.CategoryRepository;
import com.medicorex.repository.ProductRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BarcodeService barcodeService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Import products from uploaded file (CSV or Excel)
     */
    public ImportResultDTO importProducts(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        if ("csv".equals(extension)) {
            return importFromCSV(file);
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return importFromExcel(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please upload CSV or Excel file.");
        }
    }

    /**
     * Import products from CSV file
     */
    private ImportResultDTO importFromCSV(MultipartFile file) throws IOException {
        List<ImportErrorDTO> errors = new ArrayList<>();
        List<Product> validProducts = new ArrayList<>();
        int totalRows = 0;

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();

            if (records.isEmpty()) {
                errors.add(new ImportErrorDTO(0, "File is empty"));
                return new ImportResultDTO(0, 0, 0, errors);
            }

            // First pass: Parse and validate all products without saving
            for (int i = 1; i < records.size(); i++) {
                int rowNumber = i + 1;
                totalRows++;
                String[] row = records.get(i);

                // Skip empty rows
                if (isEmptyRow(row)) {
                    continue;
                }

                try {
                    Product product = parseProductFromCSV(row, rowNumber);
                    if (product != null) {
                        validProducts.add(product);
                    }
                } catch (Exception e) {
                    errors.add(new ImportErrorDTO(rowNumber, e.getMessage()));
                    log.error("Error parsing CSV row {}: {}", rowNumber, e.getMessage());
                }
            }

            // If there are any parsing errors, return without saving anything
            if (!errors.isEmpty()) {
                log.warn("CSV import aborted due to {} validation errors. No products were saved.", errors.size());
                return new ImportResultDTO(totalRows, 0, errors.size(), errors);
            }

            // Second pass: Save all valid products in batch
            List<Product> successfulImports = new ArrayList<>();
            try {
                for (Product product : validProducts) {
                    // Save product to get ID
                    Product savedProduct = productRepository.save(product);

                    // Generate barcode if not provided
                    if (savedProduct.getBarcode() == null || savedProduct.getBarcode().isEmpty()) {
                        savedProduct.setBarcode(barcodeService.generateBarcode(savedProduct));
                        productRepository.save(savedProduct);
                    }

                    successfulImports.add(savedProduct);
                }

                // Flush all changes to database
                entityManager.flush();
                log.info("CSV import completed successfully: {} products imported", successfulImports.size());

                return new ImportResultDTO(
                        totalRows,
                        successfulImports.size(),
                        0,
                        errors
                );

            } catch (Exception e) {
                log.error("Error during CSV batch save operation: {}", e.getMessage());
                errors.add(new ImportErrorDTO(0, "Database error during import: " + e.getMessage()));
                return new ImportResultDTO(totalRows, 0, 1, errors);
            }

        } catch (CsvException e) {
            log.error("CSV parsing error: {}", e.getMessage());
            errors.add(new ImportErrorDTO(0, "CSV parsing error: " + e.getMessage()));
            return new ImportResultDTO(0, 0, 1, errors);
        }
    }

    /**
     * Import products from Excel file
     */
    private ImportResultDTO importFromExcel(MultipartFile file) throws IOException {
        List<ImportErrorDTO> errors = new ArrayList<>();
        List<Product> validProducts = new ArrayList<>();
        int totalRows = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null) {
                errors.add(new ImportErrorDTO(0, "Excel file is empty"));
                return new ImportResultDTO(0, 0, 0, errors);
            }

            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            // First pass: Parse and validate all products without saving
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                int rowNumber = row.getRowNum() + 1;
                totalRows++;

                // Skip empty rows
                if (isEmptyRow(row)) {
                    continue;
                }

                try {
                    Product product = parseProductFromExcel(row, rowNumber);
                    if (product != null) {
                        validProducts.add(product);
                    }
                } catch (Exception e) {
                    errors.add(new ImportErrorDTO(rowNumber, e.getMessage()));
                    log.error("Error parsing row {}: {}", rowNumber, e.getMessage());
                }
            }

            // If there are any parsing errors, return without saving anything
            if (!errors.isEmpty()) {
                log.warn("Import aborted due to {} validation errors. No products were saved.", errors.size());
                return new ImportResultDTO(totalRows, 0, errors.size(), errors);
            }

            // Second pass: Save all valid products in batch
            List<Product> successfulImports = new ArrayList<>();
            try {
                for (Product product : validProducts) {
                    // Save product to get ID
                    Product savedProduct = productRepository.save(product);

                    // Generate barcode if not provided
                    if (savedProduct.getBarcode() == null || savedProduct.getBarcode().isEmpty()) {
                        savedProduct.setBarcode(barcodeService.generateBarcode(savedProduct));
                        productRepository.save(savedProduct);
                    }

                    successfulImports.add(savedProduct);
                }

                // Flush all changes to database
                entityManager.flush();
                log.info("Excel import completed successfully: {} products imported", successfulImports.size());

            } catch (Exception e) {
                log.error("Error during batch save operation: {}", e.getMessage());
                errors.add(new ImportErrorDTO(0, "Database error during import: " + e.getMessage()));
                return new ImportResultDTO(totalRows, 0, 1, errors);
            }

            return new ImportResultDTO(
                    totalRows,
                    successfulImports.size(),
                    0,
                    errors
            );
        }
    }

    /**
     * Parse product from CSV row
     */
    private Product parseProductFromCSV(String[] row, int rowNumber) {
        if (row.length < 9) {
            throw new IllegalArgumentException("Insufficient data. Expected at least 9 columns.");
        }

        Product product = new Product();

        // Product Code (optional, auto-generate if empty)
        String code = row[0].trim();
        if (!code.isEmpty()) {
            // Check if code already exists
            if (productRepository.findByCode(code).isPresent()) {
                throw new IllegalArgumentException("Product code '" + code + "' already exists");
            }
            product.setCode(code);
        } else {
            product.setCode(generateProductCode());
        }

        // Barcode (optional, auto-generate if empty)
        if (row.length > 1 && !row[1].trim().isEmpty()) {
            String barcode = row[1].trim();
            if (productRepository.findByBarcode(barcode).isPresent()) {
                throw new IllegalArgumentException("Barcode '" + barcode + "' already exists");
            }
            if (!barcodeService.isValidBarcode(barcode)) {
                throw new IllegalArgumentException("Invalid barcode format: " + barcode);
            }
            product.setBarcode(barcode);
        }

        // Product Name (required)
        String name = row[2].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        product.setName(name);

        // Description (optional)
        if (row.length > 3) {
            product.setDescription(row[3].trim());
        }

        // Category (required)
        String categoryName = row[4].trim();
        Category category = categoryRepository.findByName(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Category '" + categoryName + "' not found"));
        product.setCategory(category);

        // Quantity (required)
        try {
            product.setQuantity(Integer.parseInt(row[5].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid quantity: " + row[5]);
        }

        // Min Stock Level (required)
        try {
            product.setMinStockLevel(Integer.parseInt(row[6].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid minimum stock level: " + row[6]);
        }

        // Unit (required)
        String unit = row[7].trim();
        if (unit.isEmpty()) {
            throw new IllegalArgumentException("Unit is required");
        }
        product.setUnit(unit);

        // Unit Price (required)
        try {
            product.setUnitPrice(new BigDecimal(row[8].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid unit price: " + row[8]);
        }

        // Expiry Date (optional)
        if (row.length > 9 && !row[9].trim().isEmpty()) {
            try {
                product.setExpiryDate(LocalDate.parse(row[9].trim(), DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid expiry date format. Use YYYY-MM-DD");
            }
        }

        // Batch Number (optional)
        if (row.length > 10) {
            product.setBatchNumber(row[10].trim());
        }

        // Manufacturer (optional)
        if (row.length > 11) {
            product.setManufacturer(row[11].trim());
        }

        return product;
    }

    /**
     * Parse product from Excel row
     */
    private Product parseProductFromExcel(Row row, int rowNumber) {
        Product product = new Product();

        // Product Code (optional, auto-generate if empty)
        String code = getCellValueAsString(row.getCell(0));
        if (!code.isEmpty()) {
            // Check if code already exists
            if (productRepository.findByCode(code).isPresent()) {
                throw new IllegalArgumentException("Product code '" + code + "' already exists");
            }
            product.setCode(code);
        } else {
            product.setCode(generateProductCode());
        }

        // Barcode (optional, will be generated after save if empty)
        String barcode = getCellValueAsString(row.getCell(1));
        if (!barcode.isEmpty()) {
            if (productRepository.findByBarcode(barcode).isPresent()) {
                throw new IllegalArgumentException("Barcode '" + barcode + "' already exists");
            }
            if (!barcodeService.isValidBarcode(barcode)) {
                throw new IllegalArgumentException("Invalid barcode format: " + barcode);
            }
            product.setBarcode(barcode);
        }

        // Product Name (required)
        String name = getCellValueAsString(row.getCell(2));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        product.setName(name);

        // Description (optional)
        product.setDescription(getCellValueAsString(row.getCell(3)));

        // Category (required)
        String categoryName = getCellValueAsString(row.getCell(4));
        if (categoryName.isEmpty()) {
            throw new IllegalArgumentException("Category is required");
        }

        Category category = categoryRepository.findByNameIgnoreCase(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Category '" + categoryName + "' not found"));
        product.setCategory(category);

        // Quantity (required)
        product.setQuantity(getCellValueAsInteger(row.getCell(5)));

        // Min Stock Level (required)
        product.setMinStockLevel(getCellValueAsInteger(row.getCell(6)));

        // Unit (required)
        String unit = getCellValueAsString(row.getCell(7));
        if (unit.isEmpty()) {
            throw new IllegalArgumentException("Unit is required");
        }
        product.setUnit(unit);

        // Unit Price (required)
        product.setUnitPrice(getCellValueAsBigDecimal(row.getCell(8)));

        // Expiry Date (optional)
        if (row.getCell(9) != null) {
            String expiryDateStr = getCellValueAsString(row.getCell(9));
            if (!expiryDateStr.isEmpty()) {
                try {
                    product.setExpiryDate(LocalDate.parse(expiryDateStr, DATE_FORMATTER));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid expiry date format. Use YYYY-MM-DD");
                }
            }
        }

        // Batch Number (optional)
        if (row.getCell(10) != null) {
            product.setBatchNumber(getCellValueAsString(row.getCell(10)));
        }

        // Manufacturer (optional)
        if (row.getCell(11) != null) {
            product.setManufacturer(getCellValueAsString(row.getCell(11)));
        }

        // Set timestamps
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        return product;
    }

    /**
     * Generate unique product code
     */
    private String generateProductCode() {
        String prefix = "PROD";
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(7);
        String random = String.valueOf((int)(Math.random() * 1000));
        return prefix + timestamp + random;
    }

    /**
     * Check if CSV row is empty
     */
    private boolean isEmptyRow(String[] row) {
        return Arrays.stream(row).allMatch(cell -> cell == null || cell.trim().isEmpty());
    }

    /**
     * Check if Excel row is empty
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }

        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (!value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get cell value as String
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Get cell value as Integer
     */
    private int getCellValueAsInteger(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("Required field is empty");
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                try {
                    return Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number: " + cell.getStringCellValue());
                }
            default:
                throw new IllegalArgumentException("Invalid number format");
        }
    }

    /**
     * Get cell value as BigDecimal
     */
    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) {
            throw new IllegalArgumentException("Required field is empty");
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                try {
                    return new BigDecimal(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid numeric value: " + cell.getStringCellValue());
                }
            default:
                throw new IllegalArgumentException("Invalid cell type for numeric value");
        }
    }

    /**
     * Get cell value as LocalDate
     */
    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                }
                throw new IllegalArgumentException("Invalid date format");
            case STRING:
                String dateStr = cell.getStringCellValue().trim();
                if (dateStr.isEmpty()) {
                    return null;
                }
                try {
                    return LocalDate.parse(dateStr, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD");
                }
            default:
                return null;
        }
    }
}