package cn.vcampus.user;

import cn.vcampus.common.User;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creates and invalidates server-side login sessions. */
public final class SessionManager {
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final Map<String, String> activeTokensByUserId = new ConcurrentHashMap<String, String>();

    public synchronized Session create(User user) {
        return create(user, false);
    }

    public synchronized Session create(User user, boolean forcePasswordChange) {
        invalidateUser(user.getUserId());
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, user, forcePasswordChange);
        sessions.put(token, session);
        activeTokensByUserId.put(user.getUserId(), token);
        return session;
    }

    public Session find(String token) {
        return sessions.get(token);
    }

    public synchronized boolean invalidate(String token) {
        Session removed = sessions.remove(token);
        if (removed == null) {
            return false;
        }
        activeTokensByUserId.remove(removed.getUser().getUserId(), token);
        return true;
    }

    public synchronized void invalidateUser(String userId) {
        String token = activeTokensByUserId.remove(userId);
        if (token != null) {
            sessions.remove(token);
        }
    }
}
