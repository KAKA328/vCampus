package cn.vcampus.user;

import cn.vcampus.common.Role;

/** Default profile binder for tests or deployments that have not enabled archive binding yet. */
public final class NoOpProfileBindingRepository implements ProfileBindingRepository {
    @Override public ProfileBindingResult validate(Role role, String profileId, String userId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return ProfileBindingResult.NOT_REQUIRED;
        }
        return ProfileBindingResult.OK;
    }

    @Override public ProfileBindingResult bind(Role role, String profileId, String userId) {
        return validate(role, profileId, userId);
    }

    @Override public String findProfileId(Role role, String userId) {
        return "";
    }
}
