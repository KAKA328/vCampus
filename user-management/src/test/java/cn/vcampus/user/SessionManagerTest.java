package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class SessionManagerTest {
    @Test
    void expiredSessionIsRejected() throws Exception {
        SessionManager sessions = new SessionManager(Duration.ofMillis(1));
        Session session = sessions.create(new User("expire001", "过期用户", Role.STUDENT));
        Thread.sleep(5L);

        assertNull(sessions.find(session.getToken()));
    }
}
