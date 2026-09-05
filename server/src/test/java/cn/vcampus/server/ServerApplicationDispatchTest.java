package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ServerApplicationDispatchTest {
    @Test
    void dispatchRoutesCurrentCourseQueryWithoutClientStudentId() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials account = new UserCredentials("20260001", "password", "测试学生", Role.STUDENT.name());
        users.register(account); Session session = users.login(account).getData();
        InMemoryStudentSelectionProfileProvider profiles = new InMemoryStudentSelectionProfileProvider(
                Collections.singletonList(new StudentSelectionProfile("20260001", "STU-001", "计算机科学与技术", 2026, "在读", CourseSelectionDemoFactory.DEMO_TERM, 1, Collections.<String>emptySet())));
        ServerApplication server = new ServerApplication(0, users, CourseSelectionDemoFactory.createService(), profiles);

        Message response = server.dispatch(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));
        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void dispatchRoutesVersionedLibraryQuery() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials account = new UserCredentials("library_student", "password", "图书馆学生", Role.STUDENT.name());
        users.register(account);
        Session session = users.login(account).getData();
        InMemoryStudentSelectionProfileProvider profiles = new InMemoryStudentSelectionProfileProvider(
                Collections.<StudentSelectionProfile>emptyList());
        ServerApplication server = new ServerApplication(
                0, users, CourseSelectionDemoFactory.createService(), profiles);

        Message response = server.dispatch(Message.request("library-query",
                MessageType.LIBRARY_QUERY_V2,
                new LibraryQueryV2Command(session.getToken(), "Java")));
        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    // B3：isStoreMessage 白名单守护——必须恰好覆盖 MessageType 中所有 STORE_* 前缀枚举，
    // 且不误纳任何非商店类型；防新增 STORE_* 消息漏加白名单后被静默路由到 userMessages
    @Test
    void isStoreMessageWhitelistMatchesStorePrefixConvention() {
        int storeTypeCount = 0;
        for (MessageType type : MessageType.values()) {
            boolean expectedStore = type.name().startsWith("STORE_");
            assertEquals(expectedStore, ServerApplication.isStoreMessage(type),
                    "isStoreMessage 与 STORE_ 前缀约定漂移: " + type);
            if (expectedStore) {
                storeTypeCount++;
            }
        }
        assertEquals(19, storeTypeCount, "STORE_* 消息类型数量变化，请同步核对 isStoreMessage 白名单");
    }
}
