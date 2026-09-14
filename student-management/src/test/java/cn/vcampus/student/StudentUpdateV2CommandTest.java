package cn.vcampus.student;

import org.junit.Test;
import static org.junit.Assert.*;

public class StudentUpdateV2CommandTest {
    @Test public void rejectsMissingOrMismatchedSnapshots() {
        StudentRecord original = new StudentRecord("S001", "student", "Name", "男", null, null, null,
                2024, "在读", null, null);
        StudentRecord other = new StudentRecord("S002", "student", "Name", "男", null, null, null,
                2024, "在读", null, null);
        assertThrows(IllegalArgumentException.class, () -> new StudentUpdateV2Command("token", original, null));
        assertThrows(IllegalArgumentException.class, () -> new StudentUpdateV2Command("token", original, other));
        assertThrows(IllegalArgumentException.class, () -> new StudentUpdateV2Command(" ", original, original));
        assertThrows(IllegalArgumentException.class, () -> new StudentUpdateV2Command("token", null, original));
        assertSame(original, new StudentUpdateV2Command("token", original, original).getExpected());
    }
}
