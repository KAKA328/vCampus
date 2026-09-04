package cn.vcampus.library;

import java.io.Serializable;

/** Return one active record owned by the current session user. */
public final class LibraryReturnV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String recordId;

    public LibraryReturnV2Command(String token, String recordId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.recordId = LibraryCommandSupport.required(recordId, "recordId");
    }

    public String getToken() { return token; }
    public String getRecordId() { return recordId; }
}
