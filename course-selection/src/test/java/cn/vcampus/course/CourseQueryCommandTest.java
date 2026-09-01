package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseQueryCommandTest {
    @Test
    void availableRoundsQueryStoresToken() {
        CourseSelectionQueryV2Command command = CourseSelectionQueryV2Command.availableRounds(
                " token-001 ");

        assertEquals(CourseSelectionQueryV2Command.QueryType.AVAILABLE_ROUNDS,
                command.getQueryType());
        assertEquals("token-001", command.getToken());
        assertNull(command.getRoundId());
    }

    @Test
    void offeringAndSelectedQueriesDoNotCarryStudentId() {
        CourseSelectionQueryV2Command offerings =
                CourseSelectionQueryV2Command.availableOfferings("token-001", " ROUND-INITIAL ");
        CourseSelectionQueryV2Command selected =
                CourseSelectionQueryV2Command.selectedOfferings("token-001");

        assertEquals(CourseSelectionQueryV2Command.QueryType.AVAILABLE_OFFERINGS,
                offerings.getQueryType());
        assertEquals("ROUND-INITIAL", offerings.getRoundId());
        assertEquals(CourseSelectionQueryV2Command.QueryType.SELECTED_OFFERINGS,
                selected.getQueryType());
        assertNull(selected.getRoundId());
    }

    @Test
    void queriesRejectBlankCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> CourseSelectionQueryV2Command.availableRounds(""));
        assertThrows(IllegalArgumentException.class,
                () -> CourseSelectionQueryV2Command.availableOfferings("token-001", " "));
    }

    @Test
    void legacyQueryCommandKeepsOldCourseLevelShape() {
        CourseQueryCommand allCourses = CourseQueryCommand.allCourses();
        CourseQueryCommand selected = CourseQueryCommand.selectedCourses(" STU-001 ");

        assertEquals(CourseQueryCommand.QueryType.ALL_COURSES, allCourses.getQueryType());
        assertEquals(CourseQueryCommand.QueryType.SELECTED_COURSES, selected.getQueryType());
        assertEquals("STU-001", selected.getStudentId());
    }
}
