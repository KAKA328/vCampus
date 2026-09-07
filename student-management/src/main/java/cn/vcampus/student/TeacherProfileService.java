package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;

/** Teacher archive contract consumed by course offering and grade workflows. */
public interface TeacherProfileService {
    default ServiceResult<java.util.List<TeacherProfile>> findAll() {
        return ServiceResult.failure(cn.vcampus.common.StatusCode.SERVER_ERROR, "teacher directory unavailable");
    }
    ServiceResult<TeacherProfile> findById(String teacherId);
    ServiceResult<TeacherProfile> findByUserId(String userId);
    ServiceResult<TeacherProfile> save(TeacherProfile profile);
}
