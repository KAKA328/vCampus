package cn.vcampus.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryRestockV2Command;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RemoteLibraryServiceTest {
    @Test
    void restockSendsVersionedDeltaCommandAndPreservesServerResponse() throws Exception {
        Book updated = new Book("B001", "测试图书", "作者", "", "文学", "", 35.0d, 8, 6, "A-01");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        for (StatusCode status : new StatusCode[] {StatusCode.OK, StatusCode.FORBIDDEN}) {
                            Message request = (Message) input.readObject();
                            assertEquals(MessageType.LIBRARY_RESTOCK_V2, request.getType());
                            LibraryRestockV2Command command = (LibraryRestockV2Command) request.getPayload();
                            assertEquals("session-token", command.getToken());
                            assertEquals("B001", command.getBookId());
                            assertEquals(5, command.getCopies());
                            output.writeObject(Message.response(request, status,
                                    status == StatusCode.OK ? updated : "permission denied"));
                            output.flush();
                        }
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteLibraryService remote = new RemoteLibraryService("127.0.0.1", listener.getLocalPort())) {
                Message success = remote.restock("session-token", "B001", 5);
                assertEquals(StatusCode.OK, success.getStatusCode());
                assertEquals(updated, success.getPayload());
                Message denied = remote.restock("session-token", "B001", 5);
                assertEquals(StatusCode.FORBIDDEN, denied.getStatusCode());
                assertEquals("permission denied", denied.getPayload());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
