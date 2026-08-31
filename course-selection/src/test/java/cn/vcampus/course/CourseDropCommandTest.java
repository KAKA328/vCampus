package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseDropCommandTest {
    @Test
    void storesTokenAndSelectionRecordId() {
        CourseDropRecordV2Command command = new CourseDropRecordV2Command("token-001", "RECORD-001");
        assertEquals("token-001", command.getToken());
        assertEquals("RECORD-001", command.getRecordId());
    }

    @Test
    void rejectsBlankTokenOrRecordId() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseDropRecordV2Command("", "RECORD-001"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseDropRecordV2Command("token-001", " "));
    }
}
