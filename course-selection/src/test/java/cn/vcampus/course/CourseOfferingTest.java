package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseOfferingTest {

    @Test
    void storesTeachingClassInformationAndCapacityPools() {
        CourseOffering offering = new CourseOffering("OFFER-2026-001", "CS101", "2026-2027-1",
                "TEACHER-001", "周一 1-2 节", "教学楼 A201", 50, 20, 10,
                CourseOfferingStatus.OPEN);

        assertEquals("OFFER-2026-001", offering.getOfferingId());
        assertEquals("CS101", offering.getCourseId());
        assertEquals("2026-2027-1", offering.getTerm());
        assertEquals("TEACHER-001", offering.getTeacherId());
        assertEquals("周一 1-2 节", offering.getSchedule());
        assertEquals("教学楼 A201", offering.getLocation());
        assertEquals(50, offering.getCapacity(CapacityBucket.REQUIRED));
        assertEquals(20, offering.getCapacity(CapacityBucket.ELECTIVE));
        assertEquals(10, offering.getCapacity(CapacityBucket.CROSS_MAJOR));
        assertEquals(80, offering.getTotalCapacity());
        assertEquals(CourseOfferingStatus.OPEN, offering.getStatus());
    }

    @Test
    void allowsZeroCapacityForUnusedCapacityPool() {
        CourseOffering offering = new CourseOffering("OFFER-2026-002", "CS102", "2026-2027-1",
                "TEACHER-002", "周二 3-4 节", "教学楼 B101", 60, 0, 0,
                CourseOfferingStatus.DRAFT);

        assertEquals(60, offering.getRequiredCapacity());
        assertEquals(0, offering.getElectiveCapacity());
        assertEquals(0, offering.getCrossMajorCapacity());
    }

    @Test
    void rejectsInvalidTeachingClassInformation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("", "CS101", "2026-2027-1", "TEACHER-001",
                        "周一 1-2 节", "教学楼 A201", 50, 20, 10, CourseOfferingStatus.OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("OFFER-2026-001", "CS101", "2026-2027-1", "TEACHER-001",
                        "周一 1-2 节", "教学楼 A201", -1, 20, 10, CourseOfferingStatus.OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("OFFER-2026-001", "CS101", "2026-2027-1", "TEACHER-001",
                        "周一 1-2 节", "教学楼 A201", 0, 0, 0, CourseOfferingStatus.OPEN));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("OFFER-2026-001", "CS101", "2026-2027-1", "TEACHER-001",
                        "周一 1-2 节", "教学楼 A201", 50, 20, 10, null));
    }
}
