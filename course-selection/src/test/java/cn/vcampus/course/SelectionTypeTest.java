package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 验证选课类别的中文显示名称。 */
class SelectionTypeTest {
    @Test
    void providesChineseDisplayNames() {
        assertEquals("主修", SelectionType.MAJOR.getDisplayName());
        assertEquals("选修", SelectionType.ELECTIVE.getDisplayName());
        assertEquals("重修", SelectionType.RETAKE.getDisplayName());
    }
}
