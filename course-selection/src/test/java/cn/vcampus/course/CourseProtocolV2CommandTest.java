package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ObjectStreamClass;
import org.junit.jupiter.api.Test;

class CourseProtocolV2CommandTest {
    @Test
    void newProtocolCommandsUseExplicitRoundOfferingAndRecordIds() {
        CourseRoundQueryCommand rounds = new CourseRoundQueryCommand("token-001", "2026-2027-1");
        CourseOfferingQueryCommand offerings = new CourseOfferingQueryCommand("token-001", "ROUND-INITIAL", "JAVA101");
        CourseOfferingSelectionCommand select = new CourseOfferingSelectionCommand(
                "token-001", "20230001", "ROUND-INITIAL", "OFFER-JAVA101-01");
        CourseSelectionRecordQueryCommand records = new CourseSelectionRecordQueryCommand(
                "token-001", "20230001", "2026-2027-1");
        CourseSelectionRecordDropCommand drop = new CourseSelectionRecordDropCommand(
                "token-001", "20230001", "SEL-001");

        assertEquals("2026-2027-1", rounds.getTerm());
        assertEquals("ROUND-INITIAL", offerings.getRoundId());
        assertEquals("JAVA101", offerings.getCourseId());
        assertEquals("OFFER-JAVA101-01", select.getOfferingId());
        assertEquals("ROUND-INITIAL", select.getRoundId());
        assertEquals("2026-2027-1", records.getTerm());
        assertEquals("SEL-001", drop.getRecordId());
    }

    @Test
    void v2CommandsDoNotReuseOldCourseSelectionCommandSerialVersion() {
        assertEquals(1L, ObjectStreamClass.lookup(CourseSelectionCommand.class).getSerialVersionUID());
        assertEquals(2L, ObjectStreamClass.lookup(CourseOfferingSelectionCommand.class).getSerialVersionUID());
        assertEquals(2L, ObjectStreamClass.lookup(CourseSelectionRecordDropCommand.class).getSerialVersionUID());
    }

    @Test
    void v2CommandsRejectBlankProtocolFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOfferingSelectionCommand("token-001", "20230001", " ", "OFFER-JAVA101-01"));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseSelectionRecordDropCommand("token-001", "20230001", ""));
    }
}
