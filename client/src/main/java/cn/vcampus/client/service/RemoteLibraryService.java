package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryAddBookV2Command;
import cn.vcampus.library.LibraryBorrowV2Command;
import cn.vcampus.library.LibraryDetailV2Command;
import cn.vcampus.library.LibraryHistoryV2Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Client-side facade for the versioned library protocol. */
public final class RemoteLibraryService implements Closeable {
    private final SocketMessageClient client;
    private final AtomicLong requestSequence = new AtomicLong();

    public RemoteLibraryService(String host, int port) throws IOException {
        this.client = new SocketMessageClient(host, port);
    }

    public Message search(String token, String keyword, String category)
            throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_QUERY_V2,
                new LibraryQueryV2Command(token, keyword, category));
    }

    public Message detail(String token, String bookId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_DETAIL_V2,
                new LibraryDetailV2Command(token, bookId));
    }

    public Message borrow(String token, List<String> bookIds) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(token, bookIds));
    }

    public Message returnBook(String token, String recordId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_RETURN_V2,
                new LibraryReturnV2Command(token, recordId));
    }

    public Message ownHistory(String token) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_HISTORY_V2, new LibraryHistoryV2Command(token));
    }

    public Message historyFor(String token, String userId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_HISTORY_V2,
                new LibraryHistoryV2Command(token, userId, false));
    }

    public Message allHistory(String token) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_HISTORY_V2,
                new LibraryHistoryV2Command(token, null, true));
    }

    public Message addBook(String token, Book book) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_ADD_BOOK_V2,
                new LibraryAddBookV2Command(token, book));
    }

    private Message request(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        String requestId = "library-client-" + requestSequence.incrementAndGet();
        return client.send(Message.request(requestId, type, payload));
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
