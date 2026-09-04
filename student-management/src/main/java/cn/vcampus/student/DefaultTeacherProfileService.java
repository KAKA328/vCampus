package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

/** Repository-backed teacher profile service. */
public final class DefaultTeacherProfileService implements TeacherProfileService {
    private final TeacherRepository teachers;

    public DefaultTeacherProfileService(TeacherRepository teachers) {
        if (teachers == null) throw new IllegalArgumentException("teachers must not be null");
        this.teachers = teachers;
    }

    @Override
    public ServiceResult<TeacherProfile> findById(String teacherId) {
        try {
            TeacherProfile profile = teachers.findById(teacherId);
            return profile == null
                    ? ServiceResult.<TeacherProfile>failure(StatusCode.NOT_FOUND,
                            "teacher profile not found")
                    : ServiceResult.ok(profile);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find teacher profile");
        }
    }

    @Override
    public ServiceResult<TeacherProfile> findByUserId(String userId) {
        try {
            TeacherProfile profile = teachers.findByUserId(userId);
            return profile == null
                    ? ServiceResult.<TeacherProfile>failure(StatusCode.NOT_FOUND,
                            "teacher profile not bound")
                    : ServiceResult.ok(profile);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find teacher profile");
        }
    }

    @Override
    public ServiceResult<TeacherProfile> save(TeacherProfile profile) {
        try {
            return ServiceResult.ok(teachers.save(profile));
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            if ("userId is already bound to another teacher".equals(failure.getMessage())) {
                return ServiceResult.failure(StatusCode.CONFLICT, failure.getMessage());
            }
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to save teacher profile");
        }
    }
}
