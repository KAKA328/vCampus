package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseTest {

    @Test
    void storesCourseInformation() {
        Course course = new Course("CS101", "Java 程序设计", 3, 60);

        assertEquals("CS101", course.getCourseId());
        assertEquals("Java 程序设计", course.getName());
        assertEquals(3, course.getCredits());
        assertEquals(60, course.getCapacity());
    }

    @Test
    void rejectsInvalidCourseInformation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Course("", "Java 程序设计", 3, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new Course("CS101", "Java 程序设计", 0, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new Course("CS101", "Java 程序设计", 3, 0));
    }
}
