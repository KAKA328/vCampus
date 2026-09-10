package cn.vcampus.library;

import java.io.Serializable;

/** V3 history supports loss and compensation lifecycle states. */
public final class LibraryHistoryV3Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String targetUserId;
    private final boolean allUsers;

    public LibraryHistoryV3Command(String token) { this(token, null, false); }

    public LibraryHistoryV3Command(String token, String targetUserId, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.targetUserId = LibraryCommandSupport.optional(targetUserId);
        this.allUsers = allUsers;
    }

    public String getToken() { return token; }
    public String getTargetUserId() { return targetUserId; }
    public boolean isAllUsers() { return allUsers; }
}
