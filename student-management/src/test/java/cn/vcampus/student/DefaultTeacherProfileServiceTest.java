package cn.vcampus.student;

import cn.vcampus.common.StatusCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class DefaultTeacherProfileServiceTest {
    @Test
    public void savesAndFindsTeacherByIdAndUserId() {
        TeacherProfileService service = new DefaultTeacherProfileService(
                new InMemoryTeacherRepository());
        TeacherProfile profile = new TeacherProfile(" T001 ", " teacher001 ", " 张老师 ",
                "计算机学院", "讲师", true);

        assertEquals(StatusCode.OK, service.save(profile).getStatus());
        assertEquals("张老师", service.findById("T001").getData().getTeacherName());
        assertEquals("T001", service.findByUserId("teacher001").getData().getTeacherId());
    }

    @Test
    public void reportsMissingInvalidAndInactiveTeacher() {
        TeacherProfileService service = new DefaultTeacherProfileService(
                new InMemoryTeacherRepository());
        TeacherProfile inactive = new TeacherProfile("T002", null, "离职教师", null, null, false);
        service.save(inactive);

        assertEquals(StatusCode.NOT_FOUND, service.findById("MISSING").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.findById(" ").getStatus());
        assertFalse(service.findById("T002").getData().isActive());
        assertNull(service.findById("T002").getData().getUserId());
    }

    @Test
    public void rejectsBindingOneUserToTwoTeachers() {
        TeacherProfileService service = new DefaultTeacherProfileService(
                new InMemoryTeacherRepository());
        service.save(new TeacherProfile("T001", "teacher001", "教师甲", null, null, true));

        assertEquals(StatusCode.CONFLICT, service.save(new TeacherProfile(
                "T002", "teacher001", "教师乙", null, null, true)).getStatus());
    }
}
