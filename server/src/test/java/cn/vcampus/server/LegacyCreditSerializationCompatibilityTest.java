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
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCreditSerializationCompatibilityTest {
    private static final String OLD_COURSE = "rO0ABXNyABhjbi52Y2FtcHVzLmNvdXJzZS5Db3Vyc2UAAAAAAAAAAQIABEkAB2NyZWRpdHNMAAhjb3Vyc2VJZHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wABG5hbWVxAH4AAUwABnN0YXR1c3QAIExjbi92Y2FtcHVzL2NvdXJzZS9Db3Vyc2VTdGF0dXM7eHAAAAADdAAGT0xEMTAxdAAKT2xkIENvdXJzZX5yAB5jbi52Y2FtcHVzLmNvdXJzZS5Db3Vyc2VTdGF0dXMAAAAAAAAAABIAAHhyAA5qYXZhLmxhbmcuRW51bQAAAAAAAAAAEgAAeHB0AAZBQ1RJVkU=";
    private static final String OLD_COMMAND = "rO0ABXNyACljbi52Y2FtcHVzLmNvdXJzZS5Db3Vyc2VNYW5hZ2VtZW50Q29tbWFuZAAAAAAAAAABAgATSQAHY3JlZGl0c0kAEmNyb3NzTWFqb3JDYXBhY2l0eUkAEGVsZWN0aXZlQ2FwYWNpdHlJABByZXF1aXJlZENhcGFjaXR5TAAGY291cnNldAAaTGNuL3ZjYW1wdXMvY291cnNlL0NvdXJzZTtMAAxjb3Vyc2VTdGF0dXN0ACBMY24vdmNhbXB1cy9jb3Vyc2UvQ291cnNlU3RhdHVzO0wABmVuZHNBdHQAGUxqYXZhL3RpbWUvTG9jYWxEYXRlVGltZTtMAAhsb2NhdGlvbnQAEkxqYXZhL2xhbmcvU3RyaW5nO0wABG5hbWVxAH4ABEwACG9mZmVyaW5ndAAiTGNuL3ZjYW1wdXMvY291cnNlL0NvdXJzZU9mZmVyaW5nO0wADm9mZmVyaW5nU3RhdHVzdAAoTGNuL3ZjYW1wdXMvY291cnNlL0NvdXJzZU9mZmVyaW5nU3RhdHVzO0wACW9wZXJhdGlvbnQANUxjbi92Y2FtcHVzL2NvdXJzZS9Db3Vyc2VNYW5hZ2VtZW50Q29tbWFuZCRPcGVyYXRpb247TAAOc2VsZWN0aW9uUm91bmR0ACJMY24vdmNhbXB1cy9jb3Vyc2UvU2VsZWN0aW9uUm91bmQ7TAAUc2VsZWN0aW9uUm91bmRTdGF0dXN0AChMY24vdmNhbXB1cy9jb3Vyc2UvU2VsZWN0aW9uUm91bmRTdGF0dXM7TAAIc3RhcnRzQXRxAH4AA0wACHRhcmdldElkcQB+AARMAAl0ZWFjaGVySWRxAH4ABEwABHRlcm1xAH4ABEwABXRva2VucQB+AAR4cAAAAAMAAAAAAAAAAAAAAABwcHBwdAAKT2xkIENvdXJzZXBwfnIAM2NuLnZjYW1wdXMuY291cnNlLkNvdXJzZU1hbmFnZW1lbnRDb21tYW5kJE9wZXJhdGlvbgAAAAAAAAAAEgAAeHIADmphdmEubGFuZy5FbnVtAAAAAAAAAAASAAB4cHQAFVVQREFURV9DT1VSU0VfREVUQUlMU3BwcHQABk9MRDEwMXBwdAAFdG9rZW4=";
    private static final String OLD_ADMIN = "rO0ABXNyACljbi52Y2FtcHVzLnN0dWRlbnQuQWNhZGVtaWNBZG1pbkNvbW1hbmRWMQAAAAAAAAABAgAHWgAab3RoZXJSZXF1aXJlbWVudHNDb25maXJtZWRJAA9yZXF1aXJlZENyZWRpdHNMAAZhY3Rpb250ADJMY24vdmNhbXB1cy9zdHVkZW50L0FjYWRlbWljQWRtaW5Db21tYW5kVjEkQWN0aW9uO0wADGFzc2Vzc21lbnRJZHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wABG5vdGVxAH4AAkwACXN0dWRlbnRJZHEAfgACTAAFdG9rZW5xAH4AAnhwAAAAAAB+cgAwY24udmNhbXB1cy5zdHVkZW50LkFjYWRlbWljQWRtaW5Db21tYW5kVjEkQWN0aW9uAAAAAAAAAAASAAB4cgAOamF2YS5sYW5nLkVudW0AAAAAAAAAABIAAHhwdAAHSElTVE9SWXB0AAB0AAJTMXQABXRva2Vu";
    private static final String OLD_SUMMARY = "rO0ABXNyACBjbi52Y2FtcHVzLnN0dWRlbnQuQ3JlZGl0U3VtbWFyeQAAAAAAAAABAgAFSQANZWFybmVkQ3JlZGl0c0kAEWhpc3RvcmljYWxSZXRha2VzSQANcGFzc2VkQ291cnNlc0kADnBlbmRpbmdSZXRha2VzTAAJc3R1ZGVudElkdAASTGphdmEvbGFuZy9TdHJpbmc7eHAAAAAIAAAAAAAAAAMAAAAAdAACUzE=";
    private static final String OLD_ASSESSMENT = "rO0ABXNyACVjbi52Y2FtcHVzLnN0dWRlbnQuQWNhZGVtaWNBc3Nlc3NtZW50AAAAAAAAAAECAApJAA9yZXF1aXJlZENyZWRpdHNMAAViYXNpc3QAEkxqYXZhL2xhbmcvU3RyaW5nO0wAB2NyZWRpdHN0ACJMY24vdmNhbXB1cy9zdHVkZW50L0NyZWRpdFN1bW1hcnk7TAAIZXZpZGVuY2VxAH4AAUwAC2dyYWR1YXRlZEF0dAATTGphdmEvdGltZS9JbnN0YW50O0wAC2dyYWR1YXRlZEJ5cQB+AAFMAA5ncmFkdWF0aW9uTm90ZXEAfgABTAACaWRxAH4AAUwACnJldmlld2VkQXRxAH4AA0wACnJldmlld2VkQnlxAH4AAXhwAAAAC3QABWJhc2lzc3IAIGNuLnZjYW1wdXMuc3R1ZGVudC5DcmVkaXRTdW1tYXJ5AAAAAAAAAAECAAVJAA1lYXJuZWRDcmVkaXRzSQARaGlzdG9yaWNhbFJldGFrZXNJAA1wYXNzZWRDb3Vyc2VzSQAOcGVuZGluZ1JldGFrZXNMAAlzdHVkZW50SWRxAH4AAXhwAAAACAAAAAAAAAADAAAAAHQAAlMxdAAEaGFzaHBwcHQAAkExc3IADWphdmEudGltZS5TZXKVXYS6GyJIsgwAAHhwdw0CAAAAAAAAAAEAAAAAeHQABWFkbWlu";
    private static final String OLD_REVIEW = "rO0ABXNyACFjbi52Y2FtcHVzLnN0dWRlbnQuQWNhZGVtaWNSZXZpZXcAAAAAAAAAAQIAC0kAEWZhaWxlZENvdXJzZUNvdW50WgAPZ3JhZHVhdGlvblJlYWR5SQARcGFzc2VkQ291cnNlQ291bnRJABVyZXF1aXJlZEVhcm5lZENyZWRpdHNJABFyZXRha2VDb3Vyc2VDb3VudEkAEnRvdGFsRWFybmVkQ3JlZGl0c0wABnJlbWFya3QAEkxqYXZhL2xhbmcvU3RyaW5nO0wACHJldmlld0lkcQB+AAFMAApyZXZpZXdlZEF0dAATTGphdmEvdGltZS9JbnN0YW50O0wACnJldmlld2VkQnlxAH4AAUwACXN0dWRlbnRJZHEAfgABeHAAAAAAAAAAAAMAAAALAAAAAAAAAAh0AAZyZW1hcmt0AAJSMXNyAA1qYXZhLnRpbWUuU2VylV2EuhsiSLIMAAB4cHcNAgAAAAAAAAABAAAAAHh0AAVhZG1pbnQAAlMx";
    private static final String OLD_HISTORY = "rO0ABXNyACZjbi52Y2FtcHVzLnN0dWRlbnQuQ291cnNlSGlzdG9yeVJlY29yZAAAAAAAAAABAgAJSQAJYXR0ZW1wdE5vSQANZWFybmVkQ3JlZGl0c1oABnBhc3NlZEkABXNjb3JlTAALYXR0ZW1wdFR5cGV0ABJMamF2YS9sYW5nL1N0cmluZztMAAhjb3Vyc2VJZHEAfgABTAAKY291cnNlTmFtZXEAfgABTAAIc2VtZXN0ZXJxAH4AAUwACXN0dWRlbnRJZHEAfgABeHAAAAABAAAAAwEAAABYdAAJ5qOj5qCm5oWodAAGT0xEMTAxdAAKT2xkIENvdXJzZXQACzIwMjUtMjAyNi0ydAACUzE=";
    private static final String OLD_FORMAL = "rO0ABXNyACVjbi52Y2FtcHVzLnN0dWRlbnQuRm9ybWFsQ291cnNlUmVzdWx0AAAAAAAAAAECAAtJAAlhdHRlbXB0Tm9JAA1lYXJuZWRDcmVkaXRzWgAGcGFzc2VkSQAFc2NvcmVMAAthdHRlbXB0VHlwZXQAEkxqYXZhL2xhbmcvU3RyaW5nO0wACGNvdXJzZUlkcQB+AAFMAApvZmZlcmluZ0lkcQB+AAFMAApyZWNvcmRlZEF0dAAZTGphdmEvdGltZS9Mb2NhbERhdGVUaW1lO0wACHJlc3VsdElkcQB+AAFMAAhzZW1lc3RlcnEAfgABTAAJc3R1ZGVudElkcQB+AAF4cAAAAAEAAAADAQAAAFh0AAnmo6PmoKbmhah0AAZPTEQxMDFwc3IADWphdmEudGltZS5TZXKVXYS6GyJIsgwAAHhwdwkFAAAH6gECA/t4dAACRjF0AAsyMDI1LTIwMjYtMnQAAlMx";

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
        assertEquals(12, assessment.getRequiredCredits());
        assertEquals(new BigDecimal("11.5"), assessment.getRequiredCreditsDecimal());
        assertEquals(new BigDecimal("3.0"), assessment.getShortfallDecimal());
        assertEquals(3, assessment.getShortfall());

        AcademicReview review = roundTrip(new AcademicReview("REV", "S1", new BigDecimal("8.5"),
                new BigDecimal("11.5"), 3, 0, 0, false, "admin", Instant.EPOCH, ""));
        assertEquals(8, review.getTotalEarnedCredits());
        assertEquals(12, review.getRequiredEarnedCredits());
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

    @Test void oldVersionByteStreamsDeserializeWithDecimalFallbacks() throws Exception {
        Course course = (Course) readOldFixture(OLD_COURSE);
        assertEquals(new BigDecimal("3"), course.getCreditsDecimal());

        CourseManagementCommand command = (CourseManagementCommand) readOldFixture(OLD_COMMAND);
        assertEquals(new BigDecimal("3"), command.getCreditsDecimal());
        assertFalse(command.hasPreciseCredits());

        AcademicAdminCommandV1 admin = (AcademicAdminCommandV1) readOldFixture(OLD_ADMIN);
        assertEquals(0, admin.getRequiredCredits());

        CreditSummary summary = (CreditSummary) readOldFixture(OLD_SUMMARY);
        assertEquals(new BigDecimal("8"), summary.getEarnedCreditsDecimal());

        AcademicAssessment assessment = (AcademicAssessment) readOldFixture(OLD_ASSESSMENT);
        assertEquals(new BigDecimal("11"), assessment.getRequiredCreditsDecimal());

        AcademicReview review = (AcademicReview) readOldFixture(OLD_REVIEW);
        assertEquals(new BigDecimal("8"), review.getTotalEarnedCreditsDecimal());
        assertEquals(new BigDecimal("11"), review.getRequiredEarnedCreditsDecimal());

        CourseHistoryRecord history = (CourseHistoryRecord) readOldFixture(OLD_HISTORY);
        assertEquals(new BigDecimal("3"), history.getEarnedCreditsDecimal());

        FormalCourseResult formal = (FormalCourseResult) readOldFixture(OLD_FORMAL);
        assertEquals(new BigDecimal("3"), formal.getEarnedCreditsDecimal());
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

    private static Object readOldFixture(String base64) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return input.readObject();
        }
    }
}
