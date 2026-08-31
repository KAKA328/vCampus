package cn.vcampus.course;

import java.io.Serializable;

/** 学生退选一条已选教学班记录的网络请求。 */
public final class CourseDropCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String recordId;

    public CourseDropCommand(String token, String recordId) {
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
