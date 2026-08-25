package cn.vcampus.user;

import cn.vcampus.common.User;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creates and invalidates server-side login sessions. */
public final class SessionManager {
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    public Session create(User user) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, user);
        sessions.put(token, session);
        return session;
    }

    public Session find(String token) {
        return sessions.get(token);
    }

    public boolean invalidate(String token) {
        return sessions.remove(token) != null;
    }

    public void invalidateUser(String userId) {
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().getUser().getUserId().equals(userId)) {
                sessions.remove(entry.getKey());
            }
        }
    }
}
