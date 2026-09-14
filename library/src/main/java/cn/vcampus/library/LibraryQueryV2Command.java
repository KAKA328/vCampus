package cn.vcampus.library;

import java.io.Serializable;

/** Token-only catalog query; keyword and category are optional filters. */
public final class LibraryQueryV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 馆藏检索关键词。 */
    private final String keyword;
    /** 馆藏分类。 */
    private final String category;

    /** 规范化关键词和分类，封装带会话的馆藏检索请求。 */
    public LibraryQueryV2Command(String token, String keyword, String category) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.keyword = LibraryCommandSupport.optional(keyword);
        this.category = LibraryCommandSupport.optional(category);
    }

    /** 创建不限制分类的馆藏检索请求。 */
    public LibraryQueryV2Command(String token, String keyword) { this(token, keyword, null); }
    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回馆藏检索关键词。 */
    public String getKeyword() { return keyword; }
    /** 返回馆藏分类。 */
    public String getCategory() { return category; }
}
