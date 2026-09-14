package cn.vcampus.library;

import java.io.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryV4ContractTest {
    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(value); }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (T) input.readObject(); }
    }
    @Test void newCommandsRoundTripWithoutAddingFieldsToLegacyRecords() throws Exception {
        Book before = new Book("B", "书名", "作者"), after = new Book("B", "新版", "作者");
        LibraryBookUpdateV4Command edit = roundTrip(new LibraryBookUpdateV4Command(" token ", before, after));
        assertEquals("token", edit.getToken()); assertEquals(before, edit.getExpected()); assertEquals(after, edit.getReplacement());
        LibraryCopiesV4Command copies = roundTrip(new LibraryCopiesV4Command("token", " B "));
        assertEquals("B", copies.getBookId());
        LibraryHistoryV4Command history = roundTrip(new LibraryHistoryV4Command("token", " reader ", true));
        assertTrue(history.isAllUsers()); assertEquals("reader", history.getTargetUserId());
        assertEquals(1L, ObjectStreamClass.lookup(BorrowRecord.class).getSerialVersionUID());
        assertEquals(3L, ObjectStreamClass.lookup(Book.class).getSerialVersionUID());
    }
    @Test void snapshotCarriesImmutableNameAndCopyThroughSerializationAndLifecycleChange() throws Exception {
        BorrowRecord record = new BorrowRecord("O", "R", "reader", "B", LocalDate.now(), LocalDate.now().plusDays(30), null, BorrowStatus.BORROWED);
        LibraryLoanSnapshot snapshot = roundTrip(new LibraryLoanSnapshot(record, "旧书名", "CP-1", false));
        LibraryLoanSnapshot returned = snapshot.withRecord(record.returned(LocalDate.now()));
        assertEquals("旧书名", returned.getBookTitle()); assertEquals("CP-1", returned.getCopyId());
        assertFalse(returned.isHistoricalBackfill()); assertEquals(BorrowStatus.RETURNED, returned.getRecord().getStatus());
        LibraryCopy copy = roundTrip(new LibraryCopy("CP-1", "B", LibraryCopyStatus.LOST));
        assertEquals(LibraryCopyStatus.LOST, copy.getStatus());
        assertThrows(IllegalArgumentException.class, () -> new LibraryCopiesV4Command(" ", "B"));
    }
    @Test void metadataIsRevalidatedForLengthAndMalformedSerializedPrice() throws Exception {
        Book before = new Book("B", "书名", "作者");
        Book tooLong = new Book("B", new String(new char[121]).replace('\0', '字'), "作者");
        assertThrows(IllegalArgumentException.class, () -> LibraryBookMetadata.validateEdit("admin", before, tooLong));
        Book corrupted = new Book("B", "书名", "作者");
        java.lang.reflect.Field price = Book.class.getDeclaredField("price");
        price.setAccessible(true); price.setDouble(corrupted, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> LibraryBookMetadata.validateEdit("admin", before, corrupted));
        assertThrows(IllegalArgumentException.class, () -> LibraryCopyRules.checkBatch(LibraryCopyRules.MAX_BATCH + 1));
        assertDoesNotThrow(() -> LibraryCopyRules.checkBatch(LibraryCopyRules.MAX_BATCH));
    }
}
