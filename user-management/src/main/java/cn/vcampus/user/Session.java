package cn.vcampus.user;

import cn.vcampus.common.User;
import java.io.Serializable;

/** Authenticated session returned after login. */
public final class Session implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token; private final User user; private final boolean forcePasswordChange;

    public Session(String token, User user) {
        this(token, user, false);
    }

    public Session(String token, User user, boolean forcePasswordChange) {
        this.token = token;
        this.user = user;
        this.forcePasswordChange = forcePasswordChange;
    }

    public String getToken() { return token; } public User getUser() { return user; }
    public boolean isForcePasswordChange() { return forcePasswordChange; }
}
