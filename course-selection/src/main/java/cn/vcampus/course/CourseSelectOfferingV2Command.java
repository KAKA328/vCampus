package cn.vcampus.course;

import java.io.Serializable;

/**
 * 完整选课流程 V2 选课请求。
 *
 * <p>学生身份由服务端根据 token 推导；客户端提交轮次和教学班编号。
 */
public final class CourseSelectOfferingV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String roundId;
    private final String offeringId;

    public CourseSelectOfferingV2Command(String token, String roundId, String offeringId) {
        this.token = requireText(token, "token");
        this.roundId = requireText(roundId, "roundId");
        this.offeringId = requireText(offeringId, "offeringId");
    }

    public String getToken() { return token; }
    public String getRoundId() { return roundId; }
    public String getOfferingId() { return offeringId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
