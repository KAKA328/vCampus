package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCourseSelectionServiceTest {

    @Test
    void listsCoursesInTheirConfiguredOrder() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();

        ServiceResult<List<Course>> result = service.listCourses();

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().size());
        assertEquals("JAVA101", result.getData().get(0).getCourseId());
        assertEquals("DB101", result.getData().get(1).getCourseId());
    }

    @Test
    void selectsCourseAndShowsItInStudentsSelectedCourses() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();

        ServiceResult<Void> selectResult = service.select("20230001", "JAVA101");
        ServiceResult<List<Course>> selectedResult = service.selectedCourses("20230001");

        assertEquals(StatusCode.OK, selectResult.getStatus());
        assertNull(selectResult.getData());
        assertEquals(StatusCode.OK, selectedResult.getStatus());
        assertEquals(1, selectedResult.getData().size());
        assertEquals("JAVA101", selectedResult.getData().get(0).getCourseId());
    }

    @Test
    void rejectsSelectionOfUnknownCourse() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();

        ServiceResult<Void> result = service.select("20230001", "UNKNOWN");

        assertEquals(StatusCode.NOT_FOUND, result.getStatus());
    }

    @Test
    void rejectsDuplicateSelection() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();
        service.select("20230001", "JAVA101");

        ServiceResult<Void> result = service.select("20230001", "JAVA101");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
    }

    @Test
    void rejectsSelectionWhenCourseIsFull() {
        InMemoryCourseSelectionService service = new InMemoryCourseSelectionService(
                Arrays.asList(new Course("LIMITED", "限额课程", 1, 1)));
        service.select("20230001", "LIMITED");

        ServiceResult<Void> result = service.select("20230002", "LIMITED");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
    }

    @Test
    void dropsSelectedCourseAndRemovesItFromStudentsCourses() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();
        service.select("20230001", "JAVA101");

        ServiceResult<Void> dropResult = service.drop("20230001", "JAVA101");
        ServiceResult<List<Course>> selectedResult = service.selectedCourses("20230001");

        assertEquals(StatusCode.OK, dropResult.getStatus());
        assertEquals(StatusCode.OK, selectedResult.getStatus());
        assertEquals(0, selectedResult.getData().size());
    }

    @Test
    void rejectsDropWhenStudentDidNotSelectCourse() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();

        ServiceResult<Void> result = service.drop("20230001", "JAVA101");

        assertEquals(StatusCode.NOT_FOUND, result.getStatus());
    }

    @Test
    void rejectsBlankStudentIdOrCourseId() {
        InMemoryCourseSelectionService service = serviceWithTwoCourses();

        assertEquals(StatusCode.BAD_REQUEST, service.select(" ", "JAVA101").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.select("20230001", " ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.selectedCourses(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.drop(" ", "JAVA101").getStatus());
    }

    private static InMemoryCourseSelectionService serviceWithTwoCourses() {
        return new InMemoryCourseSelectionService(Arrays.asList(
                new Course("JAVA101", "Java 程序设计", 3, 2),
                new Course("DB101", "数据库原理", 3, 2)));
    }
}
