package site.arookieofc.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing Excel files
 */
@Slf4j
@Service
public class ExcelParserService {

    /**
     * Parse student numbers from Excel file
     * Expected format: First column contains student numbers
     * First row is assumed to be header and is skipped
     *
     * @param file Excel file
     * @return List of student numbers
     * @throws IOException if file cannot be read
     */
    public List<String> parseStudentNumbers(MultipartFile file) throws IOException {
        List<String> studentNumbers = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            return studentNumbers;
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row (row 0), start from row 1
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Cell cell = row.getCell(0);
                if (cell == null) {
                    continue;
                }

                String studentNo = getCellValueAsString(cell);
                if (studentNo != null && !studentNo.trim().isEmpty()) {
                    studentNumbers.add(studentNo.trim());
                }
            }

            log.info("Parsed {} student numbers from Excel file", studentNumbers.size());
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return studentNumbers;
    }

    /**
     * Get cell value as string regardless of cell type
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Handle numeric values (in case student numbers are stored as numbers)
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Format as integer if it's a whole number
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}

