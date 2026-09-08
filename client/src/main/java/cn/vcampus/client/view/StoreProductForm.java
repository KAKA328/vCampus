package cn.vcampus.client.view;

import cn.vcampus.store.Product;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * 商品新增/编辑表单对话框，沿用项目既有的 GridLayout 表单 + JOptionPane 确认范式。
 * 新增模式收集库存，编辑模式不收库存：服务端 updateProduct 不接受库存参数，库存只能由补货调整，
 * 这是商店模块「库存管理与商品信息更新职责分离」的既定约束，界面必须如实反映而不是假装可改。
 * 校验失败时重新弹出对话框并保留已填内容，避免用户因一个字段填错就重填整张表单。
 */
final class StoreProductForm {
    private final String name;
    private final double price;
    private final int stock;
    private final String description;
    private final String category;

    private StoreProductForm(String name, double price, int stock, String description, String category) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.category = category;
    }

    /** 新增商品表单；用户取消返回 null。 */
    static StoreProductForm showAdd(Component parent) {
        return show(parent, "新增商品", null);
    }

    /** 编辑商品表单，字段预填当前值；用户取消返回 null。 */
    static StoreProductForm showEdit(Component parent, Product product) {
        return show(parent, "编辑商品", product);
    }

    private static StoreProductForm show(Component parent, String title, Product existing) {
        boolean editing = existing != null;
        // 输入框在循环外创建：校验失败重弹时只换容器不换控件，已填文本得以保留
        JTextField nameField = new JTextField(editing ? existing.getName() : "", 18);
        JTextField priceField = new JTextField(
                editing ? StoreRowMapper.formatYuan(StoreRowMapper.toCents(existing.getPrice())) : "", 10);
        JTextField stockField = new JTextField("1", 8);
        JTextField categoryField = new JTextField(editing ? existing.getCategory() : "", 12);
        JTextField descriptionField = new JTextField(editing ? nullToEmpty(existing.getDescription()) : "", 18);
        // 输入框统一圆角边 + 主题字号，与商店页其它输入控件一致
        for (JTextField field : new JTextField[] { nameField, priceField, stockField, categoryField,
                descriptionField }) {
            VCampusTheme.roundedField(field);
            field.setFont(VCampusTheme.font(Font.PLAIN, 14));
        }

        while (true) {
            JPanel form = new JPanel(new GridLayout(0, 2, UiMetrics.px(16), UiMetrics.px(10)));
            form.setOpaque(false);
            addField(form, "商品名称*", nameField);
            addField(form, "单价（元）*", priceField);
            if (editing) {
                form.add(fieldLabel("库存"));
                JLabel stockNote = new JLabel("库存请用「补货」调整，此处不可改");
                stockNote.setFont(VCampusTheme.font(Font.PLAIN, 13));
                stockNote.setForeground(VCampusTheme.MUTED);
                form.add(stockNote);
            } else {
                addField(form, "初始库存*", stockField);
            }
            addField(form, "类别*", categoryField);
            addField(form, "说明", descriptionField);

            if (StorePanel.showThemedDialog(parent, title, StorePanel.dialogBody(title, form),
                    true) != JOptionPane.OK_OPTION) {
                return null;
            }

            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            String description = descriptionField.getText().trim();
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException invalidPrice) {
                showError(parent, "单价必须是数字，例如 12.50");
                continue;
            }
            // 归一到分再转回元，避免 12.999 这类超精度输入与表格显示不一致
            price = StoreRowMapper.toYuan(StoreRowMapper.toCents(price));
            if (price <= 0) {
                showError(parent, "单价必须大于 0");
                continue;
            }
            if (name.isEmpty()) {
                showError(parent, "请填写商品名称");
                continue;
            }
            if (category.isEmpty()) {
                showError(parent, "请填写商品类别");
                continue;
            }

            int stock = 0;
            if (!editing) {
                try {
                    stock = Integer.parseInt(stockField.getText().trim());
                } catch (NumberFormatException invalidStock) {
                    showError(parent, "初始库存必须是整数");
                    continue;
                }
                if (stock < 0) {
                    showError(parent, "初始库存不能为负");
                    continue;
                }
            }
            return new StoreProductForm(name, price, stock, description, category);
        }
    }

    private static void addField(JPanel form, String label, JTextField field) {
        form.add(fieldLabel(label));
        form.add(field);
    }

    private static JLabel fieldLabel(String label) {
        JLabel key = new JLabel(label);
        key.setFont(VCampusTheme.font(Font.PLAIN, 13));
        key.setForeground(VCampusTheme.MUTED);
        return key;
    }

    /** 校验失败提示：走商店统一主题对话框，替掉默认错误弹窗。 */
    private static void showError(Component parent, String message) {
        StorePanel.showThemedDialog(parent, "填写有误",
                StorePanel.dialogBody("填写有误", StorePanel.dialogMessage(message)), false);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    String getName() {
        return name;
    }

    double getPrice() {
        return price;
    }

    /** 仅新增模式有意义；编辑模式恒为 0，调用方不得据此提交库存。 */
    int getStock() {
        return stock;
    }

    String getDescription() {
        return description;
    }

    String getCategory() {
        return category;
    }
}
