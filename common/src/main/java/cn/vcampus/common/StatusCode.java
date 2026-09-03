package cn.vcampus.common;

/** Transport-level response status. */
public enum StatusCode {
    OK, BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, SERVER_ERROR,
    PAYMENT_REQUIRED;
}
