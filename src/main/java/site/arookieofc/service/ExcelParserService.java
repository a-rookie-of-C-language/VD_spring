package site.arookieofc.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.arookieofc.service.dto.BatchImportRecordDTO;

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
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

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

                String studentNo = getCellValueAsString(cell, evaluator);
                if (studentNo != null && !studentNo.trim().isEmpty()) {
                    studentNumbers.add(studentNo.trim());
                }
            }

            log.info("Parsed {} student numbers from Excel file", studentNumbers.size());
        } catch (IOException | EncryptedDocumentException | IllegalArgumentException e) {
            log.error("Failed to parse Excel file", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return studentNumbers;
    }

    /**
     * Get cell value as string regardless of cell type
     */
    private String getCellValueAsString(Cell cell, FormulaEvaluator evaluator) {
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
                return getFormulaValueAsString(cell, evaluator);
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private String getFormulaValueAsString(Cell cell, FormulaEvaluator evaluator) {
        if (evaluator == null) {
            return cell.getCellFormula();
        }
        CellValue evaluated = evaluator.evaluate(cell);
        if (evaluated == null) {
            return "";
        }
        return switch (evaluated.getCellType()) {
            case STRING -> evaluated.getStringValue();
            case NUMERIC -> {
                double numericValue = evaluated.getNumberValue();
                if (numericValue == (long) numericValue) {
                    yield String.valueOf((long) numericValue);
                }
                yield String.valueOf(numericValue);
            }
            case BOOLEAN -> String.valueOf(evaluated.getBooleanValue());
            case BLANK -> "";
            default -> "";
        };
    }

    /**
     * Parse batch import records from Excel file
     * Expected columns: 姓名、性别、学院、年级、学号、联系方式、服务时长（小时）、活动名称
     * First two rows are assumed to be title and header and are skipped
     *
     * @param file Excel file
     * @return List of batch import records
     * @throws IOException if file cannot be read
     */
    public List<BatchImportRecordDTO> parseBatchImportRecords(MultipartFile file) throws IOException {
        List<BatchImportRecordDTO> records = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            return records;
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Skip title row (row 0) and header row (row 1), start from row 2
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                // 按列顺序读取: 姓名、性别、学院、年级、学号、联系方式、服务时长、活动名称
                String username = getCellValueAsString(row.getCell(0), evaluator);
                String gender = getCellValueAsString(row.getCell(1), evaluator);
                String college = getCellValueAsString(row.getCell(2), evaluator);
                String grade = getCellValueAsString(row.getCell(3), evaluator);
                String studentNo = getCellValueAsString(row.getCell(4), evaluator);
                String phone = getCellValueAsString(row.getCell(5), evaluator);
                String durationStr = getCellValueAsString(row.getCell(6), evaluator);
                String activityName = getCellValueAsString(row.getCell(7), evaluator);

                // 跳过学号为空的行
                if (isBlankRow(username, gender, college, grade, studentNo, phone, durationStr, activityName)) {
                    continue;
                }

                // 解析服务时长
                Double duration = null;
                if (durationStr != null && !durationStr.trim().isEmpty()) {
                    try {
                        duration = Double.parseDouble(durationStr.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid duration format at row {}: {}", i + 1, durationStr);
                    }
                }

                BatchImportRecordDTO record = BatchImportRecordDTO.builder()
                        .username(username != null ? username.trim() : null)
                        .gender(gender != null ? gender.trim() : null)
                        .college(college != null ? college.trim() : null)
                        .grade(grade != null ? grade.trim() : null)
                        .studentNo(studentNo != null ? studentNo.trim() : null)
                        .phone(phone != null ? phone.trim() : null)
                        .duration(duration)
                        .activityName(activityName != null ? activityName.trim() : null)
                        .build();

                records.add(record);
            }

            log.info("Parsed {} batch import records from Excel file", records.size());
        } catch (IOException | EncryptedDocumentException | IllegalArgumentException e) {
            log.error("Failed to parse Excel file for batch import", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return records;
    }

    private boolean isBlankRow(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
