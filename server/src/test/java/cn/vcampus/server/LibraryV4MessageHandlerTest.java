package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.user.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryV4MessageHandlerTest {
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final InMemoryLibraryService library = new InMemoryLibraryService();
    private final LibraryMessageHandler handler = new LibraryMessageHandler(library, users);
    private String account(String id, Role role) {
        UserCredentials command = new UserCredentials(id, "password", id, role.name());
        assertEquals(StatusCode.OK, users.register(command).getStatus());
        return users.login(command).getData().getToken();
    }
    private Message send(MessageType type, Object payload) { return handler.handle(Message.request("test", type, payload)); }
    @Test void studentsAndTeachersCannotManageCopiesOrEditBookDetails() {
        Book before = library.getBook("B001").getData();
        for (Role role : new Role[] {Role.STUDENT, Role.TEACHER}) {
            String token = account(role.name(), role);
            assertEquals(StatusCode.FORBIDDEN, send(MessageType.LIBRARY_COPIES_V4, new LibraryCopiesV4Command(token, "B001")).getStatusCode());
            assertEquals(StatusCode.FORBIDDEN, send(MessageType.LIBRARY_BOOK_UPDATE_V4,
                    new LibraryBookUpdateV4Command(token, before, before)).getStatusCode());
        }
        assertEquals(before, library.getBook("B001").getData());
    }
    @Test void librarianCanEditZeroPriceAndSeeCopiesButCannotForgeInventory() {
        String token = account("librarian", Role.LIBRARIAN);
        Book before = library.getBook("B001").getData();
        Book free = new Book(before.getBookId(), "免费新版", before.getAuthor(), before.getIsbn(), "科幻", before.getPublisher(),
                0, before.getTotalCopies(), before.getAvailableCopies(), before.getLocation());
        assertEquals(StatusCode.OK, send(MessageType.LIBRARY_BOOK_UPDATE_V4, new LibraryBookUpdateV4Command(token, before, free)).getStatusCode());
        assertEquals(StatusCode.CONFLICT, send(MessageType.LIBRARY_BOOK_UPDATE_V4, new LibraryBookUpdateV4Command(token, before, free)).getStatusCode());
        assertEquals(StatusCode.BAD_REQUEST, send(MessageType.LIBRARY_BOOK_UPDATE_V4, new LibraryBookUpdateV4Command(token, free, free.withAdditionalCopies(1))).getStatusCode());
        assertEquals(StatusCode.OK, send(MessageType.LIBRARY_COPIES_V4, new LibraryCopiesV4Command(token, "B001")).getStatusCode());
    }
    @Test void snapshotHistoryEnforcesOwnOtherAndAllScopesAndLegacyPayloadStaysUnchanged() {
        String reader = account("reader", Role.STUDENT), other = account("other", Role.TEACHER), admin = account("admin", Role.ADMIN);
        library.borrow("reader", "B001");
        library.borrow("other", "B002");
        Message own = send(MessageType.LIBRARY_HISTORY_V4, new LibraryHistoryV4Command(reader, null, false));
        assertEquals(StatusCode.OK, own.getStatusCode());
        assertEquals(1, ((List<?>) own.getPayload()).size());
        assertTrue(((List<?>) own.getPayload()).get(0) instanceof LibraryLoanSnapshot);
        assertEquals("reader", ((LibraryLoanSnapshot) ((List<?>) own.getPayload()).get(0)).getRecord().getUserId());
        assertEquals(StatusCode.FORBIDDEN, send(MessageType.LIBRARY_HISTORY_V4, new LibraryHistoryV4Command(reader, "other", false)).getStatusCode());
        assertEquals(StatusCode.FORBIDDEN, send(MessageType.LIBRARY_HISTORY_V4, new LibraryHistoryV4Command(other, null, true)).getStatusCode());
        assertEquals(2, ((List<?>) send(MessageType.LIBRARY_HISTORY_V4, new LibraryHistoryV4Command(admin, null, true)).getPayload()).size());
        Message legacy = send(MessageType.LIBRARY_HISTORY_V3, new LibraryHistoryV3Command(reader));
        assertTrue(((List<?>) legacy.getPayload()).get(0) instanceof BorrowRecord);
    }
    @Test void invalidPayloadOrUnauthenticatedSessionCannotReachMutation() {
        Book before = library.getBook("B001").getData();
        assertEquals(StatusCode.BAD_REQUEST, send(MessageType.LIBRARY_BOOK_UPDATE_V4, "wrong DTO").getStatusCode());
        assertNotEquals(StatusCode.OK, send(MessageType.LIBRARY_BOOK_UPDATE_V4,
                new LibraryBookUpdateV4Command("unknown", before, before)).getStatusCode());
        assertEquals(before, library.getBook("B001").getData());
    }
    @Test void realServerDispatchRoutesAllNewV4Messages() throws Exception {
        String admin = account("dispatch_admin", Role.ADMIN);
        try (ServerApplication server = new ServerApplication(0, users)) {
            Message copies = server.dispatch(Message.request("copies", MessageType.LIBRARY_COPIES_V4, new LibraryCopiesV4Command(admin, "B001")));
            assertEquals(StatusCode.OK, copies.getStatusCode());
            assertFalse(((List<?>) copies.getPayload()).isEmpty());
            Message history = server.dispatch(Message.request("history", MessageType.LIBRARY_HISTORY_V4, new LibraryHistoryV4Command(admin, null, true)));
            assertEquals(StatusCode.OK, history.getStatusCode());
            Message bookResponse = server.dispatch(Message.request("detail", MessageType.LIBRARY_DETAIL_V2, new LibraryDetailV2Command(admin, "B001")));
            Book before = (Book) bookResponse.getPayload();
            Message edited = server.dispatch(Message.request("edit", MessageType.LIBRARY_BOOK_UPDATE_V4,
                    new LibraryBookUpdateV4Command(admin, before, before)));
            assertEquals(StatusCode.OK, edited.getStatusCode());
        }
    }
}
