package cn.vcampus.user;

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

    @Override public boolean deactivateById(String userId) {
        UserAccount account = accounts.get(userId);
        if (account == null) {
            return false;
        }
        accounts.put(userId, account.deactivate());
        return true;
    }
}
