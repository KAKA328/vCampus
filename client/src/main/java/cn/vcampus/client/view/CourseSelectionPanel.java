package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

/**
 * 学生选课页面，提供课程查询、选课、退选和已选课程查询功能。
 *
 * <p>该页面可独立创建，等待组长在主界面中统一接入。</p>
 */
public final class CourseSelectionPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final boolean canSelectCourses;
    private final BatchTableModel courseTableModel = new BatchTableModel(
            new Object[] {"课程号", "课程名称", "学分", "课程容量"});
    private final JTable courseTable = new JTable(courseTableModel);
    private final JLabel status = new JLabel("请点击“刷新课程”查询课程信息");
    private final JButton refreshButton = new JButton("刷新课程");
    private final JButton selectedCoursesButton = new JButton("我的课程");
    private final JButton selectButton = new JButton("选课");
    private final JButton dropButton = new JButton("退选");

    private boolean requestInProgress;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public CourseSelectionPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.canSelectCourses = session.getUser().getRole() == Role.STUDENT;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);

        add(header(), BorderLayout.NORTH);
        add(courseTablePanel(), BorderLayout.CENTER);
        add(bottomPanel(), BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> loadAllCourses());
        selectedCoursesButton.addActionListener(event -> loadSelectedCourses());
        selectButton.addActionListener(event -> selectCourse());
        dropButton.addActionListener(event -> dropCourse());
        updateButtonState();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel title = new JLabel("选课系统");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        String roleHint = canSelectCourses
                ? "可查询课程、选择课程、退选课程并查看已选课程。"
                : "当前角色仅可查看课程信息，选课和退选功能仅对学生开放。";
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName() + "；" + roleHint);
        subtitle.setForeground(VCampusTheme.MUTED);

        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel courseTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);

        courseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseTable.setRowHeight(28);
        courseTable.getTableHeader().setReorderingAllowed(false);
        courseTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        courseTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        panel.add(new JScrollPane(courseTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel bottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshButton);
        VCampusTheme.secondaryButton(selectedCoursesButton);
        VCampusTheme.primaryButton(selectButton);
        VCampusTheme.secondaryButton(dropButton);
        actions.add(refreshButton);
        actions.add(selectedCoursesButton);
        actions.add(selectButton);
        actions.add(dropButton);

        status.setForeground(VCampusTheme.MUTED);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void loadAllCourses() {
        runRequest("正在查询全部课程…", new CourseRequest() {
            @Override
            public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.listCourses();
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showCourses(response, "已显示全部课程");
            }
        });
    }

    private void loadSelectedCourses() {
        runRequest("正在查询我的课程…", new CourseRequest() {
            @Override
            public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.selectedCourses(session.getToken(), session.getUser().getUserId());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showCourses(response, "已显示我的课程");
            }
        });
    }

    private void selectCourse() {
        String courseId = selectedCourseId();
        if (courseId == null) {
            showStatus("请先在课程表中选择一门课程", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在提交选课请求…", new CourseRequest() {
            @Override
            public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.select(session.getToken(), session.getUser().getUserId(), courseId);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("选课成功，正在刷新课程列表…", VCampusTheme.SUCCESS);
                loadAllCourses();
            }
        });
    }

    private void dropCourse() {
        String courseId = selectedCourseId();
        if (courseId == null) {
            showStatus("请先在课程表中选择一门课程", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在提交退选请求…", new CourseRequest() {
            @Override
            public Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException {
                return service.drop(session.getToken(), session.getUser().getUserId(), courseId);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("退选成功，正在刷新我的课程…", VCampusTheme.SUCCESS);
                loadSelectedCourses();
            }
        });
    }

    private String selectedCourseId() {
        int selectedRow = courseTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return String.valueOf(courseTableModel.getValueAt(selectedRow, 0));
    }

    private void showCourses(Message response, String successMessage) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的课程数据格式不正确", VCampusTheme.DANGER);
            return;
        }

        List<?> courses = (List<?>) response.getPayload();
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<Object[]>();
        for (Object item : courses) {
            if (!(item instanceof Course)) {
                showStatus("服务器返回的课程数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            Course course = (Course) item;
            rows.add(new Object[] {
                    course.getCourseId(), course.getName(), course.getCredits(), course.getCapacity()
            });
        }
        courseTableModel.replaceRows(rows);
        showStatus(successMessage + "，共 " + courses.size() + " 门", VCampusTheme.SUCCESS);
    }

    private void runRequest(String loadingMessage, final CourseRequest request,
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
                try (RemoteCourseService service = new RemoteCourseService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接选课服务器，请确认服务器已启动", VCampusTheme.DANGER);
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

    private void showResponseFailure(Message response) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
            return;
        }
        showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
    }

    private void updateButtonState() {
        refreshButton.setEnabled(!requestInProgress);
        selectedCoursesButton.setEnabled(!requestInProgress);
        selectButton.setEnabled(!requestInProgress && canSelectCourses);
        dropButton.setEnabled(!requestInProgress && canSelectCourses);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST) {
            return "请求数据不正确";
        }
        if (statusCode == StatusCode.UNAUTHORIZED) {
            return "登录状态已失效，请重新登录";
        }
        if (statusCode == StatusCode.FORBIDDEN) {
            return "当前账号没有执行此操作的权限";
        }
        if (statusCode == StatusCode.NOT_FOUND) {
            return "课程或选课记录不存在";
        }
        if (statusCode == StatusCode.CONFLICT) {
            return "操作未完成：课程可能已满或已选过此课";
        }
        return "服务器处理请求失败";
    }

    private interface CourseRequest {
        Message execute(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}
