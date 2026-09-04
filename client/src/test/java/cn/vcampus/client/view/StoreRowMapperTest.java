package cn.vcampus.client.view;

import cn.vcampus.store.CartLine;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreRowMapperTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 4, 10, 30, 0);

    @Test
    void formatYuanKeepsTwoDecimalPlaces() {
        assertEquals("12.34", StoreRowMapper.formatYuan(1234L));
        assertEquals("0.00", StoreRowMapper.formatYuan(0L));
        assertEquals("0.05", StoreRowMapper.formatYuan(5L));
        assertEquals("100.00", StoreRowMapper.formatYuan(10000L));
    }

    @Test
    void formatYuanKeepsSignForNegativeAmountBelowOneYuan() {
        // 整数除法向零截断会让 -5 / 100 得到 0，若不特判就会把负号丢掉
        assertEquals("-0.05", StoreRowMapper.formatYuan(-5L));
        assertEquals("-12.34", StoreRowMapper.formatYuan(-1234L));
    }

    @Test
    void formatSignedYuanMarksIncomeAndExpense() {
        assertEquals("+50.00", StoreRowMapper.formatSignedYuan(5000L));
        assertEquals("-19.80", StoreRowMapper.formatSignedYuan(-1980L));
        assertEquals("+0.01", StoreRowMapper.formatSignedYuan(1L));
        assertEquals("-0.01", StoreRowMapper.formatSignedYuan(-1L));
        // 零额不带符号，避免「+0.00」这种让人怀疑是入账的写法
        assertEquals("0.00", StoreRowMapper.formatSignedYuan(0L));
    }

    @Test
    void yuanAndCentsConversionRoundTrips() {
        assertEquals(1250L, StoreRowMapper.toCents(12.50d));
        // 浮点尾巴 12.999 归一到分，避免输入与表格显示不一致
        assertEquals(1300L, StoreRowMapper.toCents(12.999d));
        assertEquals(12.50d, StoreRowMapper.toYuan(1250L), 0.0001d);
        assertEquals(1250L, StoreRowMapper.toCents(StoreRowMapper.toYuan(1250L)));
    }

    @Test
    void transactionTypeNamesAreChineseForUsers() {
        assertEquals("充值", StoreRowMapper.transactionTypeName(WalletTransactionType.RECHARGE));
        assertEquals("购买", StoreRowMapper.transactionTypeName(WalletTransactionType.PURCHASE));
        assertEquals("结账", StoreRowMapper.transactionTypeName(WalletTransactionType.CHECKOUT));
        assertEquals("退款", StoreRowMapper.transactionTypeName(WalletTransactionType.REFUND));
        assertEquals("管理员校正", StoreRowMapper.transactionTypeName(WalletTransactionType.ADJUST));
    }

    @Test
    void productRowShowsAvailabilityAndNumericPrice() {
        Product active = new Product("P001", "矿泉水", 20, 2.5d, "550ml", "饮品", true);
        Product retired = new Product("P002", "旧款水杯", 0, 19.9d, null, "日用", false);

        Object[] activeRow = StoreRowMapper.productRow(active);
        Object[] retiredRow = StoreRowMapper.productRow(retired);

        assertEquals(7, activeRow.length);
        assertEquals("P001", activeRow[0]);
        assertEquals("矿泉水", activeRow[1]);
        assertEquals("饮品", activeRow[2]);
        // 价格存 Double 而非字符串，否则列排序会按字典序把 9.00 排在 12.50 之后
        assertEquals(Double.valueOf(2.50d), activeRow[3]);
        assertEquals(Integer.valueOf(20), activeRow[4]);
        assertEquals("在售", activeRow[5]);
        assertEquals("550ml", activeRow[6]);

        assertEquals("已下架", retiredRow[5]);
        // 说明可空，界面用空串占位而不是显示 null
        assertEquals("", retiredRow[6]);
    }

    @Test
    void cartRowUsesServerSubtotalWithoutRecalculating() {
        // 单价 3.33 元 × 3 件，服务端给出的小计是 9.99 元（999 分）；前端不得自行重算
        CartLine line = new CartLine("C001", "P001", "矿泉水", 333L, 3, 999L, true, BASE);

        Object[] row = StoreRowMapper.cartRow(line);

        assertEquals(6, row.length);
        assertEquals("C001", row[0]);
        assertEquals("矿泉水", row[1]);
        assertEquals(Double.valueOf(3.33d), row[2]);
        assertEquals(Integer.valueOf(3), row[3]);
        assertEquals(Double.valueOf(9.99d), row[4]);
        assertEquals("在售", row[5]);
    }

    @Test
    void orderRowKeepsSnapshotNameAndFormattedTime() {
        Order order = new Order("O001", "student001", "P001", 2, 5.0d, BASE, "矿泉水", 2.5d);

        Object[] row = StoreRowMapper.orderRow(order);

        assertEquals(6, row.length);
        assertEquals("O001", row[0]);
        // 订单显示下单时的商品名快照，不是商品当前名称
        assertEquals("矿泉水", row[1]);
        assertEquals(Integer.valueOf(2), row[2]);
        assertEquals(Double.valueOf(2.50d), row[3]);
        assertEquals(Double.valueOf(5.00d), row[4]);
        assertEquals("2026-09-04 10:30", row[5]);
    }

    @Test
    void allOrderRowInsertsBuyerAsSecondColumn() {
        Order order = new Order("O001", "student001", "P001", 2, 5.0d, BASE, "矿泉水", 2.5d);

        Object[] row = StoreRowMapper.allOrderRow(order);

        assertEquals(7, row.length);
        assertEquals("O001", row[0]);
        // 全部订单比本人订单多一列买家，管理员据此核对是谁下的单
        assertEquals("student001", row[1]);
        assertEquals("矿泉水", row[2]);
        assertEquals(Integer.valueOf(2), row[3]);
        assertEquals(Double.valueOf(2.50d), row[4]);
        assertEquals(Double.valueOf(5.00d), row[5]);
        assertEquals("2026-09-04 10:30", row[6]);
    }

    @Test
    void ledgerRowKeepsSignedCentsForRenderer() {
        WalletTransaction expense = new WalletTransaction("T001", "student001", WalletTransactionType.PURCHASE,
                -1980L, 8020L, "student001", "order O001", BASE);
        WalletTransaction adjustment = new WalletTransaction("T002", "student001", WalletTransactionType.ADJUST,
                500L, 8520L, "admin001", null, BASE.plusMinutes(5));

        Object[] expenseRow = StoreRowMapper.ledgerRow(expense);
        Object[] adjustmentRow = StoreRowMapper.ledgerRow(adjustment);

        assertEquals(6, expenseRow.length);
        assertEquals("2026-09-04 10:30", expenseRow[0]);
        assertEquals("购买", expenseRow[1]);
        // 金额与余额都存 Long 分，符号与格式化交给 MoneyCellRenderer，模型里不做字符串化
        assertEquals(Long.valueOf(-1980L), expenseRow[2]);
        assertEquals(Long.valueOf(8020L), expenseRow[3]);
        assertEquals("student001", expenseRow[4]);
        assertEquals("order O001", expenseRow[5]);

        assertEquals("管理员校正", adjustmentRow[1]);
        assertEquals(Long.valueOf(500L), adjustmentRow[2]);
        // 校正的操作者是管理员本人而非账户所属用户
        assertEquals("admin001", adjustmentRow[4]);
        assertEquals("", adjustmentRow[5]);
        assertEquals("2026-09-04 10:35", adjustmentRow[0]);
    }

    @Test
    void formatDateTimeToleratesNull() {
        assertEquals("", StoreRowMapper.formatDateTime(null));
        assertTrue(StoreRowMapper.formatDateTime(BASE).startsWith("2026-09-04"));
    }
}
