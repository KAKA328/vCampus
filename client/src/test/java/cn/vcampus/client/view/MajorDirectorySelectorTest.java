package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MajorDirectorySelectorTest {
    @Test void selectionIsRestrictedAndSubmissionKeepsMajorName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MajorDirectorySelector selector = new MajorDirectorySelector(() -> null, () -> {});
            assertFalse(selector.canSave());
            assertThrows(IllegalArgumentException.class, selector::selectedName);
            selector.accept(response(StatusCode.OK, Arrays.asList(
                    new MajorDirectoryEntry("SE", "软件工程", "计算机学院", true),
                    new MajorDirectoryEntry("CS", "计算机科学", "计算机学院", true))));
            selector.selectName("软件工程");
            assertEquals("软件工程", selector.selectedName());
            JComboBox<?> options = findCombo(selector);
            assertFalse(options.isEditable());
            assertEquals("SE - 软件工程", options.getSelectedItem().toString());
            selector.selectName("未知旧专业");
            assertFalse(selector.canSave());
            selector.selectName("软件工程");
            selector.accept(response(StatusCode.SERVER_ERROR, "缺少目录表"));
            assertFalse(selector.canSave());
            assertEquals(0, options.getItemCount());
            selector.accept(response(StatusCode.OK, Collections.emptyList()));
            assertFalse(selector.canSave());
            selector.accept(response(StatusCode.OK, Collections.singletonList(
                    new MajorDirectoryEntry("OLD", "停用专业", "院系", false))));
            assertFalse(selector.canSave());
            selector.accept(response(StatusCode.OK, Collections.singletonList("malformed")));
            assertFalse(selector.canSave());
        });
    }
    private static JComboBox<?> findCombo(java.awt.Container parent) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?>) return (JComboBox<?>) child;
            if (child instanceof java.awt.Container) {
                JComboBox<?> found = findCombo((java.awt.Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static Message response(StatusCode status, Object data) {
        return Message.response(Message.request("majors", MessageType.STUDENT_MAJOR_DIRECTORY_QUERY_V1, null), status, data);
    }
}
