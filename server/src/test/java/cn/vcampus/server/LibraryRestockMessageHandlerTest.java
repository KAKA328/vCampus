package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.library.LibraryRestockV2Command;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

class LibraryRestockMessageHandlerTest {
    private final InMemoryLibraryService library = new InMemoryLibraryService();
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final LibraryMessageHandler handler = new LibraryMessageHandler(library, users);

    @Test
    void studentsTeachersAndInvalidSessionsCannotRestock() {
        Book original = library.getBook("B001").getData();
        for (Role role : new Role[] {Role.STUDENT, Role.TEACHER, Role.ACADEMIC_ADMIN, Role.STORE_MANAGER}) {
            assertEquals(StatusCode.FORBIDDEN, restock(account(role), "B001", 2).getStatusCode());
        }
        assertEquals(StatusCode.UNAUTHORIZED, restock("invalid", "B001", 2).getStatusCode());
        assertEquals(original, library.getBook("B001").getData());
    }

    @Test
    void librariansAndAdminsCanRestockAndReceiveUpdatedSnapshot() {
        int initial = library.getBook("B001").getData().getTotalCopies();
        for (Role role : new Role[] {Role.LIBRARIAN, Role.ADMIN}) {
            Message response = restock(account(role), "B001", 2);
            initial += 2;
            assertEquals(StatusCode.OK, response.getStatusCode());
            assertEquals(initial, ((Book) response.getPayload()).getTotalCopies());
        }
    }

    @Test
    void malformedPayloadAndDeserializedInvalidQuantityAreRejected() throws Exception {
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.LIBRARY_RESTOCK_V2, null)).getStatusCode());
        LibraryRestockV2Command tampered = new LibraryRestockV2Command(account(Role.LIBRARIAN), "B001", 1);
        java.lang.reflect.Field copies = LibraryRestockV2Command.class.getDeclaredField("copies");
        copies.setAccessible(true);
        copies.setInt(tampered, 0); // 反序列化不经过构造方法：业务层仍必须二次校验。
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad-count",
                MessageType.LIBRARY_RESTOCK_V2, tampered)).getStatusCode());
        assertEquals(3, library.getBook("B001").getData().getTotalCopies());
    }

    @Test
    void missingBookAndOverflowReturnExplicitFailures() {
        String token = account(Role.LIBRARIAN);
        assertEquals(StatusCode.NOT_FOUND, restock(token, "MISSING", 1).getStatusCode());
        assertEquals(StatusCode.BAD_REQUEST, restock(token, "B001", Integer.MAX_VALUE).getStatusCode());
        assertEquals(3, library.getBook("B001").getData().getTotalCopies());
    }

    @Test
    void serverDispatchRoutesNewMessageToLibraryPermissionBoundary() {
        String token = account(Role.LIBRARIAN);
        ServerApplication server = new ServerApplication(0, users);
        Message response = server.dispatch(Message.request("restock-dispatch", MessageType.LIBRARY_RESTOCK_V2,
                new LibraryRestockV2Command(token, "B001", 2)));
        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(5, ((Book) response.getPayload()).getTotalCopies());
    }

    private Message restock(String token, String bookId, int copies) {
        return handler.handle(Message.request("restock", MessageType.LIBRARY_RESTOCK_V2,
                new LibraryRestockV2Command(token, bookId, copies)));
    }

    private String account(Role role) {
        UserCredentials credentials = new UserCredentials(role.name(), "password", role.name(), role.name());
        users.register(credentials);
        return users.login(credentials).getData().getToken();
    }
}
