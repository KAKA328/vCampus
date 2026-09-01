package cn.vcampus.course;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 选课服务所需的学生资料快照。
 *
 * <p>该对象由服务端根据登录账号和学籍模块数据创建，绝不能由客户端提交。学籍模块正式接口
 * 完成后，只需替换创建本对象的适配层，不需要改变选课核心规则。</p>
 */
public final class StudentSelectionProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String studentId;
    private final String majorName;
    private final int enrollmentYear;
    private final String studentStatus;
    private final String currentTerm;
    private final int recommendedTerm;
    private final Set<String> pendingRetakeCourseIds;

    public StudentSelectionProfile(String userId, String studentId, String majorName,
            int enrollmentYear, String studentStatus, String currentTerm, int recommendedTerm,
            Set<String> pendingRetakeCourseIds) {
        this.userId = requireText(userId, "userId");
        this.studentId = requireText(studentId, "studentId");
        this.majorName = requireText(majorName, "majorName");
        if (enrollmentYear < 1900 || enrollmentYear > 9999) {
            throw new IllegalArgumentException("enrollmentYear must be a four digit year");
        }
        this.studentStatus = requireText(studentStatus, "studentStatus");
        this.currentTerm = requireText(currentTerm, "currentTerm");
        if (recommendedTerm <= 0) {
            throw new IllegalArgumentException("recommendedTerm must be positive");
        }
        if (pendingRetakeCourseIds == null) {
            throw new IllegalArgumentException("pendingRetakeCourseIds must not be null");
        }
        Set<String> copiedCourseIds = new LinkedHashSet<String>();
        for (String courseId : pendingRetakeCourseIds) {
            copiedCourseIds.add(requireText(courseId, "pendingRetakeCourseId"));
        }
        this.enrollmentYear = enrollmentYear;
        this.recommendedTerm = recommendedTerm;
        this.pendingRetakeCourseIds = Collections.unmodifiableSet(copiedCourseIds);
    }

    public String getUserId() { return userId; }
    public String getStudentId() { return studentId; }
    public String getMajorName() { return majorName; }
    public int getEnrollmentYear() { return enrollmentYear; }
    public String getStudentStatus() { return studentStatus; }
    public String getCurrentTerm() { return currentTerm; }
    public int getRecommendedTerm() { return recommendedTerm; }
    public Set<String> getPendingRetakeCourseIds() { return pendingRetakeCourseIds; }

    /** 只有“在读”学生可发起新的选课或退选。 */
    public boolean isActiveStudent() {
        return "在读".equals(studentStatus);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
