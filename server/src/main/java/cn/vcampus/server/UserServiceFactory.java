package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.AuditLogRepository;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserRepository;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.user.UserRepository;
import java.nio.file.Path;

/** Creates the user service for memory demos or Access-backed deployment. */
final class UserServiceFactory {
    private static final String ADMIN_ID_ENV = "VCAMPUS_BOOTSTRAP_ADMIN_ID";
    private static final String ADMIN_PASSWORD_ENV = "VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD";
    private static final String ADMIN_NAME_ENV = "VCAMPUS_BOOTSTRAP_ADMIN_NAME";
    private UserServiceFactory() { }

    static UserManagementService create(String[] args) {
        Path databasePath = databasePath(args);
        UserRepository users;
        AuditLogRepository auditLog;
        if (databasePath == null) {
            users = new InMemoryUserRepository();
            auditLog = new InMemoryAuditLogRepository();
        } else {
            users = new AccessUserRepository(databasePath);
            auditLog = new AccessAuditLogRepository(databasePath);
        }
        DefaultUserManagementService service = new DefaultUserManagementService(
                users, new SessionManager(), auditLog);
        provisionBootstrapAdmin(service);
        return service;
    }

    private static void provisionBootstrapAdmin(DefaultUserManagementService service) {
        String userId = setting(ADMIN_ID_ENV, "vcampus.bootstrap.admin.id");
        String password = setting(ADMIN_PASSWORD_ENV, "vcampus.bootstrap.admin.password");
        String displayName = setting(ADMIN_NAME_ENV, "vcampus.bootstrap.admin.name");
        if (userId == null && password == null && displayName == null) return;
        if (userId == null || password == null || displayName == null) {
            throw new IllegalStateException("bootstrap administrator configuration is incomplete");
        }

        ServiceResult<Void> result = service.provisionAccount(
                new UserCredentials(userId, password, displayName, Role.ADMIN.name()));
        if (result.getStatus() != StatusCode.OK && result.getStatus() != StatusCode.CONFLICT) {
            throw new IllegalStateException("failed to provision bootstrap administrator");
        }
    }

    private static String setting(String environmentName, String propertyName) {
        String value = System.getenv(environmentName);
        if (value == null || value.trim().isEmpty()) value = System.getProperty(propertyName);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static Path databasePath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--db".equals(args[i])) {
                return java.nio.file.Paths.get(args[i + 1]);
            }
        }
        String configured = System.getProperty("vcampus.db");
        return configured == null || configured.trim().isEmpty() ? null : java.nio.file.Paths.get(configured);
    }
}
