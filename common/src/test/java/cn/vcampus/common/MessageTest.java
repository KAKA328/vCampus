package cn.vcampus.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTest {
    @Test
    void messageRoundTripsThroughObjectStream() throws Exception {
        Message original = Message.request("req-1", MessageType.LOGIN, "demo-user");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(original);

        Message copy = (Message) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();

        assertEquals(original, copy);
    }
}
