package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseQueryCommandTest {
    @Test
    void availableRoundsQueryStoresToken() {
        CourseQueryCommand command = CourseQueryCommand.availableRounds(" token-001 ");

        assertEquals(CourseQueryCommand.QueryType.AVAILABLE_ROUNDS, command.getQueryType());
        assertEquals("token-001", command.getToken());
        assertNull(command.getRoundId());
    }

    @Test
    void offeringAndSelectedQueriesDoNotCarryStudentId() {
        CourseQueryCommand offerings = CourseQueryCommand.availableOfferings("token-001",
                " ROUND-INITIAL ");
        CourseQueryCommand selected = CourseQueryCommand.selectedOfferings("token-001");

        assertEquals(CourseQueryCommand.QueryType.AVAILABLE_OFFERINGS, offerings.getQueryType());
        assertEquals("ROUND-INITIAL", offerings.getRoundId());
        assertEquals(CourseQueryCommand.QueryType.SELECTED_OFFERINGS, selected.getQueryType());
        assertNull(selected.getRoundId());
    }

    @Test
    void queriesRejectBlankCredentials() {
        assertThrows(IllegalArgumentException.class, () -> CourseQueryCommand.availableRounds(""));
        assertThrows(IllegalArgumentException.class,
                () -> CourseQueryCommand.availableOfferings("token-001", " "));
    }
}
