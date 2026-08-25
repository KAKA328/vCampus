package cn.vcampus.user;

/** Stores audit records for sensitive user-management operations. */
public interface AuditLogRepository {
    void record(AuditEvent event);
}
