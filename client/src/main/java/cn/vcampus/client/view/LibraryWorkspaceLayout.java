package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

/** 按读者或管理员身份构建馆藏、借阅和赔偿三个工作区。 */
final class LibraryWorkspaceLayout {
    /** 按角色创建馆藏、借阅流通与遗失赔偿工作区。 */
    static JTabbedPane tabs(LibraryViewState v) {
        JTabbedPane tabs = v.workspaceTabs;
        VCampusTheme.tabs(tabs);
        tabs.addTab(v.manager ? "馆藏管理" : "找书借阅", LibraryWorkspaceLayout.catalogPanel(v));
        tabs.addTab(v.manager ? "流通管理" : "我的借阅", LibraryWorkspaceLayout.historyPanel(v));
        tabs.addTab("遗失赔偿", LibraryWorkspaceLayout.compensationPanel(v));
        tabs.setToolTipTextAt(0, v.manager ? "查询馆藏并录入新书" : "查询馆藏并选择一本或多本图书借阅");
        tabs.setToolTipTextAt(1, v.manager ? "查看全部借阅流水并办理归还" : "查看本人记录并归还图书");
        tabs.setToolTipTextAt(2, v.manager ? "查看全校遗失赔偿单，不能代扣读者余额" : "核对原价赔偿单并使用校园钱包支付");
        tabs.getAccessibleContext().setAccessibleName(v.manager ? "图书管理功能" : "读者图书馆功能");
        return tabs;
    }

    /** 构建馆藏检索与角色对应的借阅或维护操作。 */
    static JPanel catalogPanel(LibraryViewState v) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);

        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(LibraryWidgets.sectionHeading(v.manager ? "馆藏维护" : "馆藏检索",
                v.manager ? "按书名、作者、ISBN 或分类定位馆藏，也可录入新书。"
                        : "支持书名、作者、ISBN 等关键词搜索；可多选后一次借阅。"), BorderLayout.NORTH);
        JPanel search = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        search.setName("librarySearchActions");
        search.setOpaque(false);
        VCampusTheme.field(v.keywordField);
        VCampusTheme.field(v.categoryField);
        v.keywordField.setFont(VCampusTheme.font(Font.PLAIN, 14));
        v.categoryField.setFont(VCampusTheme.font(Font.PLAIN, 14));
        search.add(LibraryWidgets.searchField("关键词", v.keywordField));
        search.add(LibraryWidgets.searchField("分类", v.categoryField));
        VCampusTheme.secondaryButton(v.searchButton);
        VCampusTheme.secondaryButton(v.resetSearchButton);
        search.add(v.searchButton);
        search.add(v.resetSearchButton);
        top.add(search, BorderLayout.CENTER);

        LibraryTableStyles.configureBookTable(v);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(v.detailButton);
        actions.add(v.detailButton);
        if (v.manager) {
            VCampusTheme.primaryButton(v.addBookButton);
            actions.add(v.addBookButton);
            VCampusTheme.secondaryButton(v.restockButton);
            actions.add(v.restockButton);
            VCampusTheme.secondaryButton(v.editBookButton);
            actions.add(v.editBookButton);
            VCampusTheme.secondaryButton(v.copiesButton);
            actions.add(v.copiesButton);
        } else {
            VCampusTheme.primaryButton(v.borrowButton);
            actions.add(v.borrowButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(v.bookTable), BorderLayout.CENTER);
        return panel;
    }

    /** 构建本人借阅或全校流通列表及归还操作。 */
    static JPanel historyPanel(LibraryViewState v) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);
        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(LibraryWidgets.sectionHeading(v.manager ? "借阅流通记录" : "我的借阅记录",
                v.manager ? "查看全校借阅流水及实体册；旧记录标记迁移回填，选择在借记录可代还。"
                        : "书名保留借阅时快照，旧记录标记迁移回填；选择借阅中记录即可归还。"), BorderLayout.NORTH);
        LibraryTableStyles.configureHistoryTable(v);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(v.historyButton);
        VCampusTheme.primaryButton(v.returnButton);
        if (v.manager) {
            VCampusTheme.secondaryButton(v.allHistoryButton);
            actions.add(v.allHistoryButton);
        } else {
            actions.add(v.historyButton);
        }
        actions.add(v.returnButton);
        if (v.manager) {
            VCampusTheme.secondaryButton(v.declareLossButton);
            actions.add(v.declareLossButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(v.historyTable), BorderLayout.CENTER);
        return panel;
    }

    /** 构建赔偿记录及本人付款操作，管理员没有代扣按钮。 */
    static JPanel compensationPanel(LibraryViewState v) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);
        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(LibraryWidgets.sectionHeading(v.manager ? "全校遗失赔偿" : "我的遗失赔偿",
                v.manager ? "在流通管理中确认遗失，按服务器馆藏原价生成赔偿单；由借阅人本人确认支付，管理员不能代扣。"
                        : "核对馆员确认的遗失记录和原价金额后，再使用与商店共用的校园钱包支付。"), BorderLayout.NORTH);
        if (!v.manager) {
            v.walletBalanceLabel.setName("libraryWalletBalance");
            VCampusTheme.statusPill(v.walletBalanceLabel, VCampusTheme.MUTED);
            top.add(v.walletBalanceLabel, BorderLayout.CENTER);
        }
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        if (v.manager) v.refreshCompensationsButton.setText("刷新赔偿记录");
        VCampusTheme.secondaryButton(v.refreshCompensationsButton);
        actions.add(v.refreshCompensationsButton);
        if (!v.manager) {
            VCampusTheme.primaryButton(v.payCompensationButton);
            VCampusTheme.secondaryButton(v.rechargeHelpButton);
            actions.add(v.payCompensationButton);
            actions.add(v.rechargeHelpButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        LibraryTableStyles.configureTable(v.compensationTable, ListSelectionModel.SINGLE_SELECTION);
        v.compensationTable.setName("libraryCompensationTable");
        v.compensationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        LibraryTableStyles.applyColumnWidths(v.compensationTable, new int[] {135, 190, 88, 110, 120, 88, 160, 160});
        v.compensationTable.getColumnModel().getColumn(5).setCellRenderer(new LibraryTableStyles.CompensationStatusRenderer());
        v.compensationTable.moveColumn(1, 0);
        // Keep the book, amount and payment state visible together on a narrow window.
        v.compensationTable.moveColumn(4, 1);
        v.compensationTable.moveColumn(5, 2);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(v.compensationTable), BorderLayout.CENTER);
        return panel;
    }
}
