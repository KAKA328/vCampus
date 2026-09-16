package cn.vcampus.server;

import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.student.AcademicAdminCommandV1;
import cn.vcampus.student.AcademicAssessment;
import cn.vcampus.student.AcademicReview;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.CreditSummary;
import cn.vcampus.student.FormalCourseResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCreditSerializationCompatibilityTest {
    @Test void legacyFieldNamesTypesAndSerialUidsRemainStable() throws Exception {
        assertLegacyField(Course.class, "credits");
        assertLegacyField(CourseManagementCommand.class, "credits");
        assertLegacyField(AcademicAdminCommandV1.class, "requiredCredits");
        assertLegacyField(CreditSummary.class, "earnedCredits");
        assertLegacyField(AcademicAssessment.class, "requiredCredits");
        assertLegacyField(AcademicReview.class, "totalEarnedCredits");
        assertLegacyField(AcademicReview.class, "requiredEarnedCredits");
        assertLegacyField(CourseHistoryRecord.class, "earnedCredits");
        assertLegacyField(FormalCourseResult.class, "earnedCredits");

        for (Class<?> type : new Class<?>[] {Course.class, CourseManagementCommand.class,
                AcademicAdminCommandV1.class, CreditSummary.class, AcademicAssessment.class,
                AcademicReview.class, CourseHistoryRecord.class, FormalCourseResult.class}) {
            assertEquals(1L, ObjectStreamClass.lookup(type).getSerialVersionUID(), type.getName());
        }
    }

    @Test void fractionalValuesRoundTripWhileLegacyIntegerAccessorsStayAvailable() throws Exception {
        Course course = roundTrip(new Course("WEB101", "Web", new BigDecimal("2.5")));
        assertEquals(2, course.getCredits());
        assertEquals(new BigDecimal("2.5"), course.getCreditsDecimal());

        CourseManagementCommand command = roundTrip(CourseManagementCommand.updateCourseDetails(
                "token", "WEB101", "Web", new BigDecimal("2.5")));
        assertEquals(2, command.getCredits());
        assertEquals(new BigDecimal("2.5"), command.getCreditsDecimal());

        CreditSummary summary = roundTrip(new CreditSummary("S1", new BigDecimal("8.5"),
                3, 0, 0));
        assertEquals(8, summary.getEarnedCredits());
        assertEquals(new BigDecimal("8.5"), summary.getEarnedCreditsDecimal());

        CourseHistoryRecord history = roundTrip(new CourseHistoryRecord("S1", "WEB101", "Web",
                "2026-2027-1", 1, "首修", 88, true, new BigDecimal("2.5")));
        assertEquals(2, history.getEarnedCredits());
        assertEquals(new BigDecimal("2.5"), history.getEarnedCreditsDecimal());

        FormalCourseResult result = roundTrip(new FormalCourseResult("R1", "S1", "WEB101", null,
                "2026-2027-1", 1, "首修", 88, true, new BigDecimal("2.5"),
                LocalDateTime.of(2026, 9, 16, 12, 0)));
        assertEquals(2, result.getEarnedCredits());
        assertEquals(new BigDecimal("2.5"), result.getEarnedCreditsDecimal());

        AcademicAssessment assessment = roundTrip(new AcademicAssessment("A1", summary,
                new BigDecimal("11.5"), "hash", "admin", Instant.EPOCH, "", null, null, null));
        assertEquals(11, assessment.getRequiredCredits());
        assertEquals(new BigDecimal("11.5"), assessment.getRequiredCreditsDecimal());
        assertEquals(new BigDecimal("3.0"), assessment.getShortfallDecimal());

        AcademicReview review = roundTrip(new AcademicReview("REV", "S1", new BigDecimal("8.5"),
                new BigDecimal("11.5"), 3, 0, 0, false, "admin", Instant.EPOCH, ""));
        assertEquals(8, review.getTotalEarnedCredits());
        assertEquals(new BigDecimal("8.5"), review.getTotalEarnedCreditsDecimal());
        assertEquals(new BigDecimal("11.5"), review.getRequiredEarnedCreditsDecimal());
    }

    @Test void missingPreciseFieldFallsBackToLegacyIntegerValue() throws Exception {
        Course course = new Course("JAVA101", "Java", 3);
        Field precise = Course.class.getDeclaredField("creditsDecimal");
        precise.setAccessible(true);
        precise.set(course, null);

        Course restored = roundTrip(course);
        assertEquals(3, restored.getCredits());
        assertEquals(new BigDecimal("3"), restored.getCreditsDecimal());
    }

    private static void assertLegacyField(Class<?> type, String name) throws Exception {
        assertEquals(int.class, type.getDeclaredField(name).getType(), type.getName() + "." + name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
