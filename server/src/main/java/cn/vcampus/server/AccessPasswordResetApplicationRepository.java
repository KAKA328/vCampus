package cn.vcampus.server;

import cn.vcampus.user.PasswordResetApplication;
import cn.vcampus.user.PasswordResetApplicationRepository;
import cn.vcampus.user.PasswordResetStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Access-backed password reset application repository. */
public final class AccessPasswordResetApplicationRepository implements PasswordResetApplicationRepository {
    private final Path databasePath;

    public AccessPasswordResetApplicationRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override public void save(PasswordResetApplication application) {
        String deleteSql = "DELETE FROM tblPasswordResetApplication WHERE user_id=?";
        String insertSql = "INSERT INTO tblPasswordResetApplication(user_id,requested_password_hash,"
                + "reset_reason,contact_info,submitted_at,status,reviewed_by,reviewed_at) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection connection = open()) {
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, application.getUserId());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, application.getUserId());
                insert.setString(2, "not-used");
                insert.setString(3, application.getReason());
                insert.setString(4, application.getContactInfo());
                insert.setTimestamp(5, Timestamp.from(application.getSubmittedAt()));
                insert.setString(6, application.getStatus().name());
                insert.setString(7, application.getReviewedBy());
                insert.setTimestamp(8, application.getReviewedAt() == null
                        ? null : Timestamp.from(application.getReviewedAt()));
                insert.executeUpdate();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to save password reset application", failure);
        }
    }

    @Override public PasswordResetApplication findPendingByUserId(String userId) {
        String sql = "SELECT user_id,reset_reason,contact_info,submitted_at,status,reviewed_by,reviewed_at "
                + "FROM tblPasswordResetApplication WHERE user_id=? AND status=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, PasswordResetStatus.PENDING.name());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read password reset application", failure);
        }
    }

    @Override public List<PasswordResetApplication> findPending() {
        String sql = "SELECT user_id,reset_reason,contact_info,submitted_at,status,reviewed_by,reviewed_at "
                + "FROM tblPasswordResetApplication WHERE status=? ORDER BY submitted_at";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, PasswordResetStatus.PENDING.name());
            try (ResultSet rs = statement.executeQuery()) {
                List<PasswordResetApplication> applications = new ArrayList<PasswordResetApplication>();
                while (rs.next()) {
                    applications.add(map(rs));
                }
                return applications;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to list password reset applications", failure);
        }
    }

    @Override public boolean review(String userId, PasswordResetStatus status, String reviewedBy, Instant reviewedAt) {
        String sql = "UPDATE tblPasswordResetApplication SET status=?, reviewed_by=?, reviewed_at=? "
                + "WHERE user_id=? AND status=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, reviewedBy);
            statement.setTimestamp(3, Timestamp.from(reviewedAt));
            statement.setString(4, userId);
            statement.setString(5, PasswordResetStatus.PENDING.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to review password reset application", failure);
        }
    }

    private PasswordResetApplication map(ResultSet rs) throws SQLException {
        Timestamp submittedAt = rs.getTimestamp("submitted_at");
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new PasswordResetApplication(rs.getString("user_id"),
                rs.getString("reset_reason"),
                rs.getString("contact_info"),
                submittedAt == null ? null : submittedAt.toInstant(),
                PasswordResetStatus.valueOf(rs.getString("status")),
                rs.getString("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant());
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath);
    }
}
