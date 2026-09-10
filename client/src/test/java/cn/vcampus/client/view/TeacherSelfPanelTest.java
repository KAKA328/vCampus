package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeacherSelfPanelTest {
    @Test void teacherEntryContainsOnlySelfPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StudentManagementPanel panel = new StudentManagementPanel("127.0.0.1", 1,
                    new Session("token", new User("teacher", "教师", Role.TEACHER)));
            assertEquals(1, panel.getComponentCount());
            assertTrue(((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER)
                    instanceof TeacherSelfPanel);
        });
    }

    @Test void fieldsAreReadOnlyShowInactiveAndClearOnFailure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                TeacherSelfPanel panel = new TeacherSelfPanel(() -> null);
                java.lang.reflect.Field field = panel.getClass().getDeclaredField("values");
                field.setAccessible(true);
                JTextField[] values = (JTextField[]) field.get(panel);
                Message request = Message.request("self", MessageType.TEACHER_SELF_QUERY_V1, null);
                panel.showResponse(Message.response(request, StatusCode.OK,
                        new TeacherProfile("T001", "teacher", "教师", "院系", "讲师", false)));
                assertEquals("T001", values[0].getText());
                assertEquals("非在职", values[5].getText());
                for (JTextField value : values) assertFalse(value.isEditable());
                panel.showResponse(Message.response(request, StatusCode.NOT_FOUND, null));
                for (JTextField value : values) assertEquals("", value.getText());
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    @Test void firstShowLoadsExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Message request = Message.request("self", MessageType.TEACHER_SELF_QUERY_V1, null);
        TeacherSelfPanel panel = onEdt(() -> new TeacherSelfPanel(() -> {
            calls.incrementAndGet();
            return Message.response(request, StatusCode.OK,
                    new TeacherProfile("T001", "teacher", "教师", "院系", "讲师", true));
        }));

        SwingUtilities.invokeAndWait(() -> {
            panel.loadIfNeeded(false);
            panel.loadIfNeeded(true);
            panel.loadIfNeeded(true);
        });

        waitUntil(() -> "T001".equals(fields(panel)[0].getText()));
        assertEquals(1, calls.get());
    }

    @Test void removingPanelDiscardsLateResponse() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Message request = Message.request("self", MessageType.TEACHER_SELF_QUERY_V1, null);
        TeacherSelfPanel panel = onEdt(() -> new TeacherSelfPanel(() -> {
            started.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return Message.response(request, StatusCode.OK,
                    new TeacherProfile("T-LATE", "teacher", "迟到响应", "院系", "讲师", true));
        }));

        SwingUtilities.invokeAndWait(panel::reload);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(panel::removeNotify);
        release.countDown();
        waitUntil(() -> refresh(panel).isEnabled());
        SwingUtilities.invokeAndWait(() -> {
            for (JTextField field : fields(panel)) assertEquals("", field.getText());
        });
    }

    private static JTextField[] fields(TeacherSelfPanel panel) {
        return (JTextField[]) field(panel, "values");
    }

    private static JButton refresh(TeacherSelfPanel panel) {
        return (JButton) field(panel, "refresh");
    }

    private static Object field(TeacherSelfPanel panel, String name) {
        try {
            java.lang.reflect.Field field = TeacherSelfPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<T>();
        java.util.concurrent.atomic.AtomicReference<Exception> failure = new java.util.concurrent.atomic.AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(action.call()); }
            catch (Exception problem) { failure.set(problem); }
        });
        if (failure.get() != null) throw failure.get();
        return value.get();
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            AtomicInteger matched = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> matched.set(condition.getAsBoolean() ? 1 : 0));
            if (matched.get() == 1) return;
            Thread.sleep(10L);
        }
        fail("condition was not met before timeout");
    }
}
