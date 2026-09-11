package cn.vcampus.client.view;

import javax.swing.JComboBox;

/** 选课模块统一使用的三个预设学期，避免在不同管理页录入不一致的学期文本。 */
final class CourseTermOptions {
    static final String[] VALUES = { "2026-2027-1", "2025-2026-2", "2025-2026-1" };

    private CourseTermOptions() {
    }

    static JComboBox<String> comboBox() {
        return new JComboBox<String>(VALUES);
    }
}
