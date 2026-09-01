package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCourseCatalogServiceTest {

    @Test
    void createsUpdatesAndListsActiveCourses() {
        InMemoryCourseCatalogService service = new InMemoryCourseCatalogService();
        Course course = new Course("CS101", "程序设计基础", 3);

        ServiceResult<Course> createResult = service.create(course);
        ServiceResult<Course> updateResult = service.updateDetails("CS101", "Java 程序设计", 4);
        ServiceResult<List<Course>> activeResult = service.listActive();

        assertEquals(StatusCode.OK, createResult.getStatus());
        assertEquals(StatusCode.OK, updateResult.getStatus());
        assertEquals("Java 程序设计", updateResult.getData().getName());
        assertEquals(4, updateResult.getData().getCredits());
        assertEquals(1, activeResult.getData().size());
    }

    @Test
    void disabledCourseIsKeptForHistoryButCannotBeUsedAsActiveCourse() {
        InMemoryCourseCatalogService service = new InMemoryCourseCatalogService();
        service.create(new Course("CS101", "程序设计基础", 3));

        ServiceResult<Course> disableResult = service.changeStatus("CS101", CourseStatus.DISABLED);

        assertEquals(StatusCode.OK, disableResult.getStatus());
        assertEquals(CourseStatus.DISABLED, service.findById("CS101").getData().getStatus());
        assertEquals(StatusCode.CONFLICT, service.findActiveById("CS101").getStatus());
        assertEquals(0, service.listActive().getData().size());
    }

    @Test
    void rejectsInvalidDuplicateOrUnknownRequests() {
        InMemoryCourseCatalogService service = new InMemoryCourseCatalogService();
        service.create(new Course("CS101", "程序设计基础", 3));

        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.CONFLICT,
                service.create(new Course("CS101", "另一门课程", 2)).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.findById(" ").getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.updateDetails("UNKNOWN", "课程", 3).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.updateDetails("CS101", "", 3).getStatus());
    }
}
