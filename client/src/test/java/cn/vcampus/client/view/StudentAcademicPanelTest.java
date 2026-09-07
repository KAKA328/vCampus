package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.CourseHistoryRecord;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentAcademicPanelTest {
    @Test void creditsModeDisplaysServerSummaryRatherThanSummingAttemptRows() throws Exception {
        StudentAcademicPanel panel = edt(() -> new StudentAcademicPanel(type ->
                Message.response(request(), StatusCode.OK, new cn.vcampus.student.CreditSummary("S001", 6, 2, 0, 1))));
        edt(() -> { field(panel, "query", JComboBox.class).setSelectedIndex(2); return null; });
        awaitLoaded(panel);
        assertEquals(6, edt(() -> table(panel).getValueAt(0, 1)));
        assertEquals(1, edt(() -> table(panel).getRowCount()));
        assertTrue(edt(() -> status(panel).contains("去重")));
        assertEquals("6", edt(() -> field(panel, "earned", JLabel.class).getText()));
    }

    @Test void creditCardsClearWhenRefreshFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StudentAcademicPanel panel = edt(() -> new StudentAcademicPanel(type -> calls.incrementAndGet() == 1
                ? Message.response(request(), StatusCode.OK, new cn.vcampus.student.CreditSummary("S001", 6, 2, 0, 1))
                : failure(StatusCode.SERVER_ERROR)));
        edt(() -> { field(panel, "query", JComboBox.class).setSelectedIndex(2); return null; });
        awaitLoaded(panel);
        edt(() -> { panel.reload(); return null; }); awaitLoaded(panel);
        assertEquals("—", edt(() -> field(panel, "earned", JLabel.class).getText()));
        assertTrue(edt(() -> status(panel).contains("教务")));
    }

    @Test void showsServerDataAndClearsPreviousRowsOnFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StudentAcademicPanel panel = edt(() -> new StudentAcademicPanel(type ->
                calls.incrementAndGet() == 1 ? success("JAVA") : failure(StatusCode.SERVER_ERROR)));
        edt(() -> { panel.reload(); return null; });
        awaitLoaded(panel);
        assertEquals("JAVA", edt(() -> table(panel).getValueAt(0, 0)));
        assertFalse(edt(() -> table(panel).isCellEditable(0, 0)));
        edt(() -> { panel.reload(); assertEquals(0, table(panel).getRowCount()); return null; });
        awaitLoaded(panel);
        assertEquals(0, edt(() -> table(panel).getRowCount()));
        assertTrue(edt(() -> status(panel).contains("教务")));
    }

    @Test void slowHistoryCannotOverwriteNewRetakeQuery() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        StudentAcademicPanel panel = edt(() -> new StudentAcademicPanel(type -> {
            if (calls.incrementAndGet() == 1) {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                    return success("OLD");
                } finally { returned.countDown(); }
            }
            return success("NEW");
        }));
        try {
            edt(() -> { panel.reload(); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            edt(() -> { field(panel, "query", JComboBox.class).setSelectedIndex(1); return null; });
            awaitLoaded(panel);
            assertEquals("NEW", edt(() -> table(panel).getValueAt(0, 0)));
            release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            // SwingWorker batches done callbacks; let that EDT batch run before checking.
            CountDownLatch batch = new CountDownLatch(1);
            edt(() -> { javax.swing.Timer timer = new javax.swing.Timer(150, event -> batch.countDown());
                timer.setRepeats(false); timer.start(); return null; });
            assertTrue(batch.await(5, TimeUnit.SECONDS));
            assertEquals("NEW", edt(() -> table(panel).getValueAt(0, 0)));
        } finally { release.countDown(); }
    }

    @Test void emptyUnboundAndMalformedResponsesHaveDistinctStates() throws Exception {
        Message[] replies = {
                Message.response(request(), StatusCode.OK, Collections.emptyList()),
                failure(StatusCode.NOT_FOUND),
                Message.response(request(), StatusCode.OK, Collections.singletonList("invalid"))
        };
        AtomicInteger calls = new AtomicInteger();
        StudentAcademicPanel panel = edt(() -> new StudentAcademicPanel(type -> replies[calls.getAndIncrement()]));
        edt(() -> { panel.reload(); return null; }); awaitLoaded(panel);
        assertTrue(edt(() -> status(panel).contains("暂无")));
        edt(() -> { panel.reload(); return null; }); awaitLoaded(panel);
        assertTrue(edt(() -> status(panel).contains("绑定")));
        edt(() -> { panel.reload(); return null; }); awaitLoaded(panel);
        assertTrue(edt(() -> status(panel).contains("失败")));
        assertEquals(0, edt(() -> table(panel).getRowCount()));
    }

    private static Message success(String course) {
        return Message.response(request(), StatusCode.OK, Collections.singletonList(
                new CourseHistoryRecord("S001", course, course, "2026-2027-1", 1, "首修", 50, false, 0)));
    }
    private static Message failure(StatusCode code) { return Message.response(request(), code, "error"); }
    private static Message request() { return Message.request("test", MessageType.STUDENT_ACADEMIC_QUERY_V1, null); }
    private static JTable table(StudentAcademicPanel panel) throws Exception { return field(panel, "table", JTable.class); }
    private static String status(StudentAcademicPanel panel) throws Exception { return field(panel, "status", JLabel.class).getText(); }

    private static void awaitLoaded(StudentAcademicPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (edt(() -> field(panel, "refresh", JButton.class).isEnabled())) return;
            Thread.sleep(10);
        }
        fail("academic query did not finish");
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<T>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get(5, TimeUnit.SECONDS);
    }
}
