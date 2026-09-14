package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.MajorDirectoryEntry;
import java.awt.*;
import java.util.*;
import javax.swing.*;

/** Shared by inline plan forms and plan editor dialogs; never accepts free-text majors. */
final class MajorDirectorySelector extends JPanel {
    interface Loader { Message load() throws Exception; }
    private final Loader loader;
    private final Runnable changed;
    private final JComboBox<MajorDirectoryEntry> options = new JComboBox<>();
    private final JButton refresh = new JButton("刷新专业目录");
    private final JLabel status = new JLabel("专业目录尚未加载");
    private boolean ready;
    private boolean loading;
    private boolean interactive = true;
    private long generation;
    private String desiredName;

    MajorDirectorySelector(Loader loader, Runnable changed) {
        this.loader = Objects.requireNonNull(loader);
        this.changed = Objects.requireNonNull(changed);
        setLayout(new BorderLayout(0, UiMetrics.px(3)));
        setOpaque(false);
        options.setEditable(false);
        options.setPrototypeDisplayValue(new MajorDirectoryEntry("MAJOR", "计算机科学与技术", "院系", true));
        options.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                Component component = super.getListCellRendererComponent(list, value, index, selected, focus);
                setToolTipText(value instanceof MajorDirectoryEntry ? ((MajorDirectoryEntry) value).getDepartmentName() : null);
                return component;
            }
        });
        VCampusTheme.field(options);
        VCampusTheme.secondaryButton(refresh);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(4), 0));
        row.setOpaque(false);
        row.add(options); row.add(refresh);
        add(row, BorderLayout.NORTH);
        add(status, BorderLayout.SOUTH);
        refresh.addActionListener(event -> load());
        options.addActionListener(event -> {
            if (ready) {
                MajorDirectoryEntry entry = (MajorDirectoryEntry) options.getSelectedItem();
                if (entry != null) {
                    desiredName = entry.getMajorName();
                    status.setText(entry.getDepartmentName());
                    status.setForeground(VCampusTheme.MUTED);
                }
            }
            updateState();
        });
        updateState();
    }

    void load() {
        if (loading) return;
        final long current = ++generation;
        loading = true; ready = false;
        options.removeAllItems();
        status.setText("正在加载专业目录…");
        updateState();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { return loader.load(); }
            @Override protected void done() {
                if (current != generation) return;
                try { accept(get()); }
                catch (Exception failure) { fail("专业目录加载失败，请检查连接后刷新目录"); }
            }
        }.execute();
    }

    void accept(Message response) {
        ready = false; loading = false;
        options.removeAllItems();
        if (response == null || response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof java.util.List<?>)) {
            fail(response != null && response.getPayload() instanceof String
                    ? "专业目录不可用：" + response.getPayload() : "专业目录不可用，请刷新目录");
            return;
        }
        java.util.List<MajorDirectoryEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (Object value : (java.util.List<?>) response.getPayload()) {
            if (!(value instanceof MajorDirectoryEntry)) { fail("专业目录数据格式错误"); return; }
            MajorDirectoryEntry entry = (MajorDirectoryEntry) value;
            if (!entry.isActive() || !ids.add(entry.getMajorId()) || !names.add(entry.getMajorName())) {
                fail("专业目录含停用或重复专业，请联系管理员"); return;
            }
            entries.add(entry);
        }
        entries.sort(MajorDirectoryEntry.ORDER);
        for (MajorDirectoryEntry entry : entries) options.addItem(entry);
        options.setSelectedIndex(-1);
        if (entries.isEmpty()) { fail("暂无有效专业，无法保存培养方案"); return; }
        ready = true;
        selectName(desiredName);
    }

    void selectName(String name) {
        desiredName = name;
        options.setSelectedIndex(-1);
        if (!ready) { updateState(); return; }
        for (int i = 0; i < options.getItemCount(); i++) {
            if (options.getItemAt(i).getMajorName().equals(name)) {
                options.setSelectedIndex(i); return;
            }
        }
        status.setText(name == null ? "请选择专业" : "原专业“" + name + "”不在有效目录中，请重新选择");
        status.setForeground(name == null ? VCampusTheme.MUTED : VCampusTheme.DANGER);
        updateState();
    }

    String selectedName() {
        if (!canSave()) throw new IllegalArgumentException("请先加载有效专业目录并选择专业");
        return ((MajorDirectoryEntry) options.getSelectedItem()).getMajorName();
    }
    boolean canSave() { return ready && options.getSelectedItem() != null; }
    void setInteractive(boolean interactive) { this.interactive = interactive; updateControls(); }
    private void updateControls() {
        options.setEnabled(interactive && ready && !loading);
        refresh.setEnabled(interactive && !loading);
    }
    private void updateState() { updateControls(); changed.run(); }
    private void fail(String message) {
        ready = false; loading = false;
        options.removeAllItems();
        status.setText(message);
        status.setForeground(VCampusTheme.DANGER);
        updateState();
    }
    @Override public void removeNotify() {
        generation++; loading = false; ready = false;
        options.removeAllItems();
        updateState();
        super.removeNotify();
    }
}
