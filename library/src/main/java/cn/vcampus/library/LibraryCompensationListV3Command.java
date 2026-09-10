package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryCompensationListV3Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final boolean allUsers;

    public LibraryCompensationListV3Command(String token, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.allUsers = allUsers;
    }

    public String getToken() { return token; }
    public boolean isAllUsers() { return allUsers; }
}

