package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DefaultCourseOfferingCapacityServiceTest {

    private static final LocalDateTime SELECTED_AT = LocalDateTime.of(2026, 9, 2, 9, 30);

    @Test
    void calculatesEachCapacityPoolAndCountsRetakeAsRequired() {
        CourseOffering offering = offering("OFFER-001", 3, 2, 1);
        CourseSelectionRecord required = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);
        CourseSelectionRecord retake = record("RECORD-002", "STUDENT-002", "OFFER-001",
                SelectionType.RETAKE);
        CourseSelectionRecord elective = record("RECORD-003", "STUDENT-003", "OFFER-001",
                SelectionType.ELECTIVE);
        CourseSelectionRecord crossMajor = record("RECORD-004", "STUDENT-004", "OFFER-001",
                SelectionType.CROSS_MAJOR);
        CourseSelectionRecord dropped = record("RECORD-005", "STUDENT-005", "OFFER-001",
                SelectionType.REQUIRED).withDroppedAt(SELECTED_AT.plusHours(1));
        CourseSelectionRecord otherOffering = record("RECORD-006", "STUDENT-006", "OFFER-002",
                SelectionType.REQUIRED);

        DefaultCourseOfferingCapacityService service = service(Arrays.asList(offering), Arrays.asList(
                required, retake, elective, crossMajor, dropped, otherOffering));

        ServiceResult<CourseOfferingCapacitySnapshot> result = service.snapshotFor("OFFER-001");
        CourseOfferingCapacitySnapshot snapshot = result.getData();

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, snapshot.getRequiredUsage().getUsedCapacity());
        assertEquals(1, snapshot.getRequiredUsage().getRemainingCapacity());
        assertEquals(1, snapshot.getElectiveUsage().getUsedCapacity());
        assertEquals(1, snapshot.getElectiveUsage().getRemainingCapacity());
        assertEquals(1, snapshot.getCrossMajorUsage().getUsedCapacity());
        assertEquals(0, snapshot.getCrossMajorUsage().getRemainingCapacity());
        assertTrue(snapshot.getCrossMajorUsage().isFull());
        assertFalse(snapshot.getRequiredUsage().isFull());
    }

    @Test
    void exposesOverCapacityWithoutReturningNegativeRemainingSlots() {
        CourseOffering offering = offering("OFFER-001", 1, 0, 0);
        CourseSelectionRecord first = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);
        CourseSelectionRecord second = record("RECORD-002", "STUDENT-002", "OFFER-001",
                SelectionType.RETAKE);
        DefaultCourseOfferingCapacityService service = service(Arrays.asList(offering),
                Arrays.asList(first, second));

        CapacityBucketUsage requiredUsage = service.snapshotFor("OFFER-001").getData()
                .getRequiredUsage();

        assertEquals(2, requiredUsage.getUsedCapacity());
        assertEquals(0, requiredUsage.getRemainingCapacity());
        assertTrue(requiredUsage.isOverCapacity());
    }

    @Test
    void returnsOfferingLookupErrors() {
        DefaultCourseOfferingCapacityService service = service(Arrays.<CourseOffering>asList(),
                Arrays.<CourseSelectionRecord>asList());

        assertEquals(StatusCode.BAD_REQUEST, service.snapshotFor(" ").getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.snapshotFor("UNKNOWN").getStatus());
    }

    private static DefaultCourseOfferingCapacityService service(
            java.util.List<CourseOffering> offerings, java.util.List<CourseSelectionRecord> records) {
        return new DefaultCourseOfferingCapacityService(
                new InMemoryCourseOfferingService(offerings),
                new InMemoryCourseSelectionRecordService(records));
    }

    private static CourseOffering offering(String offeringId, int requiredCapacity, int electiveCapacity,
            int crossMajorCapacity) {
        return new CourseOffering(offeringId, "CS101", "2026-2027-1", "TEACHER-001",
                "周一 1-2 节", "教学楼 A201", requiredCapacity, electiveCapacity,
                crossMajorCapacity, CourseOfferingStatus.OPEN);
    }

    private static CourseSelectionRecord record(String recordId, String studentId, String offeringId,
            SelectionType selectionType) {
        return new CourseSelectionRecord(recordId, studentId, offeringId, "ROUND-INITIAL",
                selectionType, SELECTED_AT);
    }
}
