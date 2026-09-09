package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentProfileValidation;
import cn.vcampus.student.StudentProfileSnapshot;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
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
    private final JTextField majorQuery = new JTextField(12);
    private final JButton selfButton = new JButton("查询本人");
    private final JButton idButton = new JButton("按学号查询");
    private final JButton classButton = new JButton("按班级查询");
    private final JButton majorButton = new JButton("按专业查询");
    private final JButton saveButton = new JButton("保存档案");
    private final JLabel status = new JLabel("请选择查询方式");

    private final JTextField studentId = new JTextField();
    private final JTextField userId = new JTextField();
    private final JTextField name = new JTextField();
    private final JComboBox<String> gender = new JComboBox<String>(new String[] {"男", "女", "未知"});
    private final JTextField department = new JTextField();
    private final JTextField major = new JTextField();
    private final JTextField classId = new JTextField();
    private final JTextField enrollmentYear = new JTextField();
    private final JComboBox<String> academicStatus = new JComboBox<String>(new String[] {"在读", "休学", "退学"});
    private final JTextField phone = new JTextField();
    private final JTextField email = new JTextField();
    private boolean requestInProgress;
    private boolean selectionUpdateInProgress;
    private boolean loadedRecord;
    private boolean initialSelfLoadStarted;
    private boolean populatingProfile;
    private StudentRecord loadedProfile;

    public StudentManagementPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session are required");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        Role role = session.getUser().getRole();
        this.canManage = role == Role.ADMIN || role == Role.ACADEMIC_ADMIN;
        this.canQueryById = canManage;
        this.canQueryClass = canManage;
        this.canEdit = canManage || role == Role.STUDENT;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(16)));
        setOpaque(false);
        if (session.getUser().getRole() == Role.TEACHER) {
            add(new TeacherSelfPanel(host, port, session), BorderLayout.CENTER);
            return;
        }
        add(header(), BorderLayout.NORTH);
        if (session.getUser().getRole() == Role.STUDENT) {
            JTabbedPane tabs = new JTabbedPane();
            VCampusTheme.tabs(tabs);
            tabs.addTab("学籍档案", VCampusTheme.pageScroll(body()));
            tabs.addTab("成绩与重修", new StudentAcademicPanel(host, port, session));
            add(tabs, BorderLayout.CENTER);
        } else {
            JTabbedPane tabs = new JTabbedPane();
            VCampusTheme.tabs(tabs);
            tabs.addTab("学生档案维护", VCampusTheme.pageScroll(body()));
            tabs.addTab("全员信息与毕业管理", new AcademicAdministrationPanel(host, port, session));
            add(tabs, BorderLayout.CENTER);
        }

        selfButton.addActionListener(event -> loadSelf());
        idButton.addActionListener(event -> loadById());
        classButton.addActionListener(event -> loadByClass());
        majorButton.addActionListener(event -> loadByMajor());
        saveButton.addActionListener(event -> save());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionUpdateInProgress) loadSelectedRecord();
        });
        configureFields();
        updateButtons();
        if (session.getUser().getRole() == Role.STUDENT) {
            selfButton.setText("刷新本人档案");
            saveButton.setText("保存联系方式");
            status.setText("进入页面后自动加载本人档案");
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                        && isShowing() && !initialSelfLoadStarted) {
                    initialSelfLoadStarted = true;
                    loadSelf();
                }
            });
        }
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        panel.setOpaque(false);
        JLabel title = new JLabel(canManage ? "学籍管理工作台" : "我的学籍");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel(canManage ? "维护学生档案，查看师生信息，办理学分审查与毕业。"
                : "查看本人档案、修读记录与学分进度。");
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

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        if (session.getUser().getRole() == Role.STUDENT) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            actions.setOpaque(false);
            VCampusTheme.secondaryButton(selfButton);
            actions.add(selfButton);
            panel.add(actions, BorderLayout.NORTH);
            panel.add(editorPanel(), BorderLayout.CENTER);
            panel.add(footer(), BorderLayout.SOUTH);
            return panel;
        }
        panel.add(queryBar(), BorderLayout.NORTH);
        panel.add(workspace(), BorderLayout.CENTER);
        return panel;
    }

    private JSplitPane workspace() {
        JPanel detail = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        detail.setOpaque(false);
        detail.add(editorPanel(), BorderLayout.CENTER);
        detail.add(footer(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tablePanel(), detail) {
            @Override public void doLayout() {
                int orientation = getWidth() > 0 && getWidth() < UiMetrics.px(740) ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
                if (getOrientation() != orientation) {
                    setOrientation(orientation);
                    setPreferredSize(UiMetrics.dimension(0, orientation == JSplitPane.VERTICAL_SPLIT ? 920 : 620));
                    setDividerLocation(orientation == JSplitPane.VERTICAL_SPLIT ? UiMetrics.px(250) : Math.max(UiMetrics.px(250), getWidth() * 46 / 100));
                    revalidate();
                }
                super.doLayout();
            }
        };
        split.setOpaque(false);
        split.setBorder(null);
        split.setDividerSize(UiMetrics.px(10));
        split.setResizeWeight(0.46);
        split.setPreferredSize(UiMetrics.dimension(0, 620));
        split.setMinimumSize(UiMetrics.dimension(0, 520));
        return split;
    }

    private JPanel queryBar() {
        JPanel panel = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        VCampusTheme.panel(panel);
        VCampusTheme.secondaryButton(selfButton);
        VCampusTheme.secondaryButton(idButton);
        VCampusTheme.secondaryButton(classButton);
        VCampusTheme.secondaryButton(majorButton);
        studentIdQuery.setToolTipText("输入学生学号");
        classQuery.setToolTipText("输入班级编号");
        majorQuery.setToolTipText("输入专业名称");
        VCampusTheme.field(studentIdQuery);
        VCampusTheme.field(classQuery);
        VCampusTheme.field(majorQuery);
        panel.add(new JLabel("学号"));
        panel.add(studentIdQuery);
        panel.add(idButton);
        panel.add(new JLabel("班级"));
        panel.add(classQuery);
        panel.add(classButton);
        panel.add(new JLabel("专业"));
        panel.add(majorQuery);
        panel.add(majorButton);
        return panel;
    }

    private JPanel tablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);
        panel.setPreferredSize(UiMetrics.dimension(0, 260));
        panel.setMinimumSize(UiMetrics.dimension(0, 180));
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(UiMetrics.px(100));
        table.getColumnModel().getColumn(1).setPreferredWidth(UiMetrics.px(100));
        table.getColumnModel().getColumn(2).setPreferredWidth(UiMetrics.px(160));
        table.getColumnModel().getColumn(3).setPreferredWidth(UiMetrics.px(120));
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel editorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(panel);
        JLabel title = new JLabel("学生档案");
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JPanel fields = new JPanel(new GridLayout(0, 4, UiMetrics.px(10), UiMetrics.px(10)));
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
        panel.setMinimumSize(UiMetrics.dimension(0, 260));
        return panel;
    }

    private static void addField(JPanel panel, String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setLabelFor(field);
        caption.setForeground(VCampusTheme.MUTED);
        panel.add(caption);
        VCampusTheme.field(field);
        panel.add(field);
    }

    private JPanel footer() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        panel.setOpaque(false);
        VCampusTheme.primaryButton(saveButton);
        panel.add(saveButton, BorderLayout.WEST);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void configureFields() {
        boolean student = session.getUser().getRole() == Role.STUDENT;
        // Editing a loaded archive must not accidentally create another student or rebind an account.
        studentId.setEditable(false);
        userId.setEditable(false);
        studentId.setToolTipText("学号为档案标识，不在编辑档案时修改");
        userId.setToolTipText("账号绑定由用户管理流程维护");
        name.setEditable(canManage);
        gender.setEditable(false);
        gender.setEnabled(canManage);
        department.setEditable(canManage);
        major.setEditable(canManage);
        classId.setEditable(canManage);
        enrollmentYear.setEditable(canManage);
        academicStatus.setEditable(false);
        academicStatus.setEnabled(canManage);
        academicStatus.setToolTipText("选择在读、休学或退学；毕业须通过毕业管理办理");
        phone.setEditable(canEdit);
        email.setEditable(canEdit);
        phone.setToolTipText("选填；填写时须为11位数字");
        email.setToolTipText("选填；例如 student@example.com，最多100字符");
        enrollmentYear.setToolTipText("四位年份，1900至" + (java.time.Year.now().getValue() + 1));
        limit(phone, 11, true);
        limit(enrollmentYear, 4, true);
        limit(name, 64, false);
        limit(department, 64, false);
        limit(major, 64, false);
        limit(classId, 32, false);
        limit(email, 100, false);
        if (student) {
            studentIdQuery.setEnabled(false);
            classQuery.setEnabled(false);
            majorQuery.setEnabled(false);
        }
    }

    private void limit(JTextField field, int maximum, boolean digitsOnly) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                    throws BadLocationException { replace(fb, offset, 0, text, attrs); }
            @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String replacement = text == null ? "" : text;
                String old = fb.getDocument().getText(0, fb.getDocument().getLength());
                String value = old.substring(0, offset) + replacement + old.substring(offset + length);
                if (populatingProfile || (value.length() <= maximum
                        && (!digitsOnly || value.matches("[0-9]*"))
                        && value.chars().noneMatch(Character::isISOControl))) {
                    fb.replace(offset, length, replacement, attrs);
                } else {
                    showStatus(digitsOnly ? (field == phone ? "手机号只能输入数字，最多11位" : "入学年份只能输入4位数字")
                            : "该字段最多" + maximum + "个字符，不能包含换行", VCampusTheme.DANGER);
                }
            }
        });
    }

    private void loadSelf() {
        if (session.getUser().getRole() != Role.STUDENT || requestInProgress) return;
        clearEditor();
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
        clearEditor();
        runRequest("正在查询学生档案…", service -> service.findById(
                session.getToken(), studentIdQuery.getText()),
                response -> showSingle(response, "学生档案查询成功"));
    }

    private void loadByClass() {
        if (!canQueryClass) {
            showStatus("当前角色不能按班级查询", VCampusTheme.DANGER);
            return;
        }
        clearEditor();
        runRequest("正在查询班级学生…", service -> service.findByClass(
                session.getToken(), classQuery.getText()),
                response -> showList(response, "班级学生查询成功"));
    }

    private void loadByMajor() {
        if (!canQueryClass) {
            showStatus("当前角色不能按专业查询", VCampusTheme.DANGER);
            return;
        }
        clearEditor();
        runRequest("正在查询专业学生…", service -> service.findByMajor(
                session.getToken(), majorQuery.getText()),
                response -> showList(response, "专业学生查询成功"));
    }

    private void save() {
        if (!canEdit || !loadedRecord) {
            showStatus(canEdit ? "请先加载完整学生档案后再保存" : "当前角色没有修改权限",
                    VCampusTheme.DANGER);
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
        StudentProfileValidation.contacts(optional(phone.getText()), optional(email.getText()));
        if (!canManage && loadedProfile != null) {
            return StudentProfileSnapshot.withContacts(loadedProfile, optional(phone.getText()), optional(email.getText()));
        }
        String id = required(studentId.getText(), "学号");
        String displayName = required(name.getText(), "姓名");
        int year;
        try {
            year = Integer.parseInt(required(enrollmentYear.getText(), "入学年份"));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("入学年份必须是数字");
        }
        StudentRecord record = new StudentRecord(id, optional(userId.getText()), displayName, (String) gender.getSelectedItem(),
                optional(department.getText()), optional(major.getText()), optional(classId.getText()), year,
                required((String) academicStatus.getSelectedItem(), "学籍状态"), optional(phone.getText()), optional(email.getText()));
        StudentProfileValidation.profile(record);
        return record;
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
        selectionUpdateInProgress = true;
        try {
            table.setRowSelectionInterval(0, 0);
        } finally {
            selectionUpdateInProgress = false;
        }
        apply(record);
        loadedRecord = true;
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
        loadedRecord = false;
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
        if (requestInProgress || !canQueryById) return;
        loadedRecord = false;
        clearEditor();
        runRequest("正在加载所选学生完整档案…", service -> service.findById(
                session.getToken(), selectedId), response -> {
                    int selectedRow = table.getSelectedRow();
                    if (selectedRow < 0 || !selectedId.equals(
                            String.valueOf(tableModel.getValueAt(selectedRow, 0)))) {
                        return;
                    }
                    if (response.getStatusCode() != StatusCode.OK) {
                        showResponseFailure(response);
                        return;
                    }
                    if (!(response.getPayload() instanceof StudentRecord)) {
                        showStatus("服务器返回的学生数据格式不正确", VCampusTheme.DANGER);
                        return;
                    }
                    apply((StudentRecord) response.getPayload());
                    loadedRecord = true;
                    showStatus("已加载所选学生完整档案", VCampusTheme.SUCCESS);
                });
    }

    private void clearEditor() {
        loadedRecord = false;
        loadedProfile = null;
        studentId.setText("");
        userId.setText("");
        name.setText("");
        gender.setSelectedIndex(-1);
        department.setText("");
        major.setText("");
        classId.setText("");
        enrollmentYear.setText("");
        academicStatus.removeItem("毕业");
        academicStatus.setSelectedIndex(-1);
        phone.setText("");
        email.setText("");
    }

    private void apply(StudentRecord record) {
        loadedProfile = record;
        populatingProfile = true;
        try {
            studentId.setText(record.getStudentId());
            userId.setText(value(record.getUserId()));
            name.setText(value(record.getName()));
            gender.setSelectedIndex(-1);
            gender.setSelectedItem(record.getGender());
            department.setText(value(record.getDepartmentName()));
            major.setText(value(record.getMajorName()));
            classId.setText(value(record.getClassId()));
            enrollmentYear.setText(String.valueOf(record.getEnrollmentYear()));
            academicStatus.removeItem("毕业");
            if ("毕业".equals(record.getStatus())) academicStatus.addItem("毕业");
            academicStatus.setSelectedIndex(-1);
            academicStatus.setSelectedItem(record.getStatus());
            phone.setText(value(record.getPhone()));
            email.setText(value(record.getEmail()));
        } finally { populatingProfile = false; }
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
        academicStatus.setEnabled(canManage && !requestInProgress
                && (loadedProfile == null || !"毕业".equals(loadedProfile.getStatus())));
        gender.setEnabled(canManage && !requestInProgress);
        selfButton.setEnabled(!requestInProgress && session.getUser().getRole() == Role.STUDENT);
        idButton.setEnabled(!requestInProgress && canQueryById);
        classButton.setEnabled(!requestInProgress && canQueryClass);
        majorButton.setEnabled(!requestInProgress && canQueryClass);
        saveButton.setEnabled(!requestInProgress && canEdit && loadedRecord);
        table.setEnabled(!requestInProgress);
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
