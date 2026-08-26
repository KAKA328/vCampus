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
    void messageTypesExposeCourseManagementContract() {
        assertEquals("COURSE_CREATE", MessageType.valueOf("COURSE_CREATE").name());
        assertEquals("COURSE_UPDATE", MessageType.valueOf("COURSE_UPDATE").name());
        assertEquals("COURSE_DEACTIVATE", MessageType.valueOf("COURSE_DEACTIVATE").name());
        assertEquals("COURSE_GRADE_WRITE", MessageType.valueOf("COURSE_GRADE_WRITE").name());
    }

    @Test
    void messageTypesExposeFiveModuleIntegrationContract() {
        assertEquals("STUDENT_REVIEW", MessageType.valueOf("STUDENT_REVIEW").name());
        assertEquals("STORE_ORDER_QUERY", MessageType.valueOf("STORE_ORDER_QUERY").name());
    }
}
