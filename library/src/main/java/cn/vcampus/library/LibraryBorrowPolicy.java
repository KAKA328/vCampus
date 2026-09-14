package cn.vcampus.library;

/** 同时在借数量策略；只统计 BORROWED，逾期仍占额度，已归还和遗失记录不占额度。 */
public final class LibraryBorrowPolicy {
    /** 服务端 JVM 配置项；不改变请求 DTO 或消息类型。 */
    public static final String LIMIT_PROPERTY = "vcampus.library.maxActiveLoans";
    /** 未配置时学生和教师统一使用的同时在借上限。 */
    public static final int DEFAULT_LIMIT = 5;

    /** 策略工具类不需要实例。 */
    private LibraryBorrowPolicy() { }

    /**
     * 读取仓库创建时的上限；非法配置直接报错，避免意外取消限制。
     * @return 正整数上限
     */
    public static int configuredLimit() {
        return parseLimit(System.getProperty(LIMIT_PROPERTY));
    }

    /** 解析配置值，供隔离测试验证，不修改全局系统属性。 */
    static int parseLimit(String configured) {
        if (configured == null) return DEFAULT_LIMIT;
        try {
            int limit = Integer.parseInt(configured.trim());
            if (limit > 0) return limit;
        } catch (NumberFormatException invalid) {
            // 统一返回带配置项名称的诊断。
        }
        throw new IllegalArgumentException(LIMIT_PROPERTY + " must be a positive integer");
    }

    /**
     * 不相加计数，避免大批量请求造成整数溢出。
     * @param limit 配置的上限
     * @param activeCount 当前仍在借的总数
     * @param requestedCount 本批申请借阅的数量
     * @return 本批可以全部容纳时为 true
     */
    public static boolean allows(int limit, long activeCount, int requestedCount) {
        return activeCount >= 0 && requestedCount >= 0 && requestedCount <= (long) limit - activeCount;
    }

    /**
     * 返回旧客户端也能直接显示的失败说明。
     * @param limit 当前上限
     * @return 提示先归还或减少本批数量的中文消息
     */
    public static String exceededMessage(int limit) {
        return "每人同时最多借阅 " + limit + " 本图书，请先归还或减少本次借阅数量；本次未借出任何图书。";
    }
}
