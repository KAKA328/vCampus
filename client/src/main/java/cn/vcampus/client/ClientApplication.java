package cn.vcampus.client;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.client.view.LoginFrame;
import cn.vcampus.user.AuthorizationRequest;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserRegistrationCommand;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.swing.SwingUtilities;

/** Client entry point for Swing UI or the object-stream protocol demo. */
public final class ClientApplication {
    private ClientApplication() { }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String host = valueAfter(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(valueAfter(args, "--port", "19090"));
        if (contains(args, "--demo")) {
            runDemo(host, port, args);
            return;
        }
        SwingUtilities.invokeLater(() -> new LoginFrame(host, port).setVisible(true));
    }

    private static void runDemo(String host, int port, String[] args) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            UserCredentials admin = new UserCredentials(
                    valueAfter(args, "--admin-id", "demo_admin"),
                    valueAfter(args, "--admin-password", "Demo123"),
                    "Demo Administrator",
                    Role.ADMIN.name());
            UserCredentials demo = demoCredentials(System.currentTimeMillis());

            Message adminLoginResponse = exchange(output, input,
                    Message.request("demo-admin-login", MessageType.LOGIN, admin));
            printResult(adminLoginResponse, "LOGIN ADMIN", StatusCode.OK);
            Session adminSession = requireSession(adminLoginResponse);

            Message registerResponse = exchange(output, input,
                    Message.request("demo-admin-register", MessageType.REGISTER,
                            new UserRegistrationCommand(adminSession.getToken(), demo)));
            printResult(registerResponse, "REGISTER", StatusCode.OK);

            Message adminLogoutResponse = exchange(output, input,
                    Message.request("demo-admin-logout", MessageType.LOGOUT, adminSession.getToken()));
            printResult(adminLogoutResponse, "LOGOUT ADMIN", StatusCode.OK);

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

    static UserCredentials demoCredentials(long suffix) {
        return new UserCredentials("demo_student_" + suffix, "demo123", "Demo Student", "STUDENT");
    }

    static UserCredentials demoAdminCredentials() {
        return new UserCredentials("demo_admin", "Demo123", "Demo Administrator", Role.ADMIN.name());
    }

    private static boolean contains(String[] args, String option) {
        for (String arg : args) if (option.equals(arg)) return true;
        return false;
    }

    private static String valueAfter(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equals(args[i])) return args[i + 1];
        }
        if ("--port".equals(option) && args.length > 1 && !args[1].startsWith("--")) return args[1];
        if (args.length > 0 && !"--demo".equals(args[0]) && !args[0].startsWith("--")) return args[0];
        return defaultValue;
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
