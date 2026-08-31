package cn.vcampus.course;

import java.io.Serializable;

/**
 * 培养方案中的一门课程要求。
 *
 * <p>它描述“学生应当学习什么”，并不等同于某个实际开设的教学班。重修课程不写入
 * 培养方案，而是应由学籍模块根据学生的历史成绩单独计算。</p>
 */
public final class TrainingPlanCourse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final int recommendedTerm;
    private final SelectionType selectionType;
    private final boolean crossMajorAllowed;

    /**
     * 创建一条培养方案课程要求。
     *
     * @param courseId 课程编号
     * @param recommendedTerm 建议修读的第几个学期，从 1 开始
     * @param selectionType 本专业学生修读时的课程类别，只能是必修、选修或跨专业选修
     * @param crossMajorAllowed 是否允许其他专业学生以跨专业选修身份选择
     */
    public TrainingPlanCourse(String courseId, int recommendedTerm,
            SelectionType selectionType, boolean crossMajorAllowed) {
        this.courseId = requireText(courseId, "courseId");
        if (recommendedTerm <= 0) {
            throw new IllegalArgumentException("recommendedTerm must be positive");
        }
        if (selectionType == null || selectionType == SelectionType.RETAKE) {
            throw new IllegalArgumentException(
                    "selectionType must be REQUIRED, ELECTIVE or CROSS_MAJOR");
        }
        this.recommendedTerm = recommendedTerm;
        this.selectionType = selectionType;
        this.crossMajorAllowed = crossMajorAllowed;
    }

    public String getCourseId() {
        return courseId;
    }

    public int getRecommendedTerm() {
        return recommendedTerm;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public boolean isCrossMajorAllowed() {
        return crossMajorAllowed;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
