package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseSelectionCommandTest {

    @Test
    void storesTrimmedRequestFields() {
        CourseSelectionCommand command = new CourseSelectionCommand(
                " token-001 ", " 20230001 ", " JAVA101 ");

        assertEquals("token-001", command.getToken());
        assertEquals("20230001", command.getStudentId());
        assertEquals("JAVA101", command.getCourseId());
    }

    @Test
    void rejectsBlankRequestFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("", "20230001", "JAVA101"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("token-001", " ", "JAVA101"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionCommand("token-001", "20230001", null));
    }
}
