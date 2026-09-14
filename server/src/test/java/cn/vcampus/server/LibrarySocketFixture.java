package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;

/** 真 ServerApplication + TCP 对象流；线程屏障只启动请求，不绕过 Handler 或会话鉴权。 */
final class LibrarySocketFixture implements AutoCloseable {
    final ServerApplication server;
    final ExecutorService listener = Executors.newSingleThreadExecutor();
    final Future<?> task;
    final int port;

    LibrarySocketFixture(Path database) throws Exception {
        LibraryWalletRuntime runtime = LibraryWalletRuntime.create(database, false);
        CourseServiceFactory.CourseRuntime courses = CourseServiceFactory.create(database);
        server = new ServerApplication(0, UserServiceFactory.create(new String[] {"--db", database.toString()}),
                courses.getModule().getSelectionService(), null, null, null, null, null, null, null,
                courses.getProfiles(), runtime.store,
                new DefaultStudentManagementService(new InMemoryStudentRepository()), runtime.library,
                new DenyTeacherStudentAccessPolicy(), null, null, null, null, null, runtime.compensations);
        task = listener.submit(() -> { server.start(); return null; });
        java.lang.reflect.Field field = ServerApplication.class.getDeclaredField("serverSocket");
        field.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        ServerSocket bound = null;
        while (System.nanoTime() < deadline) {
            if (task.isDone()) task.get();
            bound = (ServerSocket) field.get(server);
            if (bound != null && bound.isBound()) break;
            Thread.sleep(10);
        }
        if (bound == null || !bound.isBound()) { close(); throw new IOException("test server did not bind"); }
        port = bound.getLocalPort();
    }

    String login(String user) throws Exception {
        Message result = send(MessageType.LOGIN, new UserCredentials(user, "Demo123", user, "STUDENT"));
        assertEquals(StatusCode.OK, result.getStatusCode(), String.valueOf(result.getPayload()));
        return ((Session) result.getPayload()).getToken();
    }

    Message send(MessageType type, Object payload) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            socket.setSoTimeout(15000);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                Message request = Message.request(UUID.randomUUID().toString(), type, payload);
                out.writeObject(request); out.flush();
                Message response = (Message) in.readObject();
                assertEquals(request.getRequestId(), response.getRequestId());
                assertEquals(type, response.getType());
                return response;
            }
        }
    }

    List<Message> race(MessageType type, IntFunction<Object> payload) throws Exception {
        return race(index -> type, payload);
    }

    List<Message> race(IntFunction<MessageType> type, IntFunction<Object> payload) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), go = new CountDownLatch(1);
        List<Future<Message>> futures = new ArrayList<Future<Message>>();
        try {
            for (int i = 0; i < 8; i++) {
                final int index = i;
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new TimeoutException("start barrier");
                    return send(type.apply(index), payload.apply(index));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            List<Message> results = new ArrayList<Message>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            for (Future<Message> future : futures) results.add(future.get(
                    Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            return results;
        } finally {
            go.countDown();
            for (Future<?> future : futures) future.cancel(true);
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Override public void close() throws Exception {
        server.close();
        listener.shutdownNow();
        assertTrue(listener.awaitTermination(10, TimeUnit.SECONDS));
        task.get(1, TimeUnit.SECONDS);
    }
}
