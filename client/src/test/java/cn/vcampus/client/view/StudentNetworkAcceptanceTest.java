package cn.vcampus.client.view;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real child server process + independent TCP clients + disposable Access database. */
class StudentNetworkAcceptanceTest {
    @TempDir Path temporary;
    private int port;
    private Process server;
    private Path db;
    private Path serverLog;
    private String admin;
    private String academic;

    @RepeatedTest(3) void functionalAndConcurrentRequestsPreservePersistedState() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        while (!Files.exists(root.resolve("database/schema.sql"))) root = root.getParent();
        db = temporary.resolve("network.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        for (String file : Arrays.asList("schema.sql", "seed.sql", "student-acceptance-data.sql")) {
            String sql = Files.readAllLines(root.resolve("database").resolve(file), StandardCharsets.UTF_8)
                    .stream().filter(line -> !line.trim().startsWith("--")).collect(Collectors.joining("\n"));
            try (Connection c = DriverManager.getConnection("jdbc:ucanaccess://" + db
                    + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true"); Statement s = c.createStatement()) {
                for (String part : sql.split(";")) if (!part.trim().isEmpty()) s.execute(part.trim());
            }
        }
        try {
            start();
            admin = login("demo_admin", "Demo123", Role.ADMIN);
            academic = login("demo_academic_admin", "Demo123", Role.ACADEMIC_ADMIN);
            List<UserImportRow> accounts = new UserImportFileReader().read(root.resolve("test-data/学籍完整验收账号.csv"));
            UserImportResult imported = payload(call(MessageType.USER_IMPORT, new UserImportCommand(admin, accounts)), UserImportResult.class);
            assertEquals(12, imported.getSuccessCount());
            for (UserImportRow row : accounts) {
                String token = login(row.getUserId(), row.getPassword(), Role.valueOf(row.getRoleCode()));
                if (row.getRoleCode().equals("STUDENT")) {
                    assertEquals(row.getProfileId(), profile(token, null).getStudentId());
                    assertEquals(StatusCode.FORBIDDEN, call(MessageType.STUDENT_QUERY,
                            StudentQueryCommand.byId(token, row.getProfileId().equals("QA_EMPTY") ? "QA_EXACT" : "QA_EMPTY")).getStatusCode());
                } else {
                    TeacherProfile teacher = payload(call(MessageType.TEACHER_SELF_QUERY_V1,
                            new TeacherSelfQueryV1Command(token)), TeacherProfile.class);
                    assertEquals(row.getProfileId(), teacher.getTeacherId());
                    assertEquals(row.getUserId().equals("qa_teacher"), teacher.isActive());
                    assertEquals(StatusCode.FORBIDDEN, call(MessageType.STUDENT_QUERY,
                            StudentQueryCommand.byId(token, "QA_EMPTY")).getStatusCode());
                }
            }
            for (String id : Arrays.asList("QA_EMPTY", "QA_EXACT", "QA_SHORT", "QA_PENDING", "QA_RETAKE", "QA_DUP")) {
                int expected = id.equals("QA_EMPTY") ? 0 : (id.equals("QA_EXACT") || id.equals("QA_PENDING") ? 11 : 3);
                CreditSummary credits = payload(administer(academic, id,
                        AcademicAdminCommandV1.Action.CREDITS, null), CreditSummary.class);
                assertEquals(expected, credits.getEarnedCredits(), id);
                assertEquals(id.equals("QA_PENDING") ? 1 : 0, credits.getPendingRetakes(), id);
            }
            for (String id : Arrays.asList("QA_EMPTY", "QA_SHORT", "QA_PENDING")) {
                AcademicAssessment assessment = review(id);
                assertFalse(assessment.isCreditRequirementMet());
                assertEquals(StatusCode.CONFLICT, graduate(academic, id, assessment.getId()).getStatusCode());
            }
            for (String id : Arrays.asList("QA_LEAVE", "QA_WITHDRAWN")) {
                assertEquals(StatusCode.CONFLICT, administer(academic, id,
                        AcademicAdminCommandV1.Action.REVIEW, null).getStatusCode());
            }
            AcademicAssessment shortfall = review("QA_SHORT");
            assertEquals(11, shortfall.getRequiredCredits());
            assertEquals(8, shortfall.getShortfall());
            StudentRecord valid = profile(academic, "QA_EDIT");
            for (String phone : Arrays.asList("123", "139000001088", "1390000010a", "１３９０００００１０８")) {
                assertEquals(StatusCode.BAD_REQUEST, call(MessageType.STUDENT_UPDATE_V2,
                        new StudentUpdateV2Command(academic, StudentProfileSnapshot.withContacts(valid, phone, valid.getEmail()), valid)).getStatusCode());
                assertTrue(StudentProfileSnapshot.matches(valid, profile(academic, "QA_EDIT")));
            }
            assertEquals(StatusCode.BAD_REQUEST, call(MessageType.STUDENT_UPDATE_V2,
                    new StudentUpdateV2Command(academic, StudentProfileSnapshot.withContacts(valid, valid.getPhone(), "invalid-email"), valid)).getStatusCode());
            assertEquals(StatusCode.UNAUTHORIZED, call(MessageType.STUDENT_QUERY, StudentQueryCommand.self("invalid-token")).getStatusCode());
            List<UserImportRow> invalid = new UserImportFileReader().read(root.resolve("test-data/学籍验收错误账号.csv"));
            assertEquals(0, payload(call(MessageType.USER_IMPORT, new UserImportCommand(admin, invalid)), UserImportResult.class).getSuccessCount());
            assertEquals(0, payload(call(MessageType.USER_IMPORT, new UserImportCommand(admin, accounts)), UserImportResult.class).getSuccessCount());

            // Four connections race with the same original snapshot, repeated five times.
            for (int round = 0; round < 5; round++) {
                StudentRecord before = profile(academic, "QA_EDIT");
                List<Callable<Message>> tasks = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    final String token = academic;
                    final String phone = "13900002" + String.format("%03d", round * 4 + i);
                    tasks.add(() -> call(MessageType.STUDENT_UPDATE_V2, new StudentUpdateV2Command(
                            token, StudentProfileSnapshot.withContacts(before, phone, before.getEmail()), before)));
                }
                List<Message> results = race(tasks);
                assertEquals(1, results.stream().filter(m -> m.getStatusCode() == StatusCode.OK).count());
                assertEquals(3, results.stream().filter(m -> m.getStatusCode() == StatusCode.CONFLICT).count());
                StudentRecord winner = (StudentRecord) results.stream().filter(m -> m.getStatusCode() == StatusCode.OK).findFirst().get().getPayload();
                assertTrue(StudentProfileSnapshot.matches(winner, profile(academic, "QA_EDIT")));
            }
            String studentEditor = login("qa_edit", "Test123", Role.STUDENT);
            StudentRecord oldContacts = profile(studentEditor, null);
            List<Message> contactResults = race(Arrays.asList(
                    () -> call(MessageType.STUDENT_UPDATE_V2, new StudentUpdateV2Command(studentEditor,
                            StudentProfileSnapshot.withContacts(oldContacts, "13900000301", null), oldContacts)),
                    () -> call(MessageType.STUDENT_UPDATE_V2, new StudentUpdateV2Command(studentEditor,
                            StudentProfileSnapshot.withContacts(oldContacts, "13900000302", null), oldContacts))));
            assertEquals(1, contactResults.stream().filter(m -> m.getStatusCode() == StatusCode.OK).count());
            assertEquals(1, contactResults.stream().filter(m -> m.getStatusCode() == StatusCode.CONFLICT).count());
            assertEquals(StatusCode.OK, call(MessageType.LOGOUT, studentEditor).getStatusCode());
            assertEquals(StatusCode.UNAUTHORIZED, call(MessageType.STUDENT_QUERY, StudentQueryCommand.self(studentEditor)).getStatusCode());
            AcademicAssessment stale = review("QA_EXACT");
            StudentRecord before = profile(academic, "QA_EXACT");
            payload(call(MessageType.STUDENT_UPDATE_V2, new StudentUpdateV2Command(academic,
                    StudentProfileSnapshot.withContacts(before, "13900000999", before.getEmail()), before)), StudentRecord.class);
            assertEquals(StatusCode.CONFLICT, graduate(academic, "QA_EXACT", stale.getId()).getStatusCode());
            AcademicAssessment grad = review("QA_GRAD");
            StudentRecord beforeGraduation = profile(academic, "QA_GRAD");
            List<Message> graduation = race(Arrays.asList(
                    () -> graduate(academic, "QA_GRAD", grad.getId()),
                    () -> graduate(academic, "QA_GRAD", grad.getId())));
            assertEquals(1, graduation.stream().filter(m -> m.getStatusCode() == StatusCode.OK).count());
            assertEquals(1, graduation.stream().filter(m -> m.getStatusCode() == StatusCode.CONFLICT).count());
            assertEquals("毕业", profile(academic, "QA_GRAD").getStatus());
            assertEquals(StatusCode.CONFLICT, call(MessageType.STUDENT_UPDATE_V2, new StudentUpdateV2Command(academic,
                    StudentProfileSnapshot.withContacts(beforeGraduation, "13900000777", null), beforeGraduation)).getStatusCode());

            List<Message> binding = race(Arrays.asList(
                    () -> call(MessageType.USER_IMPORT, new UserImportCommand(admin, Collections.singletonList(
                            new UserImportRow("qa_bind_a", "Test123", "竞争账号甲", "STUDENT", "QA_BIND")))),
                    () -> call(MessageType.USER_IMPORT, new UserImportCommand(admin, Collections.singletonList(
                            new UserImportRow("qa_bind_b", "Test123", "竞争账号乙", "STUDENT", "QA_BIND"))))));
            assertEquals(1, binding.stream().map(m -> payload(m, UserImportResult.class)).mapToInt(UserImportResult::getSuccessCount).sum());
            assertEquals(1, binding.stream().map(m -> payload(m, UserImportResult.class)).mapToInt(UserImportResult::getFailureCount).sum());
            String bound = profile(academic, "QA_BIND").getUserId();
            assertNotNull(bound);
            Message loserLogin = call(MessageType.LOGIN, new UserCredentials(
                    bound.equals("qa_bind_a") ? "qa_bind_b" : "qa_bind_a", "Test123", "ignored", "STUDENT"));
            if (loserLogin.getPayload() instanceof Session) {
                System.out.println("Unexpected login user=" + ((Session) loserLogin.getPayload()).getUser().getUserId());
            }
            List<?> userList = payload(call(MessageType.USER_LIST, admin), List.class);
            assertEquals(1, userList.stream().map(item -> (UserAccountSummary) item)
                    .filter(item -> item.getUserId().startsWith("qa_bind")).count());
            assertEquals(StatusCode.UNAUTHORIZED, loserLogin.getStatusCode());
            StudentRecord persisted = profile(academic, "QA_EDIT");
            AcademicAssessment beforeRestart = (AcademicAssessment) payload(administer(academic, "QA_GRAD",
                    AcademicAdminCommandV1.Action.ASSESSMENTS, null), List.class).get(0);
            assertTrue(beforeRestart.isGraduated(), "graduation must be visible before restart");
            String staleAcademic = academic;
            stop(); start();
            admin = login("demo_admin", "Demo123", Role.ADMIN);
            academic = login("demo_academic_admin", "Demo123", Role.ACADEMIC_ADMIN);
            assertTrue(StudentProfileSnapshot.matches(persisted, profile(academic, "QA_EDIT")));
            assertEquals("毕业", profile(academic, "QA_GRAD").getStatus());
            assertEquals(bound, profile(academic, "QA_BIND").getUserId());
            List<?> assessments = payload(administer(academic, "QA_GRAD",
                    AcademicAdminCommandV1.Action.ASSESSMENTS, null), List.class);
            assertEquals(1, assessments.size());
            AcademicAssessment afterRestart = (AcademicAssessment) assessments.get(0);
            assertTrue(afterRestart.isGraduated(), "after restart: actor=" + afterRestart.getGraduatedBy()
                    + " time=" + afterRestart.getGraduatedAt() + " previousTime=" + beforeRestart.getGraduatedAt());
            assertEquals(StatusCode.UNAUTHORIZED, call(MessageType.STUDENT_QUERY, StudentQueryCommand.self(staleAcademic)).getStatusCode());
            System.out.println("NETWORK ACCEPTANCE PASS: 12 imports; 5 x 4 admin-write races; 2 student writers; 2 graduation contenders; 2 binding contenders; restart persistence.");
        } catch (Throwable failure) {
            try {
                Path evidence = Paths.get("target", "network-acceptance");
                Files.createDirectories(evidence);
                if (serverLog != null && Files.exists(serverLog)) {
                    Files.copy(serverLog, evidence.resolve("server-" + UUID.randomUUID() + ".log"));
                }
            } catch (java.io.IOException loggingFailure) {
                failure.addSuppressed(loggingFailure);
            }
            throw failure;
        } finally { stop(); }
    }
    private List<Message> race(List<Callable<Message>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size()), go = new CountDownLatch(1);
        try {
            List<Future<Message>> pending = new ArrayList<>();
            for (Callable<Message> task : tasks) pending.add(pool.submit(() -> {
                ready.countDown(); if (!go.await(15, TimeUnit.SECONDS)) throw new AssertionError("barrier timeout");
                return task.call();
            }));
            assertTrue(ready.await(15, TimeUnit.SECONDS)); go.countDown();
            List<Message> responses = new ArrayList<>();
            for (Future<Message> response : pending) responses.add(response.get(30, TimeUnit.SECONDS));
            return responses;
        } finally { go.countDown(); pool.shutdownNow(); }
    }
    private Message call(MessageType type, Object data) throws Exception {
        try (SocketMessageClient socket = new SocketMessageClient("127.0.0.1", port)) {
            return socket.send(Message.request(UUID.randomUUID().toString(), type, data));
        }
    }
    private String login(String id, String password, Role role) throws Exception {
        return payload(call(MessageType.LOGIN, new UserCredentials(id, password, "ignored", role.name())), Session.class).getToken();
    }
    private StudentRecord profile(String token, String id) throws Exception {
        return payload(call(MessageType.STUDENT_QUERY, id == null ? StudentQueryCommand.self(token)
                : StudentQueryCommand.byId(token, id)), StudentRecord.class);
    }
    private Message administer(String token, String id, AcademicAdminCommandV1.Action action,
            String assessment) throws Exception {
        return call(MessageType.ACADEMIC_ADMIN_V2,
                new AcademicAdminCommandV2(token, action, id, assessment, "", true));
    }
    private AcademicAssessment review(String id) throws Exception {
        return payload(administer(academic, id, AcademicAdminCommandV1.Action.REVIEW, null),
                AcademicAssessment.class);
    }
    private Message graduate(String token, String id, String assessment) throws Exception {
        return administer(token, id, AcademicAdminCommandV1.Action.GRADUATE, assessment);
    }
    private static <T> T payload(Message response, Class<T> type) {
        assertEquals(StatusCode.OK, response.getStatusCode(), String.valueOf(response.getPayload()));
        return type.cast(response.getPayload());
    }
    private void start() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path log = temporary.resolve("server-" + UUID.randomUUID() + ".log");
        serverLog = log;
        server = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "cn.vcampus.client.view.NetworkAcceptanceServerProcess", "--db", db.toString(), "--port", String.valueOf(port))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) fail("Server exited: " + new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
            try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 200); return; }
            catch (java.io.IOException waiting) { Thread.sleep(100); }
        }
        fail("Server startup timed out");
    }
    private void stop() throws Exception {
        if (server != null && server.isAlive()) {
            server.getOutputStream().write('\n');
            server.getOutputStream().flush();
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(5, TimeUnit.SECONDS);
                fail("Server did not exit gracefully");
            }
        }
    }
}
