package cn.vcampus.client;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
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
            UserCredentials demo = new UserCredentials("demo-student", "demo123", "Demo Student", "STUDENT");
            output.writeObject(Message.request("startup-register", MessageType.REGISTER, demo));
            output.flush();
            Message registerResponse = (Message) input.readObject();
            System.out.println("register response: " + registerResponse.getStatusCode());

            output.writeObject(Message.request("startup-login", MessageType.LOGIN, demo));
            output.flush();
            Message loginResponse = (Message) input.readObject();
            System.out.println("login response: " + loginResponse.getStatusCode());
        }
    }
}
