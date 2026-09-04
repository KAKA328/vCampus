package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.user.ProfileBindingRepository;
import cn.vcampus.user.ProfileBindingResult;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Access-backed binder between user accounts and existing student/teacher archive rows. */
public final class AccessProfileBindingRepository implements ProfileBindingRepository {
    private final Path databasePath;

    public AccessProfileBindingRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override public ProfileBindingResult validate(Role role, String profileId, String userId) {
        BindingTable table = table(role);
        if (table == null) {
            return ProfileBindingResult.NOT_REQUIRED;
        }
        if (profileId == null || profileId.trim().isEmpty()) {
            return ProfileBindingResult.PROFILE_NOT_FOUND;
        }
        try (Connection connection = open()) {
            String existingUser = existingUserForProfile(connection, table, profileId.trim());
            if (existingUser == null) {
                return ProfileBindingResult.PROFILE_NOT_FOUND;
            }
            if (!existingUser.isEmpty() && !userId.equals(existingUser)) {
                return ProfileBindingResult.PROFILE_ALREADY_BOUND;
            }
            String boundProfile = profileForUser(connection, table, userId);
            if (!boundProfile.isEmpty() && !profileId.trim().equals(boundProfile)) {
                return ProfileBindingResult.USER_ALREADY_BOUND;
            }
            return ProfileBindingResult.OK;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to validate profile binding", failure);
        }
    }

    @Override public ProfileBindingResult bind(Role role, String profileId, String userId) {
        ProfileBindingResult validation = validate(role, profileId, userId);
        if (validation != ProfileBindingResult.OK) {
            return validation;
        }
        BindingTable table = table(role);
        String sql = "UPDATE " + table.tableName + " SET user_id=? WHERE " + table.profileColumn + "=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, profileId.trim());
            return statement.executeUpdate() > 0 ? ProfileBindingResult.OK
                    : ProfileBindingResult.PROFILE_NOT_FOUND;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to bind profile", failure);
        }
    }

    @Override public String findProfileId(Role role, String userId) {
        BindingTable table = table(role);
        if (table == null || userId == null || userId.trim().isEmpty()) {
            return "";
        }
        try (Connection connection = open()) {
            return profileForUser(connection, table, userId.trim());
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find bound profile", failure);
        }
    }

    private String existingUserForProfile(Connection connection, BindingTable table, String profileId)
            throws SQLException {
        String sql = "SELECT user_id FROM " + table.tableName + " WHERE " + table.profileColumn + "=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String userId = rs.getString("user_id");
                return userId == null ? "" : userId;
            }
        }
    }

    private String profileForUser(Connection connection, BindingTable table, String userId)
            throws SQLException {
        String sql = "SELECT " + table.profileColumn + " FROM " + table.tableName + " WHERE user_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(table.profileColumn) : "";
            }
        }
    }

    private static BindingTable table(Role role) {
        if (role == Role.STUDENT) {
            return new BindingTable("tblStudent", "student_id");
        }
        if (role == Role.TEACHER) {
            return new BindingTable("tblTeacher", "teacher_id");
        }
        return null;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:ucanaccess://" + databasePath + ";immediatelyReleaseResources=true");
    }

    private static final class BindingTable {
        private final String tableName;
        private final String profileColumn;

        private BindingTable(String tableName, String profileColumn) {
            this.tableName = tableName;
            this.profileColumn = profileColumn;
        }
    }
}
