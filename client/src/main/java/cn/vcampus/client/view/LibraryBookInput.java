package cn.vcampus.client.view;

/** 新增图书表单的数值校验；正式业务校验仍由 Book 及服务端执行。 */
final class LibraryBookInput {
    /** 工具类不需要实例。 */
    private LibraryBookInput() { }

    /**
     * 接受免费图书，拒绝负数、NaN 和无穷大。
     * @param value 用户输入的人民币元价格
     * @return 有限非负价格
     * @throws IllegalArgumentException 输入不是有限非负数时
     */
    static double price(String value) {
        if (value == null) throw new IllegalArgumentException("price is required");
        double amount = Double.parseDouble(value.trim());
        if (!Double.isFinite(amount) || amount < 0.0d) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        return amount == 0.0d ? 0.0d : amount;
    }
}
