package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryCompensationPayV3Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String compensationId;

    public LibraryCompensationPayV3Command(String token, String compensationId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.compensationId = LibraryCommandSupport.required(compensationId, "compensationId");
    }

    public String getToken() { return token; }
    public String getCompensationId() { return compensationId; }
}
