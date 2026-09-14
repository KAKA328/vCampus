package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** 图书馆页面骨架、身份说明和概览卡片的布局。 */
final class LibraryPageLayout {
    /** 组装图书馆页面布局并绑定一次操作监听器。 */
    static void build(LibraryViewState v) {
        v.panel.setLayout(new BorderLayout(0, UiMetrics.px(16)));
        v.panel.setOpaque(false);
        v.panel.add(LibraryPageLayout.header(v), BorderLayout.NORTH);
        v.panel.add(VCampusTheme.pageScroll(LibraryPageLayout.body(v)), BorderLayout.CENTER);
        v.panel.add(LibraryPageLayout.statusPanel(v), BorderLayout.SOUTH);

        LibraryViewBindings.bind(v);
    }

    /** 构建概览与三个任务工作区。 */
    static JPanel body(LibraryViewState v) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
        panel.setOpaque(false);
        panel.add(LibraryPageLayout.overviewPanel(v), BorderLayout.NORTH);

        JTabbedPane tabs = LibraryWorkspaceLayout.tabs(v);
        tabs.setMinimumSize(UiMetrics.dimension(0, 340));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    /** 构建馆藏、库存、在借数量和提醒区域。 */
    static JPanel overviewPanel(LibraryViewState v) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        panel.setOpaque(false);
        panel.add(LibraryPageLayout.summaryCards(v), BorderLayout.NORTH);
        panel.add(LibraryPageLayout.reminderPanel(v), BorderLayout.SOUTH);
        return panel;
    }

    /** 构建到期提醒及定位、今日已读操作区域。 */
    static JPanel reminderPanel(LibraryViewState v) {
        JPanel panel = v.reminderCard;
        panel.setName("libraryReminderCard");
        VCampusTheme.panel(panel);
        JLabel heading = new JLabel(v.manager ? "全校流通提醒" : "我的到期提醒");
        heading.setFont(VCampusTheme.font(Font.BOLD, 13));
        heading.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(v.dueReminder, VCampusTheme.MUTED);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(v.dueReminder, BorderLayout.CENTER);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(v.handleReminderButton);
        VCampusTheme.secondaryButton(v.acknowledgeReminderButton);
        v.handleReminderButton.setText(v.manager ? "去处理归还" : "去归还");
        actions.add(v.handleReminderButton);
        if (!v.manager) actions.add(v.acknowledgeReminderButton);
        v.acknowledgeReminderButton.setToolTipText("收起本机今日已读提醒，不改变借阅状态；次日或出现新提醒时重新提示");
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    /** 构建可换行的操作结果提示。 */
    static JPanel statusPanel(LibraryViewState v) {
        JPanel statusPanel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(statusPanel);
        JLabel label = new JLabel("操作状态");
        label.setFont(VCampusTheme.font(Font.BOLD, 13));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(v.status, VCampusTheme.MUTED);
        statusPanel.add(label, BorderLayout.NORTH);
        statusPanel.add(v.status, BorderLayout.CENTER);
        return statusPanel;
    }

    /** 显示图书馆标题和当前真实登录角色。 */
    static JPanel header(LibraryViewState v) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        panel.setOpaque(false);
        JPanel text = LibraryWidgets.sectionHeading(v.manager ? "图书管理工作台" : "图书馆",
                v.manager ? "维护馆藏与全校借阅流转记录" : "查找馆藏、办理借阅并管理个人借阅记录");
        String capabilities = v.manager
                ? "管理员 · " + v.session.getUser().getDisplayName()
                : "读者 · " + v.session.getUser().getDisplayName();
        JLabel role = new LibraryWrappingLabel(capabilities);
        VCampusTheme.statusPill(role, v.manager ? VCampusTheme.PRIMARY : VCampusTheme.SUCCESS);
        panel.add(text, BorderLayout.CENTER);
        panel.add(role, BorderLayout.SOUTH);
        return panel;
    }

    /** 构建馆藏种类、可借数量和在借数量概览。 */
    static JPanel summaryCards(LibraryViewState v) {
        ResponsiveCardRowPanel row = new ResponsiveCardRowPanel(UiMetrics.px(210), UiMetrics.px(12));
        row.add(LibraryWidgets.metricCard(v.manager ? "馆藏种类" : "检索结果", v.catalogCountValue,
                "本次查询返回的图书种类", VCampusTheme.PRIMARY));
        row.add(LibraryWidgets.metricCard("可借册数", v.availableCountValue,
                "当前结果中的可借总量", VCampusTheme.SUCCESS));
        row.add(LibraryWidgets.metricCard("借阅中", v.activeLoanCountValue,
                v.manager ? "当前查看范围内未归还" : "本人尚未归还的记录", VCampusTheme.ACCENT));
        return row;
    }
}
