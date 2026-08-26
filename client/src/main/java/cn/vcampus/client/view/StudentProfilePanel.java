package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicReview;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.Session;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** 学籍信息与学业审查页面。 */
public final class StudentProfilePanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JTextField studentIdField = new JTextField(16);
    private final JLabel status = new JLabel("请输入学号后查询学籍或学业审查");
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[] {"项目", "内容"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public StudentProfilePanel(String host, int port, Session session) {
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        studentIdField.setText(session.getUser().getUserId());
        VCampusTheme.field(studentIdField);

        add(header(), BorderLayout.NORTH);
        JTable table = new JTable(tableModel);
        table.setRowHeight(30);
        JPanel card = new JPanel(new BorderLayout());
        VCampusTheme.panel(card);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("学籍管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("账号只负责登录，学生基础档案、历史课程、重修和学分记录由学籍模块维护。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        JButton query = new JButton("查询学籍");
        JButton review = new JButton("学业审查");
        VCampusTheme.secondaryButton(query);
        VCampusTheme.primaryButton(review);
        query.addActionListener(e -> queryProfile());
        review.addActionListener(e -> queryReview());
        buttons.add(new JLabel("学号"));
        buttons.add(studentIdField);
        buttons.add(query);
        buttons.add(review);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void queryProfile() {
        run(new StudentRequest() {
            @Override public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.findById(session.getToken(), studentIdField.getText());
            }
        }, new StudentResponse() {
            @Override public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof StudentRecord)) {
                    showFailure(response, "未找到学籍档案，请联系教务管理员维护");
                    return;
                }
                StudentRecord record = (StudentRecord) response.getPayload();
                tableModel.setRowCount(0);
                addRow("学号", record.getStudentId());
                addRow("姓名", record.getName());
                addRow("性别", record.getGender());
                addRow("学院", record.getDepartmentName());
                addRow("专业", record.getMajorName());
                addRow("班级", record.getClassId());
                addRow("入学年份", String.valueOf(record.getEnrollmentYear()));
                addRow("状态", record.getStatus());
                addRow("电话", record.getPhone());
                addRow("邮箱", record.getEmail());
                showStatus("学籍信息查询完成", true);
            }
        });
    }

    private void queryReview() {
        run(new StudentRequest() {
            @Override public Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException {
                return service.review(session.getToken(), studentIdField.getText(), 120);
            }
        }, new StudentResponse() {
            @Override public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof AcademicReview)) {
                    showFailure(response, "学业审查失败");
                    return;
                }
                AcademicReview review = (AcademicReview) response.getPayload();
                tableModel.setRowCount(0);
                addRow("审查学号", review.getStudentId());
                addRow("已获学分", String.valueOf(review.getTotalEarnedCredits()));
                addRow("通过课程数", String.valueOf(review.getPassedCourseCount()));
                addRow("未通过课程数", String.valueOf(review.getFailedCourseCount()));
                addRow("重修课程数", String.valueOf(review.getRetakeCourseCount()));
                addRow("是否达到毕业条件", review.isGraduationReady() ? "是" : "否");
                addRow("备注", review.getRemark());
                showStatus("学业审查完成", true);
            }
        });
    }

    private void run(final StudentRequest request, final StudentResponse responseHandler) {
        showStatus("正在连接服务器…", true);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteStudentService service = new RemoteStudentService(host, port)) {
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

    private void addRow(String key, String value) {
        tableModel.addRow(new Object[] { key, value == null ? "" : value });
    }

    private void showFailure(Message response, String fallback) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), false);
        } else {
            showStatus(fallback, false);
        }
    }

    private void showStatus(String message, boolean ok) {
        status.setText(message);
        status.setForeground(ok ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
    }

    private interface StudentRequest {
        Message execute(RemoteStudentService service) throws IOException, ClassNotFoundException;
    }

    private interface StudentResponse {
        void handle(Message response);
    }
}
