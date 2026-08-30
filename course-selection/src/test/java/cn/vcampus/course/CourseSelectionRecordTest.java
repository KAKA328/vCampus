package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CourseSelectionRecordTest {

    private static final LocalDateTime SELECTED_AT = LocalDateTime.of(2026, 9, 2, 9, 30);

    @Test
    void storesSelectionInformationAndCreatesDroppedCopy() {
        CourseSelectionRecord record = record("RECORD-001", "STUDENT-001", "OFFER-001");
        CourseSelectionRecord dropped = record.withDroppedAt(SELECTED_AT.plusDays(1));

        assertEquals("STUDENT-001", record.getStudentId());
        assertEquals("OFFER-001", record.getOfferingId());
        assertEquals("ROUND-INITIAL", record.getRoundId());
        assertEquals(SelectionType.REQUIRED, record.getSelectionType());
        assertEquals(SelectionRecordStatus.DROPPED, dropped.getStatus());
        assertFalse(dropped.isActive());
    }

    @Test
    void rejectsInvalidRecordInformation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionRecord("", "STUDENT-001", "OFFER-001", "ROUND-INITIAL",
                        SelectionType.REQUIRED, SELECTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionRecord("RECORD-001", "STUDENT-001", "OFFER-001",
                        "ROUND-INITIAL", null, SELECTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> record("RECORD-001", "STUDENT-001", "OFFER-001")
                        .withDroppedAt(SELECTED_AT.minusMinutes(1)));
    }

    private static CourseSelectionRecord record(String recordId, String studentId, String offeringId) {
        return new CourseSelectionRecord(recordId, studentId, offeringId, "ROUND-INITIAL",
                SelectionType.REQUIRED, SELECTED_AT);
    }
}
