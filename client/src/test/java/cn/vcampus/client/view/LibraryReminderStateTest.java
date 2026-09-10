package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryReminderStateTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    @TempDir Path storage;
    private final Map<String, Map<String, String>> memory = new HashMap<String, Map<String, String>>();

    @Test
    void onlyActiveRecordsInsideWarningWindowAreUnread() {
        BorrowRecord overdue = record("overdue", TODAY.minusDays(1));
        BorrowRecord dueToday = record("today", TODAY);
        BorrowRecord soon = record("soon", TODAY.plusDays(3));
        List<BorrowRecord> records = Arrays.asList(overdue, dueToday, soon,
                record("later", TODAY.plusDays(4)), overdue.returned(TODAY), null);

        assertEquals(Arrays.asList(overdue, dueToday, soon), state().unread(records, TODAY));
        assertEquals(6, records.size());
    }

    @Test
    void acknowledgementSurvivesRefreshAndNewStateWithFreshMemory() throws IOException {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        state().acknowledge(records, TODAY);

        assertTrue(state().unread(records, TODAY).isEmpty());
        LibraryReminderState reopened = LibraryReminderState.forReader("campus.example", 19090,
                "reader", storage, new HashMap<String, Map<String, String>>());
        assertTrue(reopened.unread(records, TODAY).isEmpty(), "persistent storage must survive relogin");
        try (Stream<Path> files = Files.list(storage)) {
            assertEquals(1L, files.count(), "atomic save must not leave temporary files");
        }
    }

    @Test
    void readerHostAndPortHaveIndependentAcknowledgements() {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        state().acknowledge(records, TODAY);

        assertEquals(records, state("campus.example", 19090, "other-reader").unread(records, TODAY));
        assertEquals(records, state("other.example", 19090, "reader").unread(records, TODAY));
        assertEquals(records, state("campus.example", 19091, "reader").unread(records, TODAY));
        assertTrue(state(" CAMPUS.EXAMPLE ", 19090, "reader").unread(records, TODAY).isEmpty());
    }

    @Test
    void sameReminderReturnsOnTheNextDayAndEscalatesToOverdue() {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        state().acknowledge(records, TODAY);

        assertEquals(records, state().unread(records, TODAY.plusDays(1)));
        assertEquals(1, LibraryDueReminder.summarize(state().unread(records, TODAY.plusDays(1)),
                TODAY.plusDays(1)).getOverdueCount());
    }

    @Test
    void newRecordsAndChangedDueDatesAreNotHiddenByAnEarlierAcknowledgement() {
        BorrowRecord original = record("one", TODAY.plusDays(2));
        state().acknowledge(Collections.singletonList(original), TODAY);
        BorrowRecord changedDue = record("one", TODAY.plusDays(3));
        BorrowRecord newlyAdded = record("two", TODAY.plusDays(2));

        assertEquals(Arrays.asList(changedDue, newlyAdded), state().unread(
                Arrays.asList(original, changedDue, newlyAdded), TODAY));
    }

    @Test
    void returnedRecordsDoNotProduceRemindersAndAcknowledgementDoesNotReturnBooks() {
        BorrowRecord original = record("one", TODAY);
        state().acknowledge(Collections.singletonList(original), TODAY);

        assertEquals(BorrowStatus.BORROWED, original.getStatus());
        assertTrue(state().unread(Collections.singletonList(original.returned(TODAY)),
                TODAY.plusDays(1)).isEmpty());
    }

    @Test
    void acknowledgingReturnedAndDistantRecordsDoesNotHideFutureWarnings() {
        BorrowRecord later = record("later", TODAY.plusDays(4));
        BorrowRecord returned = record("returned", TODAY);
        state().acknowledge(Arrays.asList(later, returned.returned(TODAY), null), TODAY);

        assertEquals(Collections.singletonList(returned),
                state().unread(Collections.singletonList(returned), TODAY));
        assertEquals(Collections.singletonList(later),
                state().unread(Collections.singletonList(later), TODAY.plusDays(1)));
    }

    @Test
    void unavailableStorageFallsBackToSharedIsolatedMemory() {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        LibraryReminderState offline = LibraryReminderState.forReader("campus.example", 19090,
                "reader", null, memory);
        offline.acknowledge(records, TODAY);

        assertTrue(LibraryReminderState.forReader("campus.example", 19090, "reader", null, memory)
                .unread(records, TODAY).isEmpty());
        assertEquals(records, LibraryReminderState.forReader("campus.example", 19090,
                "other-reader", null, memory).unread(records, TODAY));
    }

    @Test
    void deniedWritesDoNotInterruptAcknowledgementOrRefresh() {
        TestStorage failing = new TestStorage();
        failing.denyWrites = true;
        Map<String, String> fallback = new HashMap<String, String>();
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        LibraryReminderState.withStorage(failing, fallback).acknowledge(records, TODAY);

        assertTrue(LibraryReminderState.withStorage(failing, fallback).unread(records, TODAY).isEmpty());
        assertEquals(records, LibraryReminderState.withStorage(failing, fallback).unread(records, TODAY.plusDays(1)));
    }

    @Test
    void failedSavePreservesProcessLocalAcknowledgement() {
        TestStorage failing = new TestStorage();
        failing.failSave = true;
        Map<String, String> fallback = new HashMap<String, String>();
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        LibraryReminderState.withStorage(failing, fallback).acknowledge(records, TODAY);

        assertTrue(LibraryReminderState.withStorage(failing, fallback).unread(records, TODAY).isEmpty());
    }

    @Test
    void deniedAndFailedReadsStillAllowAcknowledgementWithoutBlockingBorrowing() {
        for (boolean securityFailure : new boolean[] {false, true}) {
            TestStorage failing = new TestStorage();
            failing.denyReads = securityFailure;
            failing.failRead = !securityFailure;
            Map<String, String> fallback = new HashMap<String, String>();
            List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
            LibraryReminderState state = LibraryReminderState.withStorage(failing, fallback);
            assertEquals(records, state.unread(records, TODAY));
            state.acknowledge(records, TODAY);

            assertTrue(LibraryReminderState.withStorage(failing, fallback).unread(records, TODAY).isEmpty());
        }
    }

    @Test
    void invalidIdentityAndNullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> state(" ", 19090, "reader"));
        assertThrows(IllegalArgumentException.class, () -> state("campus.example", 0, "reader"));
        assertThrows(IllegalArgumentException.class, () -> state("campus.example", 19090, null));
        assertThrows(IllegalArgumentException.class, () -> state().unread(null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> state().acknowledge(
                Collections.<BorrowRecord>emptyList(), null));
    }

    @Test
    void memoryOnlyStatesAreCompletelyIndependent() {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        LibraryReminderState first = LibraryReminderState.memoryOnly();
        first.acknowledge(records, TODAY);

        assertTrue(first.unread(records, TODAY).isEmpty());
        assertEquals(records, LibraryReminderState.memoryOnly().unread(records, TODAY));
    }

    @Test
    void corruptPropertiesFallBackWithoutPreventingAcknowledgement() throws IOException {
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        state().acknowledge(records, TODAY);
        Path file;
        try (Stream<Path> files = Files.list(storage)) {
            file = files.findFirst().get();
        }
        Files.write(file, "invalid=\\uZZZZ".getBytes(StandardCharsets.ISO_8859_1));
        LibraryReminderState reopened = LibraryReminderState.forReader("campus.example", 19090,
                "reader", storage, new HashMap<String, Map<String, String>>());

        assertEquals(records, reopened.unread(records, TODAY));
        reopened.acknowledge(records, TODAY);
        assertTrue(reopened.unread(records, TODAY).isEmpty());
    }

    @Test
    void unusableFilesystemPathFallsBackToMemory() throws IOException {
        Path regularFile = Files.createFile(storage.resolve("not-a-directory"));
        List<BorrowRecord> records = Collections.singletonList(record("one", TODAY));
        LibraryReminderState state = LibraryReminderState.forReader("campus.example", 19090,
                "reader", regularFile, memory);

        state.acknowledge(records, TODAY);
        assertTrue(state.unread(records, TODAY).isEmpty());
    }

    private LibraryReminderState state() {
        return state("campus.example", 19090, "reader");
    }

    private LibraryReminderState state(String host, int port, String userId) {
        return LibraryReminderState.forReader(host, port, userId, storage, memory);
    }

    private static BorrowRecord record(String id, LocalDate dueDate) {
        return new BorrowRecord("order-" + id, id, "reader", "B001", TODAY.minusDays(30),
                dueDate, null, BorrowStatus.BORROWED);
    }

    private static final class TestStorage implements LibraryReminderState.Storage {
        private final Map<String, String> values = new HashMap<String, String>();
        private boolean denyWrites;
        private boolean denyReads;
        private boolean failRead;
        private boolean failSave;

        @Override public Map<String, String> load() throws IOException {
            if (denyReads) throw new SecurityException("test read denied");
            if (failRead) throw new IOException("test read failed");
            return new HashMap<String, String>(values);
        }

        @Override public void save(Map<String, String> updated) throws IOException {
            if (denyWrites) throw new SecurityException("test write denied");
            if (failSave) throw new IOException("test save failed");
            values.clear();
            values.putAll(updated);
        }
    }
}
