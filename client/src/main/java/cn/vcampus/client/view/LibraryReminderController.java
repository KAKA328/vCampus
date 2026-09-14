package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 到期提醒显示、今日已读及定位待归还记录。 */
final class LibraryReminderController {
    /** 重新计算临期和逾期提醒，已归还和遗失记录不参与。 */
    static void updateDueReminder(LibraryViewState v, List<BorrowRecord> records) {
        LibraryReminderController.updateDueReminder(v, records, LocalDate.now());
    }

    /** 重新计算临期和逾期提醒，已归还和遗失记录不参与。 */
    static void updateDueReminder(LibraryViewState v, List<BorrowRecord> records, LocalDate today) {
        v.reminderDate = today;
        v.unreadReminders = v.manager ? new ArrayList<BorrowRecord>() : v.reminderState.unread(records, today);
        if (v.manager) {
            for (BorrowRecord record : records) {
                if (record.getStatus() == BorrowStatus.BORROWED
                        && !record.getDueDate().isAfter(today.plusDays(LibraryDueReminder.WARNING_DAYS))) {
                    v.unreadReminders.add(record);
                }
            }
        }
        LibraryDueReminder.Summary summary = LibraryDueReminder.summarize(v.unreadReminders, today);
        v.dueReminder.setText(LibraryDueReminder.message(summary, v.manager));
        Color color = summary.getOverdueCount() > 0 ? VCampusTheme.DANGER
                : summary.getDueSoonCount() > 0 ? VCampusTheme.ACCENT : VCampusTheme.SUCCESS;
        VCampusTheme.statusPill(v.dueReminder, color);
        v.reminderCard.setVisible(!v.unreadReminders.isEmpty());
        LibraryRequestRunner.updateButtonState(v);
        v.panel.revalidate();
        v.panel.repaint();
    }

    /** 跨午夜时恢复当天提醒，不额外发送网络请求。 */
    static void refreshReminderDate(LibraryViewState v, LocalDate today) {
        if (v.historyLoaded && !today.equals(v.reminderDate)) LibraryReminderController.updateDueReminder(v, v.currentRecords, today);
    }

    /** 只收起本机今日已读提醒，不改变借阅或归还状态。 */
    static void acknowledgeReminders(LibraryViewState v) {
        if (v.manager || v.unreadReminders.isEmpty()) return;
        v.reminderState.acknowledge(v.unreadReminders, LocalDate.now());
        LibraryReminderController.updateDueReminder(v, v.currentRecords);
        LibraryRequestRunner.showStatus(v, "已收起今日已读提醒，图书仍需归还；次日或出现新提醒时会再次提示。", VCampusTheme.SUCCESS);
    }

    /** 切换到借阅页并定位最早到期的待归还记录。 */
    static void goToReminderRecord(LibraryViewState v) {
        v.workspaceTabs.setSelectedIndex(1);
        BorrowRecord nearest = null;
        for (BorrowRecord record : v.unreadReminders) {
            if (nearest == null || record.getDueDate().isBefore(nearest.getDueDate())) nearest = record;
        }
        if (nearest == null) return;
        for (int row = 0; row < v.historyModel.getRowCount(); row++) {
            if (nearest.getRecordId().equals(v.historyModel.getValueAt(row, 0))) {
                int visibleRow = v.historyTable.convertRowIndexToView(row);
                v.historyTable.setRowSelectionInterval(visibleRow, visibleRow);
                v.historyTable.scrollRectToVisible(v.historyTable.getCellRect(visibleRow, 0, true));
                v.historyTable.requestFocusInWindow();
                LibraryRequestRunner.showStatus(v, "已定位最早到期的借阅记录，核对后点击“归还选中记录”办理归还。", VCampusTheme.MUTED);
                return;
            }
        }
    }
}
