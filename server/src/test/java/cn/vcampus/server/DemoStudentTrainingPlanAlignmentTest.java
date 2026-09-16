package cn.vcampus.server;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DemoStudentTrainingPlanAlignmentTest {
    @TempDir Path directory;

    @Test void defaultStudentPassedCoursesExactlyMatchPublishedRequiredPlan() throws Exception {
        Path database = directory.resolve("demo-alignment.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        AccessDatabaseSchemaTest.executeScript(database,
                AccessDatabaseSchemaTest.readScript("database/schema.sql"));
        AccessDatabaseSchemaTest.executeScript(database,
                AccessDatabaseSchemaTest.readScript("database/seed.sql"));

        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true")) {
            Map<String, Integer> required = courseCredits(connection,
                    "SELECT pc.course_id,c.credits FROM ((tblStudent s INNER JOIN tblTrainingPlan p "
                            + "ON s.major_name=p.major_name AND s.enrollment_year=p.enrollment_year) "
                            + "INNER JOIN tblTrainingPlanCourse pc ON p.plan_id=pc.plan_id) "
                            + "INNER JOIN tblCourse c ON pc.course_id=c.course_id "
                            + "WHERE s.student_id=? AND p.status='PUBLISHED' "
                            + "AND pc.selection_type='REQUIRED'",
                    "20260001");
            Map<String, Integer> passed = courseCredits(connection,
                    "SELECT course_id,MAX(earned_credits) FROM tblCourseResult "
                            + "WHERE student_id=? AND passed=1 GROUP BY course_id",
                    "20260001");

            assertEquals(required, passed);
            assertEquals(11, required.values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(0, activePassedCourseSelections(connection, "20260001"),
                    "默认学生不应再选已通过的必修课");
        }
    }

    @Test void checkedInDatabaseMatchesSeededFractionalCreditScenario() throws Exception {
        Path database = locateCheckedInDatabase();
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true")) {
            Map<String, BigDecimal> firstStudent = decimalCourseCredits(connection, "20260001");
            assertEquals(new BigDecimal("2.00"), firstStudent.get("AI101"));
            assertEquals(new BigDecimal("3.00"), firstStudent.get("DS101"));
            assertEquals(new BigDecimal("3.00"), firstStudent.get("JAVA101"));
            assertEquals(new BigDecimal("3.00"), firstStudent.get("NET101"));
            assertEquals(4, firstStudent.size());

            Map<String, BigDecimal> fractionalStudent = decimalCourseCredits(connection, "20260002");
            assertEquals(new BigDecimal("3.00"), fractionalStudent.get("NET101"));
            assertEquals(new BigDecimal("2.50"), fractionalStudent.get("WEB101"));
            assertEquals(2, fractionalStudent.size());
        }
    }

    private static Map<String, Integer> courseCredits(Connection connection, String sql,
            String studentId) throws Exception {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), rows.getInt(2));
            }
        }
        return result;
    }

    private static int activePassedCourseSelections(Connection connection, String studentId)
            throws Exception {
        String sql = "SELECT COUNT(*) FROM (tblCourseSelection s INNER JOIN tblCourseOffering o "
                + "ON s.offering_id=o.offering_id) INNER JOIN tblCourseResult r "
                + "ON s.student_id=r.student_id AND o.course_id=r.course_id "
                + "WHERE s.student_id=? AND s.status='ACTIVE' AND r.passed=1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static Map<String, BigDecimal> decimalCourseCredits(Connection connection,
            String studentId) throws Exception {
        Map<String, BigDecimal> result = new LinkedHashMap<String, BigDecimal>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT course_id,MAX(earned_credits) FROM tblCourseResult "
                        + "WHERE student_id=? AND passed=1 GROUP BY course_id")) {
            statement.setString(1, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getString(1), rows.getBigDecimal(2));
            }
        }
        return result;
    }

    private static Path locateCheckedInDatabase() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("database").resolve("vCampus.accdb");
            if (java.nio.file.Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("database/vCampus.accdb not found");
    }
}
