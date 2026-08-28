package cn.vcampus.user;

import java.io.Serializable;

/** One raw row from an admin-provided user import file. */
public final class UserImportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String password;
    private final String displayName;
    private final String roleCode;

    public UserImportRow(String userId, String password, String displayName, String roleCode) {
        this.userId = userId;
        this.password = password;
        this.displayName = displayName;
        this.roleCode = roleCode;
    }

    public String getUserId() { return userId; }
    public String getPassword() { return password; }
    public String getDisplayName() { return displayName; }
    public String getRoleCode() { return roleCode; }
}
