package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.user.UserImportRow;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserImportFileReaderTest {
    @TempDir Path tempDir;

    @Test
    void readsCsvWithChineseHeaders() throws Exception {
        Path file = tempDir.resolve("users.csv");
        Files.write(file, java.util.Arrays.asList(
                "账号,姓名,初始密码,角色",
                "stu1001,张三,Secret123,STUDENT",
                "tea1001,李老师,Teacher1,TEACHER"), StandardCharsets.UTF_8);

        List<UserImportRow> rows = new UserImportFileReader().read(file);

        assertEquals(2, rows.size());
        assertEquals("stu1001", rows.get(0).getUserId());
        assertEquals("Secret123", rows.get(0).getPassword());
        assertEquals(Role.TEACHER.name(), rows.get(1).getRoleCode());
    }

    @Test
    void readsTsvWithEnglishHeaders() throws Exception {
        Path file = tempDir.resolve("users.tsv");
        Files.write(file, java.util.Arrays.asList(
                "userId\tdisplayName\tpassword\troleCode",
                "stu1002\t学生乙\tDemo123\tSTUDENT"), StandardCharsets.UTF_8);

        List<UserImportRow> rows = new UserImportFileReader().read(file);

        assertEquals(1, rows.size());
        assertEquals("学生乙", rows.get(0).getDisplayName());
    }

    @Test
    void readsXlsxFirstSheetWithSharedStrings() throws Exception {
        Path file = tempDir.resolve("users.xlsx");
        writeMinimalXlsx(file,
                new String[] {"账号", "姓名", "初始密码", "角色", "stu1003", "学生丙", "Demo123", "STUDENT"});

        List<UserImportRow> rows = new UserImportFileReader().read(file);

        assertEquals(1, rows.size());
        assertEquals("stu1003", rows.get(0).getUserId());
        assertEquals("学生丙", rows.get(0).getDisplayName());
    }

    @Test
    void rejectsUnsupportedFileType() {
        Path file = tempDir.resolve("users.accdb");

        assertThrows(IllegalArgumentException.class, () -> new UserImportFileReader().read(file));
    }

    private static void writeMinimalXlsx(Path file, String[] sharedStrings) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            entry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "</Types>");
            entry(zip, "xl/sharedStrings.xml", sharedStrings(sharedStrings));
            entry(zip, "xl/worksheets/sheet1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheetData>"
                    + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
                    + "<c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
                    + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>4</v></c><c r=\"B2\" t=\"s\"><v>5</v></c>"
                    + "<c r=\"C2\" t=\"s\"><v>6</v></c><c r=\"D2\" t=\"s\"><v>7</v></c></row>"
                    + "</sheetData></worksheet>");
        }
    }

    private static String sharedStrings(String[] values) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String value : values) {
            xml.append("<si><t>").append(value).append("</t></si>");
        }
        xml.append("</sst>");
        return xml.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStreamWriter writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        writer.write(content);
        writer.flush();
        zip.closeEntry();
    }
}
