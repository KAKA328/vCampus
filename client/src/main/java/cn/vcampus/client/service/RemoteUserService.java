package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.user.UserCommand;
import cn.vcampus.user.UserCredentials;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Client-side adapter for user-management messages. */
public final class RemoteUserService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteUserService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message register(UserCredentials credentials) throws IOException, ClassNotFoundException {
        return send(MessageType.REGISTER, credentials);
    }

    public Message login(UserCredentials credentials) throws IOException, ClassNotFoundException {
        return send(MessageType.LOGIN, credentials);
    }

    public Message logout(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.LOGOUT, token);
    }

    public Message unregister(String userId, String token) throws IOException, ClassNotFoundException {
        return send(MessageType.UNREGISTER, new UserCommand(userId, token));
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("gui-" + sequence.incrementAndGet(), type, payload));
    }

    @Override public void close() throws IOException {
        messages.close();
    }
}
