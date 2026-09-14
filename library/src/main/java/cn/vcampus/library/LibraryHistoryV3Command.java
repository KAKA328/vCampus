package cn.vcampus.library;

import java.io.Serializable;

/** V3 history supports loss and compensation lifecycle states. */
public final class LibraryHistoryV3Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 管理员请求查看的目标用户编号。 */
    private final String targetUserId;
    /** 是否请求管理员可见的全部用户范围。 */
    private final boolean allUsers;

    /** 创建本人 V3 历史请求，返回值可包含遗失和已赔偿状态。 */
    public LibraryHistoryV3Command(String token) { this(token, null, false); }

    /** 封装 V3 历史查询范围；管理员权限不由客户端标志授予。 */
    public LibraryHistoryV3Command(String token, String targetUserId, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.targetUserId = LibraryCommandSupport.optional(targetUserId);
        this.allUsers = allUsers;
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回管理员请求查看的目标用户编号。 */
    public String getTargetUserId() { return targetUserId; }
    /** 是否请求全部用户记录；该标志不能代替服务端权限校验。 */
    public boolean isAllUsers() { return allUsers; }
}
