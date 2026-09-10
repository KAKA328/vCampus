package cn.vcampus.store;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class StoreAccountLedgerV2CommandTest {
    @Test
    void ledgerV2CarriesOnlyValidatedTokenAndRoundTrips() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new StoreAccountLedgerV2Command(" "));
        StoreAccountLedgerV2Command command = new StoreAccountLedgerV2Command(" token ");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(command); }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals("token", ((StoreAccountLedgerV2Command) input.readObject()).getToken());
        }
        assertThrows(NoSuchFieldException.class, () -> StoreAccountLedgerV2Command.class.getDeclaredField("userId"));
    }
}
