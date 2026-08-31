package cn.vcampus.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory password reset application repository used by tests and demos. */
public final class InMemoryPasswordResetApplicationRepository implements PasswordResetApplicationRepository {
    private final Map<String, PasswordResetApplication> applications =
            new ConcurrentHashMap<String, PasswordResetApplication>();

    @Override public void save(PasswordResetApplication application) {
        applications.put(application.getUserId(), application);
    }

    @Override public PasswordResetApplication findPendingByUserId(String userId) {
        PasswordResetApplication application = applications.get(userId);
        return application != null && application.getStatus() == PasswordResetStatus.PENDING ? application : null;
    }

    @Override public List<PasswordResetApplication> findPending() {
        List<PasswordResetApplication> pending = new ArrayList<PasswordResetApplication>();
        for (PasswordResetApplication application : applications.values()) {
            if (application.getStatus() == PasswordResetStatus.PENDING) {
                pending.add(application);
            }
        }
        Collections.sort(pending, new Comparator<PasswordResetApplication>() {
            @Override public int compare(PasswordResetApplication left, PasswordResetApplication right) {
                return left.getSubmittedAt().compareTo(right.getSubmittedAt());
            }
        });
        return pending;
    }

    @Override public boolean review(String userId, PasswordResetStatus status, String reviewedBy, Instant reviewedAt) {
        PasswordResetApplication application = findPendingByUserId(userId);
        if (application == null) {
            return false;
        }
        applications.put(userId, application.reviewed(status, reviewedBy, reviewedAt));
        return true;
    }
}
