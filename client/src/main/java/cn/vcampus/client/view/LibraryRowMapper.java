package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryCompensation;
import java.math.BigDecimal;
import java.util.Locale;

/** 图书馆列表行、金额与中文状态的纯显示映射。 */
final class LibraryRowMapper {
    /** 生成包含书名、锁定金额及钱包说明的付款确认文字。 */
    static String paymentConfirmationText(LibraryCompensation item, Long balance) {
        return "图书：《" + item.getBookTitle() + "》（" + item.getBookId() + "）\n赔偿单：" + item.getCompensationId()
                + "\n原价赔偿金额：" + LibraryRowMapper.formatCents(item.getAmountCents())
                + (item.getAmountCents() == 0 ? "\n此单金额为零，确认后结清，不扣除钱包余额。"
                    : "\n将从本人校园钱包扣款（与商店共用同一余额）。")
                + "\n上次查询余额：" + (balance == null ? "暂不可用，以服务器校验为准" : LibraryRowMapper.formatCents(balance.longValue()))
                + (item.getAmountCents() == 0 ? "\n确认结清后将同步借阅与赔偿状态。"
                    : "\n确认后提交支付；余额不足不会扣款，可先去商店的校园钱包充值。");
    }

    /** 将赔偿快照映射为包含书名、金额和状态的行。 */
    static Object[] compensationRow(LibraryCompensation item) {
        return new Object[] {item.getCompensationId(), item.getBookTitle(), item.getBookId(), item.getUserId(),
                LibraryRowMapper.formatCents(item.getAmountCents()), item.getStatus().name(),
                StoreRowMapper.formatDateTime(item.getCreatedAt()), StoreRowMapper.formatDateTime(item.getPaidAt())};
    }

    /** 使用整数分生成精确金额显示，避免浮点累计。 */
    static String formatCents(long cents) {
        return "￥" + BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /** 判断是否显示馆员的管理工作区。 */
    static boolean canManage(Role role) {
        return role == Role.LIBRARIAN;
    }

    /** 将馆藏快照映射为表格行，不修改库存。 */
    static Object[] bookRow(Book book) {
        return new Object[] {book.getBookId(), book.getTitle(), book.getAuthor(), LibraryRowMapper.formatPrice(book.getPrice()),
                book.getCategory(), book.getIsbn(), book.getPublisher(),
                Integer.valueOf(book.getTotalCopies()), Integer.valueOf(book.getAvailableCopies()),
                book.getLocation()};
    }

    /** 按人民币元格式显示馆藏价格。 */
    static String formatPrice(double price) {
        return String.format(Locale.CHINA, "￥%.2f", Double.valueOf(price));
    }

    /** 保留记录编号用于归还，并显示当前书名映射。 */
    static Object[] historyRow(BorrowRecord record, String bookTitle) {
        return new Object[] {record.getRecordId(), record.getOrderId(), record.getUserId(), record.getBookId(),
                bookTitle == null || bookTitle.trim().isEmpty() ? "书名暂不可用" : bookTitle,
                record.getBorrowDate(), record.getDueDate(), record.getReturnDate() == null ? "" : record.getReturnDate(),
                record.getStatus().name()};
    }

    /** 将既有状态码转换为中文失败提示。 */
    static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST) return "请求数据不正确，或所选图书库存不足";
        if (statusCode == StatusCode.UNAUTHORIZED) return "登录状态已失效，请重新登录";
        if (statusCode == StatusCode.FORBIDDEN) return "当前账号没有执行该图书馆操作的权限";
        if (statusCode == StatusCode.NOT_FOUND) return "图书或借阅记录不存在";
        if (statusCode == StatusCode.CONFLICT) return "图书或借阅记录状态已发生变化，请刷新后重试";
        return "服务器处理图书馆请求失败";
    }

    /** 将借阅状态转换为可读中文，不改变底层枚举。 */
    static String borrowStatusLabel(Object value) {
        if (BorrowStatus.BORROWED.name().equals(String.valueOf(value))) return "借阅中";
        if (BorrowStatus.RETURNED.name().equals(String.valueOf(value))) return "已归还";
        if (BorrowStatus.LOST.name().equals(String.valueOf(value))) return "已遗失·待赔偿";
        if (BorrowStatus.COMPENSATED.name().equals(String.valueOf(value))) return "已赔偿";
        return String.valueOf(value);
    }
}
