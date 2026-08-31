package cn.vcampus.user;

import cn.vcampus.common.Role;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Demo/test profile binder for preloaded student and teacher archives. */
public final class InMemoryProfileBindingRepository implements ProfileBindingRepository {
    private final Map<String, String> studentUserByProfile = new ConcurrentHashMap<String, String>();
    private final Map<String, String> teacherUserByProfile = new ConcurrentHashMap<String, String>();

    public void addStudentProfile(String studentId) {
        studentUserByProfile.putIfAbsent(studentId, "");
    }

    public void addTeacherProfile(String teacherId) {
        teacherUserByProfile.putIfAbsent(teacherId, "");
    }

    @Override public ProfileBindingResult validate(Role role, String profileId, String userId) {
        if (role != Role.STUDENT && role != Role.TEACHER) {
            return ProfileBindingResult.NOT_REQUIRED;
        }
        if (profileId == null || profileId.trim().isEmpty()) {
            return ProfileBindingResult.PROFILE_NOT_FOUND;
        }
        Map<String, String> bindings = role == Role.STUDENT ? studentUserByProfile : teacherUserByProfile;
        String profileKey = profileId.trim();
        if (!bindings.containsKey(profileKey)) {
            return ProfileBindingResult.PROFILE_NOT_FOUND;
        }
        for (String boundUserId : bindings.values()) {
            if (userId.equals(boundUserId)) {
                return ProfileBindingResult.USER_ALREADY_BOUND;
            }
        }
        String existingUserId = bindings.get(profileKey);
        if (existingUserId != null && !existingUserId.isEmpty() && !userId.equals(existingUserId)) {
            return ProfileBindingResult.PROFILE_ALREADY_BOUND;
        }
        return ProfileBindingResult.OK;
    }

    @Override public ProfileBindingResult bind(Role role, String profileId, String userId) {
        ProfileBindingResult result = validate(role, profileId, userId);
        if (result == ProfileBindingResult.OK) {
            Map<String, String> bindings = role == Role.STUDENT ? studentUserByProfile : teacherUserByProfile;
            bindings.put(profileId.trim(), userId);
        }
        return result;
    }

    @Override public String findProfileId(Role role, String userId) {
        Map<String, String> bindings = role == Role.STUDENT ? studentUserByProfile
                : role == Role.TEACHER ? teacherUserByProfile : null;
        if (bindings == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            if (userId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }
}
