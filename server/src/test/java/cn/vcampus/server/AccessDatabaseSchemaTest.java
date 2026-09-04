package cn.vcampus.server;

import cn.vcampus.student.TeacherProfile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证全新 Access 数据库可以由正式 schema 和 seed 脚本建立。 */
class AccessDatabaseSchemaTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freshAccessDatabaseCanBuildFromSchemaAndSeed() throws Exception {
        Path database = temporaryDirectory.resolve("fresh-vcampus.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");

        executeScript(database, readScript("database/schema.sql"));
        executeScript(database, readScript("database/seed.sql"));

        assertEquals(6, count(database, "tblUser"));
        assertEquals(1, countWhere(database, "tblStudent", "student_id", "demo_student"));
        assertEquals(1, countWhere(database, "tblTeacher", "teacher_id", "demo_teacher"));
        assertEquals(5, count(database, "tblProduct"));
        assertTrue(Files.exists(database));
    }

    @Test
    void legacyDatabaseWithoutTeacherTableCanApplyTeacherProfileMigration() throws Exception {
        Path database = temporaryDirectory.resolve("legacy-vcampus.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblLegacyMarker (marker_id INTEGER NOT NULL, "
                    + "PRIMARY KEY (marker_id))");
        }

        executeScript(database, readScript("database/migrations/013_teacher_profile.up.sql"));
        AccessTeacherRepository teachers = new AccessTeacherRepository(database);
        teachers.save(new TeacherProfile("T001", "teacher001", "测试教师",
                "计算机学院", "讲师", true));

        assertEquals(1, count(database, "tblTeacher"));
        assertTrue(teachers.findByUserId("teacher001").isActive());
    }

    private static String readScript(String file) throws IOException {
        return new String(Files.readAllBytes(repositoryRoot().resolve(file)), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("database").resolve("schema.sql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    private static void executeScript(Path database, String script) throws SQLException, IOException {
        StringBuilder withoutComments = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(script));
        String line;
        while ((line = reader.readLine()) != null) {
            int commentStart = line.indexOf("--");
            if (commentStart >= 0) {
                line = line.substring(0, commentStart);
            }
            withoutComments.append(line).append('\n');
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            for (String sql : withoutComments.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    try {
                        statement.execute(sql.trim());
                    } catch (SQLException failure) {
                        throw new SQLException("failed SQL: " + sql.trim(), failure);
                    }
                }
            }
        }
    }

    private static int count(Path database, String table) throws SQLException {
        try (Connection connection = open(database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static int countWhere(Path database, String table, String column, String value)
            throws SQLException {
        try (Connection connection = open(database);
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static Connection open(Path database) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }
}
