package cn.vcampus.server;

import cn.vcampus.student.*;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** All writes for an assessment or graduation take place on the same JDBC transaction. */
final class AccessAcademicAdminStore implements AcademicAdminStore {
    private final Path database;
    AccessAcademicAdminStore(Path database) { this.database = database.toAbsolutePath().normalize(); }

    @Override public synchronized <T> T transaction(Work<T> work) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database
                + ";immediatelyReleaseResources=true")) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(new AccessContext(connection));
                connection.commit();
                return result;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }
    private static final class AccessContext implements Context {
        private final Connection connection;
        AccessContext(Connection connection) { this.connection = connection; }
        @Override public List<StudentRecord> students() throws SQLException {
            List<StudentRecord> rows = new ArrayList<StudentRecord>();
            try (PreparedStatement query = connection.prepareStatement("SELECT * FROM tblStudent ORDER BY student_id");
                    ResultSet result = query.executeQuery()) {
                while (result.next()) rows.add(AccessStudentRepository.readRecord(result));
            }
            return rows;
        }
        @Override public List<TeacherProfile> teachers() throws SQLException {
            List<TeacherProfile> rows = new ArrayList<TeacherProfile>();
            try (PreparedStatement query = connection.prepareStatement("SELECT * FROM tblTeacher ORDER BY teacher_id");
                    ResultSet result = query.executeQuery()) {
                while (result.next()) rows.add(AccessTeacherRepository.read(result));
            }
            return rows;
        }
        @Override public StudentRecord student(String id) throws SQLException {
            try (PreparedStatement query = connection.prepareStatement("SELECT * FROM tblStudent WHERE student_id=?")) {
                query.setString(1, id);
                try (ResultSet result = query.executeQuery()) {
                    return result.next() ? AccessStudentRepository.readRecord(result) : null;
                }
            }
        }
        @Override public List<CourseHistoryRecord> history(String id) throws SQLException {
            return AccessAcademicReviewService.readHistory(connection, id);
        }
        @Override public List<AcademicAssessment> assessments(String id) throws SQLException {
            List<AcademicAssessment> rows = new ArrayList<AcademicAssessment>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM tblAcademicAssessment WHERE student_id=? ORDER BY assessment_order DESC")) {
                query.setString(1, id);
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) rows.add(new AcademicAssessment(result.getString("assessment_id"),
                            new CreditSummary(id, result.getInt("earned_credits"), result.getInt("passed_courses"),
                                    result.getInt("pending_retakes"), result.getInt("historical_retakes")),
                            result.getInt("required_credits"), result.getString("evidence"),
                            result.getString("reviewed_by"), instant(result.getTimestamp("reviewed_at")),
                            result.getString("basis"), result.getString("graduated_by"),
                            instant(result.getTimestamp("graduated_at")), result.getString("graduation_note")));
                }
            }
            return rows;
        }
        @Override public void save(AcademicAssessment row) throws SQLException {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tblAcademicAssessment(assessment_id,student_id,earned_credits,passed_courses,"
                    + "pending_retakes,historical_retakes,required_credits,evidence,reviewed_by,reviewed_at,basis)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                insert.setString(1, row.getId()); insert.setString(2, row.getStudentId());
                insert.setInt(3, row.getCredits().getEarnedCredits()); insert.setInt(4, row.getCredits().getPassedCourses());
                insert.setInt(5, row.getCredits().getPendingRetakes()); insert.setInt(6, row.getCredits().getHistoricalRetakes());
                insert.setInt(7, row.getRequiredCredits()); insert.setString(8, row.getEvidence());
                insert.setString(9, row.getReviewedBy()); insert.setTimestamp(10, Timestamp.from(row.getReviewedAt()));
                insert.setString(11, row.getBasis()); insert.executeUpdate();
            }
        }
        @Override public void graduate(StudentRecord previous, AcademicAssessment assessment) throws SQLException {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE tblStudent SET status=? WHERE student_id=? AND status=?")) {
                update.setString(1, "毕业"); update.setString(2, previous.getStudentId());
                update.setString(3, "在读");
                if (update.executeUpdate() != 1) throw new SQLException("student status changed");
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE tblAcademicAssessment SET graduated_by=?,graduated_at=?,graduation_note=? "
                    + "WHERE assessment_id=? AND student_id=? AND graduated_at IS NULL")) {
                update.setString(1, assessment.getGraduatedBy());
                update.setTimestamp(2, Timestamp.from(assessment.getGraduatedAt()));
                update.setString(3, assessment.getGraduationNote());
                update.setString(4, assessment.getId()); update.setString(5, assessment.getStudentId());
                if (update.executeUpdate() != 1) throw new SQLException("assessment already processed");
            }
        }
        private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    }
}
