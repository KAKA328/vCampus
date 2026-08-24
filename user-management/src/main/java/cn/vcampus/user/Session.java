package cn.vcampus.user;

import cn.vcampus.common.User;
import java.io.Serializable;

/** Authenticated session returned after login. */
public final class Session implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token; private final User user;
    public Session(String token, User user) { this.token = token; this.user = user; }
    public String getToken() { return token; } public User getUser() { return user; }
}
