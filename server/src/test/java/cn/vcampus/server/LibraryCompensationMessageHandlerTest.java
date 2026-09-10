package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.*;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.store.*;
import cn.vcampus.user.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the real server dispatch, shared wallet wiring and permission boundary. */
class LibraryCompensationMessageHandlerTest {
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private String student;
    private String teacher;
    private String librarian;

    @BeforeEach
    void accounts() {
        student = account("reader", Role.STUDENT);
        teacher = account("teacher", Role.TEACHER);
        librarian = account("librarian", Role.LIBRARIAN);
    }

    @Test
    void serverSharesStoreWalletAndPaysOnlyOnceWithVisibleLedger() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            BorrowRecord loan = borrow(server, student, "B001");
            LibraryCompensation bill = loss(server, librarian, loan.getRecordId());
            assertEquals(12900L, bill.getAmountCents());
            assertEquals("reader", bill.getUserId());
            assertEquals(CompensationStatus.PENDING, bill.getStatus());
            assertEquals(StatusCode.PAYMENT_REQUIRED, send(server, MessageType.LIBRARY_COMPENSATION_PAY_V3,
                    new LibraryCompensationPayV3Command(student, bill.getCompensationId())).getStatusCode());
            assertEquals(StatusCode.OK, send(server, MessageType.STORE_ACCOUNT_RECHARGE,
                    new StoreAccountRechargeCommand(student, 20000L)).getStatusCode());
            assertEquals(20000L, send(server, MessageType.LIBRARY_WALLET_QUERY_V3,
                    new LibraryWalletQueryV3Command(student)).getPayload());
            for (int i = 0; i < 2; i++) {
                Message paid = send(server, MessageType.LIBRARY_COMPENSATION_PAY_V3,
                        new LibraryCompensationPayV3Command(student, bill.getCompensationId()));
                assertEquals(StatusCode.OK, paid.getStatusCode());
                assertEquals(CompensationStatus.PAID, ((LibraryCompensation) paid.getPayload()).getStatus());
            }
            assertEquals(7100L, send(server, MessageType.STORE_ACCOUNT_QUERY,
                    new StoreAccountQueryCommand(student)).getPayload());
            Message ledger = send(server, MessageType.STORE_ACCOUNT_LEDGER_V2,
                    new StoreAccountLedgerV2Command(student));
            assertEquals(StatusCode.OK, ledger.getStatusCode());
            List<?> entries = (List<?>) ledger.getPayload();
            assertEquals(2, entries.size());
            assertEquals(1L, entries.stream().map(value -> (WalletTransaction) value)
                    .filter(value -> value.getType() == WalletTransactionType.LIBRARY_LOSS).count());
            assertEquals(StatusCode.CONFLICT, send(server, MessageType.STORE_ACCOUNT_LEDGER,
                    new StoreAccountQueryCommand(student)).getStatusCode());
            assertEquals(BorrowStatus.COMPENSATED, history(server, student).get(0).getStatus());
            Book current = (Book) send(server, MessageType.LIBRARY_DETAIL_V2,
                    new LibraryDetailV2Command(student, "B001")).getPayload();
            assertEquals(2, current.getTotalCopies());
            assertEquals(2, current.getAvailableCopies());
            assertNotEquals(StatusCode.OK, send(server, MessageType.LIBRARY_RETURN_V2,
                    new LibraryReturnV2Command(student, loan.getRecordId())).getStatusCode());
        }
    }

    @Test
    void onlyLibraryManagersCanDeclareLossAndRepeatedDeclarationReturnsSameBill() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            BorrowRecord loan = borrow(server, student, "B001");
            for (String token : new String[] {student, teacher,
                    account("store", Role.STORE_MANAGER), account("academic", Role.ACADEMIC_ADMIN)}) {
                assertEquals(StatusCode.FORBIDDEN, send(server, MessageType.LIBRARY_LOSS_DECLARE_V3,
                        new LibraryLossDeclareV3Command(token, loan.getRecordId())).getStatusCode());
            }
            LibraryCompensation first = loss(server, librarian, loan.getRecordId());
            LibraryCompensation second = loss(server, account("admin", Role.ADMIN), loan.getRecordId());
            assertEquals(first.getCompensationId(), second.getCompensationId());
            assertEquals(BorrowStatus.LOST, history(server, student).get(0).getStatus());
        }
    }

    @Test
    void readerCannotInspectAllBillsOrPayForSomeoneElseEvenWhenManager() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            LibraryCompensation bill = loss(server, librarian, borrow(server, student, "B001").getRecordId());
            for (String token : new String[] {teacher, librarian, account("admin", Role.ADMIN)}) {
                assertNotEquals(StatusCode.OK, send(server, MessageType.LIBRARY_COMPENSATION_PAY_V3,
                        new LibraryCompensationPayV3Command(token, bill.getCompensationId())).getStatusCode());
            }
            assertEquals(StatusCode.FORBIDDEN, send(server, MessageType.LIBRARY_COMPENSATION_LIST_V3,
                    new LibraryCompensationListV3Command(student, true)).getStatusCode());
            assertEquals(0, ((List<?>) send(server, MessageType.LIBRARY_COMPENSATION_LIST_V3,
                    new LibraryCompensationListV3Command(teacher, false)).getPayload()).size());
            assertEquals(1, ((List<?>) send(server, MessageType.LIBRARY_COMPENSATION_LIST_V3,
                    new LibraryCompensationListV3Command(student, false)).getPayload()).size());
            assertEquals(1, ((List<?>) send(server, MessageType.LIBRARY_COMPENSATION_LIST_V3,
                    new LibraryCompensationListV3Command(librarian, true)).getPayload()).size());
        }
    }

    @Test
    void historyV3KeepsScopeAuthorizationAndV2NeverSerializesNewEnumStates() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            BorrowRecord loan = borrow(server, student, "B001");
            assertEquals(StatusCode.OK, send(server, MessageType.LIBRARY_HISTORY_V2,
                    new LibraryHistoryV2Command(student)).getStatusCode());
            loss(server, librarian, loan.getRecordId());
            Message old = send(server, MessageType.LIBRARY_HISTORY_V2, new LibraryHistoryV2Command(student));
            assertEquals(StatusCode.CONFLICT, old.getStatusCode());
            assertTrue(old.getPayload() instanceof String);
            assertEquals(StatusCode.FORBIDDEN, send(server, MessageType.LIBRARY_HISTORY_V3,
                    new LibraryHistoryV3Command(teacher, "reader", false)).getStatusCode());
            assertEquals(StatusCode.OK, send(server, MessageType.LIBRARY_HISTORY_V3,
                    new LibraryHistoryV3Command(librarian, null, true)).getStatusCode());
        }
    }

    @Test
    void newEndpointsRejectInvalidSessionOrMalformedPayload() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            MessageType[] types = {MessageType.LIBRARY_HISTORY_V3, MessageType.LIBRARY_LOSS_DECLARE_V3,
                    MessageType.LIBRARY_COMPENSATION_LIST_V3, MessageType.LIBRARY_COMPENSATION_PAY_V3,
                    MessageType.LIBRARY_WALLET_QUERY_V3, MessageType.STORE_ACCOUNT_LEDGER_V2};
            Object[] commands = {new LibraryHistoryV3Command("invalid"),
                    new LibraryLossDeclareV3Command("invalid", "record"),
                    new LibraryCompensationListV3Command("invalid", false),
                    new LibraryCompensationPayV3Command("invalid", "bill"),
                    new LibraryWalletQueryV3Command("invalid"), new StoreAccountLedgerV2Command("invalid")};
            for (int i = 0; i < types.length; i++) {
                assertEquals(StatusCode.UNAUTHORIZED, send(server, types[i], commands[i]).getStatusCode());
                assertEquals(StatusCode.BAD_REQUEST, send(server, types[i], "wrong-type").getStatusCode());
            }
        }
    }

    @Test
    void storeOnlyRoleCannotQueryLibraryWalletAndLedgerV2StillNeedsStoreRead() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            assertEquals(StatusCode.FORBIDDEN, send(server, MessageType.LIBRARY_WALLET_QUERY_V3,
                    new LibraryWalletQueryV3Command(account("store", Role.STORE_MANAGER))).getStatusCode());
            assertEquals(StatusCode.FORBIDDEN, send(server, MessageType.STORE_ACCOUNT_LEDGER_V2,
                    new StoreAccountLedgerV2Command(librarian)).getStatusCode());
        }
    }

    private String account(String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "password", id, role.name());
        assertEquals(StatusCode.OK, users.register(credentials).getStatus());
        return users.login(credentials).getData().getToken();
    }

    private static Message send(ServerApplication server, MessageType type, Object command) {
        return server.dispatch(Message.request("compensation-test", type, command));
    }

    private static BorrowRecord borrow(ServerApplication server, String token, String id) {
        Message message = send(server, MessageType.LIBRARY_BORROW_V2, new LibraryBorrowV2Command(token, id));
        assertEquals(StatusCode.OK, message.getStatusCode());
        return (BorrowRecord) ((List<?>) message.getPayload()).get(0);
    }

    private static LibraryCompensation loss(ServerApplication server, String token, String record) {
        Message message = send(server, MessageType.LIBRARY_LOSS_DECLARE_V3,
                new LibraryLossDeclareV3Command(token, record));
        assertEquals(StatusCode.OK, message.getStatusCode());
        return (LibraryCompensation) message.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static List<BorrowRecord> history(ServerApplication server, String token) {
        Message response = send(server, MessageType.LIBRARY_HISTORY_V3, new LibraryHistoryV3Command(token));
        assertEquals(StatusCode.OK, response.getStatusCode());
        return (List<BorrowRecord>) response.getPayload();
    }
}
