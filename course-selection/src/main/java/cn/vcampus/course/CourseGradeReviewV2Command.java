package cn.vcampus.course;

import java.io.Serializable;

/** 教务端查询和处理任课教师提交的成绩单。 */
public final class CourseGradeReviewV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Operation {
        LIST_PENDING,
        VIEW_DETAIL,
        APPROVE,
        RETURN
    }

    private final String token;
    private final Operation operation;
    private final String submissionId;
    private final String remark;

    private CourseGradeReviewV2Command(String token, Operation operation, String submissionId,
            String remark) {
        this.token = requireText(token, "token");
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        this.operation = operation;
        this.submissionId = normalize(submissionId);
        this.remark = normalize(remark);
        if (operation != Operation.LIST_PENDING && this.submissionId == null) {
            throw new IllegalArgumentException("submissionId must not be blank");
        }
        if (operation == Operation.RETURN && this.remark == null) {
            throw new IllegalArgumentException("remark must not be blank when returning a submission");
        }
    }

    public static CourseGradeReviewV2Command listPending(String token) {
        return new CourseGradeReviewV2Command(token, Operation.LIST_PENDING, null, null);
    }

    public static CourseGradeReviewV2Command viewDetail(String token, String submissionId) {
        return new CourseGradeReviewV2Command(token, Operation.VIEW_DETAIL, submissionId, null);
    }

    public static CourseGradeReviewV2Command approve(String token, String submissionId,
            String remark) {
        return new CourseGradeReviewV2Command(token, Operation.APPROVE, submissionId, remark);
    }

    public static CourseGradeReviewV2Command returnForRevision(String token, String submissionId,
            String remark) {
        return new CourseGradeReviewV2Command(token, Operation.RETURN, submissionId, remark);
    }

    public String getToken() { return token; }
    public Operation getOperation() { return operation; }
    public String getSubmissionId() { return submissionId; }
    public String getRemark() { return remark; }

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
