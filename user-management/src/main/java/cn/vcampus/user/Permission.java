package cn.vcampus.user;

/** Stable permission codes used by client/server messages. */
public enum Permission {
    USER_SELF_READ,
    USER_MANAGE,
    STUDENT_READ,
    STUDENT_WRITE,
    COURSE_READ,
    COURSE_SELECT,
    COURSE_MANAGE,
    GRADE_WRITE,
    ACADEMIC_REVIEW,
    LIBRARY_READ,
    LIBRARY_BORROW,
    LIBRARY_MANAGE,
    STORE_READ,
    STORE_PURCHASE,
    STORE_MANAGE;

    public String getCode() {
        return name();
    }

    public static Permission fromCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            return Permission.valueOf(code.trim());
        } catch (IllegalArgumentException unknownCode) {
            return null;
        }
    }
}
