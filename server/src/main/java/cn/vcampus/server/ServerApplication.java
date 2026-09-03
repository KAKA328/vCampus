package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.StudentSelectionProfileProvider;
import cn.vcampus.store.StoreService;
import cn.vcampus.store.InMemoryStoreService;
import cn.vcampus.user.UserManagementService;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal multi-client server entry point.
 */
public final class ServerApplication implements Closeable {
    public static final int DEFAULT_PORT = 19090;

    private final int port;
    private final UserMessageHandler userMessages;
    private final CourseMessageHandler courseMessages;
    private final StoreMessageHandler storeMessages;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    public ServerApplication(int port, UserManagementService users) {
        this(port, users, CourseSelectionDemoFactory.createModule(),
                CourseSelectionDemoFactory.createProfileProvider(), new InMemoryStoreService());
    }

    private ServerApplication(int port, UserManagementService users, CourseSelectionModule module,
            StudentSelectionProfileProvider profiles, StoreService store) {
        this(port, users, module.getSelectionService(), module.getCatalogService(),
                module.getOfferingService(), profiles, store);
    }

    public ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            StudentSelectionProfileProvider profiles) {
        this(port, users, courses, null, null, profiles, new InMemoryStoreService());
    }

    ServerApplication(int port, UserManagementService users, CourseSelectionService courses,
            CourseCatalogService catalog, CourseOfferingService offerings,
            StudentSelectionProfileProvider profiles, StoreService store) {
        this.port = port;
        this.userMessages = new UserMessageHandler(users);
        this.courseMessages = new CourseMessageHandler(courses, catalog, offerings, profiles, users);
        this.storeMessages = new StoreMessageHandler(store, users);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("vCampus server listening on port " + port);
        while (!serverSocket.isClosed()) {
            try {
                clients.submit(new ClientHandler(serverSocket.accept()));
            } catch (IOException acceptedFailure) {
                if (!serverSocket.isClosed())
                    throw acceptedFailure;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null)
            serverSocket.close();
        clients.shutdownNow();
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;

        private ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket client = socket;
                    ObjectInputStream input = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                while (!client.isClosed()) {
                    Message request;
                    try {
                        request = (Message) input.readObject();
                    } catch (EOFException end) {
                        break;
                    }
                    output.writeObject(ServerApplication.this.dispatch(request));
                    output.flush();
                }
            } catch (IOException | ClassNotFoundException failure) {
                System.err.println("client connection closed: " + failure.getMessage());
            }
        }
    }

    Message dispatch(Message request) {
        if (request != null && isCourseMessage(request.getType())) {
            return courseMessages.handle(request);
        }
        if (request != null && isStoreMessage(request.getType())) {
            return storeMessages.handle(request);
        }
        return userMessages.handle(request);
    }

    private static boolean isCourseMessage(MessageType type) {
        return type == MessageType.COURSE_QUERY
                || type == MessageType.COURSE_SELECT
                || type == MessageType.COURSE_DROP
                || type == MessageType.COURSE_CREATE
                || type == MessageType.COURSE_UPDATE
                || type == MessageType.COURSE_DEACTIVATE
                || type == MessageType.COURSE_MANAGE
                || type == MessageType.COURSE_SELECTION_QUERY_V2
                || type == MessageType.COURSE_SELECT_OFFERING_V2
                || type == MessageType.COURSE_DROP_RECORD_V2;
    }

    private static boolean isStoreMessage(MessageType type) {
        return type == MessageType.STORE_QUERY
                || type == MessageType.STORE_PURCHASE || type == MessageType.STORE_ORDER_QUERY
                || type == MessageType.STORE_RESTOCK || type == MessageType.STORE_PRODUCT_ADD
                || type == MessageType.STORE_PRODUCT_UPDATE || type == MessageType.STORE_PRODUCT_DEACTIVATE
                || type == MessageType.STORE_CART_ADD || type == MessageType.STORE_CART_REMOVE
                || type == MessageType.STORE_CART_QUERY || type == MessageType.STORE_CART_CHECKOUT
                || type == MessageType.STORE_ORDER_LIST_ALL || type == MessageType.STORE_HOT_PRODUCTS
                || type == MessageType.STORE_ACCOUNT_QUERY || type == MessageType.STORE_ACCOUNT_RECHARGE
                || type == MessageType.STORE_ACCOUNT_ADJUST;
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        new ServerApplication(port, UserServiceFactory.create(args),
                StoreServiceFactory.create(args)).start();
    }

    private ServerApplication(int port, UserManagementService users, StoreService store) {
        this(port, users, CourseSelectionDemoFactory.createModule(),
                CourseSelectionDemoFactory.createProfileProvider(), store);
    }

    private static int parsePort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i]))
                return Integer.parseInt(args[i + 1]);
        }
        if (args.length > 0 && args[0].matches("\\d+"))
            return Integer.parseInt(args[0]);
        return DEFAULT_PORT;
    }
}
