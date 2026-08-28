package cn.vcampus.user;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** In-memory service for local protocol demos and early integration tests. */
public final class InMemoryUserManagementService implements UserManagementService {
    private final UserManagementService delegate;

    public InMemoryUserManagementService() {
        this(new InMemoryUserRepository(), new SessionManager(), new InMemoryAuditLogRepository());
    }

    InMemoryUserManagementService(UserRepository users, SessionManager sessions, AuditLogRepository auditLog) {
        this.delegate = new DefaultUserManagementService(users, sessions, auditLog);
    }

    @Override public ServiceResult<Void> register(UserCredentials c) {
        return delegate.register(c);
    }

    @Override public ServiceResult<UserImportResult> importUsers(String token, List<UserImportRow> rows) {
        return delegate.importUsers(token, rows);
    }

    @Override public ServiceResult<Void> unregister(String userId, String token) {
        return delegate.unregister(userId, token);
    }

    @Override public ServiceResult<Session> login(UserCredentials c) {
        return delegate.login(c);
    }

    @Override public ServiceResult<Session> currentSession(String token) {
        return delegate.currentSession(token);
    }

    @Override public ServiceResult<Void> logout(String token) {
        return delegate.logout(token);
    }

    @Override public ServiceResult<Boolean> authorize(String token, String permission) {
        return delegate.authorize(token, permission);
    }
}
