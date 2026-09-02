package cn.vcampus.user;

import java.util.List;

/** Repository abstraction for replacing in-memory users with Access storage. */
public interface UserRepository {
    boolean create(UserAccount account);
    UserAccount findById(String userId);
    boolean deactivateById(String userId);
    boolean setActive(String userId, boolean active);
    boolean updatePasswordHash(String userId, String passwordHash);
    boolean updatePasswordHash(String userId, String passwordHash, boolean forcePasswordChange);
    boolean changeRole(String userId, cn.vcampus.common.Role role);
    List<UserAccount> findAll();
}
