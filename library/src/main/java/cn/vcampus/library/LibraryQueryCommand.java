package cn.vcampus.library;

import java.io.Serializable;

/** Command for authenticated library search. */
public final class LibraryQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String keyword;

    public LibraryQueryCommand(String token, String keyword) {
        this.token = requireText(token, "token");
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    public String getToken() { return token; }
    public String getKeyword() { return keyword; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
