package cn.vcampus.user;

import java.time.Instant;
import java.util.List;

/** Storage for password reset applications. */
public interface PasswordResetApplicationRepository {
    void save(PasswordResetApplication application);
    PasswordResetApplication findPendingByUserId(String userId);
    List<PasswordResetApplication> findPending();
    boolean review(String userId, PasswordResetStatus status, String reviewedBy, Instant reviewedAt);
}
