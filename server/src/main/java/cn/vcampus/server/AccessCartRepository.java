package cn.vcampus.server;

import cn.vcampus.store.CartItem;
import cn.vcampus.store.CartRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Access-backed cart repository using parameterized JDBC statements. */
public final class AccessCartRepository implements CartRepository {
    private final Path databasePath;

    public AccessCartRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public List<CartItem> findByUserId(String userId) {
        String sql = "SELECT cart_item_id,user_id,product_id,quantity,added_at "
                + "FROM tblCartItem WHERE user_id=? ORDER BY added_at";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                List<CartItem> items = new ArrayList<CartItem>();
                while (results.next()) {
                    items.add(readCartItem(results));
                }
                return items;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find cart items by user", failure);
        }
    }

    @Override
    public boolean addItem(CartItem item) {
        if (item == null)
            return false;
        // 先原子累加已有行；无行时插入，插入撞唯一约束则再累加
        if (accumulate(item.getUserId(), item.getProductId(), item.getQuantity()))
            return true;
        try {
            return insert(item);
        } catch (IllegalStateException conflict) {
            return accumulate(item.getUserId(), item.getProductId(), item.getQuantity());
        }
    }

    @Override
    public boolean removeItem(String cartItemId) {
        String sql = "DELETE FROM tblCartItem WHERE cart_item_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cartItemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to remove cart item", failure);
        }
    }

    @Override
    public boolean updateQuantity(String cartItemId, int newQuantity) {
        if (newQuantity <= 0)
            return false;
        String sql = "UPDATE tblCartItem SET quantity=? WHERE cart_item_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newQuantity);
            statement.setString(2, cartItemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to update cart quantity", failure);
        }
    }

    @Override
    public void clearByUserId(String userId) {
        String sql = "DELETE FROM tblCartItem WHERE user_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to clear cart", failure);
        }
    }

    private boolean insert(CartItem item) {
        String sql = "INSERT INTO tblCartItem(cart_item_id,user_id,product_id,quantity,added_at) "
                + "VALUES(?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getCartItemId());
            statement.setString(2, item.getUserId());
            statement.setString(3, item.getProductId());
            statement.setInt(4, item.getQuantity());
            statement.setTimestamp(5, Timestamp.valueOf(item.getAddedAt()));
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to add cart item", failure);
        }
    }

    private boolean accumulate(String userId, String productId, int quantity) {
        String sql = "UPDATE tblCartItem SET quantity=quantity+? WHERE user_id=? AND product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setString(2, userId);
            statement.setString(3, productId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to accumulate cart quantity", failure);
        }
    }

    private static CartItem readCartItem(ResultSet results) throws SQLException {
        return new CartItem(
                results.getString("cart_item_id"),
                results.getString("user_id"),
                results.getString("product_id"),
                results.getInt("quantity"),
                results.getTimestamp("added_at").toLocalDateTime());
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }
}
