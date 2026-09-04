package cn.vcampus.server;

import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access-backed student repository using parameterized JDBC statements. */
public final class AccessStudentRepository implements StudentRepository {
    private static final String COLUMNS = "student_id,user_id,student_name,gender,"
            + "department_name,major_name,class_id,enrollment_year,status,phone,email";

    private final Path databasePath;

    public AccessStudentRepository(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public StudentRecord findById(String studentId) {
        String sql = "SELECT " + COLUMNS + " FROM tblStudent WHERE student_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(studentId, "studentId"));
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readRecord(results) : null;
            }
        } catch (SQLException failure) {
            throw databaseFailure("find student by id", failure);
        }
    }

    @Override
    public StudentRecord findByUserId(String userId) {
        String sql = "SELECT " + COLUMNS + " FROM tblStudent WHERE user_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(userId, "userId"));
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readRecord(results) : null;
            }
        } catch (SQLException failure) {
            throw databaseFailure("find student by user id", failure);
        }
    }

    @Override
    public List<StudentRecord> findByClass(String classId) {
        String sql = "SELECT " + COLUMNS + " FROM tblStudent WHERE class_id=? ORDER BY student_id";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(classId, "classId"));
            try (ResultSet results = statement.executeQuery()) {
                List<StudentRecord> records = new ArrayList<StudentRecord>();
                while (results.next()) {
                    records.add(readRecord(results));
                }
                return records;
            }
        } catch (SQLException failure) {
            throw databaseFailure("find students by class", failure);
        }
    }

    @Override
    public List<StudentRecord> findByMajor(String majorName) {
        String sql = "SELECT " + COLUMNS + " FROM tblStudent WHERE major_name=? ORDER BY student_id";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(majorName, "majorName"));
            try (ResultSet results = statement.executeQuery()) {
                List<StudentRecord> records = new ArrayList<StudentRecord>();
                while (results.next()) {
                    records.add(readRecord(results));
                }
                return records;
            }
        } catch (SQLException failure) {
            throw databaseFailure("find students by major", failure);
        }
    }

    @Override
    public List<StudentRecord> findByIds(List<String> studentIds) {
        if (studentIds == null) throw new IllegalArgumentException("studentIds must not be null");
        Set<String> normalizedIds = new LinkedHashSet<String>();
        for (String studentId : studentIds) {
            normalizedIds.add(requireText(studentId, "studentId"));
        }
        if (normalizedIds.isEmpty()) return new ArrayList<StudentRecord>();

        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM tblStudent WHERE student_id IN (");
        for (int index = 0; index < normalizedIds.size(); index++) {
            if (index > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            for (String studentId : normalizedIds) statement.setString(parameter++, studentId);
            Map<String, StudentRecord> byId = new LinkedHashMap<String, StudentRecord>();
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    StudentRecord record = readRecord(results);
                    byId.put(record.getStudentId(), record);
                }
            }
            List<StudentRecord> ordered = new ArrayList<StudentRecord>();
            for (String studentId : normalizedIds) {
                StudentRecord record = byId.get(studentId);
                if (record != null) ordered.add(record);
            }
            return ordered;
        } catch (SQLException failure) {
            throw databaseFailure("find students by ids", failure);
        }
    }

    @Override
    public synchronized StudentRecord save(StudentRecord record) {
        validate(record);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                String studentId = record.getStudentId().trim();
                String userId = normalize(record.getUserId());
                if (userId != null && isBoundToAnotherStudent(connection, userId, studentId)) {
                    throw new IllegalStateException("userId is already bound to another student");
                }

                boolean exists = existsById(connection, studentId);
                if (exists) {
                    update(connection, record, userId);
                } else {
                    insert(connection, record, userId);
                }
                connection.commit();
                return record;
            } catch (SQLException failure) {
                rollbackQuietly(connection);
                throw databaseFailure("save student", failure);
            } catch (RuntimeException failure) {
                rollbackQuietly(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw databaseFailure("connect to student database", failure);
        }
    }

    private static void validate(StudentRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        requireText(record.getStudentId(), "studentId");
        requireText(record.getName(), "name");
        requireText(record.getStatus(), "status");
        if (record.getEnrollmentYear() < 0) {
            throw new IllegalArgumentException("enrollmentYear cannot be negative");
        }
    }

    private static boolean existsById(Connection connection, String studentId) throws SQLException {
        return findId(connection, "SELECT student_id FROM tblStudent WHERE student_id=?", studentId) != null;
    }

    private static boolean isBoundToAnotherStudent(Connection connection, String userId, String studentId)
            throws SQLException {
        String sql = "SELECT student_id FROM tblStudent WHERE user_id=? AND student_id<>?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, studentId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static String findId(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getString(1) : null;
            }
        }
    }

    private static void insert(Connection connection, StudentRecord record, String userId) throws SQLException {
        String sql = "INSERT INTO tblStudent(" + COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getStudentId());
            bindProfile(statement, record, userId, 2);
            statement.executeUpdate();
        }
    }

    private static void update(Connection connection, StudentRecord record, String userId) throws SQLException {
        String sql = "UPDATE tblStudent SET user_id=?,student_name=?,gender=?,department_name=?,"
                + "major_name=?,class_id=?,enrollment_year=?,status=?,phone=?,email=? WHERE student_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProfile(statement, record, userId, 1);
            statement.setString(11, record.getStudentId());
            statement.executeUpdate();
        }
    }

    private static void bindProfile(PreparedStatement statement, StudentRecord record, String userId,
            int startIndex) throws SQLException {
        int index = startIndex;
        setNullableString(statement, index++, userId);
        statement.setString(index++, record.getName());
        setNullableString(statement, index++, record.getGender());
        setNullableString(statement, index++, record.getDepartmentName());
        setNullableString(statement, index++, record.getMajorName());
        setNullableString(statement, index++, record.getClassId());
        statement.setInt(index++, record.getEnrollmentYear());
        statement.setString(index++, record.getStatus());
        setNullableString(statement, index++, record.getPhone());
        setNullableString(statement, index++, record.getEmail());
    }

    private static StudentRecord readRecord(ResultSet results) throws SQLException {
        return new StudentRecord(
                results.getString("student_id"),
                results.getString("user_id"),
                results.getString("student_name"),
                results.getString("gender"),
                results.getString("department_name"),
                results.getString("major_name"),
                results.getString("class_id"),
                results.getInt("enrollment_year"),
                results.getString("status"),
                results.getString("phone"),
                results.getString("email"));
    }

    private Connection open() throws SQLException {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException failure) {
            throw new SQLException("UCanAccess driver is unavailable", failure);
        }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        String normalized = normalize(value);
        if (normalized == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, normalized);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original persistence error.
        }
    }

    private static IllegalStateException databaseFailure(String operation, SQLException failure) {
        return new IllegalStateException("failed to " + operation, failure);
    }
}
