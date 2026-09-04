package cn.vcampus.server;

import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** Access-backed teacher archive repository using parameterized statements. */
public final class AccessTeacherRepository implements TeacherRepository {
    private static final String COLUMNS =
            "teacher_id,user_id,teacher_name,department_name,title,active";
    private final Path databasePath;

    public AccessTeacherRepository(Path databasePath) {
        if (databasePath == null) throw new IllegalArgumentException("databasePath must not be null");
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public TeacherProfile findById(String teacherId) {
        return find("SELECT " + COLUMNS + " FROM tblTeacher WHERE teacher_id=?",
                requireText(teacherId, "teacherId"), "find teacher by id");
    }

    @Override
    public TeacherProfile findByUserId(String userId) {
        return find("SELECT " + COLUMNS + " FROM tblTeacher WHERE user_id=?",
                requireText(userId, "userId"), "find teacher by user id");
    }

    @Override
    public synchronized TeacherProfile save(TeacherProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                if (profile.getUserId() != null && isBoundToAnotherTeacher(connection,
                        profile.getUserId(), profile.getTeacherId())) {
                    throw new IllegalStateException("userId is already bound to another teacher");
                }
                if (exists(connection, profile.getTeacherId())) update(connection, profile);
                else insert(connection, profile);
                connection.commit();
                return profile;
            } catch (SQLException failure) {
                rollbackQuietly(connection);
                if (profile.getUserId() != null
                        && bindingExistsAfterFailure(connection, profile)) {
                    throw new IllegalStateException(
                            "userId is already bound to another teacher", failure);
                }
                throw databaseFailure("save teacher profile", failure);
            } catch (RuntimeException failure) {
                rollbackQuietly(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw databaseFailure("connect to teacher database", failure);
        }
    }

    private TeacherProfile find(String sql, String value, String operation) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? read(results) : null;
            }
        } catch (SQLException failure) {
            throw databaseFailure(operation, failure);
        }
    }

    private static boolean exists(Connection connection, String teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT teacher_id FROM tblTeacher WHERE teacher_id=?")) {
            statement.setString(1, teacherId);
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    private static boolean isBoundToAnotherTeacher(Connection connection, String userId,
            String teacherId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT teacher_id FROM tblTeacher WHERE user_id=? AND teacher_id<>?")) {
            statement.setString(1, userId);
            statement.setString(2, teacherId);
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    private static boolean bindingExistsAfterFailure(Connection connection,
            TeacherProfile profile) {
        try {
            return isBoundToAnotherTeacher(connection, profile.getUserId(),
                    profile.getTeacherId());
        } catch (SQLException ignored) {
            return false;
        }
    }

    private static void insert(Connection connection, TeacherProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblTeacher(" + COLUMNS + ") VALUES(?,?,?,?,?,?)")) {
            bind(statement, profile, false);
            statement.executeUpdate();
        }
    }

    private static void update(Connection connection, TeacherProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblTeacher SET user_id=?,teacher_name=?,department_name=?,title=?,active=?"
                        + " WHERE teacher_id=?")) {
            bind(statement, profile, true);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, TeacherProfile profile, boolean update)
            throws SQLException {
        int index = 1;
        if (!update) statement.setString(index++, profile.getTeacherId());
        setNullable(statement, index++, profile.getUserId());
        statement.setString(index++, profile.getTeacherName());
        setNullable(statement, index++, profile.getDepartmentName());
        setNullable(statement, index++, profile.getTitle());
        statement.setBoolean(index++, profile.isActive());
        if (update) statement.setString(index, profile.getTeacherId());
    }

    private static TeacherProfile read(ResultSet results) throws SQLException {
        return new TeacherProfile(results.getString("teacher_id"), results.getString("user_id"),
                results.getString("teacher_name"), results.getString("department_name"),
                results.getString("title"), results.getBoolean("active"));
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

    private static void setNullable(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void rollbackQuietly(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static IllegalStateException databaseFailure(String operation, SQLException failure) {
        return new IllegalStateException("failed to " + operation, failure);
    }
}
