package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryAddBookV2Command;
import cn.vcampus.library.LibraryBorrowV2Command;
import cn.vcampus.library.LibraryDetailV2Command;
import cn.vcampus.library.LibraryHistoryV3Command;
import cn.vcampus.library.LibraryLossDeclareV3Command;
import cn.vcampus.library.LibraryCompensationListV3Command;
import cn.vcampus.library.LibraryCompensationPayV3Command;
import cn.vcampus.library.LibraryWalletQueryV3Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.library.LibraryRestockV2Command;

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
        return request(MessageType.LIBRARY_HISTORY_V3, new LibraryHistoryV3Command(token));
    }

    public Message historyFor(String token, String userId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_HISTORY_V3,
                new LibraryHistoryV3Command(token, userId, false));
    }

    public Message allHistory(String token) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_HISTORY_V3,
                new LibraryHistoryV3Command(token, null, true));
    }

    /** 馆员确认遗失；原价、借阅人及赔偿单均由服务器确定。 */
    public Message declareLoss(String token, String recordId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_LOSS_DECLARE_V3, new LibraryLossDeclareV3Command(token, recordId));
    }

    /** 读者查询本人赔偿，馆员可查询全部；服务端仍校验查询范围。 */
    public Message compensations(String token, boolean allUsers) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_COMPENSATION_LIST_V3,
                new LibraryCompensationListV3Command(token, allUsers));
    }

    /** 只传赔偿单编号，不允许客户端传入扣款金额或代付用户。 */
    public Message payCompensation(String token, String compensationId) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_COMPENSATION_PAY_V3,
                new LibraryCompensationPayV3Command(token, compensationId));
    }

    /** 查询会话本人共用的校园钱包余额，金额以分返回。 */
    public Message walletBalance(String token) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_WALLET_QUERY_V3, new LibraryWalletQueryV3Command(token));
    }

    public Message addBook(String token, Book book) throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_ADD_BOOK_V2,
                new LibraryAddBookV2Command(token, book));
    }

    /** 为已有图书追加册数，成功响应携带最新库存快照。 */
    public Message restock(String token, String bookId, int copies)
            throws IOException, ClassNotFoundException {
        return request(MessageType.LIBRARY_RESTOCK_V2,
                new LibraryRestockV2Command(token, bookId, copies));
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
