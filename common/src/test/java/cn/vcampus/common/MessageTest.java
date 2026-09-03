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

    @Test
    void messageTypesExposeUnifiedCourseManagementContract() {
        assertEquals("COURSE_MANAGE", MessageType.valueOf("COURSE_MANAGE").name());
    }

    @Test
    void messageTypesExposeExplicitCourseSelectionV2Contract() {
        assertEquals("COURSE_SELECTION_QUERY_V2",
                MessageType.valueOf("COURSE_SELECTION_QUERY_V2").name());
        assertEquals("COURSE_SELECT_OFFERING_V2",
                MessageType.valueOf("COURSE_SELECT_OFFERING_V2").name());
        assertEquals("COURSE_DROP_RECORD_V2",
                MessageType.valueOf("COURSE_DROP_RECORD_V2").name());
    }
}
