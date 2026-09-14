package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** V4 乐观并发编辑命令；携带读取时的资料用于冲突检查，不携带管理员身份。 */
public final class LibraryBookUpdateV4Command implements Serializable {
    /** 新命令的序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 已登录会话。 */
    private final String token;
    /** 表单打开时读取的馆藏快照。 */
    private final Book expected;
    /** 新资料；其中库存及图书号必须与 expected 一致。 */
    private final Book replacement;
    /** 构造编辑请求；服务器仍重新校验参数，不能信任反序列化数据。 */
    public LibraryBookUpdateV4Command(String token, Book expected, Book replacement) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.expected = Objects.requireNonNull(expected, "expected");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }
    /** 返回会话。 */
    public String getToken() { return token; }
    /** 返回编辑前资料。 */
    public Book getExpected() { return expected; }
    /** 返回待保存资料。 */
    public Book getReplacement() { return replacement; }
}
