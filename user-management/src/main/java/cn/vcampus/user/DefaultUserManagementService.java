package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repository-backed user service shared by memory and Access deployments. */
public final class DefaultUserManagementService implements UserManagementService {
    private final UserRepository users;
    private final SessionManager sessions;
    private final AuditLogRepository auditLog;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final RolePermissionPolicy permissionPolicy = new RolePermissionPolicy();

    public DefaultUserManagementService(UserRepository users, SessionManager sessions, AuditLogRepository auditLog) {
        this.users = users;
        this.sessions = sessions;
        this.auditLog = auditLog;
    }

    @Override public ServiceResult<Void> register(UserCredentials c) {
        Role role = role(c);
        if (role == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        return createAccount(c, role);
    }

    /** Creates a trusted bootstrap/admin-provisioned account; never expose this method as a public Socket action. */
    public ServiceResult<Void> provisionAccount(UserCredentials c) {
        Role role = role(c);
        if (role == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid account data");
        return createAccount(c, role);
    }

    @Override public ServiceResult<UserImportResult> importUsers(String token, List<UserImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "import rows are required");
        }
        ServiceResult<Session> current = currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        User actor = current.getData().getUser();
        if (!permissionPolicy.isAllowed(actor.getRole(), Permission.USER_MANAGE)) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
        }

        String batchId = UUID.randomUUID().toString();
        Instant importedAt = Instant.now();
        List<UserImportFailure> failures = new ArrayList<UserImportFailure>();
        int successCount = 0;
        for (int index = 0; index < rows.size(); index++) {
            UserImportRow row = rows.get(index);
            ImportFailure failure = importOne(actor.getUserId(), row, batchId, importedAt);
            if (failure == null) {
                successCount++;
            } else {
                failures.add(new UserImportFailure(index + 1, failure.userId, failure.message));
            }
        }
        return ServiceResult.ok(new UserImportResult(batchId, rows.size(), successCount, failures));
    }

    private ServiceResult<Void> createAccount(UserCredentials c, Role role) {
        return createAccount(c, role, null, null, null);
    }

    private ServiceResult<Void> createAccount(UserCredentials c, Role role,
                                              String createdBy, Instant createdAt, String importBatchId) {
        final User user;
        try {
            user = new User(c.getUserId(), c.getDisplayName(), role);
        } catch (IllegalArgumentException invalidUser) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        }
        UserAccount account = new UserAccount(user, passwordHasher.hash(c.getPassword()),
                true, createdBy, createdAt, importBatchId);
        if (!users.create(account)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "user already exists");
        }
        return ServiceResult.ok(null);
    }

    private ImportFailure importOne(String actorUserId, UserImportRow row, String batchId, Instant importedAt) {
        if (row == null) {
            return new ImportFailure("", "row is invalid");
        }
        try {
            UserCredentials credentials = new UserCredentials(
                    row.getUserId(), row.getPassword(), row.getDisplayName(), row.getRoleCode());
            Role role = role(credentials);
            if (role == null) {
                return new ImportFailure(row.getUserId(), "invalid role");
            }
            ServiceResult<Void> created = createAccount(credentials, role, actorUserId, importedAt, batchId);
            if (created.getStatus() != StatusCode.OK) {
                return new ImportFailure(row.getUserId(), created.getMessage());
            }
            auditLog.record(new AuditEvent(actorUserId, "IMPORT_USER", "USER", credentials.getUserId(), importedAt));
            return null;
        } catch (IllegalArgumentException invalidRow) {
            return new ImportFailure(row.getUserId(), "invalid import row");
        }
    }

    private static Role role(UserCredentials credentials) {
        try {
            return Role.valueOf(credentials.getRoleCode());
        } catch (IllegalArgumentException invalidRole) {
            return null;
        }
    }

    @Override public ServiceResult<Void> unregister(String userId, String token) {
        Session session = sessions.find(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        boolean self = session.getUser().getUserId().equals(userId);
        boolean admin = session.getUser().getRole() == Role.ADMIN;
        if (!self && !admin) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        if (!users.deactivateById(userId)) return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        sessions.invalidateUser(userId);
        auditLog.record(new AuditEvent(session.getUser().getUserId(), "UNREGISTER_USER", "USER", userId, Instant.now()));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Session> login(UserCredentials c) {
        UserAccount account = users.findById(c.getUserId());
        if (account == null || !account.isActive()
                || !passwordHasher.matches(c.getPassword(), account.getPasswordHash())) {
            return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid credentials");
        }
        Session session = sessions.create(account.getUser());
        if (session == null) {
            return ServiceResult.failure(StatusCode.CONFLICT, "user is already logged in");
        }
        return ServiceResult.ok(session);
    }

    @Override public ServiceResult<Session> currentSession(String token) {
        Session session = sessions.find(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        return ServiceResult.ok(session);
    }

    @Override public ServiceResult<Void> logout(String token) {
        if (!sessions.invalidate(token)) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Boolean> authorize(String token, String permission) {
        ServiceResult<Session> current = currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        Permission requestedPermission = Permission.fromCode(permission);
        if (requestedPermission == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid permission");
        boolean allowed = permissionPolicy.isAllowed(current.getData().getUser().getRole(), requestedPermission);
        return allowed ? ServiceResult.ok(Boolean.TRUE) : ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
    }

    private static final class ImportFailure {
        private final String userId;
        private final String message;

        private ImportFailure(String userId, String message) {
            this.userId = userId;
            this.message = message;
        }
    }
}
