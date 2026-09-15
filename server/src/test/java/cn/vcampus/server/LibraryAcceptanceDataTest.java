package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.*;
import cn.vcampus.user.UserCredentials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryAcceptanceDataTest {
    @TempDir static Path directory;
    Path database;
    AccessLibraryRepository library;
    final LocalDate base = LocalDate.of(2026, 9, 14);

    @BeforeAll void create() throws Exception {
        database = directory.resolve("acceptance.accdb");
        LibraryAcceptanceData.create(LibraryV4TestFixture.migration().toAbsolutePath().normalize().getParent().getParent().getParent(), database, base);
        library = new AccessLibraryRepository(database);
    }

    @Test void catalogAccountsAndRelationshipsAreComplete() throws Exception {
        assertEquals(80, library.search("", null).size());
        assertEquals(25, library.findAllBorrowHistory().size());
        assertEquals(34, LibraryV4TestFixture.count(database, "tblUser"));
        assertEquals(80, LibraryV4TestFixture.count(database, "tblLibraryCopyCatalog"));
        assertEquals(25, LibraryV4TestFixture.count(database, "tblLibraryLoanSnapshot"));
        assertEquals(5, LibraryV4TestFixture.count(database, "tblLibraryCompensation"));
        assertEquals(1, LibraryV4TestFixture.count(database, "tblWalletTransaction"));
        assertEquals(105, LibraryV4TestFixture.count(database, "tblProduct"));
        LibraryV4TestFixture.verify(database);
        assertTrue(library.findBorrowHistory("li_empty").isEmpty());
    }

    @Test void datesQuotaSnapshotAndWalletEdgesMatchTheGuide() {
        List<BorrowRecord> dates = library.findBorrowHistory("li_dates");
        Set<LocalDate> actual = new HashSet<LocalDate>();
        for (BorrowRecord record : dates) actual.add(record.getDueDate());
        Set<LocalDate> expected = new HashSet<LocalDate>();
        for (int offset : new int[] {-1, 0, 1, 3, 4}) expected.add(base.plusDays(offset));
        assertEquals(expected, actual);
        assertEquals(4, library.findBorrowHistory("li_quota4").size());
        assertEquals(5, library.findBorrowHistory("li_quota5").size());
        LibraryLoanSnapshot snapshot = library.loanSnapshots("li_teacher").getData().stream()
                .filter(s -> s.getRecord().getBookId().equals("LI-EDIT")).findFirst().get();
        assertEquals("借阅时的原书名", snapshot.getBookTitle());
        assertEquals("编辑后的当前书名", library.findBook("LI-EDIT").getTitle());
        assertFalse(snapshot.isHistoricalBackfill());
        AccessWalletRepository wallet = new AccessWalletRepository(database);
        assertEquals(3949, wallet.findByUserId("li_low").getBalanceCents());
        assertEquals(3950, wallet.findByUserId("li_exact").getBalanceCents());
        assertEquals(0, wallet.findByUserId("li_free").getBalanceCents());
        assertEquals(6050, wallet.findByUserId("li_paid").getBalanceCents());
        assertEquals(BorrowStatus.COMPENSATED, library.findBorrowHistory("li_paid").get(0).getStatus());
        assertEquals(BorrowStatus.LOST, library.findBorrowHistory("li_free").get(0).getStatus());
        assertEquals(0, library.findBook("LI-EMPTY").getAvailableCopies());
    }

    @Test void accountsReallyLoginButDisabledAccountDoesNot() {
        cn.vcampus.user.UserManagementService users = UserServiceFactory.create(new String[] {"--db", database.toString()});
        for (String id : new String[] {"li_empty", "li_teacher", "li_race01", "li_race08", "demo_librarian"}) {
            assertEquals(StatusCode.OK, users.login(new UserCredentials(id, "Demo123", id, "STUDENT")).getStatus(), id);
        }
        assertNotEquals(StatusCode.OK, users.login(new UserCredentials("li_disabled", "Demo123", "验收停用", "STUDENT")).getStatus());
    }

    @Test void refusingExistingOutputLeavesEveryByteUnchanged() throws Exception {
        byte[] before = Files.readAllBytes(database);
        assertThrows(IllegalArgumentException.class, () -> LibraryAcceptanceData.create(
                LibraryV4TestFixture.migration().toAbsolutePath().normalize().getParent().getParent().getParent(), database, base));
        assertArrayEquals(before, Files.readAllBytes(database));
        assertThrows(IllegalArgumentException.class, () -> LibraryAcceptanceData.main(new String[] {}));
    }
}
