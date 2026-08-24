package cn.vcampus.common;

/** Stable message names shared by client and server. */
public enum MessageType {
    REGISTER, UNREGISTER, LOGIN, LOGOUT, AUTHORIZE,
    STUDENT_QUERY, STUDENT_UPDATE, COURSE_QUERY, COURSE_SELECT, COURSE_DROP,
    LIBRARY_QUERY, LIBRARY_BORROW, LIBRARY_RETURN, STORE_QUERY, STORE_PURCHASE
}
