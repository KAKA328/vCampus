package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.user.UserManagementService;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal multi-client server entry point; replace the in-memory service with Access-backed services. */
public final class ServerApplication implements Closeable {
    public static final int DEFAULT_PORT = 19090;

    private final int port;
    private final UserMessageHandler userMessages;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    public ServerApplication(int port, UserManagementService users) {
        this.port = port;
        this.userMessages = new UserMessageHandler(users);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("vCampus server listening on port " + port);
        while (!serverSocket.isClosed()) {
            try {
                clients.submit(new ClientHandler(serverSocket.accept()));
            } catch (IOException acceptedFailure) {
                if (!serverSocket.isClosed()) throw acceptedFailure;
            }
        }
    }

    @Override public void close() throws IOException {
        if (serverSocket != null) serverSocket.close();
        clients.shutdownNow();
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private ClientHandler(Socket socket) { this.socket = socket; }

        @Override public void run() {
            try (Socket client = socket;
                 ObjectInputStream input = new ObjectInputStream(client.getInputStream());
                 ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                while (!client.isClosed()) {
                    Message request;
                    try { request = (Message) input.readObject(); }
                    catch (EOFException end) { break; }
                    output.writeObject(dispatch(request));
                    output.flush();
                }
            } catch (IOException | ClassNotFoundException failure) {
                System.err.println("client connection closed: " + failure.getMessage());
            }
        }

        private Message dispatch(Message request) {
            return userMessages.handle(request);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        new ServerApplication(port, UserServiceFactory.create(args)).start();
    }

    private static int parsePort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        if (args.length > 0 && args[0].matches("\\d+")) return Integer.parseInt(args[0]);
        return DEFAULT_PORT;
    }
}
