package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 验证选课身份的中文显示名称及其容量池映射。 */
class SelectionTypeTest {
    @Test
    void providesChineseDisplayNamesAndCapacityBuckets() {
        assertEquals("必修", SelectionType.REQUIRED.getDisplayName());
        assertEquals("选修", SelectionType.ELECTIVE.getDisplayName());
        assertEquals("跨专业选修", SelectionType.CROSS_MAJOR.getDisplayName());
        assertEquals("重修", SelectionType.RETAKE.getDisplayName());

        assertEquals(CapacityBucket.REQUIRED, SelectionType.REQUIRED.getCapacityBucket());
        assertEquals(CapacityBucket.ELECTIVE, SelectionType.ELECTIVE.getCapacityBucket());
        assertEquals(CapacityBucket.CROSS_MAJOR, SelectionType.CROSS_MAJOR.getCapacityBucket());
        assertEquals(CapacityBucket.REQUIRED, SelectionType.RETAKE.getCapacityBucket());
    }
}
