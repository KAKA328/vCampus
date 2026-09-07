package cn.vcampus.course;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** 解析统一的“学号、成绩”成绩导入文件，不负责权限或教学班归属校验。 */
public final class GradeImportFileParser {
    private static final String STUDENT_ID_HEADER = "学号";
    private static final String SCORE_HEADER = "成绩";

    private GradeImportFileParser() {
    }

    public static List<GradeImportRow> parse(String fileName, byte[] content) {
        if (fileName == null || content == null) {
            throw new IllegalArgumentException("fileName and content must not be null");
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return parseCsv(content);
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return parseExcel(content);
        throw new IllegalArgumentException("only .csv, .xls and .xlsx grade files are supported");
    }

    private static List<GradeImportRow> parseCsv(byte[] content) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("grade file is empty");
            Map<String, Integer> columns = columns(parseCsvLine(removeBom(header)), 1);
            List<GradeImportRow> result = new ArrayList<GradeImportRow>();
            Set<String> studentIds = new HashSet<String>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                List<String> values = parseCsvLine(line);
                if (allBlank(values)) continue;
                result.add(row(values, columns, rowNumber, studentIds));
            }
            return requireRows(result);
        } catch (IOException impossible) {
            throw new IllegalArgumentException("failed to read CSV grade file", impossible);
        }
    }

    private static List<GradeImportRow> parseExcel(byte[] content) {
        try (InputStream input = new ByteArrayInputStream(content);
                Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() < 1) {
                throw new IllegalArgumentException("Excel file has no worksheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = sheet.getFirstRowNum();
            Row header = sheet.getRow(headerIndex);
            if (header == null) throw new IllegalArgumentException("first worksheet has no header row");
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<String> headers = values(header, formatter, headerIndex + 1);
            Map<String, Integer> columns = columns(headers, headerIndex + 1);
            List<GradeImportRow> result = new ArrayList<GradeImportRow>();
            Set<String> studentIds = new HashSet<String>();
            for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                List<String> values = values(row, formatter, index + 1);
                if (allBlank(values)) continue;
                result.add(row(values, columns, index + 1, studentIds));
            }
            return requireRows(result);
        } catch (IllegalArgumentException invalidFile) {
            throw invalidFile;
        } catch (Exception failure) {
            throw new IllegalArgumentException("failed to read Excel grade file", failure);
        }
    }

    private static List<String> values(Row row, DataFormatter formatter, int rowNumber) {
        int lastCell = Math.max(row.getLastCellNum(), 0);
        List<String> values = new ArrayList<String>();
        for (int index = 0; index < lastCell; index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                throw new IllegalArgumentException("row " + rowNumber
                        + " contains a formula; grade cells must contain literal values");
            }
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private static Map<String, Integer> columns(List<String> headers, int rowNumber) {
        Map<String, Integer> columns = new HashMap<String, Integer>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index) == null ? "" : headers.get(index).trim();
            if (!header.isEmpty()) columns.put(header, Integer.valueOf(index));
        }
        if (!columns.containsKey(STUDENT_ID_HEADER) || !columns.containsKey(SCORE_HEADER)) {
            throw new IllegalArgumentException("row " + rowNumber
                    + " must contain headers 学号 and 成绩");
        }
        return columns;
    }

    private static GradeImportRow row(List<String> values, Map<String, Integer> columns,
            int rowNumber, Set<String> studentIds) {
        String studentId = value(values, columns.get(STUDENT_ID_HEADER).intValue()).trim();
        String scoreText = value(values, columns.get(SCORE_HEADER).intValue()).trim();
        if (studentId.isEmpty() || scoreText.isEmpty()) {
            throw new IllegalArgumentException("row " + rowNumber
                    + " must contain both 学号 and 成绩");
        }
        if (!studentIds.add(studentId)) {
            throw new IllegalArgumentException("row " + rowNumber + " has duplicate 学号: " + studentId);
        }
        if (!scoreText.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("row " + rowNumber + " has invalid 成绩");
        }
        int score = Integer.parseInt(scoreText);
        if (score > 100) throw new IllegalArgumentException("row " + rowNumber
                + " has a 成绩 outside 0 to 100");
        return new GradeImportRow(rowNumber, studentId, score);
    }

    private static List<GradeImportRow> requireRows(List<GradeImportRow> result) {
        if (result.isEmpty()) throw new IllegalArgumentException("grade file has no data rows");
        return result;
    }

    private static String value(List<String> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }

    private static boolean allBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String removeBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<String>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV has an unclosed quoted value");
        values.add(value.toString().trim());
        return values;
    }
}
