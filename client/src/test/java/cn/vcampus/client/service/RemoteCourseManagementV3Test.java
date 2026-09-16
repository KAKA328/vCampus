package cn.vcampus.client.service;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteCourseManagementV3Test {
    @Test void courseManagementUsesExplicitV3AndPreservesFractionalCredits() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept();
                        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                    Message request = (Message) input.readObject();
                    assertEquals(MessageType.COURSE_MANAGE_V3, request.getType());
                    CourseManagementCommand command = (CourseManagementCommand) request.getPayload();
                    assertEquals(new BigDecimal("2.5"), command.getCourse().getCreditsDecimal());
                    output.writeObject(Message.response(request, StatusCode.OK, command.getCourse()));
                    output.flush();
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            try (RemoteCourseService remote = new RemoteCourseService(
                    "127.0.0.1", listener.getLocalPort())) {
                Message response = remote.manage(CourseManagementCommand.createCourse("token",
                        new Course("WEB101", "Web", new BigDecimal("2.5"))));
                assertEquals(StatusCode.OK, response.getStatusCode());
                assertEquals(new BigDecimal("2.5"),
                        ((Course) response.getPayload()).getCreditsDecimal());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }
    }
}
