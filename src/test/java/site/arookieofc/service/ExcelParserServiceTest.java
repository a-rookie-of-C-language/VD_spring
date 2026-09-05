package site.arookieofc.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import site.arookieofc.service.dto.BatchImportRecordDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelParserServiceTest {

    private final ExcelParserService service = new ExcelParserService();

    @Test
    void parseBatchImportRecordsKeepsNonBlankRowsWithMissingStudentNo() throws Exception {
        MockMultipartFile file = excelFile(workbookWithMissingStudentNoRow());

        List<BatchImportRecordDTO> records = service.parseBatchImportRecords(file);

        assertEquals(1, records.size());
        assertEquals("Alice", records.get(0).getUsername());
        assertNull(records.get(0).getStudentNo());
        assertEquals("Cleanup", records.get(0).getActivityName());
    }

    @Test
    void parseBatchImportRecordsUsesFormulaResultsInsteadOfFormulaText() throws Exception {
        MockMultipartFile file = excelFile(workbookWithFormulaCells());

        List<BatchImportRecordDTO> records = service.parseBatchImportRecords(file);

        assertEquals(1, records.size());
        assertEquals("20240001", records.get(0).getStudentNo());
        assertEquals(2.0, records.get(0).getDuration());
    }

    @Test
    void parseStudentNumbersWrapsMalformedWorkbookWithCause() {
        MockMultipartFile file = malformedExcelFile();

        IOException ex = assertThrows(IOException.class, () -> service.parseStudentNumbers(file));

        assertEquals("Failed to parse Excel file: Can't open workbook - unsupported file type: UNKNOWN",
                ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    void parseBatchImportRecordsWrapsMalformedWorkbookWithCause() {
        MockMultipartFile file = malformedExcelFile();

        IOException ex = assertThrows(IOException.class, () -> service.parseBatchImportRecords(file));

        assertEquals("Failed to parse Excel file: Can't open workbook - unsupported file type: UNKNOWN",
                ex.getMessage());
        assertNotNull(ex.getCause());
    }

    private MockMultipartFile malformedExcelFile() {
        return new MockMultipartFile(
                "file",
                "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not an excel workbook".getBytes());
    }

    private MockMultipartFile excelFile(byte[] content) {
        return new MockMultipartFile(
                "file",
                "batch.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content);
    }

    private byte[] workbookWithMissingStudentNoRow() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("import");
            sheet.createRow(0).createCell(0).setCellValue("title");
            sheet.createRow(1).createCell(0).setCellValue("header");
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("Alice");
            row.createCell(6).setCellValue(2.0);
            row.createCell(7).setCellValue("Cleanup");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] workbookWithFormulaCells() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("import");
            sheet.createRow(0).createCell(0).setCellValue("title");
            sheet.createRow(1).createCell(0).setCellValue("header");
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue("Alice");
            row.createCell(4).setCellFormula("\"2024\"&\"0001\"");
            row.createCell(6).setCellFormula("1+1");
            row.createCell(7).setCellValue("Cleanup");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
