package cn.vcampus.store;

import java.io.Serializable;

public final class StoreOrderQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String studentId;

    public StoreOrderQueryCommand(String token, String studentId) {
        this.token = checkStr(token, "token");
        this.studentId = checkStr(studentId, "studentId");
    }

    public String getToken() {
        return token;
    }

    public String getStudentId() {
        return studentId;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}
