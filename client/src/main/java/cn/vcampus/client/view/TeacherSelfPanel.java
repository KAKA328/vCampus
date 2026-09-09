package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.*;

/** Only the authenticated teacher's own archive is displayed. */
final class TeacherSelfPanel extends JPanel {
    interface Loader { Message load() throws Exception; }
    private final Loader loader;
    private final JTextField[] values = new JTextField[6];
    private final JLabel status = new JLabel("进入页面后自动加载本人教师档案。");
    private final JButton refresh = new JButton("刷新个人信息");
    private long version;
    private boolean initialLoadStarted;
    private final JLabel identity = new JLabel("教师个人档案");
    private final JLabel employment = new JLabel("待加载");

    TeacherSelfPanel(String host, int port, Session session) {
        this(() -> {
            try (RemoteStudentService remote = new RemoteStudentService(host, port)) {
                return remote.currentTeacher(session.getToken());
            }
        });
    }

    TeacherSelfPanel(Loader loader) {
        this.loader = loader;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        JLabel title = new JLabel("我的教师信息");
        title.setFont(VCampusTheme.font(java.awt.Font.BOLD, 24));
        add(title, BorderLayout.NORTH);
        JPanel body = new ScrollablePagePanel(new BorderLayout(0, 12));
        JPanel form = new JPanel(new GridLayout(6, 2, 12, 12));
        VCampusTheme.panel(form);
        String[] labels = {"教师工号", "绑定账号", "姓名", "院系", "职称", "在职情况"};
        for (int i = 0; i < values.length; i++) {
            JLabel label = new JLabel(labels[i]);
            form.add(label);
            values[i] = new JTextField();
            values[i].setEditable(false);
            VCampusTheme.field(values[i]);
            label.setLabelFor(values[i]);
            form.add(values[i]);
        }
        JPanel identityCard = new JPanel(new BorderLayout(14, 8));
        VCampusTheme.panel(identityCard);
        identity.setFont(VCampusTheme.font(java.awt.Font.BOLD, 22));
        identityCard.add(identity, BorderLayout.CENTER);
        VCampusTheme.statusPill(employment, VCampusTheme.MUTED);
        identityCard.add(employment, BorderLayout.EAST);
        JLabel scope = new JLabel("仅展示本人信息 · 档案由管理员维护");
        scope.setForeground(VCampusTheme.MUTED);
        identityCard.add(scope, BorderLayout.SOUTH);
        JPanel profile = new JPanel(new BorderLayout(0, 12));
        profile.setOpaque(false);
        profile.add(identityCard, BorderLayout.NORTH);
        profile.add(form, BorderLayout.CENTER);
        body.add(profile, BorderLayout.NORTH);
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.setOpaque(false);
        VCampusTheme.primaryButton(refresh);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false); actions.add(refresh);
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.CENTER);
        add(VCampusTheme.pageScroll(body), BorderLayout.CENTER);
        refresh.addActionListener(event -> reload());
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing()) {
                loadIfNeeded(true);
            }
        });
    }

    void loadIfNeeded(boolean showing) {
        if (showing && !initialLoadStarted) {
            initialLoadStarted = true;
            reload();
        }
    }

    void reload() {
        final long current = ++version;
        clear();
        refresh.setEnabled(false);
        status.setText("正在加载本人信息…");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { return loader.load(); }
            @Override protected void done() {
                if (current != version) return;
                refresh.setEnabled(true);
                try { showResponse(get()); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    status.setText("查询已中断，请重试。");
                } catch (Exception failure) { status.setText("查询失败，请检查连接后重试。"); }
            }
        }.execute();
    }

    void showResponse(Message response) {
        clear();
        if (response != null && response.getStatusCode() == StatusCode.OK
                && response.getPayload() instanceof TeacherProfile) {
            TeacherProfile profile = (TeacherProfile) response.getPayload();
            identity.setText(profile.getTeacherName());
            employment.setText(profile.isActive() ? "在职" : "非在职");
            VCampusTheme.statusPill(employment, profile.isActive() ? VCampusTheme.PRIMARY : VCampusTheme.MUTED);
            String[] data = {profile.getTeacherId(), profile.getUserId(), profile.getTeacherName(),
                    profile.getDepartmentName(), profile.getTitle(), profile.isActive() ? "在职" : "非在职"};
            for (int i = 0; i < values.length; i++) values[i].setText(data[i] == null ? "未填写" : data[i]);
            status.setText("本人档案只读；如需更正，请联系管理员。");
        } else if (response != null && response.getStatusCode() == StatusCode.NOT_FOUND) {
            status.setText("当前账号未绑定教师档案，请联系管理员。");
        } else if (response != null && response.getStatusCode() == StatusCode.UNAUTHORIZED) {
            status.setText("登录已失效，请重新登录。");
        } else { status.setText("无法查询本人教师档案，请联系管理员或重试。"); }
    }

    private void clear() {
        for (JTextField value : values) value.setText("");
        identity.setText("教师个人档案"); employment.setText("待加载");
        VCampusTheme.statusPill(employment, VCampusTheme.MUTED);
    }

    @Override public void removeNotify() {
        version++;
        clear();
        refresh.setEnabled(true);
        super.removeNotify();
    }
}
