package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibrarySocketConcurrencyTest {
    @TempDir static Path directory;
    Path database;
    LibrarySocketFixture wire;
    String admin;
    final List<String> readers = new ArrayList<String>();

    @BeforeAll void start() throws Exception {
        database = directory.resolve("network.accdb");
        LibraryAcceptanceData.create(LibraryV4TestFixture.migration().toAbsolutePath().normalize().getParent().getParent().getParent(),
                database, LocalDate.now());
        wire = new LibrarySocketFixture(database);
        admin = wire.login("demo_librarian");
        for (int i = 1; i <= 8; i++) readers.add(wire.login(String.format("li_race%02d", i)));
    }
    @AfterAll void stop() throws Exception { if (wire != null) wire.close(); }
    @AfterEach void integrity() throws Exception { LibraryV4TestFixture.verify(database); }
    AccessLibraryRepository observed() { return new AccessLibraryRepository(database); }

    void statuses(List<Message> results, int success, StatusCode rejected) {
        assertEquals(success, results.stream().filter(r -> r.getStatusCode() == StatusCode.OK).count());
        for (Message result : results) if (result.getStatusCode() != StatusCode.OK) {
            assertEquals(rejected, result.getStatusCode(), String.valueOf(result.getPayload()));
        }
    }
    BorrowRecord borrow(String token, String book) throws Exception {
        Message result = wire.send(MessageType.LIBRARY_BORROW_V2, new LibraryBorrowV2Command(token, book));
        assertEquals(StatusCode.OK, result.getStatusCode());
        return (BorrowRecord) ((List<?>) result.getPayload()).get(0);
    }

    @Test void eightSocketClientsContendForOneAndThreePhysicalCopies() throws Exception {
        for (String id : new String[] {"LI-LAST", "LI-MULTI"}) {
            int count = id.equals("LI-LAST") ? 1 : 3;
            statuses(wire.race(MessageType.LIBRARY_BORROW_V2,
                    i -> new LibraryBorrowV2Command(readers.get(i), id)), count, StatusCode.CONFLICT);
            assertEquals(0, observed().findBook(id).getAvailableCopies());
            Set<String> copies = new HashSet<String>();
            for (LibraryLoanSnapshot snapshot : observed().loanSnapshots(null).getData()) {
                if (snapshot.getRecord().getBookId().equals(id)) assertTrue(copies.add(snapshot.getCopyId()));
            }
            assertEquals(count, copies.size());
        }
    }

    @Test void oneSessionRepeatedRequestsCannotCreateDuplicateLoansOrReturns() throws Exception {
        String token = readers.get(0);
        List<Message> responses = wire.race(MessageType.LIBRARY_BORROW_V2,
                i -> new LibraryBorrowV2Command(token, "LI-RACE-RETURN"));
        statuses(responses, 1, StatusCode.CONFLICT);
        Message winner = responses.stream().filter(m -> m.getStatusCode() == StatusCode.OK).findFirst().get();
        BorrowRecord record = (BorrowRecord) ((List<?>) winner.getPayload()).get(0);
        statuses(wire.race(MessageType.LIBRARY_RETURN_V2,
                i -> new LibraryReturnV2Command(token, record.getRecordId())), 1, StatusCode.NOT_FOUND);
        assertEquals(1, observed().findBook("LI-RACE-RETURN").getAvailableCopies());
    }

    @Test void sameSessionParallelTitlesRespectFiveLoanLimitAndRejectedBatchIsAtomic() throws Exception {
        String token = wire.login("li_quota4");
        Message batch = wire.send(MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(token, Arrays.asList("LI-Q5", "LI-Q6")));
        assertEquals(StatusCode.CONFLICT, batch.getStatusCode());
        assertEquals(4, observed().findBorrowHistory("li_quota4").size());
        statuses(wire.race(MessageType.LIBRARY_BORROW_V2,
                i -> new LibraryBorrowV2Command(token, "LI-Q" + (5 + i % 4))), 1, StatusCode.CONFLICT);
        assertEquals(5, observed().findBorrowHistory("li_quota4").size());
    }

    @Test void duplicateLossAndPaymentRequestsOnlyReduceStockAndWalletOnce() throws Exception {
        String token = readers.get(7);
        BorrowRecord record = borrow(token, "LI-RACE-PAY");
        List<Message> losses = wire.race(MessageType.LIBRARY_LOSS_DECLARE_V3,
                i -> new LibraryLossDeclareV3Command(admin, record.getRecordId()));
        statuses(losses, 8, StatusCode.OK); // 幂等返回原账单，不是八次遗失。
        String bill = ((LibraryCompensation) losses.get(0).getPayload()).getCompensationId();
        for (Message loss : losses) assertEquals(bill, ((LibraryCompensation) loss.getPayload()).getCompensationId());
        statuses(wire.race(MessageType.LIBRARY_COMPENSATION_PAY_V3,
                i -> new LibraryCompensationPayV3Command(token, bill)), 8, StatusCode.OK);
        AccessWalletRepository wallet = new AccessWalletRepository(database);
        assertEquals(6050, wallet.findByUserId("li_race08").getBalanceCents());
        assertEquals(1, wallet.findTransactionsByUserId("li_race08").size());
        assertEquals(0, observed().findBook("LI-RACE-PAY").getTotalCopies());
        assertEquals(0, observed().findBook("LI-RACE-PAY").getAvailableCopies());
    }

    @Test void adminStaleEditsCannotOverwriteTheWinningMetadataChange() throws Exception {
        Book before = observed().findBook("LI-ROUND");
        statuses(wire.race(MessageType.LIBRARY_BOOK_UPDATE_V4, i -> new LibraryBookUpdateV4Command(admin, before,
                new Book(before.getBookId(), "并发编辑胜者-" + i, before.getAuthor(), before.getIsbn(),
                        before.getCategory(), before.getPublisher(), 0, before.getTotalCopies(),
                        before.getAvailableCopies(), before.getLocation()))), 1, StatusCode.CONFLICT);
        assertEquals(0, observed().findBook("LI-ROUND").getPrice());
    }

    @Test void returningAndDeclaringTheSameCopyLostCannotBothCommit() throws Exception {
        String token = readers.get(6);
        BorrowRecord record = borrow(token, "LI-RACE-LOSS");
        List<Message> results = wire.race(i -> i % 2 == 0 ? MessageType.LIBRARY_RETURN_V2
                        : MessageType.LIBRARY_LOSS_DECLARE_V3,
                i -> i % 2 == 0 ? new LibraryReturnV2Command(token, record.getRecordId())
                        : new LibraryLossDeclareV3Command(admin, record.getRecordId()));
        BorrowRecord stored = observed().findBorrowHistory("li_race07").stream()
                .filter(r -> r.getRecordId().equals(record.getRecordId())).findFirst().get();
        if (stored.getStatus() == BorrowStatus.RETURNED) {
            assertEquals(1, observed().findBook("LI-RACE-LOSS").getAvailableCopies());
            assertEquals(1, results.stream().filter(r -> r.getStatusCode() == StatusCode.OK).count());
            for (int i = 1; i < 8; i += 2) assertEquals(StatusCode.CONFLICT, results.get(i).getStatusCode());
        } else {
            assertEquals(BorrowStatus.LOST, stored.getStatus());
            assertEquals(0, observed().findBook("LI-RACE-LOSS").getTotalCopies());
            assertEquals(0, observed().findBook("LI-RACE-LOSS").getAvailableCopies());
            for (int i = 0; i < 8; i++) assertEquals(i % 2 == 0 ? StatusCode.NOT_FOUND : StatusCode.OK,
                    results.get(i).getStatusCode());
        }
    }

    @Test void reloginInvalidatesOldSessionRatherThanProducingAnInventoryBug() throws Exception {
        String old = wire.login("li_empty"), current = wire.login("li_empty");
        assertNotEquals(StatusCode.OK, wire.send(MessageType.LIBRARY_BORROW_V2,
                new LibraryBorrowV2Command(old, "LI-ZERO")).getStatusCode());
        assertTrue(observed().findBorrowHistory("li_empty").isEmpty());
        BorrowRecord record = borrow(current, "LI-ZERO");
        assertEquals("li_empty", record.getUserId());
    }
}
