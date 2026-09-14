package cn.vcampus.client.service;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteLibraryV4Test {
    @Test void newClientMethodsSendExplicitV4DtosAndPreserveNewResponses() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Book before = new Book("B", "旧名", "作者"), after = new Book("B", "新名", "作者");
        BorrowRecord record = new BorrowRecord("O", "R", "reader", "B", LocalDate.now(), LocalDate.now().plusDays(30), null, BorrowStatus.BORROWED);
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        Message copies = (Message) input.readObject();
                        assertEquals(MessageType.LIBRARY_COPIES_V4, copies.getType());
                        assertEquals("token", ((LibraryCopiesV4Command) copies.getPayload()).getToken());
                        assertEquals("B", ((LibraryCopiesV4Command) copies.getPayload()).getBookId());
                        output.writeObject(Message.response(copies, StatusCode.OK,
                                Collections.singletonList(new LibraryCopy("CP-1", "B", LibraryCopyStatus.BORROWED))));
                        output.flush();
                        Message history = (Message) input.readObject();
                        assertEquals(MessageType.LIBRARY_HISTORY_V4, history.getType());
                        assertFalse(((LibraryHistoryV4Command) history.getPayload()).isAllUsers());
                        output.writeObject(Message.response(history, StatusCode.OK,
                                Collections.singletonList(new LibraryLoanSnapshot(record, "旧名", "CP-1", false))));
                        output.flush();
                        Message update = (Message) input.readObject();
                        assertEquals(MessageType.LIBRARY_BOOK_UPDATE_V4, update.getType());
                        LibraryBookUpdateV4Command command = (LibraryBookUpdateV4Command) update.getPayload();
                        assertEquals(before, command.getExpected()); assertEquals(after, command.getReplacement());
                        output.writeObject(Message.response(update, StatusCode.CONFLICT, "资料已更新，请重新读取"));
                        output.flush();
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try (RemoteLibraryService remote = new RemoteLibraryService("127.0.0.1", listener.getLocalPort())) {
                Message copies = remote.copies("token", "B");
                assertEquals("CP-1", ((LibraryCopy) ((List<?>) copies.getPayload()).get(0)).getCopyId());
                Message history = remote.snapshotHistory("token", false);
                assertEquals("旧名", ((LibraryLoanSnapshot) ((List<?>) history.getPayload()).get(0)).getBookTitle());
                assertEquals(StatusCode.CONFLICT, remote.updateBook("token", before, after).getStatusCode());
            }
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
