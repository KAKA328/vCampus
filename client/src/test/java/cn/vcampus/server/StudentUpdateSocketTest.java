package cn.vcampus.server;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentUpdateSocketTest {
    @Test void remoteSavePreservesOriginalSnapshotAndDispatchRejectsStaleUpdate() throws Exception {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials credentials = new UserCredentials("demo_student", "Demo123", "Student", Role.STUDENT.name());
        assertEquals(StatusCode.OK, users.register(credentials).getStatus());
        String token = users.login(credentials).getData().getToken();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerApplication application = new ServerApplication(0, users);
                ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        for (int i = 0; i < 5; i++) {
                            Message request = (Message) input.readObject();
                            if (i == 1 || i == 2 || i == 4) {
                                assertEquals(MessageType.STUDENT_UPDATE_V2, request.getType());
                                ((StudentUpdateV2Command) request.getPayload()).validate();
                            }
                            output.writeObject(application.dispatch(request));
                            output.flush();
                            output.reset();
                        }
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteStudentService remote = new RemoteStudentService("127.0.0.1", listener.getLocalPort())) {
                Message loaded = remote.currentStudent(token);
                assertEquals(StatusCode.OK, loaded.getStatusCode());
                StudentRecord original = (StudentRecord) loaded.getPayload();
                StudentRecord changed = StudentProfileSnapshot.withContacts(original, "13800000001", null);
                assertEquals(StatusCode.OK, remote.save(token, changed, original).getStatusCode());
                Message stale = remote.save(token,
                        StudentProfileSnapshot.withContacts(original, "13800000002", null), original);
                assertEquals(StatusCode.CONFLICT, stale.getStatusCode());
                assertTrue(stale.getPayload().toString().contains("重新加载"));
                StudentRecord refreshed = (StudentRecord) remote.currentStudent(token).getPayload();
                assertEquals("13800000001", refreshed.getPhone());
                assertEquals(StatusCode.OK, remote.save(token,
                        StudentProfileSnapshot.withContacts(refreshed, "13800000002", null), refreshed).getStatusCode());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
