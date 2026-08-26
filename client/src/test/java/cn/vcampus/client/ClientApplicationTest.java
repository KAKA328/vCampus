package cn.vcampus.client;

import cn.vcampus.common.Role;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientApplicationTest {
    @Test
    void demoCredentialsUseValidUserIdCharacters() {
        UserCredentials credentials = ClientApplication.demoCredentials(12345L);

        assertEquals(Role.STUDENT.name(), credentials.getRoleCode());
        assertTrue(credentials.getUserId().matches("[A-Za-z0-9_]+"));
    }
}
