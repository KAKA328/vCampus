package cn.vcampus.library;

/** 现有图书馆命令共用的文本校验。 */
final class LibraryCommandSupport {
    /** 请求参数校验工具类不需要实例。 */
    private LibraryCommandSupport() { }

    /** 校验必填命令字段并去除首尾空白。 */
    static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /** 规范化可选命令字段。 */
    static String optional(String value) { return value == null ? null : value.trim(); }
}
