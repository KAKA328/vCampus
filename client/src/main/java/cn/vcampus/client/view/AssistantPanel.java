package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import java.awt.BorderLayout;
import java.awt.Color;
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
    private final JTextField question = new JTextField();

    AssistantPanel(Role role, List<ModuleDescriptor> modules, Consumer<ModuleDescriptor> openModule) {
        this.modules = modules;
        this.openModule = openModule;
        setOpaque(false);
        setLayout(new BorderLayout(0, 8));
        avatar = new JButton();
        avatar.setToolTipText("打开本地功能助手");
        avatar.setPreferredSize(new Dimension(72, 72));
        java.net.URL iconUrl = AssistantPanel.class.getResource("/assistant/assistant-idle.png");
        if (iconUrl != null) {
            java.awt.Image image = new ImageIcon(iconUrl).getImage().getScaledInstance(62, 62, java.awt.Image.SCALE_SMOOTH);
            avatar.setIcon(new ImageIcon(image));
        }
        avatar.setFont(VCampusTheme.font(Font.BOLD, 15));
        avatar.setBackground(new Color(239, 68, 68));
        avatar.setForeground(Color.WHITE);
        avatar.setBorder(BorderFactory.createLineBorder(new Color(185, 28, 28), 2));
        avatar.addActionListener(e -> toggleCard());
        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        avatarRow.setOpaque(false);
        avatarRow.add(avatar);
        add(avatarRow, BorderLayout.SOUTH);
        card = card(role);
        add(card, BorderLayout.CENTER);
        setPreferredSize(new Dimension(340, 420));
    }

    void resizeForWindow(int width, int height) {
        int size = Math.max(52, Math.min(88, Math.min(width, height) / 11));
        avatar.setPreferredSize(new Dimension(size, size));
        java.net.URL url = AssistantPanel.class.getResource("/assistant/assistant-idle.png");
        if (url != null) avatar.setIcon(new ImageIcon(new ImageIcon(url).getImage()
                .getScaledInstance(size - 10, size - 10, java.awt.Image.SCALE_SMOOTH)));
        int cardWidth = Math.max(300, Math.min(390, width / 3));
        card.setPreferredSize(new Dimension(cardWidth, 330));
        setPreferredSize(new Dimension(cardWidth + 10, size + 368));
        revalidate();
    }

    private JPanel card(Role role) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(330, 330));
        card.setVisible(false);
        JLabel title = new JLabel("校园助手 · " + role.name());
        title.setFont(VCampusTheme.font(Font.BOLD, 15));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        title.setBorder(VCampusTheme.padding(0, 0, 4, 0));
        JButton close = new JButton("×");
        close.setToolTipText("收起助手");
        close.setPreferredSize(new Dimension(34, 30));
        VCampusTheme.secondaryButton(close);
        close.addActionListener(e -> collapse());
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.add(title, BorderLayout.CENTER);
        titleBar.add(close, BorderLayout.EAST);
        card.add(titleBar, BorderLayout.NORTH);

        answers.setOpaque(false);
        answers.setLayout(new BoxLayout(answers, BoxLayout.Y_AXIS));
        addSuggestions();
        JScrollPane scroll = VCampusTheme.pageScroll(answers);
        scroll.setPreferredSize(new Dimension(290, 195));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(scroll, BorderLayout.CENTER);

        JPanel input = new JPanel(new BorderLayout(6, 0));
        input.setOpaque(false);
        question.setToolTipText("输入例如：怎么退课、有哪些功能");
        VCampusTheme.field(question);
        JButton ask = new JButton("提问");
        VCampusTheme.primaryButton(ask);
        ask.setPreferredSize(new Dimension(72, 42));
        ask.addActionListener(e -> answer(question.getText()));
        question.addActionListener(e -> answer(question.getText()));
        input.add(question, BorderLayout.CENTER);
        input.add(ask, BorderLayout.EAST);
        card.add(input, BorderLayout.SOUTH);
        return card;
    }

    private void addSuggestions() {
        String[] labels = {"我想选课", "查看学籍", "办理借阅", "进入商店", "哪些功能当前可用"};
        for (String label : labels) {
            JButton button = new JButton(label);
            VCampusTheme.secondaryButton(button);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.addActionListener(e -> answer(label));
            answers.add(button);
        }
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
        JLabel label = new JLabel("<html><div style='width:270px;line-height:1.7;padding:4px 2px;'>" + message + "</div></html>");
        label.setForeground(VCampusTheme.TEXT);
        answers.add(label);
        if (module != null) {
            JButton open = new JButton("打开“" + module.getTitle() + "”");
            VCampusTheme.primaryButton(open);
            open.setAlignmentX(LEFT_ALIGNMENT);
            open.addActionListener(e -> { openModule.accept(module); collapse(); });
            answers.add(open);
        }
        answers.revalidate();
        answers.repaint();
    }

    private void toggleCard() {
        card.setVisible(!card.isVisible());
        if (card.isVisible() && !introduced) {
            introduced = true;
            answers.removeAll();
            JLabel welcome = new JLabel("<html><div style='width:220px;line-height:1.6;'>你好，我是东南大学吉祥物叮东！我可以帮你查找选课、学籍、图书馆和商店功能入口。你可以点击下方快捷选项，或直接输入问题。</div></html>");
            welcome.setForeground(VCampusTheme.TEXT);
            answers.add(welcome);
            addSuggestions();
        }
        revalidate();
        repaint();
    }

    private void collapse() {
        card.setVisible(false);
        revalidate();
        repaint();
    }
}
