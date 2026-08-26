package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.user.Session;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** 选课管理与成绩录入页面。 */
public final class CourseManagementPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JTextField courseIdField = new JTextField("AI101", 8);
    private final JTextField courseNameField = new JTextField("人工智能导论", 12);
    private final JTextField creditsField = new JTextField("2", 4);
    private final JTextField capacityField = new JTextField("30", 4);
    private final JTextField studentIdField = new JTextField("20230001", 8);
    private final JTextField scoreField = new JTextField("90", 4);
    private final JLabel status = new JLabel("教务管理员维护课程；教师录入成绩。");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"课程号", "课程名", "学分", "容量", "状态"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public CourseManagementPanel(String host, int port, Session session) {
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        JPanel card = new JPanel(new BorderLayout());
        VCampusTheme.panel(card);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("选课管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("开课、改课、停课需要教务权限；成绩录入需要教师权限。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        JButton refresh = new JButton("刷新课程");
        JButton create = new JButton("新增课程");
        JButton update = new JButton("更新课程");
        JButton deactivate = new JButton("停开课程");
        JButton grade = new JButton("录入成绩");
        VCampusTheme.secondaryButton(refresh);
        VCampusTheme.primaryButton(create);
        VCampusTheme.secondaryButton(update);
        VCampusTheme.secondaryButton(deactivate);
        VCampusTheme.primaryButton(grade);
        refresh.addActionListener(e -> refreshCourses());
        create.addActionListener(e -> createCourse());
        update.addActionListener(e -> updateCourse());
        deactivate.addActionListener(e -> deactivateCourse());
        grade.addActionListener(e -> recordGrade());
        boolean canManage = session.getUser().getRole() == Role.ADMIN
                || session.getUser().getRole() == Role.ACADEMIC_ADMIN;
        boolean canGrade = session.getUser().getRole() == Role.ADMIN
                || session.getUser().getRole() == Role.TEACHER;
        create.setEnabled(canManage);
        update.setEnabled(canManage);
        deactivate.setEnabled(canManage);
        grade.setEnabled(canGrade);
        decorateFields();
        row1.add(new JLabel("课程号"));
        row1.add(courseIdField);
        row1.add(new JLabel("课程名"));
        row1.add(courseNameField);
        row1.add(new JLabel("学分"));
        row1.add(creditsField);
        row1.add(new JLabel("容量"));
        row1.add(capacityField);
        row1.add(refresh);
        row1.add(create);
        row1.add(update);
        row1.add(deactivate);
        row2.add(new JLabel("学生"));
        row2.add(studentIdField);
        row2.add(new JLabel("成绩"));
        row2.add(scoreField);
        row2.add(grade);
        JPanel rows = new JPanel(new BorderLayout(0, 8));
        rows.setOpaque(false);
        rows.add(row1, BorderLayout.NORTH);
        rows.add(row2, BorderLayout.CENTER);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(rows, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void decorateFields() {
        VCampusTheme.field(courseIdField);
        VCampusTheme.field(courseNameField);
        VCampusTheme.field(creditsField);
        VCampusTheme.field(capacityField);
        VCampusTheme.field(studentIdField);
        VCampusTheme.field(scoreField);
    }

    private void refreshCourses() {
        run(new CourseRequest() {
            @Override public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.listCourses();
            }
        }, response -> showCourses(response));
    }

    private Course readCourseFromFields() {
        return new Course(courseIdField.getText(), courseNameField.getText(),
                Integer.parseInt(creditsField.getText().trim()),
                Integer.parseInt(capacityField.getText().trim()));
    }

    private void createCourse() {
        final Course course;
        try {
            course = readCourseFromFields();
        } catch (IllegalArgumentException invalid) {
            showStatus("课程信息填写不正确", false);
            return;
        }
        run(new CourseRequest() {
            @Override public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.createCourse(session.getToken(), course);
            }
        }, response -> showOperation(response, "课程保存成功"));
    }

    private void updateCourse() {
        final Course course;
        try {
            course = readCourseFromFields();
        } catch (IllegalArgumentException invalid) {
            showStatus("课程信息填写不正确", false);
            return;
        }
        run(new CourseRequest() {
            @Override public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.updateCourse(session.getToken(), course);
            }
        }, response -> showOperation(response, "课程更新成功"));
    }

    private void deactivateCourse() {
        run(new CourseRequest() {
            @Override public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.deactivateCourse(session.getToken(), courseIdField.getText());
            }
        }, response -> showOperation(response, "课程已停开"));
    }

    private void recordGrade() {
        final int score;
        try {
            score = Integer.parseInt(scoreField.getText().trim());
        } catch (NumberFormatException invalid) {
            showStatus("成绩必须是数字", false);
            return;
        }
        run(new CourseRequest() {
            @Override public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.recordGrade(session.getToken(), session.getUser().getUserId(),
                        studentIdField.getText(), courseIdField.getText(), score);
            }
        }, response -> showOperation(response, "成绩录入成功"));
    }

    private void showCourses(Message response) {
        if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
            showStatus("课程查询失败", false);
            return;
        }
        tableModel.setRowCount(0);
        List<?> courses = (List<?>) response.getPayload();
        for (Object item : courses) {
            Course course = (Course) item;
            tableModel.addRow(new Object[] {
                    course.getCourseId(), course.getName(), course.getCredits(), course.getCapacity(),
                    course.isActive() ? "开课中" : "已停开"
            });
        }
        showStatus("课程查询完成，共 " + courses.size() + " 条", true);
    }

    private void showOperation(Message response, String success) {
        if (response.getStatusCode() == StatusCode.OK) {
            showStatus(success, true);
        } else {
            showStatus("操作失败：" + response.getStatusCode(), false);
        }
    }

    private void run(final CourseRequest request, final CourseResponse responseHandler) {
        showStatus("正在连接选课服务…", true);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接服务器，请确认服务端已启动", false);
                }
            }
        }.execute();
    }

    private void showStatus(String message, boolean ok) {
        status.setText(message);
        status.setForeground(ok ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
    }

    private interface CourseRequest {
        Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface CourseResponse {
        void handle(Message response);
    }
}
