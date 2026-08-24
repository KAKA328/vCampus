package cn.vcampus.common;

import java.io.Serializable;
import java.util.Objects;

/** Serializable request/response envelope sent over the Socket object stream. */
public final class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final MessageType type;
    private final StatusCode statusCode;
    private final String sender;
    private final Object payload;

    private Message(String requestId, MessageType type, StatusCode statusCode, String sender, Object payload) {
        this.requestId = requireText(requestId, "requestId");
        this.type = Objects.requireNonNull(type, "type");
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.sender = sender;
        this.payload = payload;
    }

    public static Message request(String requestId, MessageType type, Object payload) {
        return new Message(requestId, type, StatusCode.OK, null, payload);
    }

    public static Message response(Message request, StatusCode statusCode, Object payload) {
        Objects.requireNonNull(request, "request");
        return new Message(request.requestId, request.type, statusCode, request.sender, payload);
    }

    public Message withSender(String value) {
        return new Message(requestId, type, statusCode, value, payload);
    }

    public String getRequestId() { return requestId; }
    public MessageType getType() { return type; }
    public StatusCode getStatusCode() { return statusCode; }
    public String getSender() { return sender; }
    public Object getPayload() { return payload; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Message)) return false;
        Message that = (Message) other;
        return requestId.equals(that.requestId) && type == that.type && statusCode == that.statusCode
                && Objects.equals(sender, that.sender) && Objects.equals(payload, that.payload);
    }

    @Override public int hashCode() { return Objects.hash(requestId, type, statusCode, sender, payload); }
}
