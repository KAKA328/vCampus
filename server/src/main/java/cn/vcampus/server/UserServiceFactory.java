package cn.vcampus.server;

import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserManagementService;
import java.nio.file.Path;

/** Creates the user service for memory demos or Access-backed deployment. */
final class UserServiceFactory {
    private UserServiceFactory() {
    }

    static UserManagementService create(String[] args) {
        Path databasePath = databasePath(args);
        if (databasePath == null) {
            return new InMemoryUserManagementService();
        }
        return new DefaultUserManagementService(
                new AccessUserRepository(databasePath),
                new SessionManager(),
                new AccessAuditLogRepository(databasePath));
    }

    private static Path databasePath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--db".equals(args[i])) {
                return java.nio.file.Paths.get(args[i + 1]);
            }
        }
        String configured = System.getProperty("vcampus.db");
        return configured == null || configured.trim().isEmpty() ? null : java.nio.file.Paths.get(configured);
    }
}
