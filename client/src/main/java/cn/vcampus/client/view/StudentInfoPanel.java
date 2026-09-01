package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** Student record panel for self-service lookup and academic-admin maintenance. */
public final class StudentInfoPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "性别", "学院", "专业", "班级", "入学年份", "状态", "电话", "邮箱"
    });
    private final JTable table = new JTable(tableModel);
    private final JTextField studentId = new JTextField(12);
    private final JTextField name = new JTextField(10);
    private final JTextField gender = new JTextField(6);
    private final JTextField department = new JTextField(12);
    private final JTextField major = new JTextField(12);
    private final JTextField classId = new JTextField(10);
    private final JTextField enrollmentYear = new JTextField(6);
    private final JTextField statusField = new JTextField(8);
    private final JTextField phone = new JTextField(10);
    private final JTextField email = new JTextField(14);
    private final JLabel status = new JLabel("请选择查询方式");
    private final JButton selfButton = new JButton("我的学籍");
    private final JButton idButton = new JButton("按学号查询");
    private final JButton classButton = new JButton("按班级查询");
    private final JButton saveButton = new JButton("保存学籍");
    private boolean requestInProgress;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public StudentInfoPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(center(), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
        selfButton.addActionListener(event -> loadSelf());
        idButton.addActionListener(event -> loadById());
        classButton.addActionListener(event -> loadByClass());
        saveButton.addActionListener(event -> save());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) fillFormFromSelection();
        });
        updateButtonState();
        if (session.getUser().getRole() == Role.STUDENT) loadSelf();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel(title());
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName()
                + "；学生本人查看个人学籍，教务人员维护学生基础档案。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel center() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JPanel form = new JPanel(new GridLayout(0, 5, 10, 8));
        form.setOpaque(false);
        addField(form, "学号", studentId);
        addField(form, "姓名", name);
        addField(form, "性别", gender);
        addField(form, "学院", department);
        addField(form, "专业", major);
        addField(form, "班级", classId);
        addField(form, "入学年份", enrollmentYear);
        addField(form, "状态", statusField);
        addField(form, "电话", phone);
        addField(form, "邮箱", email);

        JPanel tablePanel = new JPanel(new BorderLayout());
        VCampusTheme.panel(tablePanel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        panel.add(form, BorderLayout.NORTH);
        panel.add(tablePanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel bottom() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(selfButton);
        VCampusTheme.secondaryButton(idButton);
        VCampusTheme.secondaryButton(classButton);
        VCampusTheme.primaryButton(saveButton);
        actions.add(selfButton);
        actions.add(idButton);
        actions.add(classButton);
        actions.add(saveButton);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void addField(JPanel form, String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel text = new JLabel(label);
        text.setForeground(VCampusTheme.MUTED);
        panel.add(text, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        form.add(panel);
    }

    private void loadSelf() {
        runRequest("正在查询我的学籍…", new StudentRequest() {
            @Override
            public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.self(session.getToken());
            }
        }, response -> showSingle(response));
    }

    private void loadById() {
        final String value = studentId.getText().trim();
        if (value.isEmpty()) {
            showStatus("请输入学号", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在按学号查询…", new StudentRequest() {
            @Override
            public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.findById(session.getToken(), value);
            }
        }, response -> showSingle(response));
    }

    private void loadByClass() {
        final String value = classId.getText().trim();
        if (value.isEmpty()) {
            showStatus("请输入班级", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在按班级查询…", new StudentRequest() {
            @Override
            public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.findByClass(session.getToken(), value);
            }
        }, response -> showList(response));
    }

    private void save() {
        final StudentRecord record;
        try {
            record = recordFromForm();
        } catch (IllegalArgumentException invalid) {
            showStatus(invalid.getMessage(), VCampusTheme.DANGER);
            return;
        }
        runRequest("正在保存学籍…", new StudentRequest() {
            @Override
            public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.save(session.getToken(), record);
            }
        }, response -> showSingle(response));
    }

    private void showSingle(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof StudentRecord)) {
            showStatus("服务器返回的学籍数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        StudentRecord record = (StudentRecord) response.getPayload();
        ArrayList<Object[]> rows = new ArrayList<Object[]>();
        rows.add(row(record));
        tableModel.replaceRows(rows);
        fillForm(record);
        showStatus("已显示学籍：" + record.getStudentId(), VCampusTheme.SUCCESS);
    }

    private void showList(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的学籍列表格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> records = (List<?>) response.getPayload();
        ArrayList<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : records) {
            if (!(item instanceof StudentRecord)) {
                showStatus("服务器返回的学籍列表格式不正确", VCampusTheme.DANGER);
                return;
            }
            rows.add(row((StudentRecord) item));
        }
        tableModel.replaceRows(rows);
        showStatus("已显示学籍记录，共 " + records.size() + " 条", VCampusTheme.SUCCESS);
    }

    private StudentRecord recordFromForm() {
        String year = enrollmentYear.getText().trim();
        int parsedYear = year.isEmpty() ? 0 : Integer.parseInt(year);
        if (studentId.getText().trim().isEmpty() || name.getText().trim().isEmpty()
                || classId.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("学号、姓名和班级不能为空");
        }
        return new StudentRecord(studentId.getText(), name.getText(), gender.getText(),
                department.getText(), major.getText(), classId.getText(), parsedYear,
                statusField.getText(), phone.getText(), email.getText());
    }

    private Object[] row(StudentRecord record) {
        return new Object[] {record.getStudentId(), record.getName(), record.getGender(),
                record.getDepartmentName(), record.getMajorName(), record.getClassId(),
                record.getEnrollmentYear(), record.getStatus(), record.getPhone(), record.getEmail()};
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        studentId.setText(String.valueOf(tableModel.getValueAt(row, 0)));
        name.setText(String.valueOf(tableModel.getValueAt(row, 1)));
        gender.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        department.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        major.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        classId.setText(String.valueOf(tableModel.getValueAt(row, 5)));
        enrollmentYear.setText(String.valueOf(tableModel.getValueAt(row, 6)));
        statusField.setText(String.valueOf(tableModel.getValueAt(row, 7)));
        phone.setText(String.valueOf(tableModel.getValueAt(row, 8)));
        email.setText(String.valueOf(tableModel.getValueAt(row, 9)));
    }

    private void fillForm(StudentRecord record) {
        studentId.setText(record.getStudentId());
        name.setText(record.getName());
        gender.setText(record.getGender());
        department.setText(record.getDepartmentName());
        major.setText(record.getMajorName());
        classId.setText(record.getClassId());
        enrollmentYear.setText(String.valueOf(record.getEnrollmentYear()));
        statusField.setText(record.getStatus());
        phone.setText(record.getPhone());
        email.setText(record.getEmail());
    }

    private void runRequest(String loadingMessage, final StudentRequest request,
            final ResponseHandler responseHandler) {
        final int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateButtonState();
        final Timer loadingStatus = DelayedUiUpdate.once(() -> {
            if (requestLifecycle.isCurrent(requestId) && requestInProgress) {
                showStatus(loadingMessage, VCampusTheme.MUTED);
            }
        });
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                try (RemoteStudentService service = new RemoteStudentService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接学籍服务器，请确认服务器已启动", VCampusTheme.DANGER);
                } finally {
                    loadingStatus.stop();
                    if (requestLifecycle.isCurrent(requestId)) {
                        requestInProgress = false;
                        updateButtonState();
                    }
                }
            }
        }.execute();
    }

    private void updateButtonState() {
        Role role = session.getUser().getRole();
        boolean canManage = role == Role.ADMIN || role == Role.ACADEMIC_ADMIN;
        selfButton.setEnabled(!requestInProgress);
        idButton.setEnabled(!requestInProgress && role != Role.STUDENT);
        classButton.setEnabled(!requestInProgress && role != Role.STUDENT);
        saveButton.setEnabled(!requestInProgress && canManage);
    }

    private String title() {
        return session.getUser().getRole() == Role.STUDENT ? "学籍信息" : "学籍管理";
    }

    private void showResponseFailure(Message response) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
            return;
        }
        showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.UNAUTHORIZED) return "登录状态已失效，请重新登录";
        if (statusCode == StatusCode.FORBIDDEN) return "当前账号没有学籍操作权限";
        if (statusCode == StatusCode.NOT_FOUND) return "没有找到对应学籍";
        if (statusCode == StatusCode.BAD_REQUEST) return "学籍请求数据不正确";
        return "服务器处理学籍请求失败";
    }

    private interface StudentRequest {
        Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}
