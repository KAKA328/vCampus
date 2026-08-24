package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary service for local protocol demos; replace with Access-backed repositories. */
public final class InMemoryUserManagementService implements UserManagementService {
    private final Map<String, Account> accounts = new ConcurrentHashMap<String, Account>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final RolePermissionPolicy permissionPolicy = new RolePermissionPolicy();

    @Override public ServiceResult<Void> register(UserCredentials c) {
        final Account account;
        try {
            account = new Account(c, passwordHasher);
        } catch (IllegalArgumentException invalidRole) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        }
        if (accounts.putIfAbsent(c.getUserId(), account) != null) {
            return ServiceResult.failure(StatusCode.CONFLICT, "user already exists");
        }
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Void> unregister(String userId, String token) {
        Session session = sessions.get(token);
        if (session == null || !session.getUser().getUserId().equals(userId)) {
            return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        }
        accounts.remove(userId);
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().getUser().getUserId().equals(userId)) {
                sessions.remove(entry.getKey());
            }
        }
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Session> login(UserCredentials c) {
        Account account = accounts.get(c.getUserId());
        if (account == null || !passwordHasher.matches(c.getPassword(), account.passwordHash)) {
            return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid credentials");
        }
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, account.user); sessions.put(token, session);
        return ServiceResult.ok(session);
    }

    @Override public ServiceResult<Void> logout(String token) {
        if (sessions.remove(token) == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Boolean> authorize(String token, String permission) {
        Session session = sessions.get(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        Permission requestedPermission = Permission.fromCode(permission);
        if (requestedPermission == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid permission");
        }
        boolean allowed = permissionPolicy.isAllowed(session.getUser().getRole(), requestedPermission);
        return allowed ? ServiceResult.ok(Boolean.TRUE) : ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
    }

    private static final class Account {
        private final String passwordHash; private final User user;
        private Account(UserCredentials c, PasswordHasher hasher) {
            this.passwordHash = hasher.hash(c.getPassword());
            this.user = new User(c.getUserId(), c.getDisplayName(), Role.valueOf(c.getRoleCode()));
        }
    }
}
