package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

/** 旧接口实现未提供 V4 时明确报错，不伪造快照或绕过服务端校验。 */
public final class LibraryV4Delegation {
    /** 工具类无需实例。 */
    private LibraryV4Delegation() { }
    /** 返回不支持扩展的明确结果。 */
    public static <T> ServiceResult<T> unavailable() {
        return ServiceResult.failure(StatusCode.CONFLICT, "此服务尚未启用图书馆 V4，请配套升级服务器和数据库");
    }
}
