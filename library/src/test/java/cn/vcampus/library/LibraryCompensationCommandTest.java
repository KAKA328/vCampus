package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class LibraryCompensationCommandTest {
    @Test
    void versionedCommandsRoundTripWithoutClientControlledMoneyOrPayer() throws Exception {
        LibraryLossDeclareV3Command loss = copy(new LibraryLossDeclareV3Command("token", "record"));
        assertEquals("token", loss.getToken());
        assertEquals("record", loss.getRecordId());
        LibraryCompensationPayV3Command pay = copy(new LibraryCompensationPayV3Command("token", "bill"));
        assertEquals("token", pay.getToken());
        assertEquals("bill", pay.getCompensationId());
        LibraryCompensationListV3Command list = copy(new LibraryCompensationListV3Command("token", true));
        assertEquals("token", list.getToken());
        assertTrue(list.isAllUsers());
        assertEquals("token", copy(new LibraryWalletQueryV3Command("token")).getToken());
        LibraryHistoryV3Command history = copy(new LibraryHistoryV3Command("token", "reader", false));
        assertEquals("reader", history.getTargetUserId());
        assertFalse(history.isAllUsers());
        for (Class<?> type : new Class<?>[] {LibraryCompensationPayV3Command.class,
                LibraryLossDeclareV3Command.class, LibraryWalletQueryV3Command.class}) {
            assertThrows(NoSuchFieldException.class, () -> type.getDeclaredField("amountCents"));
            assertThrows(NoSuchFieldException.class, () -> type.getDeclaredField("userId"));
        }
    }

    @Test
    void blankTokensAndBusinessKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LibraryLossDeclareV3Command(" ", "record"));
        assertThrows(IllegalArgumentException.class, () -> new LibraryLossDeclareV3Command("token", null));
        assertThrows(IllegalArgumentException.class, () -> new LibraryCompensationPayV3Command(null, "bill"));
        assertThrows(IllegalArgumentException.class, () -> new LibraryCompensationPayV3Command("token", " "));
        assertThrows(IllegalArgumentException.class, () -> new LibraryCompensationListV3Command("", false));
        assertThrows(IllegalArgumentException.class, () -> new LibraryWalletQueryV3Command(""));
        assertThrows(IllegalArgumentException.class, () -> new LibraryHistoryV3Command(null));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T copy(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(value); }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
