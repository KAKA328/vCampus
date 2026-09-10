package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryLossDeclareV3Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String recordId;

    public LibraryLossDeclareV3Command(String token, String recordId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.recordId = LibraryCommandSupport.required(recordId, "recordId");
    }

    public String getToken() { return token; }
    public String getRecordId() { return recordId; }
}

