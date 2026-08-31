package cn.vcampus.user;

import java.util.List;

/** Stores audit records for sensitive user-management operations. */
public interface AuditLogRepository {
    void record(AuditEvent event);
    List<AuditEvent> findAll();
}
