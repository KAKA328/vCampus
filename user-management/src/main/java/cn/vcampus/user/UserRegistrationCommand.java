package cn.vcampus.user;

import java.io.Serializable;

/** Admin-scoped account creation command. */
public final class UserRegistrationCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final UserCredentials credentials;

    public UserRegistrationCommand(String token, UserCredentials credentials) {
        if (token == null || token.trim().isEmpty() || credentials == null) {
            throw new IllegalArgumentException("token and credentials are required");
        }
        this.token = token.trim();
        this.credentials = credentials;
    }

    public String getToken() {
        return token;
    }

    public UserCredentials getCredentials() {
        return credentials;
    }
}
