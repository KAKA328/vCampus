package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证最新版 schema.sql 和 seed.sql 可以直接初始化全新 Access 数据库。 */
class AccessCourseDatabaseInitializationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void newAccessDatabaseUsesSchemaSeedAndDatabaseBackedCourseRuntime() throws Exception {
        Path database = temporaryDirectory.resolve("vcampus-new.accdb");
        executeSqlScript(database, locateDatabaseScript("schema.sql"), true);
        executeSqlScript(database, locateDatabaseScript("seed.sql"), false);

        CourseServiceFactory.CourseRuntime runtime = CourseServiceFactory.create(new String[] {
                "--db", database.toString() });

        assertTrue(runtime.getModule().getCatalogService() instanceof AccessCourseCatalogService);
        assertTrue(runtime.getModule().getOfferingService() instanceof AccessCourseOfferingService);
        assertTrue(runtime.getModule().getSelectionRoundService() instanceof AccessSelectionRoundService);
        assertTrue(runtime.getModule().getGradeSubmissionService()
                instanceof AccessGradeSubmissionService);
        assertEquals(StatusCode.OK, runtime.getModule().getCatalogService()
                .findById("JAVA101").getStatus());
        assertEquals(StatusCode.OK, runtime.getModule().getOfferingService()
                .findById("offering-java-2025a").getStatus());

        assertEquals(StatusCode.OK, runtime.getModule().getCatalogService()
                .create(new Course("TEST101", "全新数据库验证课程", 1)).getStatus());
        assertEquals(StatusCode.OK, new AccessCourseCatalogService(database)
                .findById("TEST101").getStatus());
    }

    private static void executeSqlScript(Path database, Path script, boolean createDatabase)
            throws Exception {
        String url = "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true"
                + (createDatabase ? ";newDatabaseVersion=V2010" : "");
        String sql = removeCommentLines(new String(Files.readAllBytes(script), StandardCharsets.UTF_8));
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                String normalized = command.trim();
                if (!normalized.isEmpty()) {
                    try {
                        statement.execute(normalized);
                    } catch (SQLException failure) {
                        throw new SQLException("cannot execute database script statement: "
                                + normalized, failure);
                    }
                }
            }
        }
    }

    private static String removeCommentLines(String sql) {
        StringBuilder result = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (!line.trim().startsWith("--")) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static Path locateDatabaseScript(String fileName) {
        Path direct = Paths.get("database", fileName);
        if (Files.isRegularFile(direct)) return direct;
        Path parent = Paths.get("..", "database", fileName).normalize();
        if (Files.isRegularFile(parent)) return parent;
        throw new IllegalStateException("cannot locate database script: " + fileName);
    }
}
