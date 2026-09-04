package cn.vcampus.student;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe in-memory teacher archive repository for tests and demos. */
public final class InMemoryTeacherRepository implements TeacherRepository {
    private final Map<String, TeacherProfile> profiles =
            new LinkedHashMap<String, TeacherProfile>();

    @Override
    public synchronized TeacherProfile findById(String teacherId) {
        return profiles.get(requireText(teacherId, "teacherId"));
    }

    @Override
    public synchronized TeacherProfile findByUserId(String userId) {
        String normalized = requireText(userId, "userId");
        for (TeacherProfile profile : profiles.values()) {
            if (normalized.equals(profile.getUserId())) return profile;
        }
        return null;
    }

    @Override
    public synchronized TeacherProfile save(TeacherProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        TeacherProfile bound = profile.getUserId() == null
                ? null : findByUserId(profile.getUserId());
        if (bound != null && !profile.getTeacherId().equals(bound.getTeacherId())) {
            throw new IllegalStateException("userId is already bound to another teacher");
        }
        profiles.put(profile.getTeacherId(), profile);
        return profile;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
