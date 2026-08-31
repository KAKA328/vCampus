package cn.vcampus.user;

import cn.vcampus.common.Role;

/** Bridges user accounts to existing student/teacher archive rows without owning the archive modules. */
public interface ProfileBindingRepository {
    ProfileBindingResult validate(Role role, String profileId, String userId);
    ProfileBindingResult bind(Role role, String profileId, String userId);
    String findProfileId(Role role, String userId);
}
