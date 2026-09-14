package cn.vcampus.server;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MajorDirectorySocketTest {
    @Test void tokenOnlyQueryIsDispatchedAndSerialized() throws Exception {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials admin = new UserCredentials("major_admin", "Demo123", "Major Admin", Role.ACADEMIC_ADMIN.name());
        users.register(admin);
        String token = users.login(admin).getData().getToken();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerApplication app = new ServerApplication(0, users);
                ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        Message request = (Message) input.readObject();
                        assertEquals(MessageType.STUDENT_MAJOR_DIRECTORY_QUERY_V1, request.getType());
                        assertEquals(token, ((MajorDirectoryQueryV1Command) request.getPayload()).getToken());
                        output.writeObject(app.dispatch(request));
                        output.flush();
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteStudentService remote = new RemoteStudentService("127.0.0.1", listener.getLocalPort())) {
                Message response = remote.activeMajors(token);
                assertEquals(StatusCode.OK, response.getStatusCode());
                List<?> entries = (List<?>) response.getPayload();
                assertEquals(3, entries.size());
                assertEquals("CS", ((MajorDirectoryEntry) entries.get(0)).getMajorId());
                assertEquals("计算机科学与工程学院", ((MajorDirectoryEntry) entries.get(0)).getDepartmentName());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
