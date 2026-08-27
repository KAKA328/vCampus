package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserManagementService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserServiceFactoryTest {
    private static final String ADMIN_ID = "vcampus.bootstrap.admin.id";
    private static final String ADMIN_PASSWORD = "vcampus.bootstrap.admin.password";
    private static final String ADMIN_NAME = "vcampus.bootstrap.admin.name";

    @Test
    void configuredBootstrapAdminCanLoginWithoutPublicSelfRegistration() {
        System.setProperty(ADMIN_ID, "admin001");
        System.setProperty(ADMIN_PASSWORD, "admin123");
        System.setProperty(ADMIN_NAME, "System Administrator");
        try {
            UserManagementService service = UserServiceFactory.create(new String[0]);
            ServiceResult<Session> login = service.login(
                    new UserCredentials("admin001", "admin123", "ignored", Role.STUDENT.name()));

            assertEquals(StatusCode.OK, login.getStatus());
            assertEquals(Role.ADMIN, login.getData().getUser().getRole());
        } finally {
            System.clearProperty(ADMIN_ID);
            System.clearProperty(ADMIN_PASSWORD);
            System.clearProperty(ADMIN_NAME);
        }
    }

    @Test
    void memoryModeProvidesDocumentedDemoAdminAccount() {
        UserManagementService service = UserServiceFactory.create(new String[0]);

        ServiceResult<Session> login = service.login(
                new UserCredentials("demo_admin", "Demo123", "登录用户", Role.STUDENT.name()));

        assertEquals(StatusCode.OK, login.getStatus());
        assertEquals(Role.ADMIN, login.getData().getUser().getRole());
    }
}
