package cn.vcampus.user;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Admin-scoped batch account import command. */
public final class UserImportCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final List<UserImportRow> rows;

    public UserImportCommand(String token, List<UserImportRow> rows) {
        if (token == null || token.trim().isEmpty() || rows == null) {
            throw new IllegalArgumentException("token and rows are required");
        }
        this.token = token.trim();
        this.rows = Collections.unmodifiableList(new ArrayList<UserImportRow>(rows));
    }

    public String getToken() { return token; }
    public List<UserImportRow> getRows() { return rows; }
}
