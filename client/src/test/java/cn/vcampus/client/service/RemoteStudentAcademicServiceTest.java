package cn.vcampus.client.service;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentAcademicQueryV1Command;
import cn.vcampus.student.StudentAcademicQueryV1Command.QueryType;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteStudentAcademicServiceTest {
    @Test void serializesVersionedTokenQueryOverSocket() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        for (QueryType expected : QueryType.values()) {
                            Message request = (Message) input.readObject();
                            assertEquals(MessageType.STUDENT_ACADEMIC_QUERY_V1, request.getType());
                            StudentAcademicQueryV1Command command = (StudentAcademicQueryV1Command) request.getPayload();
                            assertEquals("session-token", command.getToken());
                            assertEquals(expected, command.getQueryType());
                            output.writeObject(Message.response(request, StatusCode.OK,
                                    expected == QueryType.CREDITS ? new cn.vcampus.student.CreditSummary("S001", 6, 2, 0, 1)
                                            : Collections.emptyList()));
                            output.flush();
                        }
                        Message teacherRequest = (Message) input.readObject();
                        assertEquals(MessageType.TEACHER_SELF_QUERY_V1, teacherRequest.getType());
                        assertEquals("teacher-token",
                                ((cn.vcampus.student.TeacherSelfQueryV1Command) teacherRequest.getPayload()).getToken());
                        output.writeObject(Message.response(teacherRequest, StatusCode.OK,
                                new cn.vcampus.student.TeacherProfile("T001", "teacher",
                                        "教师", "院系", "讲师", false)));
                        output.flush();
                        Message adminRequest = (Message) input.readObject();
                        assertEquals(MessageType.ACADEMIC_ADMIN_V1, adminRequest.getType());
                        cn.vcampus.student.AcademicAdminCommandV1 admin =
                                (cn.vcampus.student.AcademicAdminCommandV1) adminRequest.getPayload();
                        assertEquals("academic-token", admin.getToken());
                        assertEquals("S001", admin.getStudentId());
                        assertEquals(6, admin.getRequiredCredits());
                        output.writeObject(Message.response(adminRequest, StatusCode.OK,
                                new cn.vcampus.student.AcademicAssessment("review",
                                        new cn.vcampus.student.CreditSummary("S001", 6, 2, 0, 1), 6,
                                        "hash", "academic", java.time.Instant.now(), "依据", null, null, null)));
                        output.flush();
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteStudentService remote = new RemoteStudentService("127.0.0.1", listener.getLocalPort())) {
                for (QueryType type : QueryType.values()) {
                    Message result = remote.academicQuery("session-token", type);
                    assertEquals(StatusCode.OK, result.getStatusCode());
                    if (type == QueryType.CREDITS) {
                        assertEquals(6, ((cn.vcampus.student.CreditSummary) result.getPayload()).getEarnedCredits());
                    }
                }
                Message teacher = remote.currentTeacher("teacher-token");
                assertEquals(StatusCode.OK, teacher.getStatusCode());
                assertFalse(((cn.vcampus.student.TeacherProfile) teacher.getPayload()).isActive());
                Message assessment = remote.administer(new cn.vcampus.student.AcademicAdminCommandV1(
                        "academic-token", cn.vcampus.student.AcademicAdminCommandV1.Action.REVIEW,
                        "S001", 6, null, "依据", false));
                assertEquals(StatusCode.OK, assessment.getStatusCode());
                assertTrue(((cn.vcampus.student.AcademicAssessment) assessment.getPayload()).isCreditRequirementMet());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
