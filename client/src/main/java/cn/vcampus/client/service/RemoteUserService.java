package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.user.PasswordChangeCommand;
import cn.vcampus.user.PasswordResetRequestCommand;
import cn.vcampus.user.PasswordResetReviewCommand;
import cn.vcampus.user.UserCommand;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserImportCommand;
import cn.vcampus.user.UserImportRow;
import cn.vcampus.user.UserRegistrationCommand;
import cn.vcampus.user.UserRoleChangeCommand;
import cn.vcampus.user.UserStatusCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Client-side adapter for user-management messages. */
public final class RemoteUserService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteUserService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message register(String token, UserCredentials credentials) throws IOException, ClassNotFoundException {
        return send(MessageType.REGISTER, new UserRegistrationCommand(token, credentials));
    }

    public Message importUsers(String token, List<UserImportRow> rows) throws IOException, ClassNotFoundException {
        return send(MessageType.USER_IMPORT, new UserImportCommand(token, rows));
    }

    public Message listAccounts(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.USER_LIST, token);
    }

    public Message setAccountActive(String token, String userId, boolean active)
            throws IOException, ClassNotFoundException {
        return send(active ? MessageType.USER_ENABLE : MessageType.USER_DISABLE,
                new UserStatusCommand(token, userId, active));
    }

    public Message changeUserRole(String token, String userId, String roleCode)
            throws IOException, ClassNotFoundException {
        return send(MessageType.USER_ROLE_CHANGE, new UserRoleChangeCommand(token, userId, roleCode));
    }

    public Message unregister(String token, String userId) throws IOException, ClassNotFoundException {
        return send(MessageType.UNREGISTER, new UserCommand(userId, token));
    }

    public Message listAuditEvents(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.USER_AUDIT_LIST, token);
    }

    public Message requestPasswordReset(String userId, String reason) throws IOException, ClassNotFoundException {
        return send(MessageType.PASSWORD_RESET_REQUEST, new PasswordResetRequestCommand(userId, reason));
    }

    public Message requestPasswordReset(String userId, String reason, String contactInfo)
            throws IOException, ClassNotFoundException {
        return send(MessageType.PASSWORD_RESET_REQUEST,
                new PasswordResetRequestCommand(userId, reason, contactInfo));
    }

    public Message listPasswordResetApplications(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.PASSWORD_RESET_LIST, token);
    }

    public Message reviewPasswordReset(String token, String userId, boolean approved)
            throws IOException, ClassNotFoundException {
        return send(MessageType.PASSWORD_RESET_REVIEW,
                new PasswordResetReviewCommand(token, userId, approved));
    }

    public Message changeForcedPassword(String token, String newPassword)
            throws IOException, ClassNotFoundException {
        return send(MessageType.PASSWORD_CHANGE, new PasswordChangeCommand(token, newPassword));
    }

    public Message login(UserCredentials credentials) throws IOException, ClassNotFoundException {
        return send(MessageType.LOGIN, credentials);
    }

    public Message logout(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.LOGOUT, token);
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("gui-" + sequence.incrementAndGet(), type, payload));
    }

    @Override public void close() throws IOException {
        messages.close();
    }
}
