package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministically pauses a request after validation reads but before its actual write. */
class StudentGraduationConcurrencyTest {
    @TempDir Path temporaryDirectory;

    @Test void memoryStudentContactSaveCannotUndoConcurrentGraduation() throws Exception { race(false, Role.STUDENT); }
    @Test void accessStudentContactSaveCannotUndoConcurrentGraduation() throws Exception { race(true, Role.STUDENT); }
    @Test void memoryAdministrativeSaveCannotUndoConcurrentGraduation() throws Exception { race(false, Role.ACADEMIC_ADMIN); }
    @Test void accessAdministrativeSaveCannotUndoConcurrentGraduation() throws Exception { race(true, Role.ACADEMIC_ADMIN); }

    private void race(boolean access, Role role) throws Exception {
        String studentId = access ? "20260001" : "demo_student";
        StudentRepository repository;
        AcademicAdminService administration;
        Path database = temporaryDirectory.resolve("race.accdb");
        if (access) {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
            AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/seed.sql"));
            repository = new AccessStudentRepository(database);
        } else {
            repository = new InMemoryStudentRepository();
            repository.save(new StudentRecord("demo_student", "demo_student", "学生", "未知",
                    "院系", "专业", "班级", 2026, "在读", "old", null));
        }
        CountDownLatch snapshotRead = new CountDownLatch(1);
        CountDownLatch resumeSave = new CountDownLatch(1);
        StudentRepository intercepted = (StudentRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {StudentRepository.class}, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(repository, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if ("findById".equals(method.getName())
                            && "stale-profile-save".equals(Thread.currentThread().getName())) {
                        snapshotRead.countDown();
                        if (!resumeSave.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("test resume timeout");
                    }
                    return result;
                });
        DefaultStudentManagementService students = new DefaultStudentManagementService(intercepted);
        InMemoryAcademicReviewService academics = new InMemoryAcademicReviewService();
        academics.addHistory(new CourseHistoryRecord(studentId, "C1", "课程",
                "2026-2027-1", 1, "首修", 80, true, 6));
        administration = new AcademicAdminService(access ? new AccessAcademicAdminStore(database)
                : new InMemoryAcademicAdminStore(students, new DefaultTeacherProfileService(
                        new InMemoryTeacherRepository()), academics));
        ServiceResult<?> reviewed = administration.execute(command(studentId,
                AcademicAdminCommandV1.Action.REVIEW, null), "academic");
        assertEquals(StatusCode.OK, reviewed.getStatus());
        AcademicAssessment assessment = (AcademicAssessment) reviewed.getData();
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        String user = role == Role.STUDENT ? "demo_student" : "academic";
        UserCredentials credentials = new UserCredentials(user, "Demo123", "测试", role.name());
        users.register(credentials);
        String token = users.login(credentials).getData().getToken();
        StudentRecord before = repository.findById(studentId);
        StudentMessageHandler handler = new StudentMessageHandler(students, users);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "stale-profile-save"));
        try {
            Future<Message> save = worker.submit(() -> handler.handle(Message.request("save",
                    MessageType.STUDENT_UPDATE, new StudentUpdateCommand(token,
                            StudentProfileSnapshot.withContacts(before, "new-phone", "new@example.test")))));
            assertTrue(snapshotRead.await(15, TimeUnit.SECONDS), "request must read old profile first");
            ServiceResult<?> graduated = administration.execute(
                    command(studentId, AcademicAdminCommandV1.Action.GRADUATE, assessment.getId()), "academic");
            assertEquals(StatusCode.OK, graduated.getStatus(), graduated.getMessage());
            assertEquals("毕业", repository.findById(studentId).getStatus());
            resumeSave.countDown();
            assertEquals(StatusCode.CONFLICT, save.get(15, TimeUnit.SECONDS).getStatusCode());
            StudentRecord after = repository.findById(studentId);
            assertEquals("毕业", after.getStatus());
            assertEquals(before.getPhone(), after.getPhone());
            List<?> rows = (List<?>) administration.execute(
                    command(studentId, AcademicAdminCommandV1.Action.ASSESSMENTS, null), "academic").getData();
            assertTrue(((AcademicAssessment) rows.get(0)).isGraduated());
            // A newly loaded graduated profile can still update contacts without undoing graduation.
            ServiceResult<StudentRecord> contacts = students.updateContacts(studentId, after, "fresh", null);
            assertEquals(StatusCode.OK, contacts.getStatus());
            assertEquals("毕业", repository.findById(studentId).getStatus());
            assertEquals("fresh", repository.findById(studentId).getPhone());
        } finally {
            resumeSave.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS));
        }
    }
    private static AcademicAdminCommandV1 command(String studentId,
            AcademicAdminCommandV1.Action action, String id) {
        return new AcademicAdminCommandV1("internal", action, studentId, 6, id, "测试依据", true);
    }
}
