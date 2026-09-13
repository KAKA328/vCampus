package cn.vcampus.user;

import cn.vcampus.common.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creates and invalidates server-side login sessions. */
public final class SessionManager {
    private static final Duration DEFAULT_TTL = Duration.ofHours(8);
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final Map<String, String> activeTokensByUserId = new ConcurrentHashMap<String, String>();
    private final Duration ttl;

    public SessionManager() {
        this(DEFAULT_TTL);
    }

    public SessionManager(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("session ttl must be positive");
        }
        this.ttl = ttl;
    }

    public synchronized Session create(User user) {
        return create(user, false);
    }

    public synchronized Session create(User user, boolean forcePasswordChange) {
        invalidateUser(user.getUserId());
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, user, forcePasswordChange, Instant.now());
        sessions.put(token, session);
        activeTokensByUserId.put(user.getUserId(), token);
        return session;
    }

    public synchronized Session find(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;
        if (!Instant.now().isBefore(session.getCreatedAt().plus(ttl))) {
            invalidate(token);
            return null;
        }
        return session;
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
