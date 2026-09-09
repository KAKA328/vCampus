package cn.vcampus.course;

import java.io.Serializable;

/**
 * 教师端成绩草稿命令。
 *
 * <p>命令不包含教师工号、成绩提交单编号和选课类别。服务器必须从 token 确定教师，
 * 从教学班有效选课记录确定学生是否可录入及其选课类别。</p>
 */
public final class CourseGradeDraftV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Operation {
        OPEN_DRAFT,
        SAVE_ENTRY,
        SUBMIT_FOR_REVIEW,
        LIST_AUDIT
    }

    private final String token;
    private final Operation operation;
    private final String offeringId;
    private final String studentId;
    private final Integer score;

    private CourseGradeDraftV2Command(String token, Operation operation, String offeringId,
            String studentId, Integer score) {
        this.token = requireText(token, "token");
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        this.operation = operation;
        this.offeringId = requireText(offeringId, "offeringId");
        this.studentId = normalize(studentId);
        this.score = score;
        if (operation == Operation.SAVE_ENTRY) {
            if (this.studentId == null || score == null || score < 0 || score > 100) {
                throw new IllegalArgumentException(
                        "studentId and score between 0 and 100 are required to save a grade");
            }
        }
    }

    /** 打开教学班成绩草稿；首次打开时服务端创建草稿。 */
    public static CourseGradeDraftV2Command openDraft(String token, String offeringId) {
        return new CourseGradeDraftV2Command(token, Operation.OPEN_DRAFT, offeringId, null, null);
    }

    /** 保存或覆盖一名有效选课学生的成绩。 */
    public static CourseGradeDraftV2Command saveEntry(String token, String offeringId,
            String studentId, int score) {
        return new CourseGradeDraftV2Command(token, Operation.SAVE_ENTRY, offeringId, studentId,
                Integer.valueOf(score));
    }

    /** 教师确认本班全体有效选课学生均已录入成绩后，提交教务审核。 */
    public static CourseGradeDraftV2Command submitForReview(String token, String offeringId) {
        return new CourseGradeDraftV2Command(token, Operation.SUBMIT_FOR_REVIEW, offeringId,
                null, null);
    }

    /** 查询本人教学班成绩单的提交、通过和退回记录。 */
    public static CourseGradeDraftV2Command listAudit(String token, String offeringId) {
        return new CourseGradeDraftV2Command(token, Operation.LIST_AUDIT, offeringId, null, null);
    }

    public String getToken() { return token; }
    public Operation getOperation() { return operation; }
    public String getOfferingId() { return offeringId; }
    public String getStudentId() { return studentId; }
    public int getScore() { return score == null ? -1 : score.intValue(); }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
