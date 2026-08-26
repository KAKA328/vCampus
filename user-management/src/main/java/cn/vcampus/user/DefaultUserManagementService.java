package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.time.Instant;

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
        if (role != Role.STUDENT) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "role is not available for self-registration");
        }
        return createAccount(c, role);
    }

    /** Creates a trusted bootstrap/admin-provisioned account; never expose this method as a public Socket action. */
    public ServiceResult<Void> provisionAccount(UserCredentials c) {
        Role role = role(c);
        if (role == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid account data");
        return createAccount(c, role);
    }

    private ServiceResult<Void> createAccount(UserCredentials c, Role role) {
        final User user;
        try {
            user = new User(c.getUserId(), c.getDisplayName(), role);
        } catch (IllegalArgumentException invalidUser) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        }
        UserAccount account = new UserAccount(user, passwordHasher.hash(c.getPassword()), true);
        if (!users.create(account)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "user already exists");
        }
        return ServiceResult.ok(null);
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
        return ServiceResult.ok(sessions.create(account.getUser()));
    }

    @Override public ServiceResult<Void> logout(String token) {
        if (!sessions.invalidate(token)) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Boolean> authorize(String token, String permission) {
        Session session = sessions.find(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        Permission requestedPermission = Permission.fromCode(permission);
        if (requestedPermission == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid permission");
        boolean allowed = permissionPolicy.isAllowed(session.getUser().getRole(), requestedPermission);
        return allowed ? ServiceResult.ok(Boolean.TRUE) : ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
    }
}
