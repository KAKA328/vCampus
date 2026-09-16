package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import cn.vcampus.student.AcademicAssessment;
import cn.vcampus.student.CreditSummary;
import cn.vcampus.student.GraduationCreditRequirement;
import cn.vcampus.student.GraduationReviewOverview;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcademicAdministrationPanelTest {
    @Test void directoryAndGraduationWorkspaceUseSeparateTables() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = panel(command -> null);
                panel.displayDirectory(Action.TEACHERS, response(Collections.singletonList(
                        new TeacherProfile("T001", "teacher", "教师", "院系", "教授", false))));
                JTable directory = field(panel, "directoryTable", JTable.class);
                JTable students = field(panel, "studentTable", JTable.class);
                assertEquals(6, directory.getColumnCount());
                assertEquals("非在职", directory.getValueAt(0, 5));
                assertEquals(0, students.getRowCount());
                assertNotSame(directory.getModel(), students.getModel());
                assertThrows(NoSuchFieldException.class,
                        () -> AcademicAdministrationPanel.class.getDeclaredField("required"));
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void enteringGraduationTabLoadsStudentsThenOverview() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<String>());
        CountDownLatch overviewLoaded = new CountDownLatch(1);
        StudentRecord student = student("S001", "张三", "在读");
        AcademicAdministrationPanel panel = panel(command -> {
            actions.add(command.getAction().name());
            if (command.getAction() == Action.STUDENTS) {
                return response(Collections.singletonList(student));
            }
            return Message.response(request(), StatusCode.BAD_REQUEST, "unexpected");
        }, command -> {
            actions.add("OVERVIEW");
            overviewLoaded.countDown();
            return response(overview(student, 11, 11, null, false));
        });

        SwingUtilities.invokeAndWait(() -> tabs(panel).setSelectedIndex(1));
        assertTrue(overviewLoaded.await(5, TimeUnit.SECONDS));
        awaitIdle(panel);
        assertEquals(Arrays.asList("STUDENTS", "OVERVIEW"), actions);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertEquals(1, field(panel, "studentTable", JTable.class).getRowCount());
                assertEquals("S001  张三", field(panel, "studentTitle", JLabel.class).getText());
                assertEquals("11", field(panel, "earnedValue", JLabel.class).getText());
                assertEquals("11", field(panel, "requiredValue", JLabel.class).getText());
                assertEquals("0", field(panel, "shortfallValue", JLabel.class).getText());
                assertEquals("PLAN-S001", field(panel, "planValue", JLabel.class).getText());
                assertTrue(field(panel, "review", JButton.class).isEnabled());
                assertFalse(field(panel, "confirmed", JCheckBox.class).isEnabled());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void fractionalCreditsDisplayWithoutTruncation() throws Exception {
        StudentRecord student = student("S001", "张三", "在读");
        AcademicAdministrationPanel panel = panel(command -> null);
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.displayOverview(response(overview(student, new BigDecimal("8.5"),
                        new BigDecimal("11.5"), null, false)));
                assertEquals("8.5", field(panel, "earnedValue", JLabel.class).getText());
                assertEquals("11.5", field(panel, "requiredValue", JLabel.class).getText());
                assertEquals("3", field(panel, "shortfallValue", JLabel.class).getText());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void validAssessmentRequiresManualConfirmationBeforeGraduation() throws Exception {
        StudentRecord student = student("S001", "张三", "在读");
        AcademicAssessment assessment = assessment("S001", 11, 11, 0, false);
        AcademicAdministrationPanel panel = panel(command -> null);
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.displayOverview(response(overview(student, 11, 11, assessment, true)));
                JCheckBox confirmed = field(panel, "confirmed", JCheckBox.class);
                JButton graduate = field(panel, "graduate", JButton.class);
                assertEquals("学分审查已达标", field(panel, "assessmentState", JLabel.class).getText());
                assertTrue(confirmed.isEnabled());
                assertFalse(graduate.isEnabled());
                confirmed.doClick();
                assertTrue(graduate.isEnabled());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void graduatedAssessmentTakesPrecedenceOverStaleFlag() throws Exception {
        StudentRecord student = student("S001", "张三", "毕业");
        AcademicAssessment graduated = assessment("S001", 11, 11, 0, true);
        AcademicAdministrationPanel panel = panel(command -> null);
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.displayOverview(response(overview(student, 11, 11, graduated, false)));
                assertEquals("已办理毕业",
                        field(panel, "assessmentState", JLabel.class).getText());
                assertFalse(field(panel, "confirmed", JCheckBox.class).isEnabled());
                assertFalse(field(panel, "graduate", JButton.class).isEnabled());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void staleOrInsufficientAssessmentCannotEnableGraduation() throws Exception {
        StudentRecord student = student("S001", "张三", "在读");
        AcademicAdministrationPanel panel = panel(command -> null);
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.displayOverview(response(overview(student, 11, 11,
                        assessment("S001", 11, 11, 0, false), false)));
                assertEquals("最新审查已过期", field(panel, "assessmentState", JLabel.class).getText());
                assertFalse(field(panel, "confirmed", JCheckBox.class).isEnabled());
                assertFalse(field(panel, "graduate", JButton.class).isEnabled());

                panel.displayOverview(response(overview(student, 6, 11,
                        assessment("S001", 6, 11, 1, false), true)));
                assertEquals("学分审查未达标", field(panel, "assessmentState", JLabel.class).getText());
                assertFalse(field(panel, "confirmed", JCheckBox.class).isEnabled());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void rapidStudentSwitchDiscardsLateOverview() throws Exception {
        StudentRecord first = student("S001", "甲", "在读");
        StudentRecord second = student("S002", "乙", "在读");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReturned = new CountDownLatch(1);
        AcademicAdministrationPanel panel = panel(command -> null, command -> {
            if ("S001".equals(command.getStudentId())) {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                return response(overview(first, 11, 11, null, false));
            }
            secondReturned.countDown();
            return response(overview(second, 7, 12, null, false));
        });

        SwingUtilities.invokeAndWait(() -> panel.displayReviewStudents(
                response(Arrays.asList(first, second)), "S001"));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> fieldUnchecked(panel, "studentTable", JTable.class)
                .setRowSelectionInterval(1, 1));
        assertTrue(secondReturned.await(5, TimeUnit.SECONDS));
        awaitOverviewStudent(panel, "S002");
        releaseFirst.countDown();
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertEquals("S002  乙", field(panel, "studentTitle", JLabel.class).getText());
                assertEquals("7", field(panel, "earnedValue", JLabel.class).getText());
                assertEquals("12", field(panel, "requiredValue", JLabel.class).getText());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test void filteringDuringOverviewRequestDoesNotLeakBusyState() throws Exception {
        StudentRecord student = student("S001", "张三", "在读");
        CountDownLatch overviewStarted = new CountDownLatch(1);
        CountDownLatch releaseOverview = new CountDownLatch(1);
        AcademicAdministrationPanel panel = panel(command -> null, command -> {
            overviewStarted.countDown();
            assertTrue(releaseOverview.await(5, TimeUnit.SECONDS));
            return response(overview(student, 11, 11, null, false));
        });

        SwingUtilities.invokeAndWait(() -> panel.displayReviewStudents(
                response(Collections.singletonList(student)), "S001"));
        assertTrue(overviewStarted.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTextField search = field(panel, "reviewFilter", JTextField.class);
                assertFalse(search.isEnabled());
                search.setText("missing");
                field(panel, "studentTable", JTable.class).clearSelection();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        releaseOverview.countDown();
        awaitIdle(panel);
        assertFalse(field(panel, "reviewBusy", Boolean.class));
    }

    @Test void filteringDuringReviewRequestDoesNotLeakBusyState() throws Exception {
        StudentRecord student = student("S001", "张三", "在读");
        CountDownLatch reviewStarted = new CountDownLatch(1);
        CountDownLatch releaseReview = new CountDownLatch(1);
        AcademicAdministrationPanel panel = panel(command -> {
            if (command.getAction() == Action.REVIEW) {
                reviewStarted.countDown();
                assertTrue(releaseReview.await(5, TimeUnit.SECONDS));
                return response(assessment("S001", 11, 11, 0, false));
            }
            return Message.response(request(), StatusCode.BAD_REQUEST, "unexpected");
        }, command -> response(overview(student, 11, 11, null, false)));

        SwingUtilities.invokeAndWait(() -> panel.displayReviewStudents(
                response(Collections.singletonList(student)), "S001"));
        awaitIdle(panel);
        SwingUtilities.invokeAndWait(() -> fieldUnchecked(panel, "review", JButton.class).doClick());
        assertTrue(reviewStarted.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTextField search = field(panel, "reviewFilter", JTextField.class);
                JTable table = field(panel, "studentTable", JTable.class);
                assertFalse(search.isEnabled());
                assertFalse(table.isEnabled());
                search.setText("missing");
                table.clearSelection();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        releaseReview.countDown();
        awaitIdle(panel);
        assertFalse(field(panel, "reviewBusy", Boolean.class));
        assertFalse(field(panel, "writeBusy", Boolean.class));
        assertTrue(field(panel, "reviewFilter", JTextField.class).isEnabled());
        assertTrue(field(panel, "studentTable", JTable.class).isEnabled());
    }

    @Test void reviewSearchUsesModelIndexAfterSorting() throws Exception {
        StudentRecord first = student("S002", "乙", "在读");
        StudentRecord second = student("S001", "甲", "在读");
        AcademicAdministrationPanel panel = panel(command -> null, command -> response(overview(
                "S001".equals(command.getStudentId()) ? second : first, 3, 6, null, false)));
        SwingUtilities.invokeAndWait(() -> panel.displayReviewStudents(
                response(Arrays.asList(first, second)), null));
        awaitIdle(panel);
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable table = field(panel, "studentTable", JTable.class);
                table.getRowSorter().toggleSortOrder(0);
                JTextField search = field(panel, "reviewFilter", JTextField.class);
                search.setText("S002");
                assertEquals(1, table.getRowCount());
                table.setRowSelectionInterval(0, 0);
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        awaitOverviewStudent(panel, "S002");
    }

    @Test void workspaceHasStableSplitLayoutAndNoSharedResultTable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = panel(command -> null);
                JSplitPane split = find(panel, JSplitPane.class);
                assertNotNull(split);
                assertEquals(0.34d, split.getResizeWeight(), 0.001d);
                assertNotSame(field(panel, "directoryTable", JTable.class),
                        field(panel, "studentTable", JTable.class));
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    private static AcademicAdministrationPanel panel(AcademicAdministrationPanel.Loader loader) {
        return panel(loader, command -> null);
    }

    private static AcademicAdministrationPanel panel(AcademicAdministrationPanel.Loader loader,
            AcademicAdministrationPanel.OverviewLoader overviewLoader) {
        return new AcademicAdministrationPanel("token", loader, overviewLoader);
    }

    private static StudentRecord student(String id, String name, String status) {
        return new StudentRecord(id, "user-" + id, name, "未知", "计算机学院",
                "计算机科学与技术", "CS2026-01", 2026, status, "", "");
    }

    private static AcademicAssessment assessment(String id, int earned, int required,
            int pendingRetakes, boolean graduated) {
        AcademicAssessment result = new AcademicAssessment("review-" + id,
                new CreditSummary(id, earned, 4, pendingRetakes, 0), required,
                "hash", "academic", Instant.parse("2026-09-16T04:25:00Z"), "", null, null, null);
        return graduated ? result.graduate("academic", "") : result;
    }

    private static GraduationReviewOverview overview(StudentRecord student, int earned,
            int required, AcademicAssessment assessment, boolean current) {
        return overview(student, BigDecimal.valueOf(earned), BigDecimal.valueOf(required),
                assessment, current);
    }

    private static GraduationReviewOverview overview(StudentRecord student, BigDecimal earned,
            BigDecimal required, AcademicAssessment assessment, boolean current) {
        return new GraduationReviewOverview(student,
                new CreditSummary(student.getStudentId(), earned, 4, 0, 0),
                new GraduationCreditRequirement("PLAN-" + student.getStudentId(), required,
                        Collections.singletonList("course="
                                + required.stripTrailingZeros().toPlainString())),
                assessment, current);
    }

    private static Message request() {
        return Message.request("admin", MessageType.ACADEMIC_ADMIN_V2, null);
    }

    private static Message response(Object data) {
        return Message.response(request(), StatusCode.OK, data);
    }

    private static JTabbedPane tabs(AcademicAdministrationPanel panel) {
        return fieldUnchecked(panel, "operations", JTabbedPane.class);
    }

    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(object));
    }

    private static <T> T fieldUnchecked(Object object, String name, Class<T> type) {
        try {
            return field(object, name, type);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof java.awt.Container) {
                T found = find((java.awt.Container) component, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void awaitIdle(AcademicAdministrationPanel panel) throws Exception {
        for (int i = 0; i < 200; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            if (!field(panel, "studentListBusy", Boolean.class)
                    && !field(panel, "reviewBusy", Boolean.class)) return;
            Thread.sleep(20);
        }
        fail("panel did not become idle");
    }

    private static void awaitOverviewStudent(AcademicAdministrationPanel panel, String id)
            throws Exception {
        for (int i = 0; i < 200; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            GraduationReviewOverview value = field(panel, "overview", GraduationReviewOverview.class);
            if (value != null && id.equals(value.getStudent().getStudentId())) return;
            Thread.sleep(20);
        }
        fail("overview did not load for " + id);
    }
}
