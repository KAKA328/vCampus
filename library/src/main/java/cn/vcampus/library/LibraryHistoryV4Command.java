package cn.vcampus.library;

import java.io.Serializable;

/** 带名称快照和实体册编号的 V4 历史查询；权限由服务器判断。 */
public final class LibraryHistoryV4Command implements Serializable {
    /** 新命令的序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 已登录会话。 */
    private final String token;
    /** 可选目标用户，仅管理员能够读取他人。 */
    private final String targetUserId;
    /** 管理员全校查询标志。 */
    private final boolean allUsers;
    /** 规范化查询范围，不接受客户端自授权限。 */
    public LibraryHistoryV4Command(String token, String targetUserId, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.targetUserId = LibraryCommandSupport.optional(targetUserId);
        this.allUsers = allUsers;
    }
    /** 返回会话。 */
    public String getToken() { return token; }
    /** 返回目标用户。 */
    public String getTargetUserId() { return targetUserId; }
    /** 返回全校范围请求标志。 */
    public boolean isAllUsers() { return allUsers; }
}
