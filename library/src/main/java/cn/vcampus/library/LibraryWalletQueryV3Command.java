package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryWalletQueryV3Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;

    public LibraryWalletQueryV3Command(String token) {
        this.token = LibraryCommandSupport.required(token, "token");
    }

    public String getToken() { return token; }
}
