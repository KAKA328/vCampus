package cn.vcampus.library;

import java.io.Serializable;

/** Token-only catalog query; keyword and category are optional filters. */
public final class LibraryQueryV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String keyword;
    private final String category;

    public LibraryQueryV2Command(String token, String keyword, String category) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.keyword = LibraryCommandSupport.optional(keyword);
        this.category = LibraryCommandSupport.optional(category);
    }

    public LibraryQueryV2Command(String token, String keyword) { this(token, keyword, null); }
    public String getToken() { return token; }
    public String getKeyword() { return keyword; }
    public String getCategory() { return category; }
}
