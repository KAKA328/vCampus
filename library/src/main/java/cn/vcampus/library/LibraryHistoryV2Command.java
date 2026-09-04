package cn.vcampus.library;

import java.io.Serializable;

/** Query own history, or a target user/all histories when the caller has manage permission. */
public final class LibraryHistoryV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String targetUserId;
    private final boolean allUsers;

    public LibraryHistoryV2Command(String token) { this(token, null, false); }

    public LibraryHistoryV2Command(String token, String targetUserId, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.targetUserId = LibraryCommandSupport.optional(targetUserId);
        this.allUsers = allUsers;
    }

    public String getToken() { return token; }
    public String getTargetUserId() { return targetUserId; }
    public boolean isAllUsers() { return allUsers; }
}
