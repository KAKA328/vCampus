package cn.vcampus.course;

import java.io.Serializable;

/** 完整选课流程 V2 退选请求：按已选记录编号退选。 */
public final class CourseDropRecordV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String recordId;

    public CourseDropRecordV2Command(String token, String recordId) {
        this.token = requireText(token, "token");
        this.recordId = requireText(recordId, "recordId");
    }

    public String getToken() { return token; }
    public String getRecordId() { return recordId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
