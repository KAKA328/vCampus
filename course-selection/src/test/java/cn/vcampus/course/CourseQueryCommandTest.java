package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseQueryCommandTest {

    @Test
    void allCoursesQueryDoesNotNeedStudentCredentials() {
        CourseQueryCommand command = CourseQueryCommand.allCourses();

        assertEquals(CourseQueryCommand.QueryType.ALL_COURSES, command.getQueryType());
        assertNull(command.getToken());
        assertNull(command.getStudentId());
    }

    @Test
    void selectedCoursesQueryStoresTrimmedCredentials() {
        CourseQueryCommand command = CourseQueryCommand.selectedCourses(
                " token-001 ", " 20230001 ");

        assertEquals(CourseQueryCommand.QueryType.SELECTED_COURSES, command.getQueryType());
        assertEquals("token-001", command.getToken());
        assertEquals("20230001", command.getStudentId());
    }

    @Test
    void selectedCoursesQueryRejectsBlankCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> CourseQueryCommand.selectedCourses("", "20230001"));
        assertThrows(IllegalArgumentException.class,
                () -> CourseQueryCommand.selectedCourses("token-001", " "));
    }
}
