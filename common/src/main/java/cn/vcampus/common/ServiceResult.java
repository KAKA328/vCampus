package cn.vcampus.common;

import java.io.Serializable;

/** Generic service result that can be mapped to a protocol response. */
public final class ServiceResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final StatusCode status;
    private final T data;
    private final String message;

    private ServiceResult(StatusCode status, T data, String message) {
        this.status = status; this.data = data; this.message = message;
    }
    public static <T> ServiceResult<T> ok(T data) { return new ServiceResult<T>(StatusCode.OK, data, "OK"); }
    public static <T> ServiceResult<T> failure(StatusCode status, String message) {
        if (status == StatusCode.OK) throw new IllegalArgumentException("failure cannot use OK");
        return new ServiceResult<T>(status, null, message);
    }
    public StatusCode getStatus() { return status; }
    public T getData() { return data; }
    public String getMessage() { return message; }
}
