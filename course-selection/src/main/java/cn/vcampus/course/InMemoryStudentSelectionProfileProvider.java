package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用于联调前演示的内存学生选课资料提供者。 */
public final class InMemoryStudentSelectionProfileProvider
        implements StudentSelectionProfileProvider {
    private final Map<String, StudentSelectionProfile> profilesByUserId;

    public InMemoryStudentSelectionProfileProvider(List<StudentSelectionProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException("profiles must not be null");
        }
        this.profilesByUserId = new LinkedHashMap<String, StudentSelectionProfile>();
        for (StudentSelectionProfile profile : profiles) {
            if (profile == null || profilesByUserId.put(profile.getUserId(), profile) != null) {
                throw new IllegalArgumentException("profiles must have unique non-null userId");
            }
        }
    }

    @Override
    public ServiceResult<StudentSelectionProfile> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        StudentSelectionProfile profile = profilesByUserId.get(userId.trim());
        return profile == null
                ? ServiceResult.<StudentSelectionProfile>failure(StatusCode.NOT_FOUND,
                        "student selection profile not found")
                : ServiceResult.ok(profile);
    }
}
