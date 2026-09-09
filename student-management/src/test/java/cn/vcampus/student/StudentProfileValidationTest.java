package cn.vcampus.student;

import java.time.Year;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudentProfileValidationTest {
    @Test public void phoneAcceptsOnlyElevenAsciiDigitsOrUnconfigured() {
        StudentProfileValidation.contacts(null, null);
        StudentProfileValidation.contacts("", "");
        StudentProfileValidation.contacts("13800000001", "student@example.test");
        for (String phone : new String[] {"123", "1380000000", "138000000012", "1380000000a",
                "+8613800000001", "138 00000001", "１３８０００００００１", "13800000001\n"}) {
            assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.contacts(phone, null));
        }
    }
    @Test public void validatesEmailWithoutRequiringIt() {
        StudentProfileValidation.contacts(null, "a.b+tag@example.edu.cn");
        for (String mail : new String[] {"bad", "a@", "@example.com", "a b@example.com",
                "a@@example.com", "a@-example.com", "a..b@example.com", ".a@example.com",
                "a@example.com\n", repeat("a", 101)}) {
            assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.contacts(null, mail));
        }
    }
    @Test public void fixedStatusGenderYearAndLengthsAreEnforced() {
        for (String state : new String[] {"在读", "休学", "退学", "毕业"}) {
            StudentProfileValidation.profile(record("学生", "未知", 2026, state));
        }
        for (String state : new String[] {"", "随便填写", "ENROLLED", " 在读 "}) {
            assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record("学生", "男", 2026, state)));
        }
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record(" ", "男", 2026, "在读")));
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record("学生", "随意", 2026, "在读")));
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record("学生", "男", 0, "在读")));
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record("学生", "男", Year.now().getValue() + 2, "在读")));
        StudentProfileValidation.profile(record(repeat("字", 64), null, 1900, "在读"));
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record(repeat("字", 65), null, 2026, "在读")));
        assertThrows(IllegalArgumentException.class, () -> StudentProfileValidation.profile(record("学生\n姓名", null, 2026, "在读")));
    }
    private static StudentRecord record(String name, String gender, int year, String state) {
        return new StudentRecord("STU-001", "demo_student", name, gender, null, null, null, year, state, null, null);
    }
    private static String repeat(String text, int count) { return String.join("", java.util.Collections.nCopies(count, text)); }
}
