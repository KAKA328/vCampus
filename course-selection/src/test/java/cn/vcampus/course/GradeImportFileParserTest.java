package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** 三种成绩文件格式必须解析为同一套“学号、成绩”数据。 */
class GradeImportFileParserTest {
    @Test
    void parsesUtf8CsvWithBom() {
        byte[] content = ("\uFEFF学号,成绩\nS001,86\nS002,59\n")
                .getBytes(StandardCharsets.UTF_8);

        List<GradeImportRow> rows = GradeImportFileParser.parse("grades.csv", content);

        assertEquals(2, rows.size());
        assertEquals("S001", rows.get(0).getStudentId());
        assertEquals(59, rows.get(1).getScore());
    }

    @Test
    void rejectsCsvWithDuplicateStudentId() {
        byte[] content = "学号,成绩\nS001,86\nS001,90\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> GradeImportFileParser.parse("grades.csv", content));
    }

    @Test
    void parsesXlsxAndLegacyXls() throws Exception {
        assertEquals(88, GradeImportFileParser.parse("grades.xlsx", excel(new XSSFWorkbook(), 88))
                .get(0).getScore());
        assertEquals(72, GradeImportFileParser.parse("grades.xls", excel(new HSSFWorkbook(), 72))
                .get(0).getScore());
    }

    private static byte[] excel(Workbook workbook, int score) throws Exception {
        try (Workbook closingWorkbook = workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = closingWorkbook.createSheet("成绩");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("成绩");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("S001");
            row.createCell(1).setCellValue(score);
            closingWorkbook.write(output);
            return output.toByteArray();
        }
    }
}
