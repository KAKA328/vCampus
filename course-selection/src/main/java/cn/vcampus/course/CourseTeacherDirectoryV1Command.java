package cn.vcampus.course;

import java.io.Serializable;

/** 教务端查询可分配任课教师的请求；教师范围始终由服务端过滤。 */
public final class CourseTeacherDirectoryV1Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;

    public CourseTeacherDirectoryV1Command(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.token = token.trim();
    }

    public String getToken() { return token; }
}
