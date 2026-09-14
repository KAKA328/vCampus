package cn.vcampus.client.view;

import javax.swing.JButton;
import javax.swing.JOptionPane;

/** 绑定图书馆操作事件与无障碍名称；不执行业务校验。 */
final class LibraryViewBindings {
    /** 设置图书馆控件的无障碍名称、提示与键盘焦点。 */
    static void configureAccessibility(LibraryViewState v) {
        v.borrowButton.setToolTipText("可多选借阅；超出服务器同时在借上限时，本批不会借出任何图书");
        v.keywordField.getAccessibleContext().setAccessibleName("图书关键词");
        v.keywordField.getAccessibleContext().setAccessibleDescription("输入书名、作者、ISBN 或图书编号");
        v.keywordField.setToolTipText("可输入书名、作者、ISBN 或图书编号");
        v.categoryField.getAccessibleContext().setAccessibleName("图书分类");
        v.categoryField.getAccessibleContext().setAccessibleDescription("选择文学、科幻等分类，全部分类表示不限制分类");
        v.categoryField.setToolTipText("选择图书分类后点击查询图书");
        v.bookTable.getAccessibleContext().setAccessibleName(v.manager ? "馆藏管理列表" : "可借图书列表");
        v.bookTable.getAccessibleContext().setAccessibleDescription(
                v.manager ? "选择一行可查看图书详情" : "可选择一行或多行后批量借阅");
        v.historyTable.getAccessibleContext().setAccessibleName(v.manager ? "全部借阅记录" : "我的借阅记录");
        v.historyTable.getAccessibleContext().setAccessibleDescription("选择借阅中的记录可办理归还");
        v.compensationTable.getAccessibleContext().setAccessibleName(v.manager ? "全部遗失赔偿单" : "我的遗失赔偿单");
        v.compensationTable.getAccessibleContext().setAccessibleDescription(v.manager ? "只可查看，不能代扣余额" : "选择待支付记录，核对金额后确认支付");
        v.dueReminder.getAccessibleContext().setAccessibleName(v.manager ? "全校借阅到期提醒" : "个人借阅到期提醒");
        JButton[] actions = {v.searchButton, v.resetSearchButton, v.detailButton, v.borrowButton,
                v.historyButton, v.allHistoryButton, v.returnButton, v.addBookButton, v.restockButton, v.editBookButton, v.copiesButton,
                v.handleReminderButton, v.acknowledgeReminderButton, v.declareLossButton,
                v.refreshCompensationsButton, v.payCompensationButton, v.rechargeHelpButton};
        for (JButton action : actions) {
            action.setFocusable(true);
            action.setRequestFocusEnabled(true);
            action.setFocusPainted(true);
        }
    }

    /** 注册一次监听器；基础业务和 V4 扩展各自使用明确版本的服务端消息。 */
    static void bind(LibraryViewState v) {
        v.searchButton.addActionListener(event -> LibraryCatalogActions.loadBooks(v));
        v.detailButton.addActionListener(event -> LibraryCatalogActions.loadSelectedDetail(v));
        v.borrowButton.addActionListener(event -> LibraryCatalogActions.borrowSelected(v));
        v.historyButton.addActionListener(event -> LibraryCirculationActions.loadOwnHistory(v));
        v.allHistoryButton.addActionListener(event -> LibraryCirculationActions.loadAllHistory(v));
        v.returnButton.addActionListener(event -> LibraryCirculationActions.returnSelected(v));
        v.declareLossButton.addActionListener(event -> LibraryCompensationActions.declareSelectedLoss(v));
        v.refreshCompensationsButton.addActionListener(event -> LibraryCirculationActions.refreshLibrary(v, v.manager));
        v.payCompensationButton.addActionListener(event -> LibraryCompensationActions.paySelectedCompensation(v));
        v.rechargeHelpButton.addActionListener(event -> JOptionPane.showMessageDialog(v.panel,
                "请在主界面切换到商店，进入“钱包”页并完成充值，再回到此页点击“刷新赔偿与余额”。\n图书馆与商店使用同一登录账号的校园钱包。",
                "校园钱包充值", JOptionPane.INFORMATION_MESSAGE));
        v.addBookButton.addActionListener(event -> LibraryCatalogDialogs.showAddBookDialog(v));
        v.restockButton.addActionListener(event -> LibraryCatalogDialogs.showRestockDialog(v));
        v.editBookButton.addActionListener(event -> LibraryCatalogV4Actions.editSelected(v));
        v.copiesButton.addActionListener(event -> LibraryCatalogV4Actions.copiesSelected(v));
        v.handleReminderButton.addActionListener(event -> LibraryReminderController.goToReminderRecord(v));
        v.acknowledgeReminderButton.addActionListener(event -> LibraryReminderController.acknowledgeReminders(v));
        v.resetSearchButton.addActionListener(event -> LibraryCatalogActions.resetSearch(v));
        v.keywordField.addActionListener(event -> LibraryCatalogActions.loadBooks(v));
        v.compensationTable.getSelectionModel().addListSelectionListener(event -> LibraryRequestRunner.updateButtonState(v));
        v.workspaceTabs.addChangeListener(event -> {
            // 仅在实际展示后进入赔偿页时查询，不干扰初始借阅加载或纯布局测试。
            if (v.panel.isShowing() && v.workspaceTabs.getSelectedIndex() == 2) {
                LibraryCirculationActions.loadCompensations(v);
            }
        });
        LibraryViewBindings.configureAccessibility(v);
        LibraryRequestRunner.updateButtonState(v);
    }
}
