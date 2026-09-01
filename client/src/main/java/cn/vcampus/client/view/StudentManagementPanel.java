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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** Swing page for student profile queries and role-scoped updates. */
public final class StudentManagementPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final boolean canManage;
    private final boolean canQueryById;
    private final boolean canQueryClass;
    private final boolean canEdit;

    private final BatchTableModel tableModel = new BatchTableModel(
            new Object[] {"学号", "姓名", "专业", "班级", "入学年份", "学籍状态"});
    private final JTable table = new JTable(tableModel);
    private final JTextField studentIdQuery = new JTextField(12);
    private final JTextField classQuery = new JTextField(12);
    private final JButton selfButton = new JButton("查询本人");
    private final JButton idButton = new JButton("按学号查询");
    private final JButton classButton = new JButton("按班级查询");
    private final JButton saveButton = new JButton("保存档案");
    private final JLabel status = new JLabel("请选择查询方式");

    private final JTextField studentId = new JTextField();
    private final JTextField userId = new JTextField();
    private final JTextField name = new JTextField();
    private final JTextField gender = new JTextField();
    private final JTextField department = new JTextField();
    private final JTextField major = new JTextField();
    private final JTextField classId = new JTextField();
    private final JTextField enrollmentYear = new JTextField();
    private final JTextField academicStatus = new JTextField();
    private final JTextField phone = new JTextField();
    private final JTextField email = new JTextField();
    private boolean requestInProgress;

    public StudentManagementPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session are required");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        Role role = session.getUser().getRole();
        this.canManage = role == Role.ADMIN || role == Role.ACADEMIC_ADMIN;
        this.canQueryById = canManage || role == Role.TEACHER;
        this.canQueryClass = canManage;
        this.canEdit = canManage || role == Role.STUDENT;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 16));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(center(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);

        selfButton.addActionListener(event -> loadSelf());
        idButton.addActionListener(event -> loadById());
        classButton.addActionListener(event -> loadByClass());
        saveButton.addActionListener(event -> save());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedRecord();
        });
        configureFields();
        updateButtons();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        JLabel title = new JLabel("学生学籍管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName()
                + "；角色：" + session.getUser().getRole() + roleHint());
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private String roleHint() {
        if (canManage) return "，可查询和维护学生档案。";
        if (session.getUser().getRole() == Role.TEACHER) return "，当前为只读查询。";
        return "，只能查看本人并修改联系方式。";
    }

    private JPanel center() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.add(queryBar(), BorderLayout.NORTH);
        panel.add(tablePanel(), BorderLayout.CENTER);
        panel.add(editorPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel queryBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        VCampusTheme.secondaryButton(selfButton);
        VCampusTheme.secondaryButton(idButton);
        VCampusTheme.secondaryButton(classButton);
        studentIdQuery.setToolTipText("输入学生学号");
        classQuery.setToolTipText("输入班级编号");
        VCampusTheme.field(studentIdQuery);
        VCampusTheme.field(classQuery);
        panel.add(selfButton);
        panel.add(new JLabel("学号"));
        panel.add(studentIdQuery);
        panel.add(idButton);
        panel.add(new JLabel("班级"));
        panel.add(classQuery);
        panel.add(classButton);
        return panel;
    }

    private JPanel tablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.setGridColor(VCampusTheme.BORDER);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel editorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        VCampusTheme.panel(panel);
        JLabel title = new JLabel("学生档案");
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JPanel fields = new JPanel(new GridLayout(3, 8, 8, 8));
        fields.setOpaque(false);
        addField(fields, "学号", studentId);
        addField(fields, "账号", userId);
        addField(fields, "姓名", name);
        addField(fields, "性别", gender);
        addField(fields, "院系", department);
        addField(fields, "专业", major);
        addField(fields, "班级", classId);
        addField(fields, "入学年份", enrollmentYear);
        addField(fields, "学籍状态", academicStatus);
        addField(fields, "手机", phone);
        addField(fields, "邮箱", email);
        panel.add(title, BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);
        panel.setPreferredSize(new java.awt.Dimension(0, 188));
        return panel;
    }

    private static void addField(JPanel panel, String label, JTextField field) {
        JLabel caption = new JLabel(label);
        caption.setForeground(VCampusTheme.MUTED);
        panel.add(caption);
        VCampusTheme.field(field);
        panel.add(field);
    }

    private JPanel footer() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        VCampusTheme.primaryButton(saveButton);
        panel.add(saveButton, BorderLayout.WEST);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void configureFields() {
        boolean student = session.getUser().getRole() == Role.STUDENT;
        studentId.setEditable(canManage);
        userId.setEditable(canManage);
        name.setEditable(canManage);
        gender.setEditable(canManage);
        department.setEditable(canManage);
        major.setEditable(canManage);
        classId.setEditable(canManage);
        enrollmentYear.setEditable(canManage);
        academicStatus.setEditable(canManage);
        phone.setEditable(canEdit);
        email.setEditable(canEdit);
        if (student) {
            studentIdQuery.setEnabled(false);
            classQuery.setEnabled(false);
        }
    }

    private void loadSelf() {
        runRequest("正在查询本人档案…", new StudentCall() {
            @Override public Message execute(RemoteStudentService service)
                    throws IOException, ClassNotFoundException {
                return service.currentStudent(session.getToken());
            }
        }, response -> showSingle(response, "本人档案查询成功"));
    }

    private void loadById() {
        if (!canQueryById) {
            showStatus("当前角色不能按学号查询", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在查询学生档案…", service -> service.findById(
                session.getToken(), studentIdQuery.getText()),
                response -> showSingle(response, "学生档案查询成功"));
    }

    private void loadByClass() {
        if (!canQueryClass) {
            showStatus("当前角色不能按班级查询", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在查询班级学生…", service -> service.findByClass(
                session.getToken(), classQuery.getText()),
                response -> showList(response, "班级学生查询成功"));
    }

    private void save() {
        if (!canEdit) {
            showStatus("当前角色没有修改权限", VCampusTheme.DANGER);
            return;
        }
        final StudentRecord record;
        try {
            record = readRecord();
        } catch (IllegalArgumentException failure) {
            showStatus(failure.getMessage(), VCampusTheme.DANGER);
            return;
        }
        runRequest("正在保存学生档案…", service -> service.save(session.getToken(), record),
                response -> showSingle(response, "学生档案保存成功"));
    }

    private StudentRecord readRecord() {
        String id = required(studentId.getText(), "学号");
        String displayName = required(name.getText(), "姓名");
        int year;
        try {
            year = Integer.parseInt(required(enrollmentYear.getText(), "入学年份"));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("入学年份必须是数字");
        }
        return new StudentRecord(id, optional(userId.getText()), displayName, optional(gender.getText()),
                optional(department.getText()), optional(major.getText()), optional(classId.getText()), year,
                required(academicStatus.getText(), "学籍状态"), optional(phone.getText()), optional(email.getText()));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void showSingle(Message response, String successMessage) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof StudentRecord)) {
            showStatus("服务器返回的学生数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        StudentRecord record = (StudentRecord) response.getPayload();
        tableModel.replaceRows(java.util.Collections.singletonList(row(record)));
        table.setRowSelectionInterval(0, 0);
        apply(record);
        showStatus(successMessage, VCampusTheme.SUCCESS);
    }

    private void showList(Message response, String successMessage) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的学生数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> values = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object value : values) {
            if (!(value instanceof StudentRecord)) {
                showStatus("服务器返回的学生数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            rows.add(row((StudentRecord) value));
        }
        tableModel.replaceRows(rows);
        showStatus(successMessage + "，共 " + values.size() + " 人", VCampusTheme.SUCCESS);
    }

    private static Object[] row(StudentRecord record) {
        return new Object[] {record.getStudentId(), record.getName(), record.getMajorName(),
                record.getClassId(), record.getEnrollmentYear(), record.getStatus()};
    }

    private void loadSelectedRecord() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        String selectedId = String.valueOf(tableModel.getValueAt(row, 0));
        for (int index = 0; index < tableModel.getRowCount(); index++) {
            if (selectedId.equals(String.valueOf(tableModel.getValueAt(index, 0)))) {
                studentId.setText(selectedId);
                name.setText(value(tableModel.getValueAt(index, 1)));
                major.setText(value(tableModel.getValueAt(index, 2)));
                classId.setText(value(tableModel.getValueAt(index, 3)));
                enrollmentYear.setText(value(tableModel.getValueAt(index, 4)));
                academicStatus.setText(value(tableModel.getValueAt(index, 5)));
                return;
            }
        }
    }

    private void apply(StudentRecord record) {
        studentId.setText(record.getStudentId());
        userId.setText(value(record.getUserId()));
        name.setText(value(record.getName()));
        gender.setText(value(record.getGender()));
        department.setText(value(record.getDepartmentName()));
        major.setText(value(record.getMajorName()));
        classId.setText(value(record.getClassId()));
        enrollmentYear.setText(String.valueOf(record.getEnrollmentYear()));
        academicStatus.setText(value(record.getStatus()));
        phone.setText(value(record.getPhone()));
        email.setText(value(record.getEmail()));
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value); }

    private void runRequest(String loadingMessage, StudentCall call, ResponseHandler handler) {
        requestInProgress = true;
        updateButtons();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteStudentService service = new RemoteStudentService(host, port)) {
                    return call.execute(service);
                }
            }

            @Override protected void done() {
                try {
                    handler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接学籍服务器，请确认服务器已启动", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtons();
                }
            }
        }.execute();
        status.setText(loadingMessage);
        status.setForeground(VCampusTheme.MUTED);
    }

    private void updateButtons() {
        selfButton.setEnabled(!requestInProgress);
        idButton.setEnabled(!requestInProgress && canQueryById);
        classButton.setEnabled(!requestInProgress && canQueryClass);
        saveButton.setEnabled(!requestInProgress && canEdit);
    }

    private void showResponseFailure(Message response) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
        } else {
            showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
        }
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static String statusMessage(StatusCode code) {
        if (code == StatusCode.BAD_REQUEST) return "请求数据不正确";
        if (code == StatusCode.UNAUTHORIZED) return "登录状态已失效，请重新登录";
        if (code == StatusCode.FORBIDDEN) return "当前账号没有执行此操作的权限";
        if (code == StatusCode.NOT_FOUND) return "学生档案不存在或账号未绑定";
        if (code == StatusCode.CONFLICT) return "学生档案存在冲突";
        return "服务器处理请求失败";
    }

    private interface StudentCall {
        Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}
