package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCourseOfferingServiceTest {

    @Test
    void createsOfferingAndListsItByTerm() {
        InMemoryCourseOfferingService service = new InMemoryCourseOfferingService();
        CourseOffering offering = offering("OFFER-001", "CS101", CourseOfferingStatus.DRAFT,
                50, 20, 10);

        ServiceResult<CourseOffering> createResult = service.create(offering);
        ServiceResult<List<CourseOffering>> listResult = service.listByTerm("2026-2027-1");

        assertEquals(StatusCode.OK, createResult.getStatus());
        assertEquals(StatusCode.OK, listResult.getStatus());
        assertEquals(1, listResult.getData().size());
        assertEquals("OFFER-001", listResult.getData().get(0).getOfferingId());
    }

    @Test
    void rejectsDuplicateOfferingId() {
        CourseOffering offering = offering("OFFER-001", "CS101", CourseOfferingStatus.DRAFT,
                50, 20, 10);
        InMemoryCourseOfferingService service = new InMemoryCourseOfferingService(
                Arrays.asList(offering));

        assertEquals(StatusCode.CONFLICT, service.create(offering).getStatus());
    }

    @Test
    void listsOnlyOpenOfferingsForRequestedCourseAndTerm() {
        CourseOffering openOffering = offering("OFFER-001", "CS101", CourseOfferingStatus.OPEN,
                50, 20, 10);
        CourseOffering draftOffering = offering("OFFER-002", "CS101", CourseOfferingStatus.DRAFT,
                50, 0, 0);
        CourseOffering differentCourse = offering("OFFER-003", "CS102", CourseOfferingStatus.OPEN,
                40, 0, 0);
        InMemoryCourseOfferingService service = new InMemoryCourseOfferingService(
                Arrays.asList(openOffering, draftOffering, differentCourse));

        ServiceResult<List<CourseOffering>> result = service.listOpenByCourse("CS101", "2026-2027-1");

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("OFFER-001", result.getData().get(0).getOfferingId());
    }

    @Test
    void changesOfferingStatusAndCapacityPools() {
        CourseOffering offering = offering("OFFER-001", "CS101", CourseOfferingStatus.DRAFT,
                50, 20, 10);
        InMemoryCourseOfferingService service = new InMemoryCourseOfferingService(
                Arrays.asList(offering));

        ServiceResult<CourseOffering> statusResult = service.changeStatus("OFFER-001",
                CourseOfferingStatus.OPEN);
        ServiceResult<CourseOffering> capacityResult = service.changeCapacities("OFFER-001",
                60, 15, 5);
        ServiceResult<List<CourseOffering>> openResult = service.listOpenByCourse("CS101",
                "2026-2027-1");

        assertEquals(StatusCode.OK, statusResult.getStatus());
        assertEquals(CourseOfferingStatus.OPEN, statusResult.getData().getStatus());
        assertEquals(StatusCode.OK, capacityResult.getStatus());
        assertEquals(60, capacityResult.getData().getRequiredCapacity());
        assertEquals(15, capacityResult.getData().getElectiveCapacity());
        assertEquals(5, capacityResult.getData().getCrossMajorCapacity());
        assertEquals(1, openResult.getData().size());
    }

    @Test
    void rejectsInvalidOrUnknownManagementRequests() {
        InMemoryCourseOfferingService service = new InMemoryCourseOfferingService(Arrays.asList(
                offering("OFFER-001", "CS101", CourseOfferingStatus.DRAFT, 50, 20, 10)));

        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listByTerm(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listByCourse("CS101", " ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST,
                service.changeCapacities("OFFER-001", -1, 0, 0).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findById("UNKNOWN").getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.changeStatus("UNKNOWN", CourseOfferingStatus.OPEN).getStatus());
    }

    private static CourseOffering offering(String offeringId, String courseId,
            CourseOfferingStatus status, int requiredCapacity, int electiveCapacity,
            int crossMajorCapacity) {
        return new CourseOffering(offeringId, courseId, "2026-2027-1", "TEACHER-001",
                "周一 1-2 节", "教学楼 A201", requiredCapacity, electiveCapacity,
                crossMajorCapacity, status);
    }
}
