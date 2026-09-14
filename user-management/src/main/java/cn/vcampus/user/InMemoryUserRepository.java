package cn.vcampus.user;

import cn.vcampus.common.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory repository used by tests and early integration demos. */
public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserAccount> accounts = new ConcurrentHashMap<String, UserAccount>();

    @Override public boolean create(UserAccount account) {
        return accounts.putIfAbsent(account.getUser().getUserId(), account) == null;
    }

    @Override public UserAccount findById(String userId) {
        return accounts.get(userId);
    }

    @Override public boolean deleteById(String userId) {
        return accounts.remove(userId) != null;
    }

    @Override public boolean deactivateById(String userId) {
        return setActive(userId, false);
    }

    @Override public synchronized AccountDeactivationResult deactivateByIdIfNotLastActiveAdministrator(
            String userId) {
        UserAccount account = accounts.get(userId);
        if (account == null) {
            return AccountDeactivationResult.NOT_FOUND;
        }
        if (account.isActive() && account.getUser().getRole() == Role.ADMIN
                && activeAdministratorCount() <= 1) {
            return AccountDeactivationResult.LAST_ACTIVE_ADMINISTRATOR;
        }
        accounts.put(userId, account.withActive(false));
        return AccountDeactivationResult.SUCCESS;
    }

    @Override public boolean setActive(String userId, boolean active) {
        UserAccount account = accounts.get(userId);
        if (account == null) {
            return false;
        }
        accounts.put(userId, account.withActive(active));
        return true;
    }

    @Override public boolean updatePasswordHash(String userId, String passwordHash) {
        return updatePasswordHash(userId, passwordHash, false);
    }

    @Override public boolean updatePasswordHash(String userId, String passwordHash, boolean forcePasswordChange) {
        UserAccount account = accounts.get(userId);
        if (account == null) {
            return false;
        }
        accounts.put(userId, account.withPasswordHash(passwordHash, forcePasswordChange));
        return true;
    }

    @Override public boolean changeRole(String userId, cn.vcampus.common.Role role) {
        UserAccount account = accounts.get(userId);
        if (account == null) {
            return false;
        }
        accounts.put(userId, account.withRole(role));
        return true;
    }

    @Override public List<UserAccount> findAll() {
        return new ArrayList<UserAccount>(accounts.values());
    }

    private int activeAdministratorCount() {
        int count = 0;
        for (UserAccount account : accounts.values()) {
            if (account.isActive() && account.getUser().getRole() == Role.ADMIN) count++;
        }
        return count;
    }
}
