package cn.vcampus.store;

/**
 * 金额单位换算的唯一入口（DSH P1-2 折中）。
 *
 * <p>系统内金额「双轨」并存：实体（{@link Product}/{@link Order}）以 double 元承载展示价，
 * 账本（钱包流水、订单实扣）以 long 分参与运算。元→分必须一次性 {@link Math#round} 到分，
 * 且只允许在本类发生——此前该公式散落在 DefaultStoreService、StoreRowMapper、MoneyCellRenderer、
 * StorePanel 多处，任一处漏改（如换了舍入模式）都会让订单与流水对不上账。收敛到此处后，
 * 全链路元→分只有一条公式，订单快照与钱包流水可逐笔对账。
 *
 * <p>本类只做「元→分」这一有舍入风险方向的换算；「分→元」是精确除法、仅用于展示，
 * 由客户端 {@code StoreRowMapper.toYuan/formatYuan} 承担，不在此重复。
 */
public final class Money {

    private Money() {
    }

    /**
     * 元转分：一次性 {@code Math.round(yuan * 100)}，避免浮点误差累积进账本。
     * 传入「单价 × 数量」即得该行小计分值，与结账实扣、购物车小计同式，可逐笔对账。
     *
     * @param yuan 以元为金额的 double 值
     * @return 四舍五入到整分的 long 值
     */
    public static long toCents(double yuan) {
        return Math.round(yuan * 100);
    }
}
