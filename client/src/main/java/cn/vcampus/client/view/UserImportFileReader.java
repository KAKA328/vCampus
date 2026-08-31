package cn.vcampus.client.view;

import cn.vcampus.user.UserImportRow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads administrator account import rows from Excel-compatible files. */
final class UserImportFileReader {
    List<UserImportRow> read(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("请选择导入文件");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return rowsFromTable(UserImportDelimitedFile.read(file, ','));
        }
        if (name.endsWith(".tsv")) {
            return rowsFromTable(UserImportDelimitedFile.read(file, '\t'));
        }
        if (name.endsWith(".xlsx")) {
            return rowsFromTable(UserImportXlsxFile.read(file));
        }
        throw new IllegalArgumentException("仅支持 .xlsx、.csv、.tsv 导入文件");
    }

    private static List<UserImportRow> rowsFromTable(List<List<String>> table) {
        if (table.isEmpty()) {
            return new ArrayList<UserImportRow>();
        }
        Map<String, Integer> headers = headers(table.get(0));
        int userId = requireColumn(headers, "userId", "账号");
        int displayName = requireColumn(headers, "displayName", "姓名");
        int password = requireColumn(headers, "password", "初始密码");
        int roleCode = requireColumn(headers, "roleCode", "角色");
        int profileId = optionalColumn(headers, "profileId", "档案编号");
        List<UserImportRow> rows = new ArrayList<UserImportRow>();
        for (int i = 1; i < table.size(); i++) {
            List<String> values = table.get(i);
            if (isBlank(values)) {
                continue;
            }
            rows.add(new UserImportRow(
                    value(values, userId),
                    value(values, password),
                    value(values, displayName),
                    value(values, roleCode),
                    profileId < 0 ? "" : value(values, profileId)));
        }
        return rows;
    }

    private static Map<String, Integer> headers(List<String> row) {
        Map<String, Integer> headers = new HashMap<String, Integer>();
        for (int i = 0; i < row.size(); i++) {
            headers.put(normalizeHeader(row.get(i)), i);
        }
        return headers;
    }

    private static int requireColumn(Map<String, Integer> headers, String english, String chinese) {
        Integer column = headers.get(normalizeHeader(english));
        if (column == null) {
            column = headers.get(normalizeHeader(chinese));
        }
        if (column == null) {
            throw new IllegalArgumentException("导入文件缺少列：" + chinese);
        }
        return column.intValue();
    }

    private static int optionalColumn(Map<String, Integer> headers, String english, String chinese) {
        Integer column = headers.get(normalizeHeader(english));
        if (column == null) {
            column = headers.get(normalizeHeader(chinese));
        }
        return column == null ? -1 : column.intValue();
    }

    private static String normalizeHeader(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("userid".equals(text) || "user_id".equals(text) || "登录账号".equals(text)) return "userid";
        if ("账号".equals(text)) return "userid";
        if ("displayname".equals(text) || "display_name".equals(text) || "name".equals(text)) return "displayname";
        if ("姓名".equals(text)) return "displayname";
        if ("password".equals(text) || "passwd".equals(text)) return "password";
        if ("初始密码".equals(text) || "密码".equals(text)) return "password";
        if ("rolecode".equals(text) || "role_code".equals(text) || "role".equals(text)) return "rolecode";
        if ("角色".equals(text)) return "rolecode";
        if ("profileid".equals(text) || "profile_id".equals(text)
                || "studentid".equals(text) || "student_id".equals(text)
                || "teacherid".equals(text) || "teacher_id".equals(text)) return "profileid";
        if ("档案编号".equals(text) || "学号".equals(text) || "工号".equals(text)
                || "学生编号".equals(text) || "教师编号".equals(text)) return "profileid";
        return text;
    }

    private static boolean isBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String value(List<String> values, int index) {
        return index < values.size() && values.get(index) != null ? values.get(index).trim() : "";
    }
}
