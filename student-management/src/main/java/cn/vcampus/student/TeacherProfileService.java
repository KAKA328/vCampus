package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;

/** Teacher archive contract consumed by course offering and grade workflows. */
public interface TeacherProfileService {
    ServiceResult<TeacherProfile> findById(String teacherId);
    ServiceResult<TeacherProfile> findByUserId(String userId);
    ServiceResult<TeacherProfile> save(TeacherProfile profile);
}
