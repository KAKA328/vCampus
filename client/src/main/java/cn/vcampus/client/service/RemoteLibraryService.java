package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.library.LibraryCommand;
import cn.vcampus.library.LibraryQueryCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Client adapter for library messages. */
public final class RemoteLibraryService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteLibraryService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message search(String token, String keyword) throws IOException, ClassNotFoundException {
        return send(MessageType.LIBRARY_QUERY, new LibraryQueryCommand(token, keyword));
    }

    public Message borrow(String token, String patronId, String bookId) throws IOException, ClassNotFoundException {
        return send(MessageType.LIBRARY_BORROW, new LibraryCommand(token, patronId, bookId));
    }

    public Message returnBook(String token, String patronId, String bookId) throws IOException, ClassNotFoundException {
        return send(MessageType.LIBRARY_RETURN, new LibraryCommand(token, patronId, bookId));
    }

    private Message send(MessageType type, Object payload)
            throws IOException, ClassNotFoundException {
        return messages.send(Message.request("library-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}
