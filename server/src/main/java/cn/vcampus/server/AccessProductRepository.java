package cn.vcampus.server;

import cn.vcampus.store.Product;
import cn.vcampus.store.ProductRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Access-backed product repository using parameterized JDBC statements. */
public final class AccessProductRepository implements ProductRepository {
    private final Path databasePath;

    public AccessProductRepository(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT product_id,name,stock,price,description,category,active "
                + "FROM tblProduct ORDER BY product_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            List<Product> products = new ArrayList<Product>();
            while (results.next()) {
                products.add(readProduct(results));
            }
            return products;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to list products", failure);
        }
    }

    @Override
    public Product findById(String id) {
        String sql = "SELECT product_id,name,stock,price,description,category,active "
                + "FROM tblProduct WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next())
                    return null;
                return readProduct(results);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find product", failure);
        }
    }

    @Override
    public void save(Product product) {
        Product existing = findById(product.getProductId());
        if (existing != null) {
            update(product);
        } else {
            insert(product);
        }
    }

    @Override
    public boolean updateStock(String productId, int newStock) {
        if (newStock < 0) return false;
        String sql = "UPDATE tblProduct SET stock=? WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newStock);
            statement.setString(2, productId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to update product stock", failure);
        }
    }

    @Override
    public boolean addStock(String productId, int amount) {
        if (amount <= 0) return false;
        String sql = "UPDATE tblProduct SET stock=stock+? WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, amount);
            statement.setString(2, productId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to add product stock", failure);
        }
    }

    @Override
    public boolean updateProduct(Product product) {
        if (product == null) return false;
        String sql = "UPDATE tblProduct SET name=?,stock=?,price=?,description=?,category=?,active=? "
                + "WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getName());
            statement.setInt(2, product.getStock());
            statement.setDouble(3, product.getPrice());
            statement.setString(4, product.getDescription());
            statement.setString(5, product.getCategory());
            statement.setBoolean(6, product.isActive());
            statement.setString(7, product.getProductId());
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to update product", failure);
        }
    }

    @Override
    public boolean deleteById(String productId) {
        String sql = "DELETE FROM tblProduct WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, productId);
            return statement.executeUpdate() > 0;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to delete product", failure);
        }
    }

    private void insert(Product product) {
        String sql = "INSERT INTO tblProduct(product_id,name,stock,price,description,category,active) "
                + "VALUES(?,?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getProductId());
            statement.setString(2, product.getName());
            statement.setInt(3, product.getStock());
            statement.setDouble(4, product.getPrice());
            statement.setString(5, product.getDescription());
            statement.setString(6, product.getCategory());
            statement.setBoolean(7, product.isActive());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to insert product", failure);
        }
    }

    private void update(Product product) {
        String sql = "UPDATE tblProduct SET name=?,stock=?,price=?,description=?,category=?,active=? "
                + "WHERE product_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, product.getName());
            statement.setInt(2, product.getStock());
            statement.setDouble(3, product.getPrice());
            statement.setString(4, product.getDescription());
            statement.setString(5, product.getCategory());
            statement.setBoolean(6, product.isActive());
            statement.setString(7, product.getProductId());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to update product", failure);
        }
    }

    private static Product readProduct(ResultSet results) throws SQLException {
        return new Product(
                results.getString("product_id"),
                results.getString("name"),
                results.getInt("stock"),
                results.getDouble("price"),
                results.getString("description"),
                results.getString("category"), results.getBoolean("active"));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }
}
