package cn.vcampus.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-memory audit log used by tests and demos. */
public final class InMemoryAuditLogRepository implements AuditLogRepository {
    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<AuditEvent>());

    @Override public void record(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> findAll() {
        synchronized (events) {
            return new ArrayList<AuditEvent>(events);
        }
    }
}
