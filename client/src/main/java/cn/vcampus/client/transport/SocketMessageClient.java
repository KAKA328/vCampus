package cn.vcampus.client.transport;

import cn.vcampus.common.Message;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/** Thin Socket client for sending Message objects to the server. */
public final class SocketMessageClient implements Closeable {
    // 读超时：服务端无响应（崩溃/挂起）时 readObject 不会无限阻塞，抛 SocketTimeoutException
    private static final int SO_TIMEOUT_MS = 15000;

    private final Socket socket;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    public SocketMessageClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(SO_TIMEOUT_MS);
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized Message send(Message request) throws IOException, ClassNotFoundException {
        output.writeObject(request);
        output.flush();
        return (Message) input.readObject();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
