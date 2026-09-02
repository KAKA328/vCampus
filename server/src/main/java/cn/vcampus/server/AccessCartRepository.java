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
        // 同用户同商品已存在则累加数量，与内存实现保持一致的合并语义
        CartItem existing = findSameProduct(item.getUserId(), item.getProductId());
        if (existing != null) {
            return updateQuantity(existing.getCartItemId(), existing.getQuantity() + item.getQuantity());
        }
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

    private CartItem findSameProduct(String userId, String productId) {
        String sql = "SELECT cart_item_id,user_id,product_id,quantity,added_at "
                + "FROM tblCartItem WHERE user_id=? AND product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, productId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next())
                    return null;
                return readCartItem(results);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to locate existing cart item", failure);
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
