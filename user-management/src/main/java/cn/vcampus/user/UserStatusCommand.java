package cn.vcampus.user;

import java.io.Serializable;

/** Administrator request to enable or disable an account. */
public final class UserStatusCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String userId;
    private final boolean active;

    public UserStatusCommand(String token, String userId, boolean active) {
        this.token = token;
        this.userId = userId;
        this.active = active;
    }

    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public boolean isActive() { return active; }
}
