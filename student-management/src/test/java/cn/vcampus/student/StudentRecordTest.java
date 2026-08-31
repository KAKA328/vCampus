package cn.vcampus.student;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudentRecordTest {
    @Test
    public void studentRecordCarriesBasicProfileFields() {
        StudentRecord record = new StudentRecord(
                "S001",
                "何锦恒",
                "男",
                "计算机学院",
                "软件工程",
                "09024429",
                2025,
                "在读",
                "13800000000",
                "student@example.com"
        );

        assertEquals("S001", record.getStudentId());
        assertEquals(null, record.getUserId());
        assertEquals("何锦恒", record.getName());
        assertEquals("男", record.getGender());
        assertEquals("计算机学院", record.getDepartmentName());
        assertEquals("软件工程", record.getMajorName());
        assertEquals("09024429", record.getClassId());
        assertEquals(2025, record.getEnrollmentYear());
        assertEquals("在读", record.getStatus());
        assertEquals("13800000000", record.getPhone());
        assertEquals("student@example.com", record.getEmail());
    }
}
