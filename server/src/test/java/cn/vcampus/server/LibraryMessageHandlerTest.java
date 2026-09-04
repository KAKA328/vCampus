package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryAddBookV2Command;
import cn.vcampus.library.LibraryBorrowV2Command;
import cn.vcampus.library.LibraryHistoryV2Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LibraryMessageHandlerTest {
    private InMemoryLibraryService library;
    private InMemoryUserManagementService users;
    private LibraryMessageHandler handler;
    private Session student;
    private Session librarian;

    @BeforeEach
    void setUp() {
        library = new InMemoryLibraryService();
        users = new InMemoryUserManagementService();
        student = account("student001", Role.STUDENT);
        librarian = account("librarian001", Role.LIBRARIAN);
        handler = new LibraryMessageHandler(library, users);
    }

    @Test
    void queryRequiresValidToken() {
        Message ok = handler.handle(Message.request("query", MessageType.LIBRARY_QUERY_V2,
                new LibraryQueryV2Command(student.getToken(), "Java")));
        assertEquals(StatusCode.OK, ok.getStatusCode());

        Message denied = handler.handle(Message.request("bad", MessageType.LIBRARY_QUERY_V2,
                new LibraryQueryV2Command("invalid-token", "Java")));
        assertEquals(StatusCode.UNAUTHORIZED, denied.getStatusCode());
    }

    @Test
    void borrowUsesUserResolvedFromSession() {
        Message response = handler.handle(Message.request("borrow", MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(student.getToken(), "B001")));
        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(1, library.borrowHistory("student001").getData().size());
    }

    @Test
    void studentCannotAddBookButLibrarianCan() {
        Book book = new Book("B006", "测试图书", "测试作者");
        Message denied = handler.handle(Message.request("add-denied", MessageType.LIBRARY_ADD_BOOK_V2,
                new LibraryAddBookV2Command(student.getToken(), book)));
        assertEquals(StatusCode.FORBIDDEN, denied.getStatusCode());

        Message added = handler.handle(Message.request("add-ok", MessageType.LIBRARY_ADD_BOOK_V2,
                new LibraryAddBookV2Command(librarian.getToken(), book)));
        assertEquals(StatusCode.OK, added.getStatusCode());
    }

    @Test
    void studentCannotReadAnotherUsersHistory() {
        Session other = account("student002", Role.STUDENT);
        Message response = handler.handle(Message.request("history", MessageType.LIBRARY_HISTORY_V2,
                new LibraryHistoryV2Command(student.getToken(), other.getUser().getUserId(), false)));
        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void librarianCanReadAllHistory() {
        handler.handle(Message.request("borrow", MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(student.getToken(), "B001")));
        Message response = handler.handle(Message.request("all", MessageType.LIBRARY_HISTORY_V2,
                new LibraryHistoryV2Command(librarian.getToken(), null, true)));
        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof java.util.List<?>);
    }

    @Test
    void librarianCanReturnAStudentsBorrowedBookByRecordId() {
        Message borrowed = handler.handle(Message.request("borrow", MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(student.getToken(), "B001")));
        cn.vcampus.library.BorrowRecord record =
                (cn.vcampus.library.BorrowRecord) ((java.util.List<?>) borrowed.getPayload()).get(0);

        Message returned = handler.handle(Message.request("return", MessageType.LIBRARY_RETURN_V2,
                new LibraryReturnV2Command(librarian.getToken(), record.getRecordId())));

        assertEquals(StatusCode.OK, returned.getStatusCode());
        assertTrue(((cn.vcampus.library.BorrowRecord) returned.getPayload()).isReturned());
    }

    @Test
    void invalidPayloadReturnsBadRequest() {
        Message response = handler.handle(Message.request("invalid", MessageType.LIBRARY_BORROW_V2, null));
        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }

    private Session account(String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}
