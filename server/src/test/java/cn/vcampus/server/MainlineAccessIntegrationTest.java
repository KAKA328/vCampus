package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainlineAccessIntegrationTest {
    @Test
    void accessModeSupportsLoginCourseQueryAndStorePurchaseFlow() throws Exception {
        Path database = Files.createTempDirectory(Paths.get("target"), "mainline-access-")
                .resolve("vcampus-mainline.accdb");
        initializeDatabase(database);
        String[] args = {"--db", database.toString()};
        CourseSelectionModule courses = CourseSelectionDemoFactory.createModule();
        ServerApplication server = new ServerApplication(0,
                UserServiceFactory.create(args), courses.getSelectionService(), courses.getCatalogService(),
                courses.getOfferingService(), CourseSelectionDemoFactory.createProfileProvider(),
                StoreServiceFactory.create(args), StudentServiceFactory.create(args));

        Message login = server.dispatch(Message.request("login", MessageType.LOGIN,
                new UserCredentials("demo_student", "Demo123", "ignored", "STUDENT")));
        assertEquals(StatusCode.OK, login.getStatusCode());
        assertInstanceOf(Session.class, login.getPayload());
        String token = ((Session) login.getPayload()).getToken();

        Message rounds = server.dispatch(Message.request("course-rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(token)));
        assertEquals(StatusCode.OK, rounds.getStatusCode());
        assertFalse(((List<?>) rounds.getPayload()).isEmpty());

        Message student = server.dispatch(Message.request("student-self", MessageType.STUDENT_QUERY,
                StudentQueryCommand.self(token)));
        assertEquals(StatusCode.OK, student.getStatusCode());
        assertInstanceOf(StudentRecord.class, student.getPayload());
        assertEquals("demo_student", ((StudentRecord) student.getPayload()).getStudentId());

        Message products = server.dispatch(Message.request("store-products", MessageType.STORE_QUERY,
                new StoreQueryCommand(token)));
        assertEquals(StatusCode.OK, products.getStatusCode());
        assertTrue(containsProduct((List<?>) products.getPayload(), "P001"));

        Message purchase = server.dispatch(Message.request("store-purchase", MessageType.STORE_PURCHASE,
                new StorePurchaseCommand(token, "P001", 1)));
        assertEquals(StatusCode.OK, purchase.getStatusCode());

        Message orders = server.dispatch(Message.request("store-orders", MessageType.STORE_ORDER_QUERY,
                new StoreOrderQueryCommand(token)));
        assertEquals(StatusCode.OK, orders.getStatusCode());
        assertTrue(containsOrder((List<?>) orders.getPayload(), "P001", 1));
    }

    private static boolean containsProduct(List<?> products, String productId) {
        for (Object item : products) {
            Product product = (Product) item;
            if (productId.equals(product.getProductId())) return true;
        }
        return false;
    }

    private static boolean containsOrder(List<?> orders, String productId, int quantity) {
        for (Object item : orders) {
            Order order = (Order) item;
            if (productId.equals(order.getProductId()) && quantity == order.getQuantity()) return true;
        }
        return false;
    }

    private static void initializeDatabase(Path database) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            executeScript(statement, Paths.get("..", "database", "schema.sql"));
            executeScript(statement, Paths.get("..", "database", "seed.sql"));
        }
    }

    private static void executeScript(Statement statement, Path script) throws Exception {
        String sql = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        for (String command : cleaned.toString().split(";")) {
            String trimmed = command.trim();
            if (trimmed.toUpperCase().startsWith("CREATE INDEX")
                    || trimmed.toUpperCase().startsWith("CREATE UNIQUE INDEX")) {
                continue;
            }
            if (!trimmed.isEmpty()) {
                try {
                    statement.execute(trimmed);
                } catch (Exception failure) {
                    throw new AssertionError("Failed SQL command: " + trimmed, failure);
                }
            }
        }
    }
}
