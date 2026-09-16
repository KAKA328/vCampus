package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

class CourseTest {

    @Test
    void storesCourseInformation() {
        Course course = new Course("CS101", "Java 程序设计", 3);

        assertEquals("CS101", course.getCourseId());
        assertEquals("Java 程序设计", course.getName());
        assertEquals(new BigDecimal("3"), course.getCredits());
    }

    @Test
    void supportsFractionalCreditsWithTwoDecimalPlaces() {
        assertEquals(new BigDecimal("1.5"), new Course("GE102", "大学美育", new BigDecimal("1.50"))
                .getCredits());
    }

    @Test
    void courseManagementRequestCarriesFractionalCredits() {
        CourseManagementCommand command = CourseManagementCommand.updateCourseDetails(
                "token", "GE102", "大学美育", new BigDecimal("2.50"));

        assertEquals(new BigDecimal("2.5"), command.getCredits());
    }

    @Test
    void rejectsInvalidCourseInformation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Course("", "Java 程序设计", 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Course("CS101", "Java 程序设计", 0));
    }
}
