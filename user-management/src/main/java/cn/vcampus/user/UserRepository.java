package cn.vcampus.user;

/** Repository abstraction for replacing in-memory users with Access storage. */
public interface UserRepository {
    boolean create(UserAccount account);
    UserAccount findById(String userId);
    boolean deactivateById(String userId);
}
