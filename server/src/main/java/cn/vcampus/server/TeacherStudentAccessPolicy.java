package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;

/** Determines whether a teacher may read a student through an assigned offering. */
interface TeacherStudentAccessPolicy {
    ServiceResult<Boolean> canRead(String teacherUserId, String studentId);
}
