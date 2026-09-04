package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;

/** Default policy used when no teaching-assignment data source is configured. */
final class DenyTeacherStudentAccessPolicy implements TeacherStudentAccessPolicy {
    @Override
    public ServiceResult<Boolean> canRead(String teacherUserId, String studentId) {
        return ServiceResult.ok(Boolean.FALSE);
    }
}
