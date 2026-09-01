package cn.vcampus.student;

import java.io.Serializable;

/** Student record update command authorized on the server. */
public final class StudentUpdateCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final StudentRecord record;

    public StudentUpdateCommand(String token, StudentRecord record) {
        this.token = requireText(token, "token");
        if (record == null) throw new IllegalArgumentException("record must not be null");
        this.record = record;
    }

    public String getToken() { return token; }
    public StudentRecord getRecord() { return record; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
