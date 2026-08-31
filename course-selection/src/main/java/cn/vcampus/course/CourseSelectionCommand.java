package cn.vcampus.course;

import java.io.Serializable;

/**
 * 学生选择某个教学班的网络请求。学生身份由服务器 token 推导，客户端不再提交 studentId。
 */
public final class CourseSelectionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String roundId;
    private final String offeringId;

    public CourseSelectionCommand(String token, String roundId, String offeringId) {
        this.token = requireText(token, "token");
        this.roundId = requireText(roundId, "roundId");
        this.offeringId = requireText(offeringId, "offeringId");
    }

    public String getToken() {
        return token;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getOfferingId() {
        return offeringId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
