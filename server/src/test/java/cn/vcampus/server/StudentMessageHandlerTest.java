package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicReview;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.InMemoryStudentManagementService;
import cn.vcampus.student.StudentReviewCommand;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

class StudentMessageHandlerTest {
    @Test
    void studentCanReviewOnlyOwnAcademicProgress() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session student = login(users, "20230001", Role.STUDENT);
        InMemoryAcademicReviewService reviews = new InMemoryAcademicReviewService();
        reviews.addHistory(new CourseHistoryRecord("20230001", "JAVA101", "Java 程序设计", "2025-2026-1", 1, "首修", 80, true, 3));
        StudentMessageHandler handler = new StudentMessageHandler(new InMemoryStudentManagementService(), reviews, users);

        Message own = handler.handle(Message.request("own-review", MessageType.STUDENT_REVIEW,
                new StudentReviewCommand(student.getToken(), "20230001", 3)));
        Message forged = handler.handle(Message.request("forged-review", MessageType.STUDENT_REVIEW,
                new StudentReviewCommand(student.getToken(), "20230002", 3)));

        assertEquals(StatusCode.OK, own.getStatusCode());
        assertEquals(true, ((AcademicReview) own.getPayload()).isGraduationReady());
        assertEquals(StatusCode.FORBIDDEN, forged.getStatusCode());
    }

    @Test
    void academicAdminCanReviewAnyStudent() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session admin = login(users, "academic001", Role.ACADEMIC_ADMIN);
        InMemoryAcademicReviewService reviews = new InMemoryAcademicReviewService();
        StudentMessageHandler handler = new StudentMessageHandler(new InMemoryStudentManagementService(), reviews, users);

        Message response = handler.handle(Message.request("admin-review", MessageType.STUDENT_REVIEW,
                new StudentReviewCommand(admin.getToken(), "20230002", 120)));

        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}
