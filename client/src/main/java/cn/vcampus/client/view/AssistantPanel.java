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
    private final JPanel answers = new JPanel();
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
        close.setPreferredSize(UiMetrics.dimension(34, 30));
        VCampusTheme.secondaryButton(close);
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
        ask.setPreferredSize(UiMetrics.dimension(68, 38));
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
        String target = null;
        String reply = null;
        if (normalized.contains("退课") || normalized.contains("退选") || normalized.contains("取消课程")) { target = "选课"; reply = "退课需要进入选课系统，在已选课程中选择退课。"; }
        else if (normalized.contains("选课") || normalized.contains("课程") || normalized.contains("授课") || normalized.contains("教学班") || normalized.contains("课表") || normalized.contains("成绩") || normalized.contains("上课")) { target = "选课"; reply = "选课、课程查询、课表和授课信息都在选课系统中办理。"; }
        else if (normalized.contains("学籍") || normalized.contains("学生档案") || normalized.contains("班级") || normalized.contains("专业") || normalized.contains("学号") || normalized.contains("联系方式") || normalized.contains("学生信息")) { target = "学籍"; reply = "学号、班级、专业和联系方式等信息可以在学籍入口查看。"; }
        else if (normalized.contains("借阅") || normalized.contains("图书") || normalized.contains("还书") || normalized.contains("归还") || normalized.contains("馆藏") || normalized.contains("借书") || normalized.contains("续借")) { target = "图书"; reply = "图书馆支持馆藏查询、借阅、归还和借阅记录查询。"; }
        else if (normalized.contains("商店") || normalized.contains("购买") || normalized.contains("商品") || normalized.contains("订单") || normalized.contains("购物") || normalized.contains("库存") || normalized.contains("下单")) { target = "商店"; reply = "商店可以浏览商品、购买、下单并查看订单记录。"; }
        if (target != null) {
            for (ModuleDescriptor module : modules) {
                if (module.getTitle().contains(target)) {
                    showMessage(reply, module);
                    return;
                }
            }
            showMessage("当前角色没有可用的“" + target + "”入口。");
            return;
        }
        if (normalized.contains("可用") || normalized.contains("功能") || normalized.contains("入口")) {
            showMessage("当前角色可用：" + joinTitles());
        } else {
            showMessage("很抱歉，我不知道该怎么处理这句话。你可以询问选课、学籍、图书馆或商店相关功能。");
        }
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
