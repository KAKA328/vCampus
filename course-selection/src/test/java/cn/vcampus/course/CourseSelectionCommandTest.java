package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseSelectionCommandTest {
    @Test
    void storesTrimmedRoundAndOfferingWithoutStudentId() {
        CourseSelectionCommand command = new CourseSelectionCommand(
                " token-001 ", " ROUND-INITIAL ", " OFFER-JAVA-01 ");

        assertEquals("token-001", command.getToken());
        assertEquals("ROUND-INITIAL", command.getRoundId());
        assertEquals("OFFER-JAVA-01", command.getOfferingId());
    }

    @Test
    void rejectsBlankRequestFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("", "ROUND-INITIAL", "OFFER-JAVA-01"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("token-001", " ", "OFFER-JAVA-01"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("token-001", "ROUND-INITIAL", null));
    }
}
