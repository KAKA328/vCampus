package cn.vcampus.student;

import java.io.Serializable;

/** Teacher identity is resolved exclusively from the session token. */
public final class TeacherSelfQueryV1Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;

    public TeacherSelfQueryV1Command(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token is required");
        }
        this.token = token.trim();
    }

    public String getToken() { return token; }
}
