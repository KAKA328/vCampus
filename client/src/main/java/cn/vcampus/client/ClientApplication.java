package cn.vcampus.client;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.AuthorizationRequest;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/** Minimal client entry point used to verify the object-stream protocol. */
public final class ClientApplication {
    private ClientApplication() { }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 19090;
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            UserCredentials demo = new UserCredentials("demo-student-" + System.currentTimeMillis(),
                    "demo123", "Demo Student", "STUDENT");

            Message registerResponse = exchange(output, input,
                    Message.request("demo-register", MessageType.REGISTER, demo));
            printResult(registerResponse, "REGISTER", StatusCode.OK);

            Message loginResponse = exchange(output, input,
                    Message.request("demo-login", MessageType.LOGIN, demo));
            printResult(loginResponse, "LOGIN", StatusCode.OK);
            Session session = requireSession(loginResponse);

            Message authorizeResponse = exchange(output, input,
                    Message.request("demo-authorize-course-select", MessageType.AUTHORIZE,
                            new AuthorizationRequest(session.getToken(), Permission.COURSE_SELECT.getCode())));
            printResult(authorizeResponse, "AUTHORIZE COURSE_SELECT", StatusCode.OK);

            Message logoutResponse = exchange(output, input,
                    Message.request("demo-logout", MessageType.LOGOUT, session.getToken()));
            printResult(logoutResponse, "LOGOUT", StatusCode.OK);

            Message oldTokenResponse = exchange(output, input,
                    Message.request("demo-authorize-old-token", MessageType.AUTHORIZE,
                            new AuthorizationRequest(session.getToken(), Permission.COURSE_SELECT.getCode())));
            printResult(oldTokenResponse, "AUTHORIZE OLD_TOKEN", StatusCode.UNAUTHORIZED);
        }
    }

    private static Message exchange(ObjectOutputStream output, ObjectInputStream input, Message request)
            throws IOException, ClassNotFoundException {
        output.writeObject(request);
        output.flush();
        return (Message) input.readObject();
    }

    private static Session requireSession(Message loginResponse) {
        if (!(loginResponse.getPayload() instanceof Session)) {
            throw new IllegalStateException("login did not return a session");
        }
        return (Session) loginResponse.getPayload();
    }

    private static void printResult(Message response, String operation, StatusCode expected) {
        System.out.println(response.getRequestId() + " " + operation
                + " actual=" + response.getStatusCode()
                + " expected=" + expected);
    }
}
