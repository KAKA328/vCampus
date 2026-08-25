package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.UserAccount;
import cn.vcampus.user.UserRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Access-backed user repository using parameterized JDBC statements. */
public final class AccessUserRepository implements UserRepository {
    private final Path databasePath;

    public AccessUserRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override public boolean create(UserAccount account) {
        if (findById(account.getUser().getUserId()) != null) return false;
        String sql = "INSERT INTO tblUser(user_id,password_hash,display_name,role_code,active) VALUES(?,?,?,?,?)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getUser().getUserId());
            statement.setString(2, account.getPasswordHash());
            statement.setString(3, account.getUser().getDisplayName());
            statement.setString(4, account.getUser().getRole().name());
            statement.setBoolean(5, account.isActive());
            statement.executeUpdate();
            return true;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to create user account", failure);
        }
    }

    @Override public UserAccount findById(String userId) {
        String sql = "SELECT user_id,password_hash,display_name,role_code,active FROM tblUser WHERE user_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                User user = new User(rs.getString("user_id"), rs.getString("display_name"),
                        Role.valueOf(rs.getString("role_code")));
                return new UserAccount(user, rs.getString("password_hash"), rs.getBoolean("active"));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read user account", failure);
        }
    }

    @Override public boolean deactivateById(String userId) {
        String sql = "UPDATE tblUser SET active=? WHERE user_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, false);
            statement.setString(2, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to deactivate user account", failure);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath);
    }
}
