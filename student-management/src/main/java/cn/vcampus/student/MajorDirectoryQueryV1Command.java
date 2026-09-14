package cn.vcampus.student;

import java.io.Serializable;

public final class MajorDirectoryQueryV1Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    public MajorDirectoryQueryV1Command(String token) { this.token = token; validate(); }
    public void validate() {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("token is required");
    }
    public String getToken() { return token; }
}
