package cn.vcampus.server;

import cn.vcampus.user.AuditEvent;
import cn.vcampus.user.AuditLogRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Access-backed audit log repository for sensitive user operations. */
public final class AccessAuditLogRepository implements AuditLogRepository {
    private final Path databasePath;

    public AccessAuditLogRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override public void record(AuditEvent event) {
        String sql = "INSERT INTO tblAuditLog(log_id,actor_user_id,action,target_type,target_id,created_at)"
                + " VALUES(?,?,?,?,?,?)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, event.getActorUserId());
            statement.setString(3, event.getAction());
            statement.setString(4, event.getTargetType());
            statement.setString(5, event.getTargetId());
            statement.setTimestamp(6, Timestamp.from(event.getCreatedAt()));
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to record audit event", failure);
        }
    }

    @Override public List<AuditEvent> findAll() {
        String sql = "SELECT actor_user_id,action,target_type,target_id,created_at "
                + "FROM tblAuditLog ORDER BY created_at DESC";
        List<AuditEvent> events = new ArrayList<AuditEvent>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                events.add(new AuditEvent(rs.getString("actor_user_id"), rs.getString("action"),
                        rs.getString("target_type"), rs.getString("target_id"),
                        rs.getTimestamp("created_at").toInstant()));
            }
            return events;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to list audit events", failure);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath);
    }
}
