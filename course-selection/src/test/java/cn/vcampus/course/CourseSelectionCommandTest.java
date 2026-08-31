package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseSelectionCommandTest {
    @Test
    void storesTrimmedRoundAndOfferingWithoutStudentId() {
        CourseSelectOfferingV2Command command = new CourseSelectOfferingV2Command(
                " token-001 ", " ROUND-INITIAL ", " OFFER-JAVA-01 ");

        assertEquals("token-001", command.getToken());
        assertEquals("ROUND-INITIAL", command.getRoundId());
        assertEquals("OFFER-JAVA-01", command.getOfferingId());
    }

    @Test
    void rejectsBlankRequestFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectOfferingV2Command("", "ROUND-INITIAL", "OFFER-JAVA-01"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectOfferingV2Command("token-001", " ", "OFFER-JAVA-01"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectOfferingV2Command("token-001", "ROUND-INITIAL", null));
    }

    @Test
    void legacySelectionCommandKeepsOldCourseLevelShape() {
        CourseSelectionCommand command = new CourseSelectionCommand(" STU-001 ", " C001 ");

        assertEquals("STU-001", command.getStudentId());
        assertEquals("C001", command.getCourseId());
    }
}
