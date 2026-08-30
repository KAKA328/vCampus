package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCourseSelectionRecordServiceTest {

    private static final LocalDateTime SELECTED_AT = LocalDateTime.of(2026, 9, 2, 9, 30);

    @Test
    void createsRecordAndListsStudentsActiveSelections() {
        InMemoryCourseSelectionRecordService service = new InMemoryCourseSelectionRecordService();
        CourseSelectionRecord record = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);

        ServiceResult<CourseSelectionRecord> createResult = service.create(record);
        ServiceResult<List<CourseSelectionRecord>> listResult = service.listActiveByStudent(
                "STUDENT-001");

        assertEquals(StatusCode.OK, createResult.getStatus());
        assertEquals(StatusCode.OK, listResult.getStatus());
        assertEquals(1, listResult.getData().size());
        assertEquals("OFFER-001", listResult.getData().get(0).getOfferingId());
    }

    @Test
    void listsOnlyActiveStudentsForTeachingRoster() {
        CourseSelectionRecord active = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);
        CourseSelectionRecord dropped = record("RECORD-002", "STUDENT-002", "OFFER-001",
                SelectionType.RETAKE).withDroppedAt(SELECTED_AT.plusDays(1));
        InMemoryCourseSelectionRecordService service = new InMemoryCourseSelectionRecordService(
                Arrays.asList(active, dropped));

        ServiceResult<List<CourseSelectionRecord>> result = service.listActiveByOffering("OFFER-001");

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("STUDENT-001", result.getData().get(0).getStudentId());
        assertEquals(SelectionType.REQUIRED, result.getData().get(0).getSelectionType());
    }

    @Test
    void rejectsDuplicateActiveSelectionForSameOffering() {
        CourseSelectionRecord first = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);
        CourseSelectionRecord duplicate = record("RECORD-002", "STUDENT-001", "OFFER-001",
                SelectionType.RETAKE);
        InMemoryCourseSelectionRecordService service = new InMemoryCourseSelectionRecordService(
                Arrays.asList(first));

        assertEquals(StatusCode.CONFLICT, service.create(duplicate).getStatus());
    }

    @Test
    void marksRecordDroppedAndKeepsItInHistory() {
        CourseSelectionRecord record = record("RECORD-001", "STUDENT-001", "OFFER-001",
                SelectionType.REQUIRED);
        InMemoryCourseSelectionRecordService service = new InMemoryCourseSelectionRecordService(
                Arrays.asList(record));

        ServiceResult<CourseSelectionRecord> dropResult = service.markDropped("RECORD-001",
                SELECTED_AT.plusDays(1));
        ServiceResult<List<CourseSelectionRecord>> activeResult = service.listActiveByStudent(
                "STUDENT-001");
        ServiceResult<List<CourseSelectionRecord>> historyResult = service.listByStudent(
                "STUDENT-001");

        assertEquals(StatusCode.OK, dropResult.getStatus());
        assertEquals(SelectionRecordStatus.DROPPED, dropResult.getData().getStatus());
        assertEquals(0, activeResult.getData().size());
        assertEquals(1, historyResult.getData().size());
    }

    @Test
    void rejectsInvalidOrUnknownRecordRequests() {
        InMemoryCourseSelectionRecordService service = new InMemoryCourseSelectionRecordService();

        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listByStudent(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.markDropped("RECORD-001", null).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findById("UNKNOWN").getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.markDropped("UNKNOWN", SELECTED_AT.plusDays(1)).getStatus());
    }

    private static CourseSelectionRecord record(String recordId, String studentId, String offeringId,
            SelectionType selectionType) {
        return new CourseSelectionRecord(recordId, studentId, offeringId, "ROUND-INITIAL",
                selectionType, SELECTED_AT);
    }
}
