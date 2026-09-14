package cn.vcampus.student;

import java.io.Serializable;

/** Updates an existing archive only if it still matches the state loaded by the editor. */
public final class StudentUpdateV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final StudentRecord record;
    private final StudentRecord expected;

    public StudentUpdateV2Command(String token, StudentRecord record, StudentRecord expected) {
        this.token = token;
        this.record = record;
        this.expected = expected;
        validate();
    }

    public void validate() {
        if (token == null || token.trim().isEmpty() || record == null || expected == null) {
            throw new IllegalArgumentException("更新档案需要登录凭据、新档案和加载时的旧档案");
        }
        if (!record.getStudentId().equals(expected.getStudentId())) {
            throw new IllegalArgumentException("新旧档案学号不一致");
        }
    }

    public String getToken() { return token; }
    public StudentRecord getRecord() { return record; }
    public StudentRecord getExpected() { return expected; }
}
