package cn.vcampus.library;

/** 实体册批量创建的资源边界；避免一次请求生成数十亿条实体记录。 */
public final class LibraryCopyRules {
    /** 单次新增或补库最多生成的实体册数。 */
    public static final int MAX_BATCH = 10000;
    /** 工具类无需实例。 */
    private LibraryCopyRules() { }
    /** 允许空馆藏，但创建实体册的单次操作须有界。 */
    public static void checkBatch(int count) {
        if (count < 0 || count > MAX_BATCH) {
            throw new IllegalArgumentException("单次新增或补库最多支持 " + MAX_BATCH + " 册，请分批办理");
        }
    }
}
