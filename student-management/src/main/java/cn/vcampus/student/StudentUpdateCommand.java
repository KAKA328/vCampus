package cn.vcampus.student;

import java.io.Serializable;

/** Serializable command for saving a student profile. */
public final class StudentUpdateCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final StudentRecord record;

    public StudentUpdateCommand(String token, StudentRecord record) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        this.token = token.trim();
        this.record = record;
    }

    public String getToken() { return token; }
    public StudentRecord getRecord() { return record; }
}
