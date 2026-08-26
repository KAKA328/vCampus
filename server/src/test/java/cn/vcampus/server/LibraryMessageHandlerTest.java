package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.InMemoryLibraryService;
import cn.vcampus.library.LibraryCommand;
import cn.vcampus.library.LibraryQueryCommand;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

class LibraryMessageHandlerTest {
    @Test
    void studentCannotBorrowForAnotherUser() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session student = login(users, "20230001", Role.STUDENT);
        InMemoryLibraryService library = new InMemoryLibraryService();
        LibraryMessageHandler handler = new LibraryMessageHandler(library, users);

        Message response = handler.handle(Message.request("borrow", MessageType.LIBRARY_BORROW,
                new LibraryCommand(student.getToken(), "20230002", "B001")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void librarianCanBorrowForPatron() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session librarian = login(users, "lib001", Role.LIBRARIAN);
        InMemoryLibraryService library = new InMemoryLibraryService();
        LibraryMessageHandler handler = new LibraryMessageHandler(library, users);

        Message response = handler.handle(Message.request("borrow", MessageType.LIBRARY_BORROW,
                new LibraryCommand(librarian.getToken(), "20230001", "B001")));

        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void storeManagerCannotEnterLibrary() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session storeManager = login(users, "store001", Role.STORE_MANAGER);
        LibraryMessageHandler handler = new LibraryMessageHandler(new InMemoryLibraryService(), users);

        Message response = handler.handle(Message.request("query", MessageType.LIBRARY_QUERY,
                new LibraryQueryCommand(storeManager.getToken(), "")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}
