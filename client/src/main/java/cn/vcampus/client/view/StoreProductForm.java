package cn.vcampus.client.view;

import cn.vcampus.store.Product;
import java.awt.Component;
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

        while (true) {
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            addField(form, "商品名称*", nameField);
            addField(form, "单价（元）*", priceField);
            if (editing) {
                form.add(new JLabel("库存"));
                form.add(new JLabel("库存请用「补货」调整，此处不可改"));
            } else {
                addField(form, "初始库存*", stockField);
            }
            addField(form, "类别*", categoryField);
            addField(form, "说明", descriptionField);

            if (JOptionPane.showConfirmDialog(parent, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return null;
            }

            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            String description = descriptionField.getText().trim();
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException invalidPrice) {
                JOptionPane.showMessageDialog(parent, "单价必须是数字，例如 12.50", "填写有误",
                        JOptionPane.ERROR_MESSAGE);
                continue;
            }
            // 归一到分再转回元，避免 12.999 这类超精度输入与表格显示不一致
            price = StoreRowMapper.toYuan(StoreRowMapper.toCents(price));
            if (price <= 0) {
                JOptionPane.showMessageDialog(parent, "单价必须大于 0", "填写有误", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "请填写商品名称", "填写有误", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (category.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "请填写商品类别", "填写有误", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            int stock = 0;
            if (!editing) {
                try {
                    stock = Integer.parseInt(stockField.getText().trim());
                } catch (NumberFormatException invalidStock) {
                    JOptionPane.showMessageDialog(parent, "初始库存必须是整数", "填写有误",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                if (stock < 0) {
                    JOptionPane.showMessageDialog(parent, "初始库存不能为负", "填写有误",
                            JOptionPane.ERROR_MESSAGE);
                    continue;
                }
            }
            return new StoreProductForm(name, price, stock, description, category);
        }
    }

    private static void addField(JPanel form, String label, JTextField field) {
        form.add(new JLabel(label));
        form.add(field);
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
