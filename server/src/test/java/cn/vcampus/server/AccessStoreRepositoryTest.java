package cn.vcampus.server;

import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用临时 Access 数据库验证商品和订单数据可以真实保存。 */
class AccessStoreRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private AccessProductRepository products;
    private AccessOrderRepository orders;
    private Path database;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("store-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblProduct ("
                    + "product_id VARCHAR(32) NOT NULL,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "stock INTEGER NOT NULL,"
                    + "price DOUBLE NOT NULL,"
                    + "description VARCHAR(255),"
                    + "category VARCHAR(64) NOT NULL,"
                    + "active BIT NOT NULL,"
                    + "PRIMARY KEY (product_id))");
            statement.execute("CREATE TABLE tblOrder ("
                    + "order_id VARCHAR(36) NOT NULL,"
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "product_id VARCHAR(32) NOT NULL,"
                    + "quantity INTEGER NOT NULL,"
                    + "total_price DOUBLE NOT NULL,"
                    + "order_date DATETIME NOT NULL,"
                    + "product_name VARCHAR(100) NOT NULL,"
                    + "unit_price DOUBLE NOT NULL,"
                    + "PRIMARY KEY (order_id))");
            insertProduct(connection, "P001", "黑色签字笔", 200, 2.0, "0.5mm 中性笔", "文具");
            insertProduct(connection, "P002", "笔记本 A5", 150, 5.0, "80页横线本", "文具");
        }
        products = new AccessProductRepository(database);
        orders = new AccessOrderRepository(database);
    }

    @Test
    void findAllReturnsAllProducts() {
        assertEquals(2, products.findAll().size());
    }

    @Test
    void findByIdReturnsExistingProduct() {
        Product product = products.findById("P001");
        assertNotNull(product);
        assertEquals("黑色签字笔", product.getName());
        assertEquals(200, product.getStock());
        assertEquals(2.0, product.getPrice(), 0.001);
        assertEquals("文具", product.getCategory());
    }

    @Test
    void findByIdReturnsNullForMissingProduct() {
        assertEquals(null, products.findById("P999"));
    }

    @Test
    void saveInsertsNewProduct() {
        products.save(new Product("P003", "矿泉水", 300, 1.5, "天然矿泉水", "零食饮料"));
        Product saved = products.findById("P003");
        assertNotNull(saved);
        assertEquals("矿泉水", saved.getName());
        assertEquals(300, saved.getStock());
    }

    @Test
    void saveUpdatesExistingProduct() {
        products.save(new Product("P001", "签字笔（升级版）", 200, 2.5, "0.7mm 中性笔", "文具"));
        Product updated = products.findById("P001");
        assertNotNull(updated);
        assertEquals("签字笔（升级版）", updated.getName());
        assertEquals(2.5, updated.getPrice(), 0.001);
    }

    @Test
    void updateStockChangesStockOnly() {
        assertTrue(products.updateStock("P001", 50));
        Product updated = products.findById("P001");
        assertEquals(50, updated.getStock());
        assertEquals("黑色签字笔", updated.getName());
    }

    @Test
    void updateStockReturnsFalseForMissingProduct() {
        assertFalse(products.updateStock("P999", 10));
    }

    @Test
    void inventoryAndCatalogMutationsArePersisted() {
        assertTrue(products.addStock("P001", 25));
        assertEquals(225, products.findById("P001").getStock());

        assertTrue(products.updateProduct(new Product("P001", "签字笔升级", 225, 2.5,
                "新描述", "文具", false)));
        Product changed = products.findById("P001");
        assertEquals("签字笔升级", changed.getName());
        assertEquals(2.5, changed.getPrice(), 0.001);
        assertFalse(changed.isActive());

        assertTrue(products.deleteById("P001"));
        assertEquals(null, products.findById("P001"));
    }

    @Test
    void createOrderPersistsAndFindByUserIdReturnsIt() {
        Order order = new Order("ORD001", "student001", "P001", 5, 10.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        assertTrue(orders.create(order));
        assertEquals(1, orders.findByUserId("student001").size());
        Order saved = orders.findByUserId("student001").get(0);
        assertEquals("ORD001", saved.getOrderId());
        assertEquals("student001", saved.getUserId());
        assertEquals("P001", saved.getProductId());
        assertEquals(5, saved.getQuantity());
        assertEquals(10.0, saved.getTotalPrice(), 0.001);
        assertEquals("黑色签字笔", saved.getProductName());
        assertEquals(2.0, saved.getUnitPrice(), 0.001);
    }

    @Test
    void createRejectsDuplicateOrderId() {
        Order order = new Order("ORD002", "student001", "P001", 3, 6.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        assertTrue(orders.create(order));
        assertFalse(orders.create(order));
    }

    @Test
    void findByUserIdReturnsEmptyForNoOrders() {
        assertTrue(orders.findByUserId("nobody").isEmpty());
    }

    @Test
    void findByUserIdReturnsOnlyOwnOrders() {
        Order order1 = new Order("ORD010", "student001", "P001", 2, 4.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        Order order2 = new Order("ORD011", "student002", "P002", 1, 5.0,
                LocalDateTime.now(), "笔记本 A5", 5.0);
        orders.create(order1);
        orders.create(order2);
        assertEquals(1, orders.findByUserId("student001").size());
        assertEquals(1, orders.findByUserId("student002").size());
    }

    @Test
    void findAllAndSalesVolumeReadPersistedOrders() {
        orders.create(new Order("ORD020", "student001", "P001", 2, 4.0,
                LocalDateTime.now(), "黑色签字笔", 2.0));
        orders.create(new Order("ORD021", "student002", "P001", 3, 6.0,
                LocalDateTime.now(), "黑色签字笔", 2.0));
        orders.create(new Order("ORD022", "student002", "P002", 1, 5.0,
                LocalDateTime.now(), "笔记本 A5", 5.0));

        assertEquals(3, orders.findAll().size());
        List<Object[]> sales = orders.findSalesVolume();
        assertEquals(2, sales.size());
        assertEquals("P001", sales.get(0)[0]);
        assertEquals(5, sales.get(0)[1]);
    }

    private static void insertProduct(Connection connection, String productId, String name,
            int stock, double price, String description, String category) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProduct(product_id,name,stock,price,description,category,active) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, productId);
            statement.setString(2, name);
            statement.setInt(3, stock);
            statement.setDouble(4, price);
            statement.setString(5, description);
            statement.setString(6, category);
            statement.setBoolean(7, true);
            statement.executeUpdate();
        }
    }
}
