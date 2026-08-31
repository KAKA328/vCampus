package cn.vcampus.server;

import cn.vcampus.store.Order;
import cn.vcampus.store.OrderRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Access-backed order repository using parameterized JDBC statements. */
public final class AccessOrderRepository implements OrderRepository {
    private final Path databasePath;

    public AccessOrderRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public boolean create(Order order) {
        if (exists(order.getOrderId()))
            return false;
        String sql = "INSERT INTO tblOrder(order_id,user_id,product_id,quantity,"
                + "total_price,order_date,product_name,unit_price) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, order.getOrderId());
            statement.setString(2, order.getUserId());
            statement.setString(3, order.getProductId());
            statement.setInt(4, order.getQuantity());
            statement.setDouble(5, order.getTotalPrice());
            statement.setTimestamp(6, Timestamp.valueOf(order.getOrderDate()));
            statement.setString(7, order.getProductName());
            statement.setDouble(8, order.getUnitPrice());
            statement.executeUpdate();
            return true;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to create order", failure);
        }
    }

    @Override
    public List<Order> findByUserId(String userId) {
        String sql = "SELECT order_id,user_id,product_id,quantity,"
                + "total_price,order_date,product_name,unit_price "
                + "FROM tblOrder WHERE user_id=? ORDER BY order_date";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                List<Order> orders = new ArrayList<Order>();
                while (results.next()) {
                    orders.add(readOrder(results));
                }
                return orders;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find orders by user", failure);
        }
    }

    // 占位实现，Day 2 完成真实 JDBC 逻辑
    @Override
    public List<Order> findAll() {
        return new ArrayList<Order>();
    }

    // 占位实现，Day 2 完成真实 JDBC 逻辑
    @Override
    public List<Object[]> findSalesVolume() {
        return new ArrayList<Object[]>();
    }

    private boolean exists(String orderId) {
        String sql = "SELECT order_id FROM tblOrder WHERE order_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to check order existence", failure);
        }
    }

    private static Order readOrder(ResultSet results) throws SQLException {
        return new Order(
                results.getString("order_id"),
                results.getString("user_id"),
                results.getString("product_id"),
                results.getInt("quantity"),
                results.getDouble("total_price"),
                results.getTimestamp("order_date").toLocalDateTime(),
                results.getString("product_name"),
                results.getDouble("unit_price"));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }
}
