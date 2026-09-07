package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.time.LocalDate;
import java.util.List;

/** Calculates presentation-ready due-date reminders from authenticated borrowing history. */
final class LibraryDueReminder {
    static final int WARNING_DAYS = 3;

    private LibraryDueReminder() { }

    static Summary summarize(List<BorrowRecord> records, LocalDate today) {
        if (records == null || today == null) {
            throw new IllegalArgumentException("records and today must not be null");
        }
        int active = 0;
        int overdue = 0;
        int dueSoon = 0;
        LocalDate nearestDueDate = null;
        LocalDate warningEnd = today.plusDays(WARNING_DAYS);
        for (BorrowRecord record : records) {
            if (record == null || record.getStatus() != BorrowStatus.BORROWED) continue;
            active++;
            LocalDate dueDate = record.getDueDate();
            if (nearestDueDate == null || dueDate.isBefore(nearestDueDate)) {
                nearestDueDate = dueDate;
            }
            if (dueDate.isBefore(today)) {
                overdue++;
            } else if (!dueDate.isAfter(warningEnd)) {
                dueSoon++;
            }
        }
        return new Summary(active, overdue, dueSoon, nearestDueDate);
    }

    static String message(Summary summary, boolean manager) {
        if (summary == null) throw new IllegalArgumentException("summary must not be null");
        String scope = manager ? "全校" : "你";
        if (summary.getOverdueCount() > 0) {
            String dueSoon = summary.getDueSoonCount() > 0
                    ? "，另有 " + summary.getDueSoonCount() + " 本将在 " + WARNING_DAYS + " 天内到期"
                    : "";
            return "归还提醒：" + scope + "有 " + summary.getOverdueCount() + " 本图书已逾期"
                    + dueSoon + "，请尽快处理。";
        }
        if (summary.getDueSoonCount() > 0) {
            return "到期提醒：" + scope + "有 " + summary.getDueSoonCount() + " 本图书将在 "
                    + WARNING_DAYS + " 天内到期，最近应还日为 " + summary.getNearestDueDate() + "。";
        }
        if (summary.getActiveCount() == 0) {
            return manager ? "当前没有未归还的借阅记录。" : "当前没有待归还图书。";
        }
        return "借阅提醒：" + scope + "有 " + summary.getActiveCount() + " 本图书借阅中，最近应还日为 "
                + summary.getNearestDueDate() + "，暂无临期记录。";
    }

    static final class Summary {
        private final int activeCount;
        private final int overdueCount;
        private final int dueSoonCount;
        private final LocalDate nearestDueDate;

        Summary(int activeCount, int overdueCount, int dueSoonCount, LocalDate nearestDueDate) {
            this.activeCount = activeCount;
            this.overdueCount = overdueCount;
            this.dueSoonCount = dueSoonCount;
            this.nearestDueDate = nearestDueDate;
        }

        int getActiveCount() { return activeCount; }
        int getOverdueCount() { return overdueCount; }
        int getDueSoonCount() { return dueSoonCount; }
        LocalDate getNearestDueDate() { return nearestDueDate; }
    }
}
