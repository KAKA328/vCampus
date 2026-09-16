package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ImageIcon;

/** Local, role-aware navigation helper. No network or model API is required. */
final class AssistantPanel extends JPanel {
    private final List<ModuleDescriptor> modules;
    private final Consumer<ModuleDescriptor> openModule;
    private final JPanel answers = new ScrollablePagePanel(null);
    private JPanel card;
    private JButton avatar;
    private boolean introduced;
    private int avatarSize = UiMetrics.px(64);
    private final JTextField question = new JTextField();

    AssistantPanel(Role role, List<ModuleDescriptor> modules, Consumer<ModuleDescriptor> openModule) {
        this.modules = modules;
        this.openModule = openModule;
        setOpaque(false);
        setLayout(new BorderLayout(0, UiMetrics.px(8)));
        avatar = new JButton();
        avatar.setToolTipText("打开小松鼠助手");
        avatar.getAccessibleContext().setAccessibleName("打开小松鼠助手");
        VCampusTheme.primaryButton(avatar);
        avatar.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.ACCENT, 18), VCampusTheme.padding(5, 5, 5, 5)));
        refreshAvatar("assistant-idle.png");
        avatar.addActionListener(e -> toggleCard());
        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        avatarRow.setOpaque(false);
        avatarRow.add(avatar);
        add(avatarRow, BorderLayout.SOUTH);
        card = card(role);
        add(card, BorderLayout.CENTER);
        setPreferredSize(UiMetrics.dimension(320, 320));
    }

    void resizeForWindow(int width, int height) {
        avatarSize = Math.max(UiMetrics.px(52), Math.min(UiMetrics.px(76), Math.min(width, height) / 12));
        refreshAvatar(card.isVisible() ? "assistant-active.png" : "assistant-idle.png");
        int cardWidth = Math.max(UiMetrics.px(272), Math.min(UiMetrics.px(360),
                Math.max(UiMetrics.px(272), width - UiMetrics.px(600))));
        card.setPreferredSize(new Dimension(cardWidth, UiMetrics.px(246)));
        setPreferredSize(new Dimension(cardWidth, avatarSize + UiMetrics.px(260)));
        revalidate();
    }

    private JPanel card(Role role) {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        card.setPreferredSize(UiMetrics.dimension(320, 246));
        card.setVisible(false);
        JLabel title = new JLabel("小松鼠助手");
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("当前身份：" + roleLabel(role));
        subtitle.setFont(VCampusTheme.font(Font.PLAIN, 12));
        subtitle.setForeground(VCampusTheme.MUTED);
        JPanel titleCopy = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        titleCopy.setOpaque(false);
        titleCopy.add(title, BorderLayout.NORTH);
        titleCopy.add(subtitle, BorderLayout.SOUTH);
        JButton close = new JButton("×");
        close.setToolTipText("收起助手");
        close.getAccessibleContext().setAccessibleName("收起小松鼠助手");
        VCampusTheme.secondaryButton(close);
        close.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.BORDER),
                VCampusTheme.padding(4, 8, 4, 8)));
        close.setPreferredSize(UiMetrics.dimension(40, 34));
        close.addActionListener(e -> collapse());
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.add(titleCopy, BorderLayout.CENTER);
        titleBar.add(close, BorderLayout.EAST);
        card.add(titleBar, BorderLayout.NORTH);

        answers.setOpaque(false);
        answers.setLayout(new BoxLayout(answers, BoxLayout.Y_AXIS));
        JScrollPane scroll = VCampusTheme.pageScroll(answers);
        scroll.setPreferredSize(UiMetrics.dimension(280, 116));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(UiMetrics.px(6), 0));
        input.setOpaque(false);
        question.setToolTipText("例如：怎么退课、有哪些功能");
        question.getAccessibleContext().setAccessibleName("向小松鼠助手提问");
        VCampusTheme.field(question);
        JButton ask = new JButton("发送");
        VCampusTheme.primaryButton(ask);
        ask.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.PRIMARY_DARK, 2),
                VCampusTheme.padding(7, 13, 7, 13)));
        ask.setPreferredSize(UiMetrics.dimension(76, 38));
        ask.addActionListener(e -> answer(question.getText()));
        question.addActionListener(e -> answer(question.getText()));
        input.add(question, BorderLayout.CENTER);
        input.add(ask, BorderLayout.EAST);
        card.add(input, BorderLayout.SOUTH);
        return card;
    }

    private void addSuggestions() {
        JLabel hint = new JLabel("常用问题");
        hint.setFont(VCampusTheme.font(Font.BOLD, 12));
        hint.setForeground(VCampusTheme.MUTED);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        answers.add(hint);
        JPanel shortcuts = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(6), UiMetrics.px(4)));
        shortcuts.setOpaque(false);
        shortcuts.setAlignmentX(LEFT_ALIGNMENT);
        String[] labels = {"我想选课", "查看学籍", "办理借阅", "进入商店", "哪些功能当前可用"};
        for (String label : labels) {
            JButton button = new JButton(label);
            VCampusTheme.secondaryButton(button);
            button.setFont(VCampusTheme.font(Font.PLAIN, 12));
            button.setBorder(VCampusTheme.padding(5, 9, 5, 9));
            button.addActionListener(e -> answer(label));
            shortcuts.add(button);
        }
        answers.add(shortcuts);
    }

    private void answer(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;
        String normalized = text.toLowerCase(Locale.ROOT);
        // 管理员问法：账号、权限、密码和审计都属于用户管理入口。
        if (route(normalized, new String[] {"账号", "账户", "密码", "重置", "登录", "注销", "启用", "停用",
                "角色", "权限", "批量导入", "导入账号", "账号导入", "审计", "操作日志", "日志", "会话", "绑定档案"},
                new String[] {"用户管理"}, "账号、密码、权限、批量导入和操作日志都在用户管理中办理。", "用户管理")) return;

        // 教师个人档案和教务管理员的档案查询共用一组问法，按当前角色选择可用入口。
        if (route(normalized, new String[] {"教师信息", "教师档案", "教师工号", "工号", "职称", "在职", "非在职",
                "教师资料"}, new String[] {"教师信息", "学籍管理", "教务教学管理"},
                "教师工号、职称、院系和在职状态可以在教师/学籍档案入口查看。", "教师信息")) return;

        if (route(normalized, new String[] {"学籍信息", "学生档案", "学号", "班级", "专业", "联系方式", "手机号",
                "邮箱", "入学", "学籍状态", "在读", "休学", "退学", "学生资料"},
                new String[] {"学籍信息", "学籍管理"}, "学号、班级、专业、学籍状态和联系方式可以在学籍入口查看。", "学籍")) return;

        if (route(normalized, new String[] {"毕业", "学分审查", "毕业审查", "毕业办理", "学业审查", "毕业条件"},
                new String[] {"学籍管理", "教务教学管理"}, "毕业审查、学分核对和毕业办理在学籍管理入口完成。", "学籍管理")) return;

        if (route(normalized, new String[] {"课程目录", "课程维护", "教学班管理", "开课", "选课轮次", "轮次管理",
                "培养方案管理", "成绩审核", "审核成绩", "教务", "教务管理", "课程管理"},
                new String[] {"教务教学管理"}, "课程目录、教学班、选课轮次、培养方案和成绩审核在教务教学管理中办理。",
                "教务教学管理")) return;

        if (route(normalized, new String[] {"教学班", "授课", "学生名单", "成绩录入", "录入成绩", "导入成绩",
                "成绩草稿", "提交审核", "教师成绩", "成绩单"},
                new String[] {"教学管理", "教务教学管理", "学生选课"},
                "教学班、学生名单、成绩录入和成绩导入在教学管理入口办理。", "教学管理")) return;

        if (route(normalized, new String[] {"退课", "退选", "取消课程", "选课", "课程", "课表", "重修", "选修",
                "必修", "课程历史", "已选", "退选记录", "上课", "成绩"},
                new String[] {"学生选课", "教学管理", "教务教学管理"},
                "课程查询、选课、退课、重修和已选记录在选课入口办理。", "选课")) return;

        if (route(normalized, new String[] {"借阅", "图书", "还书", "归还", "馆藏", "借书", "续借", "逾期",
                "挂失", "遗失", "赔偿", "图书管理员", "借阅记录"}, new String[] {"图书馆"},
                "图书馆支持馆藏查询、借阅、归还、逾期、挂失赔偿和借阅记录查询。", "图书馆")) return;

        if (route(normalized, new String[] {"补货", "上架", "下架", "商品维护", "商品管理", "库存管理", "余额校正",
                "全量订单", "全部订单", "商品新增", "改价", "修改商品"}, new String[] {"商店管理", "商店"},
                "商店管理支持商品维护、库存补货、上下架、全部订单和余额校正。", "商店管理")) return;

        if (route(normalized, new String[] {"商店", "购买", "商品", "订单", "购物", "购物车", "结算", "钱包", "余额",
                "流水", "充值", "消费", "付款", "下单"}, new String[] {"商店", "商店管理"},
                "商店支持商品查询、购买、购物车结算、订单和校园钱包流水。", "商店")) return;

        if (normalized.contains("可用") || normalized.contains("功能") || normalized.contains("入口")) {
            showMessage("当前角色可用：" + joinTitles());
        } else {
            showMessage("很抱歉，我暂时无法匹配这个问题。你可以询问账号、学籍、选课、成绩、培养方案、图书馆、商店或钱包相关功能。");
        }
    }

    private boolean route(String normalized, String[] keywords, String[] moduleFragments,
            String reply, String unavailableTarget) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                ModuleDescriptor module = findModule(moduleFragments);
                if (module == null) {
                    showMessage("当前角色没有可用的“" + unavailableTarget + "”入口。");
                } else {
                    showMessage(reply, module);
                }
                return true;
            }
        }
        return false;
    }

    private ModuleDescriptor findModule(String[] fragments) {
        for (String fragment : fragments) {
            for (ModuleDescriptor module : modules) {
                if (module.getTitle().contains(fragment)) return module;
            }
        }
        return null;
    }

    private String joinTitles() {
        StringBuilder result = new StringBuilder();
        for (ModuleDescriptor module : modules) {
            if (result.length() > 0) result.append("、");
            result.append(module.getTitle());
        }
        return result.length() == 0 ? "暂无模块" : result.toString();
    }

    private void showMessage(String message) {
        showMessage(message, null);
    }

    private void showMessage(String message, ModuleDescriptor module) {
        answers.removeAll();
        JLabel label = new JLabel("<html><div style='width:" + UiMetrics.px(240)
                + "px;line-height:1.7;padding:4px 2px;'>" + message + "</div></html>");
        label.setForeground(VCampusTheme.TEXT);
        label.setBorder(VCampusTheme.padding(4, 0, 8, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        answers.add(label);
        if (module != null) {
            JButton open = new JButton("打开“" + module.getTitle() + "”");
            VCampusTheme.primaryButton(open);
            open.setAlignmentX(LEFT_ALIGNMENT);
            open.addActionListener(e -> {
                openModule.accept(module);
                resetConversation();
                collapse();
            });
            answers.add(open);
        }
        answers.revalidate();
        answers.repaint();
    }


    private void toggleCard() {
        card.setVisible(!card.isVisible());
        refreshAvatar(card.isVisible() ? "assistant-active.png" : "assistant-idle.png");
        if (card.isVisible() && !introduced) {
            introduced = true;
            answers.removeAll();
            JLabel welcome = new JLabel("<html><div style='width:" + UiMetrics.px(240)
                    + "px;line-height:1.6;'>你好，我是小松鼠叮东。我可以帮你快速找到校园功能入口。</div></html>");
            welcome.setForeground(VCampusTheme.TEXT);
            welcome.setBorder(VCampusTheme.padding(0, 0, 8, 0));
            welcome.setAlignmentX(LEFT_ALIGNMENT);
            answers.add(welcome);
            addSuggestions();
        }
        revalidate();
        repaint();
    }

    private void collapse() {
        card.setVisible(false);
        refreshAvatar("assistant-idle.png");
        revalidate();
        repaint();
    }

    private void refreshAvatar(String resourceName) {
        avatar.setPreferredSize(new Dimension(avatarSize, avatarSize));
        java.net.URL iconUrl = AssistantPanel.class.getResource("/assistant/" + resourceName);
        if (iconUrl == null) {
            avatar.setText("助手");
            return;
        }
        int iconSize = Math.max(UiMetrics.px(36), avatarSize - UiMetrics.px(10));
        java.awt.Image image = new ImageIcon(iconUrl).getImage()
                .getScaledInstance(iconSize, iconSize, java.awt.Image.SCALE_SMOOTH);
        avatar.setText("");
        avatar.setIcon(new ImageIcon(image));
    }

    private static String roleLabel(Role role) {
        if (role == Role.ADMIN) return "系统管理员";
        if (role == Role.ACADEMIC_ADMIN) return "教务管理员";
        if (role == Role.STORE_MANAGER) return "商店管理员";
        if (role == Role.LIBRARIAN) return "图书管理员";
        if (role == Role.TEACHER) return "教师";
        return "学生";
    }

    private void resetConversation() {
        introduced = false;
        question.setText("");
        answers.removeAll();
        answers.revalidate();
        answers.repaint();
    }
}
