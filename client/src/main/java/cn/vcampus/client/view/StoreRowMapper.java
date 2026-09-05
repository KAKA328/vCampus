package cn.vcampus.client.view;

import cn.vcampus.store.CartLine;
import cn.vcampus.store.Money;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 商店表格行构造与金额格式化工具，全部为纯函数，便于单元测试。
 * 金额一律以「分」为单位的 long 参与格式化，展示时才转元；商品与订单实体里的 double 价格
 * 只作展示来源，元→分统一走 Money.toCents（全链路唯一换算入口，DSH P1-2），避免把浮点误差带进界面合计。
 */
final class StoreRowMapper {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private StoreRowMapper() {
    }

    /** 商品行：商品号 / 名称 / 类别 / 价格 / 库存 / 状态 / 说明。 */
    static Object[] productRow(Product product) {
        return new Object[] {
                product.getProductId(), product.getName(), product.getCategory(),
                Double.valueOf(toYuan(toCents(product.getPrice()))),
                Integer.valueOf(product.getStock()),
                product.isActive() ? "在售" : "已下架",
                product.getDescription() == null ? "" : product.getDescription()
        };
    }

    /** 购物车明细行：条目号 / 商品 / 单价 / 数量 / 小计 / 状态。金额直接取服务端联表结果，不再本地计算。 */
    static Object[] cartRow(CartLine line) {
        return new Object[] {
                line.getCartItemId(), line.getProductName(),
                Double.valueOf(toYuan(line.getUnitPriceCents())),
                Integer.valueOf(line.getQuantity()),
                Double.valueOf(toYuan(line.getSubtotalCents())),
                line.isActive() ? "在售" : "已下架"
        };
    }

    /** 本人订单行：订单号 / 商品 / 数量 / 单价 / 总价 / 时间。 */
    static Object[] orderRow(Order order) {
        return new Object[] {
                order.getOrderId(), order.getProductName(), Integer.valueOf(order.getQuantity()),
                Double.valueOf(toYuan(order.getUnitPriceCents())),
                Double.valueOf(toYuan(order.getTotalPriceCents())),
                formatDateTime(order.getOrderDate())
        };
    }

    /** 全部订单行：比本人订单多一列买家，供管理员核对是谁下的单。 */
    static Object[] allOrderRow(Order order) {
        return new Object[] {
                order.getOrderId(), order.getUserId(), order.getProductName(),
                Integer.valueOf(order.getQuantity()),
                Double.valueOf(toYuan(order.getUnitPriceCents())),
                Double.valueOf(toYuan(order.getTotalPriceCents())),
                formatDateTime(order.getOrderDate())
        };
    }

    /** 流水行：时间 / 类型 / 金额 / 变动后余额 / 操作者 / 备注。金额带符号，入账为正、扣款为负。 */
    static Object[] ledgerRow(WalletTransaction transaction) {
        return new Object[] {
                formatDateTime(transaction.getCreatedAt()),
                transactionTypeName(transaction.getType()),
                Long.valueOf(transaction.getAmountCents()),
                Long.valueOf(transaction.getBalanceAfterCents()),
                transaction.getOperatorId(),
                transaction.getNote() == null ? "" : transaction.getNote()
        };
    }

    /** 流水类型中文名，界面不直接暴露枚举常量。 */
    static String transactionTypeName(WalletTransactionType type) {
        if (type == WalletTransactionType.RECHARGE) {
            return "充值";
        }
        if (type == WalletTransactionType.PURCHASE) {
            return "购买";
        }
        if (type == WalletTransactionType.CHECKOUT) {
            return "结账";
        }
        if (type == WalletTransactionType.REFUND) {
            return "退款";
        }
        if (type == WalletTransactionType.ADJUST) {
            return "管理员校正";
        }
        return String.valueOf(type);
    }

    /**
     * 分转元：long 分 /100 保留两位小数，仅用于展示，不参与任何账本运算。
     * 不足一元的负数必须补上负号：整数除法向零截断会让 -5 / 100 得到 0，直接格式化会丢掉符号。
     */
    static String formatYuan(long cents) {
        long whole = cents / 100;
        long fraction = Math.abs(cents % 100);
        if (cents < 0 && whole == 0) {
            return String.format("-0.%02d", Long.valueOf(fraction));
        }
        return String.format("%d.%02d", Long.valueOf(whole), Long.valueOf(fraction));
    }

    /** 带符号分转元：入账显示 +，扣款显示 -，零额不带符号，用于流水金额列。 */
    static String formatSignedYuan(long cents) {
        if (cents > 0) {
            return "+" + formatYuan(cents);
        }
        if (cents < 0) {
            return "-" + formatYuan(-cents);
        }
        return formatYuan(0L);
    }

    /** 分转元的 double 形式，供表格金额列使用，保证按数值而非字典序排序。 */
    static double toYuan(long cents) {
        return cents / 100.0d;
    }

    /** 元转分：委托全链路唯一换算入口 Money.toCents（DSH P1-2），客户端不再自持换算公式。 */
    static long toCents(double yuan) {
        return Money.toCents(yuan);
    }

    static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME.format(dateTime);
    }
}
